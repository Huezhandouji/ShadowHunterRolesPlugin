package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 「抗争」角色语音（第 7 个组件）。
 *
 * <p>语气：**被压迫者的革命领袖**——多反问、少陈述；对敌人是审判，对同伴是克制。
 * 需求点名的三个技能台词逐字收录；空闲 / 受击 / 击杀 / 死亡等状态按同一语气自拟补全。
 *
 * <p>外观：全部**加粗**、字号放大，白色 + 淡紫泛光（需求：主要颜色白与紫）。
 */
public class ResistFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_floating_text";

    // ───────── 时刻 ─────────

    /** 主武器命中。 */
    public static final String MOMENT_HIT = "resist_hit";

    /** 一技能·谷底的怒吼。 */
    public static final String MOMENT_SKILL_ROAR = "resist_skill_roar";

    /** 二技能·命运的征服。 */
    public static final String MOMENT_SKILL_CONQUEST = "resist_skill_conquest";

    /** 三技能·被欺压者的咆哮。 */
    public static final String MOMENT_SKILL_RAMPAGE = "resist_skill_rampage";

    /** 被控制 / 挣脱。 */
    public static final String MOMENT_CONTROLLED = "resist_controlled";

    /** 死亡。 */
    public static final String MOMENT_DEATH = "resist_death";

    // ───────── 台词 ─────────

    private static final String[] IDLE_LINES = {
            "剑，唯有剑。。。。",
            "你听见了吗？我们脚下。。怒吼。。",
            "自由，自由，自由！",
            "还要退到哪里去？",
            "冷，好冷，死亡，我。",
            "问过黎明吗？",
            "死亡，死亡，死亡，为何？为何？",
            "你们，黎明，还是更喜欢白昼？",
            "哈哈哈哈，所以说，没有希望！",
            "ResIsT",
            "你们，我们，总有一天，让它们消散"
    };

    private static final String[] SPAWN_LINES = {
            "起来。",
            "拔出你的剑",
            "我不会死亡"
    };

    private static final String[] HIT_LINES = {
            "站起来！",
            "不要跪下！",
            "你还要沉默多久？",
            "痛苦啊！",
            "前进！",
            "你不可倒下！"
    };

    private static final String[] ROAR_LINES = {
            "压迫不止，抗争无休",
            "奴役永存，反抗永在",
            "痛苦回眸，死亡不现"
    };

    private static final String[] CONQUEST_LINES = {
            "你所希望的黎明真的是黎明吗？",
            "你看见了那无边无底的黑暗吗？",
            "我要在谷底，撕裂你",
            "你们忘了你们所拥有的，你们忘了你们所失去的！"
    };

    private static final String[] RAMPAGE_LINES = {
            "黑暗，莫想笼罩！",
            "我不再祈求白夜，我要摧毁黎明！",
            "天际线！天际线。。。。我要斩开这一切！",
            "你们真的能走出这里吗！",
            "让白昼来，让白昼来审判！"
    };

    private static final String[] CONTROLLED_LINES = {
            "我要睁开我的眼睛！",
            "莫想让枷锁，困住世人！",
            "死亡呐！让我离开！",
            "谷底的魂灵又再次哭嚎！"
    };

    private static final String[] HURT_LINES = {
            "来吧",
            "你们，不值一提",
            "哈哈哈",
            "痛苦。。。"
    };

    private static final String[] LOW_HP_LINES = {
            "不要以为能打倒我。",
            "苦痛已经和我一体！",
            "你们的剑还是太钝！",
            "让我承受吧，让我哭嚎！"
    };

    private static final String[] KILL_LINES = {
            "请你安息",
            "不要如此悔恨",
            "上百条道路，误入歧途，只是常见",
            "带着你，走下去",
            "我只能，叹息。。。",
            "回首吧。。。"
    };

    private static final String[] DEATH_LINES = {
            "你们，黎明。。。",
            "让我，看见，白昼。。。",
            "走吧。。。"
    };

    /** 本角色全部时刻（探针读口，单测用）。 */
    static final List<String> ALL_MOMENTS = List.of(
            MOMENT_IDLE, MOMENT_SPAWN, MOMENT_HIT,
            MOMENT_SKILL_ROAR, MOMENT_SKILL_CONQUEST, MOMENT_SKILL_RAMPAGE,
            MOMENT_CONTROLLED, MOMENT_HURT, MOMENT_LOW_HP, MOMENT_KILL, MOMENT_DEATH);

    /** 全部台词（展平，单测校验字数 / 去重）。 */
    static List<String> allLines() {
        List<String> out = new ArrayList<>();
        for (String[] group : List.of(IDLE_LINES, SPAWN_LINES, HIT_LINES, ROAR_LINES, CONQUEST_LINES,
                RAMPAGE_LINES, CONTROLLED_LINES, HURT_LINES, LOW_HP_LINES, KILL_LINES, DEATH_LINES)) {
            out.addAll(List.of(group));
        }
        return List.copyOf(out);
    }

    public ResistFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 词库 / 风格 ─────────

    @Override
    protected TextUtil.Lexicon lexicon() {
        return lexiconOf(
                MOMENT_IDLE, IDLE_LINES,
                MOMENT_SPAWN, SPAWN_LINES,
                MOMENT_HIT, HIT_LINES,
                MOMENT_SKILL_ROAR, ROAR_LINES,
                MOMENT_SKILL_CONQUEST, CONQUEST_LINES,
                MOMENT_SKILL_RAMPAGE, RAMPAGE_LINES,
                MOMENT_CONTROLLED, CONTROLLED_LINES,
                MOMENT_HURT, HURT_LINES,
                MOMENT_LOW_HP, LOW_HP_LINES,
                MOMENT_KILL, KILL_LINES,
                MOMENT_DEATH, DEATH_LINES
        );
    }

    // ───────── 播报节奏（2026-10-19 需求：稀疏化，避免刷屏）─────────

    /** 空闲周期：**5 秒**判一次（严格周期，不加抖动）。 */
    public static final int IDLE_PERIOD_TICKS = 100;

    /** 空闲到点时开口的概率：**30%**。 */
    public static final double IDLE_CHANCE = 0.3d;

    /** 每次受伤开口的概率：**20%**。 */
    public static final double HURT_CHANCE = 0.2d;

    @Override
    protected int idleIntervalTicks() {
        return IDLE_PERIOD_TICKS;
    }

    @Override
    protected int idleJitterTicks() {
        return 0;       //严格 5 秒一次判定
    }

    @Override
    protected double idleChance() {
        return IDLE_CHANCE;
    }

    @Override
    protected double hurtChance() {
        return HURT_CHANCE;
    }

    @Override
    protected TextUtil.Style styleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_SKILL_ROAR -> TextUtil.STYLE_DECREE;
            case MOMENT_SKILL_CONQUEST, MOMENT_SKILL_RAMPAGE -> TextUtil.STYLE_EMPHASIS;
            case MOMENT_HIT -> TextUtil.STYLE_EMPHASIS;
            case MOMENT_CONTROLLED, MOMENT_HURT -> TextUtil.STYLE_GLITCH;
            case MOMENT_KILL -> TextUtil.STYLE_BURST;
            case MOMENT_LOW_HP -> TextUtil.STYLE_GLITCH;
            case MOMENT_SPAWN -> TextUtil.STYLE_WHISPER;
            case MOMENT_DEATH -> TextUtil.STYLE_CALM;
            default -> TextUtil.STYLE_CALM;
        };
    }

    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        boolean skill = key.equals(MOMENT_SKILL_ROAR) || key.equals(MOMENT_SKILL_CONQUEST)
                || key.equals(MOMENT_SKILL_RAMPAGE);
        if (skill) {
            //白 + 淡紫泛光 + 加粗（需求主色）
            return new TextUtil.Look(0xFFF2F2FF, true, true, 0xFFB388FF, false, true);
        }
        if (key.equals(MOMENT_KILL)) {
            return new TextUtil.Look(0xFFEDE7FF, false, true, 0xFFB388FF, false, true);
        }
        if (key.equals(MOMENT_HURT) || key.equals(MOMENT_CONTROLLED) || key.equals(MOMENT_LOW_HP)) {
            return new TextUtil.Look(0xFFE6E6F2, false, false, 0, true, true);
        }
        return new TextUtil.Look(0xFFF4F4FF, false, true, 0xFFFFFFFF, false, true);
    }

    @Override
    protected float scaleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_SKILL_ROAR, MOMENT_SKILL_CONQUEST, MOMENT_SKILL_RAMPAGE -> 1.4f;
            case MOMENT_KILL -> 1.3f;
            case MOMENT_LOW_HP, MOMENT_DEATH -> 1.25f;
            default -> 1.15f;
        };
    }

    @Override
    protected int speedFor(String moment) {
        String key = moment == null ? "" : moment;
        if (key.startsWith("resist_skill")) {
            return 120;
        }
        return 80;
    }

    // ───────── 显式调用面 ─────────

    /** 技能台词（三个主动各一组）。 */
    public boolean saySkill(Player who, String moment) {
        return say(who, moment, null);
    }

    /** 状态台词（命中 / 受击 / 死亡等）。 */
    public boolean saySpecial(Player who, String moment) {
        return say(who, moment, null);
    }

    /**
     * 死亡台词：基类不区分"受伤"与"死亡"（只喊受击），这里补一句送别。
     * 与受击同一刻，可能被台词节流拒掉一句 —— 这是已知代价（与 sayaka 同规）。
     */
    @Override
    public void onDamaged(Player source, double amount) {
        super.onDamaged(source, amount);
        Player self = svc().self().player();
        if (self != null && (self.isDead() || self.getHealth() <= 0d)) {
            say(self, MOMENT_DEATH, null);
        }
    }

    // ───────── 探针读口（同包离线单测用） ─────────

    /** 某时刻的全部候选台词（只读；基类的 {@code lexiconNow()} 是 protected，测试不继承它）。 */
    List<String> linesOf(String moment) {
        return lexiconNow().candidates(moment);
    }

    // ───────── 描述符 ─────────

    /** 本组件的描述符（泛型自类型，否则别的组件 requires 会找不到）。 */
    public static final class Specification
            extends FloatingTextComponent.Specification<ResistFloatingTextComponent> {

        public Specification() {
            super(Component.text("抗争语音"),
                    List.of(
                            Component.text("一技能：压迫不止，抗争无休 / 奴役永存，反抗永在 / 痛苦回眸，死亡不现"),
                            Component.text("二技能：四句质问（黎明、黑暗、谷底、失去）"),
                            Component.text("三技能：五句宣告（黑暗、黎明、天际线、白夜审判）"),
                            Component.text("另含空闲 / 命中 / 受控 / 受击 / 濒死 / 击杀 / 死亡的自拟反问句"),
                            Component.text("全部加粗放大，白色 + 淡紫泛光")
                    ));
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new ResistFloatingTextComponent(id, services, this);
        }
    }
}
