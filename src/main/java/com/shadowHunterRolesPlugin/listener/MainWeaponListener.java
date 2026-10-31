package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.RolesContext;
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
    private final RolesContext context;

    public MainWeaponListener(RoleManager roleManager, RolesContext context){
        this.roleManager = roleManager;
        this.context = context;
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

        //★ 2026-10-31「无法攻击」排查修复：**先查组件、再取消原版伤害**。
        //旧顺序（先 setCancelled 再查组件）在"手里是角色已换/过期的主武器、当前角色没有对应组件"时，
        //会把事件取消掉却没有任何伤害补上 ⇒ 玩家看到的是"左键完全打不中敌人"，且日志里没有任何异常。
        RoleComponent component = instance.componentRegistry().getById(weaponId);
        if(!(component instanceof ActiveComponent active)){
            context.logger().warning("[attack] " + attacker.getName() + " 手持主武器项 '" + weaponId
                    + "' 但当前角色实例里没有对应组件；已保留原版伤害（不再静默取消攻击）");
            return;
        }
        //取消原版事件
        event.setCancelled(true);
        //攻击管道归本 listener：按 id 取通用面 → 判类型 → 受保护调用 → 请求重绘
        instance.invokeComponentHook(component, "onAttack", () -> active.onAttack(new AttackSignal(victim)));
        requestRepaint(instance);
    }

    /**
     * 施放管道（归本 listener）。
     * <p>读物品 id → 按 id 取通用面 → 判「声明了主动入口」→ 判冷却 → 受保护调用 → 请求重绘。
     *
     * @return 是否真的施放了（未命中 / 未声明主动入口 / 冷却中则 {@code false}）
     */
    private boolean cast(RoleInstance instance, String weaponId, CastTrigger trigger){
        RoleComponent component = instance.componentRegistry().getById(weaponId);
        if(!(component instanceof ActiveComponent active)) return false;
        if(active.isCoolingDown()) return false;
        instance.invokeComponentHook(component, "onCast", () -> active.onCast(new CastSignal(trigger)));
        requestRepaint(instance);
        return true;
    }

    /** 请求热键栏重绘（按 id 取渲染组件后调它的通用面；容器不代劳）。 */
    private void requestRepaint(RoleInstance instance){
        //渲染组件由本 listener 自己按 id 取（容器不持有它、也不认识它）
        RoleComponent render = instance.componentRegistry().getById(HotbarRenderComponent.ID);
        if(render instanceof HotbarRenderComponent hotbar) hotbar.markDirty();
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

        event.setCancelled(true);

        cast(instance, weaponId, CastTrigger.LEFT_CLICK);
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

        event.setCancelled(true);

        cast(instance, weaponId, CastTrigger.RIGHT_CLICK);
    }

    //ignoreCancelled：已取消的丢弃不重复取消、也不触发施法
    @EventHandler(ignoreCancelled = true)
    public void onQDrop(PlayerDropItemEvent event){
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if(!MainWeapon.Utils.isMainWeapon(item)) return;

        //取消事件
        event.setCancelled(true);

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null) return;

        String weaponId = MainWeapon.Utils.getWeaponId(item);

        //设置标记
        instance.setDroppingState(true);
        //1tick后清除标记
        context.scheduler().runLater(() -> {
            instance.setDroppingState(false);
        }, 1L);


        cast(instance, weaponId, CastTrigger.DROP);

    }

    //ignoreCancelled：已取消的点击不重复取消
    @EventHandler(ignoreCancelled = true)
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
