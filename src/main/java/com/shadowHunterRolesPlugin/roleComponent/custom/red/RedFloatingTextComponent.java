package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 「红」的语音库：**新婚妇人** —— 刚嫁人、还没从喜悦里回过神，丈夫却已经不在了。
 * 于是她把"等你回家"说成日常，把占有说成体贴；语速慢、句子短、不用感叹号。
 *
 * <p>色彩：红白为主，大招带一点紫；受控 / 受击走颤抖。
 */
public class RedFloatingTextComponent extends FloatingTextComponent {

    public static final String ID = "red_floating_text";

    public static final String MOMENT_HIT = "red_hit";
    public static final String MOMENT_SKILL_THRUST = "red_skill_thrust";
    public static final String MOMENT_SKILL_SHOCK = "red_skill_shock";
    public static final String MOMENT_ULT_START = "red_ult_start";
    public static final String MOMENT_ULT_END = "red_ult_end";
    public static final String MOMENT_CONTROLLED = "red_controlled";
    public static final String MOMENT_DEATH = "red_death";

    private static final String[] IDLE_LINES = {
            "你回来了。",
            "我煮了汤，还热着。",
            "今天……也等你。",
            "别走太远。我会想。",
            "窗我留着，灯也留着。",
            "你身上，有别人的味道。",
            "没事。我不问。",
            "再坐一会儿。",
            "新做的衣裳，好看吗。"
    };

    private static final String[] SPAWN_LINES = {
            "你回来了。我等了很久。",
            "这一次，别再走了。",
            "门没锁。进来吧。"
    };

    private static final String[] HIT_LINES = {
            "疼吗。",
            "别怕。是我。",
            "你看，血是热的。",
            "这样……你就不会走了。"
    };

    private static final String[] THRUST_LINES = {
            "别躲。让我抱抱。",
            "一下就好。",
            "你身上，都是我的。",
            "到最后了。别哭。"
    };

    private static final String[] SHOCK_LINES = {
            "都别走。",
            "烟里……有我。",
            "安静一点。"
    };

    private static final String[] ULT_START_LINES = {
            "血亲呼唤。",
            "回来吧。回到我身边。",
            "这一次，谁也别走。"
    };

    private static final String[] ULT_END_LINES = {
            "……又剩我一个。",
            "没关系。我等得起。"
    };

    private static final String[] CONTROLLED_LINES = {
            "别拽我……",
            "我还没抱够。"
    };

    private static final String[] KILL_LINES = {
            "睡吧。",
            "这样就好了。",
            "下辈子，早点回来。"
    };

    private static final String[] HURT_LINES = {
            "你也舍得。",
            "我疼。你也疼吗。"
    };

    private static final String[] LOW_HP_LINES = {
            "别离开我……"
    };

    private static final String[] DEATH_LINES = {
            "我先……回去等你。",
            "汤……要凉了。"
    };

    public RedFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    @Override
    protected TextUtil.Lexicon lexicon() {
        return lexiconOf(
                MOMENT_IDLE, IDLE_LINES,
                MOMENT_SPAWN, SPAWN_LINES,
                MOMENT_HIT, HIT_LINES,
                MOMENT_SKILL_THRUST, THRUST_LINES,
                MOMENT_SKILL_SHOCK, SHOCK_LINES,
                MOMENT_ULT_START, ULT_START_LINES,
                MOMENT_ULT_END, ULT_END_LINES,
                MOMENT_CONTROLLED, CONTROLLED_LINES,
                MOMENT_KILL, KILL_LINES,
                MOMENT_HURT, HURT_LINES,
                MOMENT_LOW_HP, LOW_HP_LINES,
                MOMENT_DEATH, DEATH_LINES
        );
    }

    /** 外部调用面：随机一句（无候选则静默）。 */
    public boolean sayMoment(Player who, String moment) {
        return say(who, moment, null);
    }

    @Override
    protected int idleIntervalTicks() {
        return 120;
    }

    @Override
    protected int idleJitterTicks() {
        return 40;
    }

    @Override
    protected double idleChance() {
        return 0.35d;
    }

    @Override
    protected double hurtChance() {
        return 0.25d;
    }

    @Override
    protected TextUtil.Style styleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_ULT_START -> TextUtil.STYLE_BURST;
            case MOMENT_SKILL_THRUST, MOMENT_SKILL_SHOCK -> TextUtil.STYLE_EMPHASIS;
            case MOMENT_CONTROLLED, MOMENT_HURT, MOMENT_LOW_HP -> TextUtil.STYLE_GLITCH;
            case MOMENT_DEATH, MOMENT_ULT_END -> TextUtil.STYLE_CALM;
            default -> TextUtil.STYLE_CALM;
        };
    }

    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        if (MOMENT_ULT_START.equals(key)) {
            return new TextUtil.Look(0xFFFFE8EA, false, true, 0xFF7A1020, false, true);
        }
        if (MOMENT_SKILL_THRUST.equals(key) || MOMENT_SKILL_SHOCK.equals(key)) {
            return new TextUtil.Look(0xFFFFD9DD, false, true, 0xFF9B4DD8, false, false);
        }
        if (MOMENT_CONTROLLED.equals(key) || MOMENT_HURT.equals(key) || MOMENT_LOW_HP.equals(key)) {
            return new TextUtil.Look(0xFFE0C0C6, false, false, 0, true, false);
        }
        return new TextUtil.Look(0xFFF3E6E9, false, true, 0xFFE6122B, false, false);
    }

    @Override
    protected float scaleFor(String moment) {
        return MOMENT_ULT_START.equals(moment) ? 1.5f : 1.05f;
    }

    @Override
    public void onDamaged(Player source, double amount) {
        super.onDamaged(source, amount);
        Player self = svc().self().player();
        if (self != null && (self.isDead() || self.getHealth() <= 0d)) {
            say(self, MOMENT_DEATH, null);
        }
    }

    /** 描述符。 */
    public static final class Specification extends FloatingTextComponent.Specification<RedFloatingTextComponent> {

        public Specification() {
            super(Component.text("红 · 语音"),
                    List.of(
                            Component.text("新婚妇人：把占有说成体贴，把等待说成日常"),
                            Component.text("含空闲 / 登场 / 命中 / 三技能 / 大招起收 / 受控 / 击杀 / 受击 / 濒死 / 死亡")
                    ));
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new RedFloatingTextComponent(id, services, this);
        }
    }
}
