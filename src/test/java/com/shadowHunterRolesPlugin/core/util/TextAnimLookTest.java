package com.shadowHunterRolesPlugin.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link TextUtil.Look}（外观：颜色 / 渐变 / 泛光 / 颤抖）与其背后的
 * 纯函数（{@link TextUtil.Anim#angryRedAt}、{@link TextUtil.Anim#shadeToward}、
 * {@link TextUtil.Anim#argb}、{@link TextUtil.Anim#trembleOffset}）的离线穷举单测。
 *
 * <p>为什么值得单测：这几条全是**无报错**的行为——
 * 写错了只会"颜色不对 / 不抖 / 不泛光"，运行期看不出来，
 * 只能靠测试把口径钉死（尤其是渐变的方向与颤抖的确定性）。
 *
 * <p>全部纯函数 / 纯数据 ⇒ 不碰 Bukkit。
 */
public class TextAnimLookTest {

    private static final double EPS = 1e-9;

    // ───────── Look 预设 ─────────

    @Test
    public void plainLookIsAllOff() {
        TextUtil.Look p = TextUtil.Look.PLAIN;
        assertEquals(0, p.colorArgb());
        assertFalse(p.gradient());
        assertFalse(p.glow());
        assertFalse(p.tremble());
        assertFalse("默认不加粗", p.bold());
    }

    @Test
    public void angryLookTrembles() {
        assertTrue(TextUtil.Look.ANGRY.tremble());
        assertFalse(TextUtil.Look.ANGRY.glow());
        assertFalse(TextUtil.Look.ANGRY.gradient());
        assertFalse("ANGRY 本身不加粗（加粗版是 ANGRY_BOLD）", TextUtil.Look.ANGRY.bold());
    }

    @Test
    public void holyLookGlows() {
        assertTrue(TextUtil.Look.HOLY.glow());
        assertFalse(TextUtil.Look.HOLY.tremble());
        assertFalse(TextUtil.Look.HOLY.bold());
    }

    // ───────── ★ 加粗（bold） ─────────

    @Test
    public void angryBoldPresetTremblesAndBolds() {
        TextUtil.Look look = TextUtil.Look.ANGRY_BOLD;
        assertTrue("愤怒加强版必须颤抖", look.tremble());
        assertTrue("愤怒加强版必须加粗", look.bold());
        assertFalse(look.glow());
    }

    @Test
    public void holyBoldPresetGlowsAndBolds() {
        TextUtil.Look look = TextUtil.Look.HOLY_BOLD;
        assertTrue(look.glow());
        assertTrue(look.bold());
        assertFalse(look.tremble());
    }

    @Test
    public void withBoldIsAdditiveAndImmutable() {
        TextUtil.Look base = TextUtil.Look.glowOf(0xFFFFFF);
        TextUtil.Look derived = base.withBold();
        assertFalse("record 必须不可变，派生不该改原对象", base.bold());
        assertTrue(derived.bold());
        assertTrue("withBold 不该丢掉原有的泛光", derived.glow());
        assertEquals(0xFFFFFF, derived.colorArgb());
    }

    @Test
    public void boldSurvivesOtherChainSteps() {
        TextUtil.Look look = TextUtil.Look.colored(0x112233).withBold().withTremble().withGradient();
        assertTrue("加粗必须能在链式后仍保留", look.bold());
        assertTrue(look.tremble());
        assertTrue(look.gradient());
    }

    @Test
    public void fiveArgCompatConstructorDefaultsToNotBold() {
        // ★ 兼容构造：旧 5 参形态（升级前写的代码）必须仍然编译且 bold=false
        TextUtil.Look legacy = new TextUtil.Look(0x223344, true, false, 0, true);
        assertEquals(0x223344, legacy.colorArgb());
        assertTrue(legacy.gradient());
        assertTrue(legacy.tremble());
        assertFalse("旧形态默认不加粗", legacy.bold());
    }

    // ───────── 工厂 / 链式 ─────────

    @Test
    public void coloredFactorySetsColorOnly() {
        TextUtil.Look look = TextUtil.Look.colored(0x112233);
        assertEquals(0x112233, look.colorArgb());
        assertFalse(look.gradient());
        assertFalse(look.glow());
        assertFalse(look.tremble());
    }

    @Test
    public void glowingFactoryTurnsOnGlowAndKeepsColor() {
        TextUtil.Look look = TextUtil.Look.glowing(0xABCDEF);
        assertEquals(0xABCDEF, look.colorArgb());
        assertTrue(look.glow());
        assertFalse(look.gradient());
    }

    @Test
    public void glowOfIsSameAsGlowing() {
        assertEquals(TextUtil.Look.glowing(0x010203), TextUtil.Look.glowOf(0x010203));
    }

    @Test
    public void gradientOfIsSameAsGradient() {
        assertEquals(TextUtil.Look.gradient(0x010203), TextUtil.Look.gradientOf(0x010203));
    }

    @Test
    public void gradientFactoryTurnsOnGradientOnly() {
        TextUtil.Look look = TextUtil.Look.gradient(0x445566);
        assertTrue(look.gradient());
        assertFalse(look.glow());
        assertFalse(look.tremble());
    }

    @Test
    public void chainKeepsPreviousFlags() {
        TextUtil.Look look = TextUtil.Look.glowOf(0xFFFFFF).withTremble().withGradient();
        assertTrue(look.glow());
        assertTrue(look.tremble());
        assertTrue(look.gradient());
        assertEquals(0xFFFFFF, look.colorArgb());
    }

    @Test
    public void chainDoesNotMutateOriginal() {
        TextUtil.Look base = TextUtil.Look.glowOf(0xFFFFFF);
        TextUtil.Look derived = base.withTremble();
        assertFalse("record 必须不可变，派生不该改原对象", base.tremble());
        assertTrue(derived.tremble());
    }

    @Test
    public void withColorKeepsFlags() {
        TextUtil.Look look = TextUtil.Look.ANGRY.withColor(0x00FF00);
        assertEquals(0x00FF00, look.colorArgb());
        assertTrue(look.tremble());
    }

    // ───────── effectiveGlowArgb ─────────

    @Test
    public void effectiveGlowFallsBackToTextColor() {
        assertEquals(0x123456, TextUtil.Look.glowOf(0x123456).effectiveGlowArgb());
    }

    @Test
    public void effectiveGlowPrefersExplicitGlowColor() {
        TextUtil.Look look = new TextUtil.Look(0x111111, false, true, 0x999999, false);
        assertEquals(0x999999, look.effectiveGlowArgb());
    }

    // ───────── argb 打包 ─────────

    @Test
    public void argbPacksChannels() {
        assertEquals(0xFFFF0000, TextUtil.Anim.argb(255, 255, 0, 0));
        assertEquals(0x00000000, TextUtil.Anim.argb(0, 0, 0, 0));
        assertEquals(0x80FF8040, TextUtil.Anim.argb(128, 255, 128, 64));
    }

    @Test
    public void argbMasksOutOfRangeChannels() {
        assertEquals(TextUtil.Anim.argb(255, 255, 255, 255), TextUtil.Anim.argb(511, 511, 511, 511));
    }

    // ───────── 渐变红（愤怒） ─────────

    @Test
    public void angryRedIsAlwaysOpaqueAndRedDominant() {
        for (int i = 0; i < 12; i++) {
            int c = TextUtil.Anim.angryRedAt(i, 12);
            int a = (c >>> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            assertEquals("alpha 必须拉满", 255, a);
            assertTrue("红通道必须为最大分量", r > g && r > b);
        }
    }

    @Test
    public void angryRedDarkensAlongTheRow() {
        int first = TextUtil.Anim.angryRedAt(0, 10);
        int last = TextUtil.Anim.angryRedAt(9, 10);
        assertTrue("后面的字应更暗", redOf(last) < redOf(first));
        assertTrue("后面的字应更暗", greenOf(last) < greenOf(first));
    }

    @Test
    public void angryRedSingleCharIsBrightest() {
        assertEquals(TextUtil.Anim.angryRedAt(0, 1), TextUtil.Anim.angryRedAt(0, 0));
    }

    @Test
    public void angryRedClampsOutOfRangeIndex() {
        assertEquals(TextUtil.Anim.angryRedAt(0, 10), TextUtil.Anim.angryRedAt(-3, 10));
        assertEquals(TextUtil.Anim.angryRedAt(9, 10), TextUtil.Anim.angryRedAt(99, 10));
    }

    // ───────── shadeToward（通用渐变） ─────────

    @Test
    public void shadeTowardZeroDarkenKeepsColor() {
        assertEquals(TextUtil.Anim.argb(255, 200, 100, 50),
                TextUtil.Anim.shadeToward(7, 10, 0xC86432, 0d));
    }

    @Test
    public void shadeTowardFullDarkenGoesBlackAtEnd() {
        assertEquals(TextUtil.Anim.argb(255, 0, 0, 0),
                TextUtil.Anim.shadeToward(9, 10, 0xC86432, 1d));
    }

    @Test
    public void shadeTowardMonotonicAcrossRow() {
        int prev = Integer.MAX_VALUE;
        for (int i = 0; i < 10; i++) {
            int c = TextUtil.Anim.shadeToward(i, 10, 0xFFFFFF, 0.8d);
            int lum = redOf(c) + greenOf(c) + blueOf(c);
            assertTrue("亮度应沿行单调不增", lum <= prev);
            prev = lum;
        }
    }

    @Test
    public void shadeTowardSingleCharUnchanged() {
        int c = TextUtil.Anim.shadeToward(0, 1, 0x804020, 1d);
        assertEquals(TextUtil.Anim.argb(255, 0x80, 0x40, 0x20), c);
    }

    // ───────── 颤抖 ─────────

    @Test
    public void trembleZeroAmplitudeIsStill() {
        assertEquals(0d, TextUtil.Anim.trembleOffset(5, 0, 42L, 0d), EPS);
        assertEquals(0d, TextUtil.Anim.trembleOffset(5, 1, 42L, -1d), EPS);
    }

    @Test
    public void trembleStaysWithinAmplitude() {
        double amp = 0.05d;
        for (int f = 0; f < 200; f++) {
            for (int axis = 0; axis < 3; axis++) {
                double off = TextUtil.Anim.trembleOffset(f, axis, 12345L, amp);
                assertTrue("偏移必须在 ±amp 内，实际 = " + off,
                        off >= -amp - EPS && off <= amp + EPS);
            }
        }
    }

    @Test
    public void trembleDiffersAcrossAxes() {
        // 同一帧的三个轴不该同值（否则字只会沿对角线直线晃）
        double x = TextUtil.Anim.trembleOffset(4, 0, 7L, 0.1d);
        double y = TextUtil.Anim.trembleOffset(4, 1, 7L, 0.1d);
        double z = TextUtil.Anim.trembleOffset(4, 2, 7L, 0.1d);
        assertFalse(x == y && y == z);
    }

    @Test
    public void trembleIsDeterministic() {
        assertEquals(TextUtil.Anim.trembleOffset(9, 1, 777L, 0.03d),
                TextUtil.Anim.trembleOffset(9, 1, 777L, 0.03d), EPS);
    }

    @Test
    public void trembleChangesOverFrames() {
        // 至少要动起来：连续若干帧里不该所有值都一样
        double first = TextUtil.Anim.trembleOffset(0, 0, 99L, 0.04d);
        boolean moved = false;
        for (int f = 1; f < 30; f++) {
            if (Math.abs(TextUtil.Anim.trembleOffset(f, 0, 99L, 0.04d) - first) > EPS) {
                moved = true;
                break;
            }
        }
        assertTrue("颤抖必须逐帧变化", moved);
    }

    // ───────── 小工具 ─────────

    private static int redOf(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int greenOf(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blueOf(int argb) {
        return argb & 0xFF;
    }
}
