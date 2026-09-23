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
     * 0 处 try/catch ⇒ 构造一旦失败，玩家**先丢角色、再吃逃逸异常**。
     * <p><b>阶段 10 · t64（P6 两阶段构造 · 本卡）</b>：t54 只做到"**预检先行**"（依赖不齐这类可预知的失败
     * 在清理之前被挡下）⇒ **非依赖类**的构造异常（例如某个组件构造器抛）仍发生在 {@code clearRole}
     * **之后**，玩家照样丢角色 ✗。本卡把 {@code RoleInstance} 拆成两相后，顺序变成：
     * <ol>
     *   <li><b>预检先行</b>：{@link Role#verifyDependencies()} 放在**清理旧角色之前**（t54，保留）；</li>
     *   <li><b>第一相：构造（不可见）</b>：{@link Role#createInstance(Player, RolesContext)} ——
     *       只做装配 / 注册表冻结 / 服务集 / 依赖检查，**不写任何玩家可见状态、不创建任务**
     *       ⇒ 失败时**原样返回**，旧角色完好无损 ✓（这是 t54 那条残留的收口点）；</li>
     *   <li><b>清旧角色</b>：构造**成功之后**才 {@link #clearRole(Player)} —— 此刻新实例**尚未**写入
     *       生命修饰符 / 热键栏 / 药水 ⇒ 旧实例 {@code clear()} 的三条"按共享 key 误伤"路径全部落空 ✓；</li>
     *   <li><b>第二相：激活（可见）</b>：{@link RoleInstance#activate()} —— 写生命修饰符 + 设置生命 +
     *       生命周期广播 + 启动 ticker + 构造期同步首刷；异常同样**不逃逸**（失败面见第 4 条申报）；</li>
     *   <li><b>换引用</b>：{@code playerRoleMap.put(...)}（唯一写入点，成功路径才发生）。</li>
     * </ol>
     * <p><b>可见顺序与迁移前逐字一致</b>：旧写法的可见序列 = "旧角色清理的可见效果 → 新实例构造期的
     * 可见效果"；本写法 = "（构造期不可见）→ 旧角色清理 → 新实例激活" ⇒ 可见序列不变 ✓。
     * <p><b>如实申报（第 4 条的失败面）</b>：第二相本身也可能抛（组件在 {@code awake()/start()} 里抛）。
     * 此时旧角色**已经**被释放（第 3 步不可回退）⇒ 该分支下玩家会处于"无角色"状态：本方法记一条
     * {@code SEVERE}、**释放半激活的实例**（{@code instance.clear()} ⇒ 不泄漏 ticker / 药水 / 热键栏 /
     * 属性修饰符）并返回 {@code false}，**异常不逃逸**。该分支在现有产品里不可达（无组件在
     * {@code awake()/start()} 抛），已在交付说明的未覆盖项里申报。
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

        //A5 ②（t64 第一相）：**先把新实例构造成功**（构造器只做不可见的事）
        //⇒ 失败时旧角色原样保留（t54 残留的收口点：非依赖类构造异常不再让玩家丢角色）
        RoleInstance instance;
        try {
            instance = role.createInstance(player, context);
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to instantiate for " + player.getName()
                            + "; the previous role is kept.", failure);
            return false;
        }

        //A5 ③：构造**成功之后**才清旧角色 —— 新实例此时尚未写入任何玩家可见状态（生命修饰符 /
        //热键栏 / 药水），旧实例的 clear() 无从误伤它（t54 实测的三条约束逐条见交付说明 A3）
        if(hasRole(player)) clearRole(player);

        //A5 ④（t64 第二相）：激活 —— 可见副作用全部在这里，异常不逃逸
        try {
            instance.activate();
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to activate for " + player.getName()
                            + "; the previous role was already released, the half-activated instance was cleaned up.",
                    failure);
            instance.clear();
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

        //同 Player 重载：第一相（构造，不可见）—— 失败 ⇒ 旧角色原样保留（A5 ②）
        RoleInstance instance;
        try {
            instance = role.createInstance(player, context);
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to instantiate for " + uuid
                            + "; the previous role is kept.", failure);
            return false;
        }

        //同 Player 重载：构造成功之后才清旧角色（A5 ③）
        if(hasRole(uuid)) clearRole(uuid);

        //同 Player 重载：第二相（激活，可见）—— 异常不逃逸（A5 ④）
        try {
            instance.activate();
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to activate for " + uuid
                            + "; the previous role was already released, the half-activated instance was cleaned up.",
                    failure);
            instance.clear();
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
