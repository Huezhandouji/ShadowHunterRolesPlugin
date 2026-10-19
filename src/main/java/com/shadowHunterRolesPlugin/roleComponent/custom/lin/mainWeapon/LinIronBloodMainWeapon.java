package com.shadowHunterRolesPlugin.roleComponent.custom.lin.mainWeapon;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinAttackDebuff;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill.LinTripleStrikeSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/**
 * 「林」的主武器：**铁血镇压者**（铁剑）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>模型为<b>铁剑</b>；</li>
 *   <li>每次攻击造成 <b>8 点物理伤害</b>与 <b>4~10 点特殊值伤害</b>；</li>
 *   <li>每次攻击<b>回复 1 点能量</b>；</li>
 *   <li>击中敌人带有<b>武器破碎音效</b>；</li>
 *   <li>并使其<b>主武器攻击力下降 2 持续 5 秒</b>；</li>
 *   <li>附带<b>紫色的打击粒子特效</b>；</li>
 *   <li>主武器攻击 <b>CD 0.2 秒</b>。</li>
 * </ol>
 *
 * <h2>★ 口径申报一：攻击状态刷新由主武器承担</h2>
 * 需求"每一次攻击将刷新所有层数消失时间"（见技能「三重打击」）——
 * <b>层数的持有者是技能</b>，但"攻击发生了"这一事实只有主武器收得到
 * （插件只把攻击事件投递给主武器组件）⇒ 主武器持有一个对技能的引用，
 * 在命中后调 {@link LinTripleStrikeSkill#onBasicAttackLanded} 把窗刷新。
 * <p>依赖方向：主武器 → 技能（单向，不成环）。三重打击<b>不</b>反向依赖主武器，
 * 它只暴露"消耗一层并回答是否是强化击"的读口，由主武器来问。
 *
 * <h2>★ 口径申报二：为什么是 0.2 秒而不是更快</h2>
 * 攻速属性被统一压到 {@value #ATTACK_SPEED_VALUE}（= 100 ⇒ 原版冷却趋近 0），
 * 玩家因此可以"每秒点十几次"；真正的节流全靠本组件自己的冷却
 * （{@value #ATTACK_COOLDOWN_TICKS} 刻 = 0.2 秒）。
 * <p>★ 且 {@code MainWeaponListener} 在**近战**这一路<b>不查冷却</b>
 * （它只在 {@code cast()} 里查，那是技能 / 右键的路）⇒
 * 这道闸门<b>必须</b>由 {@link #onAttack} 自己在最开头把住。
 */
public class LinIronBloodMainWeapon extends MainWeapon {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "lin_mainWeapon_ironBlood";

    /** 普攻物理伤害（需求："8点物理伤害"）。 */
    public static final double ATTACK_DAMAGE = 8.0d;

    /** 普攻特殊值伤害下限（需求："4~10点的特殊值伤害"）。 */
    public static final int SANTE_MIN = 4;

    /** 普攻特殊值伤害上限。 */
    public static final int SANTE_MAX = 10;

    /** 每次攻击回复的能量（需求："回复1点能量"）。 */
    public static final int ENERGY_PER_HIT = 1;

    /** 普攻击退强度。 */
    private static final double ATTACK_KNOCKBACK = 0.4d;

    /**
     * **普攻冷却：0.2 秒 = 4 刻**（需求原话"主武器攻击CD:0.2S"）。
     *
     * <p>声明值同时决定两件事：① 快捷栏图标在这段时间里显示"冷却中"；
     * ② {@link #onAttack} 用它挡住冷却期内的攻击（冷却中攻击不出伤）。
     */
    public static final int ATTACK_COOLDOWN_TICKS = 4;

    /** **攻速属性统一压到 100**（工程规范：先摘后加，避免叠加）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速修饰符的键（固定名，同一把武器反复 {@code buildItem()} 时靠它去重）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            new NamespacedKey("shadowhunterroles", "lin_iron_blood_attack_speed");

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private EnergyComponent energy;
    private BuffComponent buff;

    /** 「三重打击」技能（同角色内的另一个组件）；拿不到时按 {@code null} 容忍。 */
    private LinTripleStrikeSkill tripleStrike;

    /** 纯随机数源（仅用于 4~10 的区间取值；不碰任何状态）。 */
    private final Random random = new Random();

    public LinIronBloodMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（主武器耗能由类型恒为 0；栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends MainWeapon.Specification<LinIronBloodMainWeapon> {

        public Specification() {
            super(Component.text("铁血镇压者"),
                    List.of(
                            Component.text("每次攻击造成8点物理伤害和4~10点特殊值伤害"),
                            Component.text("每次攻击回复1点能量"),
                            Component.text("命中使目标主武器攻击力下降2，持续5秒"),
                            Component.text("主武器攻击冷却：0.2秒")
                    ),
                    Material.IRON_SWORD,
                    ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class);
        }

        @Override
        public LinIronBloodMainWeapon create(String id, ComponentServicesPort services) {
            return new LinIronBloodMainWeapon(id, services, this);
        }
    }

    /** **开始生效**：协作组件一次查好缓存进字段（只在 {@code start()} 取）。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        // 三重打击是可选协作方：查不到（理论上不会，同角色内必有）也不影响普攻
        tripleStrike = getComponent(LinTripleStrikeSkill.class);
    }

    /**
     * **攻击路径**（由 {@code MainWeaponListener} 在近战命中时投递）。
     *
     * <p>★ <b>冷却闸门在第一行</b>：主武器攻击 CD 0.2 秒，冷却中这一下<b>完全不出伤</b>
     * （也不进账本、不回能量、不放特效）——
     * 因为派发侧对近战不查冷却，这道闸门只能自己把住（见类注释口径申报二）。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        // 冷却中则这一下攻击完全不出伤（也不回能量、不放特效）
        if (isCoolingDown()) {
            return;
        }

        Player attacker = svc().self().player();
        Player victim = signal.victim();
        if (attacker == null) {
            return;
        }

        if (victim != null) {
            // ① 8 点物理伤害
            vitals.physicalDamage(victim, attacker, ATTACK_DAMAGE, ATTACK_KNOCKBACK);

            // ② 4~10 点特殊值伤害（区间取值 ⇒ 纯函数 rollSanTEDamage 决定）
            int santeDamage = rollSanTEDamage(random.nextInt(SANTE_MAX - SANTE_MIN + 1));
            sante.decreaseSanTE(victim.getUniqueId(), santeDamage);

            // ③ 使目标主武器攻击力下降 2，持续 5 秒（跨实例账本）
            LinAttackDebuff.apply(victim, Bukkit.getCurrentTick());

            // ④ 三重打击：问技能"这一下是不是强化击"，是则消耗一层并追加效果
            if (tripleStrike != null) {
                tripleStrike.onBasicAttackLanded(attacker, victim);
            }

            // ⑤ 观感：紫色打击粒子 + 武器破碎音效
            LinVfx.ironBloodHit(victim.getWorld(), victim.getLocation().clone().add(0d, 1d, 0d));
            LinSound.ironBloodShatterSound(victim.getWorld(), victim.getLocation());
            LinSound.ironBloodImpactSound(victim.getWorld(), victim.getLocation());
        }

        // ⑥ 每次攻击回复 1 点能量（需求："每次攻击回复1点能量"）
        if (energy != null) {
            energy.increase(ENERGY_PER_HIT);
        }

        // 冷却由本组件在攻击成功处按声明值启动
        startCooldown();
    }

    /**
     * **特殊值伤害取值**（**纯函数** ⇒ 可离线穷举边界）。
     *
     * @param roll 已抽好的 {@code 0..(SANTE_MAX-SANTE_MIN)} 整数
     * @return {@code [SANTE_MIN, SANTE_MAX]} 闭区间内的值
     */
    public static int rollSanTEDamage(int roll) {
        int span = SANTE_MAX - SANTE_MIN + 1;
        int normalized = Math.floorMod(roll, span);
        return SANTE_MIN + normalized;
    }

    /** **闸门放行？**（基类不查容器，由本组件用自己的 {@code buff} 字段判）。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canUseMainWeapon();
    }

    // ───────── 外观：在役 + 攻速统一 100 ─────────

    /**
     * 基类默认画法 + **攻速属性压到 {@value #ATTACK_SPEED_VALUE}**。
     *
     * <p>★ 先 {@code super.buildItem()}：识别键是在基类那一步写进去的，
     * 漏了这步会导致点击无反应、物品残留。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        applyAttackSpeed(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * **攻速属性统一压到 100**：先 {@code removeAttributeModifier} 再 add ——
     * {@link #buildItem()} 会被反复调用，同一把键不去重就会叠加成 100/200/300…
     */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }
}
