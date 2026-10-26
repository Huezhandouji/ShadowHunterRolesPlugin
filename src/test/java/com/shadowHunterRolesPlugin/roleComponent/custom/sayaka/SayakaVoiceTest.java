package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「芹泽沙耶香」语音库的**离线闸门**。
 *
 * <h2>它守的四条契约</h2>
 * <ol>
 *   <li><b>每个时刻都有台词</b> —— 漏一组 = 那个场合永远不说话（而且**不报错**，只是静默）；</li>
 *   <li><b>每句都放得进单段字数上限</b>（{@link TextUtil#MAX_CHARS} = 24）——
 *       超长的句子会被工具箱截断/拆段，玩家看到的是残句
 *       （注意：需求原句有一句 30 字，已按此上限**收编**为 22 字）；</li>
 *   <li><b>全部加粗、字号 ≥ {@value SayakaFloatingTextComponent#MIN_SCALE}、风格去重后 ≥ 10 种</b>；</li>
 *   <li><b>大招三段「一。二。三。」是三个不同时刻、三组不同台词</b> —— 否则"数拍"就白做了。</li>
 * </ol>
 *
 * <h2>做法</h2>
 * 用**同包探针子类**暴露 {@code protected} 钩子（工程既有手法，不必改生产可见性）；
 * 探针构造必须传 {@code new ComponentServicesPort(null, null, null)}（组件基类拒绝 null 服务集）。
 * ★ 本组件对其它组件的读取全部走 {@code findComponent}（服务集为空时回 {@code null}）
 * ⇒ 真的能离线构造。
 */
public class SayakaVoiceTest {

    /** 同包探针：把 {@code protected} 钩子转成可测的转发方法。 */
    private static final class VoiceProbe extends SayakaFloatingTextComponent {

        private VoiceProbe() {
            super(SayakaFloatingTextComponent.ID,
                    new ComponentServicesPort(null, null, null),
                    new SayakaFloatingTextComponent.Specification());
        }

        private TextUtil.Lexicon lexiconProbe() {
            return lexicon();
        }

        private TextUtil.Style styleProbe(String moment) {
            return styleFor(moment);
        }

        private TextUtil.Look lookProbe(String moment) {
            return lookFor(moment);
        }

        private float scaleProbe(String moment) {
            return scaleFor(moment);
        }
    }

    private static final VoiceProbe PROBE = new VoiceProbe();

    // ───────── 契约一：每个时刻都有台词 ─────────

    @Test
    public void 每个时刻都有台词() {
        TextUtil.Lexicon lexicon = PROBE.lexiconProbe();
        for (String moment : SayakaFloatingTextComponent.ALL_MOMENTS) {
            List<String> lines = lexicon.candidates(moment);
            assertFalse("时刻「" + moment + "」没有台词 ⇒ 那个场合会静默不说话", lines.isEmpty());
        }
    }

    @Test
    public void 需求里的句子都在词库里() {
        //从需求里逐栏各抽一句，钉住"确实收进来了"
        String[] mustHave = {
                "今天天气不错。",
                "影尘的味道……像铁锈，又像雨。挺奇怪的。",
                "……再等等。快好了。",
                "中！",
                "我很快的。",
                "失衡了。趁现在。",
                "撑住这片区域。",
                "还有呢。",
                "收尾。",
                "全部让开。",
                "别小瞧我啊。",
                "一。",
                "二。",
                "三。",
                "搞定。下一个。",
                "就是现在！",
                "等我一下。缓一缓就好。",
                "回来了。",
                "我还在。",
                "对不起。",
                "走好。",
                "还行……死不了。"
        };
        Set<String> all = new HashSet<>(SayakaFloatingTextComponent.allLines());
        for (String line : mustHave) {
            assertTrue("需求里的这句没进词库：" + line, all.contains(line));
        }
    }

    // ───────── 契约二：每句都放得进单段上限 ─────────

    @Test
    public void 每句都在单段字数上限内() {
        for (String line : SayakaFloatingTextComponent.allLines()) {
            assertTrue("超长句子会被工具箱截断/拆段，玩家看到残句：" + line
                            + "（长度 " + line.length() + " > " + TextUtil.MAX_CHARS + "）",
                    line.length() <= TextUtil.MAX_CHARS);
        }
    }

    @Test
    public void 台词不为空白且没有多余空白() {
        for (String line : SayakaFloatingTextComponent.allLines()) {
            assertFalse("空台词会被静默丢弃", line.isBlank());
            assertEquals("首尾不该有空白：" + line, line.strip(), line);
            assertFalse("不该含换行（一行 = 一段）：" + line, line.contains("\n"));
        }
    }

    // ───────── 契约三：加粗 / 字号 / 多种风格 ─────────

    @Test
    public void 全部时刻都加粗() {
        for (String moment : SayakaFloatingTextComponent.ALL_MOMENTS) {
            TextUtil.Look look = PROBE.lookProbe(moment);
            assertTrue("时刻「" + moment + "」的外观没有加粗", look.bold());
        }
    }

    @Test
    public void 全部时刻都加大了字号() {
        assertTrue("最小缩放本身必须大于默认的 1.0",
                SayakaFloatingTextComponent.MIN_SCALE >= 1.25f);
        for (String moment : SayakaFloatingTextComponent.ALL_MOMENTS) {
            float scale = PROBE.scaleProbe(moment);
            assertTrue("时刻「" + moment + "」的字号没有加大：" + scale,
                    scale >= SayakaFloatingTextComponent.MIN_SCALE);
            assertTrue("字号也不能夸张到糊屏：" + scale, scale <= 2.0f);
        }
    }

    @Test
    public void 风格至少十种且各时刻有区别() {
        Set<TextUtil.Style> distinct = new HashSet<>();
        for (String moment : SayakaFloatingTextComponent.ALL_MOMENTS) {
            distinct.add(PROBE.styleProbe(moment));
        }
        assertTrue("风格种类太少（" + distinct.size() + " 种）⇒ 不满足「多种风格」", distinct.size() >= 10);
    }

    @Test
    public void 观感要能区分场合_不是一套颜色到底() {
        Set<Integer> colors = new HashSet<>();
        Set<Boolean> effects = new HashSet<>();
        for (String moment : SayakaFloatingTextComponent.ALL_MOMENTS) {
            TextUtil.Look look = PROBE.lookProbe(moment);
            colors.add(look.colorArgb());
            effects.add(look.glow() || look.gradient() || look.tremble());
        }
        assertTrue("配色太少（" + colors.size() + " 种）", colors.size() >= 4);
        assertTrue("至少要有一些场合带泛光/渐变/颤抖", effects.contains(true));
        assertTrue("也要有克制的场合（不带特效）", effects.contains(false));
    }

    // ───────── 契约四：大招三段「一。二。三。」互不相同 ─────────

    @Test
    public void 大招三段数拍三组互不相同() {
        List<String> one = PROBE.lexiconProbe().candidates(SayakaFloatingTextComponent.MOMENT_STORM_STAGE_1);
        List<String> two = PROBE.lexiconProbe().candidates(SayakaFloatingTextComponent.MOMENT_STORM_STAGE_2);
        List<String> three = PROBE.lexiconProbe().candidates(SayakaFloatingTextComponent.MOMENT_STORM_STAGE_3);
        assertFalse("第一段组为空", one.isEmpty());
        assertFalse("第二段组为空", two.isEmpty());
        assertFalse("第三段组为空", three.isEmpty());
        assertNotEquals("一 / 二 两段不能是同一套", one, two);
        assertNotEquals("二 / 三 两段不能是同一套", two, three);
        assertNotEquals("一 / 三 两段不能是同一套", one, three);
    }

    @Test
    public void 连斩三段各有独立时刻() {
        // 首斩 / 刷新 / 收尾是三组不同的时刻常量（三个不同事件 ⇒ 不会同时触发抢节流）
        assertNotEquals(SayakaFloatingTextComponent.MOMENT_CHAIN_FIRST,
                SayakaFloatingTextComponent.MOMENT_CHAIN_REFRESH);
        assertNotEquals(SayakaFloatingTextComponent.MOMENT_CHAIN_REFRESH,
                SayakaFloatingTextComponent.MOMENT_CHAIN_FINAL);
    }

    // ───────── 词库规模（如实反映"需求台词一句不落"） ─────────

    @Test
    public void 台词总量与需求栏位对得上() {
        int total = SayakaFloatingTextComponent.allLines().size();
        assertTrue("台词太少（" + total + " 句）", total >= 90);
        assertEquals("各组不得重复同一句", total,
                new HashSet<>(SayakaFloatingTextComponent.allLines()).size());
    }

    @Test
    public void 角色语音的注册id必须唯一且非空() {
        assertFalse(SayakaFloatingTextComponent.ID.isBlank());
        assertNotEquals("语音组件 id 不能与技能/武器撞车",
                SayakaFloatingTextComponent.ID, SayakaBladeMainWeapon.ID);
    }
}
