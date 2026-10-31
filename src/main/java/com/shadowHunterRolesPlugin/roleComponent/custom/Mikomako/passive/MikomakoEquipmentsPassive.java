package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.passive;

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
 * 被动装备：**☯独苍梅☯ / ☯鹤羽披☯ / ☯血凝恨☯ / ☯踏百川☯**。
 *
 * <table>
 *   <tr><th>槽位</th><th>物品</th><th>附魔</th></tr>
 *   <tr><td>头盔</td><td>☯独苍梅☯（铁头盔）</td><td>保护 I</td></tr>
 *   <tr><td>胸甲</td><td>☯鹤羽披☯（灰白皮革胸甲）</td><td>保护 II</td></tr>
 *   <tr><td>护腿</td><td>☯血凝恨☯（深红皮革裤子）</td><td>保护 I + 火焰保护 I</td></tr>
 *   <tr><td>靴子</td><td>☯踏百川☯（下界合金靴）</td><td>保护 I + 摔落保护 III + 灵魂疾行 III</td></tr>
 * </table>
 *
 * <h2>★★ 与旧版的差别（这是本次修的一个真 BUG）</h2>
 * 旧版 {@code stop()} **无条件清空四个护甲槽** ⇒ 换角色时会把玩家自己的装备一起吞掉。
 * 现在每件装备写入 PDC 标记 {@code mikomako_gear}，{@code stop()} **只回收带标记的那几件**。
 * 另外补了"不可破坏"（需求没写，但角色装备没有补发途径，破了就永久没了 —— 如实申报）。
 */
public class MikomakoEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_equippments_passive";

    /** 装备归属标记（★ 静态键：离线测试需装 KeyFactory 桩）。 */
    private static final NamespacedKey GEAR_KEY = KeyFactory.Registry.of("mikomako_gear");

    public MikomakoEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends PassiveSkill.Specification<MikomakoEquipmentsPassive> {

        public Specification() {
            super(Component.text("四梅装"),
                    List.of(
                            Component.text("☯独苍梅☯ 铁头盔（保护 I）"),
                            Component.text("☯鹤羽披☯ 灰白皮革胸甲（保护 II）"),
                            Component.text("☯血凝恨☯ 深红皮革护腿（保护 I / 火保 I）"),
                            Component.text("☯踏百川☯ 下界合金靴（保护 I / 摔落 III / 灵魂疾行 III）")
                    ));
        }

        @Override
        public MikomakoEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new MikomakoEquipmentsPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }

        ItemStack helmet = base(Material.IRON_HELMET, "☯独苍梅☯", NamedTextColor.WHITE, null);
        helmet = with(helmet, Enchantment.PROTECTION, 1);

        ItemStack chest = base(Material.LEATHER_CHESTPLATE, "☯鹤羽披☯", NamedTextColor.WHITE,
                Color.fromRGB(0xE8, 0xE8, 0xE8));
        chest = with(chest, Enchantment.PROTECTION, 2);

        ItemStack legs = base(Material.LEATHER_LEGGINGS, "☯血凝恨☯", NamedTextColor.DARK_RED,
                Color.fromRGB(0x8B, 0x00, 0x00));
        legs = with(legs, Enchantment.PROTECTION, 1);
        legs = with(legs, Enchantment.FIRE_PROTECTION, 1);

        ItemStack boots = base(Material.NETHERITE_BOOTS, "☯踏百川☯", NamedTextColor.AQUA, null);
        boots = with(boots, Enchantment.PROTECTION, 1);
        boots = with(boots, Enchantment.FEATHER_FALLING, 3);
        boots = with(boots, Enchantment.SOUL_SPEED, 3);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
    }

    @Override
    public void stop() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        // 只回收带本系统标记的装备（旧版无条件清空 ⇒ 会吞玩家自己的护甲）
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

    private boolean ours(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(GEAR_KEY, PersistentDataType.BYTE);
    }

    /** 底胚：名字 + 皮革染色 + 不可破坏 + 归属标记。 */
    private static ItemStack base(Material material, String name, NamedTextColor color, Color leatherColor) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (leatherColor != null && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(leatherColor);
        }
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(GEAR_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 追加一条附魔（保留既有 meta，包括 PDC 标记）。 */
    private static ItemStack with(ItemStack stack, Enchantment enchantment, int level) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addEnchant(enchantment, level, true);
        stack.setItemMeta(meta);
        return stack;
    }
}
