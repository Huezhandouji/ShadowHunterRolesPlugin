package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;


public class SkillUtil {

    //阶段 2：不再查 RoleManager 单例，改走 RoleInstance 注入的 FactionLookup（未选角色 → 敌对）
    public static boolean hasEnemyInRange(RoleInstance self, Location loc, double radius){
        if(loc == null || loc.getWorld() == null) return false;

        Faction selfFaction = self.getFaction();

        for(Player p : loc.getNearbyPlayers(radius)){
            if(p == null) continue;
            //没有选角色的玩家也要算进来（FactionLookup 对未选角色返回敌对）
            if(self.rolesContext().factions().isHostile(selfFaction, p)){
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
