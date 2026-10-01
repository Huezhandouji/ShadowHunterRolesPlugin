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
 * <p><b>★ 判敌口径（2026 语义变更，已非对称）</b>：① <b>同阵营优先</b> —— 双方都有角色且同阵营，
 * 任何情况下不敌对；② <b>只看对方在场</b> —— 对方是创造 / 旁观 ⇒ 不敌对；
 * ③ 其余（对方在场且不同阵营，含"任一方没有角色"）⇒ 敌对。
 * <p>★ <b>自己在场与否不参与判定</b>（旧口径的"自己不在场 ⇒ 一律不敌对"闸门已删除）：
 * 己方是创造 / 旁观时，敌人只要是生存 / 冒险且不同阵营 ⇒ <b>敌对</b>。
 * 合成真值 = {@link com.shadowHunterRolesPlugin.platform.Hostility}，本类只做接线。
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
     * <p>★ <b>本方法不再检查"自己是否在场"</b>（旧口径那道前置闸门已删除）：
     * 判敌只读<b>对方</b>的在场状态（对方是创造 / 旁观 ⇒ 不敌对）与双方的阵营（同阵营 ⇒ 不敌对）。
     * 因此自己处于创造 / 旁观时，敌人只要是生存 / 冒险且不同阵营，判定为<b>敌对</b>。
     * 真值 = {@link com.shadowHunterRolesPlugin.platform.Hostility#isHostileTo}。
     */
    @Override
    public boolean isHostileTo(UUID target) {
        if (target == null) {
            return false;
        }
        return lookup().isHostile(faction(), target);
    }

    /**
     * 「{@code first} 是否视 {@code second} 为敌人」——<b>非对称</b>，方向由形参顺序表达。
     *
     * <p>★ 语义已随口径变更<b>不再是"两者之间（对称）"</b>：<b>first = 发起方</b>（其阵营决定
     * 有没有同阵营豁免）、<b>second = 目标</b>（其在场状态参与判定）。因此
     * {@code isHostile(a, b)} 与 {@code isHostile(b, a)} 一般不同值。
     *
     * <p>要问"我是否与它敌对"用 {@link #isHostileTo(UUID)}（本方法正是它把 first 展开成阵营的形式）。
     * 直连 {@code platform.FactionLookup#isHostile(UUID, UUID)}——同一口径。
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
 //  ① 创造 / 旁观者不算敌人 —— 由关系表内的「对方不在场 ⇒ 不敌对」挡掉；
 //  ② 同阵营不算敌人；
 //  ③ 没有角色的玩家**算敌人**（因此这里不再有"未选角色就跳过"的过滤）。
 //  ★ 己方是否在场**不再影响本方法**（旧口径的"自己不在场 ⇒ false"闸门已删）：
 //    己方切创造 / 旁观后，只要周围有人在场且不同阵营，本方法照样返回 true。
 //  注意 getNearbyPlayers 只看得到在线玩家 ⇒ 离线者永不构成本方法的"敌人"。
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
