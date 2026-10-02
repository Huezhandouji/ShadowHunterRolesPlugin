package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/**
 * 「猎手」主武器：**遗愤**（下界合金剑）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>每次攻击<b>回复 4 点生命</b>（回复的是<b>自己</b>）；</li>
 *   <li>攻击 CD <b>0.3 秒</b>（{@value #ATTACK_COOLDOWN_TICKS} 刻）；</li>
 *   <li>造成 <b>6~10 点随机整数物理伤害</b>；</li>
 *   <li>造成 <b>4~10 点随机整数 SanTE 伤害</b>；</li>
 *   <li>（被动「猎杀」联动）普攻<b>被标记</b>的敌人额外造成 <b>10 点灵魂伤害</b>，
 *       并<b>消费掉该敌人身上的标记</b>（需求："敌人被普通攻击后就会失去这个标记"），
 *       同时产生<b>更多粒子</b>与 <b>2 倍速僵尸村民转变</b>音效。</li>
 * </ol>
 *
 * <h2>★ 口径申报：标记由"这一击"消费，顺序是先消费后结算</h2>
 * {@link HunterPreyPassive#consumeMark(UUID)} 是"取走并移除"语义（同一个标记只会被消费一次）。
 * 本类在**结算额外伤害之前**先取走标记，因此：
 * 同一 tick 内的第二次命中（例如范围伤害的其它来源）拿到的就是"没有标记"的结果 ——
 * 这正是"敌人被普通攻击后就会失去这个标记"要的排他性。
 *
 * <h2>攻击间隔 = 0.3 秒怎么做到（三件缺一不可）</h2>
 * <ol>
 *   <li>声明冷却 = {@value #ATTACK_COOLDOWN_TICKS} 刻（{@link Specification} 第 4 参）；</li>
 *   <li>{@link #onAttack} <b>开头</b>自判 {@code isCoolingDown()} ——
 *       <b>派发侧不替主武器挡冷却</b>（{@code MainWeaponListener} 只在技能路径判冷却）；</li>
 *   <li>物品攻速设为 {@value #ATTACK_SPEED_VALUE}（下界合金剑默认 1.6 ⇒ 原版冷却 ≈ 12.5 刻 ≈ 0.62 秒，
 *       会把 6 刻的间隔压长成 0.62 秒）。</li>
 * </ol>
 *
 * <h2>★ 口径申报：灵魂伤害取 TRUE（真伤）</h2>
 * 插件 {@code DamageKind} 只有 {@code PHYSICAL} / {@code TRUE}，**没有"灵魂"这一类型**
 * ⇒ "额外 10 点灵魂伤害"按"无视护甲的伤害"取 {@code TRUE}，与工程既有口径一致
 * （先例：罪棘的"魔法伤害"同样取 {@code TRUE}）。数值常量在
 * {@link HunterPreyPassive#SOUL_BONUS_DAMAGE}。
 *
 * <h2>★ 口径申报：那 10 点额外伤害为什么落在本类</h2>
 * 插件**只把攻击事件投递给主武器组件**（{@code listener/MainWeaponListener#onAttackPlayer}）
 * ⇒ "普攻被标记的敌人"这句话必须以本武器为落点，否则永远不触发。
 * 标记的真值仍住在 {@link HunterPreyPassive}，本类只读取口 {@code isMarked(uuid)}。
 *
 * <h2>★ 口径申报：攻击会中断「遁形」</h2>
 * 遁形的需求写明"若使用技能或攻击，将中断技能" ⇒ 本类在**确认这次攻击成立之后、结算之前**
 * 调 {@link HunterStealthSkill#breakStealth()}。顺序取"先破隐后结算"：破隐是这次攻击的**前提**
 * （"我出手了"这件事发生了），伤害是它的结果。
 *
 * <h2>★ 口径申报：扣血后再清一次受击无敌帧</h2>
 * {@code DamageUtil.dealtPhysicalDamage} 只在扣血**前**清 {@code noDamageTicks}，而
 * {@code damage()} 结算后会把该值设回 20 刻 ⇒ 0.3 秒连打期间若目标先被"不经本插件伤害入口"
 * 的来源打中，本武器下一击仍会被吞。扣血后再清一次即可免疫这种情形
 * （工程既有先例：{@code CangluTraumaMainWeapon#clearHitInterval}，
 * 本处按 {@code MatinaMedicalShovelMainWeapon} 的写法**内联**一行，不另建跨类工具）。
 */
public class HunterGrudgeMainWeapon extends MainWeapon {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "hunter_mainWeapon_grudge";

    /** 攻击冷却（刻）—— 需求原话"攻击CD:0.3S"。 */
    private static final int ATTACK_COOLDOWN_TICKS = 6;

    /** 攻击速度统一值（压掉原版攻击冷却；见类注释第 3 条）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速修饰符的键（先摘后加 ⇒ 反复重绘不叠加）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("hunter_grudge_attack_speed");

    /** 物理伤害下限（含）—— 需求原话"6到10点随机整数物理伤害"。 */
    public static final int PHYSICAL_DAMAGE_MIN = 6;

    /** 物理伤害上限（含）。 */
    public static final int PHYSICAL_DAMAGE_MAX = 10;

    /** SanTE 伤害下限（含）—— 需求原话"4到10点随机整数sanTE伤害"。 */
    public static final int SANTE_DAMAGE_MIN = 4;

    /** SanTE 伤害上限（含）。 */
    public static final int SANTE_DAMAGE_MAX = 10;

    /** 每次攻击回复的生命（回复自己）—— 需求原话"你每次攻击回复4点生命"。 */
    private static final double SELF_HEAL = 4d;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private HunterPreyPassive prey;
    private HunterEvolutionPassive evolution;
    private HunterStealthSkill stealth;

    /** 随机源（只影响伤害数值，不参与任何判定；每个实例一份，避免共用一个全局 Random）。 */
    private final Random random = new Random();

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HunterGrudgeMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：下界合金剑（需求指定的模型）+ 六条逐字对应需求的说明。
     * <p>主武器的 {@code energyCost} 由类型恒为 0 ⇒ {@code ENERGY_LACK} 不可达（冻结面口径）。
     */
    public static final class Specification extends MainWeapon.Specification<HunterGrudgeMainWeapon> {

        public Specification() {
            super(Component.text("遗愤"),
                    List.of(
                            Component.text("每次攻击回复 4 点生命"),
                            Component.text("造成 6~10 点随机物理伤害"),
                            Component.text("造成 4~10 点随机特殊值伤害"),
                            Component.text("普攻被标记的敌人额外造成 10 点灵魂伤害，并消耗掉该标记"),
                            Component.text("攻击冷却 0.3 秒")
                    ),
                    Material.NETHERITE_SWORD,
                    ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class);
            requires(SanTEComponent.class);
            requires(BuffComponent.class);
            requires(HunterPreyPassive.class);
            requires(HunterEvolutionPassive.class);
            requires(HunterStealthSkill.class);
        }

        @Override
        public HunterGrudgeMainWeapon create(String id, ComponentServicesPort services) {
            return new HunterGrudgeMainWeapon(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 依赖只在 {@code start()} 取（硬约定：不在构造器 / {@code awake()} 里取）。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        prey = svc().components().get(HunterPreyPassive.class);
        evolution = svc().components().get(HunterEvolutionPassive.class);
        stealth = svc().components().get(HunterStealthSkill.class);
    }

    /** 停止生效：与 {@link #start()} 严格对称。 */
    @Override
    public void stop() {
        vitals = null;
        sante = null;
        buff = null;
        prey = null;
        evolution = null;
        stealth = null;
    }

    // ───────── 攻击 ─────────

    /**
     * 命中一名玩家（{@code listener/MainWeaponListener#onAttackPlayer} 是本入口的唯一投递点）。
     *
     * <p>顺序（不可交换）：
     * <ol>
     *   <li><b>冷却自判</b> —— 冷却中这一下<b>完全不出伤</b>：不结算、不放特效、不回复、不启动新冷却；</li>
     *   <li>闸门（眩晕时不放行）；</li>
     *   <li>中断遁形（"使用攻击 ⇒ 中断技能"）；</li>
     *   <li>摇伤害 → 物理伤害 → 跨实例扣对方 SanTE；</li>
     *   <li>猎杀联动：被标记 ⇒ 额外灵魂伤害（真伤）；</li>
     *   <li>回复自己 4 点生命；</li>
     *   <li>4 级进化 ⇒ 回复 5 点自身 TE；</li>
     *   <li>扣血后再清一次受击无敌帧（连打必需）；</li>
     *   <li><b>结算成功 ⇒ 启动冷却</b>。</li>
     * </ol>
     */
    @Override
    public void onAttack(AttackSignal signal) {
        //★ 主武器必须自判冷却（派发侧不替主武器挡）—— 这一行必须在最前
        if (isCoolingDown()) {
            return;
        }
        if (!canUse()) {
            return;
        }
        Player owner = svc().self().player();
        Player victim = signal == null ? null : signal.victim();
        if (owner == null || victim == null || vitals == null) {
            return;
        }

        //"我出手了" ⇒ 中断遁形（需求："若使用技能或攻击，将中断技能"）
        if (stealth != null) {
            stealth.breakStealth();
        }

        //① 6~10 点随机整数物理伤害
        int physical = rollInclusive(random, PHYSICAL_DAMAGE_MIN, PHYSICAL_DAMAGE_MAX);
        vitals.physicalDamage(victim, owner, physical);

        //② 4~10 点随机整数 SanTE 伤害（跨实例入口：扣的是【对方】的 SanTE）
        if (sante != null) {
            int santeDamage = rollInclusive(random, SANTE_DAMAGE_MIN, SANTE_DAMAGE_MAX);
            sante.decreaseSanTE(victim.getUniqueId(), santeDamage);
        }

        //③ 猎杀联动：**普攻会消费掉敌人身上的标记**（需求："敌人被普通攻击后就会失去这个标记"）
        //   消费成功 ⇒ 额外 10 点灵魂伤害（取 TRUE）+ "更多粒子" + 2 倍速僵尸村民转变音效；
        //   没有标记 ⇒ 只是常规命中的轻量灵魂粒子。
        boolean markConsumed = prey != null && prey.consumeMark(victim.getUniqueId());
        World world = owner.getWorld();
        Location victimChest = victim.getLocation().clone().add(0d, 1.0d, 0d);
        if (world != null) {
            if (markConsumed) {
                HunterVfx.consumeBurstHunterGrudgeMarkedHit(world, victimChest, victim.getLocation());
                HunterSound.zombieVillagerCureFastHunterGrudgeMarkedHitSound(world, victimChest);
            } else {
                HunterVfx.hitSoulHunterGrudgeAttack(world, victimChest);
            }
        }
        if (markConsumed) {
            vitals.trueDamage(victim, owner, HunterPreyPassive.SOUL_BONUS_DAMAGE);
        }

        //④ 每次攻击回复自己 4 点生命
        vitals.heal(owner, SELF_HEAL);

        //⑤ 4 级进化：攻击回复 5 点自身 TE（未达该档时为 0，不写）
        if (evolution != null && sante != null) {
            int restore = evolution.grudgeSanTERestore();
            if (restore > 0) {
                sante.increase(restore);
            }
        }

        //⑥ 连打必需：扣血后再清一次受击无敌帧（见类注释的口径申报）
        victim.setNoDamageTicks(0);

        //⑦ 结算成功 ⇒ 启动冷却
        startCooldown();
    }

    /** 闸门：被眩晕时主武器不可用（{@code canUseMainWeapon()} = 非 STUN）。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    // ───────── 外观：攻速统一 100 ─────────

    /**
     * 覆写默认画法：先拿基类成品（**识别键写在基类那一步，漏了会点击无反应**），
     * 再把攻击速度压到 {@value #ATTACK_SPEED_VALUE}。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
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
     * {@link #buildItem()} 会被反复调用，同一把键不去重就会叠加成 100 / 200 / 300…
     * （表现是"打着打着攻击变瞬发且动作乱飘"）。
     */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **闭区间随机整数** {@code [min, max]}（纯函数 ⇒ 可离线穷举边界）。
     *
     * <p>与 {@code Random#nextInt(bound)}（半开区间）的区别正是这里必须用它的原因：
     * 需求写的是"6 到 10 点"与"4 到 10 点"，**两端都含**。
     *
     * <p>非法入参（{@code random == null} / {@code max < min}）时回 {@code min}
     * （宁可少打一点也不抛异常打断战斗）。
     *
     * @param min 下限（含）
     * @param max 上限（含）
     */
    public static int rollInclusive(Random random, int min, int max) {
        if (random == null || max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
