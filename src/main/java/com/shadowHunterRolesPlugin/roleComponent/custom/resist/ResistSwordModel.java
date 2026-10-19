package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * **四把巨型铁剑**（{@link ItemDisplay} 放大展示）。
 *
 * <p>为什么用展示实体：无碰撞箱、不参与伤害、不受重力，且可以逐刻改位置/缩放做出
 * "从地底拔出 → 悬停 → 落回地面消散"的动画；工程先例 = 苍鹭抓钩头的
 * {@link ItemDisplay}（同目录 {@code CangluMelodySelectionSkill}）。
 *
 * <p>生命周期由技能每刻调 {@link #tick()} 推进；技能 {@code stop()} / 玩家死亡时必须调
 * {@link #remove()} —— 展示实体是**世界实体**，不在框架资源表里，漏删会永久残留。
 */
public final class ResistSwordModel {

    /**
     * 剑竖直向上所需的左旋：铁剑 FIXED 模型默认指向 **+45°（右上方）**，
     * 逆时针再转 +45° ⇒ 90°（正上方）。★ 之前写成 -45° 会把它转成水平剑（已修）。
     */
    private static final Quaternionf UPRIGHT = new Quaternionf(
            new AxisAngle4f((float) Math.toRadians(45d), 0f, 0f, 1f));

    private final List<ItemDisplay> swords = new ArrayList<>();
    private final List<Location> bases = new ArrayList<>();
    private final List<Float> yaws = new ArrayList<>();

    private final int riseTicks;
    private final int holdTicks;
    private final int fallTicks;
    private final double scale;

    private int age;
    private boolean removed;

    private ResistSwordModel(int riseTicks, int holdTicks, int fallTicks, double scale) {
        this.riseTicks = Math.max(1, riseTicks);
        this.holdTicks = Math.max(0, holdTicks);
        this.fallTicks = Math.max(1, fallTicks);
        this.scale = Math.max(0.1d, scale);
    }

    /**
     * 在 {@code center} 周围生成 {@code count} 把巨剑（均分角度、略向外倾）。
     *
     * @param radius 围绕半径（格）
     * @param rise   破土上升刻数
     * @param hold   悬停刻数
     * @param fall   落回消散刻数
     */
    public static ResistSwordModel spawn(World world, Location center, int count,
                                         double radius, int rise, int hold, int fall, double scale) {
        ResistSwordModel model = new ResistSwordModel(rise, hold, fall, scale);
        if (world == null || center == null || count <= 0) {
            return model;
        }
        int n = Math.min(8, count);
        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(45d + i * (360d / n));
            Location base = center.clone().add(Math.cos(angle) * radius, 0d, Math.sin(angle) * radius);
            ItemDisplay display = (ItemDisplay) world.spawnEntity(base.clone().add(0d, -2.6d, 0d),
                    EntityType.ITEM_DISPLAY);
            display.setItemStack(new ItemStack(Material.IRON_SWORD));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setSilent(true);
            display.setInterpolationDuration(2);
            display.setInterpolationDelay(0);
            display.setTeleportDuration(1);
            display.setViewRange(2.0f);
            model.swords.add(display);
            model.bases.add(base);
            model.yaws.add((float) Math.toDegrees(angle) + 90f);
        }
        model.applyAll(-2.6d, model.scale);
        return model;
    }

    /**
     * 推进一帧。
     *
     * @return 是否仍然存活（{@code false} = 已消散并回收）
     */
    public boolean tick() {
        if (removed) {
            return false;
        }
        age++;
        double y;
        double s;
        if (age <= riseTicks) {
            double t = age / (double) riseTicks;
            y = -2.6d + 3.0d * t;
            s = scale;
        } else if (age <= riseTicks + holdTicks) {
            y = 0.4d;
            s = scale;
        } else {
            int f = age - riseTicks - holdTicks;
            if (f > fallTicks) {
                remove();
                return false;
            }
            double t = f / (double) fallTicks;
            y = 0.4d - 3.0d * t;
            s = scale * Math.max(0.02d, 1d - 0.95d * t);
        }
        if (age % 2 == 0 || age <= 2) {
            applyAll(y, s);
        }
        return true;
    }

    /** 立刻回收全部展示实体（幂等）。 */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (ItemDisplay display : swords) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        swords.clear();
        bases.clear();
        yaws.clear();
    }

    /** 是否已回收。 */
    public boolean isRemoved() {
        return removed;
    }

    /** 剑数（诊断读口 / 单测用）。 */
    public int size() {
        return swords.size();
    }

    /** 本模型从生成起经过的刻数（只读）。 */
    public int age() {
        return age;
    }

    private void applyAll(double y, double s) {
        for (int i = 0; i < swords.size(); i++) {
            ItemDisplay display = swords.get(i);
            if (display == null || !display.isValid()) {
                continue;
            }
            Location base = bases.get(i);
            display.teleport(base.clone().add(0d, y, 0d));
            Quaternionf right = new Quaternionf(
                    new AxisAngle4f((float) Math.toRadians(yaws.get(i)), 0f, 1f, 0f));
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), UPRIGHT, new Vector3f((float) s), right));
        }
    }
}
