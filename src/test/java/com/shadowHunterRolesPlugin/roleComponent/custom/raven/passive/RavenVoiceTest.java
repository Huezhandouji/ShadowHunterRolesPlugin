package com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive;

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
 * 「绯红渡鸦」语音库的**离线闸门**。
 *
 * <h2>它守的四条契约</h2>
 * <ol>
 *   <li><b>每个时刻都有台词</b> —— 漏一组 = 那个场合永远不说话（而且**不报错**，只是静默）；</li>
 *   <li><b>每句都放得进单段字数上限</b>（{@link TextUtil#MAX_CHARS}）——
 *       超长的句子会被工具箱截断/拆段，玩家看到的是残句；</li>
 *   <li>★ <b>需求原话："加大字号、加粗字体、使用多种风格字体"</b> ——
 *       于是有三条硬断言：<b>全部加粗</b>、<b>全部缩放 ≥ {@value RavenFloatingTextComponent#MIN_SCALE}</b>、
 *       <b>风格去重后 ≥ 10 种</b>；</li>
 *   <li><b>击杀三组互不相同</b>（普通 / 空中 / 顺手充能）—— 否则"分三组"就白做了。</li>
 * </ol>
 *
 * <h2>做法</h2>
 * 用**同包探针子类**暴露 {@code protected} 钩子（工程既有手法，不必改生产可见性）；
 * 探针构造必须传 {@code new ComponentServicesPort(null, null, null)}（组件基类拒绝 null 服务集）。
 * ★ 本组件对其它组件的读取全部走 {@code findComponent}（服务集为空时回 {@code null}）
 * ⇒ 真的能离线构造。
 */
public class RavenVoiceTest {

    /** 同包探针：把 {@code protected} 钩子转成可测的转发方法。 */
    private static final class VoiceProbe extends RavenFloatingTextComponent {

        private VoiceProbe() {
            super(RavenFloatingTextComponent.ID,
                    new ComponentServicesPort(null, null, null),
                    new RavenFloatingTextComponent.Specification());
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
        for (String moment : RavenFloatingTextComponent.ALL_MOMENTS) {
            List<String> lines = lexicon.candidates(moment);
            assertFalse("时刻「" + moment + "」没有台词 ⇒ 那个场合会静默不说话", lines.isEmpty());
        }
    }

    @Test
    public void 需求里的句子都在词库里() {
        //从需求里逐栏各抽一句，钉住"确实收进来了"
        String[] mustHave = {
                "空域干净。各小队保持通讯。",
                "别盯着我的腿看，它比你想的贵。",
                "近身？你选错了对手。",
                "别动，准星已经在你身上了。",
                "升空。视野给我。",
                "都别动，我来点名。",
                "筋斗——俯冲。",
                "目标清除。继续。",
                "空中清除。",
                "机动就绪，继续。",
                "浮空稳定，锁定中。",
                "落地——清场。",
                "还能飞。别管我。",
                "控制解除。继续。",
                "换弹中，掩护我。",
                "没有目标。继续搜索。",
                "……小队，交给你们了。"
        };
        Set<String> all = new HashSet<>(RavenFloatingTextComponent.allLines());
        for (String line : mustHave) {
            assertTrue("需求里的这句没进词库：" + line, all.contains(line));
        }
    }

    // ───────── 契约二：每句都放得进单段上限 ─────────

    @Test
    public void 每句都在单段字数上限内() {
        for (String line : RavenFloatingTextComponent.allLines()) {
            assertTrue("超长句子会被工具箱截断/拆段，玩家看到残句：" + line
                            + "（长度 " + line.length() + " > " + TextUtil.MAX_CHARS + "）",
                    line.length() <= TextUtil.MAX_CHARS);
        }
    }

    @Test
    public void 台词不为空白且没有多余空白() {
        for (String line : RavenFloatingTextComponent.allLines()) {
            assertFalse("空台词会被静默丢弃", line.isBlank());
            assertEquals("首尾不该有空白：" + line, line.strip(), line);
            assertFalse("不该含换行（一行 = 一段）：" + line, line.contains("\n"));
        }
    }

    // ───────── 契约三：加大字号 / 加粗 / 多种风格 ─────────

    @Test
    public void 全部时刻都加粗() {
        //★ 需求原话："加粗字体"
        for (String moment : RavenFloatingTextComponent.ALL_MOMENTS) {
            TextUtil.Look look = PROBE.lookProbe(moment);
            assertTrue("时刻「" + moment + "」的外观没有加粗", look.bold());
        }
    }

    @Test
    public void 全部时刻都加大了字号() {
        //★ 需求原话："加大字号"
        assertTrue("最小缩放本身必须明显大于默认的 1.0",
                RavenFloatingTextComponent.MIN_SCALE >= 1.3f);
        for (String moment : RavenFloatingTextComponent.ALL_MOMENTS) {
            float scale = PROBE.scaleProbe(moment);
            assertTrue("时刻「" + moment + "」的字号没有加大：" + scale,
                    scale >= RavenFloatingTextComponent.MIN_SCALE);
            assertTrue("字号也不能夸张到糊屏：" + scale, scale <= 2.0f);
        }
    }

    @Test
    public void 风格至少十种且各时刻有区别() {
        //★ 需求原话："使用多种风格字体"
        Set<TextUtil.Style> distinct = new HashSet<>();
        for (String moment : RavenFloatingTextComponent.ALL_MOMENTS) {
            distinct.add(PROBE.styleProbe(moment));
        }
        assertTrue("风格种类太少（" + distinct.size() + " 种）⇒ 不满足「多种风格字体」", distinct.size() >= 10);
    }

    @Test
    public void 落地冲击必须走落地砸字() {
        //只有 GROUND_SMASH / MITA_SCROLL 会走"逐字查地面"的那条路 ⇒ 落地冲击用它才对味
        assertEquals(TextUtil.Style.GROUND_SMASH, PROBE.styleProbe(RavenFloatingTextComponent.MOMENT_LANDING));
    }

    @Test
    public void 观感要能区分场合_不是一套颜色到底() {
        Set<Integer> colors = new HashSet<>();
        Set<Boolean> effects = new HashSet<>();
        for (String moment : RavenFloatingTextComponent.ALL_MOMENTS) {
            TextUtil.Look look = PROBE.lookProbe(moment);
            colors.add(look.colorArgb());
            effects.add(look.glow() || look.gradient() || look.tremble());
        }
        assertTrue("配色太少（" + colors.size() + " 种）", colors.size() >= 4);
        assertTrue("至少要有一些场合带泛光/渐变/颤抖", effects.contains(true));
        assertTrue("也要有克制的场合（不带特效）", effects.contains(false));
    }

    // ───────── 契约四：击杀三组互不相同 ─────────

    @Test
    public void 击杀三组互不相同() {
        List<String> ground = RavenFloatingTextComponent.killLines(false, false);
        List<String> air = RavenFloatingTextComponent.killLines(false, true);
        List<String> charge = RavenFloatingTextComponent.killLines(true, false);
        assertFalse("普通击杀组为空", ground.isEmpty());
        assertFalse("空中击杀组为空", air.isEmpty());
        assertFalse("顺手充能组为空", charge.isEmpty());
        assertNotEquals("空中击杀组与普通组不能是同一套", ground, air);
        assertNotEquals("顺手充能组与普通组不能是同一套", ground, charge);
        assertNotEquals("顺手充能组与空中组不能是同一套", air, charge);
    }

    @Test
    public void 充能优先于空中_充能时用充能那组() {
        //选择顺序：先看"这次击杀有没有顺手充能"，再看"是否在空中"
        assertEquals(RavenFloatingTextComponent.killLines(true, true),
                RavenFloatingTextComponent.killLines(true, false));
    }

    // ───────── 词库规模（如实反映"需求台词一句不落"） ─────────

    @Test
    public void 台词总量与需求栏位对得上() {
        int total = RavenFloatingTextComponent.allLines().size();
        assertTrue("台词太少（" + total + " 句）", total >= 70);
        assertEquals("各组不得重复同一句", total,
                new HashSet<>(RavenFloatingTextComponent.allLines()).size());
    }

    @Test
    public void 角色语音的注册id必须唯一且非空() {
        assertFalse(RavenFloatingTextComponent.ID.isBlank());
        assertNotEquals("语音组件 id 不能与技能/武器撞车",
                RavenFloatingTextComponent.ID, RavenAirMobilityPassive.ID);
    }
}
