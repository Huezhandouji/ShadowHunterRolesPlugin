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
 * <h2>阵营契约（两种判定，<b>非对称</b>，方向由形参顺序表达）</h2>
 * <ul>
 *   <li>{@link #isHostileTo(UUID)} —— <b>我</b>是否与 {@code target} 敌对（目标方视角）；
 *       落点 = {@code FactionLookup#isHostile(Faction, UUID)}。</li>
 *   <li>{@link #isHostile(UUID, UUID)} —— <b>first 是否视 second 为敌人</b>（发起方 = first，
 *       目标方 = second）；落点 = {@code FactionLookup#isHostile(UUID, UUID)}。
 *       ★ 两个 {@code isHostile} <b>不再同值</b>，旧的逐字对称断言已随口径变更作废。</li>
 *   <li>{@link #hasEnemyInRange(double)} —— 自身半径内是否有敌人（逐个走 {@code isHostileTo}）。</li>
 * </ul>
 *
 * <h2>★ 判敌口径（2026 语义变更；真值 = {@code platform.Hostility}）</h2>
 * <p>三条规则，<b>同阵营优先</b>：
 * <ol>
 *   <li><b>同阵营 ⇒ 任何情况下非敌对</b>（最高优先级）：双方都有角色且阵营相同，
 *       无论双方各是什么游戏模式，都不敌对；</li>
 *   <li><b>对方是创造 / 旁观 ⇒ 非敌对</b>：目标方不在场就不打他（不会被索敌选中）；</li>
 *   <li><b>其余 ⇒ 敌对</b>：对方在场且不同阵营（含"任一方没有角色"⇒ {@link Faction#UNKNOWN}）。</li>
 * </ol>
 *
 * <h3>★ 两个维度的读法（"在场"只读目标方）</h3>
 * <ul>
 *   <li><b>在场</b>：创造 / 旁观 ⇒ <b>非敌对</b>（真值 = {@code platform.CombatPresence}）。
 *       ★ <b>只读目标方</b>：己方是创造 / 旁观时<b>不再豁免</b> —— 敌人只要在场且不同阵营，
 *       判定为<b>敌对</b>。旧口径（"己方不在场 ⇒ 一律不敌对"）已作废。</li>
 *   <li><b>阵营</b>：双方都有角色且阵营相同 ⇒ 非敌对；阵营不同、或<b>任一方没有角色实例</b>
 *       （{@link Faction#UNKNOWN}）⇒ 敌对（真值 = {@code platform.FactionRelation}）。</li>
 * </ul>
 * 效果：未选角色的玩家会被技能当作敌人选中 / 伤害（自动索敌、"附近是否有敌人"同理）；
 * 旁观模式的玩家<b>不会</b>被任何一方索敌选中，但<b>他自己</b>对在场玩家而言仍是敌人。
 *
 * <p><b>★「无角色」口径</b>：本口径 = "没有角色算敌人"；中间曾短暂改为"没有角色则不敌对"，
 * 现按前者的口径。
 *
 * <p><b>★「不在场」口径的历史沿革（本次变更）</b>：旧口径 = "创造 / 旁观者<b>双向</b>都不敌对"
 * （己方那道闸门在 {@code core/RoleInfoImpl#isHostileTo}）；现口径 = <b>只挡目标方一侧</b>。
 * 两个 {@code isHostile} 因此由对称变为非对称。
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
     * <b>我</b>是否与 {@code target} 敌对（目标方视角）：对方在场（非创造 / 旁观）且
     * （没有角色，或阵营与自身不同）则为敌对。
     *
     * <p>★ <b>己方是否在场不参与</b>：自己处于创造 / 旁观时<b>不豁免</b> ——
     * {@code target} 只要在场且不同阵营，判定为敌对（旧口径的"己方不在场 ⇒ 不敌对"闸门已删除）。
     * 真值 = {@code platform.Hostility#isHostileTo}。
     *
     * <p>判定不需要 {@code target} 在线 —— 阵营关系表（{@code platform.FactionLookup}）本身就按 UUID 查询；
     * 离线者读不到游戏模式 ⇒ 按"在场"处理（只有阵营维度能判）。
     *
     * <p>默认实现 = 委托 {@link #isHostile(Player)}（本方法纯加性，第三方既有实现无需改动即可编译）；
     * 实现类（如 {@code core/RoleInfoImpl}）覆写为直连关系表。
     *
     * @param target 对方玩家 UUID（{@code null} → {@code false}）
     */
    default boolean isHostileTo(UUID target) {
        Player online = target == null ? null : org.bukkit.Bukkit.getPlayer(target);
        return online != null && isHostile(online);
    }

    /**
     * <b>{@code first} 是否视 {@code second} 为敌人</b>（<b>非对称</b>：发起方 = first，目标方 = second）。
     *
     * <p>★ 语义已变更：旧语义 = "两个给定玩家之间是否敌对（<b>对称</b>：{@code a,b} 与 {@code b,a} 同值）"。
     * 现语义下 {@code isHostile(a,b)} 与 {@code isHostile(b,a)} <b>一般不同值</b> ——
     * 因为"在场"那一维只读<b>目标方</b>。
     *
     * <p>判据：{@code second} 在场（非创造 / 旁观）且"双方都有角色且阵营相同"才不敌对；其余一律敌对
     * （含"任一方没有角色"）。{@code first} 的<b>在场状态不参与</b>。见类注释的「判敌口径」。
     * <p>与 {@link #isHostileTo(UUID)} 的区别：本方法把 {@code first} 展开成阵营，
     * 是 {@code isHostileTo(second)} 的方向显式形态。
     *
     * <p>默认实现 = 把 {@code first} 解析成在线玩家，再委托 {@link #isHostileTo(UUID)}，
     * 因此离线者回 {@code false}，且只在 {@code first} 一侧做了在场判定 —— 它仅在"本角色就是
     * {@code first} 之一"时与直连关系表等价；实现类覆写为直连
     * {@code FactionLookup#isHostile(UUID, UUID)} 更准确（任意两个玩家都可判）。
     *
     * @param first  发起方 UUID（{@code null} → {@code false}）
     * @param second 目标方 UUID（{@code null} → {@code false}）
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
     * 半径内是否有敌人（几何 + 判敌口径逐字沿用原实现）：<b>没有角色的玩家算敌人</b>，
     * <b>创造 / 旁观者不算</b>（那道判定在关系表的"对方不在场 ⇒ 不敌对"里，本方法不另行过滤）。
     *
     * <p>★ 己方是否在场<b>不再影响</b>本方法（旧口径的"自己不在场 ⇒ false"闸门已删）：
     * 己方切创造 / 旁观后，只要半径内有人在场且不同阵营，照样返回 {@code true}。
     * 注意 {@code getNearbyPlayers} 只看得到在线玩家 ⇒ 离线者永不构成本方法的"敌人"。
     */
    boolean hasEnemyInRange(double radius);
}