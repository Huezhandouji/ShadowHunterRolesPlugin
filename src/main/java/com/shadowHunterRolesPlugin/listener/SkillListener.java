package com.shadowHunterRolesPlugin.listener;
import com.shadowHunterRolesPlugin.core.component.ComponentRegistry;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

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

        //★ 冷却闸门与施放**都归本 listener**（容器已删 isSkillReady / handleCast）


        if(instance.componentRegistry().getById(skillId) == null){
            player.sendMessage(Component.text("unknown skill!"));
            return;
        }
        cast(instance, skillId, CastTrigger.RIGHT_CLICK);


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

        //★ 同上

        if(instance.componentRegistry().getById(skillId) == null){
            player.sendMessage(Component.text("unknown skill!"));
            return;
        }
        cast(instance, skillId, CastTrigger.LEFT_CLICK);

    }

    //Q扔物品释放技能, 并且实现禁止丢弃技能物品
    //ignoreCancelled：别的插件已取消该事件时，本处理器**不再**重复 setCancelled，
    //**也不再**触发施法（取消 = 这次丢弃没有真的发生 ⇒ 不该被当成一次技能输入）✓
    @EventHandler(ignoreCancelled = true)
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


        //★ 同上

        if(instance.componentRegistry().getById(skillId) == null){
            player.sendMessage(Component.text("unknown skill!"));
            return;
        }
        cast(instance, skillId, CastTrigger.DROP);
    }

    //禁止玩家拿出技能物品
    //ignoreCancelled：已取消的点击不重复取消 ✓
    @EventHandler(ignoreCancelled = true)
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




    /**
     * **施放管道**（★ 原先住在容器 `handleCast` / `isSkillReady`，现归本 listener）。
     * <p>按 id 取通用面 → 判「声明了主动入口」→ 判冷却 → 受保护调用 → 请求重绘。
     *
     * @return 是否真的施放了（未命中 / 未声明主动入口 / 冷却中 ⇒ {@code false}）
     */
    private boolean cast(RoleInstance instance, String skillId, CastTrigger trigger){
        RoleComponent component = instance.componentRegistry().getById(skillId);
        if(!(component instanceof ActiveComponent active)) return false;
        if(active.isCoolingDown()) return false;
        instance.invokeComponentHook(component, "onCast", () -> active.onCast(new CastSignal(trigger)));
        //★ 渲染组件**由本 listener 自己按 id 取**（容器不持有它、也不认识它）
        RoleComponent render = instance.componentRegistry().getById(HotbarRenderComponent.ID);
        if(render != null) render.requestRepaint();
        return true;
    }
}