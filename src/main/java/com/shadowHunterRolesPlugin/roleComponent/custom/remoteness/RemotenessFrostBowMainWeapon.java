package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 冷识（{@code remoteness}）的主武器「**冷淡**」：一把弓。
 *
 * <h2>玩法（数值都是常量，要调只改这一处）</h2>
 * <ul>
 *   <li><b>拉弓 / 击发归原版</b>（本族的既有边界：弓弩不挂主武器管道，右键不被取消）；
 *       射出一支箭 ⇒ 启动 {@link #COOLDOWN_TICKS}（2 秒）冷却，冷却中拉不开弓；</li>
 *   <li><b>命中敌人</b>（{@link #onProjectileHit}）：扣除其 {@link #HIT_SANTE_DAMAGE}（9）点特殊值，
 *       并造成 {@link #HIT_PHYSICAL_DAMAGE}（6）点物理伤害；</li>
 *   <li><b>飞行途中</b>（{@link #update}）：箭矢附近 {@link #PROXIMITY_RADIUS}（4）格内的敌人被扣除
 *       {@link #PROXIMITY_SANTE_DAMAGE}（3）点特殊值并受到 {@link #PROXIMITY_TRUE_DAMAGE}（2）点真实伤害
 *       —— **每支箭对每个敌人各一次**（同一支箭、同一个敌人不会重复触发）；</li>
 *   <li><b>击中方块</b>：箭矢**立即被清除**（不留在地上、不可被拾取）；</li>
 *   <li><b>[重构] 窗口内命中</b>：主武器冷却立即刷新（{@link #stopCooldown()}）——
 *       窗口的开合由 {@link RemotenessReconstructSkill} 持有，本类只问它"现在开着吗"。</li>
 * </ul>
 *
 * <h2>★ 原版箭矢伤害照常生效（如实申报的口径）</h2>
 * 本族（{@link BowWeapon}）的立场是"右键与飞行都归原版"，因此本插件**不拦**原版箭矢伤害：
 * 玩家被这一箭打中时，先吃原版伤害，再吃本组件的 9 点特殊值扣除与 6 点物理伤害。
 * 要让声明值**替代**原版伤害，须在射击管道上加一条 {@code EntityDamageByEntityEvent} 拦截
 * （取消 damager 为箭矢的那次伤害），那是一次产品口径变更，本组件不擅自改。
 *
 * <h2>为什么"飞行途中"的判定写在 {@code update()} 里</h2>
 * 箭在离弦之后由原版物理推进，插件只在两个时刻拿到它：离弦（{@code onShoot}）与命中
 * （{@code onProjectileHit}）。"飞行途中附近有谁"这件事**只有逐刻观察**才看得到，而组件侧
 * 逐刻的自然落点就是 {@link #update()}（每个角色实例每 tick 跑一次）。因此本组件在
 * {@link #onShoot} 里把箭记进 {@link #flyingArrows}，在 {@link #update} 里逐刻扫、在
 * {@link #onProjectileHit} 里出账 —— 三处各管一段，账不会记两遍。
 *
 * <h2>为什么"每支箭对每个敌人各一次"用集合表达</h2>
 * 逐刻扫描天然会重复看到同一个敌人（他在半径内待了 5 刻就是 5 次）。要"只触发一次"就得**记状态**，
 * 而这份状态属于"这一支箭"（不是属于角色、也不是属于敌人）⇒ 记在 {@link TrackedArrow} 里，
 * 随箭一起被销毁。这样"一次"是**类型上的事实**，而不是靠"伤害够快结束"这类巧合。
 *
 * <h2>能量维度</h2>
 * {@link BowWeapon} 已把能量规则封成家族不变量（声明耗能 ≡ 0）⇒ 本组件只回声明值，
 * {@code ENERGY_LACK} 态不可达（不得为它造新外观）。
 */
public class RemotenessFrostBowMainWeapon extends BowWeapon {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "remoteness_frost_bow";

    // ───────── 数值（唯一修改点）─────────

    /** 冷却（40 刻 = 2 秒）：射出一次之后要等这么久才能再拉弓。 */
    private static final int COOLDOWN_TICKS = 40;

    /** 命中敌人时扣除的特殊值。 */
    private static final int HIT_SANTE_DAMAGE = 9;

    /** 命中敌人时造成的物理伤害。 */
    private static final int HIT_PHYSICAL_DAMAGE = 6;

    /** 飞行途中的触发半径（格）。 */
    private static final double PROXIMITY_RADIUS = 4d;

    /** 飞行途中对范围内敌人扣除的特殊值。 */
    private static final int PROXIMITY_SANTE_DAMAGE = 3;

    /** 飞行途中对范围内敌人造成的真实伤害。 */
    private static final int PROXIMITY_TRUE_DAMAGE = 2;

    // ───────── 协作组件（start() 里一次取好）─────────

    private SanTEComponent sante;
    private VitalsComponent vitals;
    private BuffComponent buff;

    /** [重构] 技能（"命中刷新冷却"窗口的持有者）；{@code null} = 该角色没装它（退化为不刷新，不抛）。 */
    private RemotenessReconstructSkill reconstruct;

    /**
     * 本组件射出、仍在飞（或至少还没收到命中通知）的箭 —— 键 = 箭矢实体 UUID。
     * <p>顺序 = 射出先后（{@link java.util.LinkedHashMap} 的语义：早射的排在前面，
     * 因此逐刻扫描的顺序可复现）。实例销毁时整表清空（见 {@link #stop()}）。
     */
    private final Map<UUID, TrackedArrow> flyingArrows = new java.util.LinkedHashMap<>();

    public RemotenessFrostBowMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。
     * <p>依赖 = 实取清单；[重构] 是**必需**依赖 —— "命中刷新冷却"这条效果的全部状态都在它那里，
     * 漏声明会让装配期不报错、而运行期静默丢掉这条效果（本仓明令禁止的失败形态）。
     */
    public static final class Specification extends BowWeapon.Specification<RemotenessFrostBowMainWeapon> {

        public Specification() {
            super(Component.text("冷淡"),
                    List.of(
                            Component.text("箭矢命中敌人：扣除其9点特殊值，并造成6点物理伤害"),
                            Component.text("飞行途中：4格内的敌人被扣除3点特殊值与2点真实伤害（每支箭一次）"),
                            Component.text("箭矢击中方块立即被清除"),
                            Component.text("CD 2s"),
                            Component.text("=============================="),
                            Component.text("进化:"),
                            Component.text("1-获得永久跳跃提升2"),
                            Component.text("2-获得永久速度3"),
                            Component.text("3-获得持续生命恢复2"),
                            Component.text("4-塑造对敌人特殊值伤害提高30点,并漂浮时间上升至7秒,CD延长至40s"),
                            Component.text("5-在你身边半径10范围内的敌人会持续扣除特殊值, 1秒2点"),
                            Component.text("6-获得永久抗性1"),
                            Component.text("7-在击杀一名敌人时, 增加自己30sante, 获得6秒的伤害吸收2"),
                            Component.text("8-[塑造]直接清空敌人sante, CD延长至60s"),
                            Component.text("=============================="),
                            Component.text("被动:"),
                            Component.text("始末: 永久速度一,跳跃提升一")
                    ),
                    COOLDOWN_TICKS,
                    Material.BOW);
            requires(SanTEComponent.class).requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(RemotenessReconstructSkill.class)
                    //索敌读阵营组件 ⇒ 缺它则本武器不索敌，装配期就拦住
                    .requires(FactionComponent.class);
        }

        @Override
        public RemotenessFrostBowMainWeapon create(String id, ComponentServicesPort services) {
            return new RemotenessFrostBowMainWeapon(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        sante = svc().components().get(SanTEComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        reconstruct = getComponent(RemotenessReconstructSkill.class);
    }

    /**
     * 停止生效：清掉协作组件引用与**在飞箭的观察表**。
     * <p>为什么**不**把还在飞的箭移除：那些箭是原版的普通箭（不是本插件生成的实体），
     * 玩家已经付出一支箭、也已看到它飞出去 —— 中途"凭空消失"是玩家可见的怪事。
     * 本组件只是**不再观察**它们：它们继续按原版规则飞完、落地。
     * （对比：{@code CangluBlueIceRevolverSkill} 必须把自己的 Marker 子弹收回 —— 那些是它自己生成的实体，
     * 不收回就是实体泄漏。两者口径不同，因为"谁生成了这个实体"不同。）
     */
    @Override
    public void stop() {
        flyingArrows.clear();
        sante = null;
        vitals = null;
        buff = null;
        reconstruct = null;
    }

    // ───────── 入口一：离弦 ─────────

    /**
     * 箭矢离弦：启动冷却（"射出一次"的账在这里记）并把这一支箭纳入逐刻观察。
     *
     * <p>冷却由组件在"动作成功处"自启（框架与 listener 都不代启动）；冷却中"拉不开弓"由
     * {@code BowWeaponListener#onRightClick} 在入口把住 ⇒ 冷却中根本射不出箭，也就不会走到这里。
     */
    @Override
    public void onShoot(ShootSignal signal) {
        startCooldown();
        Projectile projectile = signal.projectile();
        if (projectile != null) {
            flyingArrows.put(projectile.getUniqueId(), new TrackedArrow(projectile));
        }
        svc().self().player().getWorld().playSound(svc().self().player().getEyeLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
    }

    // ───────── 入口二：命中 ─────────

    /**
     * 箭矢命中实体 / 方块：结算命中效果、出账，并执行"击中方块立即清除"。
     *
     * <p>顺序刻意如此（两件事的理由各不同）：
     * <ol>
     *   <li><b>先出账</b>（从观察表里摘掉）：命中之后这支箭不再"在飞"，逐刻扫描不该再看到它；
     *       摘在效果之前，是为了让效果里的任何异常都不会把一条已经过期的观察记录留在表里；</li>
     *   <li><b>再结算命中效果</b>（仅当命中的是**敌对玩家**）；</li>
     *   <li><b>最后处理方块</b>：{@code hitEntity} 与 {@code hitBlock} **可以同时非空**
     *       （箭打在贴着方块的实体上），因此这里**不是**二选一 —— 实体那段先跑，方块这段独立跑。</li>
     * </ol>
     */
    @Override
    public void onProjectileHit(ProjectileHitSignal signal) {
        Player self = selfPlayer();
        if (self == null) {
            return;
        }
        flyingArrows.remove(signal.projectile().getUniqueId());

        //① 命中敌对玩家 ⇒ 扣特殊值 + 物理伤害（+ [重构] 窗口内刷新主武器冷却）
        if (signal.hitEntity() instanceof Player victim
                && !victim.equals(self)
                && victim.isOnline()
                && !victim.isDead()
                && svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) {
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(), HIT_SANTE_DAMAGE);
            }
            if (vitals != null) {
                //物理伤害：走生命组件的原语（它会把自己记为"最后伤害者" ⇒ 由本武器打死的击杀可被判定）
                vitals.physicalDamage(victim, self, HIT_PHYSICAL_DAMAGE);
            }
            if (reconstruct != null && reconstruct.isWindowActive()) {
                stopCooldown();
            }
        }

        //② 击中方块 ⇒ 立即清除这支箭（原版会让它插在地上并留下可拾取的箭矢）
        if (signal.hitBlock() != null) {
            signal.projectile().remove();
        }
    }

    // ───────── 每 tick：飞行途中的半径判定 ─────────

    /**
     * 逐刻扫描在飞的箭：{@link #PROXIMITY_RADIUS} 格内的敌对玩家各被扣特殊值 + 受真实伤害，
     * **每支箭对每个敌人只触发一次**。
     *
     * <p>三种"这支箭已经不用再看了"的情形都在这里出账：① 实体已失效（被清除 / 世界卸载）；
     * ② 已经插在方块上（原版命中方块后的形态 —— 它不再移动，且命中已经结算过）；
     * ③ 收到命中通知（在 {@link #onProjectileHit} 里出的账）。
     * <p>逐刻扫描的范围是**箭矢当前位置**（不是弹道整段）：箭每刻走 1~3 格，而触发半径是 4 格 ⇒
     * 半径足够大以至于不会"跳过"某个敌人（不需要做线段采样）。
     */
    @Override
    public void update() {
        if (flyingArrows.isEmpty()) {
            return;
        }
        Player self = selfPlayer();
        if (self == null) {
            return;
        }

        Iterator<Map.Entry<UUID, TrackedArrow>> iterator = flyingArrows.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedArrow tracked = iterator.next().getValue();
            Projectile projectile = tracked.projectile();
            if (projectile == null || !projectile.isValid() || projectile.isDead()) {
                iterator.remove();
                continue;
            }
            if (projectile instanceof AbstractArrow arrow && arrow.isInBlock()) {
                iterator.remove();
                continue;
            }
            sweepProximity(self, projectile, tracked);
        }
    }

    /** 对一支在飞的箭做一次半径扫描（只取敌对玩家；每个敌人每支箭各一次）。 */
    private void sweepProximity(Player self, Projectile projectile, TrackedArrow tracked) {
        if (sante == null && vitals == null) {
            return;
        }
        Location center = projectile.getLocation();
        if (center.getWorld() == null) {
            return;
        }
        for (Entity entity : center.getWorld().getNearbyEntities(
                center, PROXIMITY_RADIUS, PROXIMITY_RADIUS, PROXIMITY_RADIUS)) {
            if (!(entity instanceof Player victim) || victim.equals(self)) {
                continue;
            }
            if (victim.isDead() || !victim.isOnline()) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) {
                continue;
            }
            //"一次"是类型上的事实：这一支箭已经对这个敌人结过账就跳过（集合随箭一起销毁）
            if (!tracked.proximityProcessed.add(victim.getUniqueId())) {
                continue;
            }
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(), PROXIMITY_SANTE_DAMAGE);
            }
            if (vitals != null) {
                vitals.trueDamage(victim, self, PROXIMITY_TRUE_DAMAGE);
            }
        }
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕（STUN）时不许使用本武器 —— 与主武器侧口径一致。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    //能量维度由 BowWeapon 用 final 封死（声明耗能 ≡ 0）⇒ 本类**不覆写** currentEnergy()：
    //  那个口是家族不变量，覆写会被编译期直接拒绝（本仓口径：漏写/多写要成为编译错误）。

    /**
     * 左键 / Q 的落点（本族只会收到 {@code LEFT_CLICK} 与 {@code DROP}）。
     * <p>本组件**有意不做事**：左键与 Q 在本角色身上没有产品语义（原版行为已被 {@code BowWeaponListener}
     * 取消 —— 不许挖方块、不许把弓丢出去），因此这里只留一个空的、有说明的落点，
     * 而不是"顺手接一个效果"。
     */
    @Override
    public void onCast(CastSignal signal) {
        //有意为空：见方法注释。
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null} —— 两个入口都容忍它）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    /**
     * 一支在飞的箭的观察记录（纯数据）。
     *
     * @param projectile        箭矢本体（原版实体；本组件只观察、不拥有它）
     * @param proximityProcessed 已经因"附近有它"而结过账的敌人 UUID（"每支箭每个敌人各一次"的实现）
     */
    private static final class TrackedArrow {

        private final Projectile projectile;
        private final Set<UUID> proximityProcessed = new HashSet<>();

        private TrackedArrow(Projectile projectile) {
            this.projectile = projectile;
        }

        private Projectile projectile() {
            return projectile;
        }

        private Set<UUID> proximityProcessed() {
            return proximityProcessed;
        }
    }
}
