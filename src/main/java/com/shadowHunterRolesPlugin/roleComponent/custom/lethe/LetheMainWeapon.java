package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 冷识（{@code lethe}）的主武器「Lether」：一把把「流连」标记射出去的弓。
 *
 * <h2>玩法</h2>
 * <ul>
 *   <li><b>拉弓 / 击发归原版</b>（{@link BowWeapon} 家族的既有边界：右键不被取消）；</li>
 *   <li><b>离弦</b>（{@link #onShoot(ShootSignal)}）：把这一支箭交给
 *       {@link LetheResonanceComponent#markArrow(Projectile, boolean)} 打上「流连」标记，
 *       并画出离弦拖尾、播弓弦音；</li>
 *   <li><b>命中</b>（{@link #onProjectileHit(ProjectileHitSignal)}）：命中敌对玩家 ⇒
 *       {@link LetheResonanceComponent#onMarkedArrowHit(Projectile, Player)} 兑现标记
 *       （扣 10 点特殊值 + 4 点真实伤害）；任何落点都会通知
 *       {@link LetheResonanceComponent#onMarkedArrowStopped(Projectile)}，由共鸣组件在停点生成
 *       半径 3 的共鸣圈；随后这支箭被移除（不留在地上被拾取）；</li>
 *   <li><b>附魔</b>：击退 4 / 锋利 6 / 力量 3 / 冲击 1（见 {@link #buildItem()}）。</li>
 *   <li><b>近战</b>（{@link #onAttack(AttackSignal)}，2026-10-31 新增）：8 物理 + 3~6 特殊值 + 击退力度 1.2；
 *       **无插件 CD** —— 只认原版满蓄力，出手频率完全由本武器攻速属性决定
 *       （摘掉弓自带攻速惩罚后 = 玩家基础 4.0 = 0.25 秒/击，见
 *       {@link #applyAttackSpeed(ItemMeta)} 与 {@value #MELEE_ATTACK_SPEED_TOTAL}）。</li>
 * </ul>
 *
 * <h2>★ 近战为什么现在能生效（框架放开，如实申报）</h2>
 * 弓弩家族的历史口径是：近战命中的原版伤害被 {@code BowWeaponListener} 取消，且
 * {@code BowWeapon#onAttack} 被封成 {@code final} 空实现（"弓弩没有近战扩展点"）。
 * 2026-10-31 用户要求本武器具备近战攻击能力，因此把该入口放开为**可覆写**：
 * listener 仍照旧取消原版伤害（弓弩不造成原版伤害的口径不变），随后对**玩家受害者**派发
 * {@code onAttack}；默认空实现 ⇒ 其它弓弩行为与放开前逐字一致。
 *
 * <h2>为什么这把弓没有冷却（{@code COOLDOWN_TICKS = 0}）</h2>
 * 弓的右键是原版动作（拉弓 · 击发），而 {@code BowWeaponListener} 的闸门恰恰是"冷却中取消右键"
 * —— 一旦给这把弓挂上冷却，玩家会在冷却期里<b>拉不开弓</b>，而这不是本武器的设计意图。
 * 本武器的节奏由「标记 ⇒ 共鸣圈 ⇒ 召圣」这条链条控制（每个环节都有各自的时长与次数），
 * 不靠射速 CD。因此 {@code onCast} 也不做冷却（左键 / Q 在本武器上没有产品语义）。
 *
 * <h2>「血泪」怎么认（为什么读箭矢实体而不是背包）</h2>
 * 离弦那一刻，原版已经把"这一箭用的是哪种箭"复制到箭矢实体上（{@code Arrow} 的药水数据），
 * 而"刚才消耗的是背包哪一格"没有稳定读数（可能来自副手、可能刚被补满）。
 * 因此判据 = 箭矢实体上是否存在<b>瞬时伤害</b>药水数据；读不到就按普通箭处理（{@code false}）。
 * 该布尔值当前只作为标记状态保留（血泪与普通箭走同一套标记 / 圈规则），把来源记下来是为了
 * 以后按来源分叉时不必再改一遍状态结构。
 *
 * <h2>与共鸣组件的分工</h2>
 * 本类只负责"识别事件 + 把箭交给共鸣组件 + 表现"，不持有任何标记 / 圈状态；
 * 所有结算（伤害、圈、召圣）都在 {@link LetheResonanceComponent} 里，因为那些状态的消费者
 * 是三个技能，而不是这把弓。
 *
 * <h2>原版箭矢伤害照常生效（如实申报）</h2>
 * 与弓弩家族的既有口径一致：本插件不拦原版箭矢伤害。目标先吃原版这一箭，再吃本武器追加的
 * "10 点特殊值 + 4 点真实伤害"。要让声明值替代原版伤害，须在射击管道上加一条
 * {@code EntityDamageByEntityEvent} 拦截 —— 那是一次产品口径变更，本组件不擅自改。
 */
public class LetheMainWeapon extends BowWeapon {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "lethe_main_weapon";

    /** 冷却声明值：弓的右键归原版，本武器不设冷却（理由见类注释）。 */
    private static final int COOLDOWN_TICKS = 0;

    // ───────── 近战攻击（2026-10-31 需求：位置与数值都只在这里改）─────────

    /** 近战每击物理伤害。 */
    private static final double MELEE_PHYSICAL_DAMAGE = 8d;

    /** 近战每击特殊值伤害下限。 */
    private static final int MELEE_SANTE_MIN = 3;

    /** 近战每击特殊值伤害上限（含）。 */
    private static final int MELEE_SANTE_MAX = 6;

    /** 近战击退力度（{@code DamageUtil.applyKnockback} 的 strength 参数；2026-10-31 用户裁定 1.2）。 */
    private static final double MELEE_KNOCKBACK_STRENGTH = 1.2d;

    /**
     * **武器攻速 = 4.0**（每 0.25 秒可满蓄力一次）—— 本武器近战没有插件 CD，出手频率完全由它决定。
     * <p>原版弓自带攻击速度 1.0（相对玩家基础 4.0 是 -3.0 的惩罚）；本武器在
     * {@link #applyAttackSpeed(ItemMeta)} 里**摘掉这条惩罚**，于是攻速回到玩家基础 4.0。
     * <p>想改近战手感只动这一条常量（并相应调整那行属性写入）。
     */
    private static final double MELEE_ATTACK_SPEED_TOTAL = 4.0d;

    /** 判定"原版满蓄力"的阈值（{@code Player#getAttackCooldown()} ∈ [0,1]）。 */
    private static final float MELEE_FULL_CHARGE = 0.99f;

    // ───────── 附魔（需求值，唯一修改点）─────────

    private static final int KNOCKBACK_LEVEL = 4;
    private static final int SHARPNESS_LEVEL = 6;
    private static final int POWER_LEVEL = 3;
    private static final int PUNCH_LEVEL = 1;

    /** 显示名（亮蓝加粗）：描述符与 {@link #buildItem()} 共用同一份，避免两处漂移。 */
    private static final Component DISPLAY_NAME =
            Component.text("Lether", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);

    // ───────── 协作组件（start() 里一次取好，stop() 成对置空）─────────

    /** 标记 / 共鸣圈的持有者：本武器只把箭交给它。 */
    private LetheResonanceComponent resonance;

    /** 主武器闸门（眩晕时不许使用）。 */
    private BuffComponent buff;

    /** 敌我判定（"命中的是不是敌对玩家"）。 */
    private FactionComponent faction;

    /** 生命组件（近战物理伤害的结算入口）。 */
    private VitalsComponent vitals;

    /** 特殊值组件（近战扣目标特殊值）。 */
    private SanTEComponent sante;

    public LetheMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。
     * <p>依赖 = 实取清单：共鸣组件（标记的唯一持有者）、buff（闸门）、阵营（索敌）。
     */
    public static final class Specification extends BowWeapon.Specification<LetheMainWeapon> {

        public Specification() {
            super(DISPLAY_NAME,
                    List.of(
                            Component.text("离弦：箭矢被打上「流连」标记"),
                            Component.text("标记命中敌人：扣除其10点特殊值，并造成4点真实伤害"),
                            Component.text("箭矢落地/失效：在停点生成半径3的共鸣圈"),
                            Component.text("共鸣圈：每0.25秒对圈内敌人造成1点真实伤害与2点特殊值伤害"),
                            Component.text("共鸣圈伤害兑现5次后消散；可被「召圣」抬升、召回或引爆"),
                            Component.text("血泪箭与普通箭同样会被标记（血泪由箭袋持续补给）"),
                            Component.text("附魔：击退4 / 锋利6 / 力量3 / 冲击1"),
                            Component.text("近战：8 物理 + 3~6 特殊值，击退 1.2；无插件CD（由本武器攻速决定）"),
                            Component.text("右键拉弓归原版；本武器没有冷却")
                    ),
                    COOLDOWN_TICKS,
                    Material.BOW);
            requires(LetheResonanceComponent.class)
                    .requires(BuffComponent.class)
                    .requires(FactionComponent.class)
                    .requires(VitalsComponent.class)
                    .requires(SanTEComponent.class);
        }

        @Override
        public LetheMainWeapon create(String id, ComponentServicesPort services) {
            return new LetheMainWeapon(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        resonance = svc().components().get(LetheResonanceComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
    }

    /** 停止生效：清掉协作组件引用（标记 / 圈状态由共鸣组件自己的 {@code stop()} 回收）。 */
    @Override
    public void stop() {
        resonance = null;
        buff = null;
        faction = null;
        vitals = null;
        sante = null;
    }

    // ───────── 入口一：箭矢离弦 ─────────

    /**
     * 箭矢离弦：给这一支箭打「流连」标记，并播离弦拖尾与弓弦音。
     * <p>冷却不在这里启动（声明值为 0，且弓的节奏不靠射速 CD）。
     */
    @Override
    public void onShoot(ShootSignal signal) {
        if (signal == null || !(signal.projectile() instanceof AbstractArrow projectile)) {
            return;
        }
        boolean bloodTear = isBloodTear(projectile);
        if (resonance != null) {
            resonance.markArrow(projectile, bloodTear);
        }
        World world = projectile.getWorld();
        if (world == null) {
            return;
        }
        Location at = projectile.getLocation();
        Player self = selfPlayer();
        LetheVfx.arrowMarkTrail(world, self != null ? self.getEyeLocation() : at, at);
        LetheSound.bowShoot(world, at);
    }

    // ───────── 入口二：箭矢命中 ─────────

    /**
     * 箭矢命中实体 / 方块：命中敌对玩家则兑现标记，随后把落点交给共鸣组件生成共鸣圈，最后移除箭矢。
     *
     * <p><b>顺序（三段的理由各不同）</b>：
     * <ol>
     *   <li><b>先兑现命中</b>：{@code onMarkedArrowHit} 会消耗这枚标记（从标记表移除），
     *       因此它之后调 {@code onMarkedArrowStopped} 不会在敌人脚下再长出一个圈；</li>
     *   <li><b>再收口落点</b>：原版箭矢命中后必然停下（本武器随后还会移除它），所以无论命中实体
     *       还是方块都通知一次"停"——共鸣组件按 UUID 幂等，重复通知不会出第二个圈；</li>
     *   <li><b>最后移除箭矢</b>：不留在地上被拾取（本武器射出的箭是"标记载体"，不是消耗品）。</li>
     * </ol>
     * <p>视觉：命中点画一枚「流连」兑现的十字爆点并播命中音（命中是否敌对不影响这层表现）。
     */
    @Override
    public void onProjectileHit(ProjectileHitSignal signal) {
        if (signal == null || signal.projectile() == null) {
            return;
        }
        Projectile projectile = signal.projectile();
        Location at = projectile.getLocation().clone();
        World world = projectile.getWorld();
        Player self = selfPlayer();

        if (self != null && signal.hitEntity() instanceof Player victim && isHostileVictim(self, victim)) {
            if (resonance != null) {
                resonance.onMarkedArrowHit(projectile, victim);
            }
        }
        if (resonance != null) {
            resonance.onMarkedArrowStopped(projectile);
        }
        projectile.remove();

        if (world != null) {
            LetheVfx.arrowMarkHit(world, at);
            LetheSound.arrowHit(world, at);
        }
    }

    // ───────── 入口三：近战命中（2026-10-31 新增）─────────

    /**
     * **近战命中**（左键打人）：8 物理伤害 + 3~6 特殊值伤害 + 击退力度 1.2。
     *
     * <p><b>攻击无插件 CD</b>：出手频率完全由本武器自身的攻速属性决定 —— 本方法只认原版**满蓄力**
     * （{@link Player#getAttackCooldown()} ≥ {@value #MELEE_FULL_CHARGE}），未满蓄力的那次点击直接忽略。
     * 因此"能打多快"= 武器攻速（见 {@link #MELEE_ATTACK_SPEED_BONUS}），而不是任何插件计时器。
     *
     * <p><b>为什么自己结算伤害</b>：{@code BowWeaponListener} 仍会取消原版近战伤害
     * （弓弩物品不造成原版伤害的既有口径），所以这里必须走生命组件的物理伤害入口。
     * 这也是 2026-10-31 框架放开 {@code BowWeapon#onAttack} 的唯一原因。
     *
     * <p>结算后清一次目标的受击无敌帧：按武器攻速连续出手时（最快 0.25 秒一击），
     * 上一次命中留下的 10 刻无敌会吞掉下一击。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        if (signal == null || signal.victim() == null) {
            return;
        }
        Player self = selfPlayer();
        if (self == null || !self.isOnline() || self.isDead() || !canUse()) {
            return;
        }
        //只认原版满蓄力：攻速属性决定频率（本武器没有插件 CD）
        if (self.getAttackCooldown() < MELEE_FULL_CHARGE) {
            return;
        }
        Player victim = signal.victim();
        if (vitals != null) {
            vitals.physicalDamage(victim, self, MELEE_PHYSICAL_DAMAGE, MELEE_KNOCKBACK_STRENGTH);
        }
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(),
                    ThreadLocalRandom.current().nextInt(MELEE_SANTE_MIN, MELEE_SANTE_MAX + 1));
        }
        //按攻速连击时，上一次命中的受击无敌帧会吞掉下一击 ⇒ 结算后清一次
        victim.setNoDamageTicks(0);
        World world = victim.getWorld();
        if (world != null) {
            Location at = victim.getLocation().add(0d, 1.0d, 0d);
            LetheVfx.arrowMarkHit(world, at);
            LetheSound.meleeHit(world, at);
        }
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕（STUN）时不许使用本武器 —— 与主武器侧口径一致。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    //能量维度由 BowWeapon 用 final 封死（声明耗能 ≡ 0）⇒ 本类不覆写 currentEnergy()。

    // ───────── 物品画法 ─────────

    /**
     * 热键栏物品：先取基类完整画法（三态材质 / 名称 / 状态行 / lore / 识别键），再补四道附魔与亮蓝加粗名。
     * <p>附魔第三参必须为 {@code true}（忽略等级上限）：原版对弓的附魔等级有上限
     * （力量最高 5 等），不忽略的话声明的 6 级锋利 / 4 级击退会被静默夹回原版上限。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.addEnchant(Enchantment.KNOCKBACK, KNOCKBACK_LEVEL, true);
        meta.addEnchant(Enchantment.SHARPNESS, SHARPNESS_LEVEL, true);
        meta.addEnchant(Enchantment.POWER, POWER_LEVEL, true);
        meta.addEnchant(Enchantment.PUNCH, PUNCH_LEVEL, true);
        //近战"攻击无CD"：把攻速属性补到 MELEE_ATTACK_SPEED_BONUS 对应的总量（先摘后加，防叠层）
        applyAttackSpeed(meta);
        meta.displayName(DISPLAY_NAME);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * **攻击无CD的实现点**：原版攻击冷却 = 1 / 攻速。摘掉弓自带的攻速惩罚（-3.0）后，
     * 本武器攻速 = 玩家基础的 {@value #MELEE_ATTACK_SPEED_TOTAL}（0.25 秒/击）；
     * 插件侧不再有任何 CD。
     * <p>为什么只摘不加：{@code buildItem()} 会被热键栏反复调用，任何 "先摘后加" 的写法
     * 都必须保证幂等；当前目标值恰好等于玩家基础攻速，所以"摘掉惩罚"就是完整实现。
     */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
    }

    // ───────── 内部工具 ─────────

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null} —— 两个入口都容忍它）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    /** 命中目标是不是"别人 + 在线活着 + 敌对"（排除自己与同阵营）。 */
    private boolean isHostileVictim(Player self, Player victim) {
        if (victim == null || victim.getUniqueId().equals(self.getUniqueId())) {
            return false;
        }
        if (!victim.isOnline() || victim.isDead() || victim.getHealth() <= 0d) {
            return false;
        }
        return faction != null && faction.isHostileTo(victim.getUniqueId());
    }

    /**
     * 这一箭是否由「血泪」射出：判据 = 箭矢实体上带着瞬时伤害药水数据（基础药水或自定义效果）。
     * <p>再补一次"箭物品"的读法：不同平台版本把药水数据放在实体侧或物品侧的时机不完全一致，
     * 两处都读一遍能覆盖"实体侧还没同步"的那一帧；都拿不到就按普通箭处理（不抛）。
     */
    private static boolean isBloodTear(Projectile projectile) {
        if (!(projectile instanceof Arrow arrow)) {
            return false;
        }
        if (arrow.hasCustomEffect(PotionEffectType.INSTANT_DAMAGE)) {
            return true;
        }
        if (isHarmingPotion(arrow.getBasePotionType())) {
            return true;
        }
        ItemStack item = arrow.getItemStack();
        if (item != null && item.getItemMeta() instanceof PotionMeta potion) {
            if (potion.hasCustomEffect(PotionEffectType.INSTANT_DAMAGE)) {
                return true;
            }
            return isHarmingPotion(potion.getBasePotionType());
        }
        return false;
    }

    /** 基础药水类型是否是"瞬时伤害"（{@code HARMING} = I 级、{@code STRONG_HARMING} = II 级）。 */
    private static boolean isHarmingPotion(PotionType type) {
        return type == PotionType.HARMING || type == PotionType.STRONG_HARMING;
    }
}
