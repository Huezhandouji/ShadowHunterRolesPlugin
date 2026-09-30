package com.shadowHunterRolesPlugin.platform;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * 敌对判定的「在场」维度：创造 / 旁观模式的玩家**不参与敌对判定** —— 既不算别人的敌人，
 * 也不把别人当敌人（两个方向一致；口径见 {@code core/ports/RoleInfoPort}）。
 *
 * <p>本类是那一维度的唯一真值，与 {@link FactionRelation} 同族、同写法：
 * 一个是「阵营」维度的真值（纯 {@link Faction}），一个是「在场」维度的真值（纯 {@link GameMode}）。
 * 两者都不触服务端（只读 Bukkit 的纯枚举与玩家对象上的一个只读字段）⇒ 离线可测；
 * 合成（在场 + 阵营 ⇒ 是否敌对）落在生产实现 {@code ShadowHunterRolesPlugin} 的
 * {@link FactionLookup} 匿名实现里，那里也只做接线、不自己持有语义。
 */
public final class CombatPresence {

    private CombatPresence() {
    }

    /**
     * 该游戏模式是否参与敌对判定。
     *
     * @param mode 游戏模式（{@code null} 视为参与 —— 查不到就按常规阵营口径判）
     * @return {@code false} = 创造 / 旁观模式（不在局内，双向都不敌对）
     */
    public static boolean participates(GameMode mode) {
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /**
     * 该在线玩家是否参与敌对判定。
     *
     * @param player 在线玩家对象；{@code null}（离线 / 该 UUID 不在线）⇒ {@code true}
     *               —— 离线者读不到游戏模式，判定交由阵营口径（{@link FactionRelation}），
     *               因此"任意两个玩家都可判（含离线者）"这条既有契约不变
     * @return {@code false} = 创造 / 旁观模式
     */
    public static boolean participates(Player player) {
        return player == null || participates(player.getGameMode());
    }
}
