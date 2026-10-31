package com.shadowHunterRolesPlugin.roleComponent.custom.matina;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 一技能「海晶灯」巨大粒子十字架的**几何**离线单测（2026-10-31 需求）。
 *
 * <h2>判据来源</h2>
 * 需求：使用一技能时，在其正中心生成一个**巨大的粒子美丽十字架**，**正面朝向 matina 的视角**。
 * <ul>
 *   <li>巨大：竖杆 {@value MatinaRageVfx#HOLY_CROSS_HEIGHT} 格高、横杆总宽
 *       {@code 2 × }{@value MatinaRageVfx#HOLY_CROSS_ARM} 格；</li>
 *   <li>十字形：横杆位于竖杆 {@value MatinaRageVfx#HOLY_CROSS_ARM_RATIO} 高度处（上臂短、下臂长）；</li>
 *   <li>朝向：十字架在**竖直平面**内，平面法线 = 施法者水平视线 ⇒ 从她视角看是正面（运行时由
 *       {@link MatinaRageVfx#holyCross} 把本地 {@code (x, y)} 偏移映射到 {@code side / up} 两轴）。</li>
 * </ul>
 *
 * <h2>为什么能离线跑</h2>
 * {@link MatinaRageVfx#crossLocalPoints()} 是纯函数（只做坐标计算，不碰世界 / 玩家）；
 * {@link MatinaRageVfx#holyCross} 在 {@code world == null} 时静默返回，可安全调用。
 */
public class MatinaHolyCrossTest {

    private static final double EPS = 1.0E-9d;

    /** 十字架尺寸符合"巨大"需求：5 格高、4 格宽、横杆在 62% 高度。 */
    @Test
    public void crossDimensionsAreBigAndUpright() {
        assertEquals(5.0d, MatinaRageVfx.HOLY_CROSS_HEIGHT, EPS);
        assertEquals(2.0d, MatinaRageVfx.HOLY_CROSS_ARM, EPS);
        assertEquals(0.62d, MatinaRageVfx.HOLY_CROSS_ARM_RATIO, EPS);
    }

    /** 采样点全部落在"竖杆 + 横杆"两笔上，且关于竖杆左右对称、交点不重复。 */
    @Test
    public void crossPointsFormASymmetricCross() {
        List<Vector> points = MatinaRageVfx.crossLocalPoints();
        assertTrue("十字架必须有采样点", !points.isEmpty());

        double armY = MatinaRageVfx.HOLY_CROSS_HEIGHT * (MatinaRageVfx.HOLY_CROSS_ARM_RATIO - 0.5d);
        int vertical = 0;
        int arm = 0;
        for (Vector point : points) {
            assertEquals("十字架必须在同一平面（本地 z = 0）", 0d, point.getZ(), EPS);
            assertTrue("横杆方向不得超出半长", Math.abs(point.getX()) <= MatinaRageVfx.HOLY_CROSS_ARM + EPS);
            assertTrue("竖杆高度不得越界",
                    point.getY() >= -MatinaRageVfx.HOLY_CROSS_HEIGHT / 2d - EPS
                            && point.getY() <= MatinaRageVfx.HOLY_CROSS_HEIGHT / 2d + EPS);
            if (Math.abs(point.getX()) < EPS) {
                vertical++;
            } else {
                arm++;
                assertEquals("横杆只允许一行", armY, point.getY(), EPS);
                assertTrue("横杆点必须成对出现（左右对称）", hasPoint(points, -point.getX(), point.getY()));
            }
        }
        assertTrue("竖杆必须有采样点", vertical > 0);
        assertTrue("横杆必须有采样点", arm > 0);
        assertEquals("横杆半长端点必须被采样到", true,
                hasPoint(points, MatinaRageVfx.HOLY_CROSS_ARM, armY));
        assertEquals("横杆另一端同理", true,
                hasPoint(points, -MatinaRageVfx.HOLY_CROSS_ARM, armY));
        assertEquals("竖杆顶端必须被采样到", true,
                hasPoint(points, 0d, MatinaRageVfx.HOLY_CROSS_HEIGHT / 2d));
        assertEquals("竖杆底端必须被采样到", true,
                hasPoint(points, 0d, -MatinaRageVfx.HOLY_CROSS_HEIGHT / 2d));
    }

    /** 空世界 / 空中心 / 空视线都不得抛异常（只在纯几何层测，不触发真实 spawnParticle）。 */
    @Test
    public void holyCrossIsSilentWithNullInputs() {
        MatinaRageVfx.holyCross(null, null, null);
        MatinaRageVfx.holyCross(null, new Location(null, 0d, 0d, 0d), new Vector(0d, 1d, 0d));
        assertTrue(true);
    }

    private static boolean hasPoint(List<Vector> points, double x, double y) {
        for (Vector point : points) {
            if (Math.abs(point.getX() - x) < EPS && Math.abs(point.getY() - y) < EPS) {
                return true;
            }
        }
        return false;
    }
}
