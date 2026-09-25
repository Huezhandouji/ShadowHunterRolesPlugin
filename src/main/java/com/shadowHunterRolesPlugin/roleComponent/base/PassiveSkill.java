package com.shadowHunterRolesPlugin.roleComponent.base;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;

/**
 * 被动组件基类（继承 {@link RoleComponent}）。
 * <p>与今天的差别：`id` 与 `getId()` 上移到基类（`RoleComponent.getId()` 是 `final`），
 * 本类只保留 `displayName`/`description`。
 * 首位两参 `(id, ComponentServices)` 为**构造期注入**；其后两参**顺序与含义逐字稳定**。
 * 被动**不实现** `HotbarRenderComponent.HotbarItem`/`HotbarRenderComponent.HotbarPresentable`（也不在物品支持组件那一棵子树里）⇒ "能不能被施放"
 * 与"能不能上热键栏"仍是编译期事实。
 * <p>本类**不**在热键栏能力簇内 ⇒ 不会被强制实现 `buildItem()`（被动从不被渲染）；
 * 旧构造里那个历史瑕疵（被动曾误传技能 kind）已随 kind 枚举一并消失。
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
     * **被动描述符**（收敛为纯声明）：自带显示名与描述，
     * **继承描述符根类型**（{@link RoleComponent.Specification}）。
     * <p><b>规则进类型</b>：本类型**没有** {@code setSlot}，也**没有**
     * {@link com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.hotbar.HotbarSpecification} 的表现面 ——
     * 被动**不占热键栏**于是成为**编译期事实**（不是"装配点自觉传 -1"）：
     * 拿着被动描述符根本写不出指定栏位的代码。
     * <p>本类型**不实现** {@link #create(String, ComponentServices)} ⇒ 具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制）。
     */
    public abstract static class Specification extends RoleComponent.Specification<PassiveSkill> {

        private final Component displayName;
        private final Component description;

        protected Specification(Component displayName, Component description){
            super("Passive");
            this.displayName = displayName;
            this.description = description;
        }

        public final Component getDisplayName() { return displayName; }

        public final Component getDescription() { return description; }
    }
}
