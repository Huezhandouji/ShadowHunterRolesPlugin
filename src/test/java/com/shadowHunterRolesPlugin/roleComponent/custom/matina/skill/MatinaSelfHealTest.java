package com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 马提娜**「技能能不能治疗自己」**的离线契约（2026-10-17 修复）。
 *
 * <h2>修的是什么</h2>
 * 海晶灯（2 号栏位）旧实现在遍历目标时第一行就
 * {@code candidate.equals(caster) → continue}，把施法者自己排除在治疗名单外 ⇒
 * 「洒出药物」永远治不到自己。无人机（红石）那条路虽然靠"自己与自己是同阵营"侥幸能治到，
 * 但语义上是**巧合**（依赖阵营判定的边界），一并改为显式口径。
 *
 * <h2>钉住的口径（纯函数，不需要服务端）</h2>
 * <ul>
 *   <li><b>自己恒为治疗</b>：哪怕阵营判定把自家人算成敌对，也绝不给自身上三重减益；</li>
 *   <li>其余按阵营：非敌对 ⇒ 治疗；敌对 ⇒ 减益 / 中毒。</li>
 * </ul>
 *
 * <h2>覆盖边界（如实申报）</h2>
 * 只覆盖"目标分类"这一步；{@code getNearbyPlayers} 的枚举、{@code vitals.heal} 的真实结算
 * 都要服务端，离线测不到 —— 这一条靠代码复核（两个技能都走同一个 {@code isHealTarget}）。
 */
public class MatinaSelfHealTest {

    // ───────── 海晶灯（技能之一 / 2 号栏位）─────────

    /** 海晶灯：自己**永远**走治疗分支（自伤屏障）。 */
    @Test
    public void 海晶灯_自己恒为治疗() {
        assertTrue("自己 + 非同阵营 ⇒ 仍然治疗（绝不能给自己上三重减益）",
                MatinaSeaCrystalLampSkill.isHealTarget(true, false));
        assertTrue("自己 + 被误判为敌对 ⇒ 仍然治疗（自伤屏障）",
                MatinaSeaCrystalLampSkill.isHealTarget(true, true));
    }

    /** 海晶灯：其他角色按阵营分流。 */
    @Test
    public void 海晶灯_他人按阵营分流() {
        assertTrue("同阵营 ⇒ 治疗", MatinaSeaCrystalLampSkill.isHealTarget(false, false));
        assertFalse("敌对 ⇒ 减益", MatinaSeaCrystalLampSkill.isHealTarget(false, true));
    }

    /** 海晶灯的洒药半径 = 8 格（修复不能顺手改掉范围）。 */
    @Test
    public void 海晶灯_半径与敌意半径同源() {
        assertEquals(8.0d, MatinaSeaCrystalLampSkill.EFFECT_RADIUS, 1.0E-9d);
        assertEquals("需求没给第二半径 ⇒ 敌意与治疗共用同一个范围",
                MatinaSeaCrystalLampSkill.EFFECT_RADIUS, MatinaSeaCrystalLampSkill.ENEMY_RADIUS, 1.0E-9d);
    }

    // ───────── 红石无人机（技能之二 / 3 号栏位）─────────

    /** 无人机：自己**永远**走治疗分支（每秒 4 点自我治疗的前提）。 */
    @Test
    public void 无人机_自己恒为治疗() {
        assertTrue("自己 + 非敌对 ⇒ 治疗", MatinaRedstoneDroneSkill.isHealTarget(true, false));
        assertTrue("自己 + 被误判为敌对 ⇒ 仍然治疗（自伤屏障）",
                MatinaRedstoneDroneSkill.isHealTarget(true, true));
    }

    /** 无人机：其他角色按阵营分流（敌对 ⇒ 中毒）。 */
    @Test
    public void 无人机_他人按阵营分流() {
        assertTrue("同阵营 ⇒ 治疗", MatinaRedstoneDroneSkill.isHealTarget(false, false));
        assertFalse("敌对 ⇒ 中毒", MatinaRedstoneDroneSkill.isHealTarget(false, true));
    }
}
