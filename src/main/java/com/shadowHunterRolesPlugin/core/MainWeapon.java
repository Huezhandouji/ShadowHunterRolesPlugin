package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CombatHook;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import java.util.ArrayList;
import java.util.List;


public abstract class MainWeapon extends ActiveComponent implements CombatHook {

    /**
     * 阶段 4 B0b-3：改基到 {@link ActiveComponent}（kind = MAIN_WEAPON、**`energyCost` 恒传 0**：
     * 今天武器没有 energyCost 字段，填非 0 会让武器图标多出一个今天不存在的 `ENERGY LACK` 态）。
     * 五个字段与对应 getter 已上移到基类；**构造参数顺序不变**（icon 在 cooldown 之前）
     * ⇒ 2 个武器子类的 `super(...)` 一字不改。旧回调 `onAttack/onLeftClick/onRightClick/onDrop`（后者已在 T-1 ④ 删除）
     * 保留（收尾开关 false、`isMigrated()` 全 false ⇒ 派发仍全走旧路径）。
     */
    public MainWeapon(String id, Component displayName, Component description, Material icon, int cooldown){
        super(id, displayName, description, cooldown, 0, icon, ItemKind.MAIN_WEAPON);
    }

    /** 攻击路径的新契约：今天 listener 在攻击后**无条件**启动武器冷却 ⇒ 默认 `SUCCEED`（设计 §4.3）。 */
    @Override
    public CastResult onAttack(AttackSignal signal){
        return CastResult.SUCCEED;
    }

    //创建物品
    public ItemStack createIconItem(RoleInstance instance) {
        if(instance == null) return null;
        boolean isReady = instance.isMainWeaponReady(getId());
        boolean canCast = instance.getBuffManager().canUseMainWeapon();
        Material material;

        if (!isReady) {
            material = Material.STRUCTURE_VOID;
        } else if (!canCast) {
            material = Material.BARRIER;
        } else {
            material = getIcon();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();

        if(!isReady){
            meta.displayName(getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
            lore.add(Component.text("MainWeapon is on cooldown."));
        }
        else if(!canCast){
            meta.displayName(getDisplayName().color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text("MainWeapon has been disabled."));
        }else {
            meta.displayName(getDisplayName().color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            lore.add(Component.text("MainWeapon is ready."));
        }

        lore.add(Component.text("===================="));
        lore.add(getDescription());

        meta.lore(lore);

        meta.getPersistentDataContainer().set(Utils.MAIN_WEAPON_KEY, PersistentDataType.STRING, getId());

        item.setItemMeta(meta);

        return item;


    }

    //获取物品displayName
    public Component getDisplayName(RoleInstance instance){
        if(instance == null){
            return Component.text("RoleInstance is Null!");
        }

        boolean isReady = instance.isMainWeaponReady(getId());
        boolean canCast = instance.getBuffManager().canUseMainWeapon();

        if(!isReady){
            return getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
        }
        else if(!canCast){
            return getDisplayName().color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }else {
            return getDisplayName().color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        }

    }




    public static class Utils{

        private static final NamespacedKey MAIN_WEAPON_KEY = KeyFactory.Registry.of(
                "main_weapon_id"
        );

        public static boolean isMainWeapon(ItemStack item){
            if(item == null || item.getType().isAir()) return false;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return false;
            return meta.getPersistentDataContainer().has(MAIN_WEAPON_KEY, PersistentDataType.STRING);
        }

        public static String getWeaponId(ItemStack item){
            if(item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return null;
        return meta.getPersistentDataContainer().get(MAIN_WEAPON_KEY, PersistentDataType.STRING);
        }

    }

    //getters 已上移到 ActiveComponent（getId/getDisplayName/getDescription/getIcon/getCooldownTicks/getEnergyCost/getKind）

}
