package com.shadowHunterRolesPlugin.roleComponent.custom.matina.mainWeapon;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.MatinaRageVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaKuangPassive;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 「狂躁牧师·马提娜」主武器：**医疗设备**（模型 = 铁铲）。
 *
 * <h2>行为（需求逐条）</h2>
 * <ul>
 *   <li>主武器是<b>铁铲</b>，每次攻击造成 <b>4 点物理伤害</b>；</li>
 *   <li><b>攻击 CD 0.1 秒</b>（{@value #ATTACK_COOLDOWN_TICKS} 刻）—— <b>CD 期间没有伤害</b>
 *       （冷却中直接 return，连特效都不放）；</li>
 *   <li>医疗设备<b>每次命中敌人都会回复"对方"8 点生命</b>，同时<b>对对方造成 5 点特殊值伤害</b>
 *       （需求原话里这句是"回复对方 8 点生命"⇒ 本武器在此<b>如实照做</b>，并在结卡申报）；</li>
 *   <li>命中即视为一次<b>成功攻击</b> ⇒ 记 1 层狂暴，并结算「诉说苦怒」的全部攻击型阈值加成
 *       （回血 8 / 回能 1 / 回 san 5 / 额外真伤 6 或 8），见 {@link MatinaKuangPassive#applyAttackBonuses}。</li>
 * </ul>
 *
 * <h2>为什么需要主武器组件</h2>
 * 插件把玩家攻击事件<b>只投递给主武器组件</b>（{@code listener/MainWeaponListener#onAttackPlayer}
 * 先判手持物是不是主武器，再按该武器的 id 派发 {@code onAttack}）⇒ 需求里"每次命中"的所有效果
 * 都必须落在这一把武器上。
 *
 * <h2>冷却归属</h2>
 * 冷却由<b>本组件自持</b>（{@code startCooldown()}）：命中成功处启动，闸门用
 * {@code isCoolingDown()} 自己把住（{@code MainWeaponListener} <b>不做</b>冷却判定）。
 *
 * <h2>物品外观</h2>
 * 在基类默认画法的基础上追加一行<b>狂暴层数</b>（玩家随时能看到自己的狂暴值）；
 * 层数每秒都会变 ⇒ 本组件覆写 {@link #dependsOnLiveState()} 为 {@code true}，
 * 由框架在冷却期之外也按需刷新（见 {@code update()} 里的显式请求）。
 */
public class MatinaMedicalShovelMainWeapon extends MainWeapon {

    /** **本组件的登记 id**（★ 知识归属：组件自己）。 */
    public static final String ID = "matina_mainWeapon_medicalShovel";

    /** 普攻物理伤害（需求：4 点）。 */
    private static final double ATTACK_DAMAGE = 4.0;

    /** 命中时对<b>对方</b>回复的生命（需求：8 点）。 */
    private static final double VICTIM_HEAL = 8.0;

    /** 命中时对<b>对方</b>造成的特殊值（SanTE）伤害（需求：5 点）。 */
    private static final int VICTIM_SANTE_DAMAGE = 5;

    /**
     * **普攻冷却：0.1 秒 = 2 刻**（需求）。
     * <p>声明值同时决定两件事：① 快捷栏图标在这段时间显示"冷却中"；
     * ② {@link #onAttack} 用它挡住冷却期内的攻击（冷却中攻击<b>不出伤</b>）。
     */
    private static final int ATTACK_COOLDOWN_TICKS = 2;

    /**
     * **攻击速度属性值**（需求：模型铲子攻击速度为 100）。
     *
     * <p>原版 1.9+ 的攻击冷却 = {@code 20 / 攻速} 刻（铁铲默认攻速 1.0 ⇒ **20 刻**）——
     * 那会把"0.1 秒攻击间隔"压成 1 秒。把攻速属性拉到 {@value #ATTACK_SPEED_VALUE}
     * ⇒ 原版冷却降到约 {@code 0.2} 刻（**事实上不存在**），"能多快打下一击"就**只由本组件自己的
     * 冷却闸门决定**（见 {@link #ATTACK_COOLDOWN_TICKS}）。
     */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速修饰符的键（本组件自己持有；每次重绘先摘后加 ⇒ 不会叠加）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("matina_medical_device_attack_speed");

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private MatinaKuangPassive kuang;
    private HotbarRenderComponent render;

    /** 上一帧写进 lore 的狂暴层数（用来只在"数字真的变了"时请求重绘）。 */
    private int lastShownKuang = -1;

    public MatinaMedicalShovelMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（主武器耗能由类型恒为 0；栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends MainWeapon.Specification<MatinaMedicalShovelMainWeapon> {

        public Specification() {
            super(Component.text("医疗设备"),
                    List.of(Component.text("每次命中造成 4 点物理伤害，为目标回复 8 点生命，"),
                            Component.text("并造成 5 点特殊值伤害"),
                            Component.text("攻击间隔 0.1 秒")),
                    Material.IRON_SHOVEL,
                    ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(MatinaKuangPassive.class);
        }

        @Override
        public MatinaMedicalShovelMainWeapon create(String id, ComponentServicesPort services) {
            return new MatinaMedicalShovelMainWeapon(id, services, this);
        }
    }

    /** **开始生效**：协作组件一次查好缓存进字段（依赖只在 {@code start()} 取）。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        kuang = svc().components().get(MatinaKuangPassive.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /**
     * **攻击路径**（由 {@code MainWeaponListener} 在近战命中时投递）。
     *
     * <p><b>攻速闸门</b>：冷却期直接 return ⇒ <b>这一下完全不出伤</b>（也不进 SanTE 账、不放特效、
     * 不启动新冷却、不记狂暴）。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        if (isCoolingDown()) {
            return;
        }

        Player attacker = svc().self().player();
        Player victim = signal.victim();
        if (attacker == null) {
            return;
        }

        if (victim != null) {
            //① 4 点物理伤害
            vitals.physicalDamage(victim, attacker, ATTACK_DAMAGE);
            //★ 连打必需（组件侧，不动框架）：扣血后清掉受击无敌帧，
            //  否则 0.1 秒间隔内若目标先被"不经本插件入口"的来源打中，我们下一击会被吞。
            victim.setNoDamageTicks(0);
            //② 医疗设备"回复对方 8 点生命"（需求原话）
            vitals.heal(victim, VICTIM_HEAL);
            //③ 对对方 5 点特殊值（SanTE）伤害
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(), VICTIM_SANTE_DAMAGE);
            }
            //④ 命中特效：治疗型的爱心 + 骨粉催熟，叠一层伤害型的紫色上升粒子
            World world = victim.getWorld();
            MatinaRageVfx.hearts(world, victim.getLocation().clone().add(0d, 2.1d, 0d), 3, 0.35d);
            MatinaRageVfx.boneMeal(world, victim.getLocation().clone().add(0d, 1.0d, 0d));
            MatinaRageVfx.purpleRising(world, victim.getLocation().clone(), 1.6d, 0d, 6);
            world.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        }

        attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);

        //⑤ 攻击成功 ⇒ 启动冷却（组件自持）
        startCooldown();

        //⑥ 攻击型狂暴结算（记 1 层狂暴 + 回血/回能/回san/额外真伤）
        if (kuang != null) {
            kuang.applyAttackBonuses(attacker, victim);
        }
    }

    /**
     * **物品外观**：基类默认画法 + 一行"狂暴：N 层"。
     * <p>层数是运行期读数 ⇒ 只在"数字变了"时请求重绘（不是每帧都请求）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        int value = kuang != null ? kuang.kuang() : 0;
        lastShownKuang = value;

        //★ 攻速属性 = 100（需求）：压掉原版攻击冷却，让 0.1 秒间隔只由本组件的冷却闸门决定
        applyAttackSpeed(meta);

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text("────────────────────").color(NamedTextColor.DARK_GRAY));
        lore.add(MatinaKuangPassive.loreLineFor(value));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * **把该物品的攻速属性设到 {@value #ATTACK_SPEED_VALUE}**。
     * <p>先摘后加（同一把键）⇒ 反复重绘**不会叠加**成 100/200/300…
     */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    /**
     * **外观依赖活状态**：lore 里的狂暴层数会自己变（每秒衰减、命中增加）⇒ 置 {@code true}
     * 让框架在冷却期之外也按需刷。真正的刷新节拍仍由 {@link #update()} 的显式请求给出
     * （见 {@link #dependsOnLiveState()} 与 {@link HotbarRenderComponent#requestRepaint()}）。
     */
    @Override
    public boolean dependsOnLiveState() {
        return true;
    }

    /** 层数变了就请求一次重绘（本组件的刷新节拍）。 */
    @Override
    public void update() {
        if (render == null || kuang == null) {
            return;
        }
        if (kuang.kuang() != lastShownKuang) {
            render.requestRepaint();
        }
    }

    /** **闸门放行？**（基类不查容器 ⇒ 由本组件用自己的字段判）。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    /** **当前输出物**（道具特效说明用；无消费点但保留读口便于排障）。 */
    public static Material weaponMaterial() {
        return Material.IRON_SHOVEL;
    }

    /** 命中特效用的粒子（排障读口）。 */
    public static Particle hitParticle() {
        return Particle.HEART;
    }
}
