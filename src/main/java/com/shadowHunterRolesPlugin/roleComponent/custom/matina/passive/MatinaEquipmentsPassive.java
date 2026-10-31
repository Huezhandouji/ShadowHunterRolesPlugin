package com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive;

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
 * 马提娜（{@code matina}）的被动「**狂躁牧师的圣装**」：发放四件专属护甲。
 *
 * <h2>四件（槽位 → 材质 → 附魔，逐条对齐 2026-10-31 需求）</h2>
 * <ul>
 *   <li><b>十字军修女头巾</b> · 锁链头盔（{@link Material#CHAINMAIL_HELMET}）· 保护 I · 绑定诅咒 I；</li>
 *   <li><b>唤以迢迢天命的装甲</b> · 铁胸甲（{@link Material#IRON_CHESTPLATE}）· 保护 I · 绑定诅咒 I；</li>
 *   <li><b>经常脏的红色装甲隔离布</b> · 红色皮革裤（{@link Material#LEATHER_LEGGINGS} + 红色染色）· 保护 I · 绑定诅咒 I；</li>
 *   <li><b>年复一年的白色旧军鞋</b> · 铁靴（{@link Material#IRON_BOOTS}）· 保护 II · 绑定诅咒 I。</li>
 * </ul>
 *
 * <h2>★ 名字样式（需求："白色大字、不倾斜"）</h2>
 * 「大字」在本工程既有口径 = {@link TextDecoration#BOLD}（物品名没有字号维度的合法表达）；
 * 「不倾斜」= 显式 {@code ITALIC false}（原版物品名默认斜体，不写这一条就是斜的）。
 * 因此四件统一 {@code WHITE + BOLD + ITALIC=false}；同类先例见 {@code LetheEquipmentsPassive}。
 *
 * <h2>★ 为什么附魔用 {@code meta.addEnchant(ench, level, true)} 的第三参 {@code true}</h2>
 * 第三参 = **忽略等级上限**。本需求的绑定诅咒属"非常规获取"附魔，后续若有超出附魔台上限的等级
 * 也不会被平台截断；与同族装备被动保持同一条写法。
 *
 * <h2>★ 为什么 {@code stop()} 直接把四个槽置 null</h2>
 * 本类**发的是四个槽**（头盔 / 胸甲 / 护腿 / 靴子全覆盖），因此可以严格按 {@code start()} 对称回收：
 * 发几件就收几件。已知代价（与 {@code LetheEquipmentsPassive} / {@code ResistEquipmentsPassive} 同规）：
 * 玩家原本穿在这四个槽里的装备会被覆盖并随 {@code stop()} 一并删除 —— 这是"进场即换装"的既有口径。
 * 绑定诅咒对"进场即换装"没有功能影响（{@code stop()} 直接写槽，不依赖玩家手动脱下），它是需求要求的
 * **束缚感**：穿上就脱不掉。
 */
public class MatinaEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "matina_equipments_passive";

    // ───────── 四件装备的名字（需求逐字）─────────

    /** 头盔名。 */
    public static final String HELMET_NAME = "十字军修女头巾";

    /** 胸甲名。 */
    public static final String CHESTPLATE_NAME = "唤以迢迢天命的装甲";

    /** 护腿名。 */
    public static final String LEGGINGS_NAME = "经常脏的红色装甲隔离布";

    /** 靴子名。 */
    public static final String BOOTS_NAME = "年复一年的白色旧军鞋";

    // ───────── 四件装备的附魔等级（每处只声明一次）─────────

    /** 头盔：保护 I。 */
    private static final int HELMET_PROTECTION_LEVEL = 1;

    /** 胸甲：保护 I。 */
    private static final int CHESTPLATE_PROTECTION_LEVEL = 1;

    /** 护腿：保护 I。 */
    private static final int LEGGINGS_PROTECTION_LEVEL = 1;

    /** 靴子：保护 II。 */
    private static final int BOOTS_PROTECTION_LEVEL = 2;

    /** 四件共用的绑定诅咒等级（需求：四件各带绑定诅咒）。 */
    private static final int BINDING_CURSE_LEVEL = 1;

    // ───────── 皮革染色（需求：红色皮革裤）─────────

    /** 红色皮革裤的染色（纯红）。 */
    private static final Color LEGGINGS_LEATHER_COLOR = Color.RED;

    // ───────── 需求指定的名字样式：白色 + 大字 + 不倾斜 ─────────

    /** 四件共用的名字颜色（需求："白色"）。 */
    private static final NamedTextColor NAME_COLOR = NamedTextColor.WHITE;

    public MatinaEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符：被动无栏位 ⇒ 不占热键栏；描述 4 行 = 四件装备各一行。 */
    public static final class Specification extends PassiveSkill.Specification<MatinaEquipmentsPassive> {

        public Specification() {
            super(Component.text("狂躁牧师的圣装"),
                    List.of(
                            Component.text("十字军修女头巾 · 锁链头盔（保护 I / 绑定诅咒 I）"),
                            Component.text("唤以迢迢天命的装甲 · 铁胸甲（保护 I / 绑定诅咒 I）"),
                            Component.text("经常脏的红色装甲隔离布 · 红色皮革裤（保护 I / 绑定诅咒 I）"),
                            Component.text("年复一年的白色旧军鞋 · 铁靴（保护 II / 绑定诅咒 I）")
                    ));
        }

        @Override
        public MatinaEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new MatinaEquipmentsPassive(id, services, this);
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
        player.getInventory().setHelmet(build(Material.CHAINMAIL_HELMET, HELMET_NAME,
                null, Enchantment.PROTECTION, HELMET_PROTECTION_LEVEL));
        player.getInventory().setChestplate(build(Material.IRON_CHESTPLATE, CHESTPLATE_NAME,
                null, Enchantment.PROTECTION, CHESTPLATE_PROTECTION_LEVEL));
        player.getInventory().setLeggings(build(Material.LEATHER_LEGGINGS, LEGGINGS_NAME,
                LEGGINGS_LEATHER_COLOR, Enchantment.PROTECTION, LEGGINGS_PROTECTION_LEVEL));
        player.getInventory().setBoots(build(Material.IRON_BOOTS, BOOTS_NAME,
                null, Enchantment.PROTECTION, BOOTS_PROTECTION_LEVEL));
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
     * 造一件装备：Adventure 显示名（白色、加粗、去斜体）+ 固定保护附魔 + **绑定诅咒 I**。
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

    // ───────── 内部工具 ─────────

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }
}
