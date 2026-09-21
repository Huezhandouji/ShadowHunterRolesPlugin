package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;

/**
 * 组件工厂：**构造期注入** {@link ComponentServices} 的唯一入口。
 *
 * <p>阶段 4 / T 块 ⑤ 原子批的产物：装配表由 `(id, Supplier)` 改为 `(id, ComponentFactory)`，
 * 容器在**创建组件时就**把 {@code ComponentServices} 传进构造器 ⇒ 组件在 {@code awake()} 之前
 * 即持有服务，与旧的过渡期"创建后一次性注入"语义等价，但**没有"创建后再注入"的中间态**
 * （旧 `RoleComponent.bind` 及其异常文案随之删除）。
 *
 * <p>泛型 {@code T} 保留具体组件类型，便于调用方拿到强类型返回值；实现通常写作方法引用，
 * 例如 {@code MeiqiheziUnconcernSkill::new}（其构造器为 {@code (String id, ComponentServices services)}）。
 *
 * <p><b>阶段 7 · A 步：本接口与装配期描述符的关系</b> ——
 * {@link RoleComponent.Specification#create(String, ComponentServices)} 与
 * {@link #create(String, ComponentServices)} **同签名同语义**，因此任何一个描述符都可以直接当作本接口用
 * （{@code spec::create}）；装配入口 {@code Role.Builder.addComponent(String, Specification)} 内部就是这么取的。
 * 本接口**不删**：旧装配重载（{@code (id, 工厂, 槽位, kind)} 与 {@code (id, 工厂, kind)}）仍然只收它，
 * 两条路径最终汇成同一个不可变快照（{@link RoleComponent.Specification.Snapshot}）。
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
