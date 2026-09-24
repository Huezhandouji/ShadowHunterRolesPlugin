package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.RoleInfo;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@link RoleInfo} 的独立适配器（ · A1）：**持 {@code RoleInstance}**，与
 * {@code *PortImpl} 家族同形。
 * <p><b>取值一律经聚合根</b>：{@link #faction()} 读 {@code Role#getFaction()}
 * （**不是**读某个组件实例的字段 ⇒ 阵营"一个角色一份、全局静态"）。
 * <p><b>两个行为照搬原阵营组件</b>（{@code frameworkLevel/FactionComponent} 的
 * {@code isHostile} / {@code hasEnemyInRange}）—— 逐字等价，只把"自己的阵营"与"自己的位置"的
 * 取值路径改经本适配器持有的容器。
 * <p><b>（欠账 A 后半）</b>：{@code FactionComponent} **已整体删除** ⇒ 上面的
 * "照搬原组件"只作**历史沿革**读：本端口现在是**阵营读取的唯一入口** （{@link #faction()} 读聚合根
 * {@code Role#getFaction()}），且**不带写面** （R-1：写侧在 {@code Role#setFaction/resetFaction}）。
 * <p><b>旧口径原文</b>：「本卡纯加性：不删任何既有成员 /
 * 组件 / 端口」 —— 删除的那一半已由 落地（另一个旧原件见 {@link RoleInfo} 的 javadoc）。
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

    @Override
    public boolean isHostile(Player victim) {
        return lookup().isHostile(faction(), victim);
    }

    @Override
    public boolean hasEnemyInRange(double radius) {
        Location loc = owner.getPlayer() == null ? null : owner.getPlayer().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        Faction selfFaction = faction();

        for (Player p : loc.getNearbyPlayers(radius)) {
            if (p == null) {
                continue;
            }
 //没有选角色的玩家也要算进来（FactionLookup 对未选角色返回敌对）
            if (lookup().isHostile(selfFaction, p)) {
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
