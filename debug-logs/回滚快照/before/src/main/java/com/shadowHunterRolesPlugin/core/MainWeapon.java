package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;


public abstract class MainWeapon {

    private final String id;
    private final Component displayName;
    private final Component description;
    private final Material icon;
    private final int cooldown;

    public MainWeapon(String id, Component displayName, Component description, Material icon, int cooldown){
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldown = cooldown;
    }

    public void onAttack(Player attacker, Player victim, RoleInstance instance){}

    public void onLeftClick(Player player, RoleInstance instance){}

    public boolean canRightClick() { return false; }

    public void onRightClick(Player player, RoleInstance instance){}

    public void onDrop(Player player, RoleInstance instance){}

    //创建物品
    public ItemStack createIconItem(RoleInstance instance) {
        if(instance == null) return null;
        boolean isReady = instance.isMainWeaponReady(id);
        boolean canCast = instance.getBuffManager().canUseMainWeapon();
        Material material;

        if (!isReady) {
            material = Material.STRUCTURE_VOID;
        } else if (!canCast) {
            material = Material.BARRIER;
        } else {
            material = icon;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();

        if(!isReady){
            meta.displayName(displayName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
            lore.add(Component.text("MainWeapon is on cooldown."));
        }
        else if(!canCast){
            meta.displayName(displayName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text("MainWeapon has been disabled."));
        }else {
            meta.displayName(displayName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            lore.add(Component.text("MainWeapon is ready."));
        }

        lore.add(Component.text("===================="));
        lore.add(description);

        meta.lore(lore);

        meta.getPersistentDataContainer().set(Utils.MAIN_WEAPON_KEY, PersistentDataType.STRING, id);

        item.setItemMeta(meta);

        return item;


    }

    //获取物品displayName
    public Component getDisplayName(RoleInstance instance){
        if(instance == null){
            return Component.text("RoleInstance is Null!");
        }

        boolean isReady = instance.isMainWeaponReady(id);
        boolean canCast = instance.getBuffManager().canUseMainWeapon();

        if(!isReady){
            return displayName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
        }
        else if(!canCast){
            return displayName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }else {
            return displayName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        }

    }




    public static class Utils{

        private static final NamespacedKey MAIN_WEAPON_KEY = new NamespacedKey(
                ShadowHunterRolesPlugin.getInstance(),
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

    //getters
    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
    public Material getIcon() { return icon; }
    public int getCooldown() { return cooldown; }

}
