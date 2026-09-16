package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class RoleManager {

    private final Map<UUID, RoleInstance> playerRoleMap = new HashMap<>();

    private static RoleManager instance;

    public static RoleManager getInstance(){
        if(instance == null){
            instance = new RoleManager();
        }
        return instance;
    }

    private RoleManager(){

    }

    //选择角色
    public boolean selectRole(Player player, String roleId){
        if(!RoleRegistry.hasRole(roleId)) return false;


        Role role = RoleRegistry.getRole(roleId);
        if(role == null) return false;

        if(hasRole(player)) clearRole(player);

        RoleInstance instance = role.createInstance(player);

        playerRoleMap.put(player.getUniqueId(), instance);

        return true;
    }
    public boolean selectRole(UUID uuid, String roleId){
        if(!RoleRegistry.hasRole(roleId)) return false;

        Player player = Bukkit.getPlayer(uuid);
        if(player == null) return false;

        Role role = RoleRegistry.getRole(roleId);
        if(role == null) return false;

        if(hasRole(uuid)) clearRole(uuid);


        RoleInstance instance = role.createInstance(player);

        playerRoleMap.put(player.getUniqueId(), instance);

        return true;
    }

    //获取玩家的角色实例
    public RoleInstance getRoleInstance(Player player){
        return playerRoleMap.getOrDefault(player.getUniqueId(), null);
    }
    public RoleInstance getRoleInstance(UUID uuid){
        return playerRoleMap.getOrDefault(uuid, null);
    }

    //检查玩家是否已经选择了角色
    public boolean hasRole(Player player){
        return playerRoleMap.containsKey(player.getUniqueId());
    }
    public boolean hasRole(UUID uuid){
        return playerRoleMap.containsKey(uuid);
    }


    //插件禁用/重载时：走 instance.clear() 逐个回收（属性修饰符、记账内的药水、热键栏、任务），
    //不再只把 map 清空（O-8）
    public void clearAllPlayersRole(){
        for(RoleInstance instance : new ArrayList<>(playerRoleMap.values())){
            instance.clear();
        }
        playerRoleMap.clear();
    }

    //清除玩家的角色
    public boolean clearRole(Player player){
        RoleInstance removed = playerRoleMap.remove(player.getUniqueId());
        if(removed != null){
            removed.clear();
            return true;
        }
        return false;
    }
    public boolean clearRole(UUID uuid){
        RoleInstance removed = playerRoleMap.remove(uuid);
        if(removed != null){
            removed.clear();
            return true;
        }
        return false;
    }


}
