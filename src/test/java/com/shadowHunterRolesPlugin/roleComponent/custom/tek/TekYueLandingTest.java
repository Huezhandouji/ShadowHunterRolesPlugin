package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「特克」技能二（落岳·凝灰岩）**落地判据**的离线单测。
 *
 * <h2>需求</h2>
 * 「<b>要完全落地后才能释放落地攻击</b>」⇒ 落地攻击（r=5 内 15 点物理 + 1 秒眩晕 + 一层真理）
 * 只能在**人已经踩在地面上**的那一刻结算，<b>不允许在空中提前结算</b>。
 *
 * <h2>为什么值得单独测</h2>
 * 旧实现用的是"下方一格是实心就结算"，而这条判据在玩家**离地面约 1 格**时就已经成立了
 * ⇒ 症状是"还没落地就爆了"。这种"差一格"的错误在运行期看不出是 bug（有伤害、有粒子、有音效），
 * 只有把判据写成真值表才钉得住。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li>本测试覆盖两个**纯函数**：{@link TekYueSkill#isFullyLanded(boolean, boolean)}（落地真值表）
 *       与 {@link TekYueSkill#groundLevelY(int, int, java.util.function.IntPredicate)}（地面扫描）；</li>
 *   <li><b>不</b>覆盖：真实世界的方块读数、`teleport`、粒子与音效、以及 `update()` 的每刻驱动
 *       （都要服务端运行环境）。</li>
 * </ul>
 */
public class TekYueLandingTest {

    // ───────── 落地真值表：脚那格 + 下方那格 ─────────

    @Test
    public void 脚空且下方实心_才算完全落地() {
        assertTrue("站在地面上：脚那格空、下面那格实心",
                TekYueSkill.isFullyLanded(false, true));
    }

    @Test
    public void 下方实心但脚那格也实心_不算落地() {
        //人被嵌进方块里（落点被占埋）⇒ 不算"站稳"
        assertFalse(TekYueSkill.isFullyLanded(true, true));
    }

    @Test
    public void 下方是空的_不算落地_这是空中() {
        //★ 这条正是"离地面 1 格以上"的情形：还在空中，绝不能结算
        assertFalse("悬空 ⇒ 还没落地", TekYueSkill.isFullyLanded(false, false));
    }

    @Test
    public void 脚空下方也空且被嵌住_不算落地() {
        assertFalse(TekYueSkill.isFullyLanded(true, false));
    }

    @Test
    public void 只看下方实心是错的_必须同时要求脚那格为空() {
        //回归判据：把"只看第②条"这个错误写法钉死。
        //若有人把实现改回 `return belowBlockSolid;`，下面这行会变红。
        boolean feetSolid = true;
        boolean belowSolid = true;
        assertFalse("脚被嵌住时，即使下方实心也不能算落地（旧实现的错误写法）",
                TekYueSkill.isFullyLanded(feetSolid, belowSolid));
        //而"脚空 + 下方实心"才是唯一的通过组合 ⇒ 真值表只有一个 true
        int passCount = 0;
        for (boolean feet : new boolean[] { false, true }) {
            for (boolean below : new boolean[] { false, true }) {
                if (TekYueSkill.isFullyLanded(feet, below)) {
                    passCount++;
                }
            }
        }
        assertEquals("四种组合里应当只有一种算落地", 1, passCount);
    }

    // ───────── 地面扫描 ─────────

    @Test
    public void 从脚下一格开始扫_找到地面顶面() {
        //地面方块在 y=9（占 9~10）⇒ 顶面 10.0；玩家脚在 y=12 ⇒ 起始扫描块 = 11
        Set<Integer> solid = Set.of(9);
        assertEquals(10.0d, TekYueSkill.groundLevelY(11, -64, solid::contains), 1.0E-9d);
    }

    @Test
    public void 最近的地面优先_不会被更下面的地面覆盖() {
        //y=11 与 y=9 都有方块 ⇒ 应当停在最近的 11（顶面 12.0），而不是穿到 9
        Set<Integer> solid = Set.of(9, 11);
        assertEquals(12.0d, TekYueSkill.groundLevelY(12, -64, solid::contains), 1.0E-9d);
    }

    @Test
    public void 整列无实心方块_返回世界最低高度() {
        //虚空：扫到底也没有地面 ⇒ 返回 minY（随后 isFullyLanded 会判为站不住 ⇒ 不放落地攻击）
        assertEquals(-64.0d, TekYueSkill.groundLevelY(11, -64, y -> false), 1.0E-9d);
    }

    @Test
    public void 起始块本身就是实心_返回它的顶面() {
        assertEquals(12.0d, TekYueSkill.groundLevelY(11, -64, y -> true), 1.0E-9d);
    }

    @Test
    public void 扫描不会越过下界() {
        //只有低于 minY 的地方才有方块 ⇒ 扫不到，返回 minY（判据下界没被越过）
        Set<Integer> solid = Set.of(-100);
        assertEquals(-64.0d, TekYueSkill.groundLevelY(11, -64, solid::contains), 1.0E-9d);
    }

    // ───────── 组合：15 格下落的落点算得对 ─────────

    @Test
    public void 十五格下落的落点等于地面顶面() {
        //跃起后在 y=25 开始下落，地面方块在 y=9（顶面 10.0）⇒ 落点必须是 10.0
        double groundY = TekYueSkill.groundLevelY(24, -64, y -> y == 9);
        assertEquals("落点必须是地面顶面，而不是「离地 1 格」", 10.0d, groundY, 1.0E-9d);
    }
}
