package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;

/**
 * 组件工厂：**构造期注入** {@link ComponentServices} 的唯一入口。
 *
 * <p>阶段 4 / T 块 ⑤ 原子批（`bind` → `ComponentFactory`）的产物：装配表由
 * `(id, Supplier)` 改为 `(id, ComponentFactory)`，容器在**创建组件时就**把
 * {@code ComponentServices} 传进构造器 ⇒ 组件在 {@code awake()} 之前即持有服务，
 * 与旧 {@code bind(...)} 的语义等价，但**没有"创建后再注入"的中间态**
 * （旧 `RoleComponent.bind` 及其异常文案随之删除）。
 *
 * <p>泛型 {@code T} 保留具体组件类型，便于调用方拿到强类型返回值；实现通常写作方法引用，
 * 例如 {@code MeiqiheziUnconcernSkill::new}（其构造器为 {@code (String id, ComponentServices services)}）。
 */
@FunctionalInterface
public interface ComponentFactory<T extends RoleComponent> {

    /**
     * @param id       组件唯一 id（由注册处声明，**不再**在组件构造器里硬编码）
     * @param services 该组件所属角色实例的服务集合（白名单 10 成员，构造期注入后不可更换）
     * @return 新建的组件实例（尚未注册、尚未经历任何生命周期回调）
     */
    T create(String id, ComponentServices services);
}
