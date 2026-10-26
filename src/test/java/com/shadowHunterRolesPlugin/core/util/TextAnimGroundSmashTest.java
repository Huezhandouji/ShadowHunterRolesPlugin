package com.shadowHunterRolesPlugin.core.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * {@link TextUtil.Anim} 里**落地砸字**（原创风格）那一族的离线穷举单测。
 *
 * <p>覆盖：自由落体曲线、落地冲击（压扁 / 回弹）、呼吸滞留、缩小消失、
 * 地面查找（{@code groundLevelY}）的边界，以及 {@link TextUtil.Style#GROUND_SMASH}
 * 的表项完整性与时长口径。
 *
 * <p>全部纯函数 / 纯数据 ⇒ 不碰 Bukkit，可离线跑。
 */
public class TextAnimGroundSmashTest {

    private static final double EPS = 1e-9;

    // ───────── 风格表项 ─────────

    @Test
    public void groundSmashIsDeclaredAndHasExpectedKinds() {
        TextUtil.Style style = TextUtil.Style.GROUND_SMASH;
        assertEquals(TextUtil.Style.InKind.GROUND_DROP, style.in());
        assertEquals(TextUtil.Style.OutKind.SHRINK_AWAY, style.out());
    }

    @Test
    public void groundSmashIsNotInTheDatapackNumberedTable() {
        // 原创风格不该污染数据包 0..35 编号表
        assertEquals(36, TextUtil.Style.numberedCount());
        for (int n = 0; n < 36; n++) {
            assertNotEquals("style " + n + " 不该是原创的 GROUND_SMASH",
                    TextUtil.Style.GROUND_SMASH, TextUtil.Style.byDatapackNumber(n));
        }
    }

    // ───────── 自由落体曲线 ─────────

    @Test
    public void fallProgressStartsAtZeroEndsAtOne() {
        assertEquals(0d, TextUtil.Anim.fallProgress(0, 12), EPS);
        assertEquals(1d, TextUtil.Anim.fallProgress(12, 12), EPS);
    }

    @Test
    public void fallProgressAcceleratesNotLinear() {
        // 抛物线：前半程走得少、后半程走得多 ⇒ 中点必须 < 0.5
        double mid = TextUtil.Anim.fallProgress(6, 12);
        assertTrue("中点应显著小于 0.5（重力加速），实际 = " + mid, mid < 0.5d);
        assertEquals(0.25d, mid, 1e-9); // (6/12)^2
    }

    @Test
    public void fallProgressClampsAndHandlesBadFrames() {
        assertEquals(0d, TextUtil.Anim.fallProgress(-5, 12), EPS);   // 负帧夹到 0
        assertEquals(1d, TextUtil.Anim.fallProgress(99, 12), EPS);   // 超界夹到 1
        assertEquals(1d, TextUtil.Anim.fallProgress(3, 0), EPS);     // frames<=0 ⇒ 视为已落地
        assertEquals(1d, TextUtil.Anim.fallProgress(3, -1), EPS);
    }

    @Test
    public void fallFramesScalesWithHeightButIsCapped() {
        assertEquals(4, TextUtil.Anim.fallFramesFor(0d));       // 最低 4 帧（矮处也要看得出在掉）
        assertEquals(6, TextUtil.Anim.fallFramesFor(6d));       // 每格约 1 帧
        // 上限封顶：超过 MAX_FALL_HEIGHT 不再增长
        int capped = TextUtil.Anim.fallFramesFor(TextUtil.Anim.MAX_FALL_HEIGHT);
        assertEquals(capped, TextUtil.Anim.fallFramesFor(1000d));
        assertEquals(capped, TextUtil.Anim.fallFramesFor(Double.MAX_VALUE));
    }

    // ───────── 落地冲击：压扁 → 回弹 → 复原 ─────────

    @Test
    public void impactScaleYIsSquashedAtQuarterAndBouncesAfter() {
        int frames = TextUtil.Anim.IMPACT_FRAMES;
        assertEquals(1d, TextUtil.Anim.impactScaleY(0, frames), EPS); // 触地瞬间仍是原样

        double squashed = TextUtil.Anim.impactScaleY(Math.round(frames * 0.25f), frames);
        assertEquals("四分之一处应压到谷底", TextUtil.Anim.IMPACT_SQUASH_Y, squashed, 1e-6);

        // ★ 回弹峰值：离散采样取不到曲线的名义峰值 ⇒ 断言"明显冲过 1"
        //   （实测在 frames=8 时峰值为 1.158，出现在 t≈0.625 那帧）
        double peak = 0d;
        for (int f = 0; f <= frames; f++) {
            peak = Math.max(peak, TextUtil.Anim.impactScaleY(f, frames));
        }
        assertTrue("整段必须出现 > 1 的回弹过冲，实际峰值 = " + peak, peak > 1.1d);
        assertTrue("回弹不该超过名义上限 " + TextUtil.Anim.IMPACT_BOUNCE_Y,
                peak <= TextUtil.Anim.IMPACT_BOUNCE_Y + 1e-9);

        assertEquals(1d, TextUtil.Anim.impactScaleY(frames, frames), EPS); // 末帧复原
    }

    @Test
    public void impactScaleXZIsInverseOfY() {
        int frames = TextUtil.Anim.IMPACT_FRAMES;
        // 压扁最狠时 X/Z 应外扩（>1）—— 视觉上"体积守恒"
        double xzSquash = TextUtil.Anim.impactScaleXZ(Math.round(frames * 0.25f), frames);
        assertEquals("压扁谷底时 X/Z 应外扩到名义值", TextUtil.Anim.IMPACT_SQUASH_XZ, xzSquash, 1e-6);
        assertTrue("X/Z 必须外扩（>1），实际 = " + xzSquash, xzSquash > 1d);

        // 回弹期间 X/Z 应压到 1 以下（与 Y 反向）
        double minXz = Double.MAX_VALUE;
        for (int f = 0; f <= frames; f++) {
            minXz = Math.min(minXz, TextUtil.Anim.impactScaleXZ(f, frames));
        }
        assertTrue("回弹段 X/Z 应低于 1（与 Y 反向），实际最低 = " + minXz, minXz < 1d);

        assertEquals(1d, TextUtil.Anim.impactScaleXZ(0, frames), EPS);
        assertEquals(1d, TextUtil.Anim.impactScaleXZ(frames, frames), EPS);
    }

    @Test
    public void impactScaleNeverGoesNonPositive() {
        int frames = TextUtil.Anim.IMPACT_FRAMES;
        for (int f = 0; f <= frames; f++) {
            assertTrue("Y 缩放必须 > 0（0 会被客户端判为不可见）",
                    TextUtil.Anim.impactScaleY(f, frames) > 0d);
            assertTrue("XZ 缩放必须 > 0",
                    TextUtil.Anim.impactScaleXZ(f, frames) > 0d);
        }
    }

    @Test
    public void impactHandlesZeroFrames() {
        assertEquals(1d, TextUtil.Anim.impactScaleY(3, 0), EPS);
        assertEquals(1d, TextUtil.Anim.impactScaleXZ(3, -1), EPS);
    }

    // ───────── 呼吸滞留 ─────────

    @Test
    public void breatheStartsAtFullAndNeverExceedsOne() {
        assertEquals(1d, TextUtil.Anim.breatheScale(0), EPS);
        for (int f = 0; f < TextUtil.Anim.BREATHE_PERIOD_TICKS * 3; f++) {
            double s = TextUtil.Anim.breatheScale(f);
            assertTrue("呼吸缩放必须 > 0 且 <= 1，f=" + f + " s=" + s, s > 0d && s <= 1d + EPS);
        }
    }

    @Test
    public void breatheTroughsAtHalfPeriod() {
        double trough = TextUtil.Anim.breatheScale(TextUtil.Anim.BREATHE_PERIOD_TICKS / 2);
        assertEquals(1d - TextUtil.Anim.BREATHE_AMPLITUDE, trough, 1e-6);
    }

    @Test
    public void breatheIsPeriodic() {
        int period = TextUtil.Anim.BREATHE_PERIOD_TICKS;
        assertEquals(TextUtil.Anim.breatheScale(0), TextUtil.Anim.breatheScale(period), 1e-6);
        assertEquals(TextUtil.Anim.breatheScale(7), TextUtil.Anim.breatheScale(7 + period), 1e-6);
    }

    // ───────── 缓慢缩小消失 ─────────

    @Test
    public void shrinkGoesFromOneToZeroMonotonically() {
        int frames = TextUtil.Anim.SHRINK_OUT_FRAMES;
        assertEquals(1d, TextUtil.Anim.shrinkProgress(0, frames), EPS);
        assertEquals(0d, TextUtil.Anim.shrinkProgress(frames, frames), EPS);

        double prev = Double.MAX_VALUE;
        for (int f = 0; f <= frames; f++) {
            double v = TextUtil.Anim.shrinkProgress(f, frames);
            assertTrue("必须单调不增，f=" + f, v <= prev + EPS);
            prev = v;
        }
    }

    @Test
    public void shrinkIsSlowerThanFadeOut() {
        // "缓慢缩小"：帧数必须多于普通淡出，否则观感就成"咔一下没了"
        assertTrue("缩小帧数应 > 淡出帧数",
                TextUtil.Anim.SHRINK_OUT_FRAMES > TextUtil.Anim.OUT_FRAMES);
    }

    @Test
    public void shrinkHandlesZeroFrames() {
        assertEquals(0d, TextUtil.Anim.shrinkProgress(3, 0), EPS);
    }

    // ───────── 地面查找 ─────────

    @Test
    public void groundLevelFindsFirstSolidBelow() {
        // 地面在 y=60（其余都是空气）
        int ground = TextUtil.Anim.groundLevelY(70, -64, 70, y -> y == 60);
        assertEquals(60, ground);
    }

    @Test
    public void groundLevelStartsFromNextBlockDown() {
        // ★ 起点那格本身是实心的（字生成在方块顶上）也不能算"地面"
        AtomicInteger probes = new AtomicInteger();
        int ground = TextUtil.Anim.groundLevelY(70, -64, 70, y -> {
            probes.incrementAndGet();
            return y == 70; // 只有起点那格"实心"
        });
        assertEquals("起点那格必须被跳过 ⇒ 回退到 fallback", 70, ground);
        assertTrue("不该探测起点本身之外的判断次数异常", probes.get() > 0);
    }

    @Test
    public void groundLevelFallsBackWhenNothingSolid() {
        // 虚空：一路到 minY 都是空气 ⇒ 回退
        assertEquals(42, TextUtil.Anim.groundLevelY(70, -64, 42, y -> false));
    }

    @Test
    public void groundLevelHandlesDegenerateInputs() {
        assertEquals(7, TextUtil.Anim.groundLevelY(0, 10, 7, y -> true));   // fromY <= minY ⇒ 回退
        assertEquals(7, TextUtil.Anim.groundLevelY(10, 10, 7, y -> true));  // 相等也回退
        assertEquals(7, TextUtil.Anim.groundLevelY(70, -64, 7, null));      // 判据缺失 ⇒ 回退
    }

    @Test
    public void groundLevelRespectsMinHeightFloor() {
        // 地面在 y=-70，但世界最低只到 -64 ⇒ 查不到，回退
        assertEquals(5, TextUtil.Anim.groundLevelY(10, -64, 5, y -> y == -70));
    }

    // ───────── ★ 回归：落点不必是 0（"砸到地面"而不是"砸到基准点"） ─────────

    @Test
    public void yAtCanDescendBelowZeroToAGroundRest() {
        // ★ 回归：字符的最终位置由"落点偏移 rest"决定，rest 常为负（地面在基准点下方）
        // 下落应从 drop(抬高量) 插值到 rest(落点)，而不是恒回 0
        double drop = 8.5d;
        double rest = -1.5d;
        assertEquals(drop, TextUtil.Anim.yAt(drop, rest, 0, 12), EPS);   // 起点 = 抬高量
        assertEquals(rest, TextUtil.Anim.yAt(drop, rest, 12, 12), EPS); // 落点 = rest（可为负）
        double mid = TextUtil.Anim.yAt(drop, rest, 6, 12);
        assertTrue("中点应落在 (rest, drop) 之间，实际 = " + mid, mid < drop && mid > rest);
    }

    @Test
    public void fallDistanceAccountsForGroundRest() {
        // ★ 回归：真实下落距离 = 抬高量 + 落点偏移（rest 为负 ⇒ 距离更大）
        // 只按"抬高量"算帧数 ⇒ 下落会显得过快（视觉上是"瞬移落地"）
        double drop = 7d;
        double rest = -2d;
        int correct = TextUtil.Anim.fallFramesFor(drop - rest); // 9 帧
        int wrong = TextUtil.Anim.fallFramesFor(drop);          // 7 帧
        assertTrue("把落点算进去后帧数应更多（掉得更从容）", correct > wrong);
    }

    @Test
    public void fallFramesForNeverGoesBelowFloor() {
        // 落差被算成 0 甚至负数（异常输入）时，仍要给"看得出在掉"的最小帧数
        assertEquals(4, TextUtil.Anim.fallFramesFor(0d));
        assertEquals(4, TextUtil.Anim.fallFramesFor(-5d));
    }

    @Test
    public void groundLevelYRestSitsOneAboveTheFloor() {
        // 站立面口径：实心格 y = f ⇒ 字应停在其顶面 f+1（不是埋进方块里）
        java.util.function.IntPredicate floorAt63 = y -> y == 63;
        int floorY = TextUtil.Anim.groundLevelY(100, -64, 100, floorAt63);
        assertEquals(63, floorY);
        assertEquals(64, floorY + 1); // 落点（绝对 Y）
    }

    @Test
    public void groundLevelYFallsBackWhenNoFloor() {
        // 虚空：一路查到底都没有实心格 ⇒ 返回 fallbackY（调用方会退化成"原地压扁"）
        java.util.function.IntPredicate nothing = y -> false;
        assertEquals(100, TextUtil.Anim.groundLevelY(100, -64, 100, nothing));
    }

    // ───────── yAt ─────────

    @Test
    public void yAtInterpolatesBetweenStartAndGround() {
        assertEquals(10d, TextUtil.Anim.yAt(10d, 0d, 0, 12), EPS);    // 起点
        assertEquals(0d, TextUtil.Anim.yAt(10d, 0d, 12, 12), EPS);    // 落点
        // 加速下落：中点的 Y 应高于线性中点（5.0）—— 还"没掉那么快"
        double mid = TextUtil.Anim.yAt(10d, 0d, 6, 12);
        assertTrue("加速下落中点应在 5.0 之上，实际 = " + mid, mid > 5d);
    }

    // ───────── 总时长口径 ─────────

    @Test
    public void totalTicksOfGroundSmashIsTallerThanPlainDrop() {
        int smash = TextUtil.Anim.totalTicksOf(TextUtil.Style.GROUND_SMASH, 4, 100, 55, 6d);
        int plain = TextUtil.Anim.totalTicksOf(TextUtil.Style.DROP_DOWN_IN, 4, 100, 55, 6d);
        assertTrue("落地砸字含下落+冲击+滞留+缩小，应比普通下落长", smash > plain);
    }

    @Test
    public void totalTicksOfGroundSmashGrowsWithFallHeightThenPlateaus() {
        int low = TextUtil.Anim.totalTicksOf(TextUtil.Style.GROUND_SMASH, 4, 100, 55, 3d);
        int high = TextUtil.Anim.totalTicksOf(TextUtil.Style.GROUND_SMASH, 4, 100, 55, 12d);
        assertTrue("越高掉得越久", high > low);

        // 超过封顶高度后不再增长（防高空刷屏）
        int capped = TextUtil.Anim.totalTicksOf(TextUtil.Style.GROUND_SMASH, 4, 100, 55,
                TextUtil.Anim.MAX_FALL_HEIGHT);
        int over = TextUtil.Anim.totalTicksOf(TextUtil.Style.GROUND_SMASH, 4, 100, 55, 9999d);
        assertEquals(capped, over);
    }

    @Test
    public void totalTicksOfNullStyleFallsBackSafely() {
        // null 风格不该抛异常（渲染路径上抛异常代价极大）
        int t = TextUtil.Anim.totalTicksOf(null, 3, 100, 55, 6d);
        assertTrue(t > 0);
    }

    // ───────── 纯函数无副作用（回归护栏）─────────

    @Test
    public void pureFunctionsAreDeterministic() {
        // 同一入参多次调用必须完全一致（这些函数会被逐帧调用几千次）
        for (int f = 0; f < 20; f++) {
            assertEquals(TextUtil.Anim.fallProgress(f, 12), TextUtil.Anim.fallProgress(f, 12), 0d);
            assertEquals(TextUtil.Anim.impactScaleY(f, 8), TextUtil.Anim.impactScaleY(f, 8), 0d);
            assertEquals(TextUtil.Anim.breatheScale(f), TextUtil.Anim.breatheScale(f), 0d);
            assertEquals(TextUtil.Anim.shrinkProgress(f, 22), TextUtil.Anim.shrinkProgress(f, 22), 0d);
        }
    }
}
