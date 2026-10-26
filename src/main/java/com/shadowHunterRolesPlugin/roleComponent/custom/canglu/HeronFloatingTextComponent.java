package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 苍鹭语音：**执着的追梦者 / 古典乐爱好者 / 梦碎者**。
 *
 * <p>中年人，声音低、慢，句子像在自言自语；谈音乐时短暂发亮，随即沉下去。
 * 他妄想改变一切，最后只能用沉默收尾。
 */
public class HeronFloatingTextComponent extends FloatingTextComponent {

    public static final String ID = "heron_floating_text";

    public static final String MOMENT_HIT = "heron_hit";
    public static final String MOMENT_HOOK = "heron_hook";
    public static final String MOMENT_DOMAIN = "heron_domain";
    public static final String MOMENT_LOW = "heron_low";
    public static final String MOMENT_DEATH = "heron_death";
    public static final String MOMENT_CONTROLLED = "heron_controlled";

    private static final String[] IDLE = {
            "这一段，应该是铜管。",
            "我年轻时，能把整首听完。",
            "唱片还在转。人已经不在了。",
            "再给我一点时间。就一点。",
            "他们说我疯了。也许吧。",
            "蓝色的地方，声音是干净的。",
            "我改不了结局。我知道。",
            "……算了。"
    };

    private static final String[] SPAWN = {
            "序曲还没结束。",
            "让我把这一章写完。",
            "你们听得见吗。"
    };

    private static final String[] HIT = {
            "不和谐音。",
            "这一下，是节拍。",
            "别打断我。",
            "疼。但还在调上。"
    };

    private static final String[] HOOK = {
            "抓到了。",
            "回来。你还没听完。",
            "别走。"
    };

    private static final String[] DOMAIN = {
            "湛蓝命运——这是最后的乐章。",
            "都进来吧。让我把它奏完。",
            "二十五步之内，是我的梦。"
    };

    private static final String[] LOW = {
            "还差一点……",
            "别关灯。我还没写完。"
    };

    private static final String[] CONTROLLED = {
            "放开我……",
            "我还没到终章。"
    };

    private static final String[] KILL = {
            "休止符。",
            "安静了。",
            "下一段，换个调子。"
    };

    private static final String[] HURT = {
            "……走音了。",
            "你们不懂。"
    };

    private static final String[] DEATH = {
            "梦……到这里吧。",
            "唱片，停一下。"
    };

    public HeronFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    @Override
    protected TextUtil.Lexicon lexicon() {
        return lexiconOf(
                MOMENT_IDLE, IDLE,
                MOMENT_SPAWN, SPAWN,
                MOMENT_HIT, HIT,
                MOMENT_HOOK, HOOK,
                MOMENT_DOMAIN, DOMAIN,
                MOMENT_LOW, LOW,
                MOMENT_CONTROLLED, CONTROLLED,
                MOMENT_KILL, KILL,
                MOMENT_HURT, HURT,
                MOMENT_DEATH, DEATH
        );
    }

    public boolean sayMoment(Player who, String moment) {
        return say(who, moment, null);
    }

    @Override
    protected int idleIntervalTicks() {
        return 140;
    }

    @Override
    protected int idleJitterTicks() {
        return 50;
    }

    @Override
    protected double idleChance() {
        return 0.3d;
    }

    @Override
    protected double hurtChance() {
        return 0.2d;
    }

    @Override
    protected TextUtil.Style styleFor(String moment) {
        return switch (moment == null ? "" : moment) {
            case MOMENT_DOMAIN -> TextUtil.STYLE_BURST;
            case MOMENT_HOOK, MOMENT_HIT -> TextUtil.STYLE_EMPHASIS;
            case MOMENT_CONTROLLED, MOMENT_HURT, MOMENT_LOW -> TextUtil.STYLE_GLITCH;
            default -> TextUtil.STYLE_CALM;
        };
    }

    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        if (MOMENT_DOMAIN.equals(key)) {
            return new TextUtil.Look(0xFFDDF3FF, false, true, 0xFF2E6FA8, false, true);
        }
        if (MOMENT_HOOK.equals(key) || MOMENT_HIT.equals(key)) {
            return new TextUtil.Look(0xFFCFEAFF, false, true, 0xFF7A1020, false, false);
        }
        if (MOMENT_CONTROLLED.equals(key) || MOMENT_LOW.equals(key)) {
            return new TextUtil.Look(0xFFC9D8E0, false, false, 0, true, false);
        }
        return new TextUtil.Look(0xFFE8F4FB, false, true, 0xFF3A8FD0, false, false);
    }

    @Override
    protected float scaleFor(String moment) {
        return MOMENT_DOMAIN.equals(moment) ? 1.45f : 1.02f;
    }

    /** 描述符。 */
    public static final class Specification extends FloatingTextComponent.Specification<HeronFloatingTextComponent> {

        public Specification() {
            super(Component.text("苍鹭 · 语音"),
                    List.of(
                            Component.text("执着的追梦者 / 古典乐爱好者 / 梦碎者"),
                            Component.text("中年人的低语：谈音乐时发亮，随即沉默")
                    ));
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new HeronFloatingTextComponent(id, services, this);
        }
    }
}
