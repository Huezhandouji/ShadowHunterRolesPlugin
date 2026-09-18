package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.entity.Player;

/**
 * 阵营查询：取代 core 里的 {@code RoleManager.getInstance()}。
 * 未选角色 / 已离线 → {@link Faction#UNKNOWN}。
 */
public interface FactionLookup {

    Faction factionOf(Player player);

    /**
     * 与原 {@code RoleInstance#isHostileTo(Faction)} 的语义一致：
     * 对方未选角色（UNKNOWN）或与自身阵营不同 → 敌对；自身阵营为 UNKNOWN → 恒敌对。
     */
    boolean isHostile(Faction self, Player other);
}
