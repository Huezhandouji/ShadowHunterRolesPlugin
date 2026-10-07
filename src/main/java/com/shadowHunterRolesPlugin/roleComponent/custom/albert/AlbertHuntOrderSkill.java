package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 「艾尔伯特」技能之二：**猎杀指令**（金锭）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>发射一枚<b>信号弹射弹</b>，标记目标区域（半径 {@value AlbertDroneSystem#FOCUS_RADIUS} 格）；</li>
 *   <li><b>命令所有高斯无人机集火该区域</b>，持续 <b>10 秒</b>（★ 2026-10-06 由 5 秒上调）；</li>
 *   <li><b>目标区域高亮</b>（地面主环 + 内环 + 四根角柱，窗口期内常亮）；</li>
 *   <li><b>区域内的敌人被无人机无视距离优先攻击</b>（无人机索敌不再受射程限制）；</li>
 *   <li>期间<b>无人机攻击速度提升 50%</b>（"无论是无人机间攻击间隔还是无人机自己的攻击 CD 都加快"）；</li>
 *   <li>期间每次无人机攻击<b>额外造成 2 点灵魂伤害</b>；</li>
 *   <li>并给<b>区域内所有敌人</b>附加「猎杀目标」；</li>
 *   <li>CD <b>12 秒</b>；能量消耗 <b>10</b>。</li>
 * </ol>
 *
 * <h2>★ 口径申报一："目标区域"怎么定</h2>
 * 需求说"发射一枚信号弹射弹，标记目标区域" ⇒ 区域中心取<b>落点</b>：
 * <ol>
 *   <li>视线长廊里若有敌方玩家 ⇒ 以他为落点（"命令集火那个方向的人"）；</li>
 *   <li>否则用 {@code rayTraceBlocks} 的<b>方块命中点</b>（打在墙上就地标记）；</li>
 *   <li>都没有（对天开枪）⇒ 取视线方向 {@value #MAX_AIM_RANGE} 格处的空中点。</li>
 * </ol>
 *
 * <h2>★ 口径申报二：攻速 +50% 的落地方式</h2>
 * 需求的括号写得很清楚 —— <b>两个</b>节拍都要加快：
 * ① 无人机之间的 0.7 秒闸门；② 每架自己的 6 秒出击 CD。
 * ⇒ 两处都乘 {@link AlbertDroneSystem#FOCUS_SPEED_MULTIPLIER}
 * （见 {@code AlbertDroneSystem.intervalTicks}，它被这两处共同调用 ⇒ 口径唯一）。
 *
 * <h2>★ 口径申报三：区域标记是"一次性快照 + 窗口内的新目标也吃"</h2>
 * 施放瞬间给区域内敌人打标；窗口期间<b>新走进区域的敌人</b>会在无人机索敌时被找到并攻击
 * （无人机在集火窗口内只选区域内的目标），但不会自动补「猎杀目标」标记
 * —— 补标记需要每刻扫描，而需求只说"给区域内的所有敌人附加"，取施放瞬间的读数即可。
 */
public class AlbertHuntOrderSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_skill_huntOrder";

    /** 冷却：12 秒（需求原话"CD-12"）。 */
    private static final int COOLDOWN_TICKS = 240;

    /** 能量消耗（需求原话"能量消耗10"）。 */
    private static final int ENERGY_COST = 10;

    /**
     * 信号弹的**最大射程**（格，需求原话"最大射程25格"）。
     *
     * <p>★ 到达该距离即**自动爆开**：落点取"视线方向 25 格处"（见
     * {@link #aimPoint} 的三级兜底），并照常在落点开集火窗口 + 播落点音效
     * ⇒ "打不到东西也会在 25 格处炸开"，不会出现"对着天空放，什么都没有发生"。
     */
    public static final double MAX_AIM_RANGE = 25.0d;

    /** 命中判定的侧向容差（格）—— 与主武器同口径的长廊半宽。 */
    public static final double AIM_HIT_RADIUS = 1.4d;

    private AlbertDroneSystem drones;
    private AlbertLockOnPassive lockOn;
    private EnergyComponent energy;
    private BuffComponent buff;
    private AlbertFloatingTextComponent floatingText;

    public AlbertHuntOrderSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<AlbertHuntOrderSkill> {

        public Specification() {
            super(Component.text("猎杀指令"),
                    List.of(
                            Component.text("发射信号弹（最大射程 25 格，到达即爆开）标记半径 10 格的区域"),
                            Component.text("目标区域高亮显示，命令所有无人机集火该区域，持续 10 秒"),
                            Component.text("区域内的敌人被无人机无视距离优先攻击"),
                            Component.text("期间无人机攻击速度提升 50%"),
                            Component.text("期间无人机每次攻击额外造成 2 点灵魂伤害"),
                            Component.text("区域内所有敌人被附加「猎杀目标」"),
                            Component.text("冷却 12 秒 · 消耗 10 能量")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.GOLD_INGOT);
            requires(AlbertDroneSystem.class).requires(AlbertLockOnPassive.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class)
                    .requires(AlbertFloatingTextComponent.class).requires(FactionComponent.class);
        }

        @Override
        public AlbertHuntOrderSkill create(String id, ComponentServicesPort services) {
            return new AlbertHuntOrderSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        drones = svc().components().get(AlbertDroneSystem.class);
        lockOn = svc().components().get(AlbertLockOnPassive.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        floatingText = svc().components().get(AlbertFloatingTextComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || drones == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (isCoolingDown()) {
            return;
        }
        if (energy != null && !energy.tryConsume(ENERGY_COST)) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        Location from = owner.getEyeLocation();
        Location center = aimPoint(owner, from);
        if (center == null) {
            return;
        }

        //① 集火窗口（攻速 +50% / 额外 2 灵魂，由无人机系统统一读）
        drones.openFocusWindow(center, AlbertDroneSystem.FOCUS_WINDOW_TICKS);
        //② 区域内敌人附加「猎杀目标」
        int marked = markAreaEnemies(owner, center);

        //③ 观感
        AlbertVfx.signalAlbertHuntOrder(world, from, center);
        AlbertSound.fireworkLaunchAlbertHuntOrderCastSound(world, from);
        AlbertSound.witherShootAlbertHuntOrderImpactSound(world, center);

        if (floatingText != null) {
            floatingText.saySkill(owner, AlbertFloatingTextComponent.MOMENT_SKILL_2);
        }

        startCooldown();
    }

    /** 给区域内所有敌对玩家打上「猎杀目标」。 */
    private int markAreaEnemies(Player owner, Location center) {
        if (lockOn == null || center.getWorld() == null) {
            return 0;
        }
        int count = 0;
        for (Player candidate : center.getNearbyPlayers(AlbertDroneSystem.FOCUS_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            lockOn.mark(candidate);
            AlbertVfx.markAlbertHuntTarget(candidate.getWorld(),
                    candidate.getLocation().clone().add(0d, 2.35d, 0d));
            count++;
        }
        return count;
    }

    /**
     * **落点**：优先"视线里的敌人"，其次"墙上的命中点"，最后"空中 {@value #MAX_AIM_RANGE} 格处"
     * （= 射程耗尽处自动爆开）。
     */
    private Location aimPoint(Player owner, Location eye) {
        World world = owner.getWorld();
        if (world == null) {
            return null;
        }
        Vector direction = eye.getDirection().normalize();

        double limit = MAX_AIM_RANGE;
        RayTraceResult blockHit = world.rayTraceBlocks(eye, direction, MAX_AIM_RANGE,
                FluidCollisionMode.NEVER, true);
        if (blockHit != null && blockHit.getHitPosition() != null) {
            limit = Math.min(limit, blockHit.getHitPosition().distance(eye.toVector()));
        }

        Player best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Player candidate : eye.getNearbyPlayers(MAX_AIM_RANGE)) {
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
            if (delta.clone().subtract(direction.clone().multiply(along)).length() > AIM_HIT_RADIUS) {
                continue;
            }
            if (along < bestAlong) {
                bestAlong = along;
                best = candidate;
            }
        }
        if (best != null) {
            return best.getLocation().clone();
        }
        if (blockHit != null && blockHit.getHitPosition() != null) {
            return blockHit.getHitPosition().toLocation(world);
        }
        return eye.clone().add(direction.clone().multiply(MAX_AIM_RANGE));
    }

    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /** 集火窗口生效期间，技能物品常亮（"还在集火"一眼可见）。 */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (drones != null && drones.focusActive()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
