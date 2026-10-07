package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 冷识（{@code remoteness}）的被动「**始末**」：角色的**持续自我增益**与**持续光环**的唯一节拍。
 *
 * <h2>职责一：永久增益（每 20 刻刷新一次）</h2>
 * <ul>
 *   <li><b>固有</b>（角色一装上就有）：速度一、跳跃提升一；</li>
 *   <li><b>进化升级</b>（档位读口在 {@link RemotenessEvolutionPassive}）：1 级跳跃提升 2、
 *       2 级速度 3、3 级持续生命恢复 2、6 级永久抗性提升 1。</li>
 * </ul>
 *
 * <h2>职责二：5 级光环（每秒一次）</h2>
 * 身边半径 10 格内的**敌人**每秒被扣 2 点特殊值。档位读数来自
 * {@link RemotenessEvolutionPassive#auraSanteDrainPerSecond()}（未达 5 级回 0 ⇒ 整段 no-op）。
 *
 * <h2>★ 为什么"永久增益"要"每隔一段刷新一次"，而不是施放一次了事</h2>
 * 原版在玩家**死亡 / 重生**时会清空身上的全部药水效果，因此"进场给一次"的写法会在第一次死亡后
 * 永久失效（而 `start()` 不会因重生再跑一次 —— 角色实例在死亡时就被清掉了，重生后玩家要重新选角色，
 * 那时才重新 `start()`）。用**无限时长**（{@code INFINITE_DURATION}）也救不了：它同样在死亡时被清空。
 * 因此本类采用"短时长 + 周期刷新"：每次给 {@link #EFFECT_DURATION_TICKS}（60 刻 = 3 秒）时长、
 * 每 {@link #REFRESH_INTERVAL_TICKS}（20 刻 = 1 秒）刷新一次 —— 刷新间隔只有时长的一半，
 * 于是即便有一两次节拍被服务器卡掉，效果也不会闪断。
 * <p><b>为什么时长要"比刷新间隔长"而不是刚好等于</b>：等于时，刷新那一刻的"剩余时长"与"新给的时长"
 * 相同，原版对"同增幅、同时长"的处理不会延长它 ⇒ 效果会在刷新点附近出现可见的闪断；
 * 给 3 倍余量后，每次刷新都是"更长的同增幅效果"⇒ 稳定覆盖。
 *
 * <h2>为什么药水效果走 {@link BuffComponent} 而不是直接 {@code player.addPotionEffect}</h2>
 * buff 组件是**记账**的那一个：它记下"本系统给这个玩家上过哪些类型"，
 * 并在自己 {@code stop()} 时把这些类型逐个摘掉。走它 ⇒ 角色清除时增益随之消失；
 * 直接写平台则会在角色清除后留下"已经不属于任何角色"的速度与跳跃（陈旧状态）。
 * <p>因此本类的 {@link #stop()} **不需要**摘任何药水效果 —— 那是 buff 组件自己的收尾
 * （它必有 {@code stop()}：实例销毁链的必经点，见 {@code TaskComponent} 的同类说明）。
 *
 * <h2>为什么光环也在这里（而不是在进化里）</h2>
 * 与「谁持有数据谁动手」一致：本类**持有节拍**（它已经每 tick 跑一次），进化类只持有档位数值。
 * 于是"每秒 / 每刻"的结算落在持有节拍者身上，进化类保持"只回答数字"而不去摸别的组件。
 * 判敌口径走 {@code svc().components().get(FactionComponent.class).isHostileTo(...)}（与既有的索敌实现同一条路），
 * 因此"没有阵营算敌人 / 同阵营不算 / 创造旁观不算"三条不需要在本类重写。
 *
 * <h2>不在本类里验的（如实申报）</h2>
 * 施加药水效果与查周围实体都要求真实玩家；离线（{@code self} 为 {@code null}）时整段安静跳过。
 */
public class RemotenessStartEndPassive extends PassiveSkill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "remoteness_start_end_passive";

    // ───────── 节拍与时长（唯一修改点）─────────

    /** 永久增益的刷新间隔（20 刻 = 1 秒）。 */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    /** 每次刷新给的药水时长（60 刻 = 3 秒；= 刷新间隔的 3 倍，见类注释）。 */
    private static final int EFFECT_DURATION_TICKS = 60;

    /** 光环的结算间隔（20 刻 = 1 秒）。 */
    private static final int AURA_INTERVAL_TICKS = 20;

    /** 光环半径（格）。 */
    private static final double AURA_RADIUS = 10d;

    // ───────── 协作组件（start() 里一次取好）─────────

    private BuffComponent buff;
    private SanTEComponent sante;

    /** 进化被动（档位读口的来源）；{@code null} = 该角色没装它（退化为基线值，不抛）。 */
    private RemotenessEvolutionPassive evolution;

    // ───────── 节拍计数（本组件内累计，不用调度器的次数计数）─────────

    private int refreshCountdown;
    private int auraCountdown;

    public RemotenessStartEndPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符：被动经统一 {@code addComponent} 入口装配且无栏位，因此天然不占热键栏。
     * <p>依赖 = 实取清单（{@code start()} 里的三个调用点）；进化被动是必需依赖
     * （漏声明会让装配期不报错、而运行期静默退化成基线值 —— 那是本仓明令禁止的失败形态）。
     */
    public static final class Specification extends PassiveSkill.Specification<RemotenessStartEndPassive> {

        public Specification() {
            super(Component.text("始末"),
                    List.of(
                            Component.text("永久速度一, 跳跃提升一"),
                            Component.text("进化后按档位提升：跳跃提升2 / 速度3 / 持续生命恢复2 / 永久抗性1"),
                            Component.text("5级起：身边半径10内的敌人每秒被扣除2点特殊值")
                    ));
            requires(BuffComponent.class).requires(SanTEComponent.class)
                    .requires(RemotenessEvolutionPassive.class)
                    //索敌读阵营组件 ⇒ 缺它则本被动不索敌，装配期就拦住
                    .requires(FactionComponent.class);
        }

        @Override
        public RemotenessStartEndPassive create(String id, ComponentServicesPort services) {
            return new RemotenessStartEndPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 开始生效：把三个协作组件一次查好缓存进字段，并**立刻**施加一次永久增益
     * （不等第一个 20 刻的节拍 —— 否则选完角色会有一秒的"裸状态"）。
     */
    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        evolution = getComponent(RemotenessEvolutionPassive.class);
        refreshCountdown = 0;
        auraCountdown = 0;
        Player self = selfPlayer();
        if (self != null) {
            refreshPermanentEffects();
        }
    }

    /**
     * 停止生效：清掉协作组件引用与两个节拍计数。
     * <p>**不摘药水效果**：那是 buff 组件在它自己的 {@code stop()} 里的收尾（唯一的药水记账账本在它那里），
     * 本类重复摘一遍会与"账本只在一处"的既有口径相冲突。
     */
    @Override
    public void stop() {
        buff = null;
        sante = null;
        evolution = null;
        refreshCountdown = 0;
        auraCountdown = 0;
    }

    // ───────── 每 tick：两条节拍（1 秒刷新增益 / 1 秒结算光环）─────────

    @Override
    public void update() {
        Player self = selfPlayer();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }

        if (++refreshCountdown >= REFRESH_INTERVAL_TICKS) {
            refreshCountdown = 0;
            refreshPermanentEffects();
        }

        if (++auraCountdown >= AURA_INTERVAL_TICKS) {
            auraCountdown = 0;
            applyAura(self);
        }
    }

    // ───────── 永久增益 ─────────

    /**
     * 按**当前档位**重新施加四条持续增益。每次都是"覆盖式刷新"：档位升高后（1/2/3/6 级）
     * 下一次刷新就带着新的增幅上去，不需要任何"升级时回调"。
     * <p>四个调用点各自独立判档：低档位的增益在高档位**仍然在场**（例如 2 级不会把跳跃提升顶掉），
     * 因此这里**不是** if/else 链，而是四次独立施加。
     */
    private void refreshPermanentEffects() {
        if (buff == null) {
            return;
        }
        //固有：速度一 / 跳跃提升一（档位只会把增幅抬得更高）
        apply(PotionEffectType.SPEED, evolution != null
                ? evolution.speedAmplifier() : RemotenessEvolutionPassive.BASE_SPEED_AMPLIFIER);
        apply(PotionEffectType.JUMP_BOOST, evolution != null
                ? evolution.jumpAmplifier() : RemotenessEvolutionPassive.BASE_JUMP_AMPLIFIER);

        //3 级起：持续生命恢复 2
        if (evolution != null && evolution.hasRegeneration()) {
            apply(PotionEffectType.REGENERATION, evolution.regenerationAmplifier());
        }

        //6 级起：永久抗性提升 1
        if (evolution != null && evolution.hasResistance()) {
            apply(PotionEffectType.RESISTANCE, evolution.resistanceAmplifier());
        }
    }

    /**
     * 施加一条持续增益：{@code ambient = true}（信标式光晕）、{@code particles = false}
     * —— "永久"的增益不该在屏幕上一直冒药水泡。
     */
    private void apply(PotionEffectType type, int amplifier) {
        buff.applyPotionEffect(type, EFFECT_DURATION_TICKS, Math.max(0, amplifier), true, false);
    }

    // ───────── 5 级光环 ─────────

    /**
     * 5 级光环：身边半径 {@link #AURA_RADIUS} 内的**敌人**每秒被扣
     * {@link RemotenessEvolutionPassive#auraSanteDrainPerSecond()} 点特殊值。
     *
     * <p>三条边界：① 未达 5 级时读数为 0 ⇒ 整段 no-op（不遍历、不查敌）；
     * ② 只算**敌对**玩家（判敌口径归阵营组件，本类不重写"没有阵营算敌人"等规则）；
     * ③ 扣的是**对方实例**的特殊值（走特殊值组件的跨实例入口，与「流血」的既有做法同一条路）。
     */
    private void applyAura(Player self) {
        if (sante == null) {
            return;
        }
        int drain = evolution != null
                ? evolution.auraSanteDrainPerSecond()
                : RemotenessEvolutionPassive.BASE_AURA_SANTE_DRAIN_PER_SECOND;
        if (drain <= 0) {
            return;
        }

        for (Entity entity : self.getNearbyEntities(AURA_RADIUS, AURA_RADIUS, AURA_RADIUS)) {
            if (!(entity instanceof Player victim) || victim.equals(self)) {
                continue;
            }
            if (victim.isDead() || !victim.isOnline()) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) {
                continue;
            }
            sante.decreaseSanTE(victim.getUniqueId(), drain);
        }
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null} —— 三条使用点都容忍它）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }
}
