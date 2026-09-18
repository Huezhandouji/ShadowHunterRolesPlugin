package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;


public class SkillUtil {

    public static boolean hasEnemyInRange(Faction selfFaction, Location loc, double radius){
        if(loc == null || loc.getWorld() == null) return false;

        for(Player p : loc.getNearbyPlayers(radius)){
            if(p == null) continue;
            RoleInstance other = RoleManager.getInstance().getRoleInstance(p);
            //没有选角色的玩家也要算进来，否则 getRoleInstance() 返回 null 会抛 NPE
            if(other == null || other.isHostileTo(selfFaction)){
                return true;
            }
        }
        return false;
    }

    public static List<Player> getPlayersInSightLine(Player player, double maxDistance, double range) {
        List<Player> result = new ArrayList<>();
        Set<Entity> hit = new HashSet<>();

        Location start = player.getEyeLocation().clone();
        Vector direction = start.getDirection().normalize();
        double remaining = maxDistance;

        while (remaining > 0) {
            RayTraceResult rayResult = player.getWorld().rayTraceEntities(
                    start,
                    direction,
                    remaining,
                    range,
                    entity -> entity instanceof Player
                            && !entity.equals(player)
                            && !hit.contains(entity)
            );

            if (rayResult == null || rayResult.getHitEntity() == null) break;

            Entity target = rayResult.getHitEntity();
            result.add((Player) target);
            hit.add(target);

            // 计算命中点距离，推进起点
            double distance = start.toVector().distance(rayResult.getHitPosition());
            if (distance <= 0.0001) break; // 防止死循环

            remaining -= distance;
            start = rayResult.getHitPosition().toLocation(player.getWorld());
        }

        return result;
    }

}
