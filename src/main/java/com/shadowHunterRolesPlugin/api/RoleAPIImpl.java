package com.shadowHunterRolesPlugin.api;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class RoleAPIImpl implements RoleAPI {

    private final RoleManager roleManager;

    public RoleAPIImpl(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    //设置和取消角色
    @Override
    public boolean setPlayerRole(Player player, String roleId) {
        return roleManager.selectRole(player, roleId);
    }

    @Override
    public boolean clearPlayerRole(Player player) {
        return roleManager.clearRole(player);
    }


    //角色查询
    private RoleInstance getRoleInstance(Player player){
        return roleManager.getRoleInstance(player);
    }

    @Override
    public String getPlayerRoleId(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRole().getId() : null;
    }

    @Override
    public Component getPlayerRoleDisplayName(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRole().getDisplayName() : null;
    }

    @Override
    public boolean hasRole(Player player) {
        return roleManager.hasRole(player);
    }

    //能量系统
    @Override
    public int getPlayerEnergy(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getCurrentEnergy() : -78; //-78代表没查到
    }

    @Override
    public int getPlayerMaxEnergy(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getMaxEnergy() : -78;
    }

    @Override
    public void setPlayerEnergy(Player player, int amount){
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.setCurrentEnergy(amount);
    }

    @Override
    public void increaseEnergy(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.increaseEnergy(amount);

    }

    @Override
    public void decreaseEnergy(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.decreaseEnergy(amount);
    }

    //sanTE相关
    @Override
    public int getPlayerSanTE(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getCurrentSanTE() : -78;
    }

    @Override
    public int getPlayerMaxSanTE(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getMaxSanTE() : -78;
    }

    @Override
    public void setPlayerSanTE(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.setCurrentSanTE(amount);
    }

    @Override
    public void increaseSanTE(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.increaseSanTE(amount);
    }

    @Override
    public void decreaseSanTE(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.decreaseSanTE(amount);
    }

    //生命值相关
    @Override
    public double getPlayerHealth(Player player) {
        return player.getHealth();
    }

    @Override
    public double getPlayerMaxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    @Override
    public void healPlayer(Player player, double amount) {
        double newHealth = Math.min(player.getHealth() + amount, getPlayerMaxHealth(player));
        player.setHealth(newHealth);
    }

    //技能相关
    @Override
    public boolean isSkillReady(Player player, String skillId) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null && instance.isSkillReady(skillId);
    }

    @Override
    public int getSkillCooldownTick(Player player, String skillId) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRemainingSkillCooldownTicks(skillId) : 0;
    }

    //阵营相关
    @Override
    public Faction getFaction(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getFaction() : Faction.UNKNOWN;
    }

    @Override
    public void setFaction(Player player, Faction faction) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.setFaction(faction);
    }

    @Override
    public void resetFaction(Player player) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.resetFaction();
    }

    @Override
    public boolean areHostile(Player p1, Player p2) {
        return RoleInstance.areHostile(p1, p2);
    }
}



