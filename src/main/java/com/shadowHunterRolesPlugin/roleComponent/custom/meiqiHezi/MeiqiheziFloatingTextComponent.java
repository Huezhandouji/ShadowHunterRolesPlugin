package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.datapack.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 「梅妻鹤子」语音库。
 *
 * <h2>角色语气</h2>
 * 半疯癫、半清醒；女性、年纪不大、开朗大方、小队副队长。
 * ⇒ 台词走"笑着说出很疯的话"：句尾常带语气词（呀 / 嘛 / 哦），疯起来短句连缀，
 * 清醒时忽然正经一句"我还在"，再立刻歪回去。**不写惨叫、不写自怜**（她怕的是安静，不是疼）。
 *
 * <p>配色：红 + 金；0 档爆发用颤抖 + 加粗（视觉上真的在抖）。
 */
public class MeiqiheziFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "meiqihezi_floating_text";

    // ───────── 时刻 ─────────

    /** 普攻命中。 */
    public static final String MOMENT_HIT = "meiqihezi_hit";

    /** 开始蓄力。 */
    public static final String MOMENT_CHARGE = "meiqihezi_charge";

    /** 丢出武器。 */
    public static final String MOMENT_THROW = "meiqihezi_throw";

    /** 拾回武器。 */
    public static final String MOMENT_PICKUP = "meiqihezi_pickup";

    /** 满充能。 */
    public static final String MOMENT_FULL_CHARGE = "meiqihezi_full_charge";

    /** 一技能·漫不经心。 */
    public static final String MOMENT_SKILL_UNCONCERN = "meiqihezi_skill_unconcern";

    /** 二技能·血腥连斩。 */
    public static final String MOMENT_SKILL_BLOODY = "meiqihezi_skill_bloody";

    /** 三技能·圆弧斩。 */
    public static final String MOMENT_SKILL_CIRCLE = "meiqihezi_skill_circle";

    /** 0 档爆发。 */
    public static final String MOMENT_BURST = "meiqihezi_burst";

    /** 被控制。 */
    public static final String MOMENT_CONTROLLED = "meiqihezi_controlled";

    /** 死亡。 */
    public static final String MOMENT_DEATH = "meiqihezi_death";

    // ───────── 台词 ─────────

    private static final String[] IDLE_LINES = {
            "嘿嘿……今天的风，是甜的。",
            "副队长？副队长在发呆呀。",
            "别看我，我在数心跳。",
            "清醒一会儿……就一会儿。",
            "刀上有梅花的味道，闻到了吗？",
            "队长说要多笑，我笑了哦。",
            "安静的时候，脑子会响。",
            "今天也，活着呢。"
    };

    private static final String[] SPAWN_LINES = {
            "梅妻鹤子，报到！",
            "队里还缺人吗？我会笑，也会杀人。",
            "决爵出鞘——谁先来？",
            "副队长上班啦，都精神点！"
    };

    private static final String[] HIT_LINES = {
            "叮——好听吧？",
            "这一下，算你走运。",
            "别急着倒，我还没笑够。",
            "血是热的，真好。",
            "再来一下嘛。"
    };

    private static final String[] CHARGE_LINES = {
            "让我……安静一下。",
            "把理智，一点点押上去。",
            "再等一下，就一下。",
            "嘘——我在听它说话。"
    };

    private static final String[] THROW_LINES = {
            "接着——！",
            "决爵，去打个招呼。",
            "别眨眼哦。",
            "飞吧飞吧飞吧！"
    };

    private static final String[] PICKUP_LINES = {
            "回来啦，宝贝。",
            "乖，下次别乱跑。",
            "嗯，还是你顺手。"
    };

    private static final String[] FULL_CHARGE_LINES = {
            "满了，满了！",
            "听见了吗？我脑子里在敲钟。",
            "三层……够杀几个？"
    };

    private static final String[] UNCONCERN_LINES = {
            "漫不经心——才怪！",
            "快一点，再快一点！",
            "追不上我吧？"
    };

    private static final String[] BLOODY_LINES = {
            "一路碾过去！",
            "别挡路呀！",
            "切开来看看？",
            "十格，够不够我笑？"
    };

    private static final String[] CIRCLE_LINES = {
            "转起来——都别动！",
            "一圈，够不够？",
            "站好，我请你们看花。"
    };

    private static final String[] BURST_LINES = {
            "哈——哈哈哈哈哈！",
            "清醒？那种东西，扔掉吧。",
            "都、来、陪、我。",
            "我不疼了，一点都不疼了！"
    };

    private static final String[] CONTROLLED_LINES = {
            "别拽我……",
            "我还没疯够。",
            "放开……我说，放开。"
    };

    private static final String[] DEATH_LINES = {
            "副队长……先走了。",
            "对不起，队长。",
            "这次……真的安静了。"
    };

    private static final String[] KILL_LINES = {
            "下一个。",
            "哎，站不稳了吧。",
            "笑一个嘛。",
            "又少一个陪我玩的。"
    };

    private static final String[] HURT_LINES = {
            "疼……但很清醒。",
            "再来。",
            "哦，这个有意思。"
    };

    private static final String[] LOW_HP_LINES = {
            "还没完……我还站着。",
            "别扶我，我能笑。"
    };

    public MeiqiheziFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 词库 ─────────

    @Override
    protected TextUtil.Lexicon lexicon() {
        return lexiconOf(
                MOMENT_IDLE, IDLE_LINES,
                MOMENT_SPAWN, SPAWN_LINES,
                MOMENT_HIT, HIT_LINES,
                MOMENT_CHARGE, CHARGE_LINES,
                MOMENT_THROW, THROW_LINES,
                MOMENT_PICKUP, PICKUP_LINES,
                MOMENT_FULL_CHARGE, FULL_CHARGE_LINES,
                MOMENT_SKILL_UNCONCERN, UNCONCERN_LINES,
                MOMENT_SKILL_BLOODY, BLOODY_LINES,
                MOMENT_SKILL_CIRCLE, CIRCLE_LINES,
                MOMENT_BURST, BURST_LINES,
                MOMENT_CONTROLLED, CONTROLLED_LINES,
                MOMENT_DEATH, DEATH_LINES,
                MOMENT_KILL, KILL_LINES,
                MOMENT_HURT, HURT_LINES,
                MOMENT_LOW_HP, LOW_HP_LINES
        );
    }

    /** 外部调用面：按时刻随机喊一句（无候选则静默）。 */
    public boolean sayMoment(Player who, String moment) {
        return say(who, moment, null);
    }

    // ───────── 节奏 ─────────

    @Override
    protected int idleIntervalTicks() {
        return 100;
    }

    @Override
    protected int idleJitterTicks() {
        return 20;
    }

    @Override
    protected double idleChance() {
        return 0.35d;
    }

    @Override
    protected double hurtChance() {
        return 0.25d;
    }

    // ───────── 外观 ─────────

    @Override
    protected TextUtil.Style styleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_BURST -> TextUtil.STYLE_BURST;
            case MOMENT_SKILL_CIRCLE, MOMENT_SKILL_BLOODY, MOMENT_THROW -> TextUtil.STYLE_EMPHASIS;
            case MOMENT_CONTROLLED, MOMENT_HURT, MOMENT_LOW_HP -> TextUtil.STYLE_GLITCH;
            case MOMENT_DEATH -> TextUtil.STYLE_CALM;
            case MOMENT_SPAWN -> TextUtil.STYLE_WHISPER;
            default -> TextUtil.STYLE_CALM;
        };
    }

    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        if (MOMENT_BURST.equals(key)) {
            // 爆裂：金 + 颤抖 + 加粗
            return new TextUtil.Look(0xFFFFD34D, false, true, 0xFFFF2A2A, true, true);
        }
        if (MOMENT_SKILL_BLOODY.equals(key) || MOMENT_SKILL_CIRCLE.equals(key) || MOMENT_THROW.equals(key)) {
            // 技能：红 + 金泛光 + 加粗
            return new TextUtil.Look(0xFFFFE2E2, false, true, 0xFFFFC83C, false, true);
        }
        if (MOMENT_CHARGE.equals(key) || MOMENT_FULL_CHARGE.equals(key)) {
            return new TextUtil.Look(0xFFFFC83C, false, true, 0xFFFF2020, false, false);
        }
        if (MOMENT_CONTROLLED.equals(key) || MOMENT_HURT.equals(key) || MOMENT_LOW_HP.equals(key)) {
            return new TextUtil.Look(0xFFFFB0B0, false, false, 0, true, false);
        }
        return new TextUtil.Look(0xFFFFF2F2, false, true, 0xFFFF6A3C, false, false);
    }

    @Override
    protected float scaleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_BURST -> 1.65f;
            case MOMENT_SKILL_BLOODY, MOMENT_SKILL_CIRCLE, MOMENT_THROW -> 1.35f;
            case MOMENT_DEATH -> 0.95f;
            default -> 1.1f;
        };
    }

    // ───────── 探针读口（同包离线单测用） ─────────

    static List<String> allLines() {
        List<String> out = new ArrayList<>();
        for (String[] group : List.of(IDLE_LINES, SPAWN_LINES, HIT_LINES, CHARGE_LINES, THROW_LINES,
                PICKUP_LINES, FULL_CHARGE_LINES, UNCONCERN_LINES, BLOODY_LINES, CIRCLE_LINES,
                BURST_LINES, CONTROLLED_LINES, DEATH_LINES, KILL_LINES, HURT_LINES, LOW_HP_LINES)) {
            out.addAll(List.of(group));
        }
        return List.copyOf(out);
    }

    /** 死亡台词在受伤钩子里补喊（角色被清前最后一句）。 */
    @Override
    public void onDamaged(Player source, double amount) {
        super.onDamaged(source, amount);
        Player self = svc().self().player();
        if (self != null && (self.isDead() || self.getHealth() <= 0d)) {
            say(self, MOMENT_DEATH, null);
        }
    }

    /** 描述符（泛型自类型）。 */
    public static final class Specification
            extends FloatingTextComponent.Specification<MeiqiheziFloatingTextComponent> {

        public Specification() {
            super(Component.text("梅妻鹤子语音"),
                    List.of(
                            Component.text("半疯癫、半清醒；女性、年轻、开朗大方的副队长"),
                            Component.text("空闲 / 登场 / 命中 / 蓄力 / 投掷 / 拾回 / 满充能"),
                            Component.text("三技能各一组，另有 0 档爆发 / 受控 / 受击 / 濒死 / 击杀 / 死亡"),
                            Component.text("红 + 金配色；0 档爆发台词带颤抖")
                    ));
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new MeiqiheziFloatingTextComponent(id, services, this);
        }
    }
}
