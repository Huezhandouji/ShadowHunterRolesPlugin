package com.shadowHunterRolesPlugin.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link TextUtil.Anim} 的离线穷举单测（纯函数 ⇒ 不碰 Bukkit）。
 *
 * <p>覆盖：时间模型（间隔 / 总长 / 错峰起点）、插值曲线边界（0 与 1 的收敛）、
 * 变换分量的端点与轴一致性、clamp 与确定性随机。
 */
public class TextAnimMathTest {

    // ───────── 时间模型 ─────────

    @Test
    public void staggerTicksMatchesDatapackFormula() {
        // 数据包：T = floor(3 * 100 / speed)，下限 1
        assertEquals(3, TextUtil.Anim.staggerTicks(100));  // 3*100/100 = 3
        assertEquals(6, TextUtil.Anim.staggerTicks(50));   // 3*100/50 = 6
        assertEquals(2, TextUtil.Anim.staggerTicks(150));  // floor(300/150) = 2
        assertEquals(1, TextUtil.Anim.staggerTicks(300));  // 300/300 = 1
    }

    @Test
    public void staggerTicksNeverBelowOne() {
        assertEquals(1, TextUtil.Anim.staggerTicks(Integer.MAX_VALUE));
        assertEquals(1, TextUtil.Anim.staggerTicks(1000000));
    }

    @Test
    public void staggerTicksNonPositiveFallsBackToBase() {
        // speed <= 0 ⇒ 走默认 100 ⇒ 3 刻
        assertEquals(3, TextUtil.Anim.staggerTicks(0));
        assertEquals(3, TextUtil.Anim.staggerTicks(-50));
    }

    @Test
    public void totalTicksAccountsForAllPhases() {
        // 4 字，speed=100（步 3），in=8，stay=55，有 out： (4-1)*3 + 8 + 55 + 15 = 87
        assertEquals(87, TextUtil.Anim.totalTicks(4, 100, 55, 8, true));
        // 无 out： (4-1)*3 + 8 + 55 + 0 = 72
        assertEquals(72, TextUtil.Anim.totalTicks(4, 100, 55, 8, false));
    }

    @Test
    public void totalTicksGuardsDegenerateInput() {
        // 字数 <= 0 ⇒ 按 1 算： (1-1)*3 + 8 + 55 + 15 = 78
        assertEquals(78, TextUtil.Anim.totalTicks(0, 100, 55, 8, true));
        // stay < 0 ⇒ 默认 55
        assertEquals(78, TextUtil.Anim.totalTicks(0, 100, -1, 8, true));
    }

    @Test
    public void startTickSequentialIsIndexTimesStep() {
        assertEquals(0, TextUtil.Anim.startTickOf(0, 5, TextUtil.Style.POP_SCALE, 100, 0));
        assertEquals(3, TextUtil.Anim.startTickOf(1, 5, TextUtil.Style.POP_SCALE, 100, 0));
        assertEquals(6, TextUtil.Anim.startTickOf(2, 5, TextUtil.Style.POP_SCALE, 100, 0));
    }

    @Test
    public void startTickCenterStaggeredIsSymmetric() {
        // 5 字：中心在下标 2（距离 0），两端下标 0/4（距离 2）
        assertEquals(0, TextUtil.Anim.startTickOf(2, 5, TextUtil.Style.CENTER_IN_CENTER_OUT, 100, 0));
        int left = TextUtil.Anim.startTickOf(0, 5, TextUtil.Style.CENTER_IN_CENTER_OUT, 100, 0);
        int right = TextUtil.Anim.startTickOf(4, 5, TextUtil.Style.CENTER_IN_CENTER_OUT, 100, 0);
        assertEquals(left, right); // 对称
        assertTrue(left > 0);      // 两端晚于中心
    }

    @Test
    public void startTickRandomIsDeterministic() {
        TextUtil.Style style = TextUtil.Style.RANDOM_FADE_IN_RANDOM_OUT;
        long seed = 42L;
        // 同 seed 同输入 ⇒ 恒定输出（可复现）
        int a = TextUtil.Anim.startTickOf(3, 10, style, 100, seed);
        int b = TextUtil.Anim.startTickOf(3, 10, style, 100, seed);
        assertEquals(a, b);
        // 结果落在 [0, 9*step) 区间内
        int step = TextUtil.Anim.staggerTicks(100);
        assertTrue(a >= 0 && a < 10 * step);
    }

    // ───────── 插值曲线 ─────────

    @Test
    public void inProgressConvergesToEnds() {
        assertEquals(0d, TextUtil.Anim.inProgress(0, 8), 1e-9);
        assertEquals(1d, TextUtil.Anim.inProgress(7, 8), 1e-9);
        assertEquals(1d, TextUtil.Anim.inProgress(8, 8), 1e-9); // 越界夹到 1
    }

    @Test
    public void inProgressDegenerateFrames() {
        assertEquals(1d, TextUtil.Anim.inProgress(0, 1), 1e-9); // frames<=1 ⇒ 恒 1
    }

    @Test
    public void outProgressConvergesToEnds() {
        assertEquals(1d, TextUtil.Anim.outProgress(0, 15), 1e-9);
        assertEquals(0d, TextUtil.Anim.outProgress(15, 15), 1e-9);
        assertEquals(0d, TextUtil.Anim.outProgress(20, 15), 1e-9);
    }

    @Test
    public void centerWeightEndsAndMiddle() {
        assertEquals(0d, TextUtil.Anim.centerWeight(2, 5), 1e-9); // 中心 = 0
        assertEquals(1d, TextUtil.Anim.centerWeight(0, 5), 1e-9); // 端点 = 1
        assertEquals(1d, TextUtil.Anim.centerWeight(4, 5), 1e-9);
    }

    @Test
    public void centerWeightSingleCharIsZero() {
        assertEquals(0d, TextUtil.Anim.centerWeight(0, 1), 1e-9);
    }

    // ───────── 变换分量 ─────────

    @Test
    public void scaleReachesOneAtFullProgress() {
        // 任何轴、任何样式，p=1 时 scale 收敛到 1
        for (TextUtil.Style style : TextUtil.Style.values()) {
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(style.name() + " axis" + axis + " 未收敛",
                        1d, TextUtil.Anim.scale(style.in(), 1d, axis, 0, 0), 1e-9);
            }
        }
    }

    @Test
    public void scaleNeverZero() {
        // 0 缩放会被客户端判为不可见 ⇒ 恒有下限 0.01
        for (TextUtil.Style style : TextUtil.Style.values()) {
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(style.name() + " axis" + axis + " 触底",
                        TextUtil.Anim.scale(style.in(), 0d, axis, 0, 0) >= 0.01d);
            }
        }
    }

    @Test
    public void translationReachesZeroAtFullProgress() {
        for (TextUtil.Style style : TextUtil.Style.values()) {
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(style.name() + " 位移未归零",
                        0d, TextUtil.Anim.translation(style.in(), 1d, axis, 0, 0), 1e-9);
            }
        }
    }

    @Test
    public void rotationReachesZeroAtFullProgress() {
        for (TextUtil.Style style : TextUtil.Style.values()) {
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(style.name() + " 旋转未归零",
                        0d, TextUtil.Anim.rotationDegrees(style.in(), 1d, axis, 0, 0), 1e-9);
            }
        }
    }

    @Test
    public void translateDownGoesDownThenSettles() {
        // style=11 逐字下落：出现开始时在 +1.5 格（上方），结束归零
        assertEquals(1.5d, TextUtil.Anim.translation(TextUtil.Style.InKind.TRANSLATE_DOWN, 0d, 1, 0, 0), 1e-9);
        assertEquals(0d, TextUtil.Anim.translation(TextUtil.Style.InKind.TRANSLATE_DOWN, 1d, 1, 0, 0), 1e-9);
    }

    @Test
    public void opacityClampsToByte() {
        assertEquals(0, TextUtil.Anim.opacity(0d));
        assertEquals(255, TextUtil.Anim.opacity(1d));
        assertTrue(TextUtil.Anim.opacity(0.5d) >= 0 && TextUtil.Anim.opacity(0.5d) <= 255);
        // NaN ⇒ 0
        assertEquals(0, TextUtil.Anim.opacity(Double.NaN));
    }

    // ───────── clamp 与确定性 ─────────

    @Test
    public void clamp01HandlesExtremes() {
        assertEquals(0d, TextUtil.Anim.clamp01(-1d), 1e-9);
        assertEquals(1d, TextUtil.Anim.clamp01(2d), 1e-9);
        assertEquals(0.5d, TextUtil.Anim.clamp01(0.5d), 1e-9);
        assertEquals(0d, TextUtil.Anim.clamp01(Double.NaN), 1e-9);
    }

    @Test
    public void unitOfIsDeterministicAndInRange() {
        double a = TextUtil.Anim.unitOf(5, 1, 123L);
        double b = TextUtil.Anim.unitOf(5, 1, 123L);
        assertEquals(a, b, 1e-12);
        assertTrue(a >= 0d && a <= 1d);
    }

    @Test
    public void unitOfDiffersAcrossInputs() {
        // 不同下标 / 轴 / 种子应产出不同值（统计上；此处只验证不恒等）
        double base = TextUtil.Anim.unitOf(0, 0, 0L);
        boolean anyDiffers = false;
        for (int i = 0; i < 32 && !anyDiffers; i++) {
            for (int axis = 0; axis < 3 && !anyDiffers; axis++) {
                anyDiffers = TextUtil.Anim.unitOf(i, axis, i * 7L) != base;
            }
        }
        assertTrue("确定性随机应产生不同的值", anyDiffers);
    }
}
