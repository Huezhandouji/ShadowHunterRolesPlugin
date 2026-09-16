package com.shadowHunterRolesPlugin.core;

public class Buff {

    private final BuffType type;
    private int remainingTicks;

    public Buff(BuffType type, int durationTicks){
        this.type = type;
        this.remainingTicks = durationTicks;
    }

    public BuffType getType() {
        return type;
    }

    public int getRemainingTicks(){
        return remainingTicks;
    }

    public float getRemainingSeconds(){
        return remainingTicks / 20f;
    }

    public boolean isExpired(){
        return remainingTicks <= 0;
    }

    public void tick(){
        remainingTicks--;
    }
}
