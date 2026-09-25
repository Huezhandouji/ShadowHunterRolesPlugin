package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.Role;
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

    public PlayerListener(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
        Player player = event.getEntity();
        if(roleManager.hasRole(player)){
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
 //★ 掉线路径的物品清理**不在此处重复** —— `RoleManager.clearRole(UUID)` 已就地用
 //  {@code Bukkit.getPlayer(uuid)} 取到**仍在线**的 Player 并调 {@code HotbarItems.clearFrom} ✓
 //  （清理已中心化到两个 clearRole 重载 ⇒ 本处不再重复调用）。
    }

}
