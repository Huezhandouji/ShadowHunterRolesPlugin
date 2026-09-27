package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * **角色信息服务**：聚合根（{@code core/Role}）的**只读服务面**。
 * <p><b>为什么需要它</b>：角色的**阵营**是"一个角色一份、全局静态"的属性 ⇒ 按 ADR-0006 的通用判据
 * 归**聚合根**（{@code Role}）持有；而组件不能直接摸 {@code Role}/{@code RoleInstance}（那会把
 * 内部实现细节变成组件契约）⇒ 由本端口提供**读取与行为**的唯一入口。
 * <p><b>取值来源 = 聚合根</b>：{@link #faction()} 读 {@code Role#getFaction()}
 * （**不**从每实例的组件状态取 —— 阵营不再随实例复制）。
 *
 * <h2>★ 阵营契约（两种判定，语义不同，不要混用）</h2>
 * <ul>
 *   <li>{@link #isHostileTo(UUID)} —— **自己**是否与 {@code target} 敌对（**自身相对**；
 *       落点 = `FactionLookup#isHostile(Faction, UUID)`）。</li>
 *   <li>{@link #isHostile(UUID, UUID)} —— **两个给定玩家之间**是否敌对（**对称**；
 *       落点 = `FactionLookup#isHostile(UUID, UUID)`）★ 两种 {@code isHostile} 的**参数个数**即区分点。</li>
 *   <li>{@link #hasEnemyInRange(double)} —— 自身半径内是否有敌人。</li>
 * </ul>
 *
 * <p><b>阵营判定与平台侧「同步形态」</b>：本端口与 {@code platform/FactionLookup} 的判定**同构**
 * —— UUID 版是**主口径**，`Player` 版（{@link #isHostile(Player)}）是**弃用 + 纯委托**
 * （{@code victim.getUniqueId()} → UUID 版）⇒ 语义在**编译期**就等价，不可能漂移 ✓。
 */
public interface RoleInfo {

    /** 角色 id（聚合根的身份；{@code Role} 只带 Id 的那一部分）。 */
    String id();

    /** 角色描述（**纯文本**，多行以 {@code '\n'} 连接；无描述 ⇒ 空串）。 */
    String description();

    /** 该角色所属阵营（读聚合根；不随实例复制）。 */
    Faction faction();

    /**
     * **自己是否与 {@code target} 敌对**（自身相对）：对方未选角色 / 取不到阵营（UNKNOWN）
     * 或与自身阵营不同 ⇒ 敌对；自身阵营为 UNKNOWN ⇒ 恒敌对。
     *
     * <p>判定**不需要在线玩家对象** —— 阵营关系表（{@code platform.FactionLookup}）本身就按 UUID 查询 ✓。
     *
     * <p>★ **默认实现 = 委托 {@link #isHostile(Player)}** ⇒ 本方法**纯加性**，第三方既有实现
     * **无需改动**即可编译；实现类（如 {@code core/RoleInfoImpl}）覆写为**直连关系表**更佳。
     *
     * @param target 对方玩家 UUID（{@code null} ⇒ {@code false}）
     */
    default boolean isHostileTo(UUID target) {
        Player online = target == null ? null : org.bukkit.Bukkit.getPlayer(target);
        return online != null && isHostile(online);
    }

    /**
     * **两个给定玩家之间是否敌对**（**对称**：{@code a,b} 与 {@code b,a} 同值）。
     *
     * <p>判据：两侧阵营不同，或任一方无阵营（UNKNOWN）⇒ 敌对。
     * <p>★ 与 {@link #isHostileTo(UUID)} 的区别：本方法**与本角色无关**（只是借用平台关系表做一次
     * 两个玩家之间的判定）；要问"**我**是否与它敌对"请用 {@link #isHostileTo(UUID)}。
     *
     * <p><b>默认实现 = 委托 {@link #isHostileTo(UUID)} 的对称化</b>（{@code first} 侧判 {@code second}
     * 且 {@code second} 侧判 {@code first}）—— 这在**自角色就是 first 之一**时与直连关系表等价；
     * 实现类覆写为**直连** {@code FactionLookup#isHostile(UUID, UUID)} 更准确（**任意两个玩家**都可判）。
     *
     * @param first  玩家一 UUID（{@code null} ⇒ {@code false}）
     * @param second 玩家二 UUID（{@code null} ⇒ {@code false}）
     */
    default boolean isHostile(UUID first, UUID second) {
        if (first == null || second == null) {
            return false;
        }
        Player online = org.bukkit.Bukkit.getPlayer(first);
        return online != null && isHostileTo(second);
    }

    /**
     * @deprecated 改用 {@link #isHostileTo(UUID)}（本方法只是
     * {@code victim != null && isHostileTo(victim.getUniqueId())}）。
     */
    @Deprecated
    boolean isHostile(Player victim);

    /** 半径内是否有敌人（几何 + 阵营语义逐字沿用原实现）。 */
    boolean hasEnemyInRange(double radius);
}
