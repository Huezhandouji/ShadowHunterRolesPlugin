package com.shadowHunterRolesPlugin.core.util;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;


public class ParticleUtil {

    public static void drawCircle(Location center, double radius, ParticleBuilder particleBuilder, int points){
        World world = center.getWorld();
        if(world == null) return;

        for(int i = 0; i < points; i++){
            double angle = 2 * Math.PI * i / points;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location loc = center.clone().add(x, 0, z);
            particleBuilder.location(loc).spawn();
        }
    }

    public static void drawLine(World world, Vector start, Vector end, Particle particle, double step){
        drawLine(world, start, end, particle, step, null);
    }

    /** 画一条线（带粒子 data 版本） */
    public static void drawLine(World world, Vector start, Vector end, Particle particle, double step, Object data){
        if(world == null || start == null || end == null || particle == null || step <= 0){
            return;
        }
        double distance = start.distance(end);
        int points = Math.max(1, (int)(distance / step));

        for(int i = 0; i <= points; i++){
            double t = (double) i / points;
            double x = start.getX() + (end.getX() - start.getX()) * t;
            double y = start.getY() + (end.getY() - start.getY()) * t;
            double z = start.getZ() + (end.getZ() - start.getZ()) * t;
            if(data == null){
                world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
            else{
                world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, data);
            }
        }
    }

    // ───────── 菱形（斜正方形）─────────

    /**
     * 在任意平面里画一个**菱形（斜正方形）**：按固定间距沿四条边逐点摆粒子。
     *
     * <p>纯几何版：调用方给出平面所在位置与两条半轴方向（例如"玩家背后的竖直平面"
     * = 中心 + 水平右方向 + 世界竖直），因此本方法不认识玩家、也不认识朝向。
     *
     * <p><b>为什么逐点摆，而不是给速度让客户端展线</b>：{@code count=0 + speed} 那条重载
     * 能画出"从中心绽放的线"，但展出来的长度由速度决定、**间距不可控**；
     * 需要精确间距（例如 0.2 格密铺）时只能逐点摆。
     *
     * @param center     菱形中心
     * @param rightAxis  平面内第一条半轴的**单位**方向（对应半对角线 {@code halfWidth}）
     * @param upAxis     平面内第二条半轴的**单位**方向（对应半对角线 {@code halfHeight}）
     * @param halfWidth  沿 {@code rightAxis} 的半对角线长度（格）
     * @param halfHeight 沿 {@code upAxis} 的半对角线长度（格）
     * @param particle   粒子类型
     * @param data       粒子 data（不需要则传 {@code null}）
     * @param step       相邻两颗粒子的间距（格）；{@code <= 0} 时退回"整圈约 32 颗"
     */
    public static void drawDiamond(Location center, Vector rightAxis, Vector upAxis,
                                   double halfWidth, double halfHeight,
                                   Particle particle, Object data, double step){
        if(center == null || particle == null || rightAxis == null || upAxis == null){
            return;
        }
        World world = center.getWorld();
        if(world == null){
            return;
        }
        //四个顶点，按顺序首尾相接（局部平面坐标：x 沿 rightAxis，y 沿 upAxis）
        double[][] vertices = {
                { 0d, halfHeight },    // 上
                { halfWidth, 0d },     // 右
                { 0d, -halfHeight },   // 下
                { -halfWidth, 0d },    // 左
        };
        double perimeter = 4d * Math.hypot(halfWidth, halfHeight);

        for(int edge = 0; edge < vertices.length; edge++){
            double[] a = vertices[edge];
            double[] b = vertices[(edge + 1) % vertices.length];
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double length = Math.hypot(dx, dy);
            //段数 = 边长 / 间距；间距非法时按"整圈约 32 颗"折算到这一条边
            int segments = step > 0
                    ? Math.max(1, (int) Math.ceil(length / step))
                    : Math.max(1, (int) Math.round(length / (perimeter / 32d)));

            for(int i = 0; i <= segments; i++){
                //按下标等分（而不是累加 t）：相邻两边共享顶点，转角处不留缝也不重影
                double t = (double) i / segments;
                Location at = center.clone()
                        .add(rightAxis.clone().multiply(a[0] + dx * t))
                        .add(upAxis.clone().multiply(a[1] + dy * t));
                if(data == null){
                    world.spawnParticle(particle, at, 1, 0, 0, 0, 0);
                }
                else{
                    world.spawnParticle(particle, at, 1, 0, 0, 0, 0, data);
                }
            }
        }
    }

    /**
     * 画菱形（淡色粉尘版）：颜色交给 {@link Particle.DustOptions}，
     * 实现与上面同一个（只是把 data 包好）。
     *
     * @param color 粉尘颜色
     * @param size  粉尘粒径
     */
    public static void drawDiamond(Location center, Vector rightAxis, Vector upAxis,
                                   double halfWidth, double halfHeight,
                                   Color color, float size, double step){
        if(color == null){
            return;
        }
        drawDiamond(center, rightAxis, upAxis, halfWidth, halfHeight,
                Particle.DUST, new Particle.DustOptions(color, size), step);
    }

}
