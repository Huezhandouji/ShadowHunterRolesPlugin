package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final RoleManager roleManager;

    public PlayerListener(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
        Player player = event.getEntity();
        if(roleManager.hasRole(player)){
            roleManager.clearRole(player);
            RoleInstance.clearHotbar(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event){
        Player player = event.getPlayer();
        RoleInstance.clearHotbar(player);
    }

}
