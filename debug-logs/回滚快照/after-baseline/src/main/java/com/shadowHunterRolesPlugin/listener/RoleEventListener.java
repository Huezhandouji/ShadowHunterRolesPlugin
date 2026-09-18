package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class RoleEventListener implements Listener {

    @EventHandler
    public void onEnergyChange(EnergyChangeEvent event){
        RoleInstance instance = event.getInstance();
        instance.triggerEnergyChange(event.getPreEnergy(), event.getCurrentEnergy());
    }

    @EventHandler
    public void onSanTEChange(SanTEChangeEvent event){
        RoleInstance instance = event.getInstance();
        instance.triggerSanTEChange(event.getPreSanTE(), event.getCurrentSanTE());
    }

}
