package com.shadowHunterRolesPlugin.roleComponent.base;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;

/**
 * 被动组件基类（继承 {@link RoleComponent}）。
 *
 * <p><b>声明数据的来源 = 描述符</b>（与 {@link com.shadowHunterRolesPlugin.roleComponent.ActiveComponent}
 * 同一口径）：{@code displayName} / {@code description} **只写在嵌套 {@code Specification} 里一次**，
 * 本类不再各存一份字段，{@link #getDisplayName()} / {@link #getDescription()} 直接**委托**给描述符 ✓。
 *
 * <p>★ <b>为什么要改</b>：旧形态里同一对文案被写**两遍** —— 组件构造函数的两个实参、
 * 以及描述符构造函数的两个实参。两处是**互相独立的副本** ⇒ 改一处忘另一处会**静默不一致**
 * （角色列表读描述符、热键栏读组件字段）。收敛成一支后**不可能写重**。
 *
 * <p>首位两参 {@code (id, ComponentServices)} 为**构造期注入**；第 3 参为**本组件自己的描述符**
 * （由 {@code Specification#create(...)} 把它交回来 ⇒ 组件与描述符同源）。
 *
 * <p>被动**不实现** `HotbarRenderComponent.HotbarItem`/`HotbarRenderComponent.HotbarPresentable`
 * （也不在物品支持组件那一棵子树里）⇒ "能不能被施放"与"能不能上热键栏"仍是编译期事实。
 * <p>本类**不**在热键栏能力簇内 ⇒ 不会被强制实现 `buildItem()`（被动从不被渲染）。
 */
public abstract class PassiveSkill extends RoleComponent {

    /** **本组件的描述符**（声明数据的唯一来源；由子类在构造时交回）。 */
    private final Specification specification;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（**声明数据的唯一来源**）
     */
    public PassiveSkill(String id, ComponentServices services, Specification specification){
        super(id, services);
        this.specification = specification;
    }

    /** **唯一实现点**：被动描述符的读面（显示名 / 描述都从这里取）。 */
    public final Specification specification() {
        return specification;
    }

    /** 显示名（数据源 = 描述符）。 */
    public Component getDisplayName() { return specification().getDisplayName(); }

    /** 描述（数据源 = 描述符）。 */
    public Component getDescription() { return specification().getDescription(); }

    /**
     * **被动描述符**（收敛为纯声明）：自带显示名与描述，
     * **继承描述符根类型**（{@link RoleComponent.Specification}）。
     * <p><b>规则进类型</b>：本类型**没有** {@code setSlot}，也**没有**
     * {@link com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification} 的表现面 ——
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
