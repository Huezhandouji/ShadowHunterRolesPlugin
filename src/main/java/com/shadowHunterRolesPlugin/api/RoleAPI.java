package com.shadowHunterRolesPlugin.api;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;


public interface RoleAPI {

    //不建议使用所有以Player类型作参数的api

    //查询一个角色id是否存在，即这个角色是否被实现
    boolean isValidRoleId(String id);

    //设置角色
    @Deprecated
    boolean setPlayerRole(Player player, String roleId);
    boolean setPlayerRole(UUID uuid, String roleId);

    @Deprecated
    boolean clearPlayerRole(Player player);
    boolean clearPlayerRole(UUID uuid);

    //角色查询
    @Deprecated
    String getPlayerRoleId(Player player);
    String getPlayerRoleId(UUID uuid);

    @Deprecated
    Component getPlayerRoleDisplayName(Player player);
    Component getPlayerRoleDisplayName(UUID uuid);

    @Deprecated
    boolean hasRole(Player player);
    boolean hasRole(UUID uuid);

    //角色描述查询
    Component getRoleDisplayName(String roleId);
    Component getRoleDescription(String roleId);
    Material getRoleIcon(String roleId);

    //能量系统
    @Deprecated
    int getPlayerEnergy(Player player);
    int getPlayerEnergy(UUID uuid);

    @Deprecated
    int getPlayerMaxEnergy(Player player);
    int getPlayerMaxEnergy(UUID uuid);

    @Deprecated
    void setPlayerEnergy(Player player, int amount);
    void setPlayerEnergy(UUID uuid, int amount);

    @Deprecated
    void increaseEnergy(Player player, int amount);
    void increaseEnergy(UUID uuid, int amount);

    @Deprecated
    void decreaseEnergy(Player player, int amount);
    void decreaseEnergy(UUID uuid, int amount);

    //sanTE
    @Deprecated
    int getPlayerSanTE(Player player);
    int getPlayerSanTE(UUID uuid);

    @Deprecated
    int getPlayerMaxSanTE(Player player);
    int getPlayerMaxSanTE(UUID uuid);

    @Deprecated
    void setPlayerSanTE(Player player, int amount);
    void setPlayerSanTE(UUID uuid, int amount);

    @Deprecated
    void increaseSanTE(Player player, int amount);
    void increaseSanTE(UUID uuid, int amount);

    @Deprecated
    void decreaseSanTE(Player player, int amount);
    void decreaseSanTE(UUID uuid, int amount);


    //生命值
    @Deprecated
    double getPlayerHealth(Player player);
    double getPlayerHealth(UUID uuid);

    @Deprecated
    double getPlayerMaxHealth(Player player);
    double getPlayerMaxHealth(UUID uuid);

    @Deprecated
    void healPlayer(Player player, double amount);
    void healPlayer(UUID uuid, double amount);

    //技能相关
    @Deprecated
    boolean isSkillReady(Player player, String skillId);
    boolean isSkillReady(UUID uuid, String skillId);

    @Deprecated
    int getSkillCooldownTick(Player player, String skillId);
    int getSkillCooldownTick(UUID uuid, String skillId);

    //阵营信息
    @Deprecated
    Faction getFaction(Player player);
    Faction getFaction(UUID uuid);

    @Deprecated
    void setFaction(Player player, Faction faction);
    void setFaction(UUID uuid, Faction faction);

    @Deprecated
    void resetFaction(Player player);
    void resetFaction(UUID uuid);

    @Deprecated
    boolean areHostile(Player p1, Player p2);
    boolean areHostile(UUID p1, UUID p2);
}
