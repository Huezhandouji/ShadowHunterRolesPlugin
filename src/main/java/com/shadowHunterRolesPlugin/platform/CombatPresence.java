package com.shadowHunterRolesPlugin.platform;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * 敌对判定的「在场」维度：创造 / 旁观模式的玩家<b>不被当作目标</b>（不被索敌选中、不被打）。
 *
 * <p><b>★ 只读目标方（2026 语义变更）</b>：本维度在合成判定（{@link Hostility}）里
 * <b>只作用于目标方</b> —— "对方是创造 / 旁观 ⇒ 非敌对"。发起方自己处于创造 / 旁观时
 * <b>不再豁免</b>（旧口径是"创造 / 旁观者<b>双向</b>都不敌对"，已作废）。
 *
 * <p>本类是那一维度的唯一真值，与 {@link FactionRelation} 同族、同写法：
 * 一个是「阵营」维度的真值（纯 {@code Faction}），一个是「在场」维度的真值（纯 {@code GameMode}）。
 * 两者都不触服务端（只读 Bukkit 的纯枚举与玩家对象上的一个只读字段）⇒ 离线可测；
 * 合成（在场 + 阵营 ⇒ 是否敌对）落在 {@link Hostility}，生产接线只做转发。
 */
public final class CombatPresence {

    private CombatPresence() {
    }

    /**
     * 该游戏模式是否「在场」（可作为敌对判定的<b>目标方</b>）。
     *
     * @param mode 游戏模式（{@code null} 视为在场 —— 查不到就按常规阵营口径判）
     * @return {@code false} = 创造 / 旁观模式（<b>作为目标</b>不被索敌选中）
     */
    public static boolean participates(GameMode mode) {
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    /**
     * 该在线玩家是否「在场」（可作为敌对判定的<b>目标方</b>）。
     *
     * <p>★ 注意：合成判定<b>只把目标方的这个读数算进去</b>；发起方自己是不是在场，
     * 不影响"我是否视他为敌人"（见 {@link Hostility}）。
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
