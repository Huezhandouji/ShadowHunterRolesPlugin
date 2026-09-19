package com.shadowHunterRolesPlugin.event;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

public class EnergyChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RoleInstance instance;
    private final int preEnergy;
    private final int currentEnergy;
    private final int maxEnergy;

    public EnergyChangeEvent(Player player, RoleInstance instance, int preEnergy, int currentEnergy, int maxEnergy){
        this.player = player;
        this.instance = instance;
        this.preEnergy = preEnergy;
        this.currentEnergy = currentEnergy;
        this.maxEnergy = maxEnergy;
    }

    //getter
    public Player getPlayer(){
        return player;
    }

    public RoleInstance getInstance(){
        return instance;
    }

    public int getPreEnergy() {
        return preEnergy;
    }

    public int getCurrentEnergy() {
        return currentEnergy;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public int getChangedAmount(){
        return currentEnergy - preEnergy;
    }

    public boolean isIncrease(){
        return currentEnergy > preEnergy;
    }

    public boolean isDecrease(){
        return currentEnergy < preEnergy;
    }

    public boolean isNotChanged(){
        return currentEnergy == preEnergy;
    }

    public boolean isFull(){
        return currentEnergy >= maxEnergy;
    }

    public boolean isZero(){
        return currentEnergy <= 0;
    }

    //HandlerList
    @Override
    public @NonNull HandlerList getHandlers(){
        return HANDLERS;
    }


    public static HandlerList getHandlerList(){
        return HANDLERS;
    }
}
