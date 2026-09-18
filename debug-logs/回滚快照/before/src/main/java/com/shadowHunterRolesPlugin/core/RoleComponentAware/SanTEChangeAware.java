package com.shadowHunterRolesPlugin.core.RoleComponentAware;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;

public interface SanTEChangeAware {

    void onSanTEChange(Player player, RoleInstance instance, int preSanTE, int newSanTE);

}
