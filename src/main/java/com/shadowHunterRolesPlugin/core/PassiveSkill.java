package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;

/**
 * 被动组件基类（阶段 4 B0b-1：改基到 {@link RoleComponent}）。
 * <p>与今天的差别：`id` 与 `getId()` 上移到基类（`RoleComponent.getId()` 是 `final`），
 * 本类只保留 `displayName`/`description`。
 * 阶段 4 收尾批⑤：首位两参 `(id, ComponentServices)` 为**构造期注入**；其后两参**顺序与含义与迁移前逐字一致**。
 * 被动**不实现** `HotbarItem`/`HotbarActionable` ⇒ "能不能被施放"仍是编译期事实。
 */
public abstract class PassiveSkill extends RoleComponent {

    protected final Component displayName;
    protected final Component description;

    public PassiveSkill(String id, ComponentServices services, Component displayName, Component description){
        super(id, services);
        this.displayName = displayName;
        this.description = description;
    }

    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }

    /**
     * **被动描述符**（阶段 7 · A 步骨架）：kind 固定为 {@link ItemKind#PASSIVE}，
     * 自带显示名与描述，**继承描述符根类型**（{@link RoleComponent.Specification}）。
     * <p><b>规则进类型</b>（阶段 7 拍板）：本类型**没有** {@code setSlot}，也**没有**
     * {@link com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification} 的表现面 ——
     * 被动**不占热键栏**于是成为**编译期事实**（不是"装配点自觉传 -1"）：
     * 拿着被动描述符根本写不出指定栏位的代码（反例探针见阶段 7 说明件）。
     * <p>本类型**不实现** {@link #create(String, ComponentServices)} ⇒ 具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制）。
     */
    public abstract static class Specification extends RoleComponent.Specification<PassiveSkill> {

        private final Component displayName;
        private final Component description;

        protected Specification(Component displayName, Component description){
            super(ItemKind.PASSIVE);
            this.displayName = displayName;
            this.description = description;
        }

        public final Component getDisplayName() { return displayName; }

        public final Component getDescription() { return description; }
    }
}
