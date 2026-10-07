package com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.datapack.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * 「狂躁牧师·马提娜」的**风格悬浮文本**（继承通用 {@link FloatingTextComponent}，
 * 只换词库 / 风格 / 外观）。
 *
 * <h2>人设：上班牛马 + 圣女 + 狂躁</h2>
 * 三股劲儿拧在一起：
 * <ul>
 *   <li><b>牛马</b> —— 嘴上全是"加班 / 值班 / 排班 / KPI / 扣工资"的怨气；</li>
 *   <li><b>圣女</b> —— 张口就是"神谕 / 圣光 / 净化 / 罪业 / 救赎"的圣职腔；</li>
 *   <li><b>狂躁</b> —— 这两股劲儿互相打架，于是话里带刺、带喘、带笑，
 *       上一句还在虔诚祈祷，下一句就"都该下地狱"。</li>
 * </ul>
 *
 * <h2>★ 语气随狂暴层数迁移（本类的核心机制）</h2>
 * 本组件读同角色 {@link MatinaKuangPassive} 的当前层数，把每一句话按**当时的狂暴值**
 * 分成两套词库与两套观感：
 * <table border="1">
 *   <tr><th>档位</th><th>层数</th><th>语气</th><th>观感</th></tr>
 *   <tr><td>{@link Tier#CALM}</td><td>0 ~ {@value #TIER_CALM_MAX}</td>
 *       <td><b>神性 / 平静</b>：祷告、圣光、赦罪、安抚（真正的圣女腔）</td>
 *       <td>泛光白 + 加粗（神圣）</td></tr>
 *   <tr><td>{@link Tier#STRAINED}</td><td>{@value #TIER_CALM_MAX} ~ {@value #TIER_ANGRY_MIN}</td>
 *       <td><b>疲惫 / 怨气</b>：加班、值班、扣工资（牛马但还没炸）</td>
 *       <td>暗红 + 颤抖</td></tr>
 *   <tr><td>{@link Tier#FRENZY}</td><td>≥ {@value #TIER_ANGRY_MIN}</td>
 *       <td><b>攻击性 / 班味拉满</b>：骂人、催命、催班、灭绝人性（狂躁牧师）</td>
 *       <td>渐变红 + 颤抖 + 加粗（暴怒）</td></tr>
 * </table>
 * 三层各有一套独立词库 ⇒ "层数越低越神性、越高越有攻击性"是**词库级**的差异，
 * 不只是换个颜色。词库缺失的时刻（如施法）走 {@link #tierLexicon} 的兜底链。
 *
 * <h2>外观（表达语气）</h2>
 * <ul>
 *   <li><b>愤怒</b>（受击 / 濒死）⇒ **渐变红 + 颤抖 + 加粗**，字越往右越暗、整排发抖；</li>
 *   <li><b>神性</b>（施法 / 登场）⇒ **泛光白 + 加粗**，拉满亮度的粗体白字；</li>
 *   <li>空闲 ⇒ 克制的暗红（不抢戏），同样加粗以便读清。</li>
 * </ul>
 *
 * <h2>风格：米塔风格 / 字体滚地</h2>
 * **所有时刻、所有档位统一**走 {@link TextUtil.Style#MITA_SCROLL}（移植自数据包 V26.2 的
 * {@code operation/drop}）—— 「**生成时是正常文本，消失时才滚地**」：
 * 字先**原地**正常显示（出现 + 停留），到该消失的时候才落到地面 → 触地**反弹** →
 * 摩擦**滚停** → **躺倒**（俯仰 −88°）在地上 → **淡出**。
 * 差异只在**颜色 / 颤抖 / 加粗 / 语速**上表达：
 * <ul>
 *   <li>施法 ⇒ 泛光白 + 颤抖 + 加粗；</li>
 *   <li>受击 / 濒死 ⇒ 渐变红 + 颤抖 + 加粗；</li>
 *   <li>空闲（平静档）⇒ 月白 + 加粗、不颤抖；</li>
 *   <li>空闲（怨气 / 狂暴档）⇒ 暗红 / 猩红 + 颤抖 + 加粗。</li>
 * </ul>
 * 每次施法随机**连说 1~3 句**，间隔约 0.2 秒。
 */
public class MatinaFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "matina_floating_text";

    /** 施法落地砸字的下落起始高度（格）：越高掉得越久、砸得越有分量。（★ 当前风格不使用） */
    private static final double CAST_FALL_HEIGHT = 7d;

    /** 情绪高点（受击 / 濒死 / 击杀 / 登场）的砸字高度（格）：略低于施法，够看出"摔"即可。（★ 当前风格不使用） */
    private static final double EMOTE_FALL_HEIGHT = 4.5d;

    /**
     * 空闲 + 愤怒档的砸字高度（格）：比情绪高点更矮 ——
     * 闲话是"烦得把话甩出来"，不是"砸下来"，矮即短促。（★ 当前风格不使用）
     */
    private static final double IDLE_SMASH_FALL_HEIGHT = 3.5d;

    /**
     * 空闲 + 平静档的砸字高度（格）：最矮最轻 —— 神性不是"摔"，是"缓缓降临"。
     * （★ 当前风格不使用）
     */
    private static final double CALM_SMASH_FALL_HEIGHT = 2.5d;

    /** ★ 空闲 + 平静档的语速：砸地但从容（< 愤怒档 110，> 能看清的底线）。 */
    private static final int CALM_SMASH_SPEED = 90;

    // ───────── ★ 语气分档阈值 ─────────

    /** 低于等于该层数 ⇒ {@link Tier#CALM}（神性 / 平静）。 */
    public static final int TIER_CALM_MAX = 9;

    /** 高于等于该层数 ⇒ {@link Tier#FRENZY}（攻击性 / 班味拉满）。 */
    public static final int TIER_ANGRY_MIN = 30;

    /** ★ 愤怒色：亮红（渐变起点）。 */
    private static final int ANGRY_RED = 0xFF3A20;

    /** ★ 神性色：泛光白。 */
    private static final int HOLY_WHITE = 0xFFFFFF;

    /** ★ 空闲色：低饱和暗红（不抢戏）。 */
    private static final int MUTTER_RED = 0xC06050;

    /** ★ 平静档的闲话色：柔和的月白（不用纯白，免得和施法抢）。 */
    private static final int CALM_PALE = 0xE8E4FF;

    /** ★ 狂暴档的闲话色：刺眼的猩红。 */
    private static final int FRENZY_RED = 0xFF2010;

    /** ★ 狂暴值组件（读层数用；装配期可能缺 ⇒ 读口要判空）。 */
    private MatinaKuangPassive kuang;

    /** 三档词库（不可变纯数据，构造期各建一次；层数只决定"用哪一本"）。 */
    private final TextUtil.Lexicon calmLexicon = buildCalmLexicon();
    private final TextUtil.Lexicon strainedLexicon = buildStrainedLexicon();
    private final TextUtil.Lexicon frenzyLexicon = buildFrenzyLexicon();

    public MatinaFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 生命周期：取狂暴组件 + 备好三档词库 ─────────

    /**
     * {@code start()}：取狂暴值组件（可选依赖），并把三档词库各建一份。
     *
     * <p>★ 依赖只在 {@code start()} 取（工程铁律）；{@link MatinaKuangPassive} 是
     * <b>可选</b>依赖（没它照样能说话，只是全部按"平静档"处理），
     * 所以这里用 {@link #getComponent(Class)} 而不是 {@code requires(...)}。
     */
    @Override
    public void start() {
        super.start();
        this.kuang = getComponent(MatinaKuangPassive.class);
    }

    /** 当前狂暴层数（取不到组件 ⇒ 0 ⇒ 走平静档，语义上"还没被班味污染"）。 */
    public int kuang() {
        return kuang == null ? 0 : kuang.kuang();
    }

    /** 当前语气档位（**纯函数** ⇒ 可离线穷举单测）。 */
    public static Tier tierOf(int kuang) {
        if (kuang >= TIER_ANGRY_MIN) {
            return Tier.FRENZY;
        }
        if (kuang > TIER_CALM_MAX) {
            return Tier.STRAINED;
        }
        return Tier.CALM;
    }

    /** 当前档位。 */
    public Tier tier() {
        return tierOf(kuang());
    }

    /** ★ 语气档位：层数越高越有攻击性 / 班味，越低越有神性 / 平静。 */
    public enum Tier {
        /** 神性 / 平静（层数 ≤ {@value MatinaFloatingTextComponent#TIER_CALM_MAX}）。 */
        CALM,
        /** 疲惫 / 怨气（夹在中间）。 */
        STRAINED,
        /** 攻击性 / 班味拉满（层数 ≥ {@value MatinaFloatingTextComponent#TIER_ANGRY_MIN}）。 */
        FRENZY
    }

    // ───────── 词库：按档位取 ─────────

    /**
     * 当前档位的词库（含兜底链）：
     * <ol>
     *   <li>{@link Tier#FRENZY} ⇒ 狂暴词库 → 怨气词库 → 平静词库；</li>
     *   <li>{@link Tier#STRAINED} ⇒ 怨气词库 → 狂暴词库 → 平静词库；</li>
     *   <li>{@link Tier#CALM} ⇒ 平静词库 → 怨气词库 → 狂暴词库。</li>
     * </ol>
     * 兜底链保证任何一层在某时刻缺词时都能借到别的档（不会"哑巴"）。
     *
     * <p>三本词库都是**不可变纯数据**⇒ 在构造期建一次，之后只做"选哪一本"。
     */
    private TextUtil.Lexicon tierLexicon() {
        return switch (tier()) {
            case FRENZY -> frenzyLexicon.withFallbackFrom(
                    () -> strainedLexicon.withFallbackFrom(() -> calmLexicon));
            case STRAINED -> strainedLexicon.withFallbackFrom(
                    () -> frenzyLexicon.withFallbackFrom(() -> calmLexicon));
            case CALM -> calmLexicon.withFallbackFrom(
                    () -> strainedLexicon.withFallbackFrom(() -> frenzyLexicon));
        };
    }

    /**
     * ★ **按档位取词库**（覆写基类钩子）。
     *
     * <p>基类在 {@code start()} 里调 {@link #lexicon()} 一次并缓存；本实现覆写
     * {@code lexiconNow()} ⇒ **每次说话都现算一次档位**，层数一变台词立刻跟着变
     * （缓存那份永远停在启动瞬间 ⇒ 这正是必须覆写的原因）。
     */
    @Override
    protected TextUtil.Lexicon lexiconNow() {
        return tierLexicon();
    }

    /**
     * 基类 {@code start()} 期间会调一次本方法做缓存 ⇒ 返回"三档合并的兜底视图"，
     * 保证即便 {@link MatinaKuangPassive} 还没取到（层数按 0 算）也不会没词。
     */
    @Override
    protected TextUtil.Lexicon lexicon() {
        return tierLexicon();
    }

    // ───────── 词库内容：三档各一本 ─────────

    /**
     * ★ **平静档词库**（狂暴 ≤ {@value #TIER_CALM_MAX}）：真正的圣女 ——
     * 祷告、圣光、赦罪、安抚，平和不带刺。
     */
    private static TextUtil.Lexicon buildCalmLexicon() {
        return lexiconOf(
                MOMENT_SPAWN, new String[]{
                        "圣光与你同在", "愿神庇佑你", "牧师·马提娜，为您祈祷",
                        "赞颂归于上主", "愿此间安宁", "我来，是为了救人",
                        "神爱世人", "以圣名问安", "愿光驱散黑暗"
                },
                MOMENT_IDLE, new String[]{
                        "愿主垂听", "圣光长明", "祷词在心中", "愿世人安眠",
                        "今夜无梦", "神说，要有光", "我替你们守着",
                        "愿苦难止于今日", "念一段经，静一静心",
                        "圣坛前，无需多言", "愿风也温柔", "怜悯所有迷途者",
                        "合掌，默祷", "光会记得每一个人", "愿你们被善待",
                        "这杖，只用来扶人", "神在看着，不必害怕", "我会一直在"
                },
                MOMENT_CAST, new String[]{
                        "以圣光之名", "愿主赦免你", "光明降临", "接受洗礼吧",
                        "神的慈悲在此", "让我治愈你", "圣言成真", "愿光洗净罪业",
                        "以圣典之名", "平静下来", "愿你得救", "光，落下"
                },
                MOMENT_HURT, new String[]{
                        "……无妨", "我不恨你", "愿主原谅你", "这点痛，我受得住",
                        "圣光护佑", "不必如此", "神仍爱你", "我为你祈祷",
                        "愿你的心，得以安宁", "我还站得住"
                },
                MOMENT_LOW_HP, new String[]{
                        "圣光……请再借我一点力量", "我……还不能倒下", "愿神与我同在",
                        "光明未尽", "还有人在等我祈祷", "神不会弃我",
                        "让我……再撑一会", "愿这具身躯，支撑到最后", "还未到安息之时",
                        "我答应过，要救人"
                },
                MOMENT_KILL, new String[]{
                        "愿你安息", "尘归尘，土归土", "主已收下你的灵魂",
                        "愿你来世安宁", "睡吧", "苦难已经结束", "罪业已清",
                        "神接纳了你", "归于光中", "愿你得解脱"
                }
        );
    }

    /**
     * ★ **怨气档词库**（层数夹在中间）：牛马疲惫 —— 阴阳怪气、抱怨排班与工资，
     * 还端着圣女的架子，但话里已经开始带刺。
     */
    private static TextUtil.Lexicon buildStrainedLexicon() {
        return lexiconOf(
                MOMENT_SPAWN, new String[]{
                        "又上班了", "值班开始", "圣女的排班表上，又有你的名字",
                        "打卡，然后去死", "神说，今天也要加班", "神说要有光，我说要有加班费"
                },
                MOMENT_IDLE, new String[]{
                        "又是加班的一天", "值班表上写着我的名字", "工资没发，杖先发",
                        "我是圣女，不是牛马", "圣光也要交电费", "神说要有光，我说要有加班费",
                        "排班，又是我", "累了", "再撑一会", "神不休息，我也不休息",
                        "这份工，真难做", "他们的罪，我记着呢", "怎么还没到点下班",
                        "KPI……谁定的", "别催了，在救", "五天假？梦里",
                        "圣坛的灯油，也没人报销", "医者，亦可为刽子手",
                        "下一位，请上前赎罪"
                },
                MOMENT_CAST, new String[]{
                        "加班费，用命结", "别浪费我的时间", "快点结束，我要下班",
                        "这是神的意思，也是排班的", "又是加急单", "打完好收工",
                        "这单，算你加班", "结束你的值班", "别怕，比加班快"
                },
                MOMENT_HURT, new String[]{
                        "加班还挨打……", "这份工，真难做", "你敢打我？我还在值班",
                        "工伤，算谁的", "啧……", "这月绩效还要不要了",
                        "谁给你的胆子", "好，很好，我记下了", "工资都扣没了还打我"
                },
                MOMENT_LOW_HP, new String[]{
                        "值班还没结束", "不能死在下班前", "我还……没加班完",
                        "这月工资……还没领", "累了……但班还得值", "别让我死在工位上",
                        "排班表上……还有我", "撑住，撑到交班"
                },
                MOMENT_KILL, new String[]{
                        "下班了", "解脱了", "又送走一个", "你的班，到此为止",
                        "愿你来世不加班", "总算清净了", "可以歇一位了"
                }
        );
    }

    /**
     * ★ **狂暴档词库**（≥ {@value #TIER_ANGRY_MIN} 层）：班味与攻击性彻底压过神性 ——
     * 骂人、催命、以"加班 / 考核 / 排班"为刑具，是真正的狂躁牧师。
     */
    private static TextUtil.Lexicon buildFrenzyLexicon() {
        return lexiconOf(
                MOMENT_SPAWN, new String[]{
                        "都给我滚来上班", "审判开始，一个都别跑", "忏悔吧，杂碎",
                        "苦难将至", "狂躁牧师·马提娜", "排班表上，全是死人",
                        "全员加班，直到死", "神？神也拦不住我"
                },
                MOMENT_IDLE, new String[]{
                        "都该忏悔", "都该下地狱", "他们……都该去死",
                        "一个个，全是废物", "谁再摸鱼我就净化谁", "考核不达标？死",
                        "排班！排班！排班！", "烦死了，全杀了省事",
                        "圣光？不如加班费", "这世道，就没一个好东西",
                        "再吵一句，我送你上路", "都别睡，陪我加班",
                        "杀光了，就不用排班了", "我说要光，谁不服",
                        "都欠收拾", "这杖，比谁都不好说话",
                        "怎么还没死，气死我了", "全给我闭嘴"
                },
                MOMENT_CAST, new String[]{
                        "审判！", "神罚降临", "去死吧", "跪下", "裁决",
                        "聆听你的报应", "以苦怒之名", "净化你，用血",
                        "加班费，用命结", "别怕，很快的", "起来，别装死",
                        "这是你的最后一班", "死了就不用排班了", "我宣布，你被开除",
                        "把命留下再走", "受死", "神谕已下，无人可免"
                },
                MOMENT_HURT, new String[]{
                        "你敢打我？找死", "我记住你了，等着", "好，很好，你完了",
                        "疼？我更疼", "你敢碰我", "我就该先杀了你",
                        "这一下，你拿命来还", "别急，我会亲手碾碎你",
                        "狂躁……压不住了", "你也配动手？"
                },
                MOMENT_LOW_HP, new String[]{
                        "我……死也要拉着你", "别想，我还没杀够", "神不会抛弃我？放屁",
                        "苦怒……未尽", "我偏不倒下", "全都得陪葬",
                        "还……不能死，还有账要算", "那就一起死",
                        "这点伤，也要不了我的命", "我要把这地方烧干净"
                },
                MOMENT_KILL, new String[]{
                        "安息？不如说是解脱", "下一位，滚上来", "这是神罚",
                        "死了，就别再排班了", "省了一个名额", "干净",
                        "终于安静了", "睡吧，永远别醒", "一个，还差很多",
                        "你的班，到此为止，连同命"
                }
        );
    }

    // ───────── 风格 / 外观 / 节奏 ─────────

    /**
     * ★ **风格分配（全时刻、全档位统一走"米塔风格 / 字体滚地"）**：
     * <ul>
     *   <li><b>施法 / 受击 / 濒死 / 击杀 / 登场 / 空闲</b> ⇒ 全部
     *       {@link TextUtil.Style#MITA_SCROLL} —— 字被**抛出去**、重力下坠、触地**反弹**、
     *       摩擦**滚停**、**躺倒**在地上、停留片刻后**淡出**（移植自数据包 V26.2 的
     *       {@code operation/drop}）。</li>
     * </ul>
     *
     * <p>语气差异**只由外观 / 抛出高度 / 语速**表达（见 {@link #lookFor}）：
     * 颜色、是否颤抖、砸得多高、说得多快 —— 而不是换一种出现方式。
     *
     * <p>★ 滚地会**逐字查方块**（N 次方块查询）⇒ 空闲字的节流间隔是
     * {@code IDLE_INTERVAL_TICKS} 刻（约 2~4 秒一次）⇒ 这点查询量可以忽略；
     * 但**绝不能**把它挂到每刻都吐的通道上。
     */
    @Override
    protected TextUtil.Style styleFor(String moment) {
        // ★ 全时刻、全档位统一米塔滚地（语气靠 lookFor / groundOffsetFor / speedFor 表达）
        return TextUtil.STYLE_MITA_SCROLL;
    }

    /**
     * ★ **外观（表达语气）**—— 色调由**时刻**与**狂暴档位**共同决定：
     * <ul>
     *   <li>施法 / 登场 ⇒ **泛光白 + 加粗**（神性）；狂暴档时改为猩红泛光（神性被班味吞掉）；</li>
     *   <li>受击 / 濒死 ⇒ **渐变红 + 颤抖 + 加粗**（愤怒"抖动字体"，同样走滚地）；</li>
     *   <li>击杀 ⇒ 泛光红 + 渐变 + 加粗（神圣的判决）；</li>
     *   <li>空闲 ⇒ 按档位：平静 = 月白，怨气 = 暗红，狂暴 = 猩红刺眼 —— 都加粗。</li>
     * </ul>
     */
    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        Tier tier = tier();
        switch (key) {
            case MOMENT_CAST:
                // 神性白 + 颤抖 + 加粗（"气得发抖地甩出去"）；狂暴档 = 猩红
                return (tier == Tier.FRENZY ? TextUtil.Look.glowOf(FRENZY_RED)
                        : TextUtil.Look.glowOf(HOLY_WHITE))
                        .withTremble().withBold();
            case MOMENT_SPAWN:
                return (tier == Tier.FRENZY ? TextUtil.Look.glowOf(FRENZY_RED)
                        : TextUtil.Look.glowOf(HOLY_WHITE)).withBold();
            case MOMENT_HURT:
            case MOMENT_LOW_HP:
                // ★ 愤怒：渐变红 + 颤抖 + 加粗（"改得更加明显点"）
                return TextUtil.Look.gradientOf(ANGRY_RED).withTremble().withBold();
            case MOMENT_KILL:
                return TextUtil.Look.glowOf(ANGRY_RED).withGradient().withBold();
            default:
                // 空闲：按档位换色，统一加粗
                return switch (tier) {
                    case CALM -> TextUtil.Look.colored(CALM_PALE).withBold();
                    case STRAINED -> TextUtil.Look.colored(MUTTER_RED).withTremble().withBold();
                    case FRENZY -> TextUtil.Look.gradientOf(FRENZY_RED).withTremble().withBold();
                };
        }
    }

    /**
     * ★ **起落高度**（{@link TextUtil.Style#GROUND_SMASH} 的"从多高开始掉"）。
     *
     * <p>⚠ **当前风格是米塔滚地**（{@link TextUtil.Style#MITA_SCROLL}）—— 那个风格
     * **生成时在原地正常显示、消失时才从基准点落到地面** ⇒ **不使用本方法的返回值**
     * （下落距离由地面高度决定，不由这里调）。
     * 这几个值保留是为了"切回砸地风格时立刻生效"，改外观时别误以为它们在起作用。
     *
     * <ul>
     *   <li>施法 ⇒ {@value #CAST_FALL_HEIGHT} 格（最高最重，"圣光砸下来"）；</li>
     *   <li>受击 / 濒死 / 击杀 / 登场 ⇒ {@value #EMOTE_FALL_HEIGHT} 格（略低）；</li>
     *   <li>空闲 + 怨气 / 狂暴档 ⇒ {@value #IDLE_SMASH_FALL_HEIGHT} 格（矮，"甩"）；</li>
     *   <li>空闲 + 平静档 ⇒ {@value #CALM_SMASH_FALL_HEIGHT} 格（最矮最轻，"缓缓降临"）。</li>
     * </ul>
     */
    @Override
    protected double groundOffsetFor(String moment) {
        String key = moment == null ? "" : moment;
        if (MOMENT_CAST.equals(key)) {
            return CAST_FALL_HEIGHT;
        }
        switch (key) {
            case MOMENT_HURT:
            case MOMENT_LOW_HP:
            case MOMENT_KILL:
            case MOMENT_SPAWN:
                return EMOTE_FALL_HEIGHT;
            default:
                // ★ 空闲：所有档位都滚地 ⇒ 各自给抛出高度（平静档最矮，像"轻轻落下"）
                return tier() == Tier.CALM ? CALM_SMASH_FALL_HEIGHT : IDLE_SMASH_FALL_HEIGHT;
        }
    }

    /** 施法语速快一点（字飞得利落）；情绪高点同样偏快。 */
    @Override
    protected int speedFor(String moment) {
        String key = moment == null ? "" : moment;
        switch (key) {
            case MOMENT_CAST:
                return 130;
            case MOMENT_HURT:
            case MOMENT_LOW_HP:
            case MOMENT_KILL:
                return 115;
            default:
                // ★ 空闲：愤怒档"甩"得快；平静档慢一些（神性的从容）
                return tier() == Tier.CALM ? CALM_SMASH_SPEED : 110;
        }
    }

    /** 施法期间连说 1~3 句，句间隔约 0.35 秒。 */
    @Override
    protected int castBurstMin() {
        return 1;
    }

    @Override
    protected int castBurstMax() {
        return 3;
    }

    @Override
    protected int castBurstGapTicks() {
        return 7; // 0.35 秒
    }

    /**
     * 马提娜版描述符：声明依赖 {@link VitalsComponent}（击杀订阅与受伤扇出都走它）。
     *
     * <p>★ {@link MatinaKuangPassive} 是**可选**依赖（用 {@code getComponent} 取，
     * 不进 {@code requires}）—— 少了它只会"永远按平静档说话"，不该让整个角色装配失败。
     */
    public static final class Specification extends FloatingTextComponent.Specification<MatinaFloatingTextComponent> {

        public Specification() {
            super(Component.text("苦怒低语").color(NamedTextColor.RED),
                    List.of(Component.text("状态改变时，周身浮现风格化文本"),
                            Component.text("空闲 / 施法 / 受击 / 击杀 / 濒死 / 登场各有一组台词"),
                            Component.text("台词随狂暴层数在「神性 / 怨气 / 攻击性」三档间迁移")));
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new MatinaFloatingTextComponent(id, services, this);
        }
    }
}
