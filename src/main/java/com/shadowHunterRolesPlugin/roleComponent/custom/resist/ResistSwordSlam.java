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

/**
 * **普攻附带的"巨剑砸落"动画**（2026-10-18 需求 9）。
 *
 * <p>一次只生成 1 把放大的铁剑：从目标头顶 4.5 格落到目标身上（4 刻），停留 2 刻后消散。
 * 展示实体无碰撞、无伤害 —— 纯观感，真正的伤害在主武器组件里结算。
 * <p>★ 与 {@link ResistSwordModel} 同规：世界实体不在框架资源表，主武器 {@code stop()} 必须
 * {@link #remove()}。
 */
public final class ResistSwordSlam {

    /**
     * **剑尖朝下**所需的左旋：铁剑 FIXED 模型默认指向 +45°（右上），
     * 顺时针转 135° ⇒ 45°-135° = -90°（正下方）。★ 不是 +135°（那会变成水平剑）。
     */
    private static final Quaternionf BLADE_DOWN = new Quaternionf(
            new AxisAngle4f((float) Math.toRadians(-135d), 0f, 0f, 1f));

    private final ItemDisplay sword;
    private final World world;
    private final Location base;
    private final float yaw;
    private final int fallTicks;
    private final int lingerTicks;
    private final double scale;

    private int age;
    private boolean removed;

    private ResistSwordSlam(World world, ItemDisplay sword, Location base, float yaw,
                            int fallTicks, int lingerTicks, double scale) {
        this.world = world;
        this.sword = sword;
        this.base = base;
        this.yaw = yaw;
        this.fallTicks = Math.max(1, fallTicks);
        this.lingerTicks = Math.max(0, lingerTicks);
        this.scale = scale;
    }

    /** 在目标位置生成一把落下的巨剑。 */
    public static ResistSwordSlam spawn(World world, Location target, double scale) {
        if (world == null || target == null) {
            return null;
        }
        Location base = target.clone();      //剑尖落点 = 目标脚下
        Location spawn = base.clone().add(0d, 7.0d, 0d);
        spawn.setYaw(0f);                    //基准 yaw 归零：竖直剑不受玩家朝向偏移
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
        float yaw = target.getYaw() + 30f;
        display.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), BLADE_DOWN,
                new Vector3f((float) scale), new Quaternionf()));
        return new ResistSwordSlam(world, display, base, yaw, 4, 2, scale);
    }

    /** 推进一帧；返回 false = 已消散。 */
    public boolean tick() {
        if (removed) {
            return false;
        }
        age++;
        if (age > fallTicks + lingerTicks) {
            remove();
            return false;
        }
        double y;
        if (age <= fallTicks) {
            double t = age / (double) fallTicks;
            y = 7.0d - 4.0d * t;    //7.0 → 3.0：剑身中心在 3 格时，剑尖正好触地
        } else {
            y = 3.0d;
        }
        if (sword != null && sword.isValid()) {
            sword.teleport(base.clone().add(0d, y, 0d));
            if (age == fallTicks) {
                //落地那一拍轻微放大 + 地面冲击（冲击感）
                sword.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), BLADE_DOWN,
                        new Vector3f((float) (scale * 1.12d)), new Quaternionf()));
                if (world != null) {
                    ResistVfx.slamImpact(world, base);
                }
            }
        }
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
}
