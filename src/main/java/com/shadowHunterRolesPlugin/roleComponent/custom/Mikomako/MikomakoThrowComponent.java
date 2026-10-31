package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「决爵」的**离手形态**（投掷物 / 插在敌人身上 / 插在地面 / 自行飞回）。
 *
 * <h2>需求逐条</h2>
 * <ul>
 *   <li>速度 <b>50 格/秒</b>（= 2.5 格/刻），先直线飞 <b>20 格</b>，之后逐步下落；</li>
 *   <li>命中敌人：插在敌人身上**跟随移动**，造成 <b>15 物理 + 8~12 特殊值</b>；</li>
 *   <li>充能状态命中：**消耗全部层数**，每层追加 <b>3 点真实伤害</b>，白色闪电状粒子劈向地面；</li>
 *   <li>命中地面：只出冲击特效并留存；</li>
 *   <li>靠近即可拾回；从敌人身上取下额外造成 <b>10 物理</b>；</li>
 *   <li>离手 <b>10 秒</b>无人拾取 ⇒ 以同样速度自行飞回；</li>
 *   <li>离手期间主武器不可用（由武器组件读 {@link #isOut()} 关闸）。</li>
 * </ul>
 *
 * <h2>★★ 口径申报：为什么逐刻推进而不挂 Listener</h2>
 * 投射物是 `ItemDisplay`（世界实体、不在框架资源表里）⇒ 生命周期必须由本组件 `update()` 逐刻推进，
 * `stop()` 兜底 {@code remove()}。不挂 Listener、不开调度任务，因此不会出现"角色被清掉后投射物还在飞"。
 *
 * <p>命中判定用"下一帧位置 1.2 格内的敌对玩家"，逐刻位移 2.5 格 ⇒ 单刻最大跨 2.5 格，
 * 1.2 格判定圈在极端情况下可能漏掉擦边，但正面命中不会穿过（判定中心在下一帧位置，不是线段中点）。
 */
public class MikomakoThrowComponent extends RoleComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_throw";

    /** 飞行速度（格/刻）= 50 格/秒。 */
    public static final double SPEED_PER_TICK = 50d / 20d;

    /** 直线段长度（格），之后开始下落。 */
    public static final double STRAIGHT_DISTANCE = 20d;

    /** 之后的每刻下坠加速度（格/刻²）。 */
    public static final double GRAVITY_PER_TICK = 0.055d;

    /** 离手后多久开始自行飞回（刻）= 10 秒（插地/插墙的常规情况）。 */
    public static final int RETURN_DELAY_TICKS = 200;

    /** 离鹤子超过这个距离（格）就自行返回（需求 1：50 格）。 */
    public static final double RETURN_DISTANCE = 50d;

    /** 拾取距离（格）—— 2026-10-21 需求 1：2.5 格。 */
    public static final double PICKUP_RADIUS = 2.5d;

    /** 命中判定半径（格）。 */
    public static final double HIT_RADIUS = 1.2d;

    /** 扫掠判定的采样步长（格）—— 必须显著小于最薄的墙（1 格）。 */
    public static final double SAMPLE_STEP = 0.2d;

    /** 命中敌人的物理伤害。 */
    public static final double HIT_PHYSICAL = 15d;

    /** 命中敌人的特殊值伤害区间（含两端）。 */
    public static final int HIT_SANTE_MIN = 8;
    public static final int HIT_SANTE_MAX = 12;

    /** 每层充能的追加真实伤害。 */
    public static final double CHARGE_TRUE_PER_STACK = 3d;

    /** 从敌人身上取下武器的额外物理伤害。 */
    public static final double PICKUP_FROM_ENEMY_PHYSICAL = 10d;

    private enum Mode {
        /** 未离手。 */
        NONE,
        /** 飞行中。 */
        FLYING,
        /** 插在地面。 */
        GROUND,
        /** 插在敌人身上。 */
        ENEMY,
        /** 自行飞回。 */
        RETURNING
    }

    private VitalsComponent vitals;
    private FactionComponent faction;
    private SanTEComponent sante;
    private MikomakoChargeComponent charge;
    private MikomakoSanDrainComponent sanDrain;
    private HotbarRenderComponent render;
    private MikomakoFloatingTextComponent voice;

    private Mode mode = Mode.NONE;
    private ItemDisplay display;
    private Vector velocity = new Vector(0, 0, 0);
    private double traveled;
    private int age;
    private int idleTicks;
    private UUID stuckTarget;
    private int phase;

    /** 45° 模型扶正角度：负号把"右上 45°"转到 +X。 */
    private static final float MODEL_FLATTEN_DEGREES = -45f;

    /** 锄头的头是否在扶正后的 +X 端（实测反了就改 false，模型会翻个个儿）。 */
    private static final boolean HEAD_AT_POSITIVE_X = true;

    /** 「竖着翻」的出现概率（需求：90% 竖着转 / 10% 沿长轴钻）。 */
    public static final double VERTICAL_TUMBLE_CHANCE = 0.9d;

    /** 本次投掷的旋法：true = 竖着翻（默认 90%），false = 沿长轴自旋（10%，上一版的样子）。 */
    private boolean verticalTumble = true;

    /** 扔出瞬间的**视角方向**（模型沿它对齐、并绕它自旋 —— 见 {@link #applyTransform(double)}）。 */
    private Vector flightDir = new Vector(0, 0, 1);

    public MikomakoThrowComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services);
    }

    /** 装配描述符。 */
    public static final class Specification extends RoleComponent.Specification<MikomakoThrowComponent> {

        public Specification() {
            super("MikomakoThrow");
            requires(VitalsComponent.class).requires(FactionComponent.class).requires(SanTEComponent.class)
                    .requires(MikomakoChargeComponent.class)
                    .requires(MikomakoSanDrainComponent.class);
            requiresOptional(HotbarRenderComponent.class);
            requiresOptional(MikomakoFloatingTextComponent.class);
        }

        @Override
        public MikomakoThrowComponent create(String id, ComponentServicesPort services) {
            return new MikomakoThrowComponent(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        faction = getComponent(FactionComponent.class);
        sante = getComponent(SanTEComponent.class);
        charge = getComponent(MikomakoChargeComponent.class);
        sanDrain = getComponent(MikomakoSanDrainComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        voice = getComponent(MikomakoFloatingTextComponent.class);
    }

    /** 是否离手（主武器关闸读这一条）。 */
    public boolean isOut() {
        return mode != Mode.NONE;
    }

    /** 是否插在敌人身上（诊断读口）。 */
    public boolean isStuckOnEnemy() {
        return mode == Mode.ENEMY;
    }

    /** 当前形态（诊断读口）。 */
    public String modeName() {
        return mode.name();
    }

    /**
     * 丢出武器。
     *
     * @return 是否真的丢出（已经离手 / 玩家无效 ⇒ false）
     */
    public boolean throwWeapon(Player player) {
        if (mode != Mode.NONE || player == null || !player.isOnline()) {
            return false;
        }
        World world = player.getWorld();
        // ★ 视角检测：以扔出瞬间的**视线方向**为模型长轴，整段飞行都沿它对齐 + 绕它自旋
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Location spawn = player.getEyeLocation().add(dir.clone().multiply(1.2d));
        // 实体自身朝向归零：模型姿态全部由 Transformation 表达（否则 yaw 会二次旋转，永远"不正"）
        spawn.setYaw(0f);
        spawn.setPitch(0f);

        display = (ItemDisplay) world.spawnEntity(spawn, EntityType.ITEM_DISPLAY);
        ItemStack model = new ItemStack(Material.DIAMOND_HOE);
        if (charge != null && charge.hasCharge()) {
            ItemMeta meta = model.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                model.setItemMeta(meta);
            }
        }
        display.setItemStack(model);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setTeleportDuration(1);
        display.setViewRange(2.0f);
        applyTransform(0d);

        velocity = dir.clone().multiply(SPEED_PER_TICK);
        flightDir = dir.clone();
        verticalTumble = ThreadLocalRandom.current().nextDouble() < VERTICAL_TUMBLE_CHANCE;
        traveled = 0d;
        age = 0;
        idleTicks = 0;
        stuckTarget = null;
        mode = Mode.FLYING;
        requestRepaint();
        return true;
    }

    @Override
    public void update() {
        if (mode == Mode.NONE) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            despawn();
            return;
        }
        if (display == null || !display.isValid()) {
            reset();
            return;
        }

        age++;
        phase++;

        // 需求 1：离鹤子超过 50 格 ⇒ 立即自行返回（任何形态）
        if (mode != Mode.RETURNING && display.getWorld().equals(owner.getWorld())
                && display.getLocation().distanceSquared(owner.getLocation()) > RETURN_DISTANCE * RETURN_DISTANCE) {
            mode = Mode.RETURNING;
            stickyClear();
        }

        switch (mode) {
            case FLYING -> tickFlying(owner);
            case GROUND -> tickGrounded(owner);
            case ENEMY -> tickOnEnemy(owner);
            case RETURNING -> tickReturning(owner);
            default -> {
            }
        }
    }

    // ───────── 飞行 ─────────

    /**
     * 逐刻飞行 —— **扫掠判定**。
     *
     * <h2>★★ 2026-10-21 修复：为什么会穿墙</h2>
     * 一帧位移 2.5 格（50 格/秒），旧写法只在**终点**判一次方块 ⇒ 一堵 1 格厚的墙
     * 可能整块落在 {@code from} 与 {@code next} 之间，两头都不是"墙"，于是直接穿过去。
     * 现在把这一帧的位移切成 {@value #SAMPLE_STEP} 格的小步，**沿途逐点判**：
     * 先判敌人（走到该点时 1.2 格内有没有敌对玩家），再判方块（碰撞箱非空）。
     * 先撞到谁就以谁为准；撞墙时停在**最后一个空点**，不会弹到墙顶。
     */
    private void tickFlying(Player owner) {
        Location from = display.getLocation();
        if (traveled >= STRAIGHT_DISTANCE) {
            velocity.setY(velocity.getY() - GRAVITY_PER_TICK);
        }
        Location next = from.clone().add(velocity);
        traveled += velocity.length();

        Vector delta = next.toVector().subtract(from.toVector());
        double length = delta.length();

        // 候选敌人只查一次（半径覆盖整段位移），逐点只做距离比较，避免每点一次世界查询
        List<Player> candidates = new ArrayList<>();
        for (Player candidate : from.getNearbyPlayers(length + HIT_RADIUS + 1.0d)) {
            if (candidate == null || candidate.equals(owner) || !faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            candidates.add(candidate);
        }

        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));

        // ① 先做一次**精确射线**：拿到这一帧会撞到的第一个"有碰撞箱"的方块
        //    （射线按方块的真实碰撞箱求交 ⇒ 玻璃板 / 栅栏这种薄碰撞箱也不会漏）
        RayTraceResult blockHit = null;
        if (length > 1e-6) {
            blockHit = from.getWorld().rayTraceBlocks(from, delta.clone().normalize(), length,
                    FluidCollisionMode.NEVER, true);
        }
        double blockDistance = blockHit == null ? Double.MAX_VALUE
                : from.toVector().distance(blockHit.getHitPosition());

        // ② 沿途逐点判敌人（只判到撞墙之前；先撞到谁以谁为准）
        for (int i = 1; i <= steps; i++) {
            double stepDistance = length * i / (double) steps;
            if (stepDistance > blockDistance) {
                break;
            }
            Location point = from.clone().add(delta.clone().multiply(i / (double) steps));
            Player victim = nearestCandidate(point, candidates);
            if (victim != null) {
                stickOnEnemy(owner, victim, point);
                return;
            }
        }

        // ③ 撞墙：停在射线命中点（沿来路退 0.05 格，避免半个模型嵌进方块）
        if (blockHit != null) {
            Location hit = blockHit.getHitPosition().toLocation(from.getWorld())
                    .add(delta.clone().normalize().multiply(-0.05d));
            stickOnGround(hit, false);
            return;
        }

        // 出界兜底
        if (next.getY() < next.getWorld().getMinHeight() - 5) {
            despawn();
            return;
        }

        display.teleport(next);
        applyTransform(age * (verticalTumble ? 22d : 14d));
        MikomakoVfx.throwTrail(next, phase);
    }

    /** 该点 1.2 格内最近的敌对玩家（取身体中段，命中判定与插人位置一致）。 */
    private Player nearestCandidate(Location point, List<Player> candidates) {
        Player best = null;
        double bestDistance = HIT_RADIUS * HIT_RADIUS;
        for (Player candidate : candidates) {
            if (candidate == null || !candidate.isOnline() || candidate.isDead()) {
                continue;
            }
            double distance = candidate.getLocation().add(0d, 0.9d, 0d).distanceSquared(point);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    // ───────── 地面 ─────────

    private void tickGrounded(Player owner) {
        idleTicks++;
        Location at = display.getLocation();
        if (idleTicks % 4 == 0) {
            MikomakoVfx.stuckPulse(at, phase);
        }
        if (idleTicks >= RETURN_DELAY_TICKS) {
            mode = Mode.RETURNING;
            idleTicks = 0;
            return;
        }
        tryPickup(owner, at);
    }

    // ───────── 敌人身上 ─────────

    private void tickOnEnemy(Player owner) {
        Player target = stuckTarget == null ? null : Bukkit.getPlayer(stuckTarget);
        if (target == null || !target.isOnline() || target.isDead() || target.getHealth() <= 0d
                || !target.getWorld().equals(owner.getWorld())) {
            // 2026-10-21 需求：敌人死亡 ⇒ **直接返回**，不落地、不等待、**绝不继续跟随**
            mode = Mode.RETURNING;
            stickyClear();
            return;
        }
        idleTicks++;
        // 需求 4：留存位置放在**肚子**高度（不是胸口/头顶）
        Location at = target.getLocation().add(0d, 0.9d, 0d);
        display.teleport(at);
        // 需求 1：命中后**停止转动**（保持插住的姿态）
        applyTransform(0d);
        if (phase % 3 == 0) {
            MikomakoVfx.stuckPulse(at, phase);
        }
        if (idleTicks >= RETURN_DELAY_TICKS) {
            mode = Mode.RETURNING;
            idleTicks = 0;
            return;
        }
        tryPickup(owner, at);
    }

    // ───────── 飞回 ─────────

    private void tickReturning(Player owner) {
        Location at = display.getLocation();
        Location target = owner.getEyeLocation();
        if (!at.getWorld().equals(target.getWorld())) {
            display.teleport(target);
            pickup(owner, false);
            return;
        }
        Vector delta = target.toVector().subtract(at.toVector());
        double distance = delta.length();
        if (distance <= PICKUP_RADIUS) {
            pickup(owner, false);
            return;
        }
        Location next = at.clone().add(delta.normalize().multiply(Math.min(SPEED_PER_TICK, distance)));
        display.teleport(next);
        applyTransform(phase * 20d);
        MikomakoVfx.throwTrail(next, phase);
    }

    // ───────── 落地 / 插人 / 拾取 ─────────

    private void stickOnEnemy(Player owner, Player victim, Location at) {
        vitals.physicalDamage(victim, owner, HIT_PHYSICAL);
        int special = HIT_SANTE_MIN + (int) (Math.random() * (HIT_SANTE_MAX - HIT_SANTE_MIN + 1));
        sante.decreaseSanTE(victim.getUniqueId(), special);
        if (sanDrain != null) {
            sanDrain.onHit();
        }

        int used = charge == null ? 0 : charge.consume(Integer.MAX_VALUE);
        if (used > 0) {
            vitals.trueDamage(victim, owner, CHARGE_TRUE_PER_STACK * used);
        }

        MikomakoVfx.impact(at, used > 0);
        MikomakoSound.impact(at, used > 0);
        MikomakoVfx.hitMarker(victim, phase);

        // 2026-10-21 需求：这一击就把敌人打死了 ⇒ 立刻进入返回，不跟尸体
        if (victim.isDead() || victim.getHealth() <= 0d) {
            mode = Mode.RETURNING;
            stickyClear();
            requestRepaint();
            return;
        }

        stuckTarget = victim.getUniqueId();
        mode = Mode.ENEMY;
        idleTicks = 0;
        // 需求 4：插在**肚子**高度
        display.teleport(victim.getLocation().add(0d, 0.9d, 0d));
        applyTransform(0d);
        requestRepaint();
    }

    /**
     * 变成"插住"状态。
     *
     * @param at         落点（射线命中点，已经贴着方块表面）
     * @param wasOnEnemy 是否从敌人身上掉下来的（决定要不要补一圈余韵粒子）
     */
    private void stickOnGround(Location at, boolean wasOnEnemy) {
        Location ground = at.clone();
        display.teleport(ground);
        applyTransform(0d);
        mode = Mode.GROUND;
        idleTicks = 0;
        stuckTarget = null;
        MikomakoVfx.impact(ground, false);
        MikomakoSound.impact(ground, false);
        if (wasOnEnemy) {
            MikomakoVfx.stuckPulse(ground, phase);
        }
    }

    /** 清掉"插在谁身上"的追踪状态（转入飞行/返回时用）。 */
    private void stickyClear() {
        stuckTarget = null;
        idleTicks = 0;
    }

    private void tryPickup(Player owner, Location at) {
        if (!at.getWorld().equals(owner.getWorld())) {
            return;
        }
        if (at.distanceSquared(owner.getLocation().add(0d, 1.0d, 0d)) > PICKUP_RADIUS * PICKUP_RADIUS) {
            return;
        }
        boolean fromEnemy = mode == Mode.ENEMY;
        Player target = fromEnemy && stuckTarget != null ? Bukkit.getPlayer(stuckTarget) : null;
        if (fromEnemy && target != null && target.isOnline() && !target.isDead()) {
            vitals.physicalDamage(target, owner, PICKUP_FROM_ENEMY_PHYSICAL);
            MikomakoVfx.hitMarker(target, phase);
        }
        pickup(owner, fromEnemy);
    }

    private void pickup(Player owner, boolean fromEnemy) {
        MikomakoVfx.pickup(owner.getLocation().add(0d, 0.6d, 0d));
        MikomakoSound.pickup(owner);
        if (voice != null) {
            voice.sayMoment(owner, MikomakoFloatingTextComponent.MOMENT_PICKUP);
        }
        despawn();
    }

    /** 移除展示实体并复位（幂等）。 */
    private void despawn() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        reset();
    }

    private void reset() {
        display = null;
        mode = Mode.NONE;
        velocity = new Vector(0, 0, 0);
        traveled = 0d;
        idleTicks = 0;
        stuckTarget = null;
        requestRepaint();
    }

    @Override
    public void stop() {
        despawn();
    }

    /**
     * 模型姿态：两种旋法，**90% 竖着翻 / 10% 沿长轴钻**（需求 2026-10-21）。
     *
     * <h2>A. 竖着翻（默认）</h2>
     * <ol>
     *   <li>{@code flatten}：FIXED 模型 45° 斜 ⇒ 绕 Z {@value #MODEL_FLATTEN_DEGREES}° 扶到 +X；</li>
     *   <li>{@code upright}：再绕 Z 抬 90° ⇒ 长轴竖直（像一柄立起来的锄头）；</li>
     *   <li>{@code spin}：绕**水平、且垂直于飞行方向**的轴翻滚（{@code up × dir}）⇒ 端对端地翻跟头，
     *       玩家看到的就是"竖着转"。</li>
     * </ol>
     *
     * <h2>B. 沿长轴钻（10%，上一版的样子）</h2>
     * {@code align × spin(X) × flatten}：头朝飞行方向，绕"头—柄"长轴自旋。
     *
     * <p>★ 实体自身 yaw 固定为 0：姿态全部由本变换表达，否则 yaw 会再叠一层旋转 ⇒ "怎么调都不正"。
     */
    private void applyTransform(double spinDegrees) {
        if (display == null || !display.isValid()) {
            return;
        }
        Vector3f target = new Vector3f((float) flightDir.getX(), (float) flightDir.getY(),
                (float) flightDir.getZ());
        if (target.lengthSquared() < 1e-6f) {
            target.set(0f, 0f, 1f);
        }
        target.normalize();

        Quaternionf flatten = new Quaternionf(
                new AxisAngle4f((float) Math.toRadians(MODEL_FLATTEN_DEGREES), 0f, 0f, 1f));

        Quaternionf left;
        if (verticalTumble) {
            Quaternionf upright = new Quaternionf(
                    new AxisAngle4f((float) Math.toRadians(90d), 0f, 0f, 1f));
            Quaternionf base = upright.mul(flatten);
            Vector3f axis = new Vector3f(target.z, 0f, -target.x);
            if (axis.lengthSquared() < 1e-6f) {
                axis.set(1f, 0f, 0f);
            }
            axis.normalize();
            Quaternionf spin = new Quaternionf(
                    new AxisAngle4f((float) Math.toRadians(spinDegrees), axis.x, axis.y, axis.z));
            left = spin.mul(base);
        } else {
            Quaternionf spin = new Quaternionf(
                    new AxisAngle4f((float) Math.toRadians(spinDegrees), 1f, 0f, 0f));
            Vector3f headAxis = HEAD_AT_POSITIVE_X ? new Vector3f(1f, 0f, 0f) : new Vector3f(-1f, 0f, 0f);
            Quaternionf align = new Quaternionf().rotateTo(headAxis, target);
            left = align.mul(spin).mul(flatten);
        }

        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f), left, new Vector3f(1.15f), new Quaternionf()));
    }

    private void requestRepaint() {
        if (render != null) {
            render.markDirty();
        }
    }
}
