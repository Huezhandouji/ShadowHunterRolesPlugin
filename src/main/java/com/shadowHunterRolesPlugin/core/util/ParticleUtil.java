package com.shadowHunterRolesPlugin.core.util;

import com.destroystokyo.paper.ParticleBuilder;
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

}
