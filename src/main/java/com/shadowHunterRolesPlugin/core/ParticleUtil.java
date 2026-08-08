package com.shadowHunterRolesPlugin.core;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;



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

}
