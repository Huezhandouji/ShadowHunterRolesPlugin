package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.entity.Player;

/** 阵营与敌对判定端口。{@code hasEnemyInRange} 必须保留今天的语义：**未选角色的玩家也算敌人**。 */
public interface FactionPort {

    boolean isHostile(Player victim);

    Faction faction();

    boolean hasEnemyInRange(double radius);
}
