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

    //获取玩家的角色实例
    public RoleInstance getRoleInstance(Player player){
        return playerRoleMap.getOrDefault(player.getUniqueId(), null);
    }

    public Role getCurrentRole(Player player){
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRole() : null;
    }

    //检查玩家是否已经选择了角色
    public boolean hasRole(Player player){
        return playerRoleMap.containsKey(player.getUniqueId());
    }


    public void clearAllPlayersRole(){
        playerRoleMap.clear();
    }

    public int getPlayersWithRoleCount(){
        return playerRoleMap.size();
    }

    public List<Player> getAllPlayersWithRole(){
        List<Player> players = new ArrayList<>();
        for(UUID uuid : playerRoleMap.keySet()){
            Player player = Bukkit.getPlayer(uuid);
            if(player != null && player.isOnline()){
                players.add(player);
            }
        }

        return players;
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


}
