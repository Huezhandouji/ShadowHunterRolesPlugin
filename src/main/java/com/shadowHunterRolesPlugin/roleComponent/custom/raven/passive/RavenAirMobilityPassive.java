package com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenVfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

/**
 * 「绯红渡鸦 · 林晨霜」被动：**浮空机动 + 主动机动 + 落地冲击**（三者本就同属"空中状态"一件事）。
 *
 * <h2>★★ 本组件只有一个"浮空状态"真值（2026-10-11 第二轮收窄）</h2>
 * 需求原话："**一旦浮空状态没有粒子了，就立刻改为地面状态**" ⇒
 * 粒子的绘制条件与 HUD 的读数条件**必须是同一个布尔**（{@link #hoverActive}）。
 * 于是本类的结构是：
 * <pre>
 *   hoverActive == true  ⇒  这一帧一定画浮空环（含空中机动那 0.2 秒）
 *   hoverActive == false ⇒  这一帧一定不画环，HUD 也一定显示"地面"
 * </pre>
 * ⇒ 观感与读数**不可能分叉**。任何"暂停悬停但不结束悬停"的做法都会破坏这条（旧版就是这么错的）。
 *
 * <h2>★★ 浮空状态的四条结束路径（就这四条，没有例外）</h2>
 * <ol>
 *   <li><b>触地</b> —— 需求："接触地面则立刻使得浮空状态消失"；</li>
 *   <li><b>时间到</b> —— {@value #HOVER_DURATION_TICKS} 刻（3.5 秒）；</li>
 *   <li><b>0.2 秒内双击空格</b> —— 需求："关闭悬停状态开始下落"；</li>
 *   <li><b>技能显式清除</b> —— 技能三的"使用后清除浮空机动状态"。</li>
 * </ol>
 * ★ <b>"按住蹲"不在其中</b>（2026-10-11 第二轮修正）：旧版把按蹲实现成"立刻解除悬停"，
 * 而玩家在战斗里会**随手按 Shift**（潜行 / 防掉边）⇒ 浮空被莫名其妙地取消、状态频繁跳到"地面"，
 * 表现为"从浮空到地面的转换不灵敏"。现在按蹲回到最初的口径：
 * <b>仍在悬停内，只是加速下落</b>（越久越快）——降到地面由第 ① 条结束它，
 * 因此"按住蹲快速落地"照样会触发落地冲击。
 *
 * <h2>★★ 落地冲击的"高度"用**本次悬停期间的峰值离地高度**</h2>
 * 需求要的是"空中位移状态结束且离地 ≥1.5 格"。但"按住蹲快速下落"这条路径的结束时机是
 * **触地那一刻**（离地≈0）⇒ 若照字面取"结束瞬间的高度"就永远触发不了。
 * 因此本类记 {@link #hoverPeakHeight}（本次悬停里测到的最大离地高度），
 * 四条结束路径**统一**用它判 {@value #LANDING_IMPACT_MIN_HEIGHT}——
 * 语义即"你从 ≥1.5 格高的地方悬停过，然后落地了 ⇒ 砸一下"。
 *
 * <h2>需求逐条（其余）</h2>
 * <ul>
 *   <li><b>浮空机动</b>：3.5 秒滞空、免疫黑暗与失明、水平 3 倍速自由移动、向上缓慢、蹲加速下落；</li>
 *   <li><b>瞄准模式</b>：浮空中武器是"锁定"；空中左键在「锁定 / 自主」间切换；
 *       ★ <b>非浮空（地面）时恒为「自主」</b>（需求 2026-10-11："人物在地面永远是自主的射击模式"）
 *       —— 由 {@link #update()} 每刻强制，因此 HUD 不可能残留上一轮的锁定态；</li>
 *   <li><b>主动机动</b>：充能池上限 {@value #MAX_CHARGES} 层，每 {@value #CHARGE_REGEN_TICKS} 刻
 *       耗 {@value #CHARGE_REGEN_ENERGY_COST} 能量充 1 层，击杀立刻充 1 层（初始满层）；</li>
 *   <li><b>落地冲击</b>：r{@value #LANDING_IMPACT_RADIUS} 内敌人吃 {@value #LANDING_IMPACT_DAMAGE} 点物理
 *       + {@value #LANDING_IMPACT_SANTE_MIN}~{@value #LANDING_IMPACT_SANTE_MAX} 点特殊值 + <b>击退</b>；
 *       <b>无视坠落伤害</b>；从浮空落地给 2 秒速度 6；</li>
 *   <li><b>actionbar HUD</b>：浮空 / 地面 · 散华弹药 · 机动层数 · 锁定 / 自主
 *       —— 本类是这条 HUD 的**唯一发布者**（主武器用 {@link #publishAmmo(int, int)} 推读数过来）。</li>
 * </ul>
 *
 * <h2>★★ 口径申报：悬停怎么实现（gravity off + 输入驱动）</h2>
 * <b>关掉重力</b>（{@link Entity#setGravity(boolean)}）+ 每刻按玩家真实按键写速度：
 * 水平读 {@link Input} 的方向键 + 身体朝向**自己算**（{@link #hoverHorizontalVelocity}，
 * 无状态、不会自乘放大）；垂直见 {@link #hoverVerticalVelocity(boolean, boolean, int)}。
 * ★ <b>重力还原是硬要求</b>：用 {@link #hoverGravityDisabled} 记账、只还原"自己关掉过的那一次"，
 * 且在悬停结束 / 玩家死亡 / 下线 / {@code stop()} 四条路径上都还原。
 */
public class RavenAirMobilityPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "raven_air_mobility_passive";

    // ───────── 浮空机动 ─────────

    /** 浮空持续刻数（需求："持续 3.5 秒"）。 */
    public static final int HOVER_DURATION_TICKS = 70;

    /** **悬停的水平移动速度**（格/刻）—— 需求："加速 3 倍悬停水平移动速度"。 */
    public static final double HOVER_HORIZONTAL_SPEED = 0.6d;

    /** 相对常态步速的提速倍数（需求："加速 3 倍"）。 */
    public static final double HOVER_SPEED_MULTIPLIER = 3d;

    /** 悬停时按跳键的上升速度（格/刻）—— {@code 0.12} ⇒ 2.4 格/秒，属"缓慢上升"。 */
    public static final double HOVER_RISE = 0.12d;

    /** 悬停时什么键都不按的垂直速度 = {@code 0}（真悬停；重力已关）。 */
    public static final double HOVER_HOLD = 0d;

    /** **双击空格**关闭悬停的时间窗（刻）—— 需求："0.2S 内双击空格"。 */
    public static final int DOUBLE_TAP_WINDOW_TICKS = 4;

    /** 按蹲加速下落的**起始**下沉速度（格/刻）。 */
    public static final double FAST_FALL_BASE_SINK = 0.35d;

    /** 每多按一刻的提速量（需求："按住蹲越久下落速度越快"）。 */
    public static final double FAST_FALL_RAMP_PER_TICK = 0.06d;

    /** 加速下落的速度上限（格/刻）—— {@code 2.0} ⇒ 40 格/秒（"快速落地"）。 */
    public static final double FAST_FALL_MAX_SINK = 2.0d;

    // ───────── 落地（速度 6 + 冲击）─────────

    /** 落地加速的增幅：需求"速度 6" ⇒ 原版增幅 5。 */
    public static final int LANDING_SPEED_AMPLIFIER = 5;

    /** 落地加速持续（需求："2 秒"）。 */
    public static final int LANDING_SPEED_TICKS = 40;

    /** 触发落地冲击的最小离地高度（需求："1.5 格及以上"）。 */
    public static final double LANDING_IMPACT_MIN_HEIGHT = 1.5d;

    /** 落地冲击半径（需求：r=5）。 */
    public static final double LANDING_IMPACT_RADIUS = 5.0d;

    /** 落地冲击物理伤害（需求：10 点）。 */
    public static final double LANDING_IMPACT_DAMAGE = 10d;

    /** 落地冲击特殊值伤害区间（需求："3~6 点"，两端都含）。 */
    public static final int LANDING_IMPACT_SANTE_MIN = 3;
    public static final int LANDING_IMPACT_SANTE_MAX = 6;

    /** 落地冲击的**击退强度**（需求："落地冲击还会击退附近的敌人"）。 */
    public static final double LANDING_IMPACT_KNOCKBACK = 1.5d;

    /** **跃起允许的最大离地高度**（格）——需求："只要角色身下 1.5 格内有方块就可以跃起"。 */
    public static final double TAKEOFF_GROUND_PROXIMITY = 1.5d;

    /**
     * **"触地"的离地高度阈值**（格）—— 小于它才算踩在地上。
     *
     * <p>★★ 为什么不用"脚那格空 + 下方那格实心"（整格判据）：那个判据在**离地最多约 1 格**时
     * 就已经成立（脚在空气格里、下面就是地面）⇒ 只要以"离地 0.9 格"的姿态进入浮空，
     * **下一帧就会被判成已落地、浮空瞬间消失**，表现正是"从浮空到地面的转换不灵敏"。
     * <p>用离地距离后就精确了：{@value} 覆盖半砖（0.5）/ 雪层，又远小于 1.0
     * ⇒ "贴着地面"才结束浮空，浮在空中就一直是浮空。
     */
    public static final double GROUND_TOUCH_HEIGHT = 0.6d;

    // ───────── 主动机动（充能池）─────────

    /** 充能上限（需求："可最高存储 3 次"）。 */
    public static final int MAX_CHARGES = 3;

    /** 充能周期（刻）：15 秒（需求："每 15 秒消耗 10 能量存储一个机动"）。 */
    public static final int CHARGE_REGEN_TICKS = 300;

    /** 每次充能的耗能（需求："消耗 10 能量"）。 */
    public static final int CHARGE_REGEN_ENERGY_COST = 10;

    /** 空中主动机动的两次使用间隔（刻）：1 秒（需求："缩减至 1S"）。 */
    public static final int AIR_DASH_INTERVAL_TICKS = 20;

    /**
     * **"击杀充能"标记的有效窗（刻）**——供语音组件判断"这次击杀有没有顺手充能"
     * （需求里"击杀后刷新机动"那一栏语音）。取 2 刻：击杀订阅与语音订阅在同一刻内被依次通知。
     */
    private static final int KILL_CHARGE_MARKER_TICKS = 2;

    /** 瞄准模式：浮空机动中"武器切换至锁定状态"，空中左键在两者间切换。 */
    public enum AimMode {
        /** 锁定模式：右键蓄力 0.8 秒后轮流锁定 3 名敌人。 */
        LOCK,
        /** 单发模式（自主）：右键与地面一致，直接以准心射出一发。 */
        SINGLE
    }

    // ───────── actionbar HUD ─────────

    /** HUD 在"内容没变"时的保活间隔（刻）——原版 actionbar 会自己淡出，需要定期重发。 */
    private static final int HUD_KEEPALIVE_TICKS = 10;

    /**
     * **浮空进度条的分段数**——每段代表 1 秒；浮空总时长 {@value #HOVER_DURATION_TICKS} 刻（3.5 秒）
     * ⇒ 最多 3 段（需求 2026-10-11："少一秒就 ■■□"）。
     */
    public static final int HOVER_BAR_SEGMENTS = 3;

    /** 进度条"满"的字符。 */
    private static final char HOVER_BAR_FILLED = '■';

    /** 进度条"空"的字符。 */
    private static final char HOVER_BAR_EMPTY = '□';

    // ───────── 协作组件（start() 里一次取好）─────────

    private EnergyComponent energy;
    private VitalsComponent vitals;
    private BuffComponent buff;

    /** 语音组件（落地冲击时喊一句）。 */
    private RavenFloatingTextComponent voice;
    private SanTEComponent sante;
    private FactionComponent faction;

    /** 击杀订阅（必须按引用存下来才能在 stop() 里撤销）。 */
    private VitalsComponent.PlayerKilledListener killEntry;

    /** **最近一次"击杀顺手充了机动"的刻**（{@link #NO_KILL_CHARGE} = 从未）。 */
    private int lastKillChargeTick = NO_KILL_CHARGE;

    private final Random random = new Random();

    // ───────── 悬停状态 ─────────

    /** ★ **浮空状态的唯一真值**：粒子与 HUD 都读它（见类注释）。 */
    private boolean hoverActive;

    /** 悬停到期刻（仅在 {@link #hoverActive} 为真时有意义）。 */
    private int hoverUntilTick;

    /** 本 tick 内关掉过重力吗（只还原"自己关掉的那一次"）。 */
    private boolean hoverGravityDisabled;

    /** 本次悬停期间**测到的最大离地高度**（落地冲击的高度判据来源，见类注释）。 */
    private double hoverPeakHeight;

    /** 已武装的坠落高度（{@code > 0} = 落地时要判是不是够 1.5 格）。 */
    private double armedFallHeight;

    /** 上一刻是否踩在地上（落地事件 = 它的 false → true 边沿）。 */
    private boolean wasOnGround;

    /** 本次离地期间是否经历过悬停（决定落地要不要给速度 6）。 */
    private boolean hoveredThisAirPhase;

    /** 空中主动机动进行中 ⇒ 暂停悬停的**物理与结束判定**（由主武器 begin/endAirDash 控制）。 */
    private boolean dashSuspended;

    /** 粒子相位。 */
    private double phase;

    /** 上一刻是否按着跳键（双击空格的**边沿**判据需要它）。 */
    private boolean jumpWasHeld;

    /** 上一次"按下跳键"的刻（{@code -1} = 本命还没按过）。 */
    private int lastJumpPressTick = -1;

    /** 按蹲已持续的刻数（决定"越久落得越快"；松手即归零）。 */
    private int sneakHoldTicks;

    // ───────── 瞄准 / 充能 ─────────

    private AimMode aimMode = AimMode.SINGLE;
    private int charges = MAX_CHARGES;
    private int chargeTick;

    // ───────── HUD 读数（主武器推过来的弹药量）─────────

    private int hudAmmo = -1;
    private int hudAmmoMax = -1;
    private int hudTick;
    private String lastHudText = "";

    public RavenAirMobilityPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符：被动无栏位 ⇒ 不占热键栏。 */
    public static final class Specification extends PassiveSkill.Specification<RavenAirMobilityPassive> {

        public Specification() {
            super(Component.text("浮空机动"),
                    List.of(
                            Component.text("经技能或空中丢出主武器位移后若仍在空中，进入浮空机动 3.5 秒"),
                            Component.text("浮空期间：水平高速自由移动（3 倍）、按跳缓慢上升、按蹲加速下落"),
                            Component.text("浮空期间免疫黑暗与失明；武器为「锁定」模式，空中左键切换「锁定 / 自主」"),
                            Component.text("0.2 秒内双击空格 ⇒ 主动解除浮空开始下落（这是唯一的主动解除手段）"),
                            Component.text("空中丢出主武器：快装 2 发 + 0.2 秒内前冲 8 格并刷新浮空（耗 1 层机动，间隔 1 秒）"),
                            Component.text("机动充能：上限 3 层，每 15 秒消耗 10 能量充 1 层，击杀敌人立刻充 1 层"),
                            Component.text("落地冲击：从 ≥1.5 格高的浮空落地时，对 r5 内敌人造成伤害并击退"),
                            Component.text("无视坠落伤害；每次从浮空落地获得 2 秒速度 6"),
                            Component.text("actionbar 显示：浮空/地面（浮空时后跟 ■■■ 进度条）· 散华弹药 · 机动层数 · 锁定/自主")
                    ));
            requires(EnergyComponent.class).requires(VitalsComponent.class)
                    .requires(BuffComponent.class).requires(SanTEComponent.class)
                    .requires(FactionComponent.class)
                    //语音组件（落地冲击喊话）；它对本组件只做可选读取 ⇒ 无依赖环
                    .requires(RavenFloatingTextComponent.class);
        }

        @Override
        public RavenAirMobilityPassive create(String id, ComponentServicesPort services) {
            return new RavenAirMobilityPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 开始生效：取依赖、重置状态，并订阅「击杀 ⇒ 立刻充 1 层机动」。
     *
     * <p>击杀订阅必须挂在**击杀者那一侧**（本组件就是击杀者角色的组件）——
     * 框架的击杀广播只投给击杀者实例（见 {@link VitalsComponent#addPlayerKilledListener}）。
     */
    @Override
    public void start() {
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        faction = svc().components().get(FactionComponent.class);
        voice = svc().components().get(RavenFloatingTextComponent.class);

        hoverActive = false;
        hoverUntilTick = 0;
        hoverPeakHeight = 0d;
        armedFallHeight = 0d;
        hoveredThisAirPhase = false;
        dashSuspended = false;
        phase = 0d;
        jumpWasHeld = false;
        lastJumpPressTick = -1;
        sneakHoldTicks = 0;
        aimMode = AimMode.SINGLE;
        charges = MAX_CHARGES;
        chargeTick = 0;
        hudAmmo = -1;
        hudAmmoMax = -1;
        hudTick = 0;
        lastHudText = "";
        lastKillChargeTick = NO_KILL_CHARGE;

        Player owner = self();
        //★ 初值必须量一次：否则"选角色那一刻正站在地上"会被误判成一次落地
        wasOnGround = owner != null && isOnGround(owner);

        if (vitals != null) {
            killEntry = vitals.addPlayerKilledListener(this, event -> {
                if (event == null) {
                    return;
                }
                Player selfPlayer = self();
                if (selfPlayer != null && event.getKiller() != selfPlayer) {
                    return;     // 双保险：本名单只会在"我是击杀者"时被通知
                }
                int before = charges;
                grantCharge();
                if (charges > before) {
                    //★ 只有"真的充上了"才打标记（满仓时击杀不算"顺手充能"）
                    lastKillChargeTick = org.bukkit.Bukkit.getCurrentTick();
                }
            });
        }
    }

    /**
     * 停止生效：撤销击杀订阅、<b>还原重力</b>、清空全部空中状态与 HUD 读数。
     *
     * <p>★ 还原重力这一条是硬要求：忘掉就是"换角色后玩家永久飘着"。
     */
    @Override
    public void stop() {
        if (vitals != null && killEntry != null) {
            vitals.removePlayerKilledListener(killEntry);
        }
        killEntry = null;
        releaseGravity(self());
        hoverActive = false;
        hoverUntilTick = 0;
        hoverPeakHeight = 0d;
        armedFallHeight = 0d;
        hoveredThisAirPhase = false;
        dashSuspended = false;
        wasOnGround = false;
        jumpWasHeld = false;
        lastJumpPressTick = -1;
        sneakHoldTicks = 0;
        charges = MAX_CHARGES;
        chargeTick = 0;
        aimMode = AimMode.SINGLE;
        hudAmmo = -1;
        hudAmmoMax = -1;
        lastHudText = "";
        lastKillChargeTick = NO_KILL_CHARGE;
        voice = null;
        energy = null;
        vitals = null;
        buff = null;
        sante = null;
        faction = null;
    }

    // ───────── 每 tick ─────────

    @Override
    public void update() {
        Player owner = self();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            releaseGravity(owner);
            hoverActive = false;
            hoveredThisAirPhase = false;
            armedFallHeight = 0d;
            hoverPeakHeight = 0d;
            wasOnGround = false;
            sneakHoldTicks = 0;
            return;
        }

        //① 无视坠落伤害（无条件 —— 需求写的就是"你无视坠落伤害"）
        owner.setFallDistance(0f);
        phase += 0.35d;

        int now = org.bukkit.Bukkit.getCurrentTick();
        boolean onGround = isOnGround(owner);
        InputState input = readInput(owner);

        //② 悬停推进（见类注释"四条结束路径"）
        advanceHover(owner, input, onGround, now);

        //记录"跳键按下"的边沿（不论是否在悬停 —— 否则进入悬停那一刻的边沿会错）
        if (input.jump && !jumpWasHeld) {
            lastJumpPressTick = now;
        }
        jumpWasHeld = input.jump;

        //③ 重力同步（悬停关、其余还原；只动"自己关掉的那一次"）
        syncGravity(owner);

        //④ 充能节拍
        advanceChargeRegen(owner);

        //⑤ 落地事件
        if (!wasOnGround && onGround) {
            onLand(owner);
        }
        wasOnGround = onGround;

        //⑥ ★ 非浮空 ⇒ 恒为"自主"（需求："人物在地面永远是自主的射击模式"）
        if (!hoverActive) {
            aimMode = AimMode.SINGLE;
        }

        //⑦ HUD
        publishHud(owner);
    }

    /**
     * **悬停的唯一推进点**（四条结束路径 + "环与状态同生同死"）。
     *
     * <p>★ 关键约束（需求："一旦浮空状态没有粒子了，就立刻改为地面状态"）：
     * <b>{@link #hoverActive} 为真的每一帧都必须画环</b>，反之亦然。
     * 因此"空中机动"期间只是**暂停物理与结束判定**，**照画环**——
     * 旧版在那里整段跳过（不画环但状态仍是浮空）⇒ 观感与读数分叉，正是这次要修的东西。
     */
    private void advanceHover(Player owner, InputState input, boolean onGround, int now) {
        if (!hoverActive) {
            sneakHoldTicks = 0;
            return;
        }
        hoveredThisAirPhase = true;
        boolean suspended = dashSuspended;

        boolean landed = !suspended && onGround;
        boolean expired = !suspended && now >= hoverUntilTick;
        boolean doubleTap = !suspended
                && isDoubleTapJump(now, lastJumpPressTick, input.jump, jumpWasHeld);
        if (landed || expired || doubleTap) {
            if (doubleTap && !landed) {
                RavenSound.ravenHoverDropSound(owner.getWorld(), owner.getLocation());
            }
            endHover(owner, !landed);     // 触地时不再画"脱离"特效（人已经在地上了）
            return;
        }

        if (!suspended) {
            applyHoverPhysics(owner, input);
        }
        //★ 峰值离地高度：不管有没有被挂起都要量（落地冲击的高度判据）
        hoverPeakHeight = Math.max(hoverPeakHeight, heightAboveGround(owner));
        applyHoverImmunity(owner);
        RavenVfx.ravenHoverRing(owner.getWorld(), owner.getLocation(), phase);
    }

    // ───────── 悬停：进入 / 结束 ─────────

    /** 是否正在悬停（供主武器 / 技能读）。 */
    public boolean isHovering() {
        return hoverActive;
    }

    /**
     * **进入或刷新浮空机动**（需求："每次通过技能或丢出主武器进行位移后，若位移完成后自身处于空中，
     * 则自动进入浮空机动状态…持续 3.5 秒"；主动机动再触发时"刷新持续时间"）。
     *
     * <p>★ 需求："在浮空机动状态中，武器切换至锁定状态" ⇒ 每次进入都把瞄准模式复位为
     * {@link AimMode#LOCK}。★ **刷新**时不清零峰值（那会让"快到时间了再刷新一次"丢掉落地冲击）。
     */
    public void enterHover() {
        Player owner = self();
        boolean wasActive = hoverActive;
        hoverActive = true;
        hoverUntilTick = org.bukkit.Bukkit.getCurrentTick() + HOVER_DURATION_TICKS;
        aimMode = AimMode.LOCK;
        resetPeakIfFresh(wasActive, owner);
        hoveredThisAirPhase = true;
        sneakHoldTicks = 0;
        if (owner != null) {
            World world = owner.getWorld();
            if (!wasActive) {
                RavenSound.ravenHoverEnterSound(world, owner.getLocation());
            }
            RavenVfx.ravenHoverEnterBurst(world, owner.getLocation());
        }
    }

    /** 首次进入时把峰值重置为当前高度（刷新时保留旧峰值）。 */
    private void resetPeakIfFresh(boolean wasActive, Player owner) {
        if (wasActive) {
            return;
        }
        hoverPeakHeight = owner == null ? 0d : heightAboveGround(owner);
    }

    /**
     * **只刷新浮空的剩余时间**（已悬停时用）——需求 2026-10-11："一技能在空中使用也会刷新浮空时间"。
     *
     * <p>与 {@link #enterHover()} 的差别：**不动瞄准模式**（把"自主"偷偷打回"锁定"会让玩家莫名其妙）、
     * **不动峰值高度**（那会让落地冲击的高度判据失真）。未悬停时等价于 {@link #enterHover()}。
     */
    public void refreshHover() {
        if (!hoverActive) {
            enterHover();
            return;
        }
        hoverUntilTick = org.bukkit.Bukkit.getCurrentTick() + HOVER_DURATION_TICKS;
        fastFallActiveReset();
    }

    /** 刷新时把"快速下落"的累计清零（属于上次悬停的残留）。 */
    private void fastFallActiveReset() {
        sneakHoldTicks = 0;
    }

    /** **结束浮空机动**（需求："使用后清除浮空机动状态"）。 */
    public void endHover() {
        endHover(self(), true);
    }

    /**
     * 内部：结束悬停 + **武装坠落高度** + 还原重力 + 观感。
     *
     * <p>★ 四条结束路径**统一**在这里武装（用本次悬停的峰值高度）——
     * 见类注释"落地冲击的高度"一节：按蹲快速下落的结束时机是触地（离地≈0），
     * 只有用峰值才能在那一刻仍判出"这是从高处落下来的"。
     */
    private void endHover(Player owner, boolean emitVfx) {
        if (!hoverActive) {
            return;
        }
        hoverActive = false;
        hoverUntilTick = 0;
        sneakHoldTicks = 0;
        armedFallHeight = Math.max(armedFallHeight, hoverPeakHeight);
        if (emitVfx && owner != null) {
            RavenSound.ravenHoverExitSound(owner.getWorld(), owner.getLocation());
            RavenVfx.ravenHoverExitBurst(owner.getWorld(), owner.getLocation());
        }
    }

    /**
     * **空中主动机动开始 / 结束**（由主武器在"0.2 秒前冲 8 格"期间调用）。
     *
     * <p>挂起期间**只暂停物理与结束判定**，照常画环并量高度（见 {@link #advanceHover}）。
     */
    public void beginAirDash() {
        dashSuspended = true;
    }

    /** 结束空中机动：恢复悬停物理并**刷新浮空持续时间**（需求："再次触发浮空机动状态"）。 */
    public void endAirDash() {
        endAirDash(true);
    }

    /**
     * 结束空中机动。
     *
     * @param refreshHover 是否顺手刷新浮空。★ 正常结束为 {@code true}；
     *                     玩家死亡 / 组件停用为 {@code false}
     */
    public void endAirDash(boolean refreshHover) {
        dashSuspended = false;
        if (refreshHover) {
            enterHover();
        }
    }

    /** 悬停是否被空中机动挂起（读口）。 */
    public boolean isDashSuspended() {
        return dashSuspended;
    }

    // ───────── 悬停：物理 / 免疫 ─────────

    /**
     * **悬停的每刻物理**：水平由按键 + 朝向算出（3 倍速），垂直按跳 / 蹲键
     * （见 {@link #hoverVerticalVelocity(boolean, boolean, int)}）。
     */
    private void applyHoverPhysics(Player owner, InputState input) {
        if (input.sneak) {
            sneakHoldTicks++;
        } else {
            sneakHoldTicks = 0;
        }
        double vy = hoverVerticalVelocity(input.jump, input.sneak, sneakHoldTicks);
        Vector horizontal = hoverHorizontalVelocity(input.forwardImpulse, input.strafeImpulse,
                owner.getLocation().getYaw(), HOVER_HORIZONTAL_SPEED);
        owner.setVelocity(new Vector(horizontal.getX(), vy, horizontal.getZ()));
        owner.setFallDistance(0f);
    }

    /** **免疫黑暗与失明**（需求："状态期间免疫黑暗与失明"）—— 每刻直接摘掉这两个效果。 */
    private void applyHoverImmunity(Player owner) {
        owner.removePotionEffect(PotionEffectType.BLINDNESS);
        owner.removePotionEffect(PotionEffectType.DARKNESS);
    }

    /** 重力同步：悬停 ⇒ 关；其余 ⇒ 还原（只动自己关掉过的那一次）。 */
    private void syncGravity(Player owner) {
        if (hoverActive) {
            if (!hoverGravityDisabled) {
                owner.setGravity(false);
                hoverGravityDisabled = true;
            }
        } else {
            releaseGravity(owner);
        }
    }

    /** 把重力还回去（幂等；{@code owner} 为 {@code null} 时只清记账位）。 */
    private void releaseGravity(Player owner) {
        if (!hoverGravityDisabled) {
            return;
        }
        hoverGravityDisabled = false;
        if (owner != null) {
            owner.setGravity(true);
        }
    }

    // ───────── 落地：速度 6 + 落地冲击 ─────────

    /** 落地事件：先判落地冲击，再给"从浮空落地"的 2 秒速度 6。 */
    private void onLand(Player owner) {
        double armed = armedFallHeight;
        boolean fromHover = hoveredThisAirPhase;
        armedFallHeight = 0d;
        hoverPeakHeight = 0d;
        hoveredThisAirPhase = false;
        sneakHoldTicks = 0;

        if (shouldImpactOnLanding(armed)) {
            resolveLandingImpact(owner);
        }
        if (fromHover) {
            grantLandingHaste(owner);
        }
    }

    /** r5 内敌人吃 10 物理（**带击退**）+ 3~6 特殊值。 */
    private void resolveLandingImpact(Player owner) {
        World world = owner.getWorld();
        Location center = owner.getLocation();
        if (world == null || vitals == null) {
            return;
        }
        RavenSound.ravenLandingImpactSound(world, center);
        RavenVfx.ravenLandingImpact(world, center.clone().add(0d, 0.1d, 0d), LANDING_IMPACT_RADIUS);
        //需求"落地冲击触发"那一栏语音
        if (voice != null) {
            voice.saySpecial(owner, RavenFloatingTextComponent.MOMENT_LANDING);
        }

        for (Entity entity : owner.getNearbyEntities(LANDING_IMPACT_RADIUS, LANDING_IMPACT_RADIUS,
                LANDING_IMPACT_RADIUS)) {
            if (!(entity instanceof Player victim) || victim.equals(owner)) {
                continue;
            }
            if (victim.isDead() || !victim.isOnline()) {
                continue;
            }
            if (faction != null && !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            //★ 需求："落地冲击还会击退附近的敌人" ⇒ 走带击退强度的重载
            vitals.physicalDamage(victim, owner, LANDING_IMPACT_DAMAGE, LANDING_IMPACT_KNOCKBACK);
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(),
                        rollInclusive(random, LANDING_IMPACT_SANTE_MIN, LANDING_IMPACT_SANTE_MAX));
            }
            //★ 多段 / 范围伤害必须自己清无敌帧（工程既有口径）
            victim.setNoDamageTicks(0);
        }
    }

    /** 从悬浮落地 ⇒ 2 秒速度 6。 */
    private void grantLandingHaste(Player owner) {
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SPEED, LANDING_SPEED_TICKS, LANDING_SPEED_AMPLIFIER,
                    true, false);
        }
        World world = owner.getWorld();
        RavenSound.ravenLandingHasteSound(world, owner.getLocation());
        RavenVfx.ravenLandingHasteBurst(world, owner.getLocation());
    }

    // ───────── 主动机动：充能池 ─────────

    /** 当前机动层数。 */
    public int charges() {
        return charges;
    }

    /** 距离下一次充能还有多少刻（满仓 / 未在计时 ⇒ 0）。 */
    public int chargeRegenRemainingTicks() {
        return charges >= MAX_CHARGES ? 0 : Math.max(0, CHARGE_REGEN_TICKS - chargeTick);
    }

    /** 是否还有机动可用（主武器用它决定附魔光效）。 */
    public boolean hasCharge() {
        return charges > 0;
    }

    /**
     * **消耗一层机动**（空中"丢出主武器"时调用）。
     *
     * @return 是否真的消耗了（层数为 0 ⇒ 回 {@code false}，调用方据此拒绝这次位移）
     */
    public boolean consumeCharge() {
        if (charges <= 0) {
            return false;
        }
        charges--;
        return true;
    }

    /** **立刻充能一层**（击杀 / 技能二）。 */
    public void grantCharge() {
        if (charges >= MAX_CHARGES) {
            return;
        }
        charges++;
        Player owner = self();
        if (owner != null) {
            grantChargeFeedback(owner, false);
        }
    }

    /** 充能节拍：只在不满仓时计时与扣能量。 */
    private void advanceChargeRegen(Player owner) {
        if (charges >= MAX_CHARGES) {
            chargeTick = 0;
            return;
        }
        chargeTick++;
        if (chargeTick < CHARGE_REGEN_TICKS) {
            return;
        }
        chargeTick = 0;
        int available = energy != null ? energy.current() : 0;
        if (!shouldRegenCharge(charges, MAX_CHARGES, available)) {
            return;     // 能量不足 ⇒ 这一拍不充（不欠账、不排队）
        }
        if (energy.tryConsume(CHARGE_REGEN_ENERGY_COST)) {
            charges++;
            grantChargeFeedback(owner, true);
        }
    }

    /** 充能到手的一次观感。 */
    private void grantChargeFeedback(Player owner, boolean fromRegen) {
        World world = owner.getWorld();
        Location mid = owner.getLocation().clone().add(0d, 1d, 0d);
        if (fromRegen) {
            RavenSound.ravenChargeStoredSound(world, owner.getLocation());
            RavenVfx.ravenChargeStored(world, mid);
        } else {
            RavenSound.ravenChargeFromKillSound(world, owner.getLocation());
            RavenVfx.ravenChargeFromKill(world, mid);
        }
    }

    // ───────── 瞄准模式 ─────────

    /** 当前瞄准模式（★ 非悬停时恒为 {@link AimMode#SINGLE}）。 */
    public AimMode aimMode() {
        return aimMode;
    }

    /** 是否处于锁定模式（主武器据此决定右键行为）。 */
    public boolean isAimingLock() {
        return aimMode == AimMode.LOCK;
    }

    /** **在空中左键时视为切换瞄准模式**（需求原话）—— 返回切换后的模式。 */
    public AimMode toggleAimMode() {
        aimMode = aimMode == AimMode.LOCK ? AimMode.SINGLE : AimMode.LOCK;
        Player owner = self();
        if (owner != null) {
            RavenSound.ravenAimModeToggleSound(owner.getWorld(), owner.getLocation(), aimMode == AimMode.LOCK);
        }
        return aimMode;
    }

    // ───────── actionbar HUD（本组件是唯一发布者）─────────

    /**
     * **主武器把弹药读数推进来**（需求：HUD 要显示"散华弹药量"）。
     *
     * <p>为什么是"推"：actionbar 是一行、后写覆盖先写 ⇒ 必须单一发布者。
     */
    public void publishAmmo(int current, int max) {
        this.hudAmmo = current;
        this.hudAmmoMax = max;
    }

    /** 组装并发布 actionbar（内容变了立刻发；没变则每 {@value #HUD_KEEPALIVE_TICKS} 刻保活重发）。 */
    private void publishHud(Player owner) {
        String text = hudPlainText(hoverActive, aimMode);
        if (!text.equals(lastHudText)) {
            lastHudText = text;
            hudTick = 0;
            owner.sendActionBar(hudComponent());
            return;
        }
        if (++hudTick >= HUD_KEEPALIVE_TICKS) {
            hudTick = 0;
            owner.sendActionBar(hudComponent());
        }
    }

    /** HUD 的彩色组件（分四段：状态[+浮空进度条] · 弹药 · 机动 · 瞄准）。 */
    private Component hudComponent() {
        NamedTextColor stateColor = hoverActive ? NamedTextColor.AQUA : NamedTextColor.GRAY;
        boolean lock = aimMode == AimMode.LOCK;
        Component head = Component.text(stateName(hoverActive), stateColor)
                .decorate(TextDecoration.BOLD);
        //★ 需求 2026-10-11："把浮空时间用进度条显示，放在「浮空」这个状态词条的后面"
        if (hoverActive) {
            head = head.append(Component.text(" ")).append(
                    Component.text(hoverBar(hoverRemainingTicks())).color(NamedTextColor.AQUA));
        }
        return head
                .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("散华 ").color(NamedTextColor.DARK_RED))
                .append(Component.text(ammoText()).color(NamedTextColor.RED))
                .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("机动 ").color(NamedTextColor.GOLD))
                .append(Component.text(chargeText()).color(NamedTextColor.YELLOW))
                .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(lock ? "锁定" : "自主",
                        lock ? NamedTextColor.RED : NamedTextColor.WHITE));
    }

    /**
     * HUD 的纯文本形态（判"内容是否变化"；与彩色版必须同源）。
     * <p>★ 进度条必须进这个签名：它**每刻都在变**（少一秒换一格），
     * 不进签名就会退化成"只有其它字段变化时才刷"⇒ 玩家看到的进度条顿住不动。
     */
    private String hudPlainText(boolean hovering, AimMode mode) {
        String bar = hovering ? hoverBar(hoverRemainingTicks()) : "";
        return stateName(hovering) + bar + "|" + ammoText() + "|" + chargeText() + "|" + mode.name();
    }

    /** **距浮空结束还有多少刻**（不在悬停中 ⇒ 0）。 */
    public int hoverRemainingTicks() {
        if (!hoverActive) {
            return 0;
        }
        return Math.max(0, hoverUntilTick - org.bukkit.Bukkit.getCurrentTick());
    }

    /**
     * **浮空剩余时间的进度条**（纯函数）——需求 2026-10-11："用 ■■■ 显示，少一秒就 ■■□"。
     *
     * <p>每段 1 秒、共 {@value #HOVER_BAR_SEGMENTS} 段（浮空 3.5 秒）：
     * <pre>
     *   剩余 3.5 秒（70 刻）⇒ ■■■
     *   剩余 2.5 秒（50 刻）⇒ ■■□
     *   剩余 1.5 秒（30 刻）⇒ ■□□
     *   剩余 0.5 秒（10 刻）⇒ □□□
     * </pre>
     * ★ 用"整秒数"而不是"四舍五入"，是为了**立刻**响应"少一秒"（该条需求的原话）。
     */
    public static String hoverBar(int remainingTicks) {
        int seconds = Math.max(0, remainingTicks) / 20;
        int filled = Math.min(HOVER_BAR_SEGMENTS, seconds);
        StringBuilder bar = new StringBuilder(HOVER_BAR_SEGMENTS);
        for (int i = 0; i < HOVER_BAR_SEGMENTS; i++) {
            bar.append(i < filled ? HOVER_BAR_FILLED : HOVER_BAR_EMPTY);
        }
        return bar.toString();
    }

    /** 状态名。 */
    private static String stateName(boolean hovering) {
        return hovering ? "浮空" : "地面";
    }

    /** 弹药读数文本（主武器还没推过 ⇒ {@code "—"}）。 */
    private String ammoText() {
        if (hudAmmo < 0 || hudAmmoMax < 0) {
            return "—";
        }
        return hudAmmo + "/" + hudAmmoMax;
    }

    /** 机动层数文本（实心/空心圆）。 */
    private String chargeText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_CHARGES; i++) {
            builder.append(i < charges ? '●' : '○');
        }
        return builder.toString();
    }

    // ───────── 供技能读 / 写的空中状态 ─────────

    /** 本次悬停测到的峰值离地高度（读口）。 */
    public double hoverPeakHeight() {
        return hoverPeakHeight;
    }

    /** **玩家当前的离地高度**（格）—— 供技能做"身下 1.5 格内有方块"的放松判定。 */
    public double currentHeightAboveGround() {
        Player owner = self();
        return owner == null ? Double.MAX_VALUE : heightAboveGround(owner);
    }

    /**
     * **显式武装落地冲击**（供技能三在"浮空中使用"时调用）。
     *
     * <p>需求："若在浮空机动状态中使用则不再跃起…且能和落地冲击被动一起触发"。
     * 技能三自己会把浮空清掉 ⇒ 若不显式把高度交回来，落地冲击就永远不会与它同时触发。
     */
    public void armLandingImpactForCurrentHeight() {
        armedFallHeight = Math.max(armedFallHeight, Math.max(hoverPeakHeight, currentHeightAboveGround()));
    }

    /**
     * **这次击杀是不是"顺手充了一层机动"**（供语音组件选"击杀后刷新机动"那一栏台词）。
     *
     * <p>★ 能读到正确值依赖**注册序**：本组件排在语音组件之前 ⇒ 击杀通知先到本组件
     * （充能 + 打标记）再到语音 ⇒ 语音那边读到的一定是"已经充过能"的状态。
     */
    public boolean killChargeJustGranted() {
        return org.bukkit.Bukkit.getCurrentTick() - lastKillChargeTick <= KILL_CHARGE_MARKER_TICKS;
    }

    /** **此刻是否在空中**（供语音组件区分"空中击杀"与"普通击杀"）。 */
    public boolean isAirborneNow() {
        Player owner = self();
        return owner != null && !isTouchingGround(heightAboveGround(owner));
    }

    /**
     * **现在能否起跳**（需求："只要角色身下 1.5 格内有方块就可以跃起"）。
     *
     * <p>本方法是"起跳判定"的**唯一出处**：技能一与技能三都调它 ⇒ 两处口径不可能分叉。
     */
    public boolean canTakeOffHere() {
        Player owner = self();
        if (owner == null) {
            return false;
        }
        Location at = owner.getLocation();
        boolean feetSolid = at.getBlock().getBoundingBox().getVolume() > 0d;
        return canTakeOff(feetSolid, heightAboveGround(owner));
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **悬停的垂直速度**（纯函数 ⇒ 可离线穷举）。
     *
     * <p>优先级：蹲 &gt; 跳（同时按视为"想下去"）。
     * <ul>
     *   <li>按蹲 ⇒ 向下、**越久越快**（{@link #fastFallSink(int)}）；</li>
     *   <li>按跳 ⇒ 缓慢上升（{@value #HOVER_RISE}）；</li>
     *   <li>都不按 ⇒ {@code 0}（**真悬停**，重力已关 ⇒ 不写速度就不动）。</li>
     * </ul>
     * ★ 按蹲**不再结束悬停**（见类注释"四条结束路径"）：它只让你更快地降到地面，
     * 落地由"触地"那条路径结束。
     */
    public static double hoverVerticalVelocity(boolean jump, boolean sneak, int sneakHoldTicks) {
        if (sneak) {
            return -fastFallSink(sneakHoldTicks);
        }
        return jump ? HOVER_RISE : HOVER_HOLD;
    }

    /**
     * **悬停的水平速度向量**（纯函数）。
     *
     * <p>算法沿原版 {@code Entity#moveRelative} 的旋转式：
     * {@code x = strafe·cos(yaw) − forward·sin(yaw)}、{@code z = forward·cos(yaw) + strafe·sin(yaw)}，
     * 其中 {@code forward} = 前进键（W 正 / S 负）、{@code strafe} = **左键**（A 正 / D 负）。
     * 对角输入会先归一化 ⇒ 斜着走不会更快。
     * <p>★ 无状态是关键：结果只取决于"这一帧按了什么键"，不会自己乘自己。
     */
    public static Vector hoverHorizontalVelocity(int forwardImpulse, int strafeImpulse,
                                                 double yawDegrees, double speed) {
        int forward = Integer.signum(forwardImpulse);
        int strafe = Integer.signum(strafeImpulse);
        if (forward == 0 && strafe == 0) {
            return new Vector(0d, 0d, 0d);
        }
        double radians = Math.toRadians(yawDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vector direction = new Vector(strafe * cos - forward * sin, 0d, forward * cos + strafe * sin);
        if (direction.lengthSquared() < 1.0E-9d) {
            return new Vector(0d, 0d, 0d);
        }
        return direction.normalize().multiply(Math.max(0d, speed));
    }

    /** **按蹲加速下落的下沉速度**（纯函数）——需求："按住蹲越久下落速度越快"。 */
    public static double fastFallSink(int heldTicks) {
        double ramp = FAST_FALL_BASE_SINK + FAST_FALL_RAMP_PER_TICK * Math.max(0, heldTicks);
        return Math.min(FAST_FALL_MAX_SINK, ramp);
    }

    /**
     * **是否构成"双击空格"**（纯函数）——需求："0.2S 内双击空格关闭悬停状态"。
     *
     * @param lastPressTick 上一次"按下跳键"的刻（{@code -1} = 从未按过）
     * @param jumpWasHeld   上一刻是否按着跳键（没有它就无法识别"按下"这个**边沿**）
     */
    public static boolean isDoubleTapJump(int nowTick, int lastPressTick, boolean jumpHeld, boolean jumpWasHeld) {
        if (!jumpHeld || jumpWasHeld) {
            return false;       // 不是"这一刻刚按下"
        }
        if (lastPressTick < 0) {
            return false;       // 这是本命第一次按
        }
        return nowTick - lastPressTick <= DOUBLE_TAP_WINDOW_TICKS;
    }

    /** **落地时是否该放落地冲击**（纯函数）。 */
    public static boolean shouldImpactOnLanding(double armedFallHeight) {
        return armedFallHeight >= LANDING_IMPACT_MIN_HEIGHT;
    }

    /** **是否应当消耗能量充一层机动**（纯函数）—— 满仓时**连能量都不该扣**。 */
    public static boolean shouldRegenCharge(int charges, int maxCharges, int energy) {
        return charges < maxCharges && energy >= CHARGE_REGEN_ENERGY_COST;
    }

    /**
     * **跃起判定**：脚那格空 且 **身下 1.5 格内有地面**。
     *
     * <p>★ 旧口径要求"正下方那一格是实心" ⇒ 站在半砖 / 台阶上、或离地 0.2 格时常常判不出来
     * （玩家感觉"右键没反应"）。新口径把"下方那格"放宽成"离地高度 ≤
     * {@value #TAKEOFF_GROUND_PROXIMITY} 格"。
     */
    public static boolean canTakeOff(boolean feetBlockSolid, double heightAboveGround) {
        return !feetBlockSolid && heightAboveGround <= TAKEOFF_GROUND_PROXIMITY;
    }

    /** **闭区间随机整数** {@code [min, max]}（需求写"3~6 点"，两端都含）。 */
    public static int rollInclusive(Random random, int min, int max) {
        if (random == null || max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /** **地面高度扫描**（从 {@code startBlockY} 向下找第一个实心方块，返回它顶面 Y）。 */
    public static double groundLevelY(int startBlockY, int minY, java.util.function.IntPredicate solidAt) {
        for (int by = startBlockY; by >= minY; by--) {
            if (solidAt.test(by)) {
                return by + 1d;
            }
        }
        return minY;
    }

    /**
     * **是否"踩在地上"**（纯函数）—— 判据 = 离地高度 ≤ {@value #GROUND_TOUCH_HEIGHT} 格。
     * <p>★ 不用"整格判据"的原因见 {@link #GROUND_TOUCH_HEIGHT} 的注释。
     */
    public static boolean isTouchingGround(double heightAboveGround) {
        return heightAboveGround <= GROUND_TOUCH_HEIGHT;
    }

    // ───────── 内部几何 / 输入 ─────────

    /** "从未"哨兵值（任何真实刻号都大于它）。 */
    private static final int NO_KILL_CHARGE = -1000;

    /** 一帧的输入快照（读一次、用多处）。 */
    private record InputState(boolean jump, boolean sneak, int forwardImpulse, int strafeImpulse) {
    }

    /**
     * 读玩家这一帧的真实按键（{@link Player#getCurrentInput()}）。
     *
     * <p>退化路径（如实申报）：旧版 / 极端情况下它可能抛 ⇒ 兜成
     * "只有蹲键（{@link Player#isSneaking()}）、没有方向与跳"。
     */
    private static InputState readInput(Player owner) {
        try {
            Input input = owner.getCurrentInput();
            if (input != null) {
                int forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
                int strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
                return new InputState(input.isJump(), input.isSneak(), forward, strafe);
            }
        } catch (RuntimeException ignored) {
            // 见上：退化成"只有蹲键"
        }
        return new InputState(false, owner.isSneaking(), 0, 0);
    }

    /** 本实例的玩家（离线 / 未绑定 ⇒ {@code null}；所有使用点都容忍它）。 */
    private Player self() {
        return svc().self() == null ? null : svc().self().player();
    }

    /** 几何取值 + 委托纯函数 {@link #isTouchingGround(double)}。 */
    private static boolean isOnGround(Player owner) {
        return isTouchingGround(heightAboveGround(owner));
    }

    /** 玩家脚下的**离地高度**（格）。 */
    private static double heightAboveGround(Player owner) {
        Location at = owner.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return 0d;
        }
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        double ground = groundLevelY(at.getBlockY() - 1, world.getMinHeight(),
                by -> world.getBlockAt(bx, by, bz).getBoundingBox().getVolume() > 0d);
        return at.getY() - ground;
    }
}
