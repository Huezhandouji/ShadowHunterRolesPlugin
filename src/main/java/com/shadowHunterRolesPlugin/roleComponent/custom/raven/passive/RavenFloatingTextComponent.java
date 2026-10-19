package com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.datapack.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.mainWeapon.RavenRevolverMainWeapon;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 「绯红渡鸦 · 林晨霜」的**语音库**（风格悬浮文本）。
 *
 * <h2>这套语音的定位（用户给的语气备注，逐条落到实现上）</h2>
 * <ul>
 *   <li><b>"像指挥，而不是单纯打架"</b> ⇒ 台词以"继续推进 / 保持警戒 / 交给你们"这类**战场指令**
 *       为主，而不是单纯喊打喊杀；</li>
 *   <li><b>"空中相关语音要干脆、稳定"</b> ⇒ 浮空 / 升空 / 俯冲三组一律**短句 + 陈述语气**，
 *       不拖泥带水；</li>
 *   <li>★ <b>"加大字号、加粗字体、使用多种风格字体"</b> ⇒ 三件事都是**全量覆盖**的：
 *       {@link #scaleFor(String)} 全部 ≥ {@value #MIN_SCALE}（比默认 1.0 明显更大）、
 *       {@link #lookFor(String)} **每一组都 {@code withBold()}`**、
 *       {@link #styleFor(String)} 在 45 种 {@link TextUtil.Style} 里按"语义"挑了 **15 种不同风格**。</li>
 * </ul>
 *
 * <h2>时刻分组（对应需求里的每一栏）</h2>
 * <table>
 *   <tr><th>需求栏</th><th>时刻常量</th><th>触发方式</th></tr>
 *   <tr><td>空闲</td><td>{@link #MOMENT_IDLE}</td><td>基类自己计时（安静 3 秒后随机吐一句）</td></tr>
 *   <tr><td>登场</td><td>{@link #MOMENT_SPAWN}</td><td>基类 {@code start()}</td></tr>
 *   <tr><td>近战命中 / 左轮命中</td><td>{@link #MOMENT_MELEE_HIT} / {@link #MOMENT_SHOT_HIT}</td>
 *       <td>主武器在 {@code onAttack} / 子弹命中时喊</td></tr>
 *   <tr><td>一技能 / 主动机动</td><td>{@link #MOMENT_SKILL_DASH} / {@link #MOMENT_SKILL_MOBILITY}</td>
 *       <td>技能一与主武器的空中前冲各喊一次</td></tr>
 *   <tr><td>二技能</td><td>{@link #MOMENT_SKILL_DEADEYE}</td><td>技能二 {@code onCast}</td></tr>
 *   <tr><td>三技能</td><td>{@link #MOMENT_SKILL_SOMERSAULT}</td><td>技能三 {@code onCast}</td></tr>
 *   <tr><td>普通击杀 / 空中击杀 / 击杀后刷新机动</td>
 *       <td>{@link #MOMENT_KILL}（三组按当时状态自动选一）</td>
 *       <td>基类击杀订阅；★ 选组在 {@link #lexiconNow()} 里做（见下）</td></tr>
 *   <tr><td>浮空机动中</td><td>{@link #MOMENT_HOVER}</td><td>本类 {@code update()} 里按间隔说</td></tr>
 *   <tr><td>落地冲击</td><td>{@link #MOMENT_LANDING}</td><td>浮空机动被动在结算落地冲击时喊</td></tr>
 *   <tr><td>低生命</td><td>{@link #MOMENT_LOW_HP}</td><td>基类（受伤后血量 ≤35% 触发）</td></tr>
 *   <tr><td>受击</td><td>{@link #MOMENT_HURT}</td><td>基类（框架受伤扇出）</td></tr>
 *   <tr><td>被控制 / 解控</td><td>{@link #MOMENT_CONTROLLED}</td>
 *       <td>本类**边沿判据**：技能闸门从"关"变"开"的那一刻</td></tr>
 *   <tr><td>弹药耗尽 / 换弹</td><td>{@link #MOMENT_RELOAD}</td>
 *       <td>本类读主武器的"换弹中 / 空仓"**边沿**</td></tr>
 *   <tr><td>无人可锁</td><td>{@link #MOMENT_NO_TARGET}</td><td>主武器蓄力结束但周围无敌人时喊</td></tr>
 *   <tr><td>死亡</td><td>{@link #MOMENT_DEATH}</td>
 *       <td>★ 在 {@code onDamaged} 里判"这一下把血打到 0"——**同步**在死亡事件里喊，
 *           赶在框架清角色之前（角色一清，池就关了，之后再喊也放不出来）</td></tr>
 * </table>
 *
 * <h2>★★ 为什么"击杀"要分三组，且只喊一句</h2>
 * 需求把击杀分成「普通 / 空中 / 击杀后刷新机动」三栏。若给它们各挂一个时刻，
 * 一次击杀会**同时**触发三次喊话（基类的击杀订阅 + 另外两条），池的自带间隔会随机丢掉两句
 * ⇒ 玩家看到的是"有时这句有时那句"，不可复现。
 * <p>✅ 本类的做法 = **只保留基类那一条击杀订阅**，在 {@link #lexiconNow()} 里
 * 按当时状态**选整本词库**（三本只有 {@code kill} 组不同）：
 * <ol>
 *   <li>这次击杀顺手充了机动（读 {@code flight.killChargeJustGranted()}）⇒ 用「击杀后刷新机动」组；</li>
 *   <li>否则人在空中（读 {@code flight.isAirborneNow()}）⇒ 用「空中击杀」组；</li>
 *   <li>否则 ⇒ 「普通击杀」组。</li>
 * </ol>
 * ★ 这条"读得到充能标记"依赖**注册序**：浮空机动被排在语音组件之前
 * ⇒ 它的击杀订阅先登记、先收到通知 ⇒ 语音这边读到的一定是"已经充过能"的状态。
 * 已在 {@code RoleLoader} 的装配注释里申报。
 *
 * <h2>性能</h2>
 * 悬浮文本是"每字一个实体"，因此本类所有高频时刻（命中 / 浮空 / 换弹）都走
 * {@link #saySpecial(Player, String)} 的**内部节流**（{@value #COMBAT_LINE_GAP_TICKS} 刻），
 * 叠在池自带的 {@code MIN_GAP_TICKS} 之上。台词一律 ≤ {@value TextUtil#MAX_CHARS} 字。
 */
public class RavenFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "raven_floating_text";

    // ───────── 时刻标识（需求逐栏） ─────────

    /** 近战命中。 */
    public static final String MOMENT_MELEE_HIT = "raven_melee_hit";

    /** 左轮命中。 */
    public static final String MOMENT_SHOT_HIT = "raven_shot_hit";

    /** 一技能：升空突袭。 */
    public static final String MOMENT_SKILL_DASH = "raven_skill_dash";

    /** 主动机动（空中丢出主武器前冲）。 */
    public static final String MOMENT_SKILL_MOBILITY = "raven_skill_mobility";

    /** 二技能：死亡之眼。 */
    public static final String MOMENT_SKILL_DEADEYE = "raven_skill_deadeye";

    /** 三技能：涅斯捷罗夫筋斗。 */
    public static final String MOMENT_SKILL_SOMERSAULT = "raven_skill_somersault";

    /** 浮空机动中（悬停期间按间隔说）。 */
    public static final String MOMENT_HOVER = "raven_hover";

    /** 落地冲击触发。 */
    public static final String MOMENT_LANDING = "raven_landing";

    /** 被控制 / 解控。 */
    public static final String MOMENT_CONTROLLED = "raven_controlled";

    /** 弹药耗尽 / 换弹。 */
    public static final String MOMENT_RELOAD = "raven_reload";

    /** 无人可锁 / 周围无敌人。 */
    public static final String MOMENT_NO_TARGET = "raven_no_target";

    /** 死亡。 */
    public static final String MOMENT_DEATH = "raven_death";

    // ───────── 节奏与外观参数 ─────────

    /** ★ 全组最小缩放（需求"加大字号"；默认是 1.0）。 */
    public static final float MIN_SCALE = 1.30f;

    /** 命中 / 状态类的高频台词内部节流（刻）——约 1 秒最多一句。 */
    public static final int COMBAT_LINE_GAP_TICKS = 20;

    /** 浮空机动中说话的间隔（刻）——约 4 秒一句。 */
    public static final int HOVER_LINE_INTERVAL_TICKS = 80;

    /** 浮空台词的下限（同样的台词不连着说）。 */
    private static final int HOVER_LINE_JITTER_TICKS = 60;

    // ───────── 主题色（与 RavenVfx 同一套：血绯 / 金 / 铁灰 / 冷白） ─────────

    /** 主色·绯红。 */
    private static final int CRIMSON = 0xC81A2E;

    /** 高光·金黄（"『改』"的同色）。 */
    private static final int GOLD = 0xFFC24A;

    /** 警戒·琥珀。 */
    private static final int AMBER = 0xFF9A2E;

    /** 冷白。 */
    private static final int PALE = 0xF2F2F8;

    /** 铁灰（指挥语气用低饱和色）。 */
    private static final int STEEL = 0xB0BAC6;

    /** 石板灰蓝（最克制的一档）。 */
    private static final int SLATE = 0x8A93A6;

    /** 重伤·暗红。 */
    private static final int DEEP_RED = 0x8C1220;

    // ───────── 运行状态 ─────────

    /** 上一次说"命中 / 状态类"台词的刻（{@code -1000} = 从未）。 */
    private int lastCombatLineTick = -1000;

    /** 下一次说"浮空机动中"台词的刻。 */
    private int nextHoverLineTick = -1;

    /** 上一刻主武器是否在换弹 / 空仓（边沿判据）。 */
    private boolean weaponBusyLastTick;

    /** 上一刻技能闸门是否打开（边沿判据 ⇒ 解控）。 */
    private boolean castReadyLastTick;

    /** 是否见过"技能闸门关闭"（避免登录第一刻平白喊一句"控制解除"）。 */
    private boolean controlSeenClosed;

    /** 这次受伤是否已经喊过死亡台词（防止同一具尸体连喊）。 */
    private boolean deathSaid;

    public RavenFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 对外：技能 / 武器喊话的入口 ─────────

    /**
     * **技能台词**（一次连说 1~2 句）——技能在 {@code onCast} 里调它。
     *
     * @return 实际排入的句数
     */
    public int saySkill(Player who, String moment) {
        if (moment == null) {
            return 0;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now - lastCombatLineTick < COMBAT_LINE_GAP_TICKS) {
            return 0;       //与命中台词共用同一道闸门 ⇒ 打得很密时不会"技能+命中"叠字
        }
        lastCombatLineTick = now;
        return sayBurst(who, moment, 1, 2, castBurstGapTicks());
    }

    /**
     * **状态 / 命中台词**（单句，自带内部节流）。
     *
     * <p>近战 0.4 秒一次、子弹 0.1 秒一发、浮空台词每几秒一次 ⇒ 不节流就是复读机。
     *
     * @return 是否真的放出去了
     */
    public boolean saySpecial(Player who, String moment) {
        if (moment == null) {
            return false;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now - lastCombatLineTick < COMBAT_LINE_GAP_TICKS) {
            return false;
        }
        lastCombatLineTick = now;
        return say(who, moment, null);
    }

    // ───────── 每刻：浮空 / 换弹 / 解控 三条自观察通道 ─────────

    /**
     * 每刻：先做三条**自观察**（不需要任何组件来喊），再交给基类跑空闲逻辑。
     *
     * <ol>
     *   <li><b>浮空机动中</b>：悬停期间按 {@value #HOVER_LINE_INTERVAL_TICKS} 刻说一句；</li>
     *   <li><b>弹药耗尽 / 换弹</b>：读主武器的"换弹中 / 空仓"**边沿**（进入那一刻说一句）；</li>
     *   <li><b>被控制 / 解控</b>：读技能闸门的**边沿**（从"关"变"开"= 控制解除）。</li>
     * </ol>
     * <p>★ 三条都用**边沿判据**（存上一帧的布尔），否则会退化成"每刻复读"。
     */
    @Override
    public void update() {
        Player me = me();
        if (me != null && me.isOnline()) {
            observeHover(me);
            observeWeaponBusy(me);
            observeControlReleased(me);
        }
        super.update();
    }

    /** 浮空机动中：悬停期间按间隔说话（进入悬停时先来一句）。 */
    private void observeHover(Player me) {
        RavenAirMobilityPassive flight = findComponent(RavenAirMobilityPassive.class);
        if (flight == null || !flight.isHovering()) {
            nextHoverLineTick = -1;     //离开悬停 ⇒ 下次进入重新计时
            return;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (nextHoverLineTick < 0) {
            nextHoverLineTick = now + HOVER_LINE_INTERVAL_TICKS + 1;
            say(me, MOMENT_HOVER, null);
            return;
        }
        if (now >= nextHoverLineTick) {
            if (say(me, MOMENT_HOVER, null)) {
                nextHoverLineTick = now + HOVER_LINE_INTERVAL_TICKS
                        + java.util.concurrent.ThreadLocalRandom.current().nextInt(HOVER_LINE_JITTER_TICKS);
            } else {
                nextHoverLineTick = now + 20;   //被节流拒了 ⇒ 稍后再试，不要每刻重试
            }
        }
    }

    /** 弹药耗尽 / 换弹：读主武器的状态**边沿**。 */
    private void observeWeaponBusy(Player me) {
        RavenRevolverMainWeapon weapon = findComponent(RavenRevolverMainWeapon.class);
        boolean busy = weapon != null && (weapon.reloading() || weapon.ammo() <= 0);
        if (busy && !weaponBusyLastTick) {
            say(me, MOMENT_RELOAD, null);
        }
        weaponBusyLastTick = busy;
    }

    /** 被控制 / 解控：技能闸门从"关"变"开"的那一刻 = 控制解除。 */
    private void observeControlReleased(Player me) {
        BuffComponent buff = findComponent(BuffComponent.class);
        boolean ready = buff == null || buff.canCastSkill();
        if (ready && !castReadyLastTick && controlSeenClosed) {
            say(me, MOMENT_CONTROLLED, null);
        }
        if (!ready) {
            controlSeenClosed = true;
        }
        castReadyLastTick = ready;
    }

    // ───────── 承受方回调：受伤 / 濒死 / 死亡 ─────────

    /**
     * 受伤通知（框架扇出；只观察）。
     *
     * <p>★ **死亡台词在这里喊**：这一下伤害把血打到 0 时，本方法是在
     * {@code PlayerDeathEvent}/{@code EntityDamageEvent} 的同步路径里被调到的
     * ——**早于**框架清角色（清角色会把本组件的池关掉，之后再喊就没地方放了）。
     */
    @Override
    public void onDamaged(Player source, double amount) {
        Player me = me();
        if (me != null && me.getHealth() <= 0d && !deathSaid) {
            deathSaid = true;
            say(me, MOMENT_DEATH, null);
            return;     //临终不叠"受伤 / 濒死"两句
        }
        super.onDamaged(source, amount);
    }

    // ───────── 钩子覆写：词库 / 风格 / 观感 / 节奏 ─────────

    @Override
    public void start() {
        deathSaid = false;
        weaponBusyLastTick = false;
        castReadyLastTick = true;
        controlSeenClosed = false;
        nextHoverLineTick = -1;
        super.start();
    }

    /**
     * ★ **击杀三组的选择点**（见类注释）：每次说话都问一次，因此能反映"当时"的状态。
     */
    @Override
    protected TextUtil.Lexicon lexiconNow() {
        RavenAirMobilityPassive flight = findComponent(RavenAirMobilityPassive.class);
        if (flight != null) {
            if (flight.killChargeJustGranted()) {
                return LEXICON_KILL_CHARGE;
            }
            if (flight.isAirborneNow()) {
                return LEXICON_KILL_AIR;
            }
        }
        return LEXICON_GROUND;
    }

    /** 本组件只有一本词库（三本只有击杀组不同）⇒ 由 {@link #lexiconNow()} 选。 */
    @Override
    protected TextUtil.Lexicon lexicon() {
        return LEXICON_GROUND;
    }

    /**
     * ★ **风格（"使用多种风格字体"）**：在 45 种 {@link TextUtil.Style} 里按语义挑了 15 种 ——
     * 升空用上浮、俯冲用下落、点名用居中打字、解控用乱码解码、换弹用打字机、
     * 落地冲击用**落地砸字**（会逐字查地面）、死亡用模糊消散。
     */
    @Override
    protected TextUtil.Style styleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> TextUtil.Style.PLAIN_FADE;                  //空闲：克制
            case MOMENT_SPAWN -> TextUtil.Style.GROW_SLOW;                  //登场：缓缓放大
            case MOMENT_MELEE_HIT -> TextUtil.Style.POP_SCALE;              //近战：一记短促
            case MOMENT_SHOT_HIT -> TextUtil.Style.SLIDE_LEFT_IN;           //左轮：横向扫过
            case MOMENT_CAST, MOMENT_SKILL_DASH -> TextUtil.Style.FLOAT_UP_IN;   //施法 / 升空：上浮
            case MOMENT_SKILL_MOBILITY -> TextUtil.Style.SPIN_RIGHT_IN;     //主动机动：旋转
            case MOMENT_SKILL_DEADEYE -> TextUtil.Style.CENTER_IN_CENTER_OUT;   //点名：居中打字
            case MOMENT_SKILL_SOMERSAULT -> TextUtil.Style.DROP_DOWN_IN;    //俯冲：下落
            case MOMENT_HOVER -> TextUtil.Style.FLOAT_UP_IN;                //悬停：上浮
            case MOMENT_LANDING -> TextUtil.Style.GROUND_SMASH;             //落地冲击：砸字
            case MOMENT_KILL -> TextUtil.Style.POP_STRETCH_Y;               //击杀：纵向弹开
            case MOMENT_CONTROLLED -> TextUtil.Style.DECODE_IN;             //解控：乱码解码
            case MOMENT_RELOAD -> TextUtil.Style.TYPEWRITER_FADE;           //装填：打字机
            case MOMENT_NO_TARGET -> TextUtil.Style.RANDOM_FADE_IN_RANDOM_OUT;   //搜索：零散隐现
            case MOMENT_HURT -> TextUtil.Style.FLIP_RANDOM_Y_IN;            //受击：翻面
            case MOMENT_LOW_HP -> TextUtil.Style.TYPEWRITER_BACKSPACE_BOTH;  //濒死：断续打字
            case MOMENT_DEATH -> TextUtil.Style.OBFUSCATE_OUT;              //死亡：模糊消散
            default -> TextUtil.Style.PLAIN_FADE;
        };
    }

    /**
     * ★ **外观（"加粗字体"）**：**每一组都 {@code withBold()}**，配色按语气分档
     * （指挥=铁灰 / 命中=绯红与金 / 危机=暗红颤抖 / 高光=泛光白）。
     */
    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> TextUtil.Look.colored(SLATE).withBold();
            case MOMENT_SPAWN -> TextUtil.Look.glowOf(CRIMSON).withBold();
            case MOMENT_MELEE_HIT -> TextUtil.Look.colored(CRIMSON).withBold();
            case MOMENT_SHOT_HIT -> TextUtil.Look.colored(GOLD).withBold();
            case MOMENT_CAST, MOMENT_SKILL_DASH -> TextUtil.Look.glowOf(CRIMSON).withBold();
            case MOMENT_SKILL_MOBILITY -> TextUtil.Look.gradientOf(AMBER).withBold();
            case MOMENT_SKILL_DEADEYE -> TextUtil.Look.glowOf(GOLD).withBold();
            case MOMENT_SKILL_SOMERSAULT -> TextUtil.Look.gradientOf(CRIMSON).withTremble().withBold();
            case MOMENT_HOVER -> TextUtil.Look.colored(STEEL).withBold();
            case MOMENT_LANDING -> TextUtil.Look.gradientOf(CRIMSON).withTremble().withBold();
            case MOMENT_KILL -> TextUtil.Look.glowOf(PALE).withBold();
            case MOMENT_CONTROLLED -> TextUtil.Look.colored(DEEP_RED).withTremble().withBold();
            case MOMENT_RELOAD -> TextUtil.Look.colored(STEEL).withBold();
            case MOMENT_NO_TARGET -> TextUtil.Look.colored(SLATE).withBold();
            case MOMENT_HURT -> TextUtil.Look.colored(DEEP_RED).withBold();
            case MOMENT_LOW_HP -> TextUtil.Look.colored(DEEP_RED).withTremble().withBold();
            case MOMENT_DEATH -> TextUtil.Look.gradientOf(DEEP_RED).withBold();
            default -> TextUtil.Look.colored(STEEL).withBold();
        };
    }

    /**
     * ★ **字号（"加大字号"）**：全部 ≥ {@link #MIN_SCALE}（默认是 1.0）——
     * 越"重"的场合越大：命中 1.45 / 技能 1.55 / 击杀 1.60 / 落地冲击 1.70。
     */
    @Override
    protected float scaleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> 1.30f;
            case MOMENT_HOVER, MOMENT_RELOAD, MOMENT_NO_TARGET -> 1.35f;
            case MOMENT_SPAWN, MOMENT_CONTROLLED -> 1.50f;
            case MOMENT_HURT, MOMENT_LOW_HP -> 1.40f;
            case MOMENT_MELEE_HIT, MOMENT_SHOT_HIT -> 1.45f;
            case MOMENT_CAST, MOMENT_SKILL_DASH, MOMENT_SKILL_MOBILITY,
                 MOMENT_SKILL_DEADEYE, MOMENT_SKILL_SOMERSAULT -> 1.55f;
            case MOMENT_KILL -> 1.60f;
            case MOMENT_LANDING -> 1.70f;
            case MOMENT_DEATH -> 1.55f;
            default -> MIN_SCALE;
        };
    }

    /** 节奏：指挥语气偏稳（90~110），命中与技能略快，死亡最慢。 */
    @Override
    protected int speedFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE, MOMENT_HOVER -> 100;
            case MOMENT_DEATH -> 70;
            case MOMENT_MELEE_HIT, MOMENT_SHOT_HIT -> 140;
            case MOMENT_CAST, MOMENT_SKILL_DASH, MOMENT_SKILL_MOBILITY,
                 MOMENT_SKILL_DEADEYE, MOMENT_SKILL_SOMERSAULT -> 150;
            case MOMENT_KILL -> 145;
            default -> 115;
        };
    }

    /** 落地砸字的起落高度（格）——只有 {@link #MOMENT_LANDING} 用得上。 */
    @Override
    protected double groundOffsetFor(String moment) {
        return MOMENT_LANDING.equals(moment) ? 6.0d : 4.0d;
    }

    /** 空闲 / 濒死都启用（本角色的语音量本来就该多）。 */
    @Override
    protected boolean idleEnabled() {
        return true;
    }

    @Override
    protected boolean lowHpEnabled() {
        return true;
    }

    // ───────── 内部工具 ─────────

    /** 自己（拿不到 ⇒ {@code null}）。 */
    private Player me() {
        try {
            return svc().self() == null ? null : svc().self().player();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ───────── 词库（三本只差"击杀"一组） ─────────

    private static final String[] IDLE_LINES = {
            "空域干净。各小队保持通讯。",
            "别盯着我的腿看，它比你想的贵。",
            "所以我得替你们盯着。",
            "渡鸦，待命。……这代号谁取的？",
            "地面交给你们。",
            "如果我没回话，可能是在空中。别慌。",
            "任务结束之后，我请客。前提是都活着。",
            "我不喜欢战争，但我擅长让它快点结束。"
    };

    private static final String[] SPAWN_LINES = {
            "绯红渡鸦——到岗。",
            "空中与地面，我一并接管。",
            "散华已上膛。开始吧。"
    };

    private static final String[] MELEE_HIT_LINES = {
            "近身？你选错了对手。",
            "义体不光是用来飞的。",
            "让开，我不想在你身上浪费子弹。",
            "敲掉一个。",
            "别贴太近。",
            "这一下，算你倒霉。"
    };

    private static final String[] SHOT_HIT_LINES = {
            "命中。",
            "下一个。",
            "别动，准星已经在你身上了。",
            "全威力弹。",
            "左轮不是摆设。",
            "命中确认，继续推进。"
    };

    private static final String[] SKILL_DASH_LINES = {
            "升空。视野给我。",
            "我已就位。",
            "地面掩护，我从上方进攻。",
            "别抬头，干好你自己的工作。",
            "空中机动，准备锁定。",
            "我绕一圈，你们稳住。"
    };

    private static final String[] SKILL_DEADEYE_LINES = {
            "锁定。",
            "都别动，我来点名。",
            "装填完毕。",
            "一个都跑不了。",
            "空中还是地面？我都能打。",
            "目标太多？那就排队。",
            "别站在我视野里，除非你是队友。"
    };

    private static final String[] SKILL_SOMERSAULT_LINES = {
            "筋斗——俯冲。",
            "标记落点，别站圈里。",
            "见过特技表演吗？",
            "落地区域清空，重复，清空。",
            "这一下，地面会记住。"
    };

    private static final String[] HOVER_LINES = {
            "浮空稳定，锁定中。",
            "我在上面，你们放心。",
            "空中视野良好。",
            "让我看看你们在哪。",
            "悬停不是休息。"
    };

    private static final String[] LANDING_LINES = {
            "落地——清场。",
            "站稳了，冲击来了。",
            "我下来了，别站太近。",
            "落地确认，周围安全。"
    };

    private static final String[] LOW_HP_LINES = {
            "还能飞。别管我。",
            "义体在响……但没断。",
            "我没事，继续任务。",
            "别过来，守住你们的位置。"
    };

    /**
     * 受击（★ <b>需求没有单列这一栏</b>，但基类的"受伤扇出"必然会在每一次挨打时触发那一刻 ——
     * 留空就等于每次挨打都静默。因此按同一套语气补了 4 句：**总队长 + 义体**的口吻，
     * 陈述而不叫苦，与"低生命"那栏（真的快死了）区分开）。
     */
    private static final String[] HURT_LINES = {
            "被咬了。无碍。",
            "义体吃住了。继续。",
            "擦伤而已。别分心。",
            "我还在。守住阵线。"
    };

    private static final String[] CONTROLLED_LINES = {
            "控制解除。继续。",
            "想困住我？我的腿不是白装的。",
            "别碍事。"
    };

    private static final String[] RELOAD_LINES = {
            "换弹中，掩护我。",
            "给我一秒。",
            "别催，我在装。"
    };

    private static final String[] NO_TARGET_LINES = {
            "没有目标。继续搜索。",
            "空域干净，保持警戒。",
            "他们躲起来了。别放松。"
    };

    private static final String[] DEATH_LINES = {
            "……小队，交给你们了。",
            "别为我停……继续走。",
            "天空………"
    };

    private static final String[] KILL_GROUND_LINES = {
            "目标清除。继续。",
            "倒下一个。",
            "别停，还有下一个。",
            "确认击杀。",
            "这就是挡路的下场。",
            "小队，推进。"
    };

    private static final String[] KILL_AIR_LINES = {
            "空中清除。",
            "从上面看，你们太慢了。",
            "收工。"
    };

    private static final String[] KILL_CHARGE_LINES = {
            "机动就绪，继续。",
            "又充能了。别浪费。",
            "不经打啊。"
    };

    /** 普通击杀（默认那本）。 */
    private static final TextUtil.Lexicon LEXICON_GROUND = build(KILL_GROUND_LINES);

    /** 空中击杀。 */
    private static final TextUtil.Lexicon LEXICON_KILL_AIR = build(KILL_AIR_LINES);

    /** 击杀后顺手充了机动。 */
    private static final TextUtil.Lexicon LEXICON_KILL_CHARGE = build(KILL_CHARGE_LINES);

    // ───────── 探针读口（同包离线单测用；都是只读，不改任何状态） ─────────

    /** **本角色全部时刻**（按需求的分栏顺序）——单测据此遍历"每一组都要有台词 / 都要加粗 / 都要放大"。 */
    static final List<String> ALL_MOMENTS = List.of(
            MOMENT_IDLE, MOMENT_SPAWN, MOMENT_MELEE_HIT, MOMENT_SHOT_HIT,
            MOMENT_SKILL_DASH, MOMENT_SKILL_MOBILITY, MOMENT_SKILL_DEADEYE, MOMENT_SKILL_SOMERSAULT,
            MOMENT_HOVER, MOMENT_LANDING, MOMENT_CONTROLLED, MOMENT_RELOAD,
            MOMENT_NO_TARGET, MOMENT_HURT, MOMENT_LOW_HP, MOMENT_KILL, MOMENT_DEATH);

    /** **全部台词**（按组展平）——校验"每句都放得进单段字数上限"。 */
    static List<String> allLines() {
        List<String> out = new java.util.ArrayList<>();
        for (String[] group : List.of(IDLE_LINES, SPAWN_LINES, MELEE_HIT_LINES, SHOT_HIT_LINES,
                SKILL_DASH_LINES, SKILL_DEADEYE_LINES, SKILL_SOMERSAULT_LINES, HOVER_LINES,
                LANDING_LINES, LOW_HP_LINES, HURT_LINES, CONTROLLED_LINES, RELOAD_LINES,
                NO_TARGET_LINES, DEATH_LINES, KILL_GROUND_LINES, KILL_AIR_LINES, KILL_CHARGE_LINES)) {
            out.addAll(List.of(group));
        }
        return List.copyOf(out);
    }

    /**
     * **三种击杀台词组**（校验"三组确实不同、且都被选得到"）。
     *
     * @param chargeJustGranted 这次击杀是否顺手充了机动
     * @param airborne          是否在空中击杀
     */
    static List<String> killLines(boolean chargeJustGranted, boolean airborne) {
        TextUtil.Lexicon picked = chargeJustGranted ? LEXICON_KILL_CHARGE
                : (airborne ? LEXICON_KILL_AIR : LEXICON_GROUND);
        return picked.candidates(MOMENT_KILL);
    }

    /** 组装整本词库（三本只差"击杀"那一组）。 */
    private static TextUtil.Lexicon build(String[] killLines) {
        return lexiconOf(
                MOMENT_IDLE, IDLE_LINES,
                MOMENT_SPAWN, SPAWN_LINES,
                MOMENT_MELEE_HIT, MELEE_HIT_LINES,
                MOMENT_SHOT_HIT, SHOT_HIT_LINES,
                MOMENT_SKILL_DASH, SKILL_DASH_LINES,
                //主动机动与一技能同一组（需求把两栏写在一起）
                MOMENT_SKILL_MOBILITY, SKILL_DASH_LINES,
                MOMENT_SKILL_DEADEYE, SKILL_DEADEYE_LINES,
                MOMENT_SKILL_SOMERSAULT, SKILL_SOMERSAULT_LINES,
                MOMENT_HOVER, HOVER_LINES,
                MOMENT_LANDING, LANDING_LINES,
                MOMENT_LOW_HP, LOW_HP_LINES,
                MOMENT_HURT, HURT_LINES,
                MOMENT_CONTROLLED, CONTROLLED_LINES,
                MOMENT_RELOAD, RELOAD_LINES,
                MOMENT_NO_TARGET, NO_TARGET_LINES,
                MOMENT_DEATH, DEATH_LINES,
                MOMENT_KILL, killLines
        );
    }

    // ───────── 描述符 ─────────

    /**
     * 本组件的被动描述符。
     *
     * <p>★ 泛型自类型（{@code Specification<自类>}）是硬要求 —— 否则对外声明的类型停在
     * {@code FloatingTextComponent}，别的组件 {@code requires(RavenFloatingTextComponent.class)}
     * 会报"missing component type"（工程踩过，见 §4）。
     */
    public static final class Specification
            extends FloatingTextComponent.Specification<RavenFloatingTextComponent> {

        public Specification() {
            super(Component.text("散华语音"),
                    List.of(
                            Component.text("空闲：8 句氛围台词（指挥口吻）"),
                            Component.text("命中：近战 6 句 / 左轮 6 句"),
                            Component.text("技能：升空突袭与主动机动 6 句 / 死亡之眼 7 句 / 筋斗 5 句"),
                            Component.text("击杀：普通 6 句 / 空中 3 句 / 顺手充能 3 句（按当时状态自动选一组）"),
                            Component.text("状态：浮空机动中 5 句 / 落地冲击 4 句 / 受击 4 句 / 濒死 4 句 / 解控 3 句"),
                            Component.text("其他：换弹 3 句 / 无人可锁 3 句 / 死亡 3 句"),
                            Component.text("全部台词加大字号（≥1.3 倍）、加粗，并按语义选用 15 种不同的浮现风格")
                    ));
            //★ 击杀订阅 / 受伤扇出都要它（基类走 getComponent，这里显式声明以堵住装配期漏配）
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new RavenFloatingTextComponent(id, services, this);
        }
    }
}
