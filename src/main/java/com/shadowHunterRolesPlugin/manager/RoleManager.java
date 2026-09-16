package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class RoleManager {

    private final Map<UUID, RoleInstance> playerRoleMap = new HashMap<>();

    //阶段 2：去掉静态单例，改由主类在 onEnable 构造并注入平台上下文
    private final RolesContext context;

    //阶段 4（⑤）：RoleRegistry 改为构造注入（D-2 静态桥已删）
    private final RoleRegistry roleRegistry;

    public RoleManager(RolesContext context, RoleRegistry roleRegistry){
        this.context = context;
        this.roleRegistry = roleRegistry;
    }

    //选择角色
    public boolean selectRole(Player player, String roleId){
        if(!roleRegistry.contains(roleId)) return false;


        Role role = roleRegistry.get(roleId);
        if(role == null) return false;

        if(hasRole(player)) clearRole(player);

        RoleInstance instance = role.createInstance(player, context);

        playerRoleMap.put(player.getUniqueId(), instance);

        return true;
    }
    public boolean selectRole(UUID uuid, String roleId){
        if(!roleRegistry.contains(roleId)) return false;

        Player player = Bukkit.getPlayer(uuid);
        if(player == null) return false;

        Role role = roleRegistry.get(roleId);
        if(role == null) return false;

        if(hasRole(uuid)) clearRole(uuid);


        RoleInstance instance = role.createInstance(player, context);

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

    //这两个查询原本是 core/RoleInstance 的静态方法（内部走 RoleManager.getInstance()）。
    //阶段 2 移到数据所有者这里：语义逐字保留（任一方没有角色 → true），且 core 不再依赖单例。
    public boolean areHostile(Player p1, Player p2){
        if(p1 == null || p2 == null) return false;
        RoleInstance ins1 = getRoleInstance(p1);
        RoleInstance ins2 = getRoleInstance(p2);

        if(ins1 == null || ins2 == null) return true;
        return ins1.isHostileTo(ins2);

    }
    public boolean areHostile(UUID p1, UUID p2){
        if(p1 == null || p2 == null) return false;
        RoleInstance ins1 = getRoleInstance(p1);
        RoleInstance ins2 = getRoleInstance(p2);

        if(ins1 == null || ins2 == null) return true;
        return ins1.isHostileTo(ins2);
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
