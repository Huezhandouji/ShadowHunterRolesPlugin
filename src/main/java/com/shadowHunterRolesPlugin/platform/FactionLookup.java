package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 阵营查询（平台侧关系表）。
 *
 * <h2>契约（两种判定，语义不同，不要混用）</h2>
 * <ul>
 *   <li>{@link #isHostile(UUID, UUID)} —— 两个玩家之间是否敌对（对称：{@code a,b} 与 {@code b,a} 同值）；</li>
 *   <li>{@link #isHostile(Faction, UUID)} —— 「某个阵营」与「某个玩家」是否敌对，
 *       由 {@code core/RoleInfoImpl#isHostileTo(UUID)} 用来回答"我这个角色是否与它敌对"；</li>
 *   <li>{@link #factionOf(UUID)} —— 某玩家的阵营；未选角色 / 取不到角色模板则为 {@link Faction#UNKNOWN}；</li>
 *   <li>{@link #hasRole(UUID)} —— 该玩家是否已选角色（"无角色"这一维的判据落点）；</li>
 *   <li>{@link #participatesInHostility(UUID)} —— 该玩家是否"在场"（创造 / 旁观 = 不在场）。</li>
 * </ul>
 *
 * <h2>判敌口径（两个维度，缺一不可）</h2>
 * <ol>
 *   <li><b>在场</b>：创造 / 旁观模式的玩家不参与敌对判定（双向都不敌对）—— 真值 = {@link CombatPresence}；</li>
 *   <li><b>阵营</b>：双方都有角色且阵营相同才不敌对；阵营不同、或任一方没有角色（{@link Faction#UNKNOWN}）
 *       一律敌对 —— 真值 = {@link FactionRelation}。</li>
 * </ol>
 * 两条判定的合成落在生产实现（插件主类的匿名实现）里，本接口只声明语义（与
 * {@link FactionRelation}、{@link CombatPresence} 两个真值类一起构成"测试校验的就是生产语义"）。
 *
 * <p><b>「无角色」的口径</b>：没有角色的玩家**算敌人**（与"同阵营"两两相对：只有
 * "都有角色且同阵营"才不敌对），因此未选角色的玩家会被已有角色的技能当作敌人选中 / 伤害。
 * 本条是有意变更的口径，此前是"未选角色则一律不敌对"。
 *
 * <p><b>「不在场」的口径</b>：创造 / 旁观者双向都不敌对 —— 既不会被索敌选中，也不把别人当敌人。
 * 离线者读不到游戏模式 ⇒ 按"在场"处理（判定交由上面的阵营维度）。
 *
 * <p><b>主口径 = UUID</b>：查询不需要在线玩家对象 —— 角色的阵营真值在聚合根
 * （{@code Role#getFaction()}），{@code RoleManager} 按 UUID 也能取到实例，
 * 平台层因此不必为"读一个阵营"而持有一个 {@link Player}（"在场"那一维才需要玩家对象）。
 *
 * <p><b>{@link Player} 支已弃用</b>：保留只为第三方源码兼容，实现都是纯委托
 * （{@code player.getUniqueId()}），与 UUID 版逐字等价。新代码一律用 UUID 版。
 */
public interface FactionLookup {

    /** 某玩家的阵营（按 UUID）；未选角色 / 取不到角色模板则为 {@link Faction#UNKNOWN}。 */
    Faction factionOf(UUID player);

    /**
     * 该玩家是否已选角色（默认实现 = {@code factionOf(uuid) != UNKNOWN}）。
     *
     * <p><b>推导依据</b>：{@link Faction#UNKNOWN} 是"没有角色"的专用值
     * —— 每个角色模板在注册期都必须声明一个真实阵营（{@code Role.Builder#faction(...)}），
     * 因此"阵营未知"与"没有角色实例"在生产数据里是同一件事。
     *
     * <p><b>谁读它</b>：仅作查询读口（敌对判定已改成"没角色 ⇒ 敌对"，不再需要它来挡人）。
     *
     * <p>真值转发到 {@link FactionRelation#hasRole(Faction)}（纯静态、可离线测试）。
     */
    default boolean hasRole(UUID player) {
        return FactionRelation.hasRole(factionOf(player));
    }

    /**
     * 该玩家是否参与敌对判定（"在场"维度）：创造 / 旁观模式 ⇒ {@code false}（双向都不敌对）。
     *
     * <p>默认实现 = 解析在线玩家后读其游戏模式（{@link CombatPresence#participates(Player)}）；
     * 离线 / {@code null} ⇒ {@code true}（读不到模式 ⇒ 交由阵营维度判，见类注释的「不在场」口径）。
     *
     * <p><b>谁读它</b>：{@code core/RoleInfoImpl#isHostileTo(UUID)} 用它挡掉"自己不在场"的那一半
     * （"对方不在场"的那一半由生产实现在下面两个 {@code isHostile} 里挡）。
     */
    default boolean participatesInHostility(UUID player) {
        return CombatPresence.participates(player == null ? null : Bukkit.getPlayer(player));
    }

    /**
     * 两个玩家之间是否敌对（对称）。
     * <p>判据：双方都"在场"（非创造 / 旁观）且"都有角色且阵营相同"才 {@code false}；其余 {@code true}。
     */
    boolean isHostile(UUID self, UUID other);

    /**
     * 「某个阵营」与「某个玩家」是否敌对：对方"在场"且（有角色时阵营与 {@code self} 不同）则为敌对；
     * 对方没有角色同样敌对（口径见类注释的「无角色」）。
     * <p>与旧口径的差异：此前"任一方没有角色"一律不敌对，现在一律敌对 —— 有意变更。
     * <p>本方法看不到"己方是否在场"（{@code self} 只是阵营）：己方的那一半由
     * {@code core/RoleInfoImpl#isHostileTo(UUID)} 挡（那里才持有玩家对象）。
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
