package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.RoleInfo;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * {@link RoleInfo} 的独立适配器：**持 {@code RoleInstance}**，与
 * {@code *PortImpl} 家族同形。
 * <p><b>阵营判定与平台侧「同步形态」</b>：本类与 {@code platform/FactionLookup} 的两支**同构** ——
 * UUID 版是**主口径**（直连关系表），`Player` 版是**弃用 + 纯委托**
 * （{@code victim.getUniqueId()} → UUID 版）⇒ 两侧语义在编译期就等价，不可能漂移 ✓。
 * <p><b>取值一律经聚合根</b>：{@link #faction()} 读 {@code Role#getFaction()}
 * （**不是**读某个组件实例的字段 ⇒ 阵营"一个角色一份、全局静态"）。
 * <p><b>两个行为照搬原阵营组件</b>（该组件已整体删除；它的
 * {@code isHostile} / {@code hasEnemyInRange}）—— 逐字等价，只把"自己的阵营"与"自己的位置"的
 * 取值路径改经本适配器持有的容器。
 * <p><b>（欠账 A 后半）</b>：{@code FactionComponent} **已整体删除** ⇒ 上面的
 * "照搬原组件"只作**历史沿革**读：本端口现在是**阵营读取的唯一入口** （{@link #faction()} 读聚合根
 * {@code Role#getFaction()}），且**不带写面**（写侧在 {@code Role#setFaction/resetFaction}）。
 */
final class RoleInfoImpl implements RoleInfo {

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
     * **自己是否与 {@code target} 敌对**（★ 自身相对）：直连平台关系表
     * （{@code platform.FactionLookup#isHostile(Faction, UUID)}）——
     * **不经过**"把 UUID 解析成在线玩家"这一步 ⇒ 判定与对象是否在线**无关** ✓。
     */
    @Override
    public boolean isHostileTo(UUID target) {
        return target != null && lookup().isHostile(faction(), target);
    }

    /**
     * **两个给定玩家之间是否敌对**（★ 对称）：直连平台关系表的**两 UUID 口径**
     * （{@code platform.FactionLookup#isHostile(UUID, UUID)}）——
     * 与本角色无关，任意两个玩家都可判（含离线者）✓。
     */
    @Override
    public boolean isHostile(UUID first, UUID second) {
        return first != null && second != null && lookup().isHostile(first, second);
    }

    /**
     * @deprecated 改用 {@link #isHostileTo(UUID)}
     * —— 本方法只是 {@code isHostileTo(victim.getUniqueId())}（与平台侧的弃用支同形）。
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
 //★ **未选角色的玩家不算敌人**：关系表（FactionLookup）对"任一方无角色"一律判**不敌对**
 //  ⇒ 本方法无需额外过滤（那道判定已在那层挡掉）。旧注释写的是"也要算进来"，
 //  那是**变更前**的口径，已作废 ✗
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
