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
 *       ★ <b>任一方没有角色 ⇒ {@code false}（不敌对）</b>。</li>
 *   <li>{@link #isHostile(Faction, UUID)} —— **「某个阵营」与「某个玩家」**是否敌对
 *       （判据：对方**有角色** 且 阵营不同 ⇒ 敌对；对方没角色 ⇒ **不**敌对）；
 *       由 {@code core/RoleInfoImpl#isHostileTo(UUID)} 用来回答"**我这个角色**是否与它敌对"。</li>
 *   <li>{@link #factionOf(UUID)} —— 某玩家的阵营；未选角色 / 取不到角色模板 ⇒ {@link Faction#UNKNOWN}。</li>
 *   <li>{@link #hasRole(UUID)} —— 该玩家**是否已选角色**（★ "无角色算不算敌对"的判据落点）。</li>
 * </ul>
 *
 * <p><b>★★ 「无角色」的口径</b>：**一个已选角色的玩家，与一个没选角色的玩家，视为【不敌对】**
 * ⇒ 未选角色的玩家**不会**被已有角色的技能当作敌人选中/伤害。
 * （本条是**有意变更**的口径；此前是"未选角色 ⇒ 恒敌对"。）
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
     * **该玩家是否已选角色**（★ 默认实现 = {@code factionOf(uuid) != UNKNOWN}）。
     *
     * <p><b>为什么可以这样推导</b>：{@link Faction#UNKNOWN} 是"**没有角色**"的专用值
     * —— 每个角色模板在注册期都**必须**声明一个真实阵营（`Role.Builder#faction(...)`）
     * ⇒ "阵营未知"与"没有角色"在**生产数据**里是同一件事。
     *
     * <p><b>谁读它</b>：{@code core/RoleInfoImpl} 用它把"无角色"挡在敌对判定之外
     * （见类注释的「无角色」口径）。
     *
     * <p>★ 真值转发到 {@link FactionRelation#hasRole(Faction)}（**纯静态、可离线测试**）。
     */
    default boolean hasRole(UUID player) {
        return FactionRelation.hasRole(factionOf(player));
    }

    /**
     * **两个玩家之间是否敌对**（**对称**）。
     * <p>判据：**双方都有角色** 且 **阵营不同** ⇒ 敌对；
     * ★ 任一方**没有角色** ⇒ {@code false}（不敌对）。
     */
    boolean isHostile(UUID self, UUID other);

    /**
     * **「某个阵营」与「某个玩家」是否敌对**：对方**有角色** 且 阵营与 {@code self} 不同 ⇒ 敌对；
     * ★ 对方**没有角色** ⇒ {@code false}（不敌对）。
     * <p>与原 {@code RoleInstance#isHostileTo(Faction)} 的差异只有"无角色"这一支
     * （原为恒敌对，现为不敌对）—— 见类注释的「无角色」口径。
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
