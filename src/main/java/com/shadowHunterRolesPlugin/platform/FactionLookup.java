package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 阵营查询（平台侧关系表）：取代 core 里的 {@code RoleManager.getInstance()}。
 *
 * <h2>★ 契约（两种判定，语义不同，不要混用）</h2>
 * <ul>
 *   <li>{@link #isHostile(UUID, UUID)} —— **两个玩家之间**是否敌对（**对称**：{@code a,b} 与 {@code b,a} 同值）；
 *       任一方未选角色 / 取不到阵营 ⇒ 敌对。</li>
 *   <li>{@link #isHostile(Faction, UUID)} —— **「某个阵营」与「某个玩家」**是否敌对
 *       （判据：{@code self != factionOf(other) || self == UNKNOWN}）；
 *       由 {@code core/RoleInfoImpl#isHostileTo(UUID)} 用来回答"**我这个角色**是否与它敌对"。</li>
 *   <li>{@link #factionOf(UUID)} —— 某玩家的阵营；未选角色 / 取不到角色模板 ⇒ {@link Faction#UNKNOWN}。</li>
 * </ul>
 *
 * <p><b>★ 主口径 = UUID</b>：查询**不需要在线玩家对象** —— 角色的阵营真值在聚合根
 * （{@code Role#getFaction()}），{@code RoleManager} 按 UUID 也能取到实例
 * ⇒ 平台层不必为"读一个阵营"而持有一个 {@link Player} ✓。
 *
 * <p><b>{@link Player} 支已弃用</b>：保留只为**第三方源码兼容**，实现都是**纯委托**
 * （{@code player.getUniqueId()}）⇒ 与 UUID 版**逐字等价**。新代码一律用 UUID 版。
 */
public interface FactionLookup {

    /** 某玩家的阵营（按 UUID）；未选角色 / 取不到角色模板 ⇒ {@link Faction#UNKNOWN}。 */
    Faction factionOf(UUID player);

    /**
     * **两个玩家之间是否敌对**（**对称**）。
     * <p>判据：{@code otherFaction != selfFaction || selfFaction == UNKNOWN || otherFaction == UNKNOWN}
     * —— 即「阵营不同」或「任一方无阵营」⇒ 敌对 ✓。
     */
    boolean isHostile(UUID self, UUID other);

    /**
     * **「某个阵营」与「某个玩家」是否敌对**：对方阵营与 {@code self} 不同 ⇒ 敌对；
     * {@code self == UNKNOWN} ⇒ 恒敌对。
     * <p>与原 {@code RoleInstance#isHostileTo(Faction)} 的语义一致
     * （{@code core/RoleInfoImpl#isHostileTo(UUID)} 的直接落点）。
     */
    boolean isHostile(Faction self, UUID other);

    /**
     * @deprecated 改用 {@link #factionOf(UUID)}（本方法只是 {@code factionOf(player.getUniqueId())}）。
     */
    @Deprecated
    default Faction factionOf(Player player) {
        return player == null ? Faction.UNKNOWN : factionOf(player.getUniqueId());
    }

    /**
     * @deprecated 改用 {@link #isHostile(UUID, UUID)} 或 {@link #isHostile(Faction, UUID)}
     * （本方法只是 {@code isHostile(self, other.getUniqueId())}）。
     */
    @Deprecated
    default boolean isHostile(Faction self, Player other) {
        return other != null && isHostile(self, other.getUniqueId());
    }
}
