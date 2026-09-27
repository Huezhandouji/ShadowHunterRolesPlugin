package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * 「罪棘」的**粒子特效工具箱**（纯几何绘制，不持有任何状态、不碰组件）。
 *
 * <p>抽出来的理由：被动 / 一技能 / 二技能 / 大招四处都要画「螺旋」「十字架」「尖牙」，
 * 形状算法只写一遍，各组件只决定"画在哪、多大、多快、用什么粒子"。
 *
 * <p><b>纪律</b>：本类**只画粒子**（外加调用方自己 spawn 实体），不做伤害、不改状态 ——
 * 特效与收益分开，出问题好定位。
 */
public final class SinThornVfx {

    private SinThornVfx() {
    }

    /** 罪棘主题色：暗血色（被动 / 尖牙）。 */
    public static final Particle.DustOptions THORN_RED = new Particle.DustOptions(Color.fromRGB(120, 20, 40), 1f);

    /** 尖牙前摇色：骨白。 */
    public static final Particle.DustOptions BONE_WHITE = new Particle.DustOptions(Color.fromRGB(226, 220, 200), 1f);

    /** 审判引导色：紫。 */
    public static final Particle.DustOptions JUDGMENT_PURPLE = new Particle.DustOptions(Color.fromRGB(150, 80, 220), 1f);

    /**
     * **旋转向上的螺旋**（被动的"生成前"特效）：绕 {@code center} 一圈、随高度上升的螺旋线。
     *
     * @param phase  相位（弧度）—— 每次多画一帧就 +0.5 左右，越转越明显
     * @param radius 螺旋半径
     * @param height 螺旋总高
     * @param points 螺旋上取样点数（越多越密）
     */
    public static void spawnSpiral(World world, Location center, double phase, double radius, double height,
                                   int points, Particle.DustOptions options) {
        if (world == null || center == null || points <= 0) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = phase + t * Math.PI * 2d;
            double y = t * height;
            Location at = center.clone().add(Math.cos(angle) * radius, 0.15 + y, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, options);
        }
    }

    /**
     * **单个十字架**：以 {@code center} 为中心、朝外（{@code (dx,dz)} 方向）立着的一个"+"。
     * <p>十字所在平面 = 竖直平面；一条臂沿世界 Y 轴，另一条臂沿水平切向 ⇒ 从外面看就是个十字。
     *
     * @param size     单臂长度（格）
     * @param armSteps 单臂取样点数（两侧共 2*armSteps+1 个粒子）
     */
    public static void spawnCross(World world, Location center, double dx, double dz, double size,
                                  int armSteps, Particle particle) {
        if (world == null || center == null || armSteps <= 0) {
            return;
        }
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-6) {
            return;
        }
        //水平切向（与朝外方向垂直）—— 十字的横臂就沿它
        double tx = -dz / len;
        double tz = dx / len;
        double step = size / armSteps;

        for (int i = -armSteps; i <= armSteps; i++) {
            double o = i * step;
            //竖臂（沿 Y）
            world.spawnParticle(particle, center.clone().add(0, o, 0), 1, 0, 0, 0, 0);
            //横臂（沿切向）
            world.spawnParticle(particle, center.clone().add(tx * o, 0, tz * o), 1, 0, 0, 0, 0);
        }
    }

    /**
     * **绕圈旋转的 N 个十字架**（二技能用）。
     *
     * @param radius 环绕半径
     * @param phase  相位（弧度）—— 每帧递增即形成旋转
     * @param count  十字架个数
     * @param size   单臂长度
     */
    public static void spawnOrbitingCrosses(World world, Location center, double radius, double phase,
                                            int count, double size, int armSteps, Particle particle) {
        if (world == null || center == null || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = phase + i * (Math.PI * 2d / count);
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            Location at = center.clone().add(dx * radius, 0, dz * radius);
            spawnCross(world, at, dx, dz, size, armSteps, particle);
        }
    }

    /**
     * **绕圈旋转的「单个」粒子**：每处只 spawn 一颗（{@code count=1} 且零散布），
     * 看起来就是一颗颗亮点在转。爆炸 / 白色电火花 / 任意无数据粒子都走它。
     *
     * @param ySpread 上下错落幅度（0 = 全在同一高度）
     */
    public static void spawnOrbitingPoints(World world, Location center, double radius, double phase,
                                           int count, double ySpread, Particle particle) {
        if (world == null || center == null || count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = phase + i * (Math.PI * 2d / count);
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double y = ((i % 3) - 1) * ySpread;
            Location at = center.clone().add(dx * radius, 1.0 + y, dz * radius);
            world.spawnParticle(particle, at, 1, 0, 0, 0, 0);
        }
    }

    /**
     * **绕圈旋转的单个爆炸粒子**（大招范围边缘用）。
     * <p>"单个"= 每次只 spawn 一颗（{@code count=1} 且零散布），所以看起来是一颗颗亮点在转。
     */
    public static void spawnOrbitingExplosions(World world, Location center, double radius, double phase,
                                               int count, double ySpread) {
        spawnOrbitingPoints(world, center, radius, phase, count, ySpread, Particle.EXPLOSION);
    }

    /**
     * **紫色引导柱**：一根<b>连续垂直 {@code height} 格</b>、并<b>绕目标旋转</b>的螺旋。
     * <p>做法 = 沿高度取样 {@code points} 个点，每点相位随高度递增 ⇒ 单帧看上去是一根竖直螺旋线；
     * 帧间 {@code phase} 递增 ⇒ 整根柱子绕着目标转。
     */
    public static void spawnPurpleHelix(World world, Location base, double phase, double height,
                                        double radius, int points) {
        if (world == null || base == null || points <= 0) {
            return;
        }
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            double angle = phase + t * Math.PI * 4d;   // 全程绕两圈
            double y = t * height;
            Location at = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, JUDGMENT_PURPLE);
        }
    }

    /**
     * **尖牙形状**：由"单个爆炸粒子"排出来的一个牙 —— 两条边向上收拢到尖、底部有个圆弧底座。
     *
     * @param yaw 朝向（弧度，绕 Y 轴）；决定牙立在哪个竖直平面里
     */
    public static void spawnFangShape(World world, Location base, float yaw, double scale) {
        if (world == null || base == null) {
            return;
        }
        //局部坐标 (x 向右, y 向上) 归一化轮廓；再按 yaw 转到世界
        double[][] outline = {
                {-0.55, 0.00}, {0.55, 0.00},          // 底座两端
                {0.50, 0.30}, {-0.50, 0.30},
                {0.42, 0.70}, {-0.42, 0.70},
                {0.32, 1.10}, {-0.32, 1.10},
                {0.22, 1.50}, {-0.22, 1.50},
                {0.12, 1.90}, {-0.12, 1.90},
                {0.00, 2.30}                          // 尖端
        };
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        for (double[] p : outline) {
            double lx = p[0] * scale;
            double ly = p[1] * scale;
            double wx = lx * cos;
            double wz = lx * sin;
            world.spawnParticle(Particle.EXPLOSION, base.clone().add(wx, 0.2 + ly, wz), 1, 0, 0, 0, 0);
        }
    }

    /** **一片爆点**（尖牙生成瞬间的"峰值"）。 */
    public static void spawnBurst(World world, Location center, int count, double spread, Particle particle) {
        if (world == null || center == null) {
            return;
        }
        world.spawnParticle(particle, center.clone().add(0, 0.4, 0), count, spread, spread * 0.6, spread, 0.02);
    }
}
