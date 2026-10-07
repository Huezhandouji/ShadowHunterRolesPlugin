package com.shadowHunterRolesPlugin.platform;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁住「在场」维度的真值：创造 / 旁观模式不参与敌对判定（双向）。
 *
 * <p>真值抽在 {@link CombatPresence} 里（纯静态、只读 {@link GameMode} 纯枚举与玩家对象上的
 * 一个只读字段），生产接线（{@link FactionManager}，它取在线玩家对象后转发到本类）
 * 转发到它，因此本测试校验的是生产真值本身。
 * <p>本件只传枚举 / {@code null}，不构造玩家对象、不触服务端，离线可跑。
 * <p>与 {@link FactionRelationTest} 的分工：那边管"阵营"维度，本件管"在场"维度；
 * 两个维度的合成（在场 && 阵营 ⇒ 是否敌对）落在生产接线里，不在本件范围。
 */
public class CombatPresenceTest {

    /** 创造 / 旁观 = 不在场（不参与敌对判定）。 */
    @Test
    public void creativeAndSpectatorDoNotParticipate() {
        assertFalse("创造模式不在场", CombatPresence.participates(GameMode.CREATIVE));
        assertFalse("旁观模式不在场", CombatPresence.participates(GameMode.SPECTATOR));
    }

    /** 生存 / 冒险 = 在场。 */
    @Test
    public void survivalAndAdventureParticipate() {
        assertTrue("生存模式在场", CombatPresence.participates(GameMode.SURVIVAL));
        assertTrue("冒险模式在场", CombatPresence.participates(GameMode.ADVENTURE));
    }

    /**
     * 枚举为 {@code null} ⇒ 按"在场"处理（查不到就按常规阵营口径判）。
     * <p>同时钉住这条边界的两侧：{@code null} 不是"不在场"。
     */
    @Test
    public void nullModeCountsAsParticipating() {
        assertTrue("查不到模式 ⇒ 交由阵营口径", CombatPresence.participates((GameMode) null));
    }

    /**
     * 玩家对象为 {@code null}（离线 / 该 UUID 不在线）⇒ 按"在场"处理
     * —— "任意两个玩家都可判（含离线者）"这条既有契约由此保持。
     */
    @Test
    public void offlinePlayerCountsAsParticipating() {
        assertTrue("离线者读不到模式 ⇒ 按在场处理", CombatPresence.participates((Player) null));
    }
}
