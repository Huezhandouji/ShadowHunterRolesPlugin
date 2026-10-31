package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 冷识（{@code lethe}）的被动「**如影随形**」：角色的**常驻增益**与**侵蚀光环**。
 *
 * <h2>职责一：永久跳跃提升 II、永久速度 II</h2>
 * 两条增益的增幅都是 {@code 1}（原版"跳跃提升 II / 速度 II"= amplifier 1），
 * 走 {@link BuffComponent#applyPotionEffect(PotionEffectType, int, int, boolean, boolean)} 施加，
 * 在 {@link #update()} 里按固定间隔刷新续期，{@link #stop()} 时摘除。
 *
 * <h2>★ 为什么"永久增益"要周期性续期，而不是施加一次了事</h2>
 * 原版在玩家**死亡 / 重生**时会清空身上全部药水效果，且角色实例在死亡时就被回收
 * （重生后要重新选角色才再 {@code start()}）。因此"进场给一次无限时长"的写法会在第一次死亡后失效
 * —— 用 {@code INFINITE_DURATION} 也救不了（它同样在死亡时被清空）。
 * 所以采用"**短时长 + 低频刷新**"：每次给 {@link #BUFF_DURATION_TICKS}（60 刻 = 3 秒），
 * 每 {@link #BUFF_REFRESH_INTERVAL_TICKS}（20 刻 = 1 秒）刷新一次 —— 时长是间隔的 3 倍，
 * 即使服务器卡掉一两拍也不会出现可见的闪断。
 *
 * <h2>★ 为什么药水走 {@link BuffComponent}，且 {@link #stop()} 还要再摘一次</h2>
 * buff 组件是药水账本的唯一持有者：走它的 {@code applyPotionEffect} / {@code removePotionEffect}，
 * "施加"与"账本"永远成对（直接写平台会在账本里留下陈旧项）。
 * 本类在 {@code stop()} 里显式摘掉这两条：需求要求"停止时移除"，而角色清除时本组件的回收时机
 * 不保证晚于 buff 组件 —— 谁修改谁还原，重复摘除是幂等的（{@code removePotionEffect} 对不在账本里的
 * 类型只做一次平台移除）。
 *
 * <h2>职责二：侵蚀光环（半径 12，每 3 秒 1 点特殊值）</h2>
 * 冷识身边半径 {@link #AURA_RADIUS} 格内的**其它角色玩家**（有角色实例的玩家），
 * 每 {@link #AURA_INTERVAL_TICKS}（60 刻 = 3 秒）被 {@code sante.decreaseSanTE(uuid, 1)} 扣 1 点特殊值，
 * 并在每一拍于冷识脚下播一次 {@link LetheVfx#auraTick(World, Location)}。
 * <p><b>口径申报一（只伤敌人）：同阵营 / 友军不受影响。</b>产品口径明确「光环对同阵营的友军没有伤害」
 * ⇒ 只有**被冷识视为敌对**的角色玩家才会被侵蚀（判据 = {@link FactionComponent#isHostileTo(java.util.UUID)}，
 * 与全仓索敌同一入口），同阵营（含自己）一律跳过。
 * 因此本类**必需**声明 {@link FactionComponent} 依赖：取不到时整拍跳过 —— 宁可这一拍不生效，
 * 也绝不误伤友军。
 * <p><b>口径申报二（"有角色实例"如何落实）。</b>本类不自己解析对方实例：跨实例入口
 * {@link SanTEComponent#decreaseSanTE(java.util.UUID, int)} 内部按目标 UUID 取对方的 SanTE 组件，
 * 对方**没有角色实例 / 没有 SanTE 组件时返回 0、不产生任何变更** ⇒ "只影响角色玩家"是这条入口的既有语义，
 * 本类不复制第二份判定。
 * <p><b>口径申报三（为什么几何判定放在 update 的节拍里）。</b>本组件已由框架每刻调用，
 * 再注册一个 Bukkit 调度器会多出一条生命周期（还得自己防守玩家下线），因此光环复用同一条节拍，
 * 用两个计数把"每秒刷新增益"与"每 3 秒扣特殊值"分开。
 */
public class LetheShadowsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "lethe_shadows_passive";

    // ───────── 常驻增益（唯一修改点）─────────

    /** 跳跃提升的增幅：需求"跳跃提升 II" ⇒ 原版增幅 1。 */
    private static final int JUMP_AMPLIFIER = 1;

    /** 速度的增幅：需求"速度 II" ⇒ 原版增幅 1。 */
    private static final int SPEED_AMPLIFIER = 1;

    /** 每次刷新给的药水时长（60 刻 = 3 秒 = 刷新间隔的 3 倍，见类注释）。 */
    private static final int BUFF_DURATION_TICKS = 60;

    /** 常驻增益的刷新间隔（20 刻 = 1 秒）。 */
    private static final int BUFF_REFRESH_INTERVAL_TICKS = 20;

    // ───────── 侵蚀光环 ─────────

    /** 光环半径（格）：需求 r = 12。 */
    private static final double AURA_RADIUS = 12d;

    /** 光环结算间隔（60 刻 = 3 秒）：需求"每 3 秒"。 */
    private static final int AURA_INTERVAL_TICKS = 60;

    /** 光环每拍扣除的特殊值：需求"减少 1 点"。 */
    private static final int AURA_SANTE_DRAIN = 1;

    // ───────── 协作组件（start() 里一次取好）─────────

    /** 药水账本（常驻增益的施加 / 移除都走它）。 */
    private BuffComponent buff;

    /** 特殊值组件的跨实例入口（扣**别人**的特殊值，不是自己的）。 */
    private SanTEComponent sante;

    /** 阵营组件：光环**只伤敌人**的判据来源（同阵营 / 友军跳过，见类注释口径申报一）。 */
    private FactionComponent faction;

    // ───────── 两条节拍的计数（本组件内累计，不用调度器）─────────

    private int buffRefreshCountdown;
    private int auraCountdown;

    public LetheShadowsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符：被动经统一 {@code addComponent} 入口装配且无栏位，因此天然不占热键栏。
     * <p>依赖 = 实取清单（{@code start()} 里的两个调用点）；两者都是角色必有组件，缺了应装配期就报错，
     * 而不是运行期静默退化成"没有增益 / 没有光环"。
     */
    public static final class Specification extends PassiveSkill.Specification<LetheShadowsPassive> {

        public Specification() {
            super(Component.text("如影随形"),
                    List.of(
                            Component.text("永久跳跃提升 II、永久速度 II"),
                            Component.text("身边半径 12 格内的敌对角色玩家，每 3 秒被扣除 1 点特殊值"),
                            Component.text("光环只伤敌人：同阵营 / 友军不受影响")
                    ));
            requires(BuffComponent.class).requires(SanTEComponent.class).requires(FactionComponent.class);
        }

        @Override
        public LetheShadowsPassive create(String id, ComponentServicesPort services) {
            return new LetheShadowsPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 开始生效：取两个协作组件，并**立刻**施加一次常驻增益
     * （不等第一个刷新节拍 —— 否则选完角色会有一秒的"裸状态"）。
     */
    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        faction = svc().components().get(FactionComponent.class);
        buffRefreshCountdown = 0;
        auraCountdown = 0;
        if (selfPlayer() != null) {
            refreshPermanentBuffs();
        }
    }

    /**
     * 停止生效：摘掉本类施加的两条常驻增益，清掉协作组件引用与两个节拍计数。
     * <p>摘药水放在清引用之前（{@code buff.removePotionEffect} 要用到 buff 字段）；
     * 玩家已下线时只清引用（药水随会话消失，不需要也不能再操作离线玩家）。
     */
    @Override
    public void stop() {
        if (selfPlayer() != null && buff != null) {
            buff.removePotionEffect(PotionEffectType.JUMP_BOOST);
            buff.removePotionEffect(PotionEffectType.SPEED);
        }
        buff = null;
        sante = null;
        faction = null;
        buffRefreshCountdown = 0;
        auraCountdown = 0;
    }

    // ───────── 每刻：两条节拍（1 秒刷增益 / 3 秒侵蚀光环）─────────

    @Override
    public void update() {
        Player self = selfPlayer();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }
        if (++buffRefreshCountdown >= BUFF_REFRESH_INTERVAL_TICKS) {
            buffRefreshCountdown = 0;
            refreshPermanentBuffs();
        }
        if (++auraCountdown >= AURA_INTERVAL_TICKS) {
            auraCountdown = 0;
            pulseAura(self);
        }
    }

    // ───────── 常驻增益 ─────────

    /**
     * 覆盖式刷新两条常驻增益。
     * <p>{@code ambient = true}（信标式光晕）、{@code particles = false}（"永久"的增益不该一直冒药水泡）。
     */
    private void refreshPermanentBuffs() {
        if (buff == null) {
            return;
        }
        buff.applyPotionEffect(PotionEffectType.JUMP_BOOST, BUFF_DURATION_TICKS, JUMP_AMPLIFIER, true, false);
        buff.applyPotionEffect(PotionEffectType.SPEED, BUFF_DURATION_TICKS, SPEED_AMPLIFIER, true, false);
    }

    // ───────── 侵蚀光环 ─────────

    /**
     * 一拍光环：先播光环心跳，再逐个侵蚀半径内的其它角色玩家。
     *
     * <p>三条边界：
     * <ol>
     *   <li><b>自己排除</b>：{@code victim.equals(self)} 直接跳过（自己的特殊值不受自己光环影响）；</li>
     *   <li><b>球形而不是立方体</b>：{@code getNearbyEntities} 的取件范围是包围盒，
     *       这里再用 {@code distanceSquared} 收成真正的 r=12 球（需求写的是半径）；</li>
     *   <li><b>只伤敌人</b>：{@code faction.isHostileTo(uuid)} 为 false（同阵营 / 友军）一律跳过；
     *       取不到阵营组件时整拍跳过（见类注释口径申报一：宁可不生效，也不误伤友军）。</li>
     * </ol>
     */
    private void pulseAura(Player self) {
        if (sante == null || faction == null) {
            return;
        }
        World world = self.getWorld();
        Location center = self.getLocation();
        // 光环是"角色状态"而不是"命中反馈"：这一拍没扣到人也照播一次心跳，玩家才看得见光环还在
        LetheVfx.auraTick(world, center);

        double radiusSquared = AURA_RADIUS * AURA_RADIUS;
        for (Entity entity : self.getNearbyEntities(AURA_RADIUS, AURA_RADIUS, AURA_RADIUS)) {
            if (!(entity instanceof Player victim) || victim.equals(self)) {
                continue;
            }
            if (victim.isDead() || !victim.isOnline()) {
                continue;
            }
            if (victim.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            // 只伤敌人：同阵营 / 友军不受光环影响（见类注释口径申报一）
            if (!faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            // 跨实例入口：对方无角色实例 / 无特殊值组件时它自己返回 0，本类不复制第二份判定
            sante.decreaseSanTE(victim.getUniqueId(), AURA_SANTE_DRAIN);
        }
    }

    // ───────── 内部工具 ─────────

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}，三处使用点都容忍它）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }
}
