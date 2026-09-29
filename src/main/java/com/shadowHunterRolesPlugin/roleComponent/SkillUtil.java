package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;


public class SkillUtil {

    // 本类只放无状态几何工具 `getPlayersInSightLine`（纯射线几何、不查阵营、零插件依赖）；
    // 阵营判定（"自身半径内是否有敌人"及"没有角色算敌人 / 创造旁观不算"那套口径）归聚合根的只读服务面
    // `RoleInfoPort#hasEnemyInRange(radius)`。

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
