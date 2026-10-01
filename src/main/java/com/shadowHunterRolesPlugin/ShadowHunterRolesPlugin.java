package com.shadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.listener.hook.HotbarItemProtectionListener;
import com.shadowHunterRolesPlugin.listener.hook.DamageHookListener;
import com.shadowHunterRolesPlugin.listener.hook.ImmunePotionListener;
import com.shadowHunterRolesPlugin.listener.hook.PlayerKilledHookListener;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.command.RoleCommand;
import com.shadowHunterRolesPlugin.config.ConfigurationManager;
import com.shadowHunterRolesPlugin.core.Faction;
//阵营读取经聚合根 Role（RoleInstance 不提供读视图）。
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.internal.api.RoleAPIImpl;
import com.shadowHunterRolesPlugin.listener.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import com.shadowHunterRolesPlugin.platform.CombatPresence;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import com.shadowHunterRolesPlugin.platform.FactionRelation;
import com.shadowHunterRolesPlugin.platform.Hostility;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.registry.RoleLoader;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import java.util.UUID;

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

        //默认配置落盘：saveDefaultConfig() 只在文件不存在时从 jar 内置默认值写一份
        //（不落盘则数据目录里的 config.yml 永不出现，运维发现不了 command-permission-level 这个字段）；
        //随后安装唯一读盘口径。
        saveDefaultConfig();
        ConfigurationManager.install(new ConfigurationManager(this));

        //平台层上下文在这里构造并注入，领域层 / 组件层不引用插件主类单例
        KeyFactory keys = key -> new NamespacedKey(this, key);
        KeyFactory.Registry.install(keys);

        FactionLookup factions = new FactionLookup() {
            /**
             * 按 UUID 读阵营（主口径）：取实例走 {@code RoleManager.getRoleInstance(uuid)}（UUID 重载）。
             *
             * <p>取值经聚合根（角色模板上的阵营声明值）：未选角色 / 取不到角色模板则 {@code Faction.UNKNOWN}。
             */
            @Override
            public Faction factionOf(UUID uuid) {
                if(uuid == null) return Faction.UNKNOWN;
                RoleInstance target = roleManager != null ? roleManager.getRoleInstance(uuid) : null;
                Role role = target != null ? target.getRole() : null;
                return role != null ? role.getFaction() : Faction.UNKNOWN;
            }

            /**
             * 「{@code self} 是否视 {@code other} 为敌人」——<b>非对称</b>，方向由形参顺序表达
             * （{@code self} = 发起方，{@code other} = 目标）。
             *
             * <p>★ 口径（2026 语义变更）：只读<b>对方</b>的在场状态，
             * <b>己方是创造 / 旁观不再豁免</b>（旧口径"双方都不敌对"的那道己方闸门已删除）。
             * 因此 {@code isHostile(a,b)} 与 {@code isHostile(b,a)} 一般<b>不同值</b>。
             *
             * <p>本方法只是把「阵营 + 对方的在场」按「发起方 = self」展开一遍，
             * 因此与下面 {@link #isHostile(Faction, UUID)} 逐条等价（同一条真值路径）。
             *
             * <p>真值转发到 {@link Hostility}（合成层）与两个维度真值 {@link CombatPresence} /
             * {@link FactionRelation}，本处只做接线、不自己持有语义。
             * 消费者 = {@code core/RoleInfoImpl#isHostileTo(UUID)} 与 {@code manager/RoleManager#areHostile}
             * （后者是<b>公开 API</b> {@code RoleAPI.areHostile} 的落点）。
             */
            @Override
            public boolean isHostile(UUID self, UUID other) {
                if (self == null || other == null) {
                    return false;
                }
                return isHostile(factionOf(self), other);
            }

            /**
             * 「某个阵营」与「某个玩家」是否敌对：对方<b>在场</b>且（双方都有角色时阵营不同）则为敌对。
             * <p>对方没有角色同样敌对（口径见类注释的「无角色」）。
             * <p><b>己方是否在场不参与</b>：{@code self} 只是阵营，本方法看不到玩家对象也不需要看 ——
             * 旧口径那道"自己不在场 ⇒ 一律不敌对"的前置闸门已随语义变更删除（落点已从
             * {@code core/RoleInfoImpl#isHostileTo} 移到这里统一处理）。
             * <p>真值转发到 {@link Hostility#isHostileTo(Faction, Faction, boolean)}（合成层）。
             */
            @Override
            public boolean isHostile(Faction self, UUID other) {
                return other != null
                        && Hostility.isHostileTo(self, factionOf(other), participatesInHostility(other));
            }
        };

        rolesContext = new RolesContext(this, getLogger(), new BukkitSchedulerAdapter(this), keys, factions);

        //注册表是纯容器，角色装配由 RoleLoader 在 onEnable 显式执行（fail-fast、按角色隔离）
        RoleRegistry roleRegistry = new RoleRegistry();
        RoleLoader roleLoader = new RoleLoader(getLogger());
        int loadedRoles = roleLoader.loadInto(roleRegistry);
        // 容器以构造注入交给 RoleManager 与 RoleCommand
        if (loadedRoles == 0) {
            getLogger().severe("No role templates were registered; /role and SHDF role selection will be unavailable.");
        }

        roleManager = new RoleManager(rolesContext, roleRegistry);

        //RoleAPI 的构造在指令注册之前 —— 组件操作面的指令面要直接调公开 API
        //（服务表注册仍在下面原处，两者顺序对下游无影响）
        roleAPI = new RoleAPIImpl(roleManager, roleRegistry);

        PluginCommand roleCommand = getCommand("role");
        if(roleCommand == null){
            getLogger().warning("Command 'role' is not declared in plugin.yml; /role is unavailable.");
        }
        else{
            roleCommand.setExecutor(new RoleCommand(roleManager, roleRegistry, roleAPI));
        }

        Bukkit.getPluginManager().registerEvents(new SkillListener(roleManager, rolesContext), this);
        Bukkit.getPluginManager().registerEvents(new MainWeaponListener(roleManager, rolesContext), this);
        //弓弩管道：与上面两条是"第三条输入管道"，差别在右键的归属 —— 弓弩的右键归原版
        //（拉弓 / 装填 / 击发），系统只在箭矢离弦那一刻派发 onShoot；左键与 Q 则照技能家族口径派发 onCast。
        //三条管道按各自的识别键判物，互不认领对方的物品。
        Bukkit.getPluginManager().registerEvents(new BowWeaponListener(roleManager, rolesContext), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(roleManager), this);
        Bukkit.getPluginManager().registerEvents(new DamageTrackerListener(), this);
        Bukkit.getPluginManager().registerEvents(new RoleEventListener(), this);
        //受伤 / 受治疗的平台事件面（钩子投递；ignoreCancelled、只读不取消）
        Bukkit.getPluginManager().registerEvents(new DamageHookListener(roleManager), this);
        //击杀的平台事件面（玩家被玩家击杀 ⇒ 投给**击杀者**实例的击杀订阅名单）
        //  优先级 LOWEST：必须早于 PlayerListener#onPlayerDeath(NORMAL) 读被杀者的角色 id（它之后会清角色）
        Bukkit.getPluginManager().registerEvents(new PlayerKilledHookListener(roleManager), this);
        //热键栏物品的不可动保护（拖拽 / F 键 / 容器搬运 / 合成格 四类真缺口的取消型保护）
        //  与上一行的姿态相反：保护侧必须 setCancelled(true)；读侧只通知（见该类的 javadoc）
        Bukkit.getPluginManager().registerEvents(new HotbarItemProtectionListener(), this);
        //IMMUNE 期间的原版负面药水免疫（取消型保护）：补上 buff 账本挡不住的那一半
        //（原版来源 + 绕过 buff 组件的插件技能施加；判据与覆盖范围见该类 javadoc）
        Bukkit.getPluginManager().registerEvents(new ImmunePotionListener(roleManager), this);

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

    /**
     * 按 UUID 取该玩家的角色实例（未选角色 / 未加载则 {@code null}）。
     *
     * <p><b>为何开这个口</b>：组件层没有跨实例通道（{@code ComponentLookupPort} 只看本实例、
     * 插件单例也不暴露 {@code RoleManager}），"削减敌人能量"这类跨实例技能无路可走。
     * <p>本口只做一件事：转发 {@code RoleManager} 的既有查询（不新增状态、不做缓存），
     * 语义与 {@code API} 侧的 {@code executeComponentOperation} 同源。
     */
    public RoleInstance roleInstanceOf(java.util.UUID uuid){
        return roleManager != null ? roleManager.getRoleInstance(uuid) : null;
    }

}
