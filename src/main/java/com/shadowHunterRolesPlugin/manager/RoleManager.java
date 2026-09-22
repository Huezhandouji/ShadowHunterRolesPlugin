package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

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

    /**
     * 选择角色。
     * <p><b>阶段 10 · t54（A5 · AR-2 修正）</b>：旧写法是"**先 clear 旧角色、再裸构造新实例**"且全文件
     * 0 处 try/catch ⇒ 构造一旦失败，玩家**先丢角色、再吃逃逸异常**。现在：
     * <ol>
     *   <li><b>预检先行</b>：{@link Role#verifyDependencies()} 放在**清理旧角色之前** ⇒ 依赖不齐这类
     *       "可预知"的失败**不会**让玩家先丢角色（反例判据：依赖缺失时玩家仍持有原角色）；</li>
     *   <li><b>异常不逃逸</b>：预检与构造各自包在 try/catch 里，失败只记 {@code SEVERE} 并返回 {@code false}；</li>
     *   <li><b>如实申报的残留</b>：**非依赖类**的构造异常（例如某个组件构造器抛）发生在 {@code clearRole}
     *       **之后** ⇒ 此时旧角色已被回收、无法回滚。安全的"先构造后清理"需要 {@code RoleInstance} 的新 API
     *       （旧实例的 {@code clear()} 会按**共享 key** 移除新实例的属性修饰符、并清空新实例的热键栏/药水）
     *       ⇒ 不在本卡 inScope，已在交付说明里申报（最小闭合 = 另立卡给 {@code RoleInstance} 加两阶段构造）。</li>
     * </ol>
     */
    public boolean selectRole(Player player, String roleId){
        if(!roleRegistry.contains(roleId)) return false;


        Role role = roleRegistry.get(roleId);
        if(role == null) return false;

        //A5 ①：预检（装配期依赖检查）必须在**清理旧角色之前** —— 它不产生任何副作用
        try {
            role.verifyDependencies();
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed the assembly-time dependency check for " + player.getName()
                            + "; the previous role is kept.", failure);
            return false;
        }

        if(hasRole(player)) clearRole(player);

        //A5 ②：构造异常不得逃逸
        RoleInstance instance;
        try {
            instance = role.createInstance(player, context);
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to instantiate for " + player.getName()
                            + "; the player was left without a role (see the delivery notes).", failure);
            return false;
        }

        playerRoleMap.put(player.getUniqueId(), instance);

        return true;
    }
    public boolean selectRole(UUID uuid, String roleId){
        if(!roleRegistry.contains(roleId)) return false;

        Player player = Bukkit.getPlayer(uuid);
        if(player == null) return false;

        Role role = roleRegistry.get(roleId);
        if(role == null) return false;

        //同 Player 重载：预检先行（A5 ①）
        try {
            role.verifyDependencies();
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed the assembly-time dependency check for " + uuid
                            + "; the previous role is kept.", failure);
            return false;
        }

        if(hasRole(uuid)) clearRole(uuid);


        RoleInstance instance;
        try {
            instance = role.createInstance(player, context);
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to instantiate for " + uuid
                            + "; the player was left without a role (see the delivery notes).", failure);
            return false;
        }

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
