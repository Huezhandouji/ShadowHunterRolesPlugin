package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

/**
 * 「特克」被动：**逆转天意**，兼「真理」层数的持有者与全局入口。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>近战 / 突进命中敌人</b> ⇒ <b>减少 1 秒所有技能 CD</b>，并获得 <b>1 点能量</b>；</li>
 *   <li><b>当能量 ≥ {@value #SHIELD_ENERGY_THRESHOLD} 且自身生命 ≤ {@value #SHIELD_HEALTH_THRESHOLD}
 *       （= 4 颗心）</b> ⇒ 获得 <b>20 点伤害吸收</b>，持续 {@value #SHIELD_DURATION_TICKS} 刻（10 秒），
 *       并<b>消耗掉全部能量</b>。</li>
 * </ol>
 *
 * <h2>为什么本类同时是"真理"的门面</h2>
 * 真理的真值住在 {@link TekTruth}（静态账本，跨玩家实例可读）。本类只做**薄封装**：
 * 组件侧一律经 {@code TekDestinyPassive} 的公开方法读写真理，这样"这一族怎么记账"只有一个出处，
 * 以后要换存储也只改这里。
 *
 * <h2>「减少 1 秒所有技能 CD」怎么落地（口径申报）</h2>
 * 冷却由每个 {@code ActiveComponent} 自持（{@code startCooldown(int)} / {@code remainingCooldownTicks()}
 * 都是公开面）。因此本类的做法 = 遍历本实例内所有 {@link ActiveComponent}（技能 + 主武器），
 * 对每个"正在冷却"的组件把剩余刻数**减 {@value #COOLDOWN_RELIEF_TICKS} 刻**后重新启动：
 * {@code startCooldown(remaining - 20)}。扣到 0 或以下即等于立刻可用（{@code startCooldown(≤0)} 清冷却）。
 * <p>★ <b>不动框架</b>：全程只用 {@code ComponentLookupPort#getAll(Class)} 与
 * {@code ActiveComponent} 已有的公开方法，没有新增任何框架能力、没改任何框架文件。
 *
 * <h2>为什么"减少 CD"只对技能生效、不含自己</h2>
 * 需求写的是"减少一秒所有技能 CD"（不含主武器，也没有"连自己"的意思）。本类因此**排除主武器**
 * （{@code MainWeapon} 不是技能）且**排除本被动**（被动无冷却）。
 *
 * <h2>更新节拍</h2>
 * {@code update()}（20 Hz）做四件与"命中"无关的事：
 * <ol>
 *   <li><b>绘制真理特效</b>：在携带真理的敌人身上画金环，
 *       起始高度 = 敌人坐标上方 {@value #TRUTH_ORBIT_BASE_HEIGHT} 格，层数越多环越高，
 *       但**层数到达 {@value #TRUTH_ORBIT_MAX_LAYERS} 就不再叠高**；</li>
 *   <li><b>维护真理账本</b>：<b>敌人死亡 / 离线 ⇒ 其真理层数一并消失</b>
 *       （本被动是唯一周期性遍历账本的地方）；</li>
 *   <li>自己脚下一点金色底盘（表示被动已生效）；</li>
 *   <li>兜底检查护盾阈值（命中的那条路径已经查过，这里保证"站着不动能量自然涨到 90"也能触发）。</li>
 * </ol>
 * <p>另有两条**事件驱动**的行为（不在这里）：命中时减技能 CD / 加能量 / 叠真理，
 * 以及"某个敌人的真理跨过 {@value TekTruth#UNLOCK_THRESHOLD} 层时提示一次"（见 {@link #onHit}）。
 */
public class TekDestinyPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "tekDestinyPassive";

    // ───────── 数值口径（唯一修改点）─────────

    /** 每次命中给的能量（需求：获得 1 点能量）。 */
    private static final int ENERGY_ON_HIT = 1;

    /** 每次命中减免的冷却刻数（需求：减少 1 秒 = 20 刻）。 */
    private static final int COOLDOWN_RELIEF_TICKS = 20;

    /** 护盾触发的能量阈值（需求：能量大于等于 90）。 */
    private static final int SHIELD_ENERGY_THRESHOLD = 90;

    /** 护盾触发的生命阈值（需求：8 滴血及以下 = 4 颗心 = 4.0 点血量）。 */
    private static final double SHIELD_HEALTH_THRESHOLD = 8d / 2d;

    /** 护盾持续时间（需求：10 秒 = 200 刻）。 */
    private static final int SHIELD_DURATION_TICKS = 200;

    /** 伤害吸收增幅：4 ⇒ 吸收 V（原版每级 4 点吸收 ⇒ 4 级 = 20 点，与原版幅度对齐）。 */
    private static final int SHIELD_ABSORPTION_AMPLIFIER = 4;

    /** 环绕粒子：多少层画一颗（层数 / 该值 = 粒子数）。 */
    private static final int LAYERS_PER_PARTICLE = 2;

    /**
     * **真理特效的"叠高上限"层数**：层数到这么多以后**不再往上叠**（需求）。
     *
     * <p>需求原话："**当层数到达 10 就不再叠高**"。
     * <p>含义：环的高度只随"有效层数"增长，而有效层数 = {@code min(实际层数, 本值)}
     * ⇒ 10 层以上视觉上停在"10 层那么高"，不再继续往上长。
     * <p>★ 取值与 {@link TekTruth#UNLOCK_THRESHOLD}（= 10）相同 —— 需求说的"10"就是那个解锁阈值。
     * 这里刻意写成**独立常量**（而不是直接引用解锁阈值），让"渲染上限"与"解锁阈值"
     * 将来可以分别调整；`TekTruthFeedbackTest` 里有一条断言把"两者当前相等"钉住，
     * 因此谁要分开改，测试会提醒他这是**有意的**而不是手滑。
     */
    private static final int TRUTH_ORBIT_MAX_LAYERS = 10;

    /**
     * 环绕粒子最大颗数（防止高叠层把客户端刷爆）。
     * <p>它现在通常是**第二道**保险：{@link #TRUTH_ORBIT_MAX_LAYERS} / {@link #LAYERS_PER_PARTICLE}
     * = 5 已远小于本值。留着是为了"以后调高叠高上限或调小每颗代表层数"时不至于失控。
     */
    private static final int MAX_ORBIT_PARTICLES = 12;

    /** 环绕半径（格）。 */
    private static final double ORBIT_RADIUS = 0.85d;

    /** 每颗抬高的格数。 */
    private static final double ORBIT_STEP_Y = 0.16d;

    /**
     * **敌人身上真理特效的起始高度（格）** —— 相对**敌人自身坐标**往上抬这么多。
     *
     * <p>需求："敌人身上的'真理'特效应该在其坐标位置 0.7 格高显示……（就是保持原特效不变，
     * 将特效起始高度改为敌人坐标的 0.6 格高）"
     * <p>★ 口径申报：需求正文写 0.7、括注写 0.6，本实现按**括注的 0.6**
     * （括注以"就是……改为……"的方式复述，视作最终值）。要改用 0.7 只改这一个常量。
     * <p>层数越多环越高（由 {@link #ORBIT_STEP_Y} 逐颗抬高实现）；本常量只管**起始高度**。
     */
    private static final double TRUTH_ORBIT_BASE_HEIGHT = 0.6d;

    /**
     * 真理账本的维护节拍（刻）：每这么多刻扫一次账本（死亡清理 + 画环）。
     * <p>取 2（10 Hz）：死亡清理与粒子都不需要 20 Hz，减半能省一半客户端粒子量。
     */
    private static final int TRUTH_SCAN_INTERVAL_TICKS = 2;

    // ───────── 依赖（start() 里一次查好）─────────

    private EnergyComponent energy;
    private VitalsComponent vitals;
    private BuffComponent buff;

    /** 粒子相位（每帧推进，让环绕转起来）。 */
    private double phase;

    /** 粒子节流计数（每 2 刻才画一次敌人身上的真理环）。 */
    private int phaseStep;

    /** 护盾压制标志：刚给过护盾 ⇒ 能量没被消耗完之前不重复触发（双保险）。 */
    private boolean shieldGiven;

    public TekDestinyPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（被动无栏位 ⇒ 不占热键栏）。 */
    public static final class Specification extends PassiveSkill.Specification<TekDestinyPassive> {

        public Specification() {
            super(Component.text("逆转天意"),
                    List.of(Component.text("普攻或突进命中敌人：减少 1 秒所有技能冷却，获得 1 点能量"),
                            Component.text("能量 ≥ 90 且生命 ≤ 4 颗心：获得 20 点伤害吸收，持续 10 秒，并清空全部能量"),
                            Component.text("每次命中叠加一层「真理」")));
            requires(EnergyComponent.class).requires(VitalsComponent.class).requires(BuffComponent.class);
        }

        @Override
        public TekDestinyPassive create(String id, ComponentServicesPort services) {
            return new TekDestinyPassive(id, services, this);
        }
    }

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    @Override
    public void stop() {
        shieldGiven = false;
        phase = 0d;
        phaseStep = 0;
    }

    // ─────────── 对外行为面：供主武器 / 技能在命中后调用 ───────────

    /**
     * **一次命中**（普攻 / 突进 / 技能命中敌人时统一走这里）。
     *
     * @param victim 被命中的敌人（{@code null} ⇒ 只做冷却与能量部分，不叠真理）
     * @return 本次叠加后该敌人的真理层数（{@code victim == null} ⇒ 0）
     */
    public int onHit(Player victim) {
        Player owner = svc().self().player();
        if (owner == null) {
            return 0;
        }

        //① 减少 1 秒所有技能 CD
        reduceAllSkillCooldowns(owner);

        //② +1 能量
        if (energy != null) {
            energy.increase(ENERGY_ON_HIT);
        }

        //③ 叠一层真理（挂在被击中的敌人身上）
        int layers = 0;
        if (victim != null) {
            UUID victimId = victim.getUniqueId();
            //★ 需求："当有一个敌人的真理层数到 10 时，就发出声音提示一次；
            //   即使他已经提醒过一次，但他再次达到时，也会提醒一次（一个敌人可以提示多次）"
            //   ⇒ 判据 = **跨越阈值的那一瞬**（用状态差，不是状态本身）：
            //     掉回 10 以下再涨上来会再次跨越 ⇒ 天然支持"多次提醒"，
            //     不需要额外维护"已提醒过"的名单。
            int before = TekTruth.layersOf(victimId);
            layers = TekTruth.addOne(victimId);
            if (crossesUnlockThreshold(before, layers)) {
                World alertWorld = owner.getWorld();
                if (alertWorld != null) {
                    TekSound.truthUnlockedAlertSound(alertWorld, owner.getLocation());
                }
            }
        }

        //④ 命中即顺手查一次护盾阈值（能量刚涨过 ⇒ 可能刚好到 90）
        tryTriggerShield(owner);

        World world = owner.getWorld();
        if (world != null) {
            TekSound.destinyTickSound(world, owner.getLocation());
        }
        return layers;
    }

    /**
     * **减少 1 秒所有技能 CD**（只看技能，不含主武器、不含被动）。
     *
     * <p>遍历本实例内全部 {@link ActiveComponent}，跳过非技能者（主武器不是"技能"），
     * 对每个正在冷却者把剩余刻数减 {@value #COOLDOWN_RELIEF_TICKS} 刻。
     */
    private void reduceAllSkillCooldowns(Player owner) {
        for (ActiveComponent active : svc().components().getAll(ActiveComponent.class)) {
            //主武器不是"技能"（需求：减少所有技能 CD）⇒ 跳过
            if (active instanceof com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon) {
                continue;
            }
            if (!active.isCoolingDown()) {
                continue;
            }
            int remaining = active.remainingCooldownTicks();
            //★ 用公开面重启冷却：剩余 - 20（≤0 时 startCooldown 会直接清冷却）
            active.startCooldown(remaining - COOLDOWN_RELIEF_TICKS);
        }
    }

    /**
     * **某敌人的真理"跨过解锁阈值"了吗**（纯函数 ⇒ 可离线单测）。
     *
     * <p>需求："真理层数到 10 时提示一次；再次达到时也会提醒一次（一个敌人可以提示多次）"
     * ⇒ 判据必须是**状态差（边沿）**，不是状态本身：
     * <ul>
     *   <li>9 → 10 ⇒ {@code true}（第一次达到，提示）；</li>
     *   <li>10 → 11、20 → 21 ⇒ {@code false}（已经在阈值之上，不重复提示）；</li>
     *   <li>掉回 9 后再 9 → 10 ⇒ {@code true}（再次达到 ⇒ **再提示一次**）；</li>
     *   <li>死亡清零后 0 → 10（一次加满，理论上不会）⇒ {@code true}。</li>
     * </ul>
     *
     * @param before 叠加前的层数
     * @param after  叠加后的层数
     */
    static boolean crossesUnlockThreshold(int before, int after) {
        return before < TekTruth.UNLOCK_THRESHOLD && after >= TekTruth.UNLOCK_THRESHOLD;
    }

    /**
     * **护盾判定**（需求：能量 ≥ 90 且生命 ≤ 8 滴血 ⇒ 20 点吸收 10 秒 + 清空能量）。
     *
     * <p>顺序很重要：**先确认能扣光能量**，再施加吸收，最后才 {code energy.set(0)} ——
     * 三件事必须原子地成功或都不做，否则会出现"扣了能量却没给盾"。
     */
    private void tryTriggerShield(Player owner) {
        if (energy == null || buff == null) {
            return;
        }
        int currentEnergy = energy.current();
        double health = owner.getHealth();
        if (currentEnergy < SHIELD_ENERGY_THRESHOLD || health > SHIELD_HEALTH_THRESHOLD) {
            //条件不满足 ⇒ 允许下次重新触发
            shieldGiven = false;
            return;
        }
        if (shieldGiven) {
            return;
        }
        shieldGiven = true;

        //20 点伤害吸收：原版吸收每级 4 点 ⇒ 增幅 4 = 20 点（对自己的药水 ⇒ 走 buff 的记账口是对的）
        buff.applyPotionEffect(PotionEffectType.ABSORPTION.createEffect(
                SHIELD_DURATION_TICKS, SHIELD_ABSORPTION_AMPLIFIER));
        //消耗全部能量（需求：消耗所有能量）
        energy.set(0);

        World world = owner.getWorld();
        if (world != null) {
            TekSound.destinyShieldSound(world, owner.getLocation());
        }
    }

    // ─────────── 真理的门面（组件侧一律经本类读写）───────────

    /** 叠一层真理（返回叠加后层数）。 */
    public int addTruth(UUID target) {
        return TekTruth.addOne(target);
    }

    /** 叠若干层真理（返回叠加后层数）。 */
    public int addTruth(UUID target, int amount) {
        return TekTruth.add(target, amount);
    }

    /** 读某目标的真理层数。 */
    public int truthOf(UUID target) {
        return TekTruth.layersOf(target);
    }

    /** 清空某目标的真理（返回被清掉的层数）。 */
    public int clearTruth(UUID target) {
        return TekTruth.clearOne(target);
    }

    /** 场上是否有真理 ≥ {@link TekTruth#UNLOCK_THRESHOLD} 的角色（「真理之刺」解锁条件）。 */
    public boolean isVerdictUnlocked() {
        return TekTruth.isUnlocked();
    }

    /** 清空全场真理（返回总层数）。 */
    public int clearAllTruth() {
        return TekTruth.clearAll();
    }

    /** 当前有真理的目标快照（供「真理之刺」挑最近的敌人）。 */
    public List<TekTruth.Target> truthSnapshot() {
        return TekTruth.snapshot();
    }

    // ─────────── 每 tick：环绕粒子 + 阈值兜底 ───────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        phase += 0.32d;

        //① 真理层数的观感画在**携带真理的敌人身上**（层数越多、金环越高；
        //   但层数到 TRUTH_ORBIT_MAX_LAYERS 就不再叠高 —— 由 orbitsFor 封顶），
        //   顺带维护账本（死亡清理）。每 TRUTH_SCAN_INTERVAL_TICKS 刻一次。
        if (phaseStep % TRUTH_SCAN_INTERVAL_TICKS == 0) {
            for (TekTruth.Target entry : TekTruth.snapshot()) {
                Player carrier = org.bukkit.Bukkit.getPlayer(entry.playerId());
                //★ 需求："敌人死亡时，其身上'真理'层数也随之消失"。
                //  判据用 `isDead() || 血量 ≤ 0`：死亡界面上的玩家 `isDead()` 仍可能为 false，
                //  血量才可靠（与 RedBleedPassive#canReceiveBleed 同口径）。
                //  离线也一并清（否则会留到惰性过期）。
                if (carrier == null || !carrier.isOnline()
                        || carrier.isDead() || carrier.getHealth() <= 0d) {
                    TekTruth.clearOne(entry.playerId());
                    continue;
                }
                World carrierWorld = carrier.getWorld();
                if (carrierWorld == null) {
                    continue;
                }
                int particles = orbitsFor(entry.layers());
                if (particles <= 0) {
                    continue;
                }
                //★ 需求："特效起始高度改为敌人坐标的 0.6 格高"（见 TRUTH_ORBIT_BASE_HEIGHT）
                TekVfx.truthOrbit(carrierWorld,
                        carrier.getLocation().clone().add(0d, TRUTH_ORBIT_BASE_HEIGHT, 0d),
                        particles, phase, ORBIT_RADIUS, ORBIT_STEP_Y);
            }
        }
        phaseStep++;

        //② 自己脚下一点金色底盘（表示被动已生效；低成本常驻观感）
        World world = owner.getWorld();
        if (world != null) {
            Location feet = owner.getLocation().clone().add(0d, 0.05d, 0d);
            TekVfx.truthOrbit(world, feet, 3, phase, ORBIT_RADIUS, ORBIT_STEP_Y);
        }

        //③ 阈值兜底：站着不动、能量自然涨到 90 且残血时也应当触发
        tryTriggerShield(owner);
    }

    // ─────────── 探针读口 ───────────

    /** 护盾是否刚给过（排障读口）。 */
    public boolean shieldActive() {
        return shieldGiven;
    }

    /**
     * **真理层数 → 环绕粒子颗数**（纯函数 ⇒ 可离线单测）。
     *
     * <p>三步（需求逐条）：
     * <ol>
     *   <li><b>层数封顶</b>：{@code min(实际层数, }{@value #TRUTH_ORBIT_MAX_LAYERS}{@code )}
     *       —— 需求"<b>当层数到达 10 就不再叠高</b>"；
     *       因为环的**高度**由颗数（逐颗抬高）决定 ⇒ 封住颗数 = 封住高度；</li>
     *   <li>每 {@value #LAYERS_PER_PARTICLE} 层画一颗；</li>
     *   <li>再过一道 {@value #MAX_ORBIT_PARTICLES} 的绝对上限（防止将来调参失控刷爆客户端）。</li>
     * </ol>
     *
     * <p>于是 {@code orbitsFor(10)} 与 {@code orbitsFor(50)} 得到**同一个值** —— 视觉上停在"10 层那么高"。
     * <p>负数按 0 处理（账本里不该有负数，但纯函数要自洽）。
     */
    public int orbitsFor(int layers) {
        int effectiveLayers = Math.min(Math.max(0, layers), TRUTH_ORBIT_MAX_LAYERS);
        return Math.min(MAX_ORBIT_PARTICLES, effectiveLayers / LAYERS_PER_PARTICLE);
    }

    /** 供子类/探针确认本组件已把依赖挂上（{@code null} 表示 start() 尚未跑）。 */
    RoleComponent dependencyProbe() {
        return energy;
    }

    /** 暴露给单测：真理特效的起始高度（需求 0.6 格）。 */
    static double truthOrbitBaseHeightForTest() {
        return TRUTH_ORBIT_BASE_HEIGHT;
    }

    /** 暴露给单测：每颗抬高量（层数越多环越高）。 */
    static double truthOrbitStepYForTest() {
        return ORBIT_STEP_Y;
    }

    /** 暴露给单测：叠高上限层数（层数到此不再叠高）。 */
    static int truthOrbitMaxLayersForTest() {
        return TRUTH_ORBIT_MAX_LAYERS;
    }

    /** 暴露给单测：多少层画一颗。 */
    static int layersPerParticleForTest() {
        return LAYERS_PER_PARTICLE;
    }
}
