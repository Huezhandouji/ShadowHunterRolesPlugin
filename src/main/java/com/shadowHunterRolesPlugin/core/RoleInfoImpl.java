package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.RoleInfoPort;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * {@link RoleInfoPort} 的独立适配器：持 {@code RoleInstance}，与
 * {@code *PortImpl} 家族同形。
 * <p><b>阵营判定与平台侧「同步形态」</b>：本类与 {@code platform/FactionLookup} 的两支同构 ——
 * UUID 版是主口径（直连关系表），`Player` 版是弃用 + 纯委托
 * （{@code victim.getUniqueId()} → UUID 版），因此两侧语义在编译期就等价，不可能漂移。
 * <p><b>取值一律经聚合根</b>：{@link #faction()} 读 {@code Role#getFaction()}
 * （不是读某个组件实例的字段，阵营"一个角色一份、全局静态"）。
 * <p><b>两个行为照搬原阵营组件</b>（{@code isHostile} / {@code hasEnemyInRange}）—— 逐字等价，
 * 只把"自己的阵营"与"自己的位置"的取值路径改经本适配器持有的容器。
 * <p><b>判敌口径（两个维度，与平台侧同一份真值）</b>：① 在场 —— 创造 / 旁观者双向都不敌对；
 * ② 阵营 —— 双方都有角色且同阵营才不敌对，**任一方没有角色一律敌对**。自己"不在场"的那一半在
 * {@link #isHostileTo(UUID)} 里挡（对方不在场的那一半在关系表内），因此三个判定同口径。
 * <p>本端口现在是阵营读取的唯一入口（{@link #faction()} 读聚合根
 * {@code Role#getFaction()}），且不带写面（写侧在 {@code Role#setFaction/resetFaction}）。
 */
final class RoleInfoImpl implements RoleInfoPort {

    private final RoleInstance owner;

    RoleInfoImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public String id() {
        Role role = owner.getRole();
        return role == null ? null : role.getId();
    }

    @Override
    public String description() {
        Role role = owner.getRole();
        if (role == null) {
            return "";
        }
        List<Component> lines = role.getDescription();
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Component line : lines) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return joined.toString();
    }

    @Override
    public Faction faction() {
        Role role = owner.getRole();
        return role == null ? Faction.UNKNOWN : role.getFaction();
    }

    /**
     * 自己是否与 {@code target} 敌对（自身相对）：直连平台关系表
     * （{@code platform.FactionLookup#isHostile(Faction, UUID)}）——
     * 不经过"把 UUID 解析成在线玩家"这一步，因此判定与对方是否在线无关。
     *
     * <p><b>自己"不在场"的那一半在这里挡</b>：创造 / 旁观模式的玩家不参与敌对判定
     * （{@code FactionLookup#participatesInHostility}，真值 = {@code platform.CombatPresence}）；
     * 对方"不在场"的那一半在关系表内部挡（关系表看得到对方的玩家对象）。
     */
    @Override
    public boolean isHostileTo(UUID target) {
        if (target == null) {
            return false;
        }
        Player self = owner.getPlayer();
        if (self != null && !lookup().participatesInHostility(self.getUniqueId())) {
            return false;
        }
        return lookup().isHostile(faction(), target);
    }

    /**
     * 两个给定玩家之间是否敌对（对称）：直连平台关系表的两 UUID 口径
     * （{@code platform.FactionLookup#isHostile(UUID, UUID)}）——
     * 与本角色无关，任意两个玩家都可判（含离线者）；两侧的"在场"判定都在关系表内部
     * （本方法不引入"自己"这一侧，因此这里没有额外过滤）。
     */
    @Override
    public boolean isHostile(UUID first, UUID second) {
        return first != null && second != null && lookup().isHostile(first, second);
    }

    /**
     * @deprecated 改用 {@link #isHostileTo(UUID)}
     * （本方法只是 {@code isHostileTo(victim.getUniqueId())}，与平台侧的弃用支同形）。
     */
    @Deprecated
    @Override
    public boolean isHostile(Player victim) {
        return victim != null && isHostileTo(victim.getUniqueId());
    }

    @Override
    public boolean hasEnemyInRange(double radius) {
        Location loc = owner.getPlayer() == null ? null : owner.getPlayer().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        for (Player p : loc.getNearbyPlayers(radius)) {
            if (p == null) {
                continue;
            }
 //判敌口径全在关系表 + isHostileTo 两层里，本方法不自己持有语义：
 //  没有角色的玩家**算敌人**（因此这里不再有"未选角色就跳过"的过滤）；
 //  创造 / 旁观者不算敌人 —— 那道判定由 isHostileTo（自己那一半）与关系表（对方那一半）挡掉。
            if (isHostileTo(p.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

 /** 关系表（平台侧静态数据；不随实例复制）。 */
    private FactionLookup lookup() {
        return owner.rolesContext().factions();
    }
}
