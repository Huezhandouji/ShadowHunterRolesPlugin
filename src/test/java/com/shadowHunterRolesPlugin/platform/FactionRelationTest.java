package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * **锁住「阵营关系」的真值表** —— 特别是 ★「**无角色 ⇒ 不敌对**」这条口径。
 *
 * <p><b>为什么测 {@link FactionRelation} 而不是测 {@code FactionLookup} 的实现</b>：
 * 真值原本住在插件主类的 {@code FactionLookup} **匿名实现**里，而主类引 {@code JavaPlugin}
 * ⇒ 离线测不了、那条口径**永远无法被覆盖**。现在真值抽成 {@link FactionRelation}（纯静态、零平台依赖），
 * 且**生产实现转发到它** ⇒ 本测试校验的就是**生产真值本身**，不是复述一份可能漂移的语义 ✓
 *
 * <p><b>离线可跑</b>：本件只碰 {@link Faction}（纯枚举）与静态方法 ⇒ 不触 Bukkit、不需要服务端 ✓
 */
public class FactionRelationTest {

    // ───────── ★★ 核心口径：「无角色」不是敌人 ─────────

    /** **有角色 vs 没角色 ⇒ 不敌对**（★ 本次变更的那一条，两个方向都要**对称**）。 */
    @Test
    public void aRolelessPlayerIsNotHostileToARoleHolder() {
        // 有角色者 → 没角色者
        assertFalse("有角色者不应把没角色者当敌人",
                FactionRelation.isHostileTo(Faction.SHADOW, Faction.UNKNOWN));
        // 没角色者 → 有角色者
        assertFalse("没角色者不应把有角色者当敌人",
                FactionRelation.isHostileTo(Faction.UNKNOWN, Faction.SHADOW));
        // 两 UUID 口径（对称）
        assertFalse("两 UUID 口径：一方没角色 ⇒ 不敌对",
                FactionRelation.isHostile(Faction.SHADOW, Faction.UNKNOWN));
        assertFalse("两 UUID 口径：反向同样不敌对（对称）",
                FactionRelation.isHostile(Faction.UNKNOWN, Faction.SHADOW));
    }

    /** **双方都没角色 ⇒ 不敌对**。 */
    @Test
    public void twoRolelessPlayersAreNotHostile() {
        assertFalse(FactionRelation.isHostile(Faction.UNKNOWN, Faction.UNKNOWN));
        assertFalse(FactionRelation.isHostileTo(Faction.UNKNOWN, Faction.UNKNOWN));
    }

    /** **有角色 + 阵营不同 ⇒ 敌对**（原有口径，不能改坏）。 */
    @Test
    public void differentFactionsAreHostile() {
        assertTrue("SHADOW vs HUNTER 应敌对", FactionRelation.isHostile(Faction.SHADOW, Faction.HUNTER));
        assertTrue("HUNTER vs SHADOW 应敌对", FactionRelation.isHostile(Faction.HUNTER, Faction.SHADOW));
        assertTrue("自身相对口径同样敌对",
                FactionRelation.isHostileTo(Faction.SHADOW, Faction.HUNTER));
    }

    /** **有角色 + 同阵营 ⇒ 不敌对**（原有口径）。 */
    @Test
    public void sameFactionIsNotHostile() {
        assertFalse(FactionRelation.isHostile(Faction.SHADOW, Faction.SHADOW));
        assertFalse(FactionRelation.isHostile(Faction.HUNTER, Faction.HUNTER));
        assertFalse(FactionRelation.isHostileTo(Faction.HUNTER, Faction.HUNTER));
    }

    // ───────── hasRole：判据落点 ─────────

    /** {@code UNKNOWN} / {@code null} ⇒ **没有角色**；两个真实阵营 ⇒ **有角色**。 */
    @Test
    public void hasRoleTreatsUnknownAndNullAsRoleless() {
        assertTrue("SHADOW 是有角色", FactionRelation.hasRole(Faction.SHADOW));
        assertTrue("HUNTER 是有角色", FactionRelation.hasRole(Faction.HUNTER));
        assertFalse("UNKNOWN 就是「没有角色」", FactionRelation.hasRole(Faction.UNKNOWN));
        assertFalse("null 视为没有角色（查不到就安全）", FactionRelation.hasRole(null));
    }

    // ───────── 边界与不变量 ─────────

    /** **{@code null} 一律按"没有角色"处理** ⇒ 与 {@code UNKNOWN} 同结果（不敌对）。 */
    @Test
    public void nullFactionIsTreatedAsRoleless() {
        assertFalse(FactionRelation.isHostile(null, Faction.SHADOW));
        assertFalse(FactionRelation.isHostile(Faction.SHADOW, null));
        assertFalse(FactionRelation.isHostileTo(Faction.SHADOW, null));
        assertFalse(FactionRelation.isHostileTo(null, null));
    }

    /**
     * **两 UUID 口径必须对称**：{@code isHostile(a,b) == isHostile(b,a)}。
     * <p>穷举 3×3 = 9 种组合（含 {@code UNKNOWN}）—— 对称性破一次就红 ✓
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
     * **全真值表**（写死 9 行，作为"口径快照"）：
     * <pre>
     *           对方 SHADOW  HUNTER  UNKNOWN
     * 自己 SHADOW      否      是      否
     *      HUNTER      是      否      否
     *      UNKNOWN     否      否      否
     * </pre>
     * ★ 与 {@code isHostileTo} 的差异只在 {@code UNKNOWN} 行：自身无角色时不再恒敌对
     * （实践不可达 —— 没角色就没有组件、跑不了技能）。
     */
    @Test
    public void fullTruthTableForTwoUuidVerdict() {
        boolean[][] expected = {
                //  SHADOW, HUNTER, UNKNOWN
                {   false,  true,   false },   // 自己 SHADOW
                {   true,   false,  false },   // 自己 HUNTER
                {   false,  false,  false },   // 自己 UNKNOWN
        };
        Faction[] order = {Faction.SHADOW, Faction.HUNTER, Faction.UNKNOWN};
        for (int i = 0; i < order.length; i++) {
            for (int j = 0; j < order.length; j++) {
                assertEquals("真值表不符：" + order[i] + " → " + order[j],
                        expected[i][j], FactionRelation.isHostile(order[i], order[j]));
            }
        }
    }
}
