package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.FactionPort;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * {@link FactionPort} 的独立适配器。
 * {@code hasEnemyInRange} 的几何 + 阵营语义**逐字**沿用原 {@code SkillUtil.hasEnemyInRange}
 * （**未选角色的玩家也算敌人**），以组件自己的玩家位置为圆心（与两个 AutoRecover* 的调用一致）。
 * 阶段 4（B⑤）：原静态方法已删除，逻辑搬到这里 —— 组件侧改调 {@code svc().factions().hasEnemyInRange(r)}。
 */
final class FactionPortImpl implements FactionPort {

    private final RoleInstance owner;

    FactionPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public boolean isHostile(Player victim) {
        return owner.isHostileTo(victim);
    }

    @Override
    public Faction faction() {
        return owner.getFaction();
    }

    @Override
    public boolean hasEnemyInRange(double radius) {
        Location loc = owner.getPlayer().getLocation();
        if(loc == null || loc.getWorld() == null) return false;

        Faction selfFaction = owner.getFaction();

        for(Player p : loc.getNearbyPlayers(radius)){
            if(p == null) continue;
            //没有选角色的玩家也要算进来（FactionLookup 对未选角色返回敌对）
            if(owner.rolesContext().factions().isHostile(selfFaction, p)){
                return true;
            }
        }
        return false;
    }
}
