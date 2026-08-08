package com.shadowHunterRolesPlugin.api;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;


public interface RoleAPI {

    //设置角色
    boolean setPlayerRole(Player player, String roleId);

    boolean clearPlayerRole(Player player);

    //角色查询
    String getPlayerRoleId(Player player);

    Component getPlayerRoleDisplayName(Player player);

    boolean hasRole(Player player);

    //能量系统
    int getPlayerEnergy(Player player);

    int getPlayerMaxEnergy(Player player);

    void setPlayerEnergy(Player player, int amount);

    void increaseEnergy(Player player, int amount);

    void decreaseEnergy(Player player, int amount);

    //sanTE
    int getPlayerSanTE(Player player);

    int getPlayerMaxSanTE(Player player);

    void setPlayerSanTE(Player player, int amount);

    void increaseSanTE(Player player, int amount);

    void decreaseSanTE(Player player, int amount);


    //生命值
    double getPlayerHealth(Player player);

    double getPlayerMaxHealth(Player player);

    void healPlayer(Player player, double amount);

    //技能相关
    boolean isSkillReady(Player player, String skillId);

    int getSkillCooldownTick(Player player, String skillId);

    //阵营信息
    Faction getFaction(Player player);

    void setFaction(Player player, Faction faction);

    void resetFaction(Player player);

    boolean areHostile(Player p1, Player p2);
}
