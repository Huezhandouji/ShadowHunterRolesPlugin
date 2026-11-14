package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.api.event.RoleDeathEvent;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.internal.event.RoleEventBus;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.HotbarItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final RoleManager roleManager;

    //事件总线（构造注入）：死亡事件的发布点在这里而不是 RoleManager——
    //"玩家死亡"是平台事件面（PlayerDeathEvent）才知道的事实，管理器只管角色状态。
    private final RoleEventBus eventBus;

    public PlayerListener(RoleManager roleManager, RoleEventBus eventBus){
        this.roleManager = roleManager;
        this.eventBus = eventBus;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
        Player player = event.getEntity();
        if(roleManager.hasRole(player)){
            RoleInstance instance = roleManager.getRoleInstance(player);
            //死亡通知赶在清角色之前发布（载荷 = "死时那一刻"的角色）⇒ 订阅方见序
            //Death(死者) → Clear(死者)（后者由下面的 clearRole 沿既有路径发出）；
            //本插件"死亡即清角色"，此刻是外部拿到"死时是谁"的唯一时机
            eventBus.publish(new RoleDeathEvent(player.getUniqueId(), roleIdOf(instance)));
            roleManager.clearRole(player);
            HotbarItems.clearFrom(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event){
        Player player = event.getPlayer();
        HotbarItems.clearFrom(player);
    }

    //掉线即销毁角色实例 —— 与死亡同一条 clearRole 路径。
    //不挂起、不保留、不引入 config.yml；不预实现 pendingCleanup（仅当实测证明 quit 窗口内的清除未被持久化才补）。
    //效果：该实例的 ticker 与 BuffManager updater 两个 1-tick 任务被取消、playerRoleMap 中不再有条目。
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        roleManager.clearRole(player.getUniqueId());
 //掉线路径的物品清理不在此处重复 —— {@code RoleManager.clearRole(UUID)} 已就地用
 // {@code Bukkit.getPlayer(uuid)} 取到仍在线的 Player 并调 {@code HotbarItems.clearFrom}，
 // （清理已中心化到两个 clearRole 重载，本处不再重复调用）；
 // 同理"角色已清除"事件也由 clearRole(UUID) 就地发布，本处不再重复。
    }

    /** 实例所持角色的登记 id；实例为空（或角色为空）回 {@code null}（事件载荷的防御性口径）。 */
    private static String roleIdOf(RoleInstance instance) {
        return instance == null || instance.getRole() == null ? null : instance.getRole().getId();
    }

}
