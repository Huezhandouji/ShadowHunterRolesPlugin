package com.shadowHunterRolesPlugin.api;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;


public interface RoleAPI {

    //查询id是否合法
    boolean isValidRoleId(String id);

    //设置角色
    boolean setPlayerRole(Player player, String roleId);
    boolean setPlayerRole(UUID uuid, String roleId);

    boolean clearPlayerRole(Player player);
    boolean clearPlayerRole(UUID uuid);

    //角色查询
    String getPlayerRoleId(Player player);
    String getPlayerRoleId(UUID uuid);

    Component getPlayerRoleDisplayName(Player player);
    Component getPlayerRoleDisplayName(UUID uuid);

    boolean hasRole(Player player);
    boolean hasRole(UUID uuid);

    //能量系统
    int getPlayerEnergy(Player player);
    int getPlayerEnergy(UUID uuid);

    int getPlayerMaxEnergy(Player player);
    int getPlayerMaxEnergy(UUID uuid);

    void setPlayerEnergy(Player player, int amount);
    void setPlayerEnergy(UUID uuid, int amount);

    void increaseEnergy(Player player, int amount);
    void increaseEnergy(UUID uuid, int amount);

    void decreaseEnergy(Player player, int amount);
    void decreaseEnergy(UUID uuid, int amount);

    //sanTE
    int getPlayerSanTE(Player player);
    int getPlayerSanTE(UUID uuid);

    int getPlayerMaxSanTE(Player player);
    int getPlayerMaxSanTE(UUID uuid);

    void setPlayerSanTE(Player player, int amount);
    void setPlayerSanTE(UUID uuid, int amount);

    void increaseSanTE(Player player, int amount);
    void increaseSanTE(UUID uuid, int amount);

    void decreaseSanTE(Player player, int amount);
    void decreaseSanTE(UUID uuid, int amount);


    //生命值
    double getPlayerHealth(Player player);
    double getPlayerHealth(UUID uuid);

    double getPlayerMaxHealth(Player player);
    double getPlayerMaxHealth(UUID uuid);

    void healPlayer(Player player, double amount);
    void healPlayer(UUID uuid, double amount);

    //技能相关
    boolean isSkillReady(Player player, String skillId);
    boolean isSkillReady(UUID uuid, String skillId);

    int getSkillCooldownTick(Player player, String skillId);
    int getSkillCooldownTick(UUID uuid, String skillId);

    //阵营信息
    Faction getFaction(Player player);
    Faction getFaction(UUID uuid);

    void setFaction(Player player, Faction faction);
    void setFaction(UUID uuid, Faction faction);

    void resetFaction(Player player);
    void resetFaction(UUID uuid);

    boolean areHostile(Player p1, Player p2);
    boolean areHostile(UUID p1, UUID p2);
}
