package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * 计时组件（阶段 10 · t55 · A1）：系统级能力「组件资源表（任务登记 / 取消）」的**组件形态**
 * （每角色实例一个，裁定③）。
 * <p><b>薄封装</b>：内部**转调既有端口** {@code TimerPort}（= Unity 的 StartCoroutine）——
 * 每个任务在创建时立即登记进**资源表**，组件 {@code stop()} 返回后由框架兜底取消。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * private TimerComponent timers;
 * @Override public void awake() { timers = getComponent(TimerComponent.class); }
 * @Override public void start() { timers.runLater(20L, () -> { ... }); }
 * }</pre>
 * <p><b>装配示例</b>：{@code builder.addComponent("timers", new TimerComponent.Specification());}（不占栏位）。
 * <p><b>★ 已知限制（如实申报，A4 第 5 条同族）</b>：既有端口 {@code TimerPort} 是**按组件 id 一对一**构造的
 * （资源表按"登记它的那个组件"记账）⇒ 本组件转调的是**它自己那一份**端口 ⇒ 任务登记在
 * **计时组件的资源表**下（生命周期 = 角色实例），**不**随"调用方组件"的 `stop()` 被取消。
 * 与 {@code svc().timers()}（登记在**调用方**名下）存在这一处**归属差异** ⇒
 * 需要"任务随调用方组件停止而取消"的组件，**本卡请继续使用 {@code svc().timers()}**；
 * 归属问题的最小闭合 = 重接卡给本组件加"请求者"形参（或在容器侧提供按请求者登记的口子）。
 * <p><b>本卡不改任何调用点</b>：既有组件仍走 {@code svc().timers()}。
 */
public class TimerComponent extends RoleComponent {

    public TimerComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 下一 tick 执行一次（登记进资源表）。 */
    public Task run(Runnable task) {
        return svc().timers().run(task);
    }

    /** 延迟 {@code delayTicks} 刻执行一次（登记进资源表）。 */
    public Task runLater(long delayTicks, Runnable task) {
        return svc().timers().runLater(delayTicks, task);
    }

    /** 周期执行（首次延迟 {@code initialDelayTicks}、周期 {@code periodTicks}；登记进资源表）。 */
    public Task runRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
        return svc().timers().runRepeating(initialDelayTicks, periodTicks, task);
    }

    /** 把一个**外部创建**的句柄补登记进资源表（与端口同语义）。 */
    public void track(Task task) {
        svc().timers().track(task);
    }

    /** 装配描述符：**不占栏位**；提供类型 = {@code TimerComponent.class}。 */
    public static final class Specification extends RoleComponent.Specification<TimerComponent> {

        public Specification() {
            super("TimerComponent");
        }

        @Override
        public TimerComponent create(String id, ComponentServices services) {
            return new TimerComponent(id, services);
        }
    }
}
