package com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 「红石无人机」飞行速度的**数值冻结**（2026-10-31 需求：加速到原本速度的两倍）。
 *
 * <h2>判据</h2>
 * 原始基线写在组件的口径申报里：派出 4 格/秒、附近有人 / 召回 / 紧密跟随 8 格/秒；
 * 本次需求把两组都 ×2 ⇒ 8 / 16 格/秒。速度由组件按"格/刻"逐刻累加（{@code stepBudget}），
 * 因此这里的常量就是实际平均速度，改数值必须同步改本测试（防止"改了一半/漏改一组"）。
 */
public class MatinaDroneSpeedTest {

    private static final double EPS = 1.0E-9d;

    /** 派出（直线巡航）速度 = 原 4 格/秒 × 2。 */
    @Test
    public void dispatchSpeedIsDoubleTheBaseline() {
        assertEquals("派出速度必须为 8 格/秒（原 4 × 2）",
                8.0d, MatinaRedstoneDroneSkill.SPEED_DISPATCH, EPS);
    }

    /** 附近有人 / 召回 / 紧密跟随速度 = 原 8 格/秒 × 2。 */
    @Test
    public void assistSpeedIsDoubleTheBaseline() {
        assertEquals("辅助 / 召回速度必须为 16 格/秒（原 8 × 2）",
                16.0d, MatinaRedstoneDroneSkill.SPEED_ASSIST, EPS);
    }

    /** 两组速度保持"辅助 = 派出 × 2"的原有比例，别只改一组。 */
    @Test
    public void assistStaysTwiceTheDispatchSpeed() {
        assertEquals("辅助速度必须永远是派出速度的 2 倍",
                2.0d, MatinaRedstoneDroneSkill.SPEED_ASSIST / MatinaRedstoneDroneSkill.SPEED_DISPATCH, EPS);
    }
}
