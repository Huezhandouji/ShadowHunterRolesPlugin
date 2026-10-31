package com.shadowHunterRolesPlugin.roleComponent.builtin;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 默认特殊值惩罚的**表现层**离线契约测试（2026-10-31 需求）。
 *
 * <h2>为什么这些能离线测</h2>
 * 粒子本身要活服务端，但这次需求里"可写错且不报错"的部分是**节奏与几何**：
 * <ol>
 *   <li>两次图腾爆炸的间隔是不是真的"两下"（不是同一 tick 叠在一起）；</li>
 *   <li>翻滚菱形的闪烁周期是不是正好 0.5 秒；</li>
 *   <li>菱形是不是四个顶点（上/右/下/左）的菱形，而不是三角形/正方形；</li>
 *   <li>大黑菱形与小灰菱形是不是**反方向**翻滚（同向就失去"对转"的观感）。</li>
 * </ol>
 * 这些全部由纯函数/常量承载，逐条钉住。
 */
public class DefaultSanTEPunishmentParticlesTest {

    /** 翻滚菱形每 0.5 秒闪一次 = 10 刻。 */
    @Test
    public void diamondFlickerCadenceIsHalfASecond() {
        assertEquals("闪烁周期必须正好 0.5 秒", 10, DefaultSanTEZeroPunishment.FX_PERIOD_TICKS);
        assertEquals("0.5 秒 = 20 / 2 刻", 20 / 2, DefaultSanTEZeroPunishment.FX_PERIOD_TICKS);
    }

    /** 触发时 2 次图腾爆炸：第二次延迟必须落在第一次之后的同半秒内，才读成"两下"。 */
    @Test
    public void twoTotemBurstsHappenWithinTriggerMoment() {
        assertEquals("需求：触发时 2 次图腾爆炸", 2, DefaultSanTEZeroPunishment.TOTEM_BURST_COUNT);
        assertEquals("第二次爆炸延迟 8 刻 ≈ 0.4 秒", 8, DefaultSanTEZeroPunishment.SECOND_BURST_DELAY_TICKS);
        assertTrue("第二次必须在第一次之后", DefaultSanTEZeroPunishment.SECOND_BURST_DELAY_TICKS > 0);
        assertTrue("两次都在触发后的半秒内，观感上是连续两下",
                DefaultSanTEZeroPunishment.SECOND_BURST_DELAY_TICKS
                        < DefaultSanTEZeroPunishment.FX_PERIOD_TICKS);
    }

    /** 菱形几何：四个顶点分别在上/右/下/左；对顶点互为相反向量。 */
    @Test
    public void diamondHasFourCardinalVertices() {
        double radius = 2d;
        Vector up = DefaultSanTEZeroPunishment.diamondVertex(0, radius);
        Vector right = DefaultSanTEZeroPunishment.diamondVertex(1, radius);
        Vector down = DefaultSanTEZeroPunishment.diamondVertex(2, radius);
        Vector left = DefaultSanTEZeroPunishment.diamondVertex(3, radius);

        assertEquals("上 = +Y", radius, up.getY(), 1e-9);
        assertEquals("右 = +X", radius, right.getX(), 1e-9);
        assertEquals("下 = -Y", -radius, down.getY(), 1e-9);
        assertEquals("左 = -X", -radius, left.getX(), 1e-9);

        assertEquals("上下对顶点互为相反（X）", 0d, up.getX() + down.getX(), 1e-9);
        assertEquals("上下对顶点互为相反（Y）", 0d, up.getY() + down.getY(), 1e-9);
        assertEquals("左右对顶点互为相反（X）", 0d, right.getX() + left.getX(), 1e-9);
        assertEquals("左右对顶点互为相反（Y）", 0d, right.getY() + left.getY(), 1e-9);
    }

    /** 大黑菱形与小灰菱形**反向**翻滚（需求：相反方向），且角速度各自恒定。 */
    @Test
    public void largeAndSmallDiamondsRollInOppositeDirections() {
        assertTrue("大黑菱形正向翻滚", DefaultSanTEZeroPunishment.bigDiamondSpin(1) > 0);
        assertTrue("小灰菱形反向翻滚", DefaultSanTEZeroPunishment.smallDiamondSpin(1) < 0);

        double bigStep = DefaultSanTEZeroPunishment.bigDiamondSpin(1) - DefaultSanTEZeroPunishment.bigDiamondSpin(0);
        double smallStep = DefaultSanTEZeroPunishment.smallDiamondSpin(1) - DefaultSanTEZeroPunishment.smallDiamondSpin(0);
        assertTrue("大菱形角速度恒定且为正", bigStep > 0);
        assertTrue("小菱形角速度恒定且为负", smallStep < 0);

        //连续多帧方向不会翻转（翻滚而不是来回抖）
        for (int frame = 0; frame < 20; frame++) {
            assertTrue("大菱形第 " + frame + " 帧仍正向",
                    DefaultSanTEZeroPunishment.bigDiamondSpin(frame + 1) > DefaultSanTEZeroPunishment.bigDiamondSpin(frame));
            assertTrue("小菱形第 " + frame + " 帧仍反向",
                    DefaultSanTEZeroPunishment.smallDiamondSpin(frame + 1) < DefaultSanTEZeroPunishment.smallDiamondSpin(frame));
        }
    }
}
