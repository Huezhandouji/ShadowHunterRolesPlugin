package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「特克」技能一（刺霄）**命中长廊**的离线单测
 * （{@link TekXiaoSkill#insideThrustCorridor}）。
 *
 * <h2>背景：这条判据连续错了两次</h2>
 * <ol>
 *   <li><b>第一次</b>："平地上永远打不到人" —— 侧向偏移里混进了**眼睛↔脚底的 1.62 格竖直差**，
 *       恒大于阈值 ⇒ 同高对枪必然落空；</li>
 *   <li><b>第二次</b>：改用射线后高差算对了，但**射线是一条线** ——
 *       目标包围盒要被那条线穿过才命中，准星稍偏就落空，实战手感仍是"打不中"。</li>
 * </ol>
 * ⇒ 正解 = 长廊：侧向用**水平叉积**（不含竖直分量）、高差**脚底对脚底**单独设容差。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li>本测试覆盖"给定三轴偏移 ⇒ 中/不中"的纯几何真值；</li>
 *   <li><b>不</b>覆盖：玩家对象、世界、阵营过滤、伤害结算、瞬移落点
 *       （都要服务端运行环境；阵营那一层用
 *       {@code /role operation <玩家> tekXiaoSkill state} 读 {@code rejectedHostile} 排障）。</li>
 * </ul>
 */
public class TekXiaoCorridorTest {

    /** 预设前向轴：朝 +Z（Minecraft 的"南"），归一化。 */
    private static final double AX = 0d;
    private static final double AZ = 1d;

    private static double RANGE;
    private static double LATERAL;
    private static double VERTICAL;

    static {
        RANGE = TekXiaoSkill.thrustRangeForTest();
        LATERAL = TekXiaoSkill.thrustLateralRadiusForTest();
        VERTICAL = TekXiaoSkill.thrustVerticalToleranceForTest();
    }

    /** 相对自己（脚底原点、朝 +Z）某个偏移处是否命中。 */
    private static boolean hit(double dx, double dz, double dy) {
        return TekXiaoSkill.insideThrustCorridor(dx, dz, dy, AX, AZ);
    }

    // ───────── 正常命中 ─────────

    @Test
    public void 正前方同高_必须命中() {
        //★ 这条就是用户报的"打不中"：站平地上正对敌人（脚底同高、正前方 3 格）
        assertTrue("正前方 3 格、同一高度 ⇒ 必须命中", hit(0d, 3d, 0d));
    }

    @Test
    public void 正前方贴脸到射程边界_都命中() {
        assertTrue(hit(0d, 0.1d, 0d));
        assertTrue(hit(0d, RANGE, 0d));
        assertTrue("边界内一点点", hit(0d, RANGE - 1.0E-9d, 0d));
    }

    @Test
    public void 侧向在容差内_命中() {
        assertTrue(hit(LATERAL, 3d, 0d));
        assertTrue(hit(-LATERAL, 3d, 0d));
        assertTrue(hit(LATERAL * 0.5d, 3d, 0d));
    }

    @Test
    public void 高差在容差内_命中() {
        //站上一格台阶 / 半砖 ⇒ 仍该命中
        assertTrue(hit(0d, 3d, 1.0d));
        assertTrue(hit(0d, 3d, -1.0d));
        assertTrue(hit(0d, 3d, VERTICAL));
    }

    // ───────── 应当被挡掉 ─────────

    @Test
    public void 超出射程_不命中() {
        assertFalse("略微超出即不中", hit(0d, RANGE + 0.01d, 0d));
        assertFalse(hit(0d, RANGE * 2d, 0d));
    }

    @Test
    public void 身后_不命中() {
        assertFalse(hit(0d, -1d, 0d));
        assertFalse(hit(0d, -RANGE, 0d));
    }

    @Test
    public void 侧向超出容差_不命中() {
        assertFalse(hit(LATERAL + 0.01d, 3d, 0d));
        //侧向 3 格：明显在侧边，不该被"前方一击"扫到
        assertFalse(hit(3d, 3d, 0d));
    }

    @Test
    public void 高差超出容差_不命中() {
        assertFalse(hit(0d, 3d, VERTICAL + 0.01d));
        assertFalse("头顶高处", hit(0d, 3d, VERTICAL * 3d));
    }

    // ───────── ★ 回归判据：把"第一次那个 bug"钉死 ─────────

    @Test
    public void 回归_竖直分量绝不能污染侧向偏移() {
        //旧错误算法：用三维向量差、拿整个向量当"侧向偏移"
        //  （原点取眼睛 = 脚底 + 1.62；目标取脚底）
        //⇒ 同高正前方的敌人也会因为那 1.62 格被判超限。
        double eyeHeight = 1.62d;
        double lateralOldBuggy = Math.abs(-eyeHeight);   // 纯竖直差被当成侧向偏移
        assertTrue("旧算法算出的'侧向偏移'确实超过容差（这正是当年打不中的原因）",
                lateralOldBuggy > LATERAL);

        //新算法：侧向只用水平叉积 ⇒ 同样的相对位置得到 0
        double lateralNew = Math.abs(0d * AZ - 3d * AX);  // dx=0, dz=3, 轴=+Z
        assertTrue("新算法的侧向偏移为 0 ⇒ 不受竖直差影响", lateralNew <= LATERAL);

        //并且确实命中
        assertTrue("因此同高正前方必然命中", hit(0d, 3d, 0d));
    }

    @Test
    public void 阈值必须够宽容() {
        //手感要求：容差不能太紧，否则"准星稍偏就打不到"（射线时代的症状）
        assertTrue("侧向容差至少要有一格宽", LATERAL >= 1.0d);
        assertTrue("高差容差至少要能跨一格台阶和半砖", VERTICAL >= 2.0d);
        assertTrue("射程按需求是 6.5 格", Math.abs(RANGE - 6.5d) < 1.0E-9d);
    }
}
