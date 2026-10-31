package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 冷识（{@code lethe}）的**语音库**：一位「疯狂的主教」的祷词。
 *
 * <h2>角色语气（台词自拟，全部按此落地）</h2>
 * 他**相信神明与救赎**，却早已是**黑色的、灼燃的怪物**；他用言语**蛊惑众人**，也用言语**救赎众人**
 * —— 邪恶与善良在同一句里并存。因此台词一律：<b>中文、短句、有仪式感</b>（称"孩子 / 主 / 恩典 / 罪"），
 * 不写长段独白；同一个时刻的几句之间是"换一种说法"，不是连续叙事。
 * 例：「我会救你，再吃了你。」—— 救与食同源，这就是本角色的底色。
 *
 * <h2>时刻分组（= 契约 §3.3 冻结的调用面，名字逐字一致）</h2>
 * <table>
 *   <tr><th>时刻</th><th>触发方</th></tr>
 *   <tr><td>{@link #MOMENT_WEAPON_HIT}</td><td>A 道：主武器命中</td></tr>
 *   <tr><td>{@link #MOMENT_RING_SPAWN} / {@link #MOMENT_RING_HIT}</td><td>A 道：共鸣圈生成 / 圈内命中</td></tr>
 *   <tr><td>{@link #MOMENT_FORGET_MARK} / {@link #MOMENT_FORGET_RETURN}</td><td>B 道：遗忘的标记 / 记忆归还</td></tr>
 *   <tr><td>{@link #MOMENT_SUMMON_LIFT} / {@link #MOMENT_SUMMON_RECALL} / {@link #MOMENT_SUMMON_DETONATE}</td>
 *       <td>B 道：召圣的抬升 / 召回 / 引爆</td></tr>
 *   <tr><td>{@link #MOMENT_RESHAPE_CHANNEL} / {@link #MOMENT_RESHAPE_IMPACT}</td><td>B 道：重塑引导 / 冲击</td></tr>
 *   <tr><td>{@link #MOMENT_EVOLVE}</td><td>C 道：{@link LetheEvolutionPassive} 升级</td></tr>
 *   <tr><td>{@link #MOMENT_QUIVER_BLOOD} / {@link #MOMENT_QUIVER_ARROW}</td><td>A 道：箭袋恢复（血泪 / 箭矢）</td></tr>
 *   <tr><td>{@link #MOMENT_SANTE_OUT}</td><td>A/B 道：特殊值被扣到 0 / 不足被拒</td></tr>
 *   <tr><td>{@link #MOMENT_EQUIP_FORGE}</td><td>C 道：{@link LetheEquipmentsPassive} 穿上四件</td></tr>
 * </table>
 * <b>为什么只给这 15 个时刻</b>：它们就是契约冻结的调用面（各道按常量名直接喊）。
 * 基类自带的 {@code spawn / idle / kill / hurt / lowhp} 等时刻本角色**不提供候选**
 * ⇒ {@code say} 取不到词会安静回 {@code false}（不报错、不占节流），因此不需要为此关掉基类节拍。
 *
 * <h2>外观口径</h2>
 * 配色沿用契约 §3.4 的**同一套基调**（亮蓝 / 紫 / 青白），使"语音颜色"与"粒子颜色"属于同一角色；
 * 每句都加粗（悬浮字在远距离需要更实的笔画才读得清）。风格按语义分：祷词用中心浮现、
 * 施刑用脉冲/乱码、装备用纵向展开、理智见底用模糊消散。
 */
public class LetheFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "lethe_floating_text";

    // ───────── 时刻标识（契约 §3.3：名字与取值都冻结）─────────

    /** 主武器命中。 */
    public static final String MOMENT_WEAPON_HIT = "lethe_weapon_hit";

    /** 共鸣圈生成。 */
    public static final String MOMENT_RING_SPAWN = "lethe_ring_spawn";

    /** 共鸣圈命中。 */
    public static final String MOMENT_RING_HIT = "lethe_ring_hit";

    /** 遗忘：给目标打上标记。 */
    public static final String MOMENT_FORGET_MARK = "lethe_forget_mark";

    /** 遗忘：记忆归还。 */
    public static final String MOMENT_FORGET_RETURN = "lethe_forget_return";

    /** 召圣：抬升标记箭。 */
    public static final String MOMENT_SUMMON_LIFT = "lethe_summon_lift";

    /** 召圣：召回。 */
    public static final String MOMENT_SUMMON_RECALL = "lethe_summon_recall";

    /** 召圣：引爆。 */
    public static final String MOMENT_SUMMON_DETONATE = "lethe_summon_detonate";

    /** 重塑：引导。 */
    public static final String MOMENT_RESHAPE_CHANNEL = "lethe_reshape_channel";

    /** 重塑：落地冲击。 */
    public static final String MOMENT_RESHAPE_IMPACT = "lethe_reshape_impact";

    /** 进化升级。 */
    public static final String MOMENT_EVOLVE = "lethe_evolve";

    /** 箭袋：血泪恢复。 */
    public static final String MOMENT_QUIVER_BLOOD = "lethe_quiver_blood";

    /** 箭袋：箭矢恢复。 */
    public static final String MOMENT_QUIVER_ARROW = "lethe_quiver_arrow";

    /** 特殊值见底 / 不足。 */
    public static final String MOMENT_SANTE_OUT = "lethe_sante_out";

    /** 装备锻成（穿上四件）。 */
    public static final String MOMENT_EQUIP_FORGE = "lethe_equip_forge";

    // ───────── 主题色（= 契约 §3.4 粒子基调的同一组 ARGB）─────────

    /** 亮蓝：日常祷词 / 箭矢。 */
    private static final int BLUE = 0x5AB4FF;

    /** 紫：神性、施刑与疯狂。 */
    private static final int PURPLE = 0x965AFF;

    /** 青白：圣光面的救赎。 */
    private static final int CYAN_WHITE = 0xC8EBFF;

    public LetheFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 钩子覆写：词库 / 风格 / 观感 / 字号 / 节奏 ─────────

    /** 词库（静态；不随运行时状态分档 ⇒ 不需要覆写 {@code lexiconNow()}）。 */
    @Override
    protected TextUtil.Lexicon lexicon() {
        return LEXICON;
    }

    /**
     * 按语义挑浮现风格：罪行用脉冲/乱码，救赎用中心浮现/上浮，理智崩溃用模糊消散。
     * <p>刻意不用"落地砸字"（{@code GROUND_SMASH} / {@code MITA_SCROLL}）：
     * 那两种要逐字查地面、开销更高，而本角色所有时刻都不是"砸地"叙事。
     */
    @Override
    protected TextUtil.Style styleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_WEAPON_HIT -> TextUtil.Style.POP_SCALE;
            case MOMENT_RING_SPAWN -> TextUtil.Style.CENTER_IN_CENTER_OUT;
            case MOMENT_RING_HIT -> TextUtil.Style.PULSE_Y_IN;
            case MOMENT_FORGET_MARK -> TextUtil.Style.DECODE_IN;
            case MOMENT_FORGET_RETURN -> TextUtil.Style.SPIN_LEFT_IN;
            case MOMENT_SUMMON_LIFT -> TextUtil.Style.FLOAT_UP_IN;
            case MOMENT_SUMMON_RECALL -> TextUtil.Style.SLIDE_LEFT_IN;
            case MOMENT_SUMMON_DETONATE -> TextUtil.Style.POP_STRETCH_X;
            case MOMENT_RESHAPE_CHANNEL -> TextUtil.Style.TYPEWRITER_FADE;
            case MOMENT_RESHAPE_IMPACT -> TextUtil.Style.GROW_SLOW;
            case MOMENT_EVOLVE -> TextUtil.Style.CENTER_FADE_IN_CENTER_OUT;
            case MOMENT_QUIVER_BLOOD -> TextUtil.Style.FLIP_RANDOM_Y_IN;
            case MOMENT_QUIVER_ARROW -> TextUtil.Style.POP_STRETCH_Y;
            case MOMENT_SANTE_OUT -> TextUtil.Style.OBFUSCATE_OUT;
            case MOMENT_EQUIP_FORGE -> TextUtil.Style.SHRINK_IN;
            default -> TextUtil.Style.PLAIN_FADE;
        };
    }

    /**
     * 观感：每句都加粗；"救赎面"用青白 / "施刑面"用紫 / "日常面"用亮蓝。
     * <p>疯狂与慈悲同时在场 ⇒ 施刑类台词加 {@code withTremble()}（颤抖），与台词内容同义。
     */
    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_WEAPON_HIT, MOMENT_QUIVER_ARROW -> TextUtil.Look.colored(BLUE).withBold();
            case MOMENT_RING_SPAWN, MOMENT_EVOLVE, MOMENT_SUMMON_LIFT ->
                    TextUtil.Look.glowOf(CYAN_WHITE).withBold();
            case MOMENT_RING_HIT -> TextUtil.Look.colored(BLUE).withBold();
            case MOMENT_FORGET_MARK, MOMENT_SUMMON_RECALL, MOMENT_FORGET_RETURN ->
                    TextUtil.Look.colored(PURPLE).withBold();
            case MOMENT_SUMMON_DETONATE, MOMENT_QUIVER_BLOOD ->
                    TextUtil.Look.gradientOf(PURPLE).withTremble().withBold();
            case MOMENT_RESHAPE_CHANNEL, MOMENT_RESHAPE_IMPACT ->
                    TextUtil.Look.glowOf(CYAN_WHITE).withBold();
            case MOMENT_SANTE_OUT -> TextUtil.Look.gradientOf(PURPLE).withTremble().withBold();
            case MOMENT_EQUIP_FORGE -> TextUtil.Look.gradientOf(BLUE).withBold();
            default -> TextUtil.Look.colored(CYAN_WHITE).withBold();
        };
    }

    /** 字号：救赎 / 进化 / 引爆这类"仪式顶点"略大；日常命中最小。 */
    @Override
    protected float scaleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_WEAPON_HIT, MOMENT_QUIVER_ARROW -> 1.30f;
            case MOMENT_RING_HIT, MOMENT_FORGET_RETURN -> 1.35f;
            case MOMENT_FORGET_MARK, MOMENT_SUMMON_RECALL, MOMENT_RESHAPE_CHANNEL -> 1.40f;
            case MOMENT_SUMMON_LIFT, MOMENT_QUIVER_BLOOD -> 1.45f;
            case MOMENT_RING_SPAWN, MOMENT_RESHAPE_IMPACT, MOMENT_SANTE_OUT, MOMENT_EQUIP_FORGE -> 1.50f;
            case MOMENT_EVOLVE, MOMENT_SUMMON_DETONATE -> 1.55f;
            default -> 1.30f;
        };
    }

    /** 节奏：祷词类偏慢（90~100），施刑类更快（150~160），"数字越大人越狂"。 */
    @Override
    protected int speedFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_EQUIP_FORGE, MOMENT_SANTE_OUT -> 90;
            case MOMENT_RESHAPE_CHANNEL -> 100;
            case MOMENT_FORGET_MARK, MOMENT_SUMMON_LIFT -> 120;
            case MOMENT_QUIVER_ARROW, MOMENT_RING_SPAWN -> 130;
            case MOMENT_WEAPON_HIT, MOMENT_RING_HIT, MOMENT_FORGET_RETURN -> 140;
            case MOMENT_SUMMON_RECALL, MOMENT_RESHAPE_IMPACT, MOMENT_EVOLVE -> 150;
            default -> 155;
        };
    }

    // ───────── 词库（每个时刻 ≥1 句；关键时刻 2~3 句轮换）─────────

    private static final String[] WEAPON_HIT_LINES = {
            "神在看。",
            "痛，是恩典。",
            "我会救你，再吃了你。"
    };

    private static final String[] RING_SPAWN_LINES = {
            "圈内皆罪人。",
            "钟声已响。",
            "都站进来。"
    };

    private static final String[] RING_HIT_LINES = {
            "赎你的罪。",
            "听见了吗？",
            "这是慈悲。"
    };

    private static final String[] FORGET_MARK_LINES = {
            "忘了吧。",
            "我替你记着。",
            "忘记，是仁慈。"
    };

    private static final String[] FORGET_RETURN_LINES = {
            "都还给你。",
            "记忆归位。",
            "拿好你的过去。"
    };

    private static final String[] SUMMON_LIFT_LINES = {
            "升起来。",
            "圣座在上。",
            "起来吧，孩子。"
    };

    private static final String[] SUMMON_RECALL_LINES = {
            "回来。",
            "它们都属于我。",
            "回到我身边。"
    };

    private static final String[] SUMMON_DETONATE_LINES = {
            "献祭。",
            "烧尽吧。",
            "主啊，收下他们。"
    };

    private static final String[] RESHAPE_CHANNEL_LINES = {
            "重塑的钟声。",
            "让我想想你的形状。",
            "神说，要有光。"
    };

    private static final String[] RESHAPE_IMPACT_LINES = {
            "新生了。",
            "看，多美。",
            "这是你的救赎。"
    };

    private static final String[] EVOLVE_LINES = {
            "我听见祂了。",
            "更近一步。",
            "这是恩典。"
    };

    private static final String[] QUIVER_BLOOD_LINES = {
            "血是圣餐。",
            "饮下它。",
            "红色的祷词。"
    };

    private static final String[] QUIVER_ARROW_LINES = {
            "箭上有名字。",
            "每一支都算过。",
            "搭箭，祈祷。"
    };

    private static final String[] SANTE_OUT_LINES = {
            "我的理智……见底了。",
            "别怕，我仍是祂的。",
            "黑，正在烧。"
    };

    private static final String[] EQUIP_FORGE_LINES = {
            "甲壳合身吗？",
            "枷锁也是恩赐。",
            "穿上它，成为圣物。"
    };

    // ───────── 基础时刻（2026-10-31 全员语音：影都口径 —— 巨大怪物，少有人性，却要有人性）─────────

    private static final String[] IDLE_LINES = {
            "神还在听吗。",
            "我数过自己的骨头。",
            "圣餐是热的。",
            "祂答应过，会记得我的名字。",
            "钟摆停了。我还在。",
            "这具身体里，住过一个人。"
    };

    private static final String[] SPAWN_LINES = {
            "以圣名问安。",
            "罪人们，祷告开始了。"
    };

    private static final String[] HURT_LINES = {
            "疼是恩典。",
            "别碰圣痕。"
    };

    private static final String[] KILL_LINES = {
            "主收下了。",
            "又一个名字。",
            "安息吧。"
    };

    private static final String[] LOW_HP_LINES = {
            "我仍是祂的。",
            "别关灯，祂会看不见我。"
    };

    /** 整本词库（时刻 → 候选；{@code say} 随机取一条，{@code sayBurst} 取多条不重复）。 */
    private static final TextUtil.Lexicon LEXICON = lexiconOf(
            MOMENT_IDLE, IDLE_LINES,
            MOMENT_SPAWN, SPAWN_LINES,
            MOMENT_HURT, HURT_LINES,
            MOMENT_KILL, KILL_LINES,
            MOMENT_LOW_HP, LOW_HP_LINES,
            MOMENT_WEAPON_HIT, WEAPON_HIT_LINES,
            MOMENT_RING_SPAWN, RING_SPAWN_LINES,
            MOMENT_RING_HIT, RING_HIT_LINES,
            MOMENT_FORGET_MARK, FORGET_MARK_LINES,
            MOMENT_FORGET_RETURN, FORGET_RETURN_LINES,
            MOMENT_SUMMON_LIFT, SUMMON_LIFT_LINES,
            MOMENT_SUMMON_RECALL, SUMMON_RECALL_LINES,
            MOMENT_SUMMON_DETONATE, SUMMON_DETONATE_LINES,
            MOMENT_RESHAPE_CHANNEL, RESHAPE_CHANNEL_LINES,
            MOMENT_RESHAPE_IMPACT, RESHAPE_IMPACT_LINES,
            MOMENT_EVOLVE, EVOLVE_LINES,
            MOMENT_QUIVER_BLOOD, QUIVER_BLOOD_LINES,
            MOMENT_QUIVER_ARROW, QUIVER_ARROW_LINES,
            MOMENT_SANTE_OUT, SANTE_OUT_LINES,
            MOMENT_EQUIP_FORGE, EQUIP_FORGE_LINES
    );

    // ───────── 探针读口（同包离线单测用；只读，不改任何状态）─────────

    /** 本角色的全部时刻（顺序 = 契约 §3.3 列出序）—— 单测据此遍历"每个时刻都有台词"。 */
    static final List<String> ALL_MOMENTS = List.of(
            MOMENT_WEAPON_HIT, MOMENT_RING_SPAWN, MOMENT_RING_HIT,
            MOMENT_FORGET_MARK, MOMENT_FORGET_RETURN,
            MOMENT_SUMMON_LIFT, MOMENT_SUMMON_RECALL, MOMENT_SUMMON_DETONATE,
            MOMENT_RESHAPE_CHANNEL, MOMENT_RESHAPE_IMPACT, MOMENT_EVOLVE,
            MOMENT_QUIVER_BLOOD, MOMENT_QUIVER_ARROW, MOMENT_SANTE_OUT, MOMENT_EQUIP_FORGE);

    /** 本角色全部台词（按组展平）—— 校验"每句都放得进单段字数上限"。 */
    static List<String> allLines() {
        List<String> out = new java.util.ArrayList<>();
        for (String[] group : List.of(WEAPON_HIT_LINES, RING_SPAWN_LINES, RING_HIT_LINES,
                FORGET_MARK_LINES, FORGET_RETURN_LINES, SUMMON_LIFT_LINES, SUMMON_RECALL_LINES,
                SUMMON_DETONATE_LINES, RESHAPE_CHANNEL_LINES, RESHAPE_IMPACT_LINES, EVOLVE_LINES,
                QUIVER_BLOOD_LINES, QUIVER_ARROW_LINES, SANTE_OUT_LINES, EQUIP_FORGE_LINES)) {
            out.addAll(List.of(group));
        }
        return List.copyOf(out);
    }

    // ───────── 描述符 ─────────

    /**
     * 本组件的被动描述符。
     *
     * <p>★ 泛型自类型（{@code Specification<自类>}）是硬要求 —— 否则对外声明的类型停在
     * {@code FloatingTextComponent}，别的组件 {@code requires(LetheFloatingTextComponent.class)}
     * 会在装配期报"missing component type"（本工程踩过的坑，见 {@code FloatingTextComponent} 的注释）。
     */
    public static final class Specification
            extends FloatingTextComponent.Specification<LetheFloatingTextComponent> {

        public Specification() {
            super(Component.text("冷识语音"),
                    List.of(
                            Component.text("主武器命中 / 共鸣圈生成与命中"),
                            Component.text("遗忘：标记与归还；召圣：抬升 / 召回 / 引爆"),
                            Component.text("重塑：引导与冲击；进化、箭袋（血泪 / 箭矢）"),
                            Component.text("特殊值见底、装备锻成；每时刻 2~3 句轮换"),
                            Component.text("疯狂主教的祷词：短句、仪式感，邪恶与慈悲并存"),
                            Component.text("亮蓝 / 紫 / 青白三色主题，全部加粗")
                    ));
            //基类要挂受伤 / 击杀订阅（{@code getComponent(VitalsComponent.class)}）⇒ 显式声明，堵住装配期漏配
            requires(VitalsComponent.class);
        }

        /**
         * 造出本类（而不是基类的通用实现）。
         * <p>直接覆写 {@code create} 而不是工厂钩子 {@code createComponent}：返回值类型就是本类，
         * "本组件确实被造出来"是编译期事实，不依赖基类那一处 unchecked 转型。
         */
        @Override
        public LetheFloatingTextComponent create(String id, ComponentServicesPort services) {
            return new LetheFloatingTextComponent(id, services, this);
        }
    }
}
