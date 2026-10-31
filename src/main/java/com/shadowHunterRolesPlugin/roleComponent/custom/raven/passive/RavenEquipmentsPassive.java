package com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenSound;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 「绯红渡鸦 · 林晨霜」被动：**装备发放 + 角色常驻速度 2**。
 *
 * <h2>需求逐条</h2>
 * <ul>
 *   <li><b>无头盔</b>；</li>
 *   <li>灰黑色<b>皮革胸甲</b>，配<b>红石 · 幽静</b>盔甲纹饰 —— {@code 5} 护甲值 / 无击退抗性 / {@code 1} 盔甲韧性；</li>
 *   <li><b>下界合金护腿</b>，配<b>红石 · 幽静</b>纹饰 —— {@code 5} 护甲值 / 无击退抗性 / {@code 1} 盔甲韧性；</li>
 *   <li><b>下界合金靴</b>，配<b>红石 · 坚守</b>纹饰 —— {@code 5} 护甲值 / {@code 0.2} 击退抗性 / {@code 1} 盔甲韧性；</li>
 *   <li><b>无附魔</b>；</li>
 *   <li><b>角色常驻速度 2</b>。</li>
 * </ul>
 *
 * <h2>★★ 口径申报一：纹饰名对照（中文 → 枚举常量）</h2>
 * 中文官方译名到 {@link TrimPattern} 常量的对照只写这一次：
 * <ul>
 *   <li><b>幽静</b> ⇒ {@link TrimPattern#SILENCE}（1.21 的 silence 纹饰）；</li>
 *   <li><b>坚守</b> ⇒ {@link TrimPattern#WARD}（1.21 的 ward 纹饰，官方译名"监守"；
 *       需求写"坚守"，取最近的这一个）；</li>
 *   <li><b>红石</b> ⇒ {@link TrimMaterial#REDSTONE}。</li>
 * </ul>
 * 三者都经 {@code javap} 从 paper-api 1.21.11 的常量表核实存在（不是猜的）。
 *
 * <h2>★★ 口径申报二：为什么属性用"增量"而不是"直接设值"</h2>
 * 需求给的是**绝对值**（5 护甲值 / 1 韧性 / 0.2 击退抗性），而原版材质本身自带属性
 * （皮革胸甲 3 护甲、下界合金护腿 6 护甲 + 3 韧性…）。
 * 两条实现路线：
 * <ol>
 *   <li>{@code setAttributeModifiers(...)} <b>整体替换</b> —— 只在"物品 NBT 里的属性就是全部属性"
 *       时才等于绝对值；若平台的 item meta 不含材质默认值，结果会变成"默认 + 我设的"；</li>
 *   <li>✅ <b>本类采用：{@code addAttributeModifier(增量)}</b> —— 增量 = 目标绝对值 − 原版材质基础值。
 *       无论"材质默认值是否出现在 meta 里"，最终都是"基础 + 增量 = 目标" ⇒ <b>两种解释下都正确</b>。</li>
 * </ol>
 * 原版基础值写在下面的 {@code VANILLA_*} 常量里（1.21 数值），换算抽成纯函数
 * {@link #modifierDelta(double, double)} ⇒ 可离线穷举。
 * <p>★ 不调 {@code removeAttributeModifier}：那会连"材质默认值"一起摘掉，在不同平台上语义不一致；
 * 而本类只在 {@code start()} 里造**全新的物品**，本来就不存在"重复叠加"的风险。
 *
 * <h2>★★ 口径申报三：常驻速度 2 的落地方式</h2>
 * 与 {@code LetheStartEndPassive}（"始末"）同口径：原版在**死亡 / 重生**时会清空身上全部药水，
 * 因此"进场给一次"的写法会在第一次死亡后永久失效 ⇒ 用"短时长 + 每秒刷新"（时长 = 刷新间隔的 3 倍，
 * 留出卡帧余量）。走 {@link BuffComponent} 而不是直接 {@code addPotionEffect}
 * ⇒ 角色清除时由 buff 组件自己的账本一并回收，本类**不需要**在 {@code stop()} 里摘药水。
 */
public class RavenEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "raven_equipments_passive";

    // ───────── 需求给的绝对目标值 ─────────

    /** 三件装备的护甲值（需求：都是 5）。 */
    public static final double TARGET_ARMOR = 5d;

    /** 三件装备的盔甲韧性（需求：都是 1）。 */
    public static final double TARGET_TOUGHNESS = 1d;

    /** 靴子的击退抗性（需求：0.2；胸甲与护腿"无击退抗性"= 不额外加）。 */
    public static final double TARGET_BOOTS_KNOCKBACK_RESISTANCE = 0.2d;

    // ───────── 原版材质基础值（1.21；换算增量用）─────────

    /** 原版皮革胸甲的护甲值。 */
    public static final double VANILLA_LEATHER_CHESTPLATE_ARMOR = 3d;
    /** 原版皮革胸甲的盔甲韧性。 */
    public static final double VANILLA_LEATHER_CHESTPLATE_TOUGHNESS = 0d;

    /** 原版下界合金护腿的护甲值。 */
    public static final double VANILLA_NETHERITE_LEGGINGS_ARMOR = 6d;
    /** 原版下界合金护腿的盔甲韧性。 */
    public static final double VANILLA_NETHERITE_LEGGINGS_TOUGHNESS = 3d;

    /** 原版下界合金靴的护甲值。 */
    public static final double VANILLA_NETHERITE_BOOTS_ARMOR = 3d;
    /** 原版下界合金靴的盔甲韧性。 */
    public static final double VANILLA_NETHERITE_BOOTS_TOUGHNESS = 3d;
    /** 原版下界合金靴的击退抗性。 */
    public static final double VANILLA_NETHERITE_BOOTS_KNOCKBACK_RESISTANCE = 0.1d;

    // ───────── 常驻速度 ─────────

    /** 常驻速度的增幅：需求"速度 2" ⇒ 原版增幅 1。 */
    public static final int SPEED_AMPLIFIER = 1;

    /** 常驻速度的刷新间隔（20 刻 = 1 秒）。 */
    private static final int SPEED_REFRESH_INTERVAL_TICKS = 20;

    /**
     * **常驻速度的能量门槛**——需求 2026-10-11："当能量低于 50 时，角色速度 2 消失"。
     *
     * <p>判据 = {@code 当前能量 >= 50}（等于 50 仍**保留**速度："低于 50"才消失）。
     */
    public static final int SPEED_ENERGY_THRESHOLD = 50;

    /** 每次刷新的药水时长（60 刻 = 3 秒 = 刷新间隔的 3 倍，见类注释口径申报三）。 */
    private static final int SPEED_DURATION_TICKS = 60;

    // ───────── 属性修饰符的键（先删后加不需要，但键必须唯一）─────────

    private static final NamespacedKey CHEST_ARMOR_KEY = KeyFactory.Registry.of("raven_chest_armor");
    private static final NamespacedKey CHEST_TOUGHNESS_KEY = KeyFactory.Registry.of("raven_chest_toughness");
    private static final NamespacedKey LEGS_ARMOR_KEY = KeyFactory.Registry.of("raven_legs_armor");
    private static final NamespacedKey LEGS_TOUGHNESS_KEY = KeyFactory.Registry.of("raven_legs_toughness");
    private static final NamespacedKey BOOTS_ARMOR_KEY = KeyFactory.Registry.of("raven_boots_armor");
    private static final NamespacedKey BOOTS_TOUGHNESS_KEY = KeyFactory.Registry.of("raven_boots_toughness");
    private static final NamespacedKey BOOTS_KNOCKBACK_KEY = KeyFactory.Registry.of("raven_boots_knockback");

    /** 胸甲的配色：灰黑（需求："灰黑色皮革护甲"）。 */
    private static final Color CHEST_COLOR = Color.fromRGB(42, 42, 48);

    private BuffComponent buff;
    private int speedTick;

    /** 此刻身上是否挂着本角色给的速度（用于"能量掉到门槛以下就**立刻**摘掉"）。 */
    private boolean speedApplied;

    /** 能量组件（速度门槛的读数来源）。 */
    private EnergyComponent energy;

    public RavenEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符：被动无栏位 ⇒ 不占热键栏。 */
    public static final class Specification extends PassiveSkill.Specification<RavenEquipmentsPassive> {

        public Specification() {
            super(Component.text("穿戴装备"),
                    List.of(
                            Component.text("灰黑皮革胸甲（红石·幽静）：5 护甲值 / 1 盔甲韧性"),
                            Component.text("下界合金护腿（红石·幽静）：5 护甲值 / 1 盔甲韧性"),
                            Component.text("下界合金靴（红石·坚守）：5 护甲值 / 1 盔甲韧性 / 0.2 击退抗性"),
                            Component.text("无头盔、无附魔"),
                            Component.text("角色常驻速度 2（能量低于 50 时消失，回到 50 立刻恢复）")
                    ));
            requires(BuffComponent.class)
                    //速度门槛要读能量 ⇒ 缺它则装配期就拦住（不要静默退化成"永远有速度"）
                    .requires(EnergyComponent.class);
        }

        @Override
        public RavenEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new RavenEquipmentsPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 开始生效：先铺三件装备（并立刻刷一次常驻速度）。 */
    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        speedTick = 0;
        speedApplied = false;
        equipAll();
        advancePermanentSpeed();
    }

    /**
     * 停止生效。
     *
     * <p>回收口径（如实申报）：本类只发了 3 件（**无头盔**），因此只清这 3 个槽，
     * <b>不动头盔槽</b> —— 那个槽里的东西从来不是本类给的，摘了就是误删。
     * <p>不摘药水：常驻速度走 {@link BuffComponent} 的账本，由它在自己的 {@code stop()} 里回收。
     */
    @Override
    public void stop() {
        Player player = svc().self().player();
        if (player != null) {
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
        }
        buff = null;
        energy = null;
        speedTick = 0;
        speedApplied = false;
    }

    /** 每秒刷新常驻速度 2。 */
    @Override
    public void update() {
        Player player = svc().self().player();
        if (player == null || !player.isOnline() || player.isDead() || player.getHealth() <= 0d) {
            return;
        }
        advancePermanentSpeed();
    }

    /**
     * **常驻速度的节拍**（每刻都判门槛，只有"该给的时候"才每秒刷一次）。
     *
     * <ol>
     *   <li>能量 **≥ {@value #SPEED_ENERGY_THRESHOLD}** ⇒ 每秒覆盖式刷一次（时长 = 间隔 3 倍，防卡帧闪断）；</li>
     *   <li>★ 能量 **&lt; {@value #SPEED_ENERGY_THRESHOLD}** ⇒ **立刻摘掉**速度
     *       （不能等那 3 秒自然到期 —— 需求要的是"低于 50 就消失"）；
     *       并把刷新计时归零 ⇒ 回到门槛以上时**当刻就恢复**。</li>
     * </ol>
     * ★ 单独抽成 `shouldHavePermanentSpeed(int)` 纯函数 ⇒ 边界（49/50/51）可离线穷举。
     */
    private void advancePermanentSpeed() {
        boolean should = shouldHavePermanentSpeed(energy == null ? Integer.MAX_VALUE : energy.current());
        if (!should) {
            if (speedApplied) {
                if (buff != null) {
                    //★ 走 buff 的账本口 ⇒ 效果与账本一起摘掉（不留陈旧项）
                    buff.removePotionEffect(PotionEffectType.SPEED);
                }
                speedApplied = false;
            }
            speedTick = 0;
            return;
        }
        if (!speedApplied || ++speedTick >= SPEED_REFRESH_INTERVAL_TICKS) {
            speedTick = 0;
            refreshSpeed();
            speedApplied = true;
        }
    }

    /**
     * **此刻是否应当拥有常驻速度**（纯函数）——需求："当能量低于 50 时，角色速度 2 消失"。
     *
     * @param energy 当前能量
     */
    public static boolean shouldHavePermanentSpeed(int energy) {
        return energy >= SPEED_ENERGY_THRESHOLD;
    }

    /** 施加常驻速度 2（{@code ambient = true} 信标式光晕、{@code particles = false} 不冒泡）。 */
    private void refreshSpeed() {
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS, SPEED_AMPLIFIER, true, false);
        }
    }

    // ───────── 装备发放 ─────────

    /**
     * 发放三件装备（顺序 胸甲 → 护腿 → 靴子，与既有 {@code *EquipmentsPassive} 一致）。
     *
     * <p>★ 每一件都是**全新构造**的 {@code ItemStack} ⇒ 属性修饰符不存在"重复叠加"问题
     * （这也是本类不调 {@code removeAttributeModifier} 的原因，见类注释口径申报二）。
     */
    private void equipAll() {
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        ItemStack chestplate = buildChestplate();
        ItemStack leggings = buildLeggings();
        ItemStack boots = buildBoots();
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
        RavenSound.ravenEquipLeatherSound(player.getWorld(), player.getLocation());
        RavenSound.ravenEquipNetheriteSound(player.getWorld(), player.getLocation());
    }

    /** 灰黑皮革胸甲：红石·幽静纹饰 + 护甲/韧性增量。 */
    private static ItemStack buildChestplate() {
        ItemStack stack = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta rawMeta = stack.getItemMeta();
        if (rawMeta instanceof LeatherArmorMeta leather) {
            leather.setColor(CHEST_COLOR);
        }
        if (rawMeta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.SILENCE));
        }
        addArmorModifiers(rawMeta, CHEST_ARMOR_KEY, CHEST_TOUGHNESS_KEY, null,
                VANILLA_LEATHER_CHESTPLATE_ARMOR, VANILLA_LEATHER_CHESTPLATE_TOUGHNESS, 0d);
        stack.setItemMeta(rawMeta);
        return stack;
    }

    /** 下界合金护腿：红石·幽静纹饰 + 护甲/韧性增量。 */
    private static ItemStack buildLeggings() {
        ItemStack stack = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.SILENCE));
        }
        addArmorModifiers(meta, LEGS_ARMOR_KEY, LEGS_TOUGHNESS_KEY, null,
                VANILLA_NETHERITE_LEGGINGS_ARMOR, VANILLA_NETHERITE_LEGGINGS_TOUGHNESS, 0d);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 下界合金靴：红石·坚守纹饰 + 护甲/韧性/击退抗性增量。 */
    private static ItemStack buildBoots() {
        ItemStack stack = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.WARD));
        }
        addArmorModifiers(meta, BOOTS_ARMOR_KEY, BOOTS_TOUGHNESS_KEY, BOOTS_KNOCKBACK_KEY,
                VANILLA_NETHERITE_BOOTS_ARMOR, VANILLA_NETHERITE_BOOTS_TOUGHNESS,
                VANILLA_NETHERITE_BOOTS_KNOCKBACK_RESISTANCE);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 给一件盔甲挂上"目标绝对值"对应的三条属性增量（增量 = 目标 − 原版基础）。
     *
     * @param knockbackKey      击退抗性的键；{@code null} = 这件不要击退抗性
     * @param vanillaKnockback  原版击退抗性基础值（无 {@code knockbackKey} 时忽略）
     */
    private static void addArmorModifiers(ItemMeta meta, NamespacedKey armorKey, NamespacedKey toughnessKey,
                                          NamespacedKey knockbackKey,
                                          double vanillaArmor, double vanillaToughness,
                                          double vanillaKnockback) {
        if (meta == null) {
            return;
        }
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                armorKey, modifierDelta(vanillaArmor, TARGET_ARMOR), AttributeModifier.Operation.ADD_NUMBER));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                toughnessKey, modifierDelta(vanillaToughness, TARGET_TOUGHNESS),
                AttributeModifier.Operation.ADD_NUMBER));
        if (knockbackKey != null) {
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                    knockbackKey, modifierDelta(vanillaKnockback, TARGET_BOOTS_KNOCKBACK_RESISTANCE),
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **属性增量**（纯函数）：{@code 目标 − 原版基础}。
     *
     * <p>它是"目标绝对值"这条口径的唯一换算点 —— 把"原版基础值"这个外部事实与"我要多少"
     * 这个需求值分开，于是"哪一件算错了"能被单测逐件钉住。
     */
    public static double modifierDelta(double vanillaBase, double target) {
        return target - vanillaBase;
    }
}
