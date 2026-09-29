package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 角色信息服务：聚合根（{@code core/Role}）的只读服务面。
 * <p>角色的阵营是"一个角色一份、全局静态"的属性，故归聚合根（{@code Role}）持有；而组件不能直接摸
 * {@code Role}/{@code RoleInstance}（那会把内部实现细节变成组件契约），因此由本端口提供读取与行为的唯一入口。
 * <p>取值来源 = 聚合根：{@link #faction()} 读 {@code Role#getFaction()}（不从每实例的组件状态取 —— 阵营不随实例复制）。
 *
 * <h2>阵营契约（两种判定，语义不同，不要混用）</h2>
 * <ul>
 *   <li>{@link #isHostileTo(UUID)} —— 自己是否与 {@code target} 敌对（自身相对；
 *       落点 = {@code FactionLookup#isHostile(Faction, UUID)}）。</li>
 *   <li>{@link #isHostile(UUID, UUID)} —— 两个给定玩家之间是否敌对（对称；
 *       落点 = {@code FactionLookup#isHostile(UUID, UUID)}）。两种 {@code isHostile} 的参数个数即区分点。</li>
 *   <li>{@link #hasEnemyInRange(double)} —— 自身半径内是否有敌人。</li>
 * </ul>
 *
 * <h2>判敌口径（两个维度，三个判定全都遵守）</h2>
 * <ol>
 *   <li><b>在场</b>：创造 / 旁观模式的玩家**双向都不敌对** —— 既不会被索敌选中，也不把别人当敌人
 *       （真值 = {@code platform.CombatPresence}，"自己"那一半在 {@code core/RoleInfoImpl#isHostileTo(UUID)}，
 *       "对方"那一半在关系表内）。</li>
 *   <li><b>阵营</b>：双方都有角色且阵营相同才不敌对；阵营不同、或**任一方没有角色实例**（{@link Faction#UNKNOWN}）
 *       一律敌对（真值 = {@code platform.FactionRelation}）。</li>
 * </ol>
 * 效果：未选角色的玩家会被技能当作敌人选中 / 伤害（自动索敌、"附近是否有敌人"同理）。
 * <p><b>「无角色」与本条的历史沿革（有意变更）</b>：本口径 = "没有角色算敌人"；
 * 中间曾短暂改为"没有角色则不敌对"（判据落点 {@code FactionLookup#hasRole(UUID)}），现按前者的口径。
 *
 * <p><b>阵营判定与平台侧「同步形态」</b>：本端口与 {@code platform/FactionLookup} 的判定同构
 * —— UUID 版是主口径，{@code Player} 版（{@link #isHostile(Player)}）是弃用 + 纯委托
 * （{@code victim.getUniqueId()} → UUID 版），因此语义在编译期就等价，不可能漂移。
 *
 * <p><b>默认实现的能力边界（如实申报）</b>：下面两个默认方法只为第三方既有实现保留源码兼容，
 * 它们拿不到平台关系表，"在场"那一维只有 {@link #isHostileTo(UUID)} 里能看到在线玩家对象的那一半
 * （远端离线时连玩家对象都没有）；生产实现（{@code core/RoleInfoImpl}）覆写了三个判定并把两个维度
 * 都接上，因此**生产路径不受本限制影响**。
 */
public interface RoleInfoPort {

    /** 角色 id（聚合根的身份；{@code Role} 只带 Id 的那一部分）。 */
    String id();

    /** 角色描述（纯文本，多行以 {@code '\n'} 连接；无描述 → 空串）。 */
    String description();

    /** 该角色所属阵营（读聚合根；不随实例复制）。 */
    Faction faction();

    /**
     * 自己是否与 {@code target} 敌对（自身相对）：对方在场（非创造 / 旁观）且
     * （没有角色，或阵营与自身不同）则为敌对。
     *
     * <p>判定不需要 {@code target} 在线 —— 阵营关系表（{@code platform.FactionLookup}）本身就按 UUID 查询；
     * 离线者读不到游戏模式 ⇒ 按"在场"处理（只有阵营维度能判）。
     *
     * <p>默认实现 = 委托 {@link #isHostile(Player)}（本方法纯加性，第三方既有实现无需改动即可编译），
     * 且自己这一侧的"在场"判定不在默认实现里（见类注释的「默认实现的能力边界」）；
     * 实现类（如 {@code core/RoleInfoImpl}）覆写为直连关系表更佳。
     *
     * @param target 对方玩家 UUID（{@code null} → {@code false}）
     */
    default boolean isHostileTo(UUID target) {
        Player online = target == null ? null : org.bukkit.Bukkit.getPlayer(target);
        return online != null && isHostile(online);
    }

    /**
     * 两个给定玩家之间是否敌对（对称：{@code a,b} 与 {@code b,a} 同值）。
     *
     * <p>判据：两侧都在场（非创造 / 旁观）且"都有角色且阵营相同"才不敌对；其余一律敌对
     * （含"任一方没有角色"）。见类注释的「判敌口径」。
     * <p>与 {@link #isHostileTo(UUID)} 的区别：本方法与本角色无关（只是借用平台关系表做一次
     * 两个玩家之间的判定）；要问"我是否与它敌对"用 {@link #isHostileTo(UUID)}。
     *
     * <p>默认实现 = 把 {@code first} 解析成在线玩家，再委托 {@link #isHostileTo(UUID)}，
     * 因此离线者回 {@code false}，且只在 {@code first} 一侧做了在场判定 —— 它仅在"本角色就是
     * {@code first} 之一"时与直连关系表等价；实现类覆写为直连
     * {@code FactionLookup#isHostile(UUID, UUID)} 更准确（任意两个玩家都可判，两侧的在场判定都在表内）。
     *
     * @param first  玩家一 UUID（{@code null} → {@code false}）
     * @param second 玩家二 UUID（{@code null} → {@code false}）
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

    /**
     * 半径内是否有敌人（几何 + 判敌口径逐字沿用原实现）：**没有角色的玩家算敌人**，
     * 创造 / 旁观者不算（那道判定在 {@link #isHostileTo(UUID)} 与关系表里，本方法不另行过滤）。
     */
    boolean hasEnemyInRange(double radius);
}
