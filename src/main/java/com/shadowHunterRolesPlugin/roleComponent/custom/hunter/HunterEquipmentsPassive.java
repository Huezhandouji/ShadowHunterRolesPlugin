package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
 * 猎手（{@code hunter}）的被动「**影都之壳**」：发放四件专属护甲。
 *
 * <h2>四件（槽位 → 材质 → 附魔，逐条对齐 2026-10-31 需求）</h2>
 * <ul>
 *   <li><b>黑色甲壳</b> · 下界合金头盔（{@link Material#NETHERITE_HELMET}）· 保护 I；</li>
 *   <li><b>腹部肢体</b> · 下界合金胸甲（{@link Material#NETHERITE_CHESTPLATE}）· 爆炸保护 V；</li>
 *   <li><b>肌腱隔膜</b> · **暗紫色皮革护腿**（{@link Material#LEATHER_LEGGINGS} + 暗紫染色）· 弹射物保护 III；</li>
 *   <li><b>锐利末端</b> · 下界合金靴（{@link Material#NETHERITE_BOOTS}）· 保护 I。</li>
 * </ul>
 * <p><b>四件另各带「绑定诅咒 I」</b>（需求："所有装甲再额外加上绑定诅咒附魔"）——
 * 穿上就脱不掉；与"进场即换装、离场清槽"的既有口径不冲突（{@code stop()} 直接写槽，不靠玩家手动脱）。
 *
 * <h2>★ 口径申报：后两件的材质</h2>
 * 需求最初只给前两件写了材质（下界合金头盔 / 下界合金胸甲）；2026-10-31 追加明确「肌腱隔膜 = 暗紫色皮革护腿」。
 * 「锐利末端」仍未指定材质 ⇒ 按"整套同材质"取下界合金靴（与头盔/胸甲一致）；
 * 若要换成别的材质，只改 {@link #start()} 里对应那一行即可，附魔与名字不受影响。
 *
 * <h2>★ 名字样式（需求："亮紫色大字、不倾斜"）</h2>
 * 「大字」在本工程既有口径 = {@link TextDecoration#BOLD}（物品名没有字号维度的合法表达）；
 * 「不倾斜」= 显式 {@code ITALIC false}（原版物品名默认斜体，不写这一条就是斜的）；
 * 「亮紫色」取 {@code #FF55FF}（工程里 bossbar 增强画法用的同一档亮紫），比 {@code LIGHT_PURPLE} 更亮一档。
 *
 * <h2>★ 为什么附魔用 {@code meta.addEnchant(ench, level, true)} 的第三参 {@code true}</h2>
 * 第三参 = **忽略等级上限**。本需求的"爆炸保护 V / 弹射物保护 III"中，爆炸保护原版上限 IV，
 * 不开这个开关会被平台按上限截断，附魔等级就不再是需求值（同 {@code LinEquipmentsPassive}）。
 *
 * <h2>★ 为什么 {@code stop()} 直接把四个槽置 null</h2>
 * 本类**发的是四个槽**（头盔 / 胸甲 / 护腿 / 靴子全覆盖），因此可以严格按 {@code start()} 对称回收：
 * 发几件就收几件。已知代价（与 {@code LetheEquipmentsPassive} / {@code ResistEquipmentsPassive} 同规）：
 * 玩家原本穿在这四个槽里的装备会被覆盖并随 {@code stop()} 一并删除 —— 这是"进场即换装"的既有口径。
 */
public class HunterEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "hunter_equipments_passive";

    // ───────── 四件装备的名字（需求逐字）─────────

    /** 头盔名。 */
    public static final String HELMET_NAME = "黑色甲壳";

    /** 胸甲名。 */
    public static final String CHESTPLATE_NAME = "腹部肢体";

    /** 护腿名。 */
    public static final String LEGGINGS_NAME = "肌腱隔膜";

    /** 靴子名。 */
    public static final String BOOTS_NAME = "锐利末端";

    // ───────── 四件装备的附魔等级（每处只声明一次）─────────

    /** 头盔：保护 I。 */
    private static final int HELMET_PROTECTION_LEVEL = 1;

    /** 胸甲：爆炸保护 V。 */
    private static final int CHESTPLATE_BLAST_PROTECTION_LEVEL = 5;

    /** 护腿：弹射物保护 III。 */
    private static final int LEGGINGS_PROJECTILE_PROTECTION_LEVEL = 3;

    /** 靴子：保护 I。 */
    private static final int BOOTS_PROTECTION_LEVEL = 1;

    /** 四件共用的绑定诅咒等级（需求："所有装甲再额外加上绑定诅咒附魔"）。 */
    private static final int BINDING_CURSE_LEVEL = 1;

    // ───────── 皮革染色（需求：肌腱隔膜 = 暗紫色皮革护腿）─────────

    /** 暗紫皮革染色（与影都主题的墨紫同色，2026-10-31 需求）。 */
    private static final Color LEGGINGS_LEATHER_COLOR = Color.fromRGB(78, 24, 112);

    // ───────── 需求指定的名字样式：亮紫 + 大字 + 不倾斜 ─────────

    /** 四件共用的名字颜色（需求："亮紫色"）。 */
    private static final TextColor NAME_COLOR = TextColor.color(0xFF55FF);

    public HunterEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符：被动无栏位 ⇒ 不占热键栏；描述 4 行 = 四件装备各一行。 */
    public static final class Specification extends PassiveSkill.Specification<HunterEquipmentsPassive> {

        public Specification() {
            super(Component.text("影都之壳"),
                    List.of(
                            Component.text("黑色甲壳 · 下界合金头盔（保护 I / 绑定诅咒 I）"),
                            Component.text("腹部肢体 · 下界合金胸甲（爆炸保护 V / 绑定诅咒 I）"),
                            Component.text("肌腱隔膜 · 暗紫色皮革护腿（弹射物保护 III / 绑定诅咒 I）"),
                            Component.text("锐利末端 · 下界合金靴（保护 I / 绑定诅咒 I）")
                    ));
        }

        @Override
        public HunterEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new HunterEquipmentsPassive(id, services, this);
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
        player.getInventory().setHelmet(build(Material.NETHERITE_HELMET, HELMET_NAME, null,
                Enchantment.PROTECTION, HELMET_PROTECTION_LEVEL));
        player.getInventory().setChestplate(build(Material.NETHERITE_CHESTPLATE, CHESTPLATE_NAME, null,
                Enchantment.BLAST_PROTECTION, CHESTPLATE_BLAST_PROTECTION_LEVEL));
        player.getInventory().setLeggings(build(Material.LEATHER_LEGGINGS, LEGGINGS_NAME, LEGGINGS_LEATHER_COLOR,
                Enchantment.PROJECTILE_PROTECTION, LEGGINGS_PROJECTILE_PROTECTION_LEVEL));
        player.getInventory().setBoots(build(Material.NETHERITE_BOOTS, BOOTS_NAME, null,
                Enchantment.PROTECTION, BOOTS_PROTECTION_LEVEL));
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
     * 造一件装备：Adventure 显示名（亮紫、加粗、去斜体）+ 固定保护类附魔 + **绑定诅咒 I**。
     * <p>{@code addEnchant} 的第三参 {@code true} = 忽略等级上限（理由见类注释）。
     * <p>{@code leatherColor} 非 null 时对皮革甲染色（只有皮革类 meta 才接受染色）；
     * meta 取不到时原样返回（平台异常的兜底，不抛）。
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
