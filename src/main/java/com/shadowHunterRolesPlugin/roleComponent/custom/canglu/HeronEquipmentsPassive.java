package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 苍鹭四件套（2026-10-25）：残歌 / 遗志 / 呼唤 / 走梦。
 *
 * <p>残歌（青色皮革帽·弹射物保护 V）、遗志（青色皮革衣·保护 I）、
 * 呼唤（钻石腿·弹射物保护 V）、走梦（钻石靴·摔落保护 X）；不可损坏，PDC 标记只回收自己的。
 */
public class HeronEquipmentsPassive extends PassiveSkill {

    public static final String ID = "heron_equipments_passive";

    private static final NamespacedKey GEAR_KEY = KeyFactory.Registry.of("heron_gear");
    private static final Color CYAN = Color.fromRGB(0x2E, 0x8B, 0x8B);

    public HeronEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification<HeronEquipmentsPassive> {

        public Specification() {
            super(Component.text("苍鹭 · 四件套"),
                    List.of(
                            Component.text("残歌：青色皮革帽 · 弹射物保护 V"),
                            Component.text("遗志：青色皮革衣 · 保护 I"),
                            Component.text("呼唤：钻石护腿 · 弹射物保护 V"),
                            Component.text("走梦：钻石靴 · 摔落保护 X")
                    ));
        }

        @Override
        public HeronEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new HeronEquipmentsPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        player.getInventory().setHelmet(item(Material.LEATHER_HELMET, "残歌", CYAN, Enchantment.PROJECTILE_PROTECTION, 5));
        player.getInventory().setChestplate(item(Material.LEATHER_CHESTPLATE, "遗志", CYAN, Enchantment.PROTECTION, 1));
        player.getInventory().setLeggings(item(Material.DIAMOND_LEGGINGS, "呼唤", null, Enchantment.PROJECTILE_PROTECTION, 5));
        player.getInventory().setBoots(item(Material.DIAMOND_BOOTS, "走梦", null, Enchantment.FEATHER_FALLING, 10));
    }

    @Override
    public void stop() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        if (ours(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(null);
        }
        if (ours(player.getInventory().getChestplate())) {
            player.getInventory().setChestplate(null);
        }
        if (ours(player.getInventory().getLeggings())) {
            player.getInventory().setLeggings(null);
        }
        if (ours(player.getInventory().getBoots())) {
            player.getInventory().setBoots(null);
        }
    }

    private boolean ours(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(GEAR_KEY, PersistentDataType.BYTE);
    }

    private static ItemStack item(Material material, String name, Color color,
                                  Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        if (color != null && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        }
        meta.addEnchant(enchantment, level, true);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(GEAR_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }
}
