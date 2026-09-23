package com.shadowHunterRolesPlugin;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.command.RoleCommand;
import com.shadowHunterRolesPlugin.config.ConfigurationManager;
import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.internal.api.RoleAPIImpl;
import com.shadowHunterRolesPlugin.listener.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.registry.RoleLoader;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
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

        //阶段 7 · A 步 · 默认配置落盘：此前全仓 0 处调用 ⇒ 数据目录里那份 config.yml **永不出现**，
        //运维（与任何读配置的人）根本发现不了 command-permission-level 这个字段。
        //saveDefaultConfig() 只在文件不存在时从 jar 内置默认值写一份；随后安装**唯一读盘口径**。
        saveDefaultConfig();
        ConfigurationManager.install(new ConfigurationManager(this));

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

        //阶段 3.1/3.2：注册表降级为纯容器，角色装配由 RoleLoader 在 onEnable 显式执行（fail-fast、按角色隔离）
        RoleRegistry roleRegistry = new RoleRegistry();
        RoleLoader roleLoader = new RoleLoader(getLogger());
        int loadedRoles = roleLoader.loadInto(roleRegistry);
        //阶段 4（⑤）：D-2 静态兼容桥已删除 —— 容器改为**构造注入**给 RoleManager 与 RoleCommand
        if (loadedRoles == 0) {
            getLogger().severe("No role templates were registered; /role and SHDF role selection will be unavailable.");
        }

        roleManager = new RoleManager(rolesContext, roleRegistry);

        PluginCommand roleCommand = getCommand("role");
        if(roleCommand == null){
            getLogger().warning("Command 'role' is not declared in plugin.yml; /role is unavailable.");
        }
        else{
            roleCommand.setExecutor(new RoleCommand(roleManager, roleRegistry));
        }

        Bukkit.getPluginManager().registerEvents(new SkillListener(roleManager, rolesContext), this);
        Bukkit.getPluginManager().registerEvents(new MainWeaponListener(roleManager, rolesContext), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(roleManager), this);
        Bukkit.getPluginManager().registerEvents(new DamageTrackerListener(), this);
        Bukkit.getPluginManager().registerEvents(new RoleEventListener(), this);
        //阶段 11 · t83：受伤 / 受治疗的**平台事件面**（钩子投递；ignoreCancelled、只读不取消）
        Bukkit.getPluginManager().registerEvents(new DamageHookListener(roleManager), this);

        roleAPI = new RoleAPIImpl(roleManager, roleRegistry);

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
