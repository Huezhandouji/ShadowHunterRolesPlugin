package com.shadowHunterRolesPlugin.roleComponent.base;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 被动组件基类（继承 {@link RoleComponent}）。
 *
 * <p>声明数据的来源 = 描述符（与 {@link com.shadowHunterRolesPlugin.roleComponent.ActiveComponent}
 * 同一口径）：{@code displayName} / {@code description} 只写在嵌套 {@code Specification} 里一次，
 * 本类不再各存一份字段，{@link #getDisplayName()} / {@link #getDescription()} 直接委托给描述符；
 * 一处声明 ⇒ 角色列表与热键栏读到的文案不可能不一致。
 *
 * <p>首位两参 {@code (id, ComponentServicesPort)} 为构造期注入；第 3 参为本组件自己的描述符
 * （由 {@code Specification#create(...)} 把它交回来 ⇒ 组件与描述符同源）。
 *
 * <p>被动不在物品支持组件那一棵子树上（它不继承 {@code ActiveComponent}，也没有带栏位的描述符支），
 * 因此"能不能被施放"与"能不能上热键栏"仍是编译期事实；本类不会被强制实现 `buildItem()`
 * （被动从不被渲染）。
 */
public abstract class PassiveSkill extends RoleComponent {

    /** 本组件的描述符（声明数据的唯一来源；由子类在构造时交回）。 */
    private final Specification specification;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public PassiveSkill(String id, ComponentServicesPort services, Specification specification){
        super(id, services);
        this.specification = specification;
    }

    /** 唯一实现点：被动描述符的读面（显示名 / 描述都从这里取）。 */
    public final Specification specification() {
        return specification;
    }

    /** 显示名（数据源 = 描述符）。 */
    public Component getDisplayName() { return specification().getDisplayName(); }

    /** 描述（数据源 = 描述符；零到多行，恒非 null）。 */
    public List<Component> getDescription() { return specification().getDescription(); }

    /**
     * 被动描述符（纯声明）：自带显示名与描述，继承描述符根类型（{@link RoleComponent.Specification}）。
     * <p>规则进类型：本类型没有 {@code setSlot}，也没有
     * {@link com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification} 的表现面，
     * 因此被动不占热键栏是编译期事实（不是"装配点自觉传 -1"）：拿着被动描述符根本写不出指定栏位的代码。
     * <p>泛型化（`<P>` = 本组件自己的类型；理由同 `Skill.Specification`）：参数化后
     * `providedType()` 推导出具体被动类，别的组件可以 `requires(某具体被动.class)`
     * （实测事故：`CangluTraumaMainWeapon` 声明 `requires(CangluHysteriaPassive.class)`，
     * 而后者推导成 `PassiveSkill.class` ⇒ 装配期报「缺必需依赖」）。
     * <p>本类型不实现 {@link #create(String, ComponentServicesPort)}，具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制）。
     */
    public abstract static class Specification<P extends PassiveSkill>
            extends RoleComponent.Specification<P> {

        private final Component displayName;
        /**
         * 描述 = 零到多行（每行一个 {@link Component}）；{@code null} 在构造期归一为空列表 ⇒ 恒非 null。
         * <p>被动不上热键栏，因此本列表不是物品 lore；口径与 {@code HotbarSpecification#getDescription()} 一致，
         * 只为"描述可以多行"这一条不因家族而异。
         */
        private final List<Component> description;

        protected Specification(Component displayName, List<Component> description){
            super("Passive");
            this.displayName = displayName;
            this.description = description == null ? List.of() : List.copyOf(description);
        }

        public final Component getDisplayName() { return displayName; }

        /** 描述行序（恒非 null；零行 = 无描述）。 */
        public final List<Component> getDescription() { return description; }
    }
}
