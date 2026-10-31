package com.shadowHunterRolesPlugin.roleComponent.custom.voice;

import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★ **全员语音台词表**（2026-10-31 需求：给所有角色加入对应的语音表现内容）。
 *
 * <h2>世界观口径（本表所有台词的写作基准）</h2>
 * <ul>
 *   <li><b>影都（SHADOW）</b>：巨大的怪物，<b>少有人性，却要有人性</b>。
 *       它们记得人的名字、人的灯、人的伤，却已经不会用人的方式说话 ——
 *       台词必须短、低、带饥饿与钝痛；"人性"只以**残片**形式漏出来（一盏灯、一个名字、
 *       一次会疼），而不是煽情的整段告白。</li>
 *   <li><b>猎影人（HUNTER）</b>：生活在复杂背景下的<b>反英雄</b>。
 *       他们做的事不干净，理由却干净得刺眼；台词冷静、自嘲、带代价，
 *       不喊口号、不宣告正义，只报"账"。</li>
 * </ul>
 *
 * <h2>本表覆盖谁</h2>
 * 目标是"每个角色都有语音"。已经自带手写语音组件的 9 个角色
 * （红 / 冷识 / 抗争 / 梅妻鹤子 / 苍鹭 / 马提娜 / 艾尔伯特 / 渡鸦 / 沙耶香）保持原样；
 * 本表补齐**四个此前没有语音的角色**：
 * <ul>
 *   <li>{@code lin} 林（猎影人）—— 以血为薪的镇压者；</li>
 *   <li>{@code tek} 特克（猎影人）—— 把真理逼到枪尖上的人；</li>
 *   <li>{@code hunter} 猎手（影都）—— 会记得名字、会疼的巨大怪物；</li>
 *   <li>{@code sinThorn} 罪棘（影都）—— 以荆棘为律法、替罪受钉的巨物。</li>
 * </ul>
 * 框架示例角色 {@code selfUpdateExample} 不参与（它不是世界观角色，是组件演示）。
 *
 * <h2>怎么被用到</h2>
 * {@code RoleLoader} 在四个角色的装配末尾各注册一次
 * {@code addComponent(ScriptedVoiceComponent.ID, ...Voice())}；
 * 组件按 {@code FloatingTextComponent} 既有挂载点自动在登场 / 空闲 / 受击 / 濒死 / 击杀 / 死亡时开口。
 * 施法台词留给技能侧显式 {@code say(...)}（本表不预设时机）。
 */
public final class VoiceScripts {

    private VoiceScripts() {
    }

    /** 本表补齐的角色 id（顺序 = 注册顺序）。 */
    public static final List<String> SCRIPTED_ROLE_IDS = List.of("lin", "tek", "hunter", "sinThorn");

    // ───────── 四套角色语音（描述符）─────────

    /** 林 · 猎影人（反英雄：镇压者的账本）。 */
    public static ScriptedVoiceComponent.Specification linVoice() {
        return new ScriptedVoiceComponent.Specification(
                Component.text("林 · 语音"),
                List.of(
                        Component.text("猎影人：以血为薪的镇压者 —— 替秩序记账，也替秩序流血"),
                        Component.text("登场 / 空闲 / 受击 / 濒死 / 击杀 / 死亡台词"),
                        Component.text("语气：冷静、克制，把殉道说成流程")
                ),
                linScript());
    }

    /** 特克 · 猎影人（反英雄：把真理逼到枪尖上）。 */
    public static ScriptedVoiceComponent.Specification tekVoice() {
        return new ScriptedVoiceComponent.Specification(
                Component.text("特克 · 语音"),
                List.of(
                        Component.text("猎影人：追真理追到自己也成了证据的人"),
                        Component.text("登场 / 空闲 / 受击 / 濒死 / 击杀 / 死亡台词"),
                        Component.text("语气：冷、准、像在读一行读数")
                ),
                tekScript());
    }

    /** 猎手 · 影都（巨大怪物，少有人性，却要有人性）。 */
    public static ScriptedVoiceComponent.Specification hunterVoice() {
        return new ScriptedVoiceComponent.Specification(
                Component.text("猎手 · 语音"),
                List.of(
                        Component.text("影都：巨大怪物，少有人性，却要有人性"),
                        Component.text("登场 / 空闲 / 受击 / 濒死 / 击杀 / 死亡台词"),
                        Component.text("语气：短促、饥饿；人性只以残片漏出（名字 / 灯 / 会疼）")
                ),
                hunterScript());
    }

    /** 罪棘 · 影都（以荆棘为律法、替罪受钉的巨物）。 */
    public static ScriptedVoiceComponent.Specification sinThornVoice() {
        return new ScriptedVoiceComponent.Specification(
                Component.text("罪棘 · 语音"),
                List.of(
                        Component.text("影都：以荆棘为律法的巨物 —— 数人的罪，也替人受钉"),
                        Component.text("登场 / 空闲 / 受击 / 濒死 / 击杀 / 死亡台词"),
                        Component.text("语气：审判腔、钝而慢；罪与刺是它的骨")
                ),
                sinThornScript());
    }

    /** 按角色 id 取台词表（测试 / 调试读口；未知 id ⇒ {@code null}）。 */
    public static ScriptedVoiceComponent.Script scriptFor(String roleId) {
        if (roleId == null) {
            return null;
        }
        return switch (roleId) {
            case "lin" -> linScript();
            case "tek" -> tekScript();
            case "hunter" -> hunterScript();
            case "sinThorn" -> sinThornScript();
            default -> null;
        };
    }

    // ───────── 四套台词本体 ─────────

    /** 猎影人语气：冷白泛光、无颤抖。 */
    private static final TextUtil.Look HUNTER_CALM = TextUtil.Look.colored(0xFFC9D3E0);
    private static final TextUtil.Look HUNTER_INTENSE = TextUtil.Look.glowOf(0xFFF2F6FF).withBold();
    /** 影都语气：灰烬底色、烈色颤抖（怪物的嗓子）。 */
    private static final TextUtil.Look SHADOW_CALM = TextUtil.Look.colored(0xFFD6C6C2);
    private static final TextUtil.Look SHADOW_INTENSE = TextUtil.Look.gradientOf(0xFF8A0F14).withTremble().withBold();

    /** 空闲开口：6 秒一次 ± 1.5 秒抖动、30% 概率开口（避免话痨）。 */
    private static final int IDLE_INTERVAL_TICKS = 120;
    private static final int IDLE_JITTER_TICKS = 30;
    private static final double IDLE_CHANCE = 0.30d;
    /** 受击开口概率：25%。 */
    private static final double HURT_CHANCE = 0.25d;

    private static ScriptedVoiceComponent.Script linScript() {
        return new ScriptedVoiceComponent.Script(lines(
                FloatingTextComponent.MOMENT_SPAWN, new String[]{
                        "血还够。把名字报上来。",
                        "镇压令已签。别让我改。"
                },
                FloatingTextComponent.MOMENT_IDLE, new String[]{
                        "以血为薪，以骨为钉。",
                        "每一条命都记在册上。包括我的。",
                        "他们叫我镇压者。我只是在清点。",
                        "殉道不是誓言，是流程。",
                        "灯亮着。等我的人不会少。",
                        "干净的手，办不了脏事。"
                },
                FloatingTextComponent.MOMENT_HURT, new String[]{
                        "这点血，还算数。",
                        "疼。说明我还活着。"
                },
                FloatingTextComponent.MOMENT_LOW_HP, new String[]{
                        "账……还没平。",
                        "别急着给我收尸。"
                },
                FloatingTextComponent.MOMENT_KILL, new String[]{
                        "结案。",
                        "下一个。",
                        "名字，我记住了。"
                },
                ScriptedVoiceComponent.MOMENT_DEATH, new String[]{
                        "把我……也钉进册子里。",
                        "账平了。剩下的，交给活着的人。"
                }
        ), HUNTER_CALM, HUNTER_INTENSE, false, IDLE_INTERVAL_TICKS, IDLE_JITTER_TICKS, IDLE_CHANCE, HURT_CHANCE);
    }

    private static ScriptedVoiceComponent.Script tekScript() {
        return new ScriptedVoiceComponent.Script(lines(
                FloatingTextComponent.MOMENT_SPAWN, new String[]{
                        "真理在枪尖上。你站得太远。",
                        "校准完成。说出你的证词。"
                },
                FloatingTextComponent.MOMENT_IDLE, new String[]{
                        "命运在枪尖外。它看得见我，我看不见它。",
                        "每一道伤口都是一行证词。",
                        "我追的东西，从不回头。",
                        "别信我。信这把枪。",
                        "真理从不等我。所以我从不睡。",
                        "问到最后，问题比答案值钱。"
                },
                FloatingTextComponent.MOMENT_HURT, new String[]{
                        "误差。可以接受。",
                        "疼，是最准的一行读数。"
                },
                FloatingTextComponent.MOMENT_LOW_HP, new String[]{
                        "还没到结论。",
                        "这一枪，不能偏。"
                },
                FloatingTextComponent.MOMENT_KILL, new String[]{
                        "供词结束。",
                        "你只是过程。",
                        "下一具证据。"
                },
                ScriptedVoiceComponent.MOMENT_DEATH, new String[]{
                        "原来……我也在枪尖之外。",
                        "把枪留下。它比我会说话。"
                }
        ), HUNTER_CALM, HUNTER_INTENSE, false, IDLE_INTERVAL_TICKS, IDLE_JITTER_TICKS, IDLE_CHANCE, HURT_CHANCE);
    }

    private static ScriptedVoiceComponent.Script hunterScript() {
        return new ScriptedVoiceComponent.Script(lines(
                FloatingTextComponent.MOMENT_SPAWN, new String[]{
                        "……气味。近了。",
                        "这片林子里只有两种东西：我，和猎物。"
                },
                FloatingTextComponent.MOMENT_IDLE, new String[]{
                        "……饿。",
                        "爪子又长了。",
                        "我见过镜子。里面的东西会眨眼。",
                        "我记得一个名字。只是名字。",
                        "影子比我本人更像人。",
                        "树在长。我也在长。",
                        "月亮在的时候，我不看天。",
                        "别唱。我会想起什么。"
                },
                FloatingTextComponent.MOMENT_HURT, new String[]{
                        "疼。原来还会疼。",
                        "别碰……那个印记。"
                },
                FloatingTextComponent.MOMENT_LOW_HP, new String[]{
                        "别让我……变回去。",
                        "那盏灯……还亮着吗。"
                },
                FloatingTextComponent.MOMENT_KILL, new String[]{
                        "……不够。",
                        "下一个。",
                        "跑得再快一点。"
                },
                ScriptedVoiceComponent.MOMENT_DEATH, new String[]{
                        "我……曾经叫什么？",
                        "把灯……留给它。"
                }
        ), SHADOW_CALM, SHADOW_INTENSE, true, IDLE_INTERVAL_TICKS, IDLE_JITTER_TICKS, IDLE_CHANCE, HURT_CHANCE);
    }

    private static ScriptedVoiceComponent.Script sinThornScript() {
        return new ScriptedVoiceComponent.Script(lines(
                FloatingTextComponent.MOMENT_SPAWN, new String[]{
                        "罪孽负身。荆棘为证。",
                        "抬起头。让我数你的罪。"
                },
                FloatingTextComponent.MOMENT_IDLE, new String[]{
                        "律法不是人写的，是人怕出来的。",
                        "荆棘会记住每一道伤。",
                        "我也被钉过。很久。",
                        "我记得第一根刺长出来的样子。",
                        "罪不会消失，只会长出刺。",
                        "别替我拔刺。它们是我的骨。",
                        "城里的钟响了。又有人要赎罪。",
                        "我不数眼泪。只数伤口。"
                },
                FloatingTextComponent.MOMENT_HURT, new String[]{
                        "我的血也是黑的。",
                        "刺……扎回来了。"
                },
                FloatingTextComponent.MOMENT_LOW_HP, new String[]{
                        "审判……还没结束。",
                        "这些刺，还站得住。"
                },
                FloatingTextComponent.MOMENT_KILL, new String[]{
                        "罪已清偿。",
                        "下一根荆棘。",
                        "别跪。站着受。"
                },
                ScriptedVoiceComponent.MOMENT_DEATH, new String[]{
                        "把我和罪……一起钉住。",
                        "审判到此。荆棘会继续长。"
                }
        ), SHADOW_CALM, SHADOW_INTENSE, true, IDLE_INTERVAL_TICKS, IDLE_JITTER_TICKS, IDLE_CHANCE, HURT_CHANCE);
    }

    // ───────── 内部工具 ─────────

    /** 把「时刻 → 台词数组」的散表拼成不可变表。 */
    private static Map<String, List<String>> lines(Object... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            List<String> list = new ArrayList<>();
            Object raw = pairs[i + 1];
            if (raw instanceof String[] array) {
                list.addAll(List.of(array));
            } else if (raw instanceof List<?> values) {
                for (Object value : values) {
                    if (value != null) {
                        list.add(String.valueOf(value));
                    }
                }
            }
            map.put(String.valueOf(pairs[i]), List.copyOf(list));
        }
        return Map.copyOf(map);
    }
}
