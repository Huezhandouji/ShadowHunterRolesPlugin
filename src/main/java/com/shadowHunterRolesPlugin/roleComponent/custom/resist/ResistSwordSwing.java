package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * **二技能「命运的征服」的巨型铁剑挥砍动画**（2026-10-18 23:02 重写旋转口径）。
 *
 * <h2>★★ 剑模型到底朝哪（按实测口径，这是唯一正确的基准）</h2>
 * 铁剑在 {@code ItemDisplayTransform.FIXED} 姿态下，模型是 <b>45° 斜向</b>的（剑尖指右上方）：
 * <ul>
 *   <li><b>要"正"（竖直向上）</b>：在这个 45° 上<b>再转 45°</b>；</li>
 *   <li><b>要"水平"（平躺）</b>：从"正"再<b>横向转 90°</b> ⇒ 相对原始斜姿一共 <b>-45°</b>，
 *       旋转轴是<b>垂直于屏幕的轴</b>（Z 轴，即"画面里横向转"）。</li>
 * </ul>
 * ★★ 旧实现角度其实写对了（-45°），错在<b>把摆平放在 rightRotation</b>：两侧各放一半旋转时，
 * "先摆平再转向"的先后无法保证 ⇒ 剑被先绕 Y 转、再绕 Z 摆平，Y 分量没消掉，看着就还是斜的。
 *
 * <h2>旋转的书写方式（消除顺序歧义）</h2>
 * {@code Transformation} 的顺序是 平移 → leftRotation → 缩放 → rightRotation，
 * 两侧各放一半旋转容易出现"谁先谁后"的误判 ⇒ 本类**把所有旋转合成到 leftRotation**、
 * rightRotation 恒为 identity，用四元数右乘的先后顺序明确表达"先摆平、再转刀路、最后抬落"。
 *
 * <h2>三刀的动作</h2>
 * <ul>
 *   <li><b>第 1 / 2 刀</b>：<b>水平轴向平挥</b>。剑身躺在水平面上、剑柄贴身、剑尖沿半径朝外，
 *       绕玩家垂直轴从一侧扫到另一侧（右→左 / 左→右）。</li>
 *   <li><b>第 3 刀</b>：<b>竖直抡砸</b>。以摆平后的剑为起点，绕垂直于刀路的水平轴
 *       从 +{@value #SLAM_FROM_DEGREES}° 抡到 -{@value #SLAM_TO_DEGREES}°，支点从头顶前移到前方目标点，
 *       剑尖最后插进地面。</li>
 * </ul>
 */
public final class ResistSwordSwing {

    /**
     * **把剑摆平到水平**所需的旋转角（绕 <b>Z 轴</b>，即"画面里横向转"）。
     *
     * <p>推导（按实测口径）：原始 <b>45° 斜</b> → 补 +45° 得到"正"（竖直）→ 再横向转 90° 得到"水平"，
     * 合计相对原始姿态 <b>-45°</b>。摆平后剑尖指向局部 <b>+X</b>。
     */
    public static final double BLADE_FLAT_DEGREES = -45d;

    /** 摆平后的剑尖方向 = 局部 +X。 */
    private static final Quaternionf BLADE_FLAT = new Quaternionf(
            new AxisAngle4f((float) Math.toRadians(BLADE_FLAT_DEGREES), 0f, 0f, 1f));

    /**
     * **前两刀的剑身自转**（绕剑自己的中轴线 = 摆平后的局部 +X 轴）。
     *
     * <p>2026-10-19 需求：前两刀巨剑要以剑的中轴线为轴旋转 <b>90°</b> ——
     * 摆平后剑指 +X，所以绕 +X 转半圈 = 绕剑身长轴翻面（剑刃朝上/朝下互换，剑身指向不变）。
     * <p>★ 只作用于第 1 / 2 刀；第三刀（竖直抡砸）不带这份自转。
     */
    public static final double BLADE_TWIST_DEGREES = 90d;

    private static final Quaternionf BLADE_TWIST = new Quaternionf(
            new AxisAngle4f((float) Math.toRadians(BLADE_TWIST_DEGREES), 1f, 0f, 0f));

    /** 一次挥砍的刻数。 */
    public static final int DURATION_TICKS = 5;

    /** 平挥时剑中心离身体的半径（剑长 6 ⇒ 剑柄在身体附近、剑尖在外）。 */
    public static final double SWING_RADIUS = 3.0d;

    /** 平挥的扫击角度（相对正前方，左右各 105°）。 */
    public static final double SWING_ARC_DEGREES = 105d;

    /** 第三刀起始仰角（举过头顶）。 */
    public static final double SLAM_FROM_DEGREES = 85d;

    /** 第三刀终止仰角（砸进地面）。 */
    public static final double SLAM_TO_DEGREES = -88d;

    /** 第三刀的支点到剑中心的距离（剑长 6 ⇒ 剑柄在支点内侧）。 */
    public static final double SLAM_RADIUS = 1.6d;

    private final ItemDisplay sword;
    private final World world;
    private final Location eye;
    private final Vector forward;
    private final Vector side;
    private final int stage;

    private int age;
    private boolean removed;

    private ResistSwordSwing(World world, ItemDisplay sword, Location eye,
                             Vector forward, Vector side, int stage) {
        this.world = world;
        this.sword = sword;
        this.eye = eye;
        this.forward = forward;
        this.side = side;
        this.stage = stage;
    }

    /** 以眼睛为圆心生成一把挥砍巨剑。 */
    public static ResistSwordSwing spawn(World world, Location eye, Vector dir, int stage) {
        if (world == null || eye == null || dir == null) {
            return null;
        }
        Vector flat = new Vector(dir.getX(), 0d, dir.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        flat.normalize();
        Vector side = new Vector(-flat.getZ(), 0d, flat.getX());
        //★ 展示实体基准 yaw / pitch 归零：模型固定，旋转全部交给 Transformation
        Location spawn = eye.clone();
        spawn.setYaw(0f);
        spawn.setPitch(0f);
        ItemDisplay display = (ItemDisplay) world.spawnEntity(spawn, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.IRON_SWORD));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
        display.setTeleportDuration(1);
        display.setViewRange(2.0f);
        ResistSwordSwing swing = new ResistSwordSwing(world, display, eye.clone(), flat, side, stage);
        swing.apply(0d);
        return swing;
    }

    /** 推进一帧；返回 false = 已回收。 */
    public boolean tick() {
        if (removed) {
            return false;
        }
        age++;
        if (age > DURATION_TICKS) {
            remove();
            return false;
        }
        apply(age / (double) DURATION_TICKS);
        return true;
    }

    /** 立刻回收（幂等）。 */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        if (sword != null && sword.isValid()) {
            sword.remove();
        }
    }

    /** 是否已回收。 */
    public boolean isRemoved() {
        return removed;
    }

    private void apply(double t) {
        if (sword == null || !sword.isValid()) {
            return;
        }
        if (stage >= 3) {
            applySlam(clamp01(t));
        } else {
            applySweep(clamp01(t));
        }
    }

    /**
     * 第 1 / 2 刀：**水平轴向平挥**。
     *
     * <p>剑身躺在水平面上（{@link #BLADE_FLAT} 绕 X 轴摆平），剑尖沿半径朝外，绕玩家垂直轴扫过。
     */
    private void applySweep(double t) {
        double angle = Math.toRadians(sweepAngleDegrees(stage, t));
        //半径方向（当前刀路）：剑中心放在这个方向上 SWING_RADIUS 处
        Vector bladeDir = forward.clone().multiply(Math.cos(angle))
                .add(side.clone().multiply(Math.sin(angle)));
        Location pos = eye.clone()
                .add(bladeDir.clone().multiply(SWING_RADIUS))
                .add(0d, -0.45d, 0d);       //胸/腰高度
        pos.setYaw(0f);
        pos.setPitch(0f);
        sword.teleport(pos);

        //先摆平 → 再绕剑身中轴线自转 90°（让刃口朝扫击方向）→ 最后绕世界 Y 转到当前刀路
        float rotY = (float) Math.atan2(-bladeDir.getZ(), bladeDir.getX());
        Quaternionf left = new Quaternionf(new AxisAngle4f(rotY, 0f, 1f, 0f))
                .mul(BLADE_TWIST, new Quaternionf())
                .mul(BLADE_FLAT, new Quaternionf());
        sword.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f), left, new Vector3f(6.0f), new Quaternionf()));
    }

    /**
     * 第 3 刀：**竖直抡砸**。绕垂直于刀路的水平轴（{@link #side}）从头顶抡到地面，
     * 同时支点前移 ⇒ 剑身扫过一个大竖弧，剑尖最后插进地面。
     */
    private void applySlam(double t) {
        double pitch = Math.toRadians(slamPitchDegrees(t));
        Vector up = new Vector(0d, 1d, 0d);
        Vector bladeDir = forward.clone().multiply(Math.cos(pitch))
                .add(up.clone().multiply(Math.sin(pitch)));

        //支点：起点 = 头顶前方，终点 = 前方 4.2 格处上方 2.6 格
        Vector start = eye.toVector().add(forward.clone().multiply(1.2d)).add(new Vector(0d, 1.6d, 0d));
        Vector end = eye.toVector().add(forward.clone().multiply(4.2d)).add(new Vector(0d, 2.6d, 0d));
        Vector pivot = start.multiply(1d - t).add(end.multiply(t));
        Vector center = pivot.add(bladeDir.multiply(SLAM_RADIUS));

        Location pos = new Location(world, center.getX(), center.getY(), center.getZ());
        pos.setYaw(0f);
        pos.setPitch(0f);
        sword.teleport(pos);

        //先摆平 → 再绕 Y 转刀路 → 最后绕水平轴抬起/砸下
        float rotY = (float) Math.atan2(-forward.getZ(), forward.getX());
        Quaternionf yaw = new Quaternionf(new AxisAngle4f(rotY, 0f, 1f, 0f));
        Quaternionf pitchRot = new Quaternionf(new AxisAngle4f(
                (float) pitch, (float) side.getX(), 0f, (float) side.getZ()));
        Quaternionf left = pitchRot.mul(yaw, new Quaternionf()).mul(BLADE_FLAT, new Quaternionf());
        sword.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f), left, new Vector3f(6.0f), new Quaternionf()));
    }

    /**
     * 第 1 / 2 刀的扫击角（纯函数，度）：相对正前方，{@code +} 偏向 {@link #side}。
     *
     * <p>第 1 刀从 -{@code 105°} 扫到 +{@code 105°}（右 → 左），第 2 刀反向（左 → 右）。
     */
    public static double sweepAngleDegrees(int stage, double t) {
        double from = stage == 1 ? -SWING_ARC_DEGREES : SWING_ARC_DEGREES;
        double to = stage == 1 ? SWING_ARC_DEGREES : -SWING_ARC_DEGREES;
        return from + (to - from) * clamp01(t);
    }

    /** 第 3 刀的仰角（纯函数，度）：+ 举过头顶、- 砸向地面。 */
    public static double slamPitchDegrees(double t) {
        return SLAM_FROM_DEGREES + (SLAM_TO_DEGREES - SLAM_FROM_DEGREES) * clamp01(t);
    }

    /** 当前这一刀是否是竖直抡砸（离线测试 / 诊断读口）。 */
    public static boolean isSlamStage(int stage) {
        return stage >= 3;
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
