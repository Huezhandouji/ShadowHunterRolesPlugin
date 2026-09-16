package com.shadowHunterRolesPlugin.core.RoleComponentAware;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;

public interface UpdateAware {

    void update(Player player, RoleInstance instance);

}
