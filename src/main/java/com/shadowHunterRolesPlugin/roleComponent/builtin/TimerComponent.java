package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.platform.Scheduler;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * 计时组件（**归属按请求者**）：系统级能力
 * 「组件资源表（任务登记 / 取消）」的**组件形态**（每角色实例一个）。
 * <p><b>★ 本组件持有行为，并且**归属按请求者**</b>：
 * <ul>
 *   <li>任务的**创建**（{@link Scheduler} = Bukkit 调度器 = 允许依赖的"外部东西" ✓）在本组件内完成；</li>
 *   <li>任务的**登记归属**由本组件按 {@code requester}（请求者组件）决定 —— 这是原先散布在
 *       **按组件 id 解析请求者的转发形态**里的逻辑的**唯一新家** ✓；</li>
 *   <li>资源的**存储**仍落在容器的**每组件资源表**（{@link TaskSink} 注入 = {@code ComponentRegistry#track} /
 *       {@code #cancelAll}）⇒ <b>既有回收机制一条都不改</b>（{@code clear()} 的 {@code cancelAllAndClear()}、
 *       运行期删除路径的 {@code cancelAll} 全部照旧生效 ⇒ 不引入新泄漏面 ✓）。</li>
 * </ul>
 * <p><b>★ 调用方 {@code stop()} ⇒ 其请求的任务全部取消</b>：{@link #cancelAllOf(RoleComponent)}
 * 是这条语义的入口，容器在 {@code triggerLifecycleStop()} 里**每个组件 {@code stop()} 之后**调用一次
 * ⇒ "谁请求的计时，谁停止时被取消" ✓（只在实例 {@code clear()} 的兜底里取消是不够的 ⇒ 单独 {@code stop()}
 * 会漏 ⇒ 本方法把这一点补上）。
 * <p><b>调用点一律直接用本组件</b> ✓（不经服务集转发；
 * 那一族端口**已整体删除** ⇒ 全库零残留）。
 */
public class TimerComponent extends RoleComponent {

    /**
     * 资源表接入口（容器在构造期注入）：**就是** {@code ComponentRegistry} 的每组件资源表
     * （{@code registry::track} / {@code registry::cancelAll}）。
     * <p>刻意做成接口而不是直接用 {@code ComponentRegistry}：{@code core/component/**} 不是本组件的依赖面，
     * 且"组件不该反向依赖容器的具体实现类"是组件化的本意 ✓。
     */
    public interface TaskSink {

        /** 把句柄登记到 {@code requester} 名下。 */
        void track(RoleComponent requester, Task task);

        /** 取消并清空 {@code requester} 名下的全部句柄（返回实际取消数）。 */
        int cancelAll(RoleComponent requester);
    }

    private final Scheduler scheduler;
    private final TaskSink sink;

    public TimerComponent(String id, ComponentServices services, Scheduler scheduler, TaskSink sink) {
        super(id, services);
        this.scheduler = scheduler;
        this.sink = sink;
    }

    /** 下一 tick 执行一次（登记在 {@code requester} 名下）。 */
    public Task run(RoleComponent requester, Runnable task) {
        Task handle = scheduler.run(task);
        sink.track(requesterOf(requester), handle);
        return handle;
    }

    /** 延迟 {@code delayTicks} 刻执行一次（登记在 {@code requester} 名下）。 */
    public Task runLater(RoleComponent requester, long delayTicks, Runnable task) {
        Task handle = scheduler.runLater(task, delayTicks);
        sink.track(requesterOf(requester), handle);
        return handle;
    }

    /** 周期执行（首次延迟 {@code initialDelayTicks}、周期 {@code periodTicks}；登记在 {@code requester} 名下）。 */
    public Task runRepeating(RoleComponent requester, long initialDelayTicks, long periodTicks, Runnable task) {
        //initialDelay <= 0 归一为 1：与 BukkitSchedulerAdapter 同值归一（Math.max 幂等 ⇒ 双入口双保险）
        Task handle = scheduler.runRepeating(task, Math.max(1L, initialDelayTicks), periodTicks);
        sink.track(requesterOf(requester), handle);
        return handle;
    }

    /** 把一个**外部创建**的句柄补登记到 {@code requester} 名下（语义同既有端口）。 */
    public void track(RoleComponent requester, Task handle) {
        if (handle == null) {
            return;
        }
        sink.track(requesterOf(requester), handle);
    }

    /**
     * **取消 {@code requester} 请求的全部任务**。
     * <p>容器在每次组件 {@code stop()} 之后调用它（{@code RoleInstance#triggerLifecycleStop()}）⇒
     * "调用方停止 ⇒ 它的计时全部取消" ✓。
     *
     * @return 实际取消的句柄数（无资源 ⇒ 0）
     */
    public int cancelAllOf(RoleComponent requester) {
        return sink.cancelAll(requesterOf(requester));
    }

    /**
     * 请求者归一：{@code null} ⇒ **本组件自己**（无请求者信息的句柄仍然可回收 ⇒ 不制造"无人认领的任务"）。
     * 端口侧永远给出真实请求者（按组件 id 解析），故本分支只在补全路径上生效。
     */
    private RoleComponent requesterOf(RoleComponent requester) {
        return requester != null ? requester : this;
    }
}
