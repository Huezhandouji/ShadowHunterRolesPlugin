package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

/**
 * 「艾尔伯特」主武器：**构筑者铳剑**。
 *
 * <h2>形态</h2>
 * 一把"能砍也能打"的铳剑 —— 近战是重刃，右键是 6 发弹匣的亚音速穿甲弹。
 *
 * <h2>需求逐条（近战）</h2>
 * <ol>
 *   <li>造成 <b>9 点物理伤害</b>与 <b>3~6 点特殊值伤害</b>；</li>
 *   <li><b>每命中一次积攒 1 层「零件」</b>，满 {@link AlbertLockOnPassive#PARTS_REQUIRED} 层
 *       自动部署一架高斯无人机（不超过 {@link AlbertDroneSystem#MAX_DRONES} 架）；</li>
 *   <li>攻击 CD <b>0.4 秒</b>（{@value #ATTACK_COOLDOWN_TICKS} 刻）。</li>
 * </ol>
 *
 * <h2>需求逐条（右键射击）</h2>
 * <ol>
 *   <li>发射<b>亚音速穿甲弹</b>，弹匣共 <b>{@value #MAGAZINE_SIZE} 发</b>；</li>
 *   <li><b>每 5 秒【消耗 2 能量】回复 1 发</b>（能量不足 ⇒ 这一拍不回复）；</li>
 *   <li>命中造成 <b>9 点物理</b> + <b>3~6 点特殊值</b>，附加<b>缓慢 II 1 秒</b>、<b>中毒 5 秒</b>，
 *       并把对方标记为<b>「猎杀目标」20 秒</b>；</li>
 *   <li>每发间隔 <b>0.1 秒</b>（{@value #SHOT_INTERVAL_TICKS} 刻）；</li>
 *   <li>在 <b>actionbar</b> 显示剩余弹药（由 {@link AlbertDroneSystem} 统一发布）。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：为什么「子弹」是命中扫描而不是召唤一个投射物实体</h2>
 * 插件的攻击派发集中在 listener（{@code MainWeaponListener} / {@code BowWeaponListener}），
 * 而<b>组件不允许自挂 Bukkit Listener</b>（服务集拿不到 {@code Plugin}，工程铁律）。
 * 若召唤一个真实投射物，命中就得靠 {@code ProjectileHitEvent} 才能知道 ⇒ 组件够不着。
 * ⇒ 本武器用**命中扫描**（{@code rayTraceBlocks} 求墙体距离 + "<b>长廊</b>"判定命中玩家），
 * 弹道只画粒子。这也正好符合工程既有结论：<b>范围 / 前向命中用长廊，不要用射线</b>
 * （准星稍偏即落空；长廊用"沿视线方向的投影 + 侧向偏离"两个量，手感稳定）。
 *
 * <h2>★★ 口径申报二：近战 CD 与射击间隔是两个独立的计时器</h2>
 * <ul>
 *   <li><b>近战</b>用组件自带的冷却（{@code startCooldown()}，声明值
 *       {@value #ATTACK_COOLDOWN_TICKS} 刻 = 0.4 秒）—— 这样热键栏图标会走框架既有的
 *       "冷却态变结构空位"画法，不必自己重画；</li>
 *   <li><b>射击</b>用本类自己的 {@link #nextShotTick} 字段（{@value #SHOT_INTERVAL_TICKS} 刻）
 *       —— <b>不</b>调用 {@code startCooldown()}。理由：两者间隔差 4 倍，
 *       共用一个冷却只会让"连射"被 0.4 秒的近战冷却压住。</li>
 * </ul>
 * ★ <b>代价（如实申报）</b>：施放管道（{@code MainWeaponListener#cast}）会检查
 * {@code isCoolingDown()} ⇒ 近战命中后的 0.4 秒内右键<b>打不出子弹</b>。
 * 这是"刚挥完重刃"的恢复期，与手感方向一致；若要彻底解耦必须改框架，
 * 而框架是禁改区 ⇒ 接受这一条。
 *
 * <h2>★★ 口径申报三：「零件」只由近战积攒</h2>
 * 需求把"每命中一次积攒 1 层零件"写在**近战攻击**那一条下面，右键射击那一条只写了
 * "标记猎杀目标"，没提零件 ⇒ 本武器只在 {@link #onAttack} 里积攒零件。
 *
 * <h2>★★ 口径申报四：攻速统一 100</h2>
 * 工程规矩（{@code HunterGrudgeMainWeapon} 等既有主武器同口径）：主武器物品攻速统一压到
 * {@value #ATTACK_SPEED_VALUE}，且**先摘后加**（{@code buildItem()} 会被反复调用，
 * 同一把键不去重会叠成 100/200/300…）。
 */
public class AlbertMainWeapon extends MainWeapon {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_mainWeapon_gunblade";

    /** 近战攻击冷却（刻）—— 需求原话"攻击CD:0.4S"。 */
    private static final int ATTACK_COOLDOWN_TICKS = 8;

    /** 攻击速度统一值（压掉原版攻击冷却）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速修饰符的键（先摘后加 ⇒ 反复重绘不叠加）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("albert_gunblade_attack_speed");

    // ───────── 近战 ─────────

    /** 近战物理伤害（需求原话"造成 9 点物理伤害"）。 */
    public static final double MELEE_PHYSICAL_DAMAGE = 9d;

    /** 近战特殊值伤害区间（需求原话"3~6 点特殊值伤害"，两端都含）。 */
    public static final int MELEE_SANTE_MIN = 3;
    public static final int MELEE_SANTE_MAX = 6;

    // ───────── 射击 ─────────

    /** 弹匣容量（需求原话"弹匣一共 6 发"）。 */
    public static final int MAGAZINE_SIZE = 6;

    /** 射击间隔（刻）—— 需求原话"每发间隔0.1S"。 */
    public static final int SHOT_INTERVAL_TICKS = 2;

    /** 弹药回复周期（刻）—— 需求原话"每 5 秒"。 */
    public static final int AMMO_REGEN_TICKS = 100;

    /** 每次回复弹药的能量消耗（需求原话"【消耗2能量】"）。 */
    public static final int AMMO_REGEN_ENERGY_COST = 2;

    /** 弹道有效射程（格）。 */
    public static final double SHOT_RANGE = 30.0d;

    /** 命中判定的**侧向容差**（格）：长廊的半宽（太窄 ⇒ 准星稍偏即落空）。 */
    public static final double SHOT_HIT_RADIUS = 0.95d;

    /** 射击物理伤害（需求原话"命中造成 9 点物理伤害"）。 */
    public static final double SHOT_PHYSICAL_DAMAGE = 9d;

    /** 射击特殊值伤害区间（需求原话"3~6点特殊值伤害"）。 */
    public static final int SHOT_SANTE_MIN = 3;
    public static final int SHOT_SANTE_MAX = 6;

    /** 命中附加的缓慢（需求原话"缓慢 2 持续 1秒"）⇒ 增幅 1。 */
    public static final int SHOT_SLOWNESS_AMPLIFIER = 1;
    public static final int SHOT_SLOWNESS_TICKS = 20;

    /** 命中附加的中毒（需求原话"中毒一5秒"）⇒ 增幅 0。 */
    public static final int SHOT_POISON_AMPLIFIER = 0;
    public static final int SHOT_POISON_TICKS = 100;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private EnergyComponent energy;
    private AlbertLockOnPassive lockOn;
    private AlbertDroneSystem drones;
    private AlbertFloatingTextComponent floatingText;

    private final Random random = new Random();

    // ───────── 运行期状态 ─────────

    /** 当前弹药（需求："弹匣"）。 */
    private int ammo = MAGAZINE_SIZE;

    /** 下一发的可用刻（射击间隔）。 */
    private int nextShotTick;

    /** 弹药回复节拍。 */
    private int regenTick;

    public AlbertMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：铁剑（"铳剑"的模型）+ 逐条对应需求的说明。
     * <p>主武器 {@code energyCost} 由类型恒为 0 ⇒ {@code ENERGY_LACK} 不可达（冻结面口径）。
     */
    public static final class Specification extends MainWeapon.Specification<AlbertMainWeapon> {

        public Specification() {
            super(Component.text("构筑者铳剑"),
                    List.of(
                            Component.text("近战：9 点物理伤害 + 3~6 点特殊值伤害"),
                            Component.text("近战每命中一次积攒 1 层「零件」，满 4 层自动部署一架高斯无人机"),
                            Component.text("右键：发射亚音速穿甲弹，弹匣 6 发"),
                            Component.text("命中造成 9 点物理 + 3~6 点特殊值，附加缓慢 II 1 秒与中毒 5 秒"),
                            Component.text("命中标记对方为「猎杀目标」20 秒"),
                            Component.text("每 5 秒消耗 2 能量回复 1 发；攻击冷却 0.4 秒，每发间隔 0.1 秒")
                    ),
                    Material.IRON_SWORD,
                    ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(FactionComponent.class);
            requires(SanTEComponent.class);
            requires(BuffComponent.class);
            requires(EnergyComponent.class);
            requires(AlbertLockOnPassive.class);
            requires(AlbertDroneSystem.class);
            requires(AlbertFloatingTextComponent.class);
        }

        @Override
        public AlbertMainWeapon create(String id, ComponentServicesPort services) {
            return new AlbertMainWeapon(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        lockOn = svc().components().get(AlbertLockOnPassive.class);
        drones = svc().components().get(AlbertDroneSystem.class);
        floatingText = svc().components().get(AlbertFloatingTextComponent.class);
        ammo = MAGAZINE_SIZE;
        regenTick = 0;
    }

    @Override
    public void stop() {
        vitals = null;
        sante = null;
        buff = null;
        energy = null;
        lockOn = null;
        drones = null;
        floatingText = null;
    }

    // ───────── 每刻：回复弹药 + 推送 HUD ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        regenTick++;
        if (regenTick >= AMMO_REGEN_TICKS) {
            regenTick = 0;
            tryRegenAmmo(owner);
        }
        // ★ 弹药读数推给无人机系统（由它统一发 actionbar —— 单一发布者，避免互相覆盖）
        if (drones != null) {
            drones.publishAmmo(ammo, MAGAZINE_SIZE);
        }
    }

    /**
     * **每 5 秒回复一发**（需求："每 5 秒【消耗 2 能量】回复 1 发"）。
     *
     * <p>弹匣已满 / 能量不足 ⇒ 这一拍不回复（不排队、不欠账）。
     */
    private void tryRegenAmmo(Player owner) {
        if (ammo >= MAGAZINE_SIZE) {
            return;
        }
        if (energy == null || !energy.tryConsume(AMMO_REGEN_ENERGY_COST)) {
            return;
        }
        ammo++;
        World world = owner.getWorld();
        if (world != null) {
            AlbertSound.reloadAlbertGunbladeMagazineSound(world, owner.getLocation());
        }
    }

    // ───────── 近战 ─────────

    /**
     * 近战命中（{@code listener/MainWeaponListener#onAttackPlayer} 是唯一投递点）。
     *
     * <p>顺序（不可交换）：
     * <ol>
     *   <li><b>冷却自判</b> —— 冷却中这一下完全不出伤（派发侧不替主武器挡冷却）；</li>
     *   <li>闸门（眩晕时不可用）；</li>
     *   <li>9 点物理 → 3~6 点特殊值；</li>
     *   <li>「猎杀目标」追加 1 点真实伤害（被动「锁定」）；</li>
     *   <li>积攒 1 层「零件」，满层 ⇒ 部署一架无人机；</li>
     *   <li>清一次受击无敌帧（连打必需）；</li>
     *   <li>结算成功 ⇒ 启动冷却。</li>
     * </ol>
     */
    @Override
    public void onAttack(AttackSignal signal) {
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

        //① 9 点物理
        vitals.physicalDamage(victim, owner, MELEE_PHYSICAL_DAMAGE);
        //② 3~6 点特殊值（跨实例入口：扣的是【对方】的 SanTE）
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(),
                    rollInclusive(random, MELEE_SANTE_MIN, MELEE_SANTE_MAX));
        }
        //③ 对「猎杀目标」追加 1 点真实伤害（被动「锁定」）
        applyMarkBonus(owner, victim);

        //④ 积攒零件（★ 只有近战积攒）
        if (lockOn != null && lockOn.addPart() && drones != null) {
            if (drones.deployOne()) {
                World world = owner.getWorld();
                if (world != null) {
                    AlbertSound.beaconActivateAlbertAutoDeploySound(world,
                            owner.getLocation().clone().add(0d, 1.2d, 0d));
                }
            }
        } else if (lockOn != null) {
            World world = owner.getWorld();
            if (world != null) {
                AlbertSound.latchAlbertPartLoadedSound(world,
                        owner.getLocation().clone().add(0d, 1.2d, 0d));
            }
        }

        World world = owner.getWorld();
        if (world != null) {
            AlbertVfx.hitSparkAlbertGunbladeMelee(world, victim.getLocation().clone().add(0d, 1.0d, 0d));
            AlbertSound.ironGolemAttackAlbertGunbladeMeleeImpactSound(world,
                    victim.getLocation().clone().add(0d, 1.0d, 0d));
        }

        //⑤ 连打必需：扣血后再清一次受击无敌帧（工程既有口径）
        victim.setNoDamageTicks(0);

        //⑥ 台词（命中类自带内部节流，见 AlbertFloatingTextComponent）
        if (floatingText != null) {
            floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_MELEE_HIT);
        }

        //⑦ 结算成功 ⇒ 启动冷却
        startCooldown();
    }

    /** 对「猎杀目标」追加 1 点真实伤害（需求："你对「猎杀目标」的攻击附加 1 点真实伤害"）。 */
    private void applyMarkBonus(Player owner, Player victim) {
        if (lockOn == null || vitals == null) {
            return;
        }
        if (lockOn.isMarked(victim.getUniqueId())) {
            vitals.trueDamage(victim, owner, AlbertLockOnPassive.MARK_TRUE_DAMAGE_BONUS);
        }
    }

    // ───────── 右键射击 ─────────

    /**
     * 右键：发射一发亚音速穿甲弹（{@code CastTrigger.RIGHT_CLICK}）。
     *
     * <p>顺序：射速闸门 → 闸门（眩晕）→ 弹匣检查 → 打空音 → 命中扫描 → 结算 → 扣弹。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal == null || signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !canUse()) {
            return;
        }
        //① 射速闸门（0.1 秒/发）—— 不占组件冷却
        if (org.bukkit.Bukkit.getCurrentTick() < nextShotTick) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        //② 弹匣
        if (ammo <= 0) {
            AlbertSound.clickEmptyAlbertGunbladeDryFireSound(world, owner.getLocation());
            nextShotTick = org.bukkit.Bukkit.getCurrentTick() + SHOT_INTERVAL_TICKS;
            return;
        }

        Location eye = owner.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        //③ 命中扫描（沿视线的长廊）
        Player victim = hitscan(owner, eye, direction);

        //④ 结算
        ammo--;
        nextShotTick = org.bukkit.Bukkit.getCurrentTick() + SHOT_INTERVAL_TICKS;
        Location muzzle = eye.clone().add(direction.clone().multiply(1.0d));
        Location impact = victim != null
                ? victim.getLocation().clone().add(0d, 1.0d, 0d)
                : muzzle.clone().add(direction.clone().multiply(SHOT_RANGE));
        AlbertVfx.tracerAlbertGunbladeShot(world, muzzle, impact);
        AlbertSound.crossbowShootAlbertGunbladeShotSound(world, muzzle);

        if (victim != null) {
            resolveShot(owner, victim, impact);
        }

        if (drones != null) {
            drones.publishAmmo(ammo, MAGAZINE_SIZE);
        }
    }

    /** 射击命中结算：9 物理 + 3~6 特殊值 + 缓慢 II 1s + 中毒 5s + 标记 20s + 猎杀目标真伤。 */
    private void resolveShot(Player owner, Player victim, Location impact) {
        World world = owner.getWorld();
        if (vitals != null) {
            vitals.physicalDamage(victim, owner, SHOT_PHYSICAL_DAMAGE);
        }
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(),
                    rollInclusive(random, SHOT_SANTE_MIN, SHOT_SANTE_MAX));
        }
        // ★ 必须直接对"目标"上药水 —— buff.applyPotionEffect(...) 全部重载都只作用于自己
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                SHOT_SLOWNESS_TICKS, SHOT_SLOWNESS_AMPLIFIER, true, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                SHOT_POISON_TICKS, SHOT_POISON_AMPLIFIER, true, false, false));

        boolean fresh = lockOn != null && lockOn.mark(victim);
        applyMarkBonus(owner, victim);
        victim.setNoDamageTicks(0);

        if (world != null) {
            AlbertSound.netheriteHitAlbertGunbladeShotImpactSound(world, impact);
            if (fresh) {
                AlbertSound.elderGuardianCurseAlbertHuntTargetSound(world, impact);
                AlbertVfx.markAlbertHuntTarget(world, impact.clone().add(0d, 1.2d, 0d));
            }
        }
        // 台词：新标记 ⇒ 说"锁定"那一组；否则说"射击命中"那一组
        if (floatingText != null) {
            floatingText.saySpecial(owner, fresh
                    ? AlbertFloatingTextComponent.MOMENT_LOCK
                    : AlbertFloatingTextComponent.MOMENT_SHOT_HIT);
        }
    }

    /**
     * **命中扫描**（长廊判定）。
     *
     * <p>两个量：
     * <ol>
     *   <li><b>沿轴向的投影</b>（{@code along = Δ · dir}）—— 必须在 {@code (0, 墙体距离]}；
     *       墙体距离由 {@code rayTraceBlocks} 给出 ⇒ 子弹不会穿墙；</li>
     *   <li><b>侧向偏离</b>（{@code |Δ − along·dir|}）—— 必须小于
     *       {@link #SHOT_HIT_RADIUS}。</li>
     * </ol>
     * 这就是工程既有的"长廊"口径：侧向用投影减法（天然不含竖直分量），不拿三维距离当侧向偏离。
     *
     * @return 最近的敌方玩家；没有 ⇒ {@code null}
     */
    private Player hitscan(Player owner, Location eye, Vector direction) {
        World world = owner.getWorld();
        if (world == null) {
            return null;
        }
        double limit = SHOT_RANGE;
        RayTraceResult blockHit = world.rayTraceBlocks(eye, direction, SHOT_RANGE,
                FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitPosition() != null) {
            limit = Math.min(limit, blockHit.getHitPosition().distance(eye.toVector()));
        }
        Player best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Player candidate : eye.getNearbyPlayers(SHOT_RANGE)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            Location chest = candidate.getLocation().clone().add(0d, 1.0d, 0d);
            Vector delta = chest.toVector().subtract(eye.toVector());
            double along = delta.dot(direction);
            if (along <= 0d || along > limit) {
                continue;
            }
            double lateral = delta.clone().subtract(direction.clone().multiply(along)).length();
            if (lateral > SHOT_HIT_RADIUS) {
                continue;
            }
            if (along < bestAlong) {
                bestAlong = along;
                best = candidate;
            }
        }
        return best;
    }

    /** 存活判定（死亡 / 已下线一律排除）。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** 闸门：被眩晕时主武器不可用（{@code canUseMainWeapon()} = 非 STUN）。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    // ───────── 外观：攻速统一 100 ─────────

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
     * {@link #buildItem()} 会被反复调用，同一把键不去重就会叠加成 100/200/300…
     */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    // ───────── 读口 ─────────

    /** 当前弹药。 */
    public int ammo() {
        return ammo;
    }

    /** 弹匣容量。 */
    public int magazineSize() {
        return MAGAZINE_SIZE;
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **闭区间随机整数** {@code [min, max]}（纯函数 ⇒ 可离线穷举边界）。
     *
     * <p>与 {@code Random#nextInt(bound)}（半开区间）的区别正是这里必须用它的原因：
     * 需求写的是"3~6 点"，**两端都含**。
     */
    public static int rollInclusive(Random random, int min, int max) {
        if (random == null || max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /**
     * **弹药回复是否应当发生**（**纯函数**）：弹匣未满 且 能量足够。
     *
     * @param ammo     当前弹药
     * @param capacity 弹匣容量
     * @param energy   当前能量
     */
    public static boolean shouldRegenAmmo(int ammo, int capacity, int energy) {
        return ammo < capacity && energy >= AMMO_REGEN_ENERGY_COST;
    }

    /**
     * **一发是否能打出**（**纯函数**）：有弹药 且 过了射速闸门。
     *
     * @param ammo       当前弹药
     * @param nowTick    当前刻
     * @param nextShotAt 下一发可用刻
     */
    public static boolean canFire(int ammo, int nowTick, int nextShotAt) {
        return ammo > 0 && nowTick >= nextShotAt;
    }
}
