package com.shadowHunterRolesPlugin.roleComponent.custom.resist.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 「抗争」的衣物：四件护甲（每件**保护 I**、不可破坏）。
 *
 * <p>槽位与材质逐条对齐需求：
 * <ul>
 *   <li>头盔 = 下界合金头盔 · {@code ▓磐山▓}</li>
 *   <li>胸甲 = 铁胸甲 · {@code ▓亡我▓}</li>
 *   <li>护腿 = 下界合金护腿 · {@code ▓重林▓}</li>
 *   <li>靴子 = 铁靴子 · {@code ▓阔地▓}</li>
 * </ul>
 *
 * <p>与 {@code MikomakoEquipmentsPassive} 同规：{@code start()} 直接写入四槽，
 * {@code stop()} 清空四槽（已知代价：玩家原本穿在这四槽的其它装备会被一并删除）。
 */
public class ResistEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_equipments_passive";

    /** 四件衣物的名字（需求逐字）。 */
    public static final String HELMET_NAME = "▓磐山▓";
    public static final String CHESTPLATE_NAME = "▓亡我▓";
    public static final String LEGGINGS_NAME = "▓重林▓";
    public static final String BOOTS_NAME = "▓阔地▓";

    public ResistEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 被动描述符（无栏位）。 */
    public static final class Specification extends PassiveSkill.Specification<ResistEquipmentsPassive> {

        public Specification() {
            super(Component.text("抗争的衣物"),
                    List.of(
                            Component.text("▓磐山▓ · 下界合金头盔（保护 I）"),
                            Component.text("▓亡我▓ · 铁胸甲（保护 I）"),
                            Component.text("▓重林▓ · 下界合金护腿（保护 I）"),
                            Component.text("▓阔地▓ · 铁靴子（保护 I）")
                    ));
        }

        @Override
        public ResistEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new ResistEquipmentsPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        player.getInventory().setHelmet(build(Material.NETHERITE_HELMET, HELMET_NAME));
        player.getInventory().setChestplate(build(Material.IRON_CHESTPLATE, CHESTPLATE_NAME));
        player.getInventory().setLeggings(build(Material.NETHERITE_LEGGINGS, LEGGINGS_NAME));
        player.getInventory().setBoots(build(Material.IRON_BOOTS, BOOTS_NAME));
    }

    @Override
    public void stop() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        //与 start 严格对称
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
    }

    private static ItemStack build(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.PROTECTION, 1, false);
            meta.setUnbreakable(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
