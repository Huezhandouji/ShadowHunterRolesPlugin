package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * **战斗状态组件的纯逻辑闸门**（2026-10-31 需求：修改进入战斗 / 脱离战斗的框架）。
 *
 * <p>离线单测不碰 Bukkit 玩家：本组件把全部时间判定放在自己的 {@code tickCounter} 上，
 * 因此"6 秒脱战 / 3 秒回血间隔 / 阵营恢复表"都可以直接穷举边界。
 * 需要游戏内才成立的部分（每刻邻敌检测、真实恢复落地、伤害事件归属）由
 * {@code registry/CombatStateWiringTest}（装配接线）与游戏内实测覆盖。
 */
public class CombatStateComponentTest {

    /** 空服务集：构造期只被存下来，本测试从不读它。 */
    private static ComponentServicesPort inertServices() {
        return new ComponentServicesPort(null, null, null);
    }

    private static CombatStateComponent fresh() {
        return new CombatStateComponent(CombatStateComponent.ID, inertServices(),
                new CombatStateComponent.Specification());
    }

    /** 六条规则里可穷举的四个数值边界。 */
    @Test
    public void constantsMatchTheRules() {
        assertEquals("规则 1：敌人判定半径 = 4 格", 4.0d, CombatStateComponent.ENEMY_RANGE, 0.0d);
        assertEquals("规则 6：6 秒 = 120 刻", 120, CombatStateComponent.COMBAT_MEMORY_TICKS);
        assertEquals("规则 4：回血间隔期 3 秒 = 60 刻", 60, CombatStateComponent.HEALING_INTERVAL_TICKS);
        assertEquals("脱战恢复 1 秒一跳 = 20 刻", 20, CombatStateComponent.REGEN_PERIOD_TICKS);
    }

    /** 规则 6：最后一次刷新后 < 120 刻仍在战斗，恰好 120 刻即脱战。 */
    @Test
    public void combatMemoryWindowIsSixSeconds() {
        assertTrue(CombatStateComponent.combatFromIdleTicks(0));
        assertTrue(CombatStateComponent.combatFromIdleTicks(1));
        assertTrue(CombatStateComponent.combatFromIdleTicks(119));
        assertFalse("6 秒整必须已经脱战", CombatStateComponent.combatFromIdleTicks(120));
        assertFalse(CombatStateComponent.combatFromIdleTicks(121));
        assertFalse(CombatStateComponent.combatFromIdleTicks(10_000));
    }

    /** 脱战恢复表：猎影人 4/4/5，影都 5/4/0，未知阵营不恢复。 */
    @Test
    public void outOfCombatRegenMatchesFactions() {
        CombatStateComponent.Regen hunter = CombatStateComponent.regenFor(Faction.HUNTER);
        assertEquals("猎影人 SanTE", 4, hunter.sante());
        assertEquals("猎影人 生命", 4, hunter.health());
        assertEquals("猎影人 能量", 5, hunter.energy());
        assertTrue(hunter.any());

        CombatStateComponent.Regen shadow = CombatStateComponent.regenFor(Faction.SHADOW);
        assertEquals("影都 SanTE", 5, shadow.sante());
        assertEquals("影都 生命", 4, shadow.health());
        assertEquals("影都 不给能量", 0, shadow.energy());

        CombatStateComponent.Regen unknown = CombatStateComponent.regenFor(Faction.UNKNOWN);
        assertFalse("未知阵营不恢复", unknown.any());
        assertFalse("null 按未知阵营处理", CombatStateComponent.regenFor(null).any());
    }

    /** 实例面：初始不在战斗；刷新后进入战斗；回血间隔期可开启。 */
    @Test
    public void instanceStateTransitionsWithoutServer() {
        CombatStateComponent component = fresh();
        assertNotNull(component.specification());
        assertFalse("刚创建时不在战斗", component.inCombat());
        assertFalse("刚创建时没有回血间隔期", component.healingIntervalActive());

        component.refreshCombat();
        assertTrue("刷新后进入战斗", component.inCombat());
        assertEquals("刷新当刻 idle = 0", 0, component.idleTicks());

        component.beginHealingInterval();
        assertTrue("方块伤害后进入回血间隔期", component.healingIntervalActive());

        //无阵营组件 ⇒ UNKNOWN ⇒ 恢复表全 0（不产生任何恢复）
        assertEquals(Faction.UNKNOWN, component.factionValue());
    }

    /** 跨实例静态口在"没有该玩家实例"时必须静默 no-op（不抛、不返回 true）。 */
    @Test
    public void staticAccessorsAreNullSafeForUnknownPlayers() {
        UUID absent = UUID.randomUUID();
        CombatStateComponent.refreshFor(absent);
        CombatStateComponent.beginHealingIntervalFor(absent);
        assertFalse("没有实例 ⇒ 不处于战斗", CombatStateComponent.inCombatFor(absent));
        CombatStateComponent.refreshFor(null);
        CombatStateComponent.beginHealingIntervalFor(null);
        assertFalse(CombatStateComponent.inCombatFor(null));
    }
}
