package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.ports.TimerPort;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.TimerComponent;

/**
 * {@link TimerPort} 的独立适配器（阶段 10 · t63 · A2）：**纯转发**到 {@link TimerComponent}
 * （= Unity 的 StartCoroutine）—— 任务的创建、登记归属、取消入口都在组件里，本类不持有任何状态 ✗。
 * <p><b>★ 请求者解析仍在唯一一处</b>：本端口是**按组件 id 一对一**构造的
 * （{@code RoleInstance#createServices(componentId)}）⇒ 调用方组件 = {@code registry.getById(componentId)}，
 * 由本端口解析后作为 {@code requester} 形参交给组件 ⇒ 任务登记进**调用方**的资源表
 * （与既有实现逐字一致：既有实现是在 {@code track} 里做同一件事）。
 * <p><b>⚠ 两处"同名反序"</b>：{@link TimerPort} 的 {@code task} 在**最后**，平台 {@code Scheduler} 的
 * {@code task} 在**前** —— 交换点在 {@link TimerComponent} 内（本类只做形参透传，不再做任何顺序变换）。
 */
final class TimerPortImpl implements TimerPort {

    private final RoleInstance owner;
    private final ComponentRegistry registry;
    private final String componentId;

    /**
     * **创建后绑定**的目标（阶段 11 · t84 · F-3）：{@code null} ⇒ 按 id 回落
     * （见 {@link RoleInstance#preferBound(RoleComponent, RoleComponent)}）。
     */
    private RoleComponent bound;

    TimerPortImpl(RoleInstance owner, ComponentRegistry registry, String componentId) {
        this.owner = owner;
        this.registry = registry;
        this.componentId = componentId;
    }

    /** 绑定"持有本端口的那一个组件实例"（由两个创建点在构造返回后**立刻**调用；幂等，后绑定覆盖）。 */
    void bind(RoleComponent component) {
        this.bound = component;
    }

    private TimerComponent component() {
        return owner.timerComponent();
    }

    /**
     * 请求者 = 持有本端口的那一个组件。
     * <p><b>阶段 11 · t84（F-3 修复）</b>：**先读已绑定实例**（{@link #bind(RoleComponent)}，由两个创建点
     * 在构造返回后立刻写入），**未绑定才按 id 回落** {@code registry.getById(componentId)}
     * （= 添加顺序第一个同 id 者）—— 同 id 两份实例时，任务登记归属不再可能落到别人身上 ✓；
     * 回落**必须保留**：框架级服务组件与"构造早于组件"的时刻只能按 id 解析，
     * **不得因此静默丢登记** ✗。
     * <p>与既有实现同源：装配完成后 {@code getById} 才合法（组件在 {@code awake()}/{@code start()} 里
     * 注册任务，均晚于 {@code freeze()}）；返回 {@code null} 的"不可能态"由组件回落到它自己，
     * **不静默丢登记**（旧实现在该分支直接跳过登记 ⇒ 会留下无人认领的任务，本工程已补上）。
     */
    private RoleComponent requester() {
        return RoleInstance.preferBound(bound, registry.getById(componentId));
    }

    @Override
    public Task run(Runnable task) {
        return component().run(requester(), task);
    }

    @Override
    public Task runLater(long delayTicks, Runnable task) {
        return component().runLater(requester(), delayTicks, task);
    }

    @Override
    public Task runRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        return component().runRepeating(requester(), initialDelayTicks, periodTicks, task);
    }

    @Override
    public void track(Task task) {
        component().track(requester(), task);
    }
}
