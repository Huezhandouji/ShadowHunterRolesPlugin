package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import org.bukkit.entity.Player;

import java.util.UUID;

/** {@link SelfPort} 的独立适配器：每次现取玩家（不缓存引用）。 */
final class SelfImpl implements SelfPort {

    private final RoleInstance owner;

    SelfImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public Player player() {
        return owner.getPlayer();
    }

    @Override
    public UUID id() {
        return owner.getPlayer().getUniqueId();
    }
}
