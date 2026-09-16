package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.FactionPort;
import com.shadowHunterRolesPlugin.roleComponent.SkillUtil;
import org.bukkit.entity.Player;

/**
 * {@link FactionPort} 的独立适配器。
 * {@code hasEnemyInRange} 复用 {@code SkillUtil} 的几何 + 阵营语义（**未选角色也算敌人**），
 * 以组件自己的玩家位置为圆心（与今天两个 AutoRecover* 的调用一致）。
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
        return SkillUtil.hasEnemyInRange(owner, owner.getPlayer().getLocation(), radius);
    }
}
