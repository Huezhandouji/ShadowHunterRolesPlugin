package com.shadowHunterRolesPlugin.roleComponent.custom.red;

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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 「红」的四件套：**血纱帘 / 红衣角 / 净环裙 / 白高跟**。
 *
 * <ul>
 *   <li>每件保护 V；</li>
 *   <li>耐久上限 **40**，每秒自动回复 **1** 点；</li>
 *   <li>损坏后 **5 秒**补发一件，补发时耐久为 **10**；</li>
 *   <li>PDC 标记，换角色只回收自己的装备（不吞玩家自己的护甲）。</li>
 * </ul>
 */
public class RedEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "red_equipments_passive";

    public static final int MAX_DURABILITY = 40;
    public static final int REGEN_PER_SECOND = 1;
    public static final int RESPAWN_SECONDS = 5;
    public static final int RESPAWN_DURABILITY = 10;

    private static final NamespacedKey GEAR_KEY = KeyFactory.Registry.of("red_gear");
    private static final Color WINE = Color.fromRGB(0x6B, 0x0F, 0x1A);

    private final int[] missingSeconds = new int[4];
    private int ticker;

    public RedEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends PassiveSkill.Specification<RedEquipmentsPassive> {

        public Specification() {
            super(Component.text("血衣"),
                    List.of(
                            Component.text("血纱帘 / 红衣角（酒红皮革）保护 V"),
                            Component.text("净环裙 / 白高跟（铁）保护 V"),
                            Component.text("耐久上限 40，每秒回复 1，损坏 5 秒后补发（耐久 10）")
                    ));
        }

        @Override
        public RedEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new RedEquipmentsPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        player.getInventory().setHelmet(item(Material.LEATHER_HELMET, "血纱帘", WINE, MAX_DURABILITY));
        player.getInventory().setChestplate(item(Material.LEATHER_CHESTPLATE, "红衣角", WINE, MAX_DURABILITY));
        player.getInventory().setLeggings(item(Material.IRON_LEGGINGS, "净环裙", null, MAX_DURABILITY));
        player.getInventory().setBoots(item(Material.IRON_BOOTS, "白高跟", null, MAX_DURABILITY));
    }

    @Override
    public void update() {
        if (++ticker < 20) {
            return;
        }
        ticker = 0;
        Player player = svc().self().player();
        if (player == null || !player.isOnline()) {
            return;
        }
        maintain(player, 0, player.getInventory().getHelmet(),
                item -> player.getInventory().setHelmet(item),
                () -> item(Material.LEATHER_HELMET, "血纱帘", WINE, RESPAWN_DURABILITY));
        maintain(player, 1, player.getInventory().getChestplate(),
                item -> player.getInventory().setChestplate(item),
                () -> item(Material.LEATHER_CHESTPLATE, "红衣角", WINE, RESPAWN_DURABILITY));
        maintain(player, 2, player.getInventory().getLeggings(),
                item -> player.getInventory().setLeggings(item),
                () -> item(Material.IRON_LEGGINGS, "净环裙", null, RESPAWN_DURABILITY));
        maintain(player, 3, player.getInventory().getBoots(),
                item -> player.getInventory().setBoots(item),
                () -> item(Material.IRON_BOOTS, "白高跟", null, RESPAWN_DURABILITY));
    }

    private void maintain(Player player, int slot, ItemStack current,
                          java.util.function.Consumer<ItemStack> setter,
                          java.util.function.Supplier<ItemStack> factory) {
        if (ours(current)) {
            missingSeconds[slot] = 0;
            ItemMeta meta = current.getItemMeta();
            if (meta instanceof Damageable damageable && damageable.getDamage() > 0) {
                damageable.setDamage(Math.max(0, damageable.getDamage() - REGEN_PER_SECOND));
                current.setItemMeta(meta);
            }
            return;
        }
        if (current != null && !current.getType().isAir()) {
            // 玩家自己的装备：不覆盖、不计数
            missingSeconds[slot] = 0;
            return;
        }
        missingSeconds[slot]++;
        if (missingSeconds[slot] >= RESPAWN_SECONDS) {
            missingSeconds[slot] = 0;
            setter.accept(factory.get());
        }
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

    private static ItemStack item(Material material, String name, Color color, int durability) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(name, NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        if (color != null && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
        }
        meta.addEnchant(Enchantment.PROTECTION, 5, true);
        meta.setUnbreakable(false);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(MAX_DURABILITY);
            damageable.setDamage(Math.max(0, MAX_DURABILITY - durability));
        }
        meta.getPersistentDataContainer().set(GEAR_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }
}
