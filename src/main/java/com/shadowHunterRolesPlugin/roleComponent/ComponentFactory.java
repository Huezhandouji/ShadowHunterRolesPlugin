package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;

/**
 * 组件工厂：构造期注入 {@link ComponentServicesPort} 的唯一入口。
 *
 * <p>装配表由 `(id, Supplier)` 改为 `(id, ComponentFactory)`，容器在创建组件时就把
 * {@code ComponentServicesPort} 传进构造器，因此组件在 {@code awake()} 之前即持有服务，
 * 与"创建后一次性注入"语义等价，但没有"创建后再注入"的中间态。
 *
 * <p>泛型 {@code T} 保留具体组件类型，便于调用方拿到强类型返回值；实现通常写作方法引用，
 * 例如 {@code MeiqiheziUnconcernSkill::new}
 * （其构造器为 {@code (String id, ComponentServicesPort services)}）。
 *
 * <p>本接口与装配期描述符的关系：{@link RoleComponent.Specification#create(String, ComponentServicesPort)}
 * 与 {@link #create(String, ComponentServicesPort)} 同签名同语义，因此任何一个描述符都可以直接当作本接口用
 * （{@code spec::create}）；装配入口 {@code Role.Builder.addComponent(String, Specification)}
 * 内部就是这么取的，快照形态见 {@link RoleComponent.Specification.Snapshot}。
 */
@FunctionalInterface
public interface ComponentFactory<T extends RoleComponent> {

    /**
     * @param id       组件唯一 id（由注册处声明，不在组件构造器里硬编码）
     * @param services 该组件所属角色实例的服务集合
     *                 （3 成员：self / components / roleInfo，构造期注入后不可更换）
     * @return 新建的组件实例（尚未注册、尚未经历任何生命周期回调）
     */
    T create(String id, ComponentServicesPort services);
}
