package com.shadowHunterRolesPlugin.core.RoleComponentAware;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;

public interface EnergyChangeAware {

    void onEnergyChange(Player player, RoleInstance instance, int preEnergy, int newEnergy);

}
