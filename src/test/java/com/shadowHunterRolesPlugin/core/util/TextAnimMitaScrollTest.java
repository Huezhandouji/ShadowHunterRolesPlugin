package com.shadowHunterRolesPlugin.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ★ **米塔风格 / 字体掉落**（{@link TextUtil.Style#MITA_SCROLL}）的离线穷举单测。
 *
 * <h2>口径</h2>
 * 「**生成时是正常文本，消失时才掉落**」：字先原地正常显示（出现 + 停留），
 * 到消失阶段才落到正下方地面 → 触地弹跳 → **空中翻滚** → **平躺不动** → 淡出。
 *
 * <h2>2026-10-04 加强的物理（本测试的断言对象）</h2>
 * <ol>
 *   <li><b>落地位置小幅度不同</b>：每字有自己的横向漂移 ⇒ 落点散成一小圈；</li>
 *   <li><b>落地角度不同</b>：躺倒俯仰 = −88° ± 24°、偏航 = ±30°，**每字固定**；</li>
 *   <li><b>丝滑翻动</b>：空中额外翻 0..2 圈，按总下落进度驱动 ⇒ 落地收敛到躺倒角；</li>
 *   <li><b>跳完平躺不动</b>：静止后横向/竖直全冻结（不许"钉住了还在滑"）。</li>
 * </ol>
 *
 * <h2>为什么值得单测</h2>
 * 这套物理全是**无报错**的行为：算错了只会"字掉错高度 / 穿地 / 不弹 / 不翻 / 躺得一样 /
 * 静止后还在滑 / 掉到一半被强收"，运行期看不出来。且它是**纯函数自积分**（不借任何实体）
 * ⇒ 正好可以离线把每条性质钉死。
 *
 * <p>移植对象 = 数据包 V26.2 的 {@code operation/drop}（重力 + 反弹）
 * 与 {@code positional/animation/process/drop} 的落地淡出。
 *
 * <h2>★ 元组口径（改版后）</h2>
 * {@code scrollTrackAt} 返回 **6 元**：{@code [x, z, y, pitch(度), yaw(度), restingAt]}
 * —— 水平分量在前，y 在第 3 位。
 */
public class TextAnimMitaScrollTest {

    private static final long SEED = 20261004L;

    /** 元组下标（避免满篇魔数）。 */
    private static final int X = 0;
    private static final int Z = 1;
    private static final int Y = 2;
    private static final int PITCH = 3;
    private static final int YAW = 4;
    private static final int REST = 5;

    // ───────── 兼容读口：历史方法仍必须存在且安全 ─────────

    @Test
    public void legacyHorizontalReadersStillReturnZero() {
        // ★ 旧的"水平散布"读口已废除水平位移（改由漂移读口承担）⇒ 恒 0，保证旧调用方不炸
        for (int i = 0; i < 64; i++) {
            assertEquals("scrollSpeedOf 必须恒 0", 0d, TextUtil.Anim.scrollSpeedOf(i, SEED), 1e-12);
            assertEquals("scrollAngleOf 必须恒 0", 0d, TextUtil.Anim.scrollAngleOf(i, SEED), 1e-12);
        }
    }

    @Test
    public void liftSpeedStaysInConfiguredRange() {
        for (int i = 0; i < 64; i++) {
            double v = TextUtil.Anim.scrollLiftOf(i, SEED);
            assertTrue("落差初速必须 >= 下限", v >= TextUtil.Anim.SCROLL_LIFT_MIN - 1e-9);
            assertTrue("落差初速必须 <= 上限", v <= TextUtil.Anim.SCROLL_LIFT_MAX + 1e-9);
            assertTrue("落差初速必须很小（掉落不该被抛高）",
                    TextUtil.Anim.SCROLL_LIFT_MAX <= 0.2d);
        }
    }

    @Test
    public void negativeIndexDoesNotBlowUp() {
        assertEquals(0d, TextUtil.Anim.scrollSpeedOf(-5, SEED), 1e-12);
        assertTrue(TextUtil.Anim.scrollLiftOf(-5, SEED) >= TextUtil.Anim.SCROLL_LIFT_MIN - 1e-9);
        assertTrue("漂移速度必须有限", Double.isFinite(TextUtil.Anim.scrollDriftOf(-5, SEED)));
        assertTrue("漂移方向必须有限", Double.isFinite(TextUtil.Anim.scrollDriftAngleOf(-5, SEED)));
        assertTrue("躺倒角必须有限", Float.isFinite(TextUtil.Anim.scrollLiePitchOf(-5, SEED)));
    }

    @Test
    public void velocityXIsAlwaysZero() {
        for (int f = 0; f <= 200; f++) {
            assertEquals("水平速度读口必须恒 0，frame=" + f, 0d,
                    TextUtil.Anim.scrollVelocityX(0, SEED, f), 1e-12);
        }
    }

    // ───────── 新增：横向漂移读口（落地位置小幅度不同） ─────────

    @Test
    public void driftSpeedStaysInConfiguredRange() {
        for (int i = 0; i < 64; i++) {
            double v = TextUtil.Anim.scrollDriftOf(i, SEED);
            assertTrue("漂移速度必须 >= 0，index=" + i, v >= -1e-9);
            assertTrue("漂移速度必须有界（不超过上限），index=" + i,
                    v <= TextUtil.Anim.SCROLL_DRIFT_MAX + 1e-9);
        }
    }

    @Test
    public void driftAngleCoversFullCircle() {
        // 满圈 ⇒ 落点散成一圈，而不是全朝同一侧
        boolean sawLower = false;
        boolean sawUpper = false;
        for (int i = 0; i < 64; i++) {
            double a = TextUtil.Anim.scrollDriftAngleOf(i, SEED);
            assertTrue("方向落在 [0, 2π)，index=" + i, a >= 0d && a < Math.PI * 2d + 1e-9);
            if (a < Math.PI / 2d) {
                sawLower = true;
            }
            if (a > Math.PI) {
                sawUpper = true;
            }
        }
        assertTrue("漂移方向必须覆盖多个象限（否则全朝一个方向偏）", sawLower && sawUpper);
    }

    @Test
    public void differentCharsDriftDifferently() {
        // 反证：若每字漂移一样，就退化成"整行一起平移"，不是"散落"
        double a = TextUtil.Anim.scrollDriftOf(0, SEED);
        double b = TextUtil.Anim.scrollDriftOf(1, SEED);
        assertNotEquals("相邻字的漂移速度不该相同", a, b, 1e-9);
    }

    // ───────── 新增：落地角度随机（每字不同且固定） ─────────

    @Test
    public void liePitchIsJitteredPerCharButBounded() {
        for (int i = 0; i < 64; i++) {
            float p = TextUtil.Anim.scrollLiePitchOf(i, SEED);
            float base = TextUtil.Anim.SCROLL_LIE_PITCH;
            float jitter = TextUtil.Anim.SCROLL_LIE_JITTER;
            assertTrue("躺倒角必须落在 ±抖动内，index=" + i,
                    p >= base - jitter - 1e-4f && p <= base + jitter + 1e-4f);
        }
    }

    @Test
    public void liePitchActuallyVariesAcrossChars() {
        // ★ "每字躺得不一样" —— 必须有真实差异，否则等于没加
        boolean varies = false;
        float first = TextUtil.Anim.scrollLiePitchOf(0, SEED);
        for (int i = 1; i < 32; i++) {
            if (Math.abs(TextUtil.Anim.scrollLiePitchOf(i, SEED) - first) > 1f) {
                varies = true;
                break;
            }
        }
        assertTrue("不同字的躺倒角必须不同", varies);
    }

    @Test
    public void lieYawIsJitteredPerCharAndBounded() {
        boolean varies = false;
        float first = TextUtil.Anim.scrollLieYawOf(0, SEED);
        for (int i = 0; i < 64; i++) {
            float y = TextUtil.Anim.scrollLieYawOf(i, SEED);
            assertTrue("躺倒偏航必须落在 ±抖动内，index=" + i,
                    Math.abs(y) <= TextUtil.Anim.SCROLL_LIE_YAW_JITTER + 1e-4f);
            if (Math.abs(y - first) > 1f) {
                varies = true;
            }
        }
        assertTrue("不同字的躺倒偏航必须不同", varies);
    }

    @Test
    public void turnsStayInConfiguredRange() {
        for (int i = 0; i < 64; i++) {
            int t = TextUtil.Anim.scrollTurnsOf(i, SEED);
            assertTrue("圈数必须 >= 0，index=" + i, t >= 0);
            assertTrue("圈数必须 <= 上限，index=" + i, t <= TextUtil.Anim.SCROLL_TUMBLE_MAX_TURNS);
        }
    }

    @Test
    public void someCharsTumbleAndSomeDoNot() {
        // 全部同圈数 ⇒ 一排字翻得一模一样（很假）⇒ 必须有差异
        boolean sawZero = false;
        boolean sawMore = false;
        for (int i = 0; i < 64; i++) {
            int t = TextUtil.Anim.scrollTurnsOf(i, SEED);
            if (t == 0) {
                sawZero = true;
            }
            if (t > 0) {
                sawMore = true;
            }
        }
        assertTrue("必须有的字不翻、有的字翻（参差感）", sawZero && sawMore);
    }

    // ───────── 位置积分（掉落 + 弹跳 + 横向漂移） ─────────

    @Test
    public void stateAtFrameZeroIsAtStart() {
        double[] s = TextUtil.Anim.scrollStateAt(0, SEED, 0, 4d, 60);
        assertEquals("第 0 刻水平未漂移", 0d, s[X], 1e-9);
        assertEquals("第 0 刻侧向未漂移", 0d, s[Z], 1e-9);
        assertEquals("第 0 刻竖直偏移 = 起点高度", 4d, s[Y], 1e-9);
    }

    @Test
    public void characterFallsTowardsGroundImmediately() {
        double drop = 3d;
        double[] s = TextUtil.Anim.scrollStateAt(0, SEED, 4, drop, 80);
        assertTrue("必须很快开始往下掉（第 4 刻要低于起点）", s[Y] < drop);
    }

    @Test
    public void characterEventuallyRestsOnGround() {
        double[] s = TextUtil.Anim.scrollStateAt(0, SEED, 300, 4d, 300);
        assertEquals("最终必须落到地面", 0d, s[Y], 1e-6);
    }

    @Test
    public void verticalOffsetNeverGoesBelowGround() {
        for (int f = 0; f <= 200; f++) {
            double[] s = TextUtil.Anim.scrollStateAt(0, SEED, f, 4d, 200);
            assertTrue("不得穿地，frame=" + f, s[Y] >= -1e-9);
        }
    }

    @Test
    public void bounceHappensAtLeastOnce() {
        int touches = 0;
        boolean wasAirborne = true;
        for (int f = 0; f <= 200; f++) {
            double[] s = TextUtil.Anim.scrollStateAt(0, SEED, f, 4d, 200);
            boolean airborne = s[Y] > 1e-6d;
            if (wasAirborne && !airborne) {
                touches++;
            }
            wasAirborne = airborne;
        }
        assertTrue("至少触地一次", touches >= 1);
        assertTrue("弹跳次数合理（不该无限弹）", touches <= 12);
    }

    @Test
    public void horizontalDriftStaysWithinMax() {
        // ★ 落地位置只是"小幅度"不同 ⇒ 全过程水平偏移必须有界
        for (int i = 0; i < 16; i++) {
            double limit = TextUtil.Anim.SCROLL_DRIFT_MAX * 2d + 1e-6d;
            for (int f = 0; f <= 300; f++) {
                double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, f, 0d, -1.2d,
                        TextUtil.Anim.scrollLiftOf(i, SEED), 300);
                assertTrue("index=" + i + " frame=" + f + " 水平漂移不得超出预期",
                        Math.abs(s[X]) <= limit);
                assertTrue("index=" + i + " frame=" + f + " 侧向漂移不得超出预期",
                        Math.abs(s[Z]) <= limit);
            }
        }
    }

    @Test
    public void charsLandAtDifferentPlaces() {
        // ★ 需求核心：落地位置"小幅度不同" ⇒ 终点的水平落点必须散开
        boolean differs = false;
        double[] first = TextUtil.Anim.scrollTrackAt(0, SEED, 300, 0d, -1.2d, 0d, 300);
        for (int i = 1; i < 24; i++) {
            double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, 300, 0d, -1.2d, 0d, 300);
            if (Math.abs(s[X] - first[X]) > 0.01d || Math.abs(s[Z] - first[Z]) > 0.01d) {
                differs = true;
                break;
            }
        }
        assertTrue("不同字的落点必须不同（否则会原地叠成一条线）", differs);
    }

    @Test
    public void horizontalFreezesAfterResting() {
        // ★★ 静止后横向必须锁死 —— 否则会出现"被 FIXED 钉住了却还在平移"的矛盾动作
        for (int i = 0; i < 8; i++) {
            int restAt = -1;
            for (int f = 0; f <= 300 && restAt < 0; f++) {
                double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, f, 0d, -1.2d,
                        TextUtil.Anim.scrollLiftOf(i, SEED), 300);
                if (s[REST] >= 0) {
                    restAt = (int) s[REST];
                }
            }
            assertTrue("index=" + i + " 必须能静止", restAt > 0);
            double[] a = TextUtil.Anim.scrollTrackAt(i, SEED, restAt + 5, 0d, -1.2d, 0d, 300);
            double[] b = TextUtil.Anim.scrollTrackAt(i, SEED, restAt + 60, 0d, -1.2d, 0d, 300);
            assertEquals("静止后 x 不得再变，index=" + i, a[X], b[X], 1e-9);
            assertEquals("静止后 z 不得再变，index=" + i, a[Z], b[Z], 1e-9);
            assertEquals("静止后 y 不得再变，index=" + i, a[Y], b[Y], 1e-9);
        }
    }

    @Test
    public void poseIsStableAfterResting() {
        // ★ "跳到平躺在地上不动" ⇒ 静止后姿态必须完全冻结
        for (int i = 0; i < 8; i++) {
            double[] a = TextUtil.Anim.scrollTrackAt(i, SEED, 150, 0d, -1.2d, 0d, 300);
            double[] b = TextUtil.Anim.scrollTrackAt(i, SEED, 260, 0d, -1.2d, 0d, 300);
            assertEquals("静止后俯仰必须冻结，index=" + i, a[PITCH], b[PITCH], 1e-9);
            assertEquals("静止后偏航必须冻结，index=" + i, a[YAW], b[YAW], 1e-9);
        }
    }

    @Test
    public void restingPitchEqualsThatCharsLieAngle() {
        // ★ 静止时的俯仰 == 该字自己的躺倒角（每字不同），而不是全体同一个常数
        for (int i = 0; i < 16; i++) {
            double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, 400, 0d, -1.2d, 0d, 400);
            assertEquals("静止俯仰必须等于该字的躺倒角，index=" + i,
                    TextUtil.Anim.scrollLiePitchOf(i, SEED), s[PITCH], 1e-6);
            assertEquals("静止偏航必须等于该字的躺倒偏航，index=" + i,
                    TextUtil.Anim.scrollLieYawOf(i, SEED), s[YAW], 1e-6);
        }
    }

    @Test
    public void landingAnglesActuallyDifferAcrossChars() {
        // ★ 需求核心：落地后"跳动角度不同" ⇒ 终点的 pitch/yaw 必须真有差异
        double firstPitch = TextUtil.Anim.scrollTrackAt(0, SEED, 400, 0d, -1.2d, 0d, 400)[PITCH];
        double firstYaw = TextUtil.Anim.scrollTrackAt(0, SEED, 400, 0d, -1.2d, 0d, 400)[YAW];
        boolean pitchVaries = false;
        boolean yawVaries = false;
        for (int i = 1; i < 24; i++) {
            double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, 400, 0d, -1.2d, 0d, 400);
            if (Math.abs(s[PITCH] - firstPitch) > 1d) {
                pitchVaries = true;
            }
            if (Math.abs(s[YAW] - firstYaw) > 1d) {
                yawVaries = true;
            }
        }
        assertTrue("落地俯仰必须每字不同", pitchVaries);
        assertTrue("落地偏航必须每字不同", yawVaries);
    }

    @Test
    public void airbornePoseDiffersFromLandingPose() {
        // ★ "空中自然翻转" ⇒ 空中姿态必须与落地姿态不同（否则等于没翻）
        boolean anyDiffers = false;
        for (int i = 0; i < 16; i++) {
            double[] air = TextUtil.Anim.scrollTrackAt(i, SEED, 3, 0d, -4d, 0d, 300);
            if (air[REST] >= 0) {
                continue;
            }
            double[] land = TextUtil.Anim.scrollTrackAt(i, SEED, 400, 0d, -4d, 0d, 400);
            if (Math.abs(air[PITCH] - land[PITCH]) > 0.5d) {
                anyDiffers = true;
                break;
            }
        }
        assertTrue("空中姿态必须与落地姿态不同（说明确实在翻）", anyDiffers);
    }

    @Test
    public void tumbleConvergesToLieAngleOnLanding() {
        // ★ 丝滑的关键：翻滚必须"落地即收敛" —— 静止前一帧与静止后一帧不得跳变过大
        for (int i = 0; i < 16; i++) {
            int restAt = -1;
            for (int f = 0; f <= 300 && restAt < 0; f++) {
                double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, f, 0d, -1.2d, 0d, 300);
                if (s[REST] >= 0) {
                    restAt = (int) s[REST];
                }
            }
            assertTrue("index=" + i + " 必须能静止", restAt > 0);
            double[] before = TextUtil.Anim.scrollTrackAt(i, SEED, Math.max(0, restAt - 1),
                    0d, -1.2d, 0d, 300);
            double[] after = TextUtil.Anim.scrollTrackAt(i, SEED, restAt, 0d, -1.2d, 0d, 300);
            assertTrue("index=" + i + " 落地瞬间姿态不得跳变（|Δpitch| = "
                            + Math.abs(before[PITCH] - after[PITCH]) + "）",
                    Math.abs(before[PITCH] - after[PITCH]) <= 40d);
        }
    }

    @Test
    public void zeroHeightStillWorks() {
        double[] s = TextUtil.Anim.scrollStateAt(0, SEED, 50, 0d, 60);
        assertTrue(Double.isFinite(s[X]) && Double.isFinite(s[Y]) && Double.isFinite(s[PITCH]));
        assertEquals("起点高度为 0 ⇒ 从地面起步", 0d, s[Y], 1e-6);
    }

    @Test
    public void clampingFrameBeyondMaxIsSafe() {
        double[] a = TextUtil.Anim.scrollStateAt(0, SEED, 9999, 4d, 40);
        double[] b = TextUtil.Anim.scrollStateAt(0, SEED, 40, 4d, 40);
        assertEquals(a[X], b[X], 1e-9);
        assertEquals(a[Y], b[Y], 1e-9);
    }

    // ───────── 淡出 ─────────

    @Test
    public void fadeStartsFullyOpaque() {
        assertEquals(1d, TextUtil.Anim.scrollFadeOpacity(0), 1e-9);
    }

    @Test
    public void fadeEndsTransparent() {
        assertEquals("淡出尾帧必须全透明", 0d,
                TextUtil.Anim.scrollFadeOpacity(TextUtil.Anim.SCROLL_FADE_FRAMES), 1e-9);
    }

    @Test
    public void fadeIsMonotonicDecreasing() {
        double prev = Double.MAX_VALUE;
        for (int f = 0; f <= TextUtil.Anim.SCROLL_FADE_FRAMES + 5; f++) {
            double o = TextUtil.Anim.scrollFadeOpacity(f);
            assertTrue("不透明度不得回升，frame=" + f, o <= prev + 1e-9);
            assertTrue("不透明度落在 [0,1]", o >= -1e-9 && o <= 1d + 1e-9);
            prev = o;
        }
    }

    @Test
    public void negativeFadeFrameTreatedAsStart() {
        assertEquals(1d, TextUtil.Anim.scrollFadeOpacity(-10), 1e-9);
    }

    // ───────── 新口径：生成时"正常文本"，消失时才掉落 ─────────

    @Test
    public void rollStartsAtTheBasePosition() {
        double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, 0, 0d, -1.2d, 0d, 200);
        assertEquals("水平未位移", 0d, s[X], 1e-9);
        assertEquals("侧向未位移", 0d, s[Z], 1e-9);
        assertEquals("竖直未位移（就在生成位置）", 0d, s[Y], 1e-9);
        assertEquals("尚未静止", -1d, s[REST], 1e-9);
    }

    @Test
    public void rollFallsToGroundBelowBase() {
        double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, 400, 0d, -1.2d, 0d, 400);
        assertEquals("必须落到地面高度", -1.2d, s[Y], 1e-6);
        assertEquals("落地后躺倒（该字自己的角）", TextUtil.Anim.scrollLiePitchOf(0, SEED),
                s[PITCH], 1e-6);
    }

    @Test
    public void rollNeverGoesBelowGround() {
        for (int f = 0; f <= 300; f++) {
            double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, f, 0d, -1.5d, 0d, 300);
            assertTrue("不得穿地，frame=" + f, s[Y] >= -1.5d - 1e-9);
        }
    }

    @Test
    public void rollReportsRestingFrame() {
        int restAt = -1;
        for (int f = 0; f <= 200 && restAt < 0; f++) {
            double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, f, 0d, -1.2d, 0d, 200);
            if (s[REST] >= 0) {
                restAt = (int) s[REST];
            }
        }
        assertTrue("必须在合理时间内静止", restAt > 0 && restAt < 80);
    }

    @Test
    public void verticalMotionFreezesAfterResting() {
        double[] a = TextUtil.Anim.scrollTrackAt(0, SEED, 120, 0d, -1.2d, 0d, 200);
        double[] b = TextUtil.Anim.scrollTrackAt(0, SEED, 180, 0d, -1.2d, 0d, 200);
        assertEquals("静止后竖直不再变", a[Y], b[Y], 1e-9);
        assertEquals("静止后姿态不变", a[PITCH], b[PITCH], 1e-9);
    }

    @Test
    public void rollBudgetCoversRestAndFade() {
        // ★ scrollRollTicksFor 必须 >= 实际静止刻 + 停留 + 淡出
        //   否则整段会被 advance() 的兜底逻辑提前强收（字掉到一半突然消失）
        for (double h : new double[]{0.5d, 1d, 1.2d, 2d, 4d, 8d, 24d}) {
            int total = TextUtil.Anim.scrollRollTicksFor(h);
            int restAt = -1;
            for (int f = 0; f <= total && restAt < 0; f++) {
                double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, f, 0d, -h, 0d, total + 1);
                if (s[REST] >= 0) {
                    restAt = (int) s[REST];
                }
            }
            assertTrue("h=" + h + " 必须能静止", restAt >= 0);
            assertTrue("h=" + h + " 预算必须覆盖静止+停留+淡出（预算=" + total + " 静止=" + restAt + "）",
                    total >= restAt + TextUtil.Anim.SCROLL_REST_TICKS + TextUtil.Anim.SCROLL_FADE_FRAMES);
        }
    }

    @Test
    public void rollBudgetCoversRestWithPerCharLift() {
        // ★ 渲染侧传的是**逐字初速 + 逐字漂移**（不是 0）⇒ 预算也必须覆盖那种情况
        for (double h : new double[]{0.5d, 1.2d, 4d, 12d}) {
            for (int i = 0; i < 8; i++) {
                int total = TextUtil.Anim.scrollRollTicksFor(h);
                int restAt = -1;
                for (int f = 0; f <= total && restAt < 0; f++) {
                    double[] s = TextUtil.Anim.scrollTrackAt(i, SEED, f, 0d, -h,
                            TextUtil.Anim.scrollLiftOf(i, SEED), total + 1);
                    if (s[REST] >= 0) {
                        restAt = (int) s[REST];
                    }
                }
                assertTrue("h=" + h + " index=" + i + " 必须能静止（预算=" + total + "）", restAt >= 0);
                assertTrue("h=" + h + " index=" + i + " 预算必须覆盖静止+停留+淡出",
                        total >= restAt + TextUtil.Anim.SCROLL_REST_TICKS
                                + TextUtil.Anim.SCROLL_FADE_FRAMES);
            }
        }
    }

    @Test
    public void rollBudgetIsMonotonicInFallDistance() {
        assertTrue("落得越远预算越长",
                TextUtil.Anim.scrollRollTicksFor(4d) >= TextUtil.Anim.scrollRollTicksFor(1d));
        assertTrue("零落距也要够停留+淡出",
                TextUtil.Anim.scrollRollTicksFor(0d)
                        >= TextUtil.Anim.SCROLL_REST_TICKS + TextUtil.Anim.SCROLL_FADE_FRAMES);
    }

    @Test
    public void fallIsCappedForHighAltitude() {
        assertEquals("封顶值",
                TextUtil.Anim.clampScrollFall(TextUtil.Anim.MAX_FALL_HEIGHT * 10d),
                TextUtil.Anim.clampScrollFall(TextUtil.Anim.MAX_FALL_HEIGHT), 1e-9);
        assertEquals("封顶后预算 == 按封顶值算的预算",
                TextUtil.Anim.scrollRollTicksFor(TextUtil.Anim.MAX_FALL_HEIGHT * 10d),
                TextUtil.Anim.scrollRollTicksFor(TextUtil.Anim.MAX_FALL_HEIGHT));
    }

    @Test
    public void totalTicksOfMitaScrollGrowsWithFall() {
        int shortFall = TextUtil.Anim.totalTicksOf(TextUtil.Style.MITA_SCROLL, 4, 100, 55, 1d);
        int longFall = TextUtil.Anim.totalTicksOf(TextUtil.Style.MITA_SCROLL, 4, 100, 55, 6d);
        assertTrue("落得越远整段越长", longFall > shortFall);
    }

    @Test
    public void legacyScrollStateAtMatchesGeneralTrack() {
        // 旧口径读口必须与通用积分完全一致（保证既有调用方行为不变）
        for (int f = 0; f <= 120; f++) {
            double[] a = TextUtil.Anim.scrollStateAt(0, SEED, f, 4d, 200);
            double[] b = TextUtil.Anim.scrollTrackAt(0, SEED, f, 4d, 0d,
                    TextUtil.Anim.scrollLiftOf(0, SEED), 200);
            for (int k = 0; k <= REST; k++) {
                assertEquals("下标 " + k + " 必须一致，frame=" + f, a[k], b[k], 1e-9);
            }
        }
    }

    // ───────── 确定性（可复现） ─────────

    @Test
    public void trackIsDeterministicForSameInputs() {
        // ★ 同一 (index, seed, frame, 参数) 必须得到完全一样的轨迹（离线可复现）
        for (int i = 0; i < 8; i++) {
            for (int f : new int[]{0, 1, 5, 20, 60, 150}) {
                double[] a = TextUtil.Anim.scrollTrackAt(i, SEED, f, 0d, -1.6d, 0.05d, 300);
                double[] b = TextUtil.Anim.scrollTrackAt(i, SEED, f, 0d, -1.6d, 0.05d, 300);
                assertArrayEquals("index=" + i + " frame=" + f + " 必须逐位一致", a, b, 0d);
            }
        }
    }

    // ───────── ★ 落地后不再随视角转动（billboard 切换） ─────────

    @Test
    public void airborneUsesCenterBillboard() {
        assertEquals("空中必须用 CENTER（面向视线）",
                org.bukkit.entity.Display.Billboard.CENTER, TextUtil.billboardFor(false));
    }

    @Test
    public void restingUsesFixedBillboard() {
        assertEquals("落地后必须用 FIXED（朝向固定，不随视角转）",
                org.bukkit.entity.Display.Billboard.FIXED, TextUtil.billboardFor(true));
    }

    @Test
    public void billboardSwitchesExactlyAtRestingFrame() {
        double ground = -1.4d;
        int restAt = -1;
        for (int f = 0; f <= 400 && restAt < 0; f++) {
            double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, f, 0d, ground, 0d, 400);
            if (s[REST] >= 0) {
                restAt = (int) s[REST];
            }
        }
        assertTrue("必须先落地", restAt > 0);
        for (int f = 0; f <= restAt; f++) {
            double[] s = TextUtil.Anim.scrollTrackAt(0, SEED, f, 0d, ground, 0d, 400);
            boolean resting = s[REST] >= 0;
            org.bukkit.entity.Display.Billboard want = resting
                    ? org.bukkit.entity.Display.Billboard.FIXED
                    : org.bukkit.entity.Display.Billboard.CENTER;
            assertEquals("frame=" + f + " 的 billboard 不对",
                    want, TextUtil.billboardFor(resting));
        }
    }

    // ───────── 风格接线 ─────────

    @Test
    public void styleIsRegisteredAndDistinct() {
        assertNotNull(TextUtil.Style.MITA_SCROLL);
        assertNotEquals("米塔风格必须与落地砸字不同",
                TextUtil.Style.GROUND_SMASH, TextUtil.Style.MITA_SCROLL);
        assertEquals("别名必须指向同一个枚举值",
                TextUtil.Style.MITA_SCROLL, TextUtil.STYLE_MITA_SCROLL);
        assertEquals("口语别名也必须指向它",
                TextUtil.Style.MITA_SCROLL, TextUtil.STYLE_TEXT_ROLL);
    }

    @Test
    public void styleDoesNotStealDatapackNumbering() {
        assertNotEquals("原创风格不得进入编号表",
                TextUtil.Style.MITA_SCROLL, TextUtil.Style.byDatapackNumber(36));
    }

    @Test
    public void styleUsesMitaRollInKind() {
        assertEquals(TextUtil.Style.InKind.MITA_ROLL, TextUtil.Style.MITA_SCROLL.in());
    }
}
