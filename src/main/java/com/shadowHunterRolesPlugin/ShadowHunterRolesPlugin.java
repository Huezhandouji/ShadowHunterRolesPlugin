package com.shadowHunterRolesPlugin;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.api.RoleAPIImpl;
import com.shadowHunterRolesPlugin.command.RoleCommand;
import com.shadowHunterRolesPlugin.listener.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShadowHunterRolesPlugin extends JavaPlugin {

    private static ShadowHunterRolesPlugin instance;
    private RoleManager roleManager;

    private RoleAPI roleAPI;

    @Override
    public void onEnable() {

        instance = this;
        roleManager = RoleManager.getInstance();

        getCommand("role").setExecutor(new RoleCommand(roleManager));

        Bukkit.getPluginManager().registerEvents(new SkillListener(roleManager), this);
        Bukkit.getPluginManager().registerEvents(new MainWeaponListener(roleManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(roleManager), this);
        Bukkit.getPluginManager().registerEvents(new DamageTrackerListener(), this);
        Bukkit.getPluginManager().registerEvents(new RoleEventListener(), this);

        roleAPI = new RoleAPIImpl(roleManager);

        getLogger().info("ShadowHunter Character System enabled.");

    }

    @Override
    public void onDisable() {

        if(roleManager != null){
            roleManager.clearAllPlayersRole();
        }


        getLogger().info("ShadowHunter Character System disabled.");

    }

    public static ShadowHunterRolesPlugin getInstance(){
        return instance;
    }

    public RoleAPI getRoleAPI(){
        return roleAPI;
    }

}
