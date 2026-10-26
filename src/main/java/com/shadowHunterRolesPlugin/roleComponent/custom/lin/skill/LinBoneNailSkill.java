package com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 「林」技能之三：**透骨钉**（下界之星）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>★ 技能<b>转好拿在手上时</b>，改变角色手部动作为<b>持弓动作</b>，
 *       并在其手部勾勒一把<b>白灰为主色调的科幻粒子狙击枪</b>；</li>
 *   <li>直接释放，发射一条<b>每秒前进 30 格</b>的大威力<b>白色射线</b>，伴随螺旋状粒子；</li>
 *   <li><b>不穿透墙壁</b>；</li>
 *   <li>击中一名敌人造成 <b>10 物理伤害</b>后，使其向射线方向<b>继续被推动，在 0.3 秒内位移 8 格</b>；</li>
 *   <li>若位移途中<b>被墙壁阻挡</b> ⇒ 使其<b>无法移动 2 秒、致盲 2 秒、沉默 2 秒</b>
 *       （沉默 = 无法使用技能或者主武器）；</li>
 *   <li>若<b>没有被阻挡</b> ⇒ 仅造成 <b>2 秒的缓慢 IV 和致盲</b>；</li>
 *   <li>CD 25 秒，<b>能量消耗 25</b>。</li>
 * </ol>
 *
 * <h2>★ 口径申报一：射线为什么"瞬间结算"而不是逐刻飞行</h2>
 * "每秒前进 30 格"是<b>速度</b>描述，不是"要玩家看着它飞 2 秒"。
 * 30 格/秒 = 1.5 格/刻，而最大射程 {@value #MAX_RANGE} 格 ⇒ 全程不到 1.6 秒。
 * 逐刻飞行要维护"飞行中的射线"状态机（跨刻、跨死亡、跨换世界都要处理），
 * 换来的只是"玩家能看到一秒钟的光柱" —— 而这条观感用<b>同刻一次性画完</b>
 * （{@link LinVfx#boneNailBeam} 沿整条射线采样）就已经达成。
 * <p>⇒ 本实现：<b>同刻完成"追踪命中点 → 结算伤害 → 画出整条射线"</b>。
 * 命点用 {@link #traceImpact} 按 {@value #TRACE_STEP} 格步进求得，
 * 撞到不透明方块即终止（需求第 3 条"不穿透墙壁"）。
 *
 * <h2>★ 口径申报二："推动 8 格 / 0.3 秒 / 撞墙判定"为什么必须跨刻</h2>
 * 需求第 4 / 5 条要求"位移途中<b>被墙壁阻挡</b>"才转为眩晕，
 * 这是一个<b>过程性</b>判定（要看这 8 格路上有没有墙），不可能在一刻内回答。
 * 因此本技能维护一个短状态机：
 * <ul>
 *   <li>命中点成立后起 {@value #PUSH_TICKS} 刻（0.3 秒）的推动；
 *       每刻把目标沿射线方向推进 {@code PUSH_DISTANCE / PUSH_TICKS} 格；</li>
 *   <li>推进中若<b>下一格不可通行</b> ⇒ 立刻中断，判定为"被阻挡" ⇒ 上 STUN（含沉默/致盲）；</li>
 *   <li>全程没被挡 ⇒ 推动完成后上"缓慢 IV + 致盲"。</li>
 * </ul>
 * <p>★ 推动用 {@code teleport} + 每刻 {@code setFallDistance(0f)}（技能接管了目标位置，
 * 摔落伤害得我们自己吃掉），与工程规范一致。
 *
 * <h2>★ 口径申报三：被推动者为什么用"每刻 teleport"而不是 {@code setVelocity}</h2>
 * {@code setVelocity} 会交给原版物理：受击者能自己反抗（速度被自己输入覆盖）、
 * 会撞地形弹开、会掉进虚空 —— "精确位移 8 格"根本保证不了。
 * 本技能要的是<b>确定性</b>：0.3 秒 8 格、撞墙即停，所以逐刻 teleport 是唯一可控的做法。
 *
 * <h2>★ 口径申报四：粒子狙击枪只在本技能"就绪"时出现</h2>
 * 需求第 1 条限定"技能转好拿在手上时"。判据 = {@code !isCoolingDown() && canUse() && 当前选中槽是本技能}。
 * 做法：每刻在 {@link #update()} 里画 {@link LinVfx#boneNailRifle}，没就绪就不画。
 * ★ 判断"是否选中的就是本技能"用 {@link Skill.Utils#getSkillId(ItemStack)} 读手上物品的识别键，
 * 而不是比较物品名字（名字会随冷却前缀变化）。
 *
 * <h2>★★ 口径申报五：持弓姿势为什么**不能常驻**（2026-10-08 修的真 bug）</h2>
 * 初版把"持弓动作"实现成<b>只要就绪就每刻 {@code owner.startUsingItem(HAND)}</b>
 * —— 结果<b>右键与左键都按不出技能</b>。根因（实测）：
 * <ul>
 *   <li>{@code startUsingItem} 会给玩家挂上"正在使用物品"状态；</li>
 *   <li>而 {@code NETHER_STAR} **没有原版使用行为** ⇒ 服务端与客户端的"使用"状态很快失配，
 *       客户端不再为后续的左右键派发 {@code PlayerInteractEvent}；</li>
 *   <li>⇒ {@code listener/SkillListener} 的两条派发路径（左键 / 右键）**都收不到事件** ⇒
 *       "按了没反应"（且不报错）。</li>
 * </ul>
 * <b>正解</b>：姿势只在<b>释放后的极短脉冲窗口</b>（{@value #POSE_PULSE_TICKS} 刻）里维持 ——
 * 玩家看到的仍是"抬手一枪"的持弓动作，但窗口一过立刻 {@code clearActiveItem()}，
 * 因此**输入永不被长时间占用**。★ 先例 {@code TekTridentMainWeapon} 之所以能常驻，
 * 是因为它是<b>主武器</b>、且姿势由"按住右键"驱动；技能位上做不到同样的假设。
 *
 * <h2>★ 口径申报六：2026-10-08 的观感/手感追加（用户）</h2>
 * <ul>
 *   <li><b>开火音</b>：1.5 倍速烈焰人受击 + 1.5 倍速火焰弹爆炸（{@code ENTITY_DRAGON_FIREBALL_EXPLODE}，
 *       1.21.11 无 {@code ENTITY_FIREBALL_EXPLODE}）；</li>
 *   <li><b>枪口爆炸粒子</b>：射出瞬间在枪口位置发 1 颗 {@code EXPLOSION}（"单个"取字面语义）；</li>
 *   <li><b>后坐力</b>：分 {@value #RECOIL_STEPS} 刻把视角上抬 {@value #RECOIL_TOTAL_PITCH} 度
 *       （{@code teleport} 改 pitch，yaw 原样）；</li>
 *   <li><b>命中追加</b>：白色闪电击铜块粒子（{@code ELECTRIC_SPARK}，1.21.11 无专用"铜块被雷击"粒子）
 *       + 白色穿刺特效（沿入射方向的一根白刺）；</li>
 *   <li><b>钉墙</b>：撞墙瞬间**再次**爆炸粒子，并在 2 秒控制期内每刻画
 *       <b>3 个平面菱形</b>围住敌人身体；</li>
 *   <li><b>就绪提示三连</b>（"当三技能可以释放时"）：1.5 倍速活塞推出 → 0.3 秒后 2 倍速活塞拉回
 *       → 再 0.1 秒后 2 倍速烈焰人受击。★ 用**边沿判据**（{@code wasReady}）触发，否则会每刻复读。</li>
 * </ul>
 */
public class LinBoneNailSkill extends Skill {

    /** **本组件的登记 id**。 */
    public static final String ID = "lin_skill_boneNail";

    /** 射线的最大射程（格）。 */
    public static final double MAX_RANGE = 45.0d;

    /** 射线步进（格）：越小越不容易"穿过一格薄墙"。 */
    public static final double TRACE_STEP = 0.25d;

    /** 命中造成的物理伤害（需求："造成10物理伤害"）。 */
    public static final double HIT_PHYSICAL_DAMAGE = 10.0d;

    /** 命中后的推动距离（格）。 */
    public static final double PUSH_DISTANCE = 8.0d;

    /** 推动时长（刻）：0.3 秒 = 6 刻。 */
    public static final int PUSH_TICKS = 6;

    /** 被阻挡时的无法移动时长（刻）：2 秒。 */
    public static final int BLOCKED_IMMOBILE_TICKS = 40;

    /** 被阻挡时的致盲时长（刻）：2 秒。 */
    public static final int BLOCKED_BLIND_TICKS = 40;

    /** 被阻挡时的沉默时长（刻）：2 秒。 */
    public static final int BLOCKED_SILENCE_TICKS = 40;

    /** 未被阻挡时的缓慢时长（刻）：2 秒，等级 IV（amplifier 3）。 */
    public static final int FREE_SLOW_TICKS = 40;

    /** 未被阻挡时的缓慢等级（IV ⇒ amplifier 3）。 */
    public static final int FREE_SLOW_AMPLIFIER = 3;

    /** 未被阻挡时的致盲时长（刻）：2 秒。 */
    public static final int FREE_BLIND_TICKS = 40;

    /** 声明冷却（刻）：25 秒（需求："CD-25"）。 */
    public static final int COOLDOWN_TICKS = 500;

    /** 能量消耗（需求："能量消耗25"）。 */
    public static final int ENERGY_COST = 25;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private EnergyComponent energy;
    private BuffComponent buff;

    // ───────── 推动状态机（口径申报二）─────────

    /** 正在被推动的目标（{@code null} = 没有推动在进行）。 */
    private LivingEntity pushTarget;

    /** 推动方向（单位向量）。 */
    private Vector pushDirection;

    /** 剩余推动刻数。 */
    private int pushRemaining;

    /** 本次推动是否已经被墙挡住（一旦挡住就按"被阻挡"结算）。 */
    private boolean pushBlocked;

    /** 推动结束后要施加的效果（缓存到推动结束再上，避免"边推边减速"）。 */
    private boolean pushPendingBlockedEffects;

    // ───────── 持弓姿势（★ 见类注释口径申报五：为什么不是"一直举着"）─────────

    /**
     * **持弓姿势的维持截止刻**（绝对刻；{@code -1} = 当前没有姿势）。
     *
     * <p>★ 姿势只在<b>释放后的极短窗口</b>里维持（给出"端枪开火"的一帧观感），
     * 而**不是**"只要就绪就一直举着" —— 见 {@link #POSE_PULSE_TICKS} 与类注释口径申报五。
     */
    private int poseUntilTick = -1;

    /** 姿势脉冲时长（刻）：只够"看一眼抬枪"，不会挡住玩家的左/右键输入。 */
    private static final int POSE_PULSE_TICKS = 3;

    // ───────── 后坐力（★ 需求 2026-10-08："微微平滑快速上抬视角模拟后坐力"）─────────

    /**
     * **后坐力的"剩余抬升量"**（单位：度）。
     *
     * <p>做法 = 分若干小步把视角往上抬，每刻抬一点 ⇒ "平滑快速"。
     * 用 {@code teleport} 改视角（{@code Player#setRotation} 不存在，
     * 只能用 {@code teleport(Location)} 把 yaw/pitch 一起写回去）。
     */
    private float recoilRemainingPitch;

    /** 后坐力总抬升量（度）：小到"微微"，但能感觉到。 */
    private static final float RECOIL_TOTAL_PITCH = 6.0f;

    /** 后坐力分几步抬完（刻）：2 刻 ≈ 0.1 秒 ⇒ "快速"。 */
    private static final int RECOIL_STEPS = 2;

    /** 单步抬升量（度）。 */
    private static final float RECOIL_STEP_PITCH = RECOIL_TOTAL_PITCH / RECOIL_STEPS;

    // ───────── 就绪提示三连（★ 需求 2026-10-08）─────────

    /**
     * **"技能刚转好"的边沿判据上一帧值**。
     *
     * <p>★ 必须存上一帧：直接用 {@code isReadyHeld()} 当条件会**每刻都放一遍**提示
     * （那是复读机，不是"刚转好"的信号）。边沿判据 = "上一帧没就绪 且 这一帧就绪"。
     */
    private boolean wasReady;

    /** 就绪三连里"第 2 声（活塞拉回）"的到期刻（{@code -1} = 无待播）。 */
    private int readyContractAtTick = -1;

    /** 就绪三连里"第 3 声（烈焰人受击）"的到期刻（{@code -1} = 无待播）。 */
    private int readyBlazeAtTick = -1;

    // ───────── 钉墙控制期（★ 需求 2026-10-08："钉墙期间 3 个平面菱形围住敌人"）─────────

    /** 被钉墙的敌人（{@code null} = 当前没有"钉住"状态）。 */
    private LivingEntity pinnedTarget;

    /** 钉住的剩余刻数（> 0 ⇒ 每刻画菱形围笼）。 */
    private int pinnedRemaining;

    public LinBoneNailSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<LinBoneNailSkill> {

        public Specification() {
            super(Component.text("透骨钉"),
                    List.of(
                            Component.text("就绪持握时手部勾勒白灰科幻狙击枪，开火瞬间转为持弓动作"),
                            Component.text("释放一条每秒前进30格的白色螺旋射线，不穿透墙壁"),
                            Component.text("命中造成10点物理伤害，并将目标沿射线方向推动8格（0.3秒）"),
                            Component.text("若被墙壁阻挡：无法移动2秒、致盲2秒、沉默2秒"),
                            Component.text("若未被阻挡：缓慢IV与致盲，各2秒"),
                            Component.text("冷却：25秒；能量消耗：25")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.NETHER_STAR);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class);
        }

        @Override
        public LinBoneNailSkill create(String id, ComponentServicesPort services) {
            return new LinBoneNailSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        clearPush();
        clearPin();
        recoilRemainingPitch = 0f;
        // ★ 起始值取"当前是否就绪"：否则组件刚装上的第一刻会被判成"刚转好"，
        //   平白在选角瞬间放一遍就绪三连。
        Player owner = svc().self().player();
        wasReady = owner != null && isReadyHeld(owner);
        readyContractAtTick = -1;
        readyBlazeAtTick = -1;
    }

    /**
     * 停用：收回持弓动作 + 清推动 / 钉墙 / 后坐力状态。
     * <p>★ 不清推动状态会让被推的实体在换角色后继续被 teleport（技能已死、状态还活着）。
     */
    @Override
    public void stop() {
        Player owner = svc().self().player();
        if (owner != null && owner.isHandRaised()) {
            owner.clearActiveItem();
        }
        poseUntilTick = -1;
        clearPush();
        clearPin();
        recoilRemainingPitch = 0f;
        wasReady = false;
        readyContractAtTick = -1;
        readyBlazeAtTick = -1;
    }

    /**
     * 每刻：① 后坐力步进；② 就绪三连的延时声；③ 钉墙围笼；④ 推动状态机；
     * ⑤ 姿势脉冲到期收回；⑥ 就绪时画手部狙击枪。
     *
     * ★ **本方法绝不常驻"使用物品"状态** —— 见类注释口径申报五。
     */
    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            clearPush();
            clearPin();
            poseUntilTick = -1;
            recoilRemainingPitch = 0f;
            wasReady = false;
            readyContractAtTick = -1;
            readyBlazeAtTick = -1;
            return;
        }
        int now = Bukkit.getCurrentTick();

        // ① 后坐力：每刻抬一点，抬完即止（"微微平滑快速上抬视角"）
        advanceRecoil(owner);

        // ② 就绪三连的延时声（第 1 声已在边沿检测处播了）——
        //    ★ 用"到期刻"而不是 schedule 任务：组件停用/玩家下线时天然作废，不需要额外取消。
        if (readyContractAtTick >= 0 && now >= readyContractAtTick) {
            readyContractAtTick = -1;
            LinSound.boneNailReadyContractSound(owner.getWorld(), owner.getLocation());
        }
        if (readyBlazeAtTick >= 0 && now >= readyBlazeAtTick) {
            readyBlazeAtTick = -1;
            LinSound.boneNailReadyBlazeSound(owner.getWorld(), owner.getLocation());
        }

        // ③ 就绪边沿：上一帧没就绪、这一帧就绪 ⇒ 放提示三连（"当三技能可以释放时"）
        boolean ready = isReadyHeld(owner);
        if (ready && !wasReady) {
            LinSound.boneNailReadyShiftSound(owner.getWorld(), owner.getLocation());
            readyContractAtTick = now + LinSound.READY_CONTRACT_DELAY_TICKS;
            readyBlazeAtTick = now + LinSound.READY_BLAZE_DELAY_TICKS;
        }
        wasReady = ready;

        // ④ 钉墙围笼：钉住期间每刻画 3 个平面菱形（"围住敌人身体"）
        advancePin();

        // ⑤ 推动（优先于外观：推动期间不动手枪，避免两件事抢同一帧）
        if (pushRemaining > 0) {
            advancePush();
            return;
        }

        // ⑥ 姿势脉冲到期 ⇒ 收回（自己停，不依赖下一次状态变化）
        if (poseUntilTick >= 0 && now >= poseUntilTick) {
            poseUntilTick = -1;
            if (owner.isHandRaised()) {
                owner.clearActiveItem();
            }
        }

        // ⑦ 就绪 ⇒ 只画手部狙击枪（**不动 "使用物品" 状态** ⇒ 不挡左/右键）
        if (ready) {
            World world = owner.getWorld();
            if (world != null) {
                LinVfx.boneNailRifle(world, owner.getEyeLocation(), owner.getEyeLocation().getDirection());
            }
        }
    }

    /**
     * **后坐力步进**：把剩余的抬升量按 {@link #RECOIL_STEP_PITCH} 抬掉，最多 {@link #RECOIL_STEPS} 步。
     *
     * <p>★ 为什么用 {@code teleport}：Bukkit 的 {@code Player} 没有 {@code setRotation}，
     * 改视角的唯一手段是 {@code teleport(Location)} 把 yaw/pitch 写回去。
     * 这里**只改 pitch**，yaw 原样带回 ⇒ 玩家水平朝向不受影响。
     */
    private void advanceRecoil(Player owner) {
        if (recoilRemainingPitch <= 0f) {
            return;
        }
        float step = Math.min(RECOIL_STEP_PITCH, recoilRemainingPitch);
        recoilRemainingPitch -= step;
        Location loc = owner.getLocation();
        // 抬头 = pitch 减小（原版：pitch -90 看天、+90 看地）
        float newPitch = clampPitch(loc.getPitch() - step);
        Location rotated = loc.clone();
        rotated.setPitch(newPitch);
        owner.teleport(rotated);
    }

    /**
     * 把 pitch 夹在合法区间（原版允许 -90 ~ +90）。
     *
     * <p>★ 公开为**纯函数口**（供离线测试穷举边界）：后坐力必须走这里，
     * 否则向天开枪时会写出 -95° 这类非法值，客户端夹回后观感是"抬到一半又掉回来"。
     */
    public static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    /** 释放：追踪命中点 → 结算伤害与推动 → 画整条射线。 */
    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        if (energy == null || energy.current() < ENERGY_COST) {
            return;
        }

        Location eye = owner.getEyeLocation();
        Vector direction = eye.getDirection().clone();
        if (direction.lengthSquared() < 1.0E-9d) {
            return;
        }
        direction.normalize();

        // ★ 能量消耗只在"真的要发射"时扣
        energy.decrease(ENERGY_COST);

        // ① 追踪命中点（不穿墙）
        Impact impact = traceImpact(eye, direction, MAX_RANGE, owner.getUniqueId());
        Location end = impact.point();

        // ② 开火观感 + 整条射线（同刻一次画完 —— 口径申报一）
        //    2026-10-08 追加：1.5 倍速烈焰人受击 + 1.5 倍速火焰弹爆炸音；
        //    枪口位置单个爆炸粒子；微微平滑快速上抬视角（后坐力）。
        LinSound.boneNailShotSound(world, eye);
        LinVfx.boneNailMuzzleFlash(world, eye, direction);
        LinVfx.boneNailBeam(world, eye, end, 0d);
        startRecoil();

        // ③ 命中敌人 ⇒ 伤害 + 穿刺/电火花观感 + 起推动
        if (impact.victim() instanceof LivingEntity living) {
            if (vitals != null) {
                vitals.physicalDamage(living, owner, HIT_PHYSICAL_DAMAGE);
            }
            Location body = living.getLocation().clone().add(0d, 1d, 0d);
            LinVfx.boneNailImpact(world, body);
            // 2026-10-08 追加：白色闪电击铜块粒子 + 白色穿刺特效
            LinVfx.boneNailCopperSpark(world, body);
            LinVfx.boneNailPierce(world, body, direction);
            LinSound.boneNailHitSound(world, living.getLocation());
            beginPush(living, direction);
        }

        // ④ ★ 姿势脉冲：释放这一刻给出"端枪开火"的持弓动作，**只维持 POSE_PULSE_TICKS 刻**
        //     —— 不常驻（常驻会挡住玩家的左/右键输入，见类注释口径申报五）。
        owner.startUsingItem(EquipmentSlot.HAND);
        poseUntilTick = Bukkit.getCurrentTick() + POSE_PULSE_TICKS;

        startCooldown();
    }

    /** 起一次后坐力：按总抬升量分 {@link #RECOIL_STEPS} 步，由 {@link #update()} 逐刻抬完。 */
    private void startRecoil() {
        recoilRemainingPitch = RECOIL_TOTAL_PITCH;
    }

    // ───────── 推动状态机 ─────────

    /**
     * 起一次推动：记录目标与方向。
     * <p>★ 重复命中同一目标：刷新推动（不叠两段），避免"8 格推完又被推 8 格"。
     */
    private void beginPush(LivingEntity target, Vector direction) {
        pushTarget = target;
        pushDirection = direction.clone();
        pushRemaining = PUSH_TICKS;
        pushBlocked = false;
        pushPendingBlockedEffects = true;
    }

    /** 清空推动状态（中止 / 结束 / 换角色）。 */
    private void clearPush() {
        pushTarget = null;
        pushDirection = null;
        pushRemaining = 0;
        pushBlocked = false;
        pushPendingBlockedEffects = false;
    }

    /**
     * **推进一帧推动**（口径申报二 / 三）：
     * ① 目标失效 ⇒ 直接收工（不当成"被阻挡"，什么都不上）；
     * ② 下一格不可通行 ⇒ 标记被阻挡并立即收工 ⇒ 上"无法移动 + 致盲 + 沉默"；
     * ③ 正常推进一格；推到最后一刻 ⇒ 未被阻挡 ⇒ 上"缓慢 IV + 致盲"。
     */
    private void advancePush() {
        LivingEntity target = pushTarget;
        if (target == null || target.isDead() || !target.isValid() || pushDirection == null) {
            clearPush();
            return;
        }
        World world = target.getWorld();
        if (world == null) {
            clearPush();
            return;
        }

        double step = PUSH_DISTANCE / PUSH_TICKS;
        Location from = target.getLocation();
        Location next = from.clone().add(pushDirection.clone().multiply(step));

        // ② 撞墙 ⇒ 立即中断（= "被钉到墙面上"）
        if (!isPassable(next)) {
            pushRemaining = 0;
            applyBlockedEffects(target);
            LinSound.boneNailWallStopSound(world, from);
            // 2026-10-08 追加：钉墙瞬间**再次播放爆炸粒子**，并进入"钉住"状态（每刻画菱形围笼）
            LinVfx.boneNailPinBurst(world, target.getLocation().clone().add(0d, 1d, 0d));
            beginPin(target);
            clearPush();
            return;
        }

        // ③ 推进
        target.teleport(next);
        target.setFallDistance(0f);
        pushRemaining--;

        if (pushRemaining <= 0) {
            // 全程没有被挡 ⇒ 只上"缓慢 IV + 致盲"
            applyFreeEffects(target);
            clearPush();
        }
    }

    /** 被阻挡的结算（需求第 5 条）：无法移动 2 秒 + 致盲 2 秒 + 沉默 2 秒。 */
    private void applyBlockedEffects(LivingEntity target) {
        if (buff == null || !(target instanceof Player player)) {
            // 非玩家目标：沉默 / STUN 是"技能门"，对它们没有意义 ⇒ 仍给原版形态
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    BLOCKED_IMMOBILE_TICKS, 6, true, false, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    BLOCKED_BLIND_TICKS, 0, true, false, false));
            return;
        }
        // 玩家：技能门 / 主武器门都在 STUN 里 ⇒ 一条 STUN 同时表达"无法移动 + 沉默"
        // ★ 用跨玩家 API（BuffComponent#addTo），不是只作用于自己的 add(...)
        buff.addTo(player, BuffType.STUN, BLOCKED_IMMOBILE_TICKS);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                BLOCKED_BLIND_TICKS, 0, true, false, false));
    }

    /** 未被阻挡的结算（需求第 6 条）：缓慢 IV + 致盲，各 2 秒。 */
    private void applyFreeEffects(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                FREE_SLOW_TICKS, FREE_SLOW_AMPLIFIER, true, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                FREE_BLIND_TICKS, 0, true, false, false));
    }

    // ───────── 钉墙状态机（★ 需求 2026-10-08：钉墙期间 3 个平面菱形围住敌人）─────────

    /**
     * **进入"钉住"状态**：在 {@link #BLOCKED_IMMOBILE_TICKS} 刻内，每刻在敌人身上画
     * 3 个平面菱形围笼（{@link LinVfx#boneNailPinDiamonds}）。
     *
     * <p>时长与"无法移动 2 秒"对齐 —— 围笼的存在期恰好就是控制期，
     * 控制一结束围笼立刻消失（不会留一个"空笼子"）。
     */
    private void beginPin(LivingEntity target) {
        pinnedTarget = target;
        pinnedRemaining = BLOCKED_IMMOBILE_TICKS;
    }

    /** 清空钉住状态（结束 / 中止 / 换角色）。 */
    private void clearPin() {
        pinnedTarget = null;
        pinnedRemaining = 0;
    }

    /**
     * **推进一帧钉住围笼**：目标失效或时长走完 ⇒ 收工；否则每刻画 3 个菱形。
     */
    private void advancePin() {
        if (pinnedRemaining <= 0) {
            return;
        }
        LivingEntity target = pinnedTarget;
        if (target == null || target.isDead() || !target.isValid()) {
            clearPin();
            return;
        }
        World world = target.getWorld();
        if (world == null) {
            clearPin();
            return;
        }
        // 相位由剩余刻推进 ⇒ 菱形缓慢旋转 / 呼吸（不做自转位图，菱形是几何线条，自转安全）
        double phase = pinnedRemaining * 0.08d;
        LinVfx.boneNailPinDiamonds(world, target.getLocation().clone().add(0d, 1d, 0d), phase);
        pinnedRemaining--;
        if (pinnedRemaining <= 0) {
            clearPin();
        }
    }

    // ───────── 射线追踪 ─────────

    /** 一条射线的追踪结果：命中点 + 命中的实体（没命中 ⇒ {@code null}）。 */
    public record Impact(Location point, Entity victim) { }

    /**
     * ★ **沿射线追踪到命中点**（**纯几何逻辑**，可离线穷举）：逐 {@value #TRACE_STEP} 格前进，
     * 命中第一个敌对可击实体的包围盒 ⇒ 返回它；否则撞到不透明方块 ⇒ 停在方块前；都没碰到 ⇒ 满射程。
     *
     * @param origin  起点（眼睛）
     * @param dir     方向（内部归一化）
     * @param range   最大射程（格）
     * @param shooter 施放者（跳过自己）
     */
    public Impact traceImpact(Location origin, Vector dir, double range, java.util.UUID shooter) {
        if (origin == null || origin.getWorld() == null || dir == null) {
            return new Impact(origin, null);
        }
        Vector unit = dir.clone();
        if (unit.lengthSquared() < 1.0E-9d) {
            return new Impact(origin, null);
        }
        unit.normalize();
        World world = origin.getWorld();
        Location last = origin.clone();
        for (double t = 0d; t <= range + 1.0E-6d; t += TRACE_STEP) {
            Location point = origin.clone().add(unit.clone().multiply(t));
            // 先判实体（实体可能站在方块边界上 ⇒ 实体优先）
            Entity victim = firstEntityAt(world, point, shooter);
            if (victim != null) {
                return new Impact(point, victim);
            }
            // 再判方块
            if (!isPassable(point)) {
                return new Impact(last, null);
            }
            last = point;
        }
        return new Impact(origin.clone().add(unit.clone().multiply(range)), null);
    }

    /** 该点附近是否有"能被射线打中"的实体（排除施放者自己）。 */
    private Entity firstEntityAt(World world, Location point, java.util.UUID shooter) {
        if (world == null) {
            return null;
        }
        for (Entity entity : world.getNearbyEntities(point, 0.6d, 0.9d, 0.6d)) {
            if (!(entity instanceof LivingEntity) || entity.isDead() || !entity.isValid()) {
                continue;
            }
            if (shooter != null && entity.getUniqueId().equals(shooter)) {
                continue;
            }
            return entity;
        }
        return null;
    }

    /** 该位置能否穿过（脚那格与头那格都得可通过）。 */
    private static boolean isPassable(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block head = at.clone().add(0d, 1d, 0d).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    // ───────── 就绪 / 持握判据 ─────────

    /** 当前手上选中的是不是本技能，且技能已就绪。 */
    private boolean isReadyHeld(Player owner) {
        if (owner == null || isCoolingDown() || !canUse()) {
            return false;
        }
        ItemStack main = owner.getInventory().getItemInMainHand();
        return main != null && getId().equals(Skill.Utils.getSkillId(main));
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    // ───────── 外观 ─────────

    /**
     * 基类三态画法 + 本技能自己的状态表达（工程规范第 1 / 4 条）。
     *
     * <p>★ 先 {@code super.buildItem()} —— 识别键写在基类那一步。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        // 就绪（冷却已走完、能量也够）⇒ 常亮光效（配合"手上的狙击枪"一眼看出可放）
        if (!isCoolingDown() && canUse() && currentEnergy() >= getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
            return stack;
        }
        // 只差能量（冷却已走完、其它条件都满足）⇒ 结构空位也发光，与"冷却中"区分开
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ───────── 读口 / 探针 ─────────

    /** 当前是否正在推动某个目标。 */
    public boolean pushing() {
        return pushRemaining > 0;
    }

    /** 剩余推动刻数。 */
    public int pushRemainingTicks() {
        return Math.max(0, pushRemaining);
    }

    /** 当前 tick（探针口径）。 */
    static int currentTick() {
        return Bukkit.getCurrentTick();
    }
}
