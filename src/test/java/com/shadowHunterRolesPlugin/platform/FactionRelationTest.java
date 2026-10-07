package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁住「阵营关系」的真值表，特别是「没有角色也算敌人」这条口径。
 *
 * <p>真值抽在 {@link FactionRelation} 里（纯静态、零平台依赖），生产实现转发到它，
 * 因此本测试校验的是生产真值本身，而不是复述一份可能漂移的语义。
 * <p>本件只碰 {@link Faction}（纯枚举）与静态方法，不触 Bukkit、不需要服务端，离线可跑。
 * <p><b>本件只管"阵营"这一维</b>：另一维是"在场"（创造 / 旁观不参与敌对判定），
 * 真值 = {@link CombatPresence}，判别力在 {@link CombatPresenceTest}；两者的合成=生产接线。
 */
public class FactionRelationTest {

    // ───────── 核心口径：「无角色」也是敌人 ─────────

    /** 有角色 vs 没角色，两个方向都敌对（对称）。 */
    @Test
    public void aRolelessPlayerIsHostileToARoleHolder() {
        // 有角色者 → 没角色者
        assertTrue("有角色者应把没角色者当敌人",
                FactionRelation.isHostileTo(Faction.SHADOW, Faction.UNKNOWN));
        // 没角色者 → 有角色者
        assertTrue("没角色者也应把有角色者当敌人",
                FactionRelation.isHostileTo(Faction.UNKNOWN, Faction.SHADOW));
        // 两 UUID 口径（对称）
        assertTrue("两 UUID 口径：一方没角色 ⇒ 敌对",
                FactionRelation.isHostile(Faction.SHADOW, Faction.UNKNOWN));
        assertTrue("两 UUID 口径：反向同样敌对（对称）",
                FactionRelation.isHostile(Faction.UNKNOWN, Faction.SHADOW));
    }

    /** 双方都没角色 ⇒ 敌对（"没有角色也算敌人"的极端情形：不能因为两边都没角色就互不敌对）。 */
    @Test
    public void twoRolelessPlayersAreHostile() {
        assertTrue(FactionRelation.isHostile(Faction.UNKNOWN, Faction.UNKNOWN));
        assertTrue(FactionRelation.isHostileTo(Faction.UNKNOWN, Faction.UNKNOWN));
    }

    /** 有角色 + 阵营不同 ⇒ 敌对（既有口径，不得改坏）。 */
    @Test
    public void differentFactionsAreHostile() {
        assertTrue("SHADOW vs HUNTER 应敌对", FactionRelation.isHostile(Faction.SHADOW, Faction.HUNTER));
        assertTrue("HUNTER vs SHADOW 应敌对", FactionRelation.isHostile(Faction.HUNTER, Faction.SHADOW));
        assertTrue("自身相对口径同样敌对",
                FactionRelation.isHostileTo(Faction.SHADOW, Faction.HUNTER));
    }

    /** 有角色 + 同阵营 ⇒ 不敌对（既有口径）：这是本真值表里**唯一**的 false 情形。 */
    @Test
    public void sameFactionIsNotHostile() {
        assertFalse(FactionRelation.isHostile(Faction.SHADOW, Faction.SHADOW));
        assertFalse(FactionRelation.isHostile(Faction.HUNTER, Faction.HUNTER));
        assertFalse(FactionRelation.isHostileTo(Faction.HUNTER, Faction.HUNTER));
    }

    // ───────── hasFaction：判据落点 ─────────

    /** {@code UNKNOWN} / {@code null} 视为没有阵营；两个真实阵营视为有阵营。 */
    @Test
    public void hasFactionTreatsUnknownAndNullAsFactionless() {
        assertTrue("SHADOW 是有阵营", FactionRelation.hasFaction(Faction.SHADOW));
        assertTrue("HUNTER 是有阵营", FactionRelation.hasFaction(Faction.HUNTER));
        assertFalse("UNKNOWN 就是「没有阵营」（含组件缺失那一档）", FactionRelation.hasFaction(Faction.UNKNOWN));
        assertFalse("null 视为没有阵营", FactionRelation.hasFaction(null));
    }

    // ───────── 边界与不变量 ─────────

    /** {@code null} 一律按"没有阵营"处理，与 {@code UNKNOWN} 同结果（敌对）。 */
    @Test
    public void nullFactionIsTreatedAsFactionless() {
        assertTrue(FactionRelation.isHostile(null, Faction.SHADOW));
        assertTrue(FactionRelation.isHostile(Faction.SHADOW, null));
        assertTrue(FactionRelation.isHostileTo(Faction.SHADOW, null));
        assertTrue(FactionRelation.isHostileTo(null, null));
    }

    /**
     * 两 UUID 口径必须对称：{@code isHostile(a,b) == isHostile(b,a)}。
     * <p>穷举 3×3 = 9 种组合（含 {@code UNKNOWN}），对称性破一次就红。
     */
    @Test
    public void twoUuidVerdictIsSymmetricForEveryCombination() {
        Faction[] all = {Faction.SHADOW, Faction.HUNTER, Faction.UNKNOWN};
        for (Faction a : all) {
            for (Faction b : all) {
                assertEquals("对称性破了：" + a + " / " + b,
                        FactionRelation.isHostile(a, b), FactionRelation.isHostile(b, a));
            }
        }
    }

    /**
     * 全真值表（写死 9 行，作为"口径快照"）：
     * <pre>
     *           对方 SHADOW  HUNTER  UNKNOWN
     * 自己 SHADOW      否      是      是
     *      HUNTER      是      否      是
     *      UNKNOWN     是      是      是
     * </pre>
     * 与"有角色且有阵营差异"的旧真值表只差 {@code UNKNOWN} 那一行/列：
     * 现在"没角色"落在**敌对**那一侧（此前是"没角色 ⇒ 不敌对"，属有意变更）。
     * <p>{@code isHostileTo} 与 {@code isHostile} 同值（同一实现，见该方法的 javadoc），
     * 因此本表对两个方法都成立。
     */
    @Test
    public void fullTruthTableForTwoUuidVerdict() {
        boolean[][] expected = {
                //  SHADOW, HUNTER, UNKNOWN
                {   false,  true,   true  },   // 自己 SHADOW
                {   true,   false,  true  },   // 自己 HUNTER
                {   true,   true,   true  },   // 自己 UNKNOWN（没角色 = 敌人）
        };
        Faction[] order = {Faction.SHADOW, Faction.HUNTER, Faction.UNKNOWN};
        for (int i = 0; i < order.length; i++) {
            for (int j = 0; j < order.length; j++) {
                assertEquals("真值表不符：" + order[i] + " → " + order[j],
                        expected[i][j], FactionRelation.isHostile(order[i], order[j]));
                assertEquals("自身相对口径必须与两 UUID 口径同值：" + order[i] + " → " + order[j],
                        expected[i][j], FactionRelation.isHostileTo(order[i], order[j]));
            }
        }
    }
}
