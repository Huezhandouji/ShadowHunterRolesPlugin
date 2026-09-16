package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class MainWeaponListener implements Listener {

    protected final RoleManager roleManager;

    public MainWeaponListener(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    //攻击玩家时
    @EventHandler
    public void onAttackPlayer(EntityDamageByEntityEvent event){
        if(!(event.getDamager() instanceof Player attacker)) return;
        if(!(event.getEntity() instanceof Player victim)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();

        if(!MainWeapon.Utils.isMainWeapon(item)) return;

        RoleInstance instance = roleManager.getRoleInstance(attacker);

        //如果没有角色返回
        if(instance == null) return;

        String weaponId = MainWeapon.Utils.getWeaponId(item);
        MainWeapon weapon = instance.getMainWeaponById(weaponId);
        if(weapon == null) return;

        //取消原版事件
        event.setCancelled(true);

        //检查冷却
        if(!instance.isMainWeaponReady(weaponId)){
            return;
        }

        //开始冷却
        instance.startMainWeaponCooldown(weaponId, weapon.getCooldown());

        //执行攻击逻辑
        weapon.onAttack(attacker, victim, instance);
        weapon.onLeftClick(attacker, instance);
    }

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        Action action = event.getAction();
        if(action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;



        if(!MainWeapon.Utils.isMainWeapon(item)) return;

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null) return;

        if(instance.isDropping()) return;


        String weaponId = MainWeapon.Utils.getWeaponId(item);
        MainWeapon weapon = instance.getMainWeaponById(weaponId);
        if(weapon == null) return;

        event.setCancelled(true);

        if(!instance.isMainWeaponReady(weaponId)) return;

        instance.castMainWeaponLeftClick(weaponId, player);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        Action action = event.getAction();
        if(action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if(!MainWeapon.Utils.isMainWeapon(item)) return;

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null) return;

        String weaponId = MainWeapon.Utils.getWeaponId(item);
        MainWeapon weapon = instance.getMainWeaponById(weaponId);
        if(weapon == null) return;

        event.setCancelled(true);

        if(!instance.isMainWeaponReady(weaponId)) return;

        instance.castMainWeaponRightClick(weaponId, player);
    }

    @EventHandler
    public void onQDrop(PlayerDropItemEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if(!MainWeapon.Utils.isMainWeapon(item)) return;

        //取消事件
        event.setCancelled(true);

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null) return;

        String weaponId = MainWeapon.Utils.getWeaponId(item);
        MainWeapon weapon = instance.getMainWeaponById(weaponId);
        if(weapon == null) return;

        //设置标记
        instance.setDroppingState(true);
        //1tick后清除标记
        Bukkit.getScheduler().runTaskLater(ShadowHunterRolesPlugin.getInstance(), () -> {
            instance.setDroppingState(false);
        }, 1L);




        if(!instance.isMainWeaponReady(weaponId)) return;



        instance.castMainWeaponQDrop(weaponId, player);

    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if(MainWeapon.Utils.isMainWeapon(clicked)){
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if(MainWeapon.Utils.isMainWeapon(cursor)){
            event.setCancelled(true);
            return;
        }
    }
}
