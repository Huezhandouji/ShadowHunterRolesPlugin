package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.CooldownAware;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItem;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 主动组件基类（设计 §4.3）：= 原 `Skill` + `MainWeapon` 去重后的并集，不多一个成员。
 * <p>
 * <b>阶段 6 · 统一装配</b>：本类降级为**可选的便利实现** —— 它只做一件事：把构造参数装进
 * {@link HotbarSpecification} 并实现 {@link #specification()}（**唯一实现点**）。7 个表现访问器全部由
 * {@link HotbarPresentable} 的 `default` 方法提供 ⇒ 子类不再需要（也不应）逐个手写委托。
 * <p>
 * <b>阶段 7 · A 步</b>：表现规格类改全拼（`HotbarSpec` → {@link HotbarSpecification}），
 * 同时把它升格为**装配期描述符**（{@link RoleComponent.Specification}）的"带栏位"分支。
 * 本类持有的这一份是**表现值对象**：它没有栏位（栏位由装配器在描述符上设置），
 * 也从不由装配入口消费 —— 因此这条路径与阶段 6 逐字等价。
 * <p>
 * 行为分支（冷却表 / 闸门 / PDC 键 / 文案表）一律由**注册处**的 kind 决定
 * （`Role.Builder` 统一入口的入参 ⇒ `RoleInstance.createServices(id)`）；
 * 本类规格里的 kind **只作表现用途**。新组件若不需要"能施放 + 能渲染"这套默认组合，
 * 可以只 `extends RoleComponent` 并按需实现能力接口（本批不删三个基类）。
 */
public abstract class ActiveComponent extends RoleComponent
        implements HotbarItem, HotbarActionable, HotbarPresentable, CooldownAware {

    private final HotbarSpecification<?> specification;

    /**
     * **描述符口径的构造**（阶段 7 · B 步）：表现规格**不再由构造实参内联**，而是由组件自己的
     * 嵌套 `Specification` 声明、经装配入口 {@code Role.Builder.addComponent(id, specification)}
     * 交给容器，容器再经 {@code Specification.create(id, services)} 把它交给本构造器。
     * <p>本类持有的这一份同时是**渲染器的读面**（{@link #specification()}）；它由装配期
     * {@code freeze()} 置为只读 ⇒ 同一实例被多个玩家实例共享也不会被串改。
     */
    protected ActiveComponent(String id, ComponentServices services, HotbarSpecification<?> specification) {
        super(id, services);
        this.specification = specification;
    }

    /**
     * 旧构造口径（表现参数内联）：**保留为兼容别名** —— 语义等价于把七个参数原样交给
     * {@link HotbarSpecification#of}（kind 由子类给定）。
     *
     * @deprecated 改用 `(id, services, specification)`：表现值写进组件自己的嵌套 `Specification`。
     */
    @Deprecated
    protected ActiveComponent(String id, ComponentServices services, Component displayName, Component description,
                              int cooldownTicks, int energyCost, Material icon, ItemKind kind) {
        this(id, services, HotbarSpecification.of(id, displayName, description, icon, cooldownTicks, energyCost, kind));
    }

    /** **唯一实现点**：表现规格（`getId` / `getDisplayName` / … 7 个访问器由接口 default 委托到本方法）。 */
    @Override
    public final HotbarSpecification<?> specification() {
        return specification;
    }

    /**
     * 默认：不做事、也**不**进冷却（与今天 listener 的行为一致：未重写的热键栏触发只做就绪预检）。
     * 两种 kind 统一为此默认值，不需要按 kind 分支。
     */
    @Override
    public CastResult onCast(CastSignal signal) {
        return CastResult.NO_COOLDOWN;
    }

    //冷却结束通知与 CooldownEndReason 已迁到能力接口 CooldownAware（阶段 6 · 派发面能力化第二刀）：
    //  · 本类通过 implements CooldownAware 继续获得"默认空实现"的同一语义（既有组件零改动、运行时等价）；
    //  · 需要响应冷却结束的组件改为**覆写** CooldownAware#onCooldownEnd，不再依赖继承本类。
}
