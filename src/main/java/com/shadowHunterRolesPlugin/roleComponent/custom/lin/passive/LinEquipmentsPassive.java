package com.shadowHunterRolesPlugin.roleComponent.custom.lin.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;

/**
 * 林（{@code lin}）的被动「**殉道者的遗装**」：发放四件专属护甲。
 *
 * <h2>四件（槽位 → 材质 → 附魔，逐条对齐 2026-10-31 需求）</h2>
 * <ul>
 *   <li><b>殉道之人</b> · 黑色皮革帽（{@link Material#LEATHER_HELMET} + 黑色染色）· 保护 II；</li>
 *   <li><b>苦痛之人</b> · 黑色皮革胸甲（{@link Material#LEATHER_CHESTPLATE} + 黑色染色）· 保护 II；</li>
 *   <li><b>不幸之人</b> · 下界合金护腿（{@link Material#NETHERITE_LEGGINGS}）· 保护 I；</li>
 *   <li><b>命定之人</b> · 下界合金靴（{@link Material#NETHERITE_BOOTS}）· 保护 I + 灵魂急行 III + 摔落保护 V；</li>
 * </ul>
 * <p><b>四件另统一带「绑定诅咒 I」</b>（2026-10-31 需求："统一加上绑定诅咒附魔"）——
 * 穿上就脱不掉；与"进场即换装、离场清槽"的既有口径不冲突（{@code stop()} 直接写槽，不靠玩家手动脱）。
 * <p>四件名字取自"殉道者四个身份"这层叙事：殉道、苦痛、不幸、命定。
 *
 * <h2>★ 名字样式（需求："灰色大字、不倾斜"）</h2>
 * 「大字」在本工程既有口径 = {@link TextDecoration#BOLD}（物品名没有字号维度的合法表达）；
 * 「不倾斜」= 显式 {@code ITALIC false}（原版物品名默认斜体，不写这一条就是斜的）。
 * 因此四件统一 {@code GRAY + BOLD + ITALIC=false}；同类先例见 {@code LetheEquipmentsPassive}。
 *
 * <h2>★ 为什么附魔用 {@code meta.addEnchant(ench, level, true)} 的第三参 {@code true}</h2>
 * 第三参 = **忽略等级上限**。本需求的"灵魂急行 III / 摔落保护 V"属非常规获取等级
 * （摔落保护原版上限 IV、灵魂急行需宝箱/交易），不开这个开关会被平台按上限截断或拒绝，
 * 附魔等级就不再是需求值。先例：{@code LetheEquipmentsPassive}（摔落保护 X）。
 *
 * <h2>★ 为什么 {@code stop()} 直接把四个槽置 null</h2>
 * 本类**发的是四个槽**（头盔 / 胸甲 / 护腿 / 靴子全覆盖），因此可以严格按 {@code start()} 对称回收：
 * 发几件就收几件。已知代价（与 {@code LetheEquipmentsPassive} / {@code ResistEquipmentsPassive} 同规）：
 * 玩家原本穿在这四个槽里的装备会被覆盖并随 {@code stop()} 一并删除 —— 这是"进场即换装"的既有口径。
 * 绑定诅咒对"进场即换装"没有功能影响（{@code stop()} 直接写槽，不依赖玩家手动脱下）。
 */
public class LinEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "lin_equipments_passive";

    // ───────── 四件装备的名字（需求逐字）─────────

    /** 头盔名。 */
    public static final String HELMET_NAME = "殉道之人";

    /** 胸甲名。 */
    public static final String CHESTPLATE_NAME = "苦痛之人";

    /** 护腿名。 */
    public static final String LEGGINGS_NAME = "不幸之人";

    /** 靴子名。 */
    public static final String BOOTS_NAME = "命定之人";

    // ───────── 四件装备的附魔等级（每处只声明一次）─────────

    /** 头盔：保护 II。 */
    private static final int HELMET_PROTECTION_LEVEL = 2;

    /** 胸甲：保护 II。 */
    private static final int CHESTPLATE_PROTECTION_LEVEL = 2;

    /** 护腿：保护 I。 */
    private static final int LEGGINGS_PROTECTION_LEVEL = 1;

    /** 靴子：保护 I。 */
    private static final int BOOTS_PROTECTION_LEVEL = 1;

    /** 靴子：灵魂急行 III。 */
    private static final int BOOTS_SOUL_SPEED_LEVEL = 3;

    /** 靴子：摔落保护 V。 */
    private static final int BOOTS_FEATHER_FALLING_LEVEL = 5;

    /** 四件统一附带的绑定诅咒等级（需求："统一加上绑定诅咒附魔"）。 */
    private static final int BINDING_CURSE_LEVEL = 1;

    // ───────── 皮革染色（需求：黑色皮革帽 / 胸甲）─────────

    /** 黑色皮革的染色（纯黑）。 */
    private static final Color LEATHER_COLOR = Color.BLACK;

    // ───────── 需求指定的名字样式：灰色 + 大字 + 不倾斜 ─────────

    /** 四件共用的名字颜色（需求："灰色"）。 */
    private static final NamedTextColor NAME_COLOR = NamedTextColor.GRAY;

    public LinEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符：被动无栏位 ⇒ 不占热键栏；描述 4 行 = 四件装备各一行。 */
    public static final class Specification extends PassiveSkill.Specification<LinEquipmentsPassive> {

        public Specification() {
            super(Component.text("殉道者的遗装"),
                    List.of(
                            Component.text("殉道之人 · 黑色皮革帽（保护 II / 绑定诅咒 I）"),
                            Component.text("苦痛之人 · 黑色皮革胸甲（保护 II / 绑定诅咒 I）"),
                            Component.text("不幸之人 · 下界合金护腿（保护 I / 绑定诅咒 I）"),
                            Component.text("命定之人 · 下界合金靴（保护 I / 灵魂急行 III / 摔落保护 V / 绑定诅咒 I）")
                    ));
        }

        @Override
        public LinEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new LinEquipmentsPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 开始生效：穿上四件（顺序：头盔 → 胸甲 → 护腿 → 靴子，与槽位自上而下一致）。 */
    @Override
    public void start() {
        Player player = selfPlayer();
        if (player == null) {
            return;
        }
        player.getInventory().setHelmet(build(Material.LEATHER_HELMET, HELMET_NAME,
                LEATHER_COLOR, Enchantment.PROTECTION, HELMET_PROTECTION_LEVEL));
        player.getInventory().setChestplate(build(Material.LEATHER_CHESTPLATE, CHESTPLATE_NAME,
                LEATHER_COLOR, Enchantment.PROTECTION, CHESTPLATE_PROTECTION_LEVEL));
        player.getInventory().setLeggings(build(Material.NETHERITE_LEGGINGS, LEGGINGS_NAME,
                null, Enchantment.PROTECTION, LEGGINGS_PROTECTION_LEVEL));
        ItemStack boots = build(Material.NETHERITE_BOOTS, BOOTS_NAME,
                null, Enchantment.PROTECTION, BOOTS_PROTECTION_LEVEL);
        addBootsEnchants(boots);
        player.getInventory().setBoots(boots);
    }

    /**
     * 停止生效：**发几件收几件** —— 四槽置 null（见类注释的已知代价）。
     * <p>只清本组件发放的四个槽，不碰背包与热键栏。
     */
    @Override
    public void stop() {
        Player player = selfPlayer();
        if (player == null) {
            return;
        }
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
    }

    // ───────── 装备发放 ─────────

    /**
     * 造一件装备：Adventure 显示名（灰色、加粗、去斜体）+ 固定保护附魔 + **绑定诅咒 I**。
     * <p>{@code addEnchant} 的第三参 {@code true} = 忽略等级上限（理由见类注释）。
     * <p>{@code leatherColor} 非 null 时对皮革甲染色（只有皮革类 meta 才接受染色）；meta 取不到时
     * 原样返回（平台异常的兜底，不抛）。
     */
    private static ItemStack build(Material material, String name, Color leatherColor,
                                   Enchantment protection, int protectionLevel) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(name, NAME_COLOR, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(protection, protectionLevel, true);
        meta.addEnchant(Enchantment.BINDING_CURSE, BINDING_CURSE_LEVEL, true);
        if (leatherColor != null && meta instanceof LeatherArmorMeta leather) {
            leather.setColor(leatherColor);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 给靴子补两条附魔：灵魂急行 III + 摔落保护 V（需求逐字）。
     * <p>单独成法是为了让"靴子专属附魔"与"四件共有的保护/绑定诅咒"在同一条注释里可分辨：
     * 前者只此一件，后者走 {@link #build}。
     */
    private static void addBootsEnchants(ItemStack boots) {
        ItemMeta meta = boots.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.addEnchant(Enchantment.SOUL_SPEED, BOOTS_SOUL_SPEED_LEVEL, true);
        meta.addEnchant(Enchantment.FEATHER_FALLING, BOOTS_FEATHER_FALLING_LEVEL, true);
        boots.setItemMeta(meta);
    }

    // ───────── 内部工具 ─────────

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }
}
