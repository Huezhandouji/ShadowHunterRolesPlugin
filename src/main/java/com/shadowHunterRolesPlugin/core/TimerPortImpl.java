package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.ports.TimerPort;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * {@link TimerPort} 的独立适配器（= Unity 的 StartCoroutine）：
 * 每个任务在创建时**立即登记**进本组件的资源表，组件 {@code stop()} 返回后由框架兜底取消。
 * <p>
 * 阶段 4 收尾批⑤（构造期注入）：服务集必须先于组件实例存在，因此本类**不再捕获组件实例**，
 * 改为捕获组件 id、登记时按 id 解析（{@code ComponentRegistry.getById}）。等价性：id 在角色实例内唯一
 * （{@code Role.Builder} 对技能/被动/主武器跨类型去重）⇒ 解析到的就是同一实例、同一资源表键；
 * 解引用只发生在运行期（装配完成且 {@code freeze()} 之后），与旧实现登记的对象一致。
 * <p>
 * {@code null} 分支：{@code getById} 返回 {@code null}（理论上只可能发生在"组件尚未登记/已被注销"的
 * 装配外状态）时**安全跳过**登记，不做任何 substitute —— 与旧实现"不可能为 null"的差异只在该不可能态，
 * 且不静默替换资源表键。
 */
final class TimerPortImpl implements TimerPort {

    private final RoleInstance owner;
    private final ComponentRegistry registry;
    private final String componentId;

    TimerPortImpl(RoleInstance owner, ComponentRegistry registry, String componentId) {
        this.owner = owner;
        this.registry = registry;
        this.componentId = componentId;
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
        //⚠ 本行是 TimerPort 与 Scheduler 两个"同名反序"签名之间的**唯一**转调点：此处把 task 从末位挪到首位。
        //initialDelay <= 0 归一为 1：与 BukkitSchedulerAdapter 同值归一（Math.max 幂等 ⇒ 双入口双保险）。
        Task handle = owner.rolesContext().scheduler().runRepeating(task, Math.max(1L, initialDelayTicks), periodTicks);
        track(handle);
        return handle;
    }

    @Override
    public void track(Task task) {
        if (task == null) {
            return;
        }
        RoleComponent component = registry.getById(componentId);
        if (component != null) {
            registry.track(component, task);
        }
    }
}