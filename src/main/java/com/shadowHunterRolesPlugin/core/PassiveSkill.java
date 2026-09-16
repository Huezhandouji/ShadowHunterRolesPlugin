package com.shadowHunterRolesPlugin.core;

import net.kyori.adventure.text.Component;

public abstract class PassiveSkill {

    protected final String id;
    protected final Component displayName;
    protected final Component description;

    public PassiveSkill(String id, Component displayName, Component description){
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
}
