package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.ports.TimerPort;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * {@link TimerPort} 的独立适配器（= Unity 的 StartCoroutine）：
 * 每个任务在创建时**立即登记**进本组件的资源表，组件 {@code stop()} 返回后由框架兜底取消。
 */
final class TimerPortImpl implements TimerPort {

    private final RoleInstance owner;
    private final ComponentRegistry registry;
    private final RoleComponent component;

    TimerPortImpl(RoleInstance owner, ComponentRegistry registry, RoleComponent component) {
        this.owner = owner;
        this.registry = registry;
        this.component = component;
    }

    @Override
    public Task run(Runnable task) {
        Task handle = owner.rolesContext().scheduler().run(task);
        track(handle);
        return handle;
    }

    @Override
    public Task runLater(long delayTicks, Runnable task) {
        Task handle = owner.rolesContext().scheduler().runLater(task, delayTicks);
        track(handle);
        return handle;
    }

    @Override
    public Task runRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        Task handle = owner.rolesContext().scheduler().runRepeating(task, initialDelayTicks, periodTicks);
        track(handle);
        return handle;
    }

    @Override
    public void track(Task task) {
        if (task != null) {
            registry.track(component, task);
        }
    }
}
