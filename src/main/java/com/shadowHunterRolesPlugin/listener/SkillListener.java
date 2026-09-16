package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SkillListener implements Listener {

    private final RoleManager roleManager;
    private final RolesContext context;

    public SkillListener(RoleManager roleManager, RolesContext context){
        this.roleManager = roleManager;
        this.context = context;
    }

    //右键释放技能
    @EventHandler
    public void onPlayerRightClickSkillItem(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if(!Skill.Utils.isSkillItem(item)) return;

        if(event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK){
            return;
        }

        event.setCancelled(true);


        String skillId = Skill.Utils.getSkillId(item);

        if(skillId == null){
            player.sendMessage(Component.text("Unknown skill!"));
            return;
        }


        RoleInstance instance = roleManager.getRoleInstance(player);

        if(instance == null){
            player.sendMessage(Component.text("You have no role yet, but trying to cast a skill!"));
            return;
        }

        //没有冷却完也return
        if(!instance.isSkillReady(skillId)) return;


        instance.castSkillRightClick(skillId, player);


    }

    //左键释放技能
    @EventHandler
    public void onPlayerLeftClickSkillItem(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if(!Skill.Utils.isSkillItem(item)) return;

        Action action = event.getAction();
        if(action != Action.LEFT_CLICK_AIR &&
            action != Action.LEFT_CLICK_BLOCK){
            return;
        }

        event.setCancelled(true);


        String skillId = Skill.Utils.getSkillId(item);

        if(skillId == null){
            player.sendMessage(Component.text("Unknown skill!"));
            return;
        }

        RoleInstance instance = roleManager.getRoleInstance(player);

        if(instance == null){
            player.sendMessage(Component.text("You have no role yet, but trying to cast a skill!"));
            return;
        }

        if(instance.isDropping()) return;

        //没有冷却完return
        if(!instance.isSkillReady(skillId)) return;

        instance.castSkillLeftClick(skillId, player);

    }

    //Q扔物品释放技能, 并且实现禁止丢弃技能物品
    @EventHandler
    public void onPlayerQDropSkillItem(PlayerDropItemEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if(!Skill.Utils.isSkillItem(item)) return;

        event.setCancelled(true);

        String skillId = Skill.Utils.getSkillId(item);

        if(skillId == null){
            player.sendMessage(Component.text("Unknown skill!"));
            return;
        }

        RoleInstance instance = roleManager.getRoleInstance(player);

        if(instance == null){
            player.sendMessage(Component.text("You have no role yet, but trying to cast a skill!"));
            return;
        }

        //设置标记
        instance.setDroppingState(true);
        //1tick后清除标记
        context.scheduler().runLater(() -> {
            instance.setDroppingState(false);
        }, 1L);


        if(!instance.isSkillReady(skillId)) return;

        instance.castSkillQDrop(skillId, player);
    }

    //禁止玩家拿出技能物品
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if(Skill.Utils.isSkillItem(clicked)){
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if(Skill.Utils.isSkillItem(cursor)){
            event.setCancelled(true);
            return;
        }
    }


}
