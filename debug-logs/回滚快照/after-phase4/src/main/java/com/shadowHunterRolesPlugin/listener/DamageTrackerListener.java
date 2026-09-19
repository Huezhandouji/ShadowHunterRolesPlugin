package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class DamageTrackerListener implements Listener {

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player victim)) return;
        if(!(event.getDamager() instanceof Player damager)) return;

        victim.getPersistentDataContainer().set(
                DamageUtil.LAST_DAMAGER_KEY,
                PersistentDataType.STRING,
                damager.getUniqueId().toString()
        );
    }
}
