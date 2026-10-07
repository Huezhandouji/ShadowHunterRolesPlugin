package com.shadowHunterRolesPlugin.datapack;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ★ **风格悬浮文本组件**（被动家族）—— 角色在**空闲 / 施法 / 受击 / 击杀**等状态改变时，
 * 于周身随机位置浮现风格化文本。
 *
 * <h2>它用的工具箱</h2>
 * 全部渲染能力来自 {@link com.shadowHunterRolesPlugin.core.util.TextUtil}
 * （与 {@code SoundUtil} / {@code ParticleUtil} 同目录的工具类，**不依赖任何数据包**）。
 * 本类只负责"**什么时候说话**"的策略，说话这件事本身交给工具箱。
 *
 * <h2>为什么是"被动"</h2>
 * 不占热键栏、没有冷却、没有耗能，只在后台观察状态 ⇒ 天然是 {@link PassiveSkill}
 * （编译期事实：被动描述符里根本没有 {@code setSlot}）。
 *
 * <h2>它怎么感知"状态改变"（三条来源，全部走既有框架挂载点，零框架改动）</h2>
 * <ol>
 *   <li><b>空闲</b>：{@link #update()} 里自己计时，连续 N 刻"没在打架"就吐一句氛围字；</li>
 *   <li><b>受击 / 濒死</b>：本组件实现 {@link VitalsComponent.Participant}，
 *       {@code onDamaged} 由框架的 {@code DamageHookListener} 在真实受伤事件里自动扇出
 *       （只观察、不改事件、不取消 ⇒ 与任何战斗逻辑零冲突）；</li>
 *   <li><b>击杀</b>：{@code start()} 里经 {@link VitalsComponent#addPlayerKilledListener}
 *       订阅"自己击杀了敌人"；</li>
 *   <li><b>施法 / 自定义</b>：由**其它组件显式调用** {@link #say(Player, String)} ——
 *       你的技能在 {@code onCast} 里喊一句就完事。</li>
 * </ol>
 *
 * <h2>与框架的关系（铁律：不动框架）</h2>
 * 本组件是**普通角色组件**：走 {@code start()} / {@code update()} / {@code stop()} 既有生命周期，
 * 由 {@code Role.Builder.addComponent(...)} 注册。它引用的两个框架挂载点
 * （{@code Participant}、{@code addPlayerKilledListener}）都是**既有公开面**，
 * 不新增任何框架能力、不修改任何禁改区文件。
 *
 * <h2>性能边界（必须遵守）</h2>
 * 悬浮文本是"每字一个展示实体"，比粒子贵得多。本组件因此内置三层闸门：
 * ① 池内同时最多 {@link TextUtil.Pool#MAX_SEGMENTS} 段；
 * ② 单段最多 {@link TextUtil#MAX_CHARS} 字；
 * ③ 全局最小间隔 {@link TextUtil.Pool#MIN_GAP_TICKS} 刻。
 * 别把这些阈值调大 —— 满编服务器上几十个玩家同时吐字就是几百个实体。
 */
public class FloatingTextComponent extends PassiveSkill implements VitalsComponent.Participant {

    /** 本组件的登记 id（注册处与显式调用点都用它）。 */
    public static final String ID = "floatingText";

    // ───────── 时刻标识（组件间"喊话"用的词汇） ─────────

    /** 空闲氛围字。 */
    public static final String MOMENT_IDLE = "idle";

    /** 施放技能。 */
    public static final String MOMENT_CAST = "cast";

    /** 受到伤害。 */
    public static final String MOMENT_HURT = "hurt";

    /** 击杀敌人。 */
    public static final String MOMENT_KILL = "kill";

    /** 濒死（血量低）。 */
    public static final String MOMENT_LOW_HP = "lowhp";

    /** 登场 / 选中角色。 */
    public static final String MOMENT_SPAWN = "spawn";

    // ───────── 摆放与节奏参数 ─────────

    /** 周身摆放：最小半径（格）。 */
    private static final double RADIUS_MIN = 0.9d;

    /** 周身摆放：最大半径（格）。 */
    private static final double RADIUS_MAX = 1.8d;

    /** 周身摆放：最低高度（格，相对脚底）。 */
    private static final double HEIGHT_MIN = 1.1d;

    /** 周身摆放：最高高度（格）。 */
    private static final double HEIGHT_MAX = 2.2d;

    /** 空闲氛围字的最小间隔（刻）。 */
    private static final int IDLE_INTERVAL_TICKS = 45;

    /** 空闲氛围字的随机抖动上限（刻）⇒ 间隔落在 [INTERVAL, INTERVAL+JITTER)。 */
    private static final int IDLE_JITTER_TICKS = 40;

    /** 空闲判定的"最近打过架"窗口（刻）：这段时间内受过伤或施过法 ⇒ 不算空闲。 */
    private static final int IDLE_QUIET_TICKS = 60;

    /** 濒死阈值：当前血量 / 最大血量 ≤ 此值 ⇒ 触发濒死字。 */
    private static final double LOW_HP_RATIO = 0.35d;

    /** 每段文本的滞留刻数（{@code < 0} ⇒ 工具箱默认）。 */
    private static final int STAY_TICKS = -1;

    /** 数据包口径的速度。 */
    private static final int SPEED = 80;

    /** 落地砸字：字从基准点上方多少格开始往下掉（越大掉得越久）。 */
    private static final double CAST_GROUND_OFFSET = 5d;

    // ───────── 实例状态 ─────────

    /** 悬浮文本池（本组件唯一持有的"渲染重量"）。 */
    private TextUtil.Pool pool;

    /** 词库（由子类经 {@link #lexicon()} 提供）。 */
    private TextUtil.Lexicon lexicon;

    /** 随机源（自持 ⇒ 不与其它组件抢用全局 Random）。 */
    private final Random random = new Random();

    /** 击杀监听登记（{@code stop()} 里按引用移除）。 */
    private VitalsComponent.PlayerKilledListener killedEntry;

    /** 组件自己的刻计数（每 tick +1；空闲 / 打架窗口都用它，不依赖 Bukkit 计时器 ⇒ 可离线测）。 */
    private int tickCounter;

    /** 最近一次"打过架"的刻（受伤 / 施法 / 击杀都会刷新它）。 */
    private int lastCombatTick;

    /** 下一次空闲吐字的刻。 */
    private int nextIdleTick;

    /** 组件是否已启动（防止 {@code update()} 在 {@code start()} 之前跑）。 */
    private boolean started;

    public FloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 生命周期 ─────────

    /**
     * {@code start()}：建池、订阅击杀、初始化计时。
     * <p>依赖只在 {@code start()} 取（工程铁律）。
     */
    @Override
    public void start() {
        this.pool = TextUtil.newPool(this);
        this.lexicon = lexicon();
        this.lastCombatTick = 0;
        this.nextIdleTick = IDLE_INTERVAL_TICKS + random.nextInt(IDLE_JITTER_TICKS);
        registerKillListener();
        this.started = true;

        // 登场字：选中角色时先来一句（"状态改变"的最典型场景）
        say(self(), MOMENT_SPAWN, null);
    }

    /**
     * {@code stop()}：摘击杀订阅、清池。
     * <p>框架在 {@code stop()} 返回后还会兜底回收本组件登记的资源；
     * 但展示实体**不在**框架资源表里（它们是世界实体）⇒ 必须在这里显式清掉，
     * 否则玩家换角色后字会永久留在原地。
     */
    @Override
    public void stop() {
        this.started = false;
        unregisterKillListener();
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    /**
     * 每刻推进（20 Hz）。
     * <p>本方法是本组件唯一的"节拍器"：池的推进与空闲判定都在这里，
     * 不额外注册任何 Bukkit 任务（那样会多一条生命周期，还得自己防守玩家下线）。
     */
    @Override
    public void update() {
        if (!started || pool == null) {
            return;
        }
        tickCounter++;
        pool.tick();

        Player self = self();
        if (self == null || !self.isOnline()) {
            return;
        }
        if (!idleEnabled()) {
            return;
        }

        boolean quiet = tickCounter - lastCombatTick >= IDLE_QUIET_TICKS;
        if (quiet && tickCounter >= nextIdleTick) {
            if (say(self, MOMENT_IDLE, null)) {
                nextIdleTick = tickCounter + IDLE_INTERVAL_TICKS + random.nextInt(IDLE_JITTER_TICKS);
            } else {
                // 被节流拒了 ⇒ 稍后再试，不要每刻重试（否则节流一过就连吐）
                nextIdleTick = tickCounter + 20;
            }
        }
    }

    // ───────── 对外：让别的组件"喊一句" ─────────

    /**
     * 吐一句风格悬浮文本（**供其它组件调用**）。
     *
     * <p>这是本组件与技能之间的**唯一接口**：技能在 {@code onCast} 里写
     * {@code floatingText.say(player, FloatingTextComponent.MOMENT_CAST, "自定义台词")} 即可。
     * 传 {@code null} 台词 ⇒ 从词库该时刻的候选里随机取。
     *
     * @param who    主体（通常就是自己）；{@code null} ⇒ 忽略
     * @param moment 时刻标识（见本类常量）；{@code null} ⇒ {@link #MOMENT_IDLE}
     * @param text   自定义文本；{@code null} / 空白 ⇒ 走词库
     * @return 是否真的放出去了（未启动 / 被节流 / 无候选 ⇒ {@code false}）
     */
    public boolean say(Player who, String moment, String text) {
        return say(who, moment, text, null, null);
    }

    /**
     * ★ **施法专用**：一次吐 1~3 句连续台词（技能释放时用）。
     *
     * <p>技能里就写这一行：{@code floatingText.onCast(self());}
     * <p>{@link #MOMENT_CAST} 的句数范围 / 风格 / 外观由
     * {@link #castBurstMin()}、{@link #castBurstMax()}、{@link #styleFor(String)}、
     * {@link #lookFor(String)} 决定（子类覆写即可定制）。
     *
     * @return 实际排入的句数
     */
    public int onCast(Player who) {
        return sayBurst(who, MOMENT_CAST, castBurstMin(), castBurstMax(), castBurstGapTicks());
    }

    /** 施法连排的**最少**句数（默认 1）。 */
    protected int castBurstMin() {
        return 1;
    }

    /** 施法连排的**最多**句数（默认 3）。 */
    protected int castBurstMax() {
        return 3;
    }

    /** 施法连排的句间隔（刻；默认 {@link TextUtil#SEQUENCE_GAP_TICKS}，约 0.2 秒）。 */
    protected int castBurstGapTicks() {
        return TextUtil.SEQUENCE_GAP_TICKS;
    }

    /**
     * 带风格覆盖的喊话（想让某个技能用不同观感时用）。
     *
     * @param styleOverride 风格覆盖；{@code null} ⇒ 用 {@link #styleFor(String)}
     * @param scaleOverride 缩放覆盖；{@code null} ⇒ 用 {@link #scaleFor(String)}
     */
    public boolean say(Player who, String moment, String text,
                       TextUtil.Style styleOverride, Float scaleOverride) {
        if (!started || pool == null || who == null) {
            return false;
        }
        String key = moment == null ? MOMENT_IDLE : moment;
        String content = (text == null || text.isBlank()) ? lexiconNow().pick(key, random) : text;
        if (content == null || content.isBlank()) {
            return false;
        }
        TextUtil.Style style = styleOverride != null ? styleOverride : styleFor(key);
        float scale = scaleOverride != null ? scaleOverride : scaleFor(key);
        int speed = speedFor(key);
        TextUtil.Look look = lookFor(key);

        // ★ 落地砸字 / 米塔滚地都要走"逐字查地面"的那条路（普通路径不知道地面在哪）
        boolean emitted;
        if (style == TextUtil.Style.GROUND_SMASH || style == TextUtil.Style.MITA_SCROLL) {
            emitted = TextUtil.emitOnGround(pool, who, key, content, style, look,
                    groundOffsetFor(key), RADIUS_MIN, RADIUS_MAX, HEIGHT_MIN, HEIGHT_MAX,
                    speed, STAY_TICKS, scale);
        } else {
            emitted = TextUtil.emit(pool, who, key, content, style, look,
                    RADIUS_MIN, RADIUS_MAX, HEIGHT_MIN, HEIGHT_MAX,
                    speed, STAY_TICKS, scale);
        }

        // 施法 / 受击 / 击杀 / 濒死都算"打过架"⇒ 推迟空闲字
        if (emitted && isCombatMoment(key)) {
            lastCombatTick = tickCounter;
        }
        return emitted;
    }

    /**
     * ★ **一次吐 1~3 句（连着说）** —— 用于"技能释放时讲一句话"。
     *
     * <p>从该时刻的词库里**随机抽 n 句不重复的**，排队后每隔 {@code gap} 刻放一句，
     * 形成"一口气连着说"的对话感（而不是一句句孤立地蹦）。
     *
     * @param who      主体；{@code null} ⇒ 忽略
     * @param moment   时刻标识
     * @param minLines 最少几句（{@code <= 0} ⇒ 1）
     * @param maxLines 最多几句（{@code < min} ⇒ 取 min）；实际上限 = 词库该时刻的候选数
     * @param gapTicks 句间隔（刻）；{@code <= 0} ⇒ {@link TextUtil#SEQUENCE_GAP_TICKS}（约 0.2 秒）
     * @return 实际排入的句数
     */
    public int sayBurst(Player who, String moment, int minLines, int maxLines, int gapTicks) {
        if (!started || pool == null || who == null) {
            return 0;
        }
        String key = moment == null ? MOMENT_IDLE : moment;
        List<String> candidates = lexiconNow().candidates(key);
        if (candidates.isEmpty()) {
            return 0;
        }
        int lo = Math.max(1, minLines);
        int hi = Math.max(lo, maxLines);
        int want = lo + (hi > lo ? random.nextInt(hi - lo + 1) : 0);
        want = Math.min(want, candidates.size());

        List<String> picked = pickDistinct(candidates, want);
        if (picked.isEmpty()) {
            return 0;
        }

        TextUtil.Style style = styleFor(key);
        TextUtil.Look look = lookFor(key);
        // ★ 落地方案（砸地 / 滚地）的连排台词必须带上起落高度 ⇒ 否则队列里播出来的字只会原地缩放
        double groundOffset = (style == TextUtil.Style.GROUND_SMASH || style == TextUtil.Style.MITA_SCROLL)
                ? groundOffsetFor(key) : 0d;
        int speed = speedFor(key);
        // 连排台词统一按"该时刻的摆位"，走池的队列（由 update() 每刻推进）
        int queued = TextUtil.emitSequence(pool, who, key, picked, style, look,
                groundOffset, speed, gapTicks);
        if (queued > 0 && isCombatMoment(key)) {
            lastCombatTick = tickCounter;
        }
        return queued;
    }

    /** 从候选里抽 {@code n} 条**不重复**的（候选不足 ⇒ 全给）。 */
    private List<String> pickDistinct(List<String> candidates, int n) {
        List<String> pool = new ArrayList<>(candidates);
        List<String> out = new ArrayList<>();
        int take = Math.min(Math.max(0, n), pool.size());
        for (int i = 0; i < take; i++) {
            out.add(pool.remove(random.nextInt(pool.size())));
        }
        return out;
    }

    // ───────── 承受方回调（Participant） ─────────

    /** 受伤通知（框架 {@code DamageHookListener} 扇出；只观察，不干预）。 */
    @Override
    public void onDamaged(Player source, double amount) {
        if (!started || pool == null) {
            return;
        }
        Player self = self();
        if (self == null) {
            return;
        }
        say(self, MOMENT_HURT, null);
        if (lowHpEnabled() && isLowHp(self)) {
            say(self, MOMENT_LOW_HP, null);
        }
    }

    /** 受治疗通知（本组件不响应治疗，空实现即可）。 */
    @Override
    public void onHealed(double amount) {
        // 悬浮文本不响应治疗
    }

    // ───────── 击杀订阅 ─────────

    private void registerKillListener() {
        if (killedEntry != null) {
            return;
        }
        VitalsComponent vitals = getComponent(VitalsComponent.class);
        if (vitals == null) {
            return;
        }
        Player self = self();
        killedEntry = vitals.addPlayerKilledListener(this, event -> {
            if (!started) {
                return;
            }
            // 双保险：本名单只会在"我是击杀者"时被通知，这里再确认一次
            if (self != null && event.getKiller() != self) {
                return;
            }
            say(self(), MOMENT_KILL, null);
        });
    }

    private void unregisterKillListener() {
        if (killedEntry == null) {
            return;
        }
        VitalsComponent vitals = getComponent(VitalsComponent.class);
        if (vitals != null) {
            vitals.removePlayerKilledListener(killedEntry);
        }
        killedEntry = null;
    }

    // ───────── 可覆写钩子（子类定制） ─────────

    /**
     * 词库：本组件按时刻取候选文本的地方。
     * <p>默认空词库（⇒ 只有显式传 {@code text} 的调用才有字）。子类覆写它给出自己的词库。
     */
    protected TextUtil.Lexicon lexicon() {
        return TextUtil.Lexicon.of(Map.of());
    }

    /**
     * ★ **本次说话时实际使用的词库**（默认 = {@link #lexicon()}）。
     *
     * <p>为什么要有这一层：基类在 {@code start()} 里把 {@link #lexicon()} 的结果**缓存**下来
     * （只为省一次构造），但有的子类的词库会**随运行时状态变化**（例：马提娜的台词按狂暴层数
     * 在"神性 / 怨气 / 攻击性"三档间迁移）⇒ 缓存的那一份会永远停在启动瞬间的档位。
     *
     * <p>因此把"取词库"这件事抽成一个**每次说话都问一次**的钩子：
     * <ul>
     *   <li>词库是静态的（绝大多数组件）⇒ 不用管它，默认实现直接返回缓存字段，零额外开销；</li>
     *   <li>词库是动态的（马提娜一类）⇒ 覆写它，在内部按当前状态选档。</li>
     * </ul>
     */
    protected TextUtil.Lexicon lexiconNow() {
        return lexicon != null ? lexicon : lexicon();
    }

    /** 某时刻用的风格（默认：空闲用克制款，施法 / 击杀用凸显款，受击用乱码款）。 */
    protected TextUtil.Style styleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_CAST -> TextUtil.STYLE_EMPHASIS;
            case MOMENT_HURT -> TextUtil.STYLE_GLITCH;
            case MOMENT_KILL -> TextUtil.STYLE_BURST;
            case MOMENT_LOW_HP -> TextUtil.STYLE_GLITCH;
            case MOMENT_SPAWN -> TextUtil.STYLE_WHISPER;
            default -> TextUtil.STYLE_CALM;
        };
    }

    /** 某时刻用的整体缩放（默认 1.0；击杀 / 施法略大一点以更醒目）。 */
    protected float scaleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_CAST, MOMENT_KILL -> 1.15f;
            default -> 1.0f;
        };
    }

    /**
     * 某时刻用的**弹出速度**（数据包口径：越大越快；间隔 = {@code 3 * 100 / speed} 刻）。
     *
     * <p>换算参考：{@code 80} ⇒ 每字隔 3 刻；{@code 150} ⇒ 2 刻；{@code 300} ⇒ 1 刻（最快）。
     * <p>子类覆写它就能给不同时刻配不同节奏（例如施法快、空闲慢）。
     */
    protected int speedFor(String moment) {
        return SPEED;
    }

    /**
     * 某时刻的**外观**（颜色 / 泛光 / 颤抖）。
     *
     * <p>默认全 Plain（不改色、不泛光、不颤抖）⇒ 老角色零影响。
     * 子类覆写它给出自己的语气（例如"愤怒 = 渐变红 + 颤抖"、"神性 = 泛光白"）。
     */
    protected TextUtil.Look lookFor(String moment) {
        return TextUtil.Look.PLAIN;
    }

    /**
     * 某时刻的**文本下方起始高度**（格，相对基准点）：只有落地砸字用得上。
     *
     * <p>越大 ⇒ 字从越高处掉下来、掉得越久（上限见 {@code Anim#MAX_FALL_HEIGHT}）。
     * <p>默认 {@value #CAST_GROUND_OFFSET}；非落地砸字风格会忽略本值。
     */
    protected double groundOffsetFor(String moment) {
        return CAST_GROUND_OFFSET;
    }

    /** 是否启用空闲氛围字（默认启用；子类可关掉以免啰嗦）。 */
    protected boolean idleEnabled() {
        return true;
    }

    /** 是否启用濒死提醒（默认启用）。 */
    protected boolean lowHpEnabled() {
        return true;
    }

    // ───────── 内部工具 ─────────

    /** 自己（组件服务面给的是"本角色实例的玩家"）。 */
    private Player self() {
        try {
            return svc().self().player();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isCombatMoment(String moment) {
        return MOMENT_CAST.equals(moment) || MOMENT_HURT.equals(moment)
                || MOMENT_KILL.equals(moment) || MOMENT_LOW_HP.equals(moment);
    }

    private static boolean isLowHp(Player player) {
        double max = player.getAttribute(Attribute.MAX_HEALTH) == null
                ? 20d
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        return max > 0d && player.getHealth() / max <= LOW_HP_RATIO;
    }

    // ───────── 描述符 ─────────

    /**
     * 悬浮文本组件的描述符（纯声明）。
     * <p>文案由具体角色在注册处给出（每个角色的组件面板上读得到它做什么）。
     * <p>专用角色组件（如马提娜版）通过**继承本描述符 + 覆写
     * {@link #createComponent(String, ComponentServicesPort)}** 换掉组件实现类。
     */
    /**
     * 通用描述符。
     *
     * <p>★ <b>泛型自类型</b>：{@code T} = 本描述符实际造出来的组件类型。
     * 数据包口径的"组件类型"由描述符的泛型实参决定（装配期依赖检查按它比对）
     * ⇒ 子类必须写 {@code Specification extends FloatingTextComponent.Specification<子类>}，
     * 否则**对外声明的类型会停在 {@code FloatingTextComponent}**，别的组件
     * {@code requires(子类.class)} 会报"missing component type"（踩过：马提娜三个技能全装配失败）。
     */
    public static class Specification<T extends FloatingTextComponent>
            extends PassiveSkill.Specification<T> {

        public Specification(Component displayName, List<Component> description) {
            super(displayName, description);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T create(String id, ComponentServicesPort services) {
            return (T) createComponent(id, services);
        }

        /** 工厂钩子：默认造出通用实现；专用组件继承本描述符后覆写它。 */
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new FloatingTextComponent(id, services, this);
        }
    }

    /**
     * 词库便捷构造：把"时刻 → 候选文本"的散表组装成 {@link TextUtil.Lexicon}。
     * <p>转发到 {@link TextUtil#lexiconOf(Object...)}（保留本名是为了让子类读起来顺）。
     */
    protected static TextUtil.Lexicon lexiconOf(Object... pairs) {
        return TextUtil.lexiconOf(pairs);
    }
}
