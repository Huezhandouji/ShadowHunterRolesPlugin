package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;

/**
 * 被动组件基类（阶段 4 B0b-1：改基到 {@link RoleComponent}）。
 * <p>与今天的差别：`id` 与 `getId()` 上移到基类（`RoleComponent.getId()` 是 `final`），
 * 本类只保留 `displayName`/`description`；**构造参数顺序不变** ⇒ 8 个被动组件的 `super(...)` 一字不改。
 * 被动**不实现** `HotbarItem`/`HotbarActionable` ⇒ "能不能被施放"仍是编译期事实。
 */
public abstract class PassiveSkill extends RoleComponent {

    protected final Component displayName;
    protected final Component description;

    public PassiveSkill(String id, Component displayName, Component description){
        super(id);
        this.displayName = displayName;
        this.description = description;
    }

    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
}
