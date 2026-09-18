package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.passive;

import com.shadowHunterRolesPlugin.core.PassiveSkill;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public class MeiqiheziEquipmentsPassive extends PassiveSkill implements LifecycleAware {

    public MeiqiheziEquipmentsPassive() {
        super("meiqihezi_equippments_passive", Component.text("穿戴装备"), Component.text("ccb"));
    }

    @Override
    public void start(Player player, RoleInstance instance) {
        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        {
            ItemMeta meta = helmet.getItemMeta();
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, false);
            meta.addEnchant(Enchantment.PROTECTION, 1, false);
            meta.addEnchant(Enchantment.PROJECTILE_PROTECTION, 1, false);
            meta.setUnbreakable(true);
            helmet.setItemMeta(meta);
        }

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        {
            ItemMeta meta = chestplate.getItemMeta();
            LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) meta;
            leatherArmorMeta.setColor(Color.fromRGB(139, 0, 0));
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, false);
            meta.addEnchant(Enchantment.PROTECTION, 2, false);
            meta.setUnbreakable(true);
            chestplate.setItemMeta(meta);
        }

        ItemStack leggings = new ItemStack(Material.IRON_LEGGINGS);
        {
            ItemMeta meta = leggings.getItemMeta();
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, false);
            meta.addEnchant(Enchantment.PROTECTION, 1, false);
            meta.addEnchant(Enchantment.FIRE_PROTECTION, 1, false);
            meta.setUnbreakable(true);
            leggings.setItemMeta(meta);
        }

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        {
            ItemMeta meta = boots.getItemMeta();
            LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) meta;
            leatherArmorMeta.setColor(Color.GRAY);
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, false);
            meta.addEnchant(Enchantment.PROTECTION, 1, false);
            meta.addEnchant(Enchantment.FEATHER_FALLING, 3, false);
            meta.setUnbreakable(true);
            boots.setItemMeta(meta);
        }

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
    }

    @Override
    public void stop(Player player, RoleInstance instance) {
    }
}
