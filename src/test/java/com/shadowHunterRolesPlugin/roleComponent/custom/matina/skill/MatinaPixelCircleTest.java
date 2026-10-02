package com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill;

import com.shadowHunterRolesPlugin.roleComponent.custom.matina.MatinaRageVfx;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 马提娜大招「神罚」**脚下像素画魔法阵**的离线单测（数据来自外部 pixelart 粒子画数据包）。
 *
 * <h2>为什么这些断言重要</h2>
 * 这张画的**两轴像素间距并不相同**（x 是 0.1 格、y 是 0.2 格）——
 * 如果归一化时"每轴各自缩放到 1.0"，画会被**横向拉伸一倍**（实测宽/高 0.984 → 1.0/1.0 的假象，
 * 形状却歪了）。因此本测试把"**两轴同除以最大边**"这条口径钉死：
 * 归一化后 {@code x 跨度} 必须仍 ≈ 0.984 而不是 1.0。
 *
 * <h2>边界（如实申报）</h2>
 * 覆盖的是"资源内容 + 解析 + 归一化"这条纯链路；**不**覆盖真实的粒子落点与观感
 * （`spawnParticle` 要服务端，见 {@link MatinaRageVfx#pixelCircle} 的坐标变换说明）。
 */
public class MatinaPixelCircleTest {

    /** 打包在 jar 里的资源（与组件同源，从测试 classpath 读）。 */
    private static final String RESOURCE = "/matina_magic_circle.txt";

    /** 源数据包解析后的点数（三段 mcfunction 去重落格的结果，4554）。 */
    private static final int EXPECTED_POINTS = 4554;

    private static InputStream resource() {
        InputStream in = MatinaPixelCircleTest.class.getResourceAsStream(RESOURCE);
        assertNotNull("资源" + RESOURCE + "必须随 jar/测试类路径提供", in);
        return in;
    }

    // ───────── ① 真实资源：内容与形状 ─────────

    @Test
    public void 资源可解析且点数与源数据包一致() throws Exception {
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(resource());
        assertNotNull("资源必须能解析成像素画", art);
        assertEquals("点数必须等于数据包去重落格后的 4554", EXPECTED_POINTS, art.size());
    }

    @Test
    public void 归一化后局部坐标必须落在负零点五到正零点五() throws Exception {
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(resource());
        assertNotNull(art);
        for (int i = 0; i < art.size(); i++) {
            float x = art.localX()[i];
            float y = art.localY()[i];
            assertTrue("第 " + i + " 点 x 越界: " + x, x >= -0.5f && x <= 0.5f);
            assertTrue("第 " + i + " 点 y 越界: " + y, y >= -0.5f && y <= 0.5f);
        }
    }

    /**
     * ★★ **核心断言：长宽比必须与源画一致**（两轴同除以最大边，而不是各自归一）。
     * <p>源画的实际方块范围是 x 24.6 格、y 25.0 格 ⇒ 两轴同除以 25.0 后：
     * x 跨度 = 0.984、y 跨度 = 1.0。
     * <p>若有人把它改成"每轴各自归一"，两者都会变成 1.0 ⇒ 本断言立刻变红。
     */
    @Test
    public void 两轴必须同比例归一化_长宽比不能被拉歪() throws Exception {
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(resource());
        assertNotNull(art);
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < art.size(); i++) {
            minX = Math.min(minX, art.localX()[i]);
            maxX = Math.max(maxX, art.localX()[i]);
            minY = Math.min(minY, art.localY()[i]);
            maxY = Math.max(maxY, art.localY()[i]);
        }
        float spanX = maxX - minX;
        float spanY = maxY - minY;

        //y 是"最大边"（250 个 0.1 格单位）= 1.0；x = 246/250 = 0.984
        assertEquals("y 跨度应恰为 1.0（它是最大边）", 1.0f, spanY, 0.005f);
        assertEquals("x 跨度应是 246/250 = 0.984（★ 不是 1.0 —— 那说明被各自归一再拉伸了）",
                0.984f, spanX, 0.005f);
        assertTrue("★ 两轴跨度必须不同（源画本就是 24.6×25.1 的近方形，不是正方形）",
                Math.abs(spanX - spanY) > 0.01f);
    }

    @Test
    public void 每个点都要有颜色() throws Exception {
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(resource());
        assertNotNull(art);
        for (int i = 0; i < art.size(); i++) {
            assertNotNull("第 " + i + " 点缺颜色", art.colors()[i]);
        }
    }

    // ───────── ② 解析器：边界与容错（合成输入，不依赖真实资源）─────────

    /** 两轴**不同**范围时必须保持比例：4×2 的合成画 ⇒ x 跨度 1.0、y 跨度 0.5。 */
    @Test
    public void 合成小画_两轴比例按最大边换算() throws Exception {
        String text = String.join("\n",
                "0 0 FF0000",
                "10 0 00FF00",
                "20 0 0000FF",
                "30 0 FFFFFF",
                "0 10 123456",
                "30 10 ABCDEF") + "\n";
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(stream(text));
        assertNotNull(art);
        assertEquals(6, art.size());
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < art.size(); i++) {
            maxX = Math.max(maxX, art.localX()[i]);
            maxY = Math.max(maxY, art.localY()[i]);
        }
        //最大边 = 30（x）⇒ x 归一化到 +0.5、y（10/30）归一化到 1/6 ≈ 0.1667
        assertEquals("x 最大边应归一到 +0.5", 0.5f, maxX, 1.0E-4f);
        assertEquals("y 应按同一个比例（10/30）缩小 ⇒ 不被拉伸",
                10f / 30f / 2f, maxY, 1.0E-4f);
    }

    @Test
    public void 注释与空行被忽略_坏行被跳过() throws Exception {
        String text = String.join("\n",
                "# 这是注释",
                "",
                "   ",
                "1 2 FF0000",
                "这是坏行",
                "3 4",
                "5 6 00FF00",
                "# 尾注释") + "\n";
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(stream(text));
        assertNotNull(art);
        assertEquals("只有两行是有效点", 2, art.size());
    }

    @Test
    public void 空资源回null而不是空画() throws Exception {
        assertNull("全注释/空 ⇒ null（调用方据此静默不画）",
                MatinaJudgmentSkill.parsePixelArt(stream("# 只有注释\n\n")));
        assertNull("完全空 ⇒ null", MatinaJudgmentSkill.parsePixelArt(stream("")));
    }

    @Test
    public void 单点画不炸_归一化到零点() throws Exception {
        MatinaRageVfx.PixelArt art = MatinaJudgmentSkill.parsePixelArt(stream("7 7 FF0000\n"));
        assertNotNull(art);
        assertEquals(1, art.size());
        assertEquals("只有一个点 ⇒ 它就落在圆心", 0.0f, art.localX()[0], 1.0E-6f);
        assertEquals(0.0f, art.localY()[0], 1.0E-6f);
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
