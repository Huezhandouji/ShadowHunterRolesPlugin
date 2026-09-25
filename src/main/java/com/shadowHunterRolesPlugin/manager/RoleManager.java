package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.core.ports.RoleInfo;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import com.shadowHunterRolesPlugin.roleComponent.HotbarItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public class RoleManager {

    private final Map<UUID, RoleInstance> playerRoleMap = new HashMap<>();

    //去掉静态单例，改由主类在 onEnable 构造并注入平台上下文
    private final RolesContext context;

    //RoleRegistry 改为构造注入（静态桥已删）
    private final RoleRegistry roleRegistry;

    /**
     * 故障隔离的**提醒器**（全服简报 + OP 详情 + **限流/去重**）。
     * 它由管理器持有（而不是 RoleInstance）：提醒是"对全服说一句话"，属管理器职责；
     * 且限流表需要**跨实例**共享（同一角色反复失败时，N 秒内只播一次 ✓）。
     */
    private final QuarantineNotifier quarantineNotifier;

    public RoleManager(RolesContext context, RoleRegistry roleRegistry){
        this.context = context;
        this.roleRegistry = roleRegistry;
        this.quarantineNotifier = new QuarantineNotifier(context);
    }

    /** 隔离提醒器（诊断/取证读口）。 */
    public QuarantineNotifier quarantineNotifier(){
        return quarantineNotifier;
    }

    /**
     * 选择角色。
 * <p><b>早先写法是"**先 clear 旧角色、再裸构造新实例**"</b>且全文件
     * 0 处 try/catch ⇒ 构造一旦失败，玩家**先丢角色、再吃逃逸异常**。
     * <p><b>两阶段构造</b>：只做到"**预检先行**"（依赖不齐这类可预知的失败
     * 在清理之前被挡下）⇒ **非依赖类**的构造异常（例如某个组件构造器抛）仍发生在 {@code clearRole}
 * **之后**，玩家照样丢角色 ✗。把 {@code RoleInstance} 拆成两相后，顺序变成：
     * <ol>
 * <li><b>预检先行</b>：{@link Role#verifyDependencies()} 放在**清理旧角色之前**（，保留）；</li>
     *   <li><b>第一相：构造（不可见）</b>：{@link Role#createInstance(Player, RolesContext)} ——
     *       只做装配 / 注册表冻结 / 服务集 / 依赖检查，**不写任何玩家可见状态、不创建任务**
 * ⇒ 失败时**原样返回**，旧角色完好无损 ✓（这是 那条残留的收口点）；</li>
     *   <li><b>清旧角色</b>：构造**成功之后**才 {@link #clearRole(Player)} —— 此刻新实例**尚未**写入
     *       生命修饰符 / 热键栏 / 药水 ⇒ 旧实例 {@code clear()} 的三条"按共享 key 误伤"路径全部落空 ✓；</li>
     *   <li><b>第二相：激活（可见）</b>：{@link RoleInstance#activate()} —— 写生命修饰符 + 设置生命 +
     *       生命周期广播 + 启动 ticker + 构造期同步首刷；异常同样**不逃逸**（失败面见第 4 条申报）；</li>
     *   <li><b>换引用</b>：{@code playerRoleMap.put(...)}（唯一写入点，成功路径才发生）。</li>
     * </ol>
 * <p><b>可见顺序与既有实现逐字一致</b>：早先写法的可见序列 = "旧角色清理的可见效果 → 新实例构造期的
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

 //A5 ②（第一相）：**先把新实例构造成功**（构造器只做不可见的事）
 //⇒ 失败时旧角色原样保留（残留的收口点：非依赖类构造异常不再让玩家丢角色）
        RoleInstance instance;
        try {
            instance = role.createInstance(player, context);
        } catch (Throwable failure) {
            context.logger().log(Level.SEVERE,
                    "Role '" + roleId + "' failed to instantiate for " + player.getName()
                            + "; the previous role is kept.", failure);
            return false;
        }

        //**绑定隔离处置**（必须在 activate() 之前 ⇒ awake()/start() 里的异常也能被隔离）
        instance.bindQuarantineHandler(this::onInstanceQuarantined);

        //A5 ③：构造**成功之后**才清旧角色 —— 新实例此时尚未写入任何玩家可见状态（生命修饰符 /
 //热键栏 / 药水），旧实例的 clear() 无从误伤它（实测的三条约束逐条见交付说明 A3）
        if(hasRole(player)) clearRole(player);

 //A5 ④（第二相）：激活 —— 可见副作用全部在这里，异常不逃逸
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

        //激活期被隔离（组件在 awake()/start() 里抛）⇒ 隔离流程已经记日志 / 提醒 / 清空角色，
        //这里**不再**把已死的实例写进 map（否则玩家会拿到一个"组件已被全部移除"的空壳角色 ✗）
        if(instance.isQuarantined()) {
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

 //同 Player 重载：绑定隔离处置（，必须在 activate() 之前）
        instance.bindQuarantineHandler(this::onInstanceQuarantined);

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

 //同 Player 重载：激活期被隔离 ⇒ 不再写入 map
        if(instance.isQuarantined()) {
            return false;
        }

        playerRoleMap.put(player.getUniqueId(), instance);

        return true;
    }

    /**
     * **故障隔离的对外处置** ——由 {@link RoleInstance} 在隔离的第 ④ 步回调。
     *
     * <ol>
     *   <li><b>提醒</b>：全服简报 + OP 详情，**经限流器**（同一 {@code role:component} 在窗口内只播一次 ✓）；</li>
 * <li><b>清空角色</b>：**复用既有清理链** {@link #clearRole(UUID)}（= 从表里摘除 +
     *       {@code instance.clear()}）⇒ 热键栏 / 生命修饰符 / 药水 / 任务全部回收，**不另写一套** ✓。</li>
     * </ol>
     * 未入表的实例（构造后、激活期被隔离）⇒ 直接 {@code instance.clear()} 释放半激活实例（不留泄漏面）。
     */
    private void onInstanceQuarantined(RoleInstance instance, String componentId, String phase, Throwable failure) {
        if (instance == null) {
            return;
        }
        Player player = instance.getPlayer();
        String roleId = instance.getRole() == null ? "<unknown>" : instance.getRole().getId();
        String playerName = player == null ? "<unknown>" : player.getName();
        UUID uuid = player == null ? null : player.getUniqueId();

        //④ 提醒：全服简报（所有人）+ OP 详情（含组件名 / 阶段 / 异常 / 栈摘要）+ **限流去重**
        quarantineNotifier.announce(roleId, playerName, componentId, phase, failure);

        //A6：隔离后角色归属 = 清空（复用既有清理链；玩家变为无角色、可重选）
        if (uuid != null && playerRoleMap.containsKey(uuid)) {
            clearRole(uuid);
        } else {
            instance.clear();
        }
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
    //移到数据所有者这里：语义逐字保留（任一方没有角色 → true），且 core 不再依赖单例。
    //判定入口从 `RoleInstance#isHostileTo(...)` 的**读视图**
    //（已随 FactionComponent 整体删除 ✗）改走 **`roleInfo` 服务面** ✓ —— 唯一读入口 ✓。
    //口径逐字等价（同一条 `FactionLookup#isHostile` 关系表 + 同一个 `isHostileTo(RoleInstance)` 的
    //对称化写法：任一方敌对即敌对；任一方未选角色/为空/info 为空 → 真值 true 直返）✓。
    public boolean areHostile(Player p1, Player p2){
        if(p1 == null || p2 == null) return false;
        RoleInstance ins1 = getRoleInstance(p1);
        RoleInstance ins2 = getRoleInstance(p2);

        if(ins1 == null || ins2 == null) return true;
        RoleInfo info1 = ins1.roleInfo();
        RoleInfo info2 = ins2.roleInfo();
        if(info1 == null || info2 == null) return true;
        return info1.isHostile(p2) || info2.isHostile(p1);

    }
    public boolean areHostile(UUID p1, UUID p2){
        if(p1 == null || p2 == null) return false;
        RoleInstance ins1 = getRoleInstance(p1);
        RoleInstance ins2 = getRoleInstance(p2);

        if(ins1 == null || ins2 == null) return true;
        RoleInfo info1 = ins1.roleInfo();
        RoleInfo info2 = ins2.roleInfo();
        if(info1 == null || info2 == null) return true;
        return info1.isHostile(playerOf(p2)) || info2.isHostile(playerOf(p1));
    }

    /** uuid → 在线 {@code Player}（离线 / 未加载 ⇒ {@code null}）；供 {@code areHostile(UUID,UUID)} 走服务面。 */
    private static Player playerOf(UUID uuid){
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }


    //插件禁用/重载时：走 instance.clear() 逐个回收（属性修饰符、记账内的药水、热键栏、任务），
    //不再只把 map 清空
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
 //清角色 ⇒ 一并清掉本系统写在热键栏里的物品（物品关注点归 roleComponent/HotbarItems ✓）。
 //★ 放在**管理器**而不是容器：容器只剩"容器职责"的对外面（查取入口 / 隔离 / 生命周期）✗，
 //  "清哪些物品"属物品关注点；此处是**所有在线清角色路径的汇聚点**（手动 /role clear · 死亡 · 重载）。
 //★ 两个重载**都**清物品（这一"清角色"的语义不因取 Player 的方式而变）——
 //  故三条走 UUID 的路径（换角色 / 隔离 / API 的 UUID 重载）与掉线路径**都不再需要各自补清** ✓。
            HotbarItems.clearFrom(player);
            return true;
        }
        return false;
    }
    public boolean clearRole(UUID uuid){
        RoleInstance removed = playerRoleMap.remove(uuid);
        if(removed != null){
            removed.clear();
 //清角色 ⇒ 一并清掉本系统写在热键栏里的物品（物品关注点归 roleComponent/HotbarItems ✓）。
 //★ 与 {@link #clearRole(Player)} 的**唯一**差别只是取 Player 的方式：本重载用
 //  {@code Bukkit.getPlayer(uuid)}（离线 ⇒ null ⇒ 无实体可清，跳过）。**清物品这一点两个重载必须一致** ——
 //  否则三条走 UUID 的路径（换角色 / 隔离 / API 的 UUID 重载）会静默失去清理（掉线那处曾因此出现
 //  「随存档持久化的残留」，见 t56 的行为空洞判定）。
            Player online = Bukkit.getPlayer(uuid);
            if(online != null){
                HotbarItems.clearFrom(online);
            }
            return true;
        }
        return false;
    }


}
