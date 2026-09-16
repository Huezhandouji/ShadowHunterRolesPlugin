package com.shadowHunterRolesPlugin;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.api.RoleAPIImpl;
import com.shadowHunterRolesPlugin.command.RoleCommand;
import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.listener.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShadowHunterRolesPlugin extends JavaPlugin {

    private static ShadowHunterRolesPlugin instance;
    private RoleManager roleManager;
    private RolesContext rolesContext;

    private RoleAPI roleAPI;


    @Override
    public void onEnable() {

        instance = this;

        //平台层剥离（阶段 2）：上下文在这里构造并注入，领域层/组件层不再引用插件主类单例
        KeyFactory keys = key -> new NamespacedKey(this, key);
        KeyFactory.Registry.install(keys);

        FactionLookup factions = new FactionLookup() {
            @Override
            public Faction factionOf(Player player) {
                if(player == null) return Faction.UNKNOWN;
                RoleInstance target = roleManager != null ? roleManager.getRoleInstance(player) : null;
                return target != null ? target.getFaction() : Faction.UNKNOWN;
            }

            @Override
            public boolean isHostile(Faction self, Player other) {
                Faction otherFaction = factionOf(other);
                return self != otherFaction || self == Faction.UNKNOWN;
            }
        };

        rolesContext = new RolesContext(this, getLogger(), new BukkitSchedulerAdapter(this), keys, factions);

        roleManager = new RoleManager(rolesContext);

        PluginCommand roleCommand = getCommand("role");
        if(roleCommand == null){
            getLogger().warning("Command 'role' is not declared in plugin.yml; /role is unavailable.");
        }
        else{
            roleCommand.setExecutor(new RoleCommand(roleManager));
        }

        Bukkit.getPluginManager().registerEvents(new SkillListener(roleManager, rolesContext), this);
        Bukkit.getPluginManager().registerEvents(new MainWeaponListener(roleManager, rolesContext), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(roleManager), this);
        Bukkit.getPluginManager().registerEvents(new DamageTrackerListener(), this);
        Bukkit.getPluginManager().registerEvents(new RoleEventListener(), this);

        roleAPI = new RoleAPIImpl(roleManager);

        Bukkit.getServicesManager().register(RoleAPI.class, roleAPI, this, ServicePriority.Normal);

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
