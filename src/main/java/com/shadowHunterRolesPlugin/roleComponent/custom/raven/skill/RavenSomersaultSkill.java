package com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenAirMobilityPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenFloatingTextComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

/**
 * 「林晨霜」技能之三：**涅斯捷罗夫筋斗**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>地面状态</b>：0.2 秒内<b>垂直跃起 12 格</b>（身下 1.5 格内需有方块），随后高速俯冲直到坠向地面；
 *       <b>浮空机动状态中使用</b>则不再跃起，伤害 <b>25 点</b>、眩晕 <b>2 秒</b>，且与落地冲击一同触发；</li>
 *   <li><b>手持该技能</b>时给出<b>落地区域标记</b>（圈 + 叉，与俯冲目标同源）；</li>
 *   <li>落地对周围 <b>r=7</b> 敌人造成 <b>15 点物理伤害</b>、<b>4~7 点特殊值伤害</b>、
 *       <b>1.5 秒眩晕</b>并<b>击飞</b>；</li>
 *   <li>使用后清除浮空；CD <b>40 秒</b>。</li>
 * </ol>
 *
 * <h2>★★★ 落点算法（2026-10-11 第三版 · 用户逐句给定的规则）</h2>
 * <b>① 抽象</b>：以玩家<b>头部</b>射出光线，长度 {@value #RAY_LENGTH} 格，沿视线方向**逐格扫描**：
 * <ol>
 *   <li>射线上的方块若<b>人物可穿越</b> ⇒ 射线**穿过**（继续往前走）；</li>
 *   <li>遇到<b>不可穿越</b>的方块 ⇒ 依次判两件事：
 *     <ol type="a">
 *       <li><b>它上方两格有没有不可穿越方块？</b>（即"有没有 2 格净空"）
 *           —— 有 ⇒ 这一列**站不下人**，不成落点，**继续沿射线找**；</li>
 *       <li>没有 ⇒ 再判<b>它的高度是否 ≥ （跃起后的高度 − {@value #LANDING_CLEARANCE}）</b>？
 *           —— 是 ⇒ **太高**（贴着飞行高度，砸上去只会撞墙侧），**继续沿射线找**；
 *           <b>不是</b> ⇒ ★ <b>以该方块上方作为突进目标终点</b>（就是"落在它顶上"）。</li>
 *     </ol>
 *   </li>
 *   <li>★ <b>扫完整条射线都没有合格落点</b>（包括"全是可穿越方块"这一情形）⇒ 回退：
 *       以**射线末端**作垂线**垂直下坠**，直到遇到不可穿越方块，取该<b>垂足</b>为突进目标终点；
 *       但**垂足不可高于自己 {@value #MAX_FOOT_ABOVE_SELF} 格**。</li>
 * </ol>
 *
 * <h3>"跃起后的高度"取多少</h3>
 * 需求括注写明：<b>浮空状态就是当前高度</b>；地面状态则是"跃起之后"的高度
 * ⇒ {@code apexY = 当前 Y + (非浮空 ? }{@value #RISE_HEIGHT}{@code  : 0)}，
 * 由纯函数 {@link #apexYFor(double, boolean)} 唯一给出。
 *
 * <h3>几处需求没写死、由本实现补上的口径（如实申报）</h3>
 * <ul>
 *   <li><b>① 与 ② 都不满足时</b>：需求只说"进行下一步判定"，没说失败怎么办
 *       ⇒ 本实现把这一列**判为不可用并继续沿射线找**（而不是就地停下）——
 *       否则"一堵没有净空的墙"会把射线直接截断，玩家永远砸不到墙后面的地面。</li>
 *   <li><b>垂足高于自己 6 格时</b>：需求只说"不可高于"，没说违反怎么办
 *       ⇒ 本实现退化为"飞到射线末端、与自己同高度"，随后由既有的"到位后继续垂直下坠"收尾
 *       （绝不把落点定在自己头顶的悬崖上）。</li>
 *   <li><b>扫描步长</b> {@value #RAY_STEP} 格：必须显著小于 1 格，否则会**整格跳过**一堵一格厚的墙。</li>
 * </ul>
 *
 * <h2>★★ 标记与落点同源（结构性保证）</h2>
 * 手持时的落点标记与俯冲目标调的是<b>同一个</b> {@link #resolveLandingPoint(Player)}：
 * <ul>
 *   <li>IDLE（待机）⇒ 每刻按当前准心实时预览（★ 且只在"**冷却走完 + 闸门允许**"时才画，
 *       见 {@link #shouldShowLandingMarker(boolean, boolean, boolean)}）；</li>
 *   <li>跃起 / 俯冲 ⇒ 画在**施放那一刻冻结**的 {@link #dashTarget} 上（玩家能一路看着要砸哪儿）。</li>
 * </ul>
 * ⇒ "标记与实际落点不符"这一类 BUG **不可能存在**（只有一处算法）。
 */
public class RavenSomersaultSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "raven_somersault_skill";

    /** 冷却（刻）：需求 CD 40 秒。 */
    public static final int COOLDOWN_TICKS = 800;

    /** 能量消耗：需求未提 ⇒ 0。 */
    private static final int ENERGY_COST = 0;

    /** 地面形态的跃起高度（格）：需求"垂直跃起 12 格"。 */
    public static final double RISE_HEIGHT = 12.0d;

    /** 跃起阶段持续刻数：需求"在 0.2S 内" ⇒ 4 刻。 */
    public static final int RISE_TICKS = 4;

    /** **射线长度（格）**——需求 2026-10-11："射线长度为 25"。 */
    public static final double RAY_LENGTH = 25.0d;

    /** **射线扫描步长（格）**——必须远小于 1，否则会整格跳过一格厚的墙。 */
    public static final double RAY_STEP = 0.25d;

    /** **落点高度余量（格）**——需求："此方块高度是否 ≥（跃起后高度 − 3）" ⇒ 高过就落不了。 */
    public static final double LANDING_CLEARANCE = 3.0d;

    /** **垂足允许高出自己的上限（格）**——需求："垂足不可高于自己 6 个格子"。 */
    public static final double MAX_FOOT_ABOVE_SELF = 6.0d;

    /** 俯冲速度（格/刻）：需求"25 格每秒" ⇒ 25 / 20 = 1.25。 */
    public static final double DIVE_STEP_PER_TICK = 1.25d;

    /** 冲击半径（格）：需求"周围 r=7"。 */
    public static final double IMPACT_RADIUS = 7.0d;

    /** 地面形态的物理伤害（需求：15 点）。 */
    public static final double GROUND_PHYSICAL_DAMAGE = 15d;

    /** 浮空形态的物理伤害（需求："伤害提升至 25 点"）。 */
    public static final double HOVER_PHYSICAL_DAMAGE = 25d;

    /** 特殊值伤害区间（需求："4~7 点"；浮空形态只提升物理伤害 ⇒ 区间不变）。 */
    public static final int IMPACT_SANTE_MIN = 4;
    public static final int IMPACT_SANTE_MAX = 7;

    /** 地面形态的眩晕刻数（需求：1.5 秒）。 */
    public static final int GROUND_STUN_TICKS = 30;

    /** 浮空形态的眩晕刻数（需求：2 秒）。 */
    public static final int HOVER_STUN_TICKS = 40;

    /** **击飞的向上冲量**（需求："大招会击飞附近的敌人"）。 */
    public static final double LAUNCH_UP = 1.0d;

    /** **击飞的水平冲量**（沿"落点 → 目标"方向）。 */
    public static final double LAUNCH_OUT = 0.45d;

    /**
     * **大招最长持续（刻）**——需求 2026-10-11："最长持续 10S，超过 10S 则不会再尝试去突进，
     * 而是**就地进行砸地操作**"。
     *
     * <p>计时从**施放那一刻**起算（含跃起那 0.2 秒）——"最长持续"说的是整个技能。
     * 到点后本类不再做任何水平推进，只沿当前列往下砸。
     */
    public static final int MAX_FLIGHT_TICKS = 200;

    /** "到位后 / 超时后"垂直下坠的保险上限（刻）——防止在虚空里无限刷。 */
    private static final int PLUNGE_GUARD_TICKS = 200;

    /**
     * **本刻该做什么**：继续朝落点突进，还是转入"只下坠"。
     * <p>★ 抽成纯函数是为了让"**撞墙必须转下坠**"这条可离线断言 ——
     * 旧实现撞墙只"停在原地"，看门狗永远不会计数 ⇒ 玩家被**永久冻结**（"卡墙"的真根因）。
     */
    public enum DiveAction {
        /** 继续朝落点推进。 */
        ADVANCE,
        /** 停止突进，沿本列往下砸。 */
        PLUNGE
    }

    /** 三阶段。 */
    public enum Phase {
        /** 空闲（可以释放；此时标记跟随准心实时预览）。 */
        IDLE,
        /** 垂直跃起（只在地面形态出现）。 */
        RISE,
        /** 高速俯冲（朝冻结的落点）。 */
        DIVE
    }

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private FactionComponent faction;
    private RavenAirMobilityPassive flight;

    /** 语音组件（技能三那一栏台词）。 */
    private RavenFloatingTextComponent voice;

    private HotbarRenderComponent render;

    private final Random random = new Random();

    // ───────── 运行期状态 ─────────

    private Phase phase = Phase.IDLE;

    /** 跃起阶段的基准位置。 */
    private Location riseOrigin;

    /** 跃起阶段已过的刻数。 */
    private int riseTick;

    /** ★ **冻结的落点**（施放那一刻由 {@link #resolveLandingPoint(Player)} 求出，之后不再变）。 */
    private Location dashTarget;

    /** 到位后继续垂直下坠已过的刻数（保险）。 */
    private int plungeTicks;

    /** ★ **是否已转入"只下坠不再突进"**（到位 / 撞墙 / 超过 10 秒 三种情形之一）。 */
    private boolean plunging;

    /** 本次释放的**最晚突进刻**（= 施放刻 + {@value #MAX_FLIGHT_TICKS}）。 */
    private int flightDeadlineTick;

    /** 本次释放是否浮空形态（决定伤害 / 眩晕档位）。 */
    private boolean hoverForm;

    /** 粒子相位。 */
    private double phaseAngle;

    public RavenSomersaultSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<RavenSomersaultSkill> {

        public Specification() {
            super(Component.text("涅斯捷罗夫筋斗"),
                    List.of(
                            Component.text("地面：身下 1.5 格内需有方块；0.2 秒内垂直跃起 12 格后高速俯冲"),
                            Component.text("落点判定：以头部射出 25 格射线；遇可穿越方块穿过，遇不可穿越方块则"),
                            Component.text("  要求它上方有 2 格净空、且高度低于（跃起后高度 − 3），取它上方为落点"),
                            Component.text("  若整条射线没有合格落点 ⇒ 在射线末端垂直下坠，取垂足（不得高于自己 6 格）"),
                            Component.text("冷却走完且可以释放时，手持才显示落地区域标记（圈与叉），与俯冲目标同源"),
                            Component.text("落地对 r7 内敌人造成 15 点物理伤害、4~7 点特殊值伤害、1.5 秒眩晕并击飞"),
                            Component.text("浮空机动中使用：不再跃起，伤害提升至 25 点、眩晕提升至 2 秒，并与落地冲击一同触发"),
                            Component.text("最长持续 10 秒；超时或撞墙则不再突进，就地砸地；使用后清除浮空机动状态；冷却 40 秒")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.HEAVY_CORE);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(RavenAirMobilityPassive.class)
                    .requires(RavenFloatingTextComponent.class);
        }

        @Override
        public RavenSomersaultSkill create(String id, ComponentServicesPort services) {
            return new RavenSomersaultSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        flight = svc().components().get(RavenAirMobilityPassive.class);
        voice = svc().components().get(RavenFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        resetState();
    }

    @Override
    public void stop() {
        resetState();
        vitals = null;
        sante = null;
        buff = null;
        faction = null;
        flight = null;
        voice = null;
        render = null;
    }

    private void resetState() {
        phase = Phase.IDLE;
        riseOrigin = null;
        riseTick = 0;
        dashTarget = null;
        plungeTicks = 0;
        plunging = false;
        flightDeadlineTick = 0;
        hoverForm = false;
        phaseAngle = 0d;
    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            resetState();       //★ 空中死亡 / 下线 ⇒ 直接收工（不 teleport 尸体、不白结算）
            return;
        }
        phaseAngle += 0.6d;
        switch (phase) {
            case IDLE -> drawLandingMarker(owner, resolveLandingPoint(owner));
            case RISE -> advanceRise(owner);
            case DIVE -> advanceDive(owner);
        }
    }

    /**
     * **手持该技能、且"冷却走完并且真的放得出来"时**才画出落地区域标记（圈 + 叉）。
     *
     * <p>★★ 需求 2026-10-11（第二轮明确）："应该是**冷却完全可以释放时**，此技能才能手持显现落地位置"。
     * 判据 = ① 技能在主手；② 不在冷却；③ 闸门允许（没被眩晕 / 沉默等）。
     * 三条由纯函数 {@link #shouldShowLandingMarker(boolean, boolean, boolean)} 唯一给出
     * ⇒ "到底什么时候该显示"只有一处口径，不会与其它状态判断分叉。
     */
    private void drawLandingMarker(Player owner, Location landing) {
        boolean holding = ID.equals(Skill.Utils.getSkillId(owner.getInventory().getItemInMainHand()));
        if (!shouldShowLandingMarker(holding, isCoolingDown(), canUse())) {
            return;
        }
        World world = owner.getWorld();
        if (world == null || landing == null) {
            return;
        }
        RavenVfx.ravenLandingMarker(world, landing.clone().add(0d, 0.06d, 0d), IMPACT_RADIUS);
    }

    /** 跃起阶段：每刻把玩家抬到"基准 + 累计高度"。 */
    private void advanceRise(Player owner) {
        if (riseOrigin == null || riseOrigin.getWorld() != owner.getWorld()) {
            resetState();
            return;
        }
        riseTick++;
        double targetY = riseOrigin.getY() + (RISE_HEIGHT * riseTick) / RISE_TICKS;
        Location at = riseOrigin.clone();
        at.setY(Math.min(targetY, riseOrigin.getY() + RISE_HEIGHT));
        //★ 头顶有方块 ⇒ **提前结束跃起**（不再硬抬，否则人会被塞进方块里 = 另一种"卡墙"）
        boolean blocked = at.getBlock().getBoundingBox().getVolume() > 0d
                || at.clone().add(0d, 1d, 0d).getBlock().getBoundingBox().getVolume() > 0d;
        if (!blocked) {
            owner.teleport(at);
            owner.setFallDistance(0f);
        }
        RavenVfx.ravenSomersaultDiveTrail(owner.getWorld(), owner.getLocation(), phaseAngle);
        drawLandingMarker(owner, dashTarget);       //跃起期间也把落点画着
        if (blocked || riseTick >= RISE_TICKS) {
            phase = nextPhase(Phase.RISE, false);
            riseOrigin = null;
            riseTick = 0;
            RavenSound.ravenSomersaultDiveSound(owner.getWorld(), owner.getLocation());
        }
    }

    /** 俯冲：朝冻结的落点直线推进；到位后若仍在空中 ⇒ 继续垂直下坠，落地即结算。 */
    /**
     * 俯冲：朝冻结的落点直线推进；**三种情形统一转入"只下坠"**（{@link #plunging}）：
     * <ol>
     *   <li><b>到位</b>（射线算出来的落点已经够近）；</li>
     *   <li>★ <b>撞墙</b>——下一格不可穿越。旧实现在这里是"停在原地"，
     *       而"是否到位"的判据仍然为假 ⇒ {@code plungeTicks} 永远不加
     *       ⇒ 玩家被**永久冻结**在半空（这正是"向高处位移会卡墙"的根因）；</li>
     *   <li>★ <b>超过 {@value #MAX_FLIGHT_TICKS} 刻（10 秒）</b>——需求："超过 10S 则不会再尝试去突进，
     *       而是就地进行砸地操作"。</li>
     * </ol>
     * <p>转入"只下坠"之后，每刻沿**本列地面高度**向下夹（{@link #groundYAt}）——
     * 这样既不会把人塞进方块里，也不会因为落到半砖/台阶上而反复触发。
     */
    private void advanceDive(Player owner) {
        World world = owner.getWorld();
        if (world == null || dashTarget == null) {
            resetState();
            return;
        }
        Location current = owner.getLocation();
        //虚空：一路到底都没地面 ⇒ 收工且不出手（先于一切结算）
        if (current.getY() <= world.getMinHeight() + 1d) {
            resetState();
            return;
        }
        if (isOnGround(owner)) {
            resolveLanding(owner);
            return;
        }
        //★ 超过 10 秒 ⇒ 不再尝试突进，就地转砸地
        if (!plunging && shouldStopAdvancing(org.bukkit.Bukkit.getCurrentTick(), flightDeadlineTick)) {
            plunging = true;
        }

        //先算出"若能推进会到哪"，再让纯函数决定走还是砸
        Vector delta = dashTarget.toVector().subtract(current.toVector());
        boolean arrived = delta.length() <= DIVE_STEP_PER_TICK + 1.0E-6d;
        Location candidate = arrived ? current.clone()
                : current.clone().add(delta.normalize().multiply(DIVE_STEP_PER_TICK));
        boolean blocked = !arrived && candidate.getBlock().getBoundingBox().getVolume() > 0d;

        Location next;
        if (diveAction(plunging, arrived, blocked) == DiveAction.PLUNGE) {
            plunging = true;        //到位 / 撞墙 / 超时 ⇒ 就地转砸地
            next = plungeStep(world, current);
        } else {
            next = candidate;
        }

        if (plunging) {
            plungeTicks++;
            if (plungeTicks > PLUNGE_GUARD_TICKS) {
                resetState();
                return;
            }
        }
        owner.teleport(next);
        owner.setFallDistance(0f);
        RavenVfx.ravenSomersaultDiveTrail(world, owner.getLocation(), phaseAngle);
        drawLandingMarker(owner, dashTarget);

        if (isOnGround(owner)) {
            resolveLanding(owner);
        }
    }

    /**
     * **下坠一步**：沿**本列地面高度**向下夹，最多降 {@value #DIVE_STEP_PER_TICK} 格。
     *
     * <p>为什么用"地面高度"而不是简单减一个常数：减常数会在落到半砖/台阶上时
     * **一步扎进方块里**（人的脚嵌进去 ⇒ 之后每刻都在方块里"卡住"）。
     */
    private static Location plungeStep(World world, Location current) {
        double ground = groundYAt(world, current);
        double nextY = Math.max(ground, current.getY() - DIVE_STEP_PER_TICK);
        Location next = current.clone();
        next.setY(nextY);
        return next;
    }

    /** 某一列的地面顶面 Y（从脚下方那格起向下扫；虚空 ⇒ 世界最低高度）。 */
    private static double groundYAt(World world, Location from) {
        int bx = from.getBlockX();
        int bz = from.getBlockZ();
        return RavenAirMobilityPassive.groundLevelY(from.getBlockY() - 1, world.getMinHeight(),
                by -> world.getBlockAt(bx, by, bz).getBoundingBox().getVolume() > 0d);
    }

    /**
     * **是否已经超过"最长突进时间"**（纯函数）——需求："最长持续 10S，超过 10S 则不会再尝试去突进"。
     *
     * @param nowTick      当前刻
     * @param deadlineTick 最晚突进刻（= 施放刻 + {@value #MAX_FLIGHT_TICKS}）
     */
    public static boolean shouldStopAdvancing(int nowTick, int deadlineTick) {
        return nowTick >= deadlineTick;
    }

    /**
     * **本刻走哪一路**（纯函数）——只要满足任一条就**转入下坠**：
     * <ol>
     *   <li>{@code alreadyPlunging} —— 已经在砸了（保持）；</li>
     *   <li>{@code arrived} —— 已经到射线算出来的落点；</li>
     *   <li>★ {@code blocked} —— **下一格不可穿越（撞墙）**。
     *       这一条是"向高处位移会卡墙"的修复点：旧实现撞墙只"停在原地"，
     *       于是"到位"判据永远为假、看门狗永远不计数 ⇒ **永久冻结在半空**。</li>
     * </ol>
     */
    public static DiveAction diveAction(boolean alreadyPlunging, boolean arrived, boolean blocked) {
        if (alreadyPlunging || arrived || blocked) {
            return DiveAction.PLUNGE;
        }
        return DiveAction.ADVANCE;
    }

    /** 落地结算：r7 内敌人吃物理 + 特殊值 + 眩晕 + 击飞（档位由本次形态决定）。 */
    private void resolveLanding(Player owner) {
        World world = owner.getWorld();
        Location center = owner.getLocation();
        if (world == null) {
            resetState();
            return;
        }
        double physical = hoverForm ? HOVER_PHYSICAL_DAMAGE : GROUND_PHYSICAL_DAMAGE;
        int stunTicks = hoverForm ? HOVER_STUN_TICKS : GROUND_STUN_TICKS;

        RavenSound.ravenSomersaultLandSound(world, center);
        RavenVfx.ravenSomersaultLandBurst(world, center.clone().add(0d, 0.1d, 0d), IMPACT_RADIUS);

        for (Entity entity : owner.getNearbyEntities(IMPACT_RADIUS, IMPACT_RADIUS, IMPACT_RADIUS)) {
            if (!(entity instanceof Player victim) || victim.equals(owner)) {
                continue;
            }
            if (victim.isDead() || !victim.isOnline()) {
                continue;
            }
            if (faction != null && !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            if (vitals != null) {
                vitals.physicalDamage(victim, owner, physical);
            }
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(),
                        rollInclusive(random, IMPACT_SANTE_MIN, IMPACT_SANTE_MAX));
            }
            if (buff != null) {
                buff.addTo(victim, BuffType.STUN, stunTicks);
            }
            //★ 需求："大招会击飞附近的敌人" ⇒ 向外 + 向上给一记冲量
            victim.setVelocity(launchVelocity(
                    victim.getLocation().getX() - center.getX(),
                    victim.getLocation().getZ() - center.getZ()));
            victim.setNoDamageTicks(0);
        }
        resetState();
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (phase != Phase.IDLE) {
            return;     //已经在动作中
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        boolean hovering = flight != null && flight.isHovering();
        if (!hovering && (flight == null || !flight.canTakeOffHere())) {
            return;     //需求："跃起前提是脚下有方块"（已放松为"身下 1.5 格内"）；浮空形态不需要
        }

        //★ 落点在施放那一刻冻结（跃起期间人还会上移 ⇒ 不冻结标记就会与实际分叉）
        Location target = resolveLandingPoint(owner);
        if (target == null) {
            return;
        }
        hoverForm = hovering;
        dashTarget = target;
        plungeTicks = 0;
        plunging = false;
        flightDeadlineTick = org.bukkit.Bukkit.getCurrentTick() + MAX_FLIGHT_TICKS;

        if (hovering && flight != null) {
            flight.armLandingImpactForCurrentHeight();
        }
        if (flight != null) {
            flight.endHover();      //需求："使用后清除浮空机动状态"
        }

        //需求"三技能：涅斯捷罗夫筋斗"那一栏语音
        if (voice != null) {
            voice.saySkill(owner, RavenFloatingTextComponent.MOMENT_SKILL_SOMERSAULT);
        }

        phase = nextPhase(Phase.IDLE, hovering);
        if (phase == Phase.RISE) {
            riseOrigin = owner.getLocation().clone();
            riseTick = 0;
            RavenSound.ravenSomersaultLaunchSound(world, owner.getLocation());
        } else {
            riseOrigin = null;
            riseTick = 0;
            owner.setFallDistance(0f);
            RavenSound.ravenSomersaultDiveSound(world, owner.getLocation());
        }
        startCooldown();
        if (render != null) {
            render.requestRepaint();
        }
    }

    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    // ───────── 读口 ─────────

    /** 当前阶段（探针读口）。 */
    public Phase phase() {
        return phase;
    }

    /** 冻结的落点（探针读口；{@code null} = 当前没有动作）。 */
    public Location dashTarget() {
        return dashTarget == null ? null : dashTarget.clone();
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **阶段迁移**（纯函数，穷举全表）。
     *
     * <table>
     *   <tr><th>当前</th><th>startHovering</th><th>下一阶段</th></tr>
     *   <tr><td>IDLE</td><td>true</td><td>DIVE（浮空使用 ⇒ 不再跃起）</td></tr>
     *   <tr><td>IDLE</td><td>false</td><td>RISE</td></tr>
     *   <tr><td>RISE</td><td>任意</td><td>DIVE</td></tr>
     *   <tr><td>DIVE</td><td>任意</td><td>IDLE</td></tr>
     * </table>
     */
    public static Phase nextPhase(Phase current, boolean startHovering) {
        if (current == null) {
            return Phase.IDLE;
        }
        return switch (current) {
            case IDLE -> startHovering ? Phase.DIVE : Phase.RISE;
            case RISE -> Phase.DIVE;
            case DIVE -> Phase.IDLE;
        };
    }

    /**
     * **"跃起后的高度"**（纯函数）——需求："自己当前的跃起后的高度"，
     * 括注补充"**浮空状态就是当前高度**"。
     *
     * @param currentY  当前 Y
     * @param hovering  是否处于浮空机动（是 ⇒ 不加跃起量）
     */
    public static double apexYFor(double currentY, boolean hovering) {
        return currentY + (hovering ? 0d : RISE_HEIGHT);
    }

    /**
     * **这一列能不能落**（纯函数）＝ 该不可穿越方块的高度 < （跃起后高度 − {@value #LANDING_CLEARANCE}）。
     *
     * <p>需求原文："此不可穿越方块高度是否高于或等于自己当前的跃起后的高度减少 3 格？不是则…
     * 以那个不可穿越方块上方作为突进的目标终点" ⇒ <b>不满足"高过"这一条才可落</b>。
     *
     * @param surfaceY 该方块**顶面**的 Y（= {@code blockY + 1}）
     * @param apexY    跃起后的高度
     */
    public static boolean isLandable(double surfaceY, double apexY) {
        return surfaceY < apexY - LANDING_CLEARANCE;
    }

    /**
     * **是否应当显示落点标记**（纯函数）——需求："冷却完全可以释放时，此技能才能手持显现落地位置"。
     *
     * @param holdingSkill 技能是否拿在主手
     * @param coolingDown  是否正在冷却
     * @param castable     技能闸门是否允许（未被眩晕 / 沉默等）
     */
    public static boolean shouldShowLandingMarker(boolean holdingSkill, boolean coolingDown, boolean castable) {
        return holdingSkill && !coolingDown && castable;
    }

    /** **上方两格有没有净空**（纯函数）——两格都可穿越才算有净空。 */
    public static boolean hasHeadroom(boolean aboveOnePassable, boolean aboveTwoPassable) {
        return aboveOnePassable && aboveTwoPassable;
    }

    /**
     * **垂足是否在允许范围内**（纯函数）——需求："垂足不可高于自己 {@value #MAX_FOOT_ABOVE_SELF} 格"。
     *
     * @param footY 垂足（也就是落点）的 Y
     * @param selfY 玩家自己的 Y
     */
    public static boolean isFootAcceptable(double footY, double selfY) {
        return footY <= selfY + MAX_FOOT_ABOVE_SELF;
    }

    /**
     * **击飞冲量**（纯函数）——需求："大招会击飞附近的敌人"。
     *
     * <p>向上给固定的 {@value #LAUNCH_UP}，水平沿"从落点指向目标"的方向给
     * {@value #LAUNCH_OUT}；与落点完全重合 ⇒ 只向上。
     */
    public static Vector launchVelocity(double offsetX, double offsetZ) {
        Vector flat = new Vector(offsetX, 0d, offsetZ);
        if (flat.lengthSquared() > 1.0E-6d) {
            flat.normalize().multiply(LAUNCH_OUT);
        } else {
            flat = new Vector(0d, 0d, 0d);
        }
        return new Vector(flat.getX(), LAUNCH_UP, flat.getZ());
    }

    /** 闭区间随机整数 {@code [min, max]}（需求写"4~7 点"，两端都含）。 */
    public static int rollInclusive(Random random, int min, int max) {
        if (random == null || max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    // ───────── 内部几何：落点算法 ─────────

    /**
     * **解析落点**（唯一的落点出处：**标记与俯冲目标都调它**）。
     *
     * <p>完整流程逐条对应需求，见类注释"落点算法"一节。实现要点：
     * <ol>
     *   <li>从**头部**（{@code getEyeLocation()}）沿视线以 {@value #RAY_STEP} 格为步长扫描到
     *       {@value #RAY_LENGTH} 格；</li>
     *   <li>可穿越方块 ⇒ 跳过（射线穿过）；</li>
     *   <li>不可穿越方块 ⇒ 用纯函数 {@link #hasHeadroom} / {@link #isLandable} 判两件事，
     *       都过了就取"该方块顶面中心"为落点；否则**继续扫描**（见口径申报）；</li>
     *   <li>整条射线都没找到 ⇒ 在**射线末端**垂直下坠，取垂足（受 {@link #isFootAcceptable} 约束）。</li>
     * </ol>
     *
     * @return 落点；玩家 / 世界取不到、或射线方向退化 ⇒ {@code null}
     */
    private Location resolveLandingPoint(Player owner) {
        Location eye = owner.getEyeLocation();
        World world = owner.getWorld();
        if (eye == null || world == null) {
            return null;
        }
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < 1.0E-6d) {
            return null;
        }
        Vector unit = direction.clone().normalize();
        double apexY = apexYFor(owner.getLocation().getY(),
                flight != null && flight.isHovering());

        //① 沿射线逐格扫描（步长远小于 1 ⇒ 不会整格跳过一堵薄墙）
        for (double t = RAY_STEP; t <= RAY_LENGTH + 1.0E-6d; t += RAY_STEP) {
            Location at = eye.clone().add(unit.clone().multiply(t));
            Block block = at.getBlock();
            if (block.isPassable()) {
                continue;                       //可穿越 ⇒ 射线穿过
            }
            int bx = block.getX();
            int by = block.getY();
            int bz = block.getZ();
            //① 上方两格有没有不可穿越方块？（没有 ⇒ 有 2 格净空）
            boolean headroom = hasHeadroom(
                    world.getBlockAt(bx, by + 1, bz).isPassable(),
                    world.getBlockAt(bx, by + 2, bz).isPassable());
            if (!headroom) {
                continue;                       //站不下人 ⇒ 这一列不是落点
            }
            //② 高度是否 ≥（跃起后高度 − 3）？（是 ⇒ 太高，落上去只会撞墙侧）
            double surfaceY = by + 1d;
            if (!isLandable(surfaceY, apexY)) {
                continue;
            }
            //★ 命中：以该不可穿越方块"上方"为突进目标终点
            return new Location(world, bx + 0.5d, surfaceY, bz + 0.5d);
        }

        //② 整条射线没有合格落点 ⇒ 射线末端垂直下坠取垂足
        Location end = eye.clone().add(unit.clone().multiply(RAY_LENGTH));
        return footBelow(world, end, owner.getLocation().getY());
    }

    /**
     * **在给定位置垂直下坠到第一个不可穿越方块**，返回它的**顶面中心**（垂足）。
     *
     * <p>★ 需求："垂足不可高于自己 {@value #MAX_FOOT_ABOVE_SELF} 格" ——
     * 违反时退化为"射线末端、与自己同高度"（绝不把落点定在自己头顶的悬崖上），
     * 随后由既有的"到位后继续垂直下坠"收尾。扫描一路到世界底部都没有地面 ⇒ 同样退化。
     */
    private static Location footBelow(World world, Location from, double selfY) {
        int bx = from.getBlockX();
        int bz = from.getBlockZ();
        for (int by = from.getBlockY(); by >= world.getMinHeight(); by--) {
            if (!world.getBlockAt(bx, by, bz).isPassable()) {
                double footY = by + 1d;
                if (isFootAcceptable(footY, selfY)) {
                    return new Location(world, bx + 0.5d, footY, bz + 0.5d);
                }
                break;      //垂足太高 ⇒ 退化（不再往更下面找，那一层已经被地形盖住了）
            }
        }
        return new Location(world, from.getX(), selfY, from.getZ());
    }

    /** 几何取值 + 纯判据（离地高度 ≤ {@code GROUND_TOUCH_HEIGHT}）。 */
    private static boolean isOnGround(Player owner) {
        return RavenAirMobilityPassive.isTouchingGround(heightAboveGround(owner));
    }

    /** 玩家脚下的离地高度（格）。 */
    private static double heightAboveGround(Player owner) {
        Location at = owner.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return 0d;
        }
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        double ground = RavenAirMobilityPassive.groundLevelY(at.getBlockY() - 1, world.getMinHeight(),
                by -> world.getBlockAt(bx, by, bz).getBoundingBox().getVolume() > 0d);
        return at.getY() - ground;
    }
}
