package com.shadowHunterRolesPlugin.event;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;

public class SanTEChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RoleInstance instance;
    private final int preSanTE;
    private final int currentSanTE;
    private final int maxSanTE;

    public SanTEChangeEvent(Player player, RoleInstance instance, int preSanTE, int currentSanTE, int maxSanTE){
        this.player = player;
        this.instance = instance;
        this.preSanTE = preSanTE;
        this.currentSanTE = currentSanTE;
        this.maxSanTE = maxSanTE;
    }

    //getter
    public Player getPlayer() {
        return player;
    }

    public RoleInstance getInstance() {
        return instance;
    }

    public int getPreSanTE() {
        return preSanTE;
    }

    public int getCurrentSanTE() {
        return currentSanTE;
    }

    public int getMaxSanTE() {
        return maxSanTE;
    }

    public int getChangedAmount(){
        return currentSanTE - preSanTE;
    }

    public boolean isIncreased(){
        return getChangedAmount() > 0;
    }

    public boolean isDecreased(){
        return getChangedAmount() < 0;
    }

    public boolean isNotChanged(){
        return getChangedAmount() == 0;
    }

    public boolean isZero(){
        return currentSanTE <= 0;
    }

    public boolean isFull(){
        return currentSanTE >= maxSanTE;
    }

    @Override
    public HandlerList getHandlers(){
        return HANDLERS;
    }

    public static HandlerList getHandlerList(){
        return HANDLERS;
    }
}

