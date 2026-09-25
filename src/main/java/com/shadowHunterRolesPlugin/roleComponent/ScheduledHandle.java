package com.shadowHunterRolesPlugin.roleComponent;

/**
 * **一个已排定任务的句柄**（组件侧的**最小**契约）。
 *
 * <h2>为什么需要它</h2>
 * 组件排定任务后要能「查是否已取消」与「取消它」。而**任务实现有两个来源**：
 * <ul>
 *   <li>{@code platform/Task}（插件自己的平台端口句柄，容器 ticker 那条路仍在用）；</li>
 *   <li>Paper 的 {@code io.papermc.paper.threadedregions.scheduler.ScheduledTask}
 *       （{@code TaskComponent} 直接调 {@code GlobalRegionScheduler} 得到的那一种）。</li>
 * </ul>
 * 若组件直接依赖其中任一个，就会被迫认识那条实现。本接口把两者收敛成**两个方法**，
 * 于是组件只依赖**组件侧的这一个契约** ✓（与 {@code OperationProvider} 同规：
 * 组件契约落在 {@code roleComponent/} 下，不进 {@code core/}）。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li>{@link #cancel()} 幂等：对已取消 / 已完成的句柄调用**不抛**；</li>
 *   <li>{@link #isCancelled()} 只回答"是否已被取消"，**不回答"是否已跑完"**
 *       （一次性任务跑完后是否算取消由实现决定，调用方不得据此判断"还会不会再跑"）。</li>
 * </ul>
 */
public interface ScheduledHandle {

    /** 取消该任务（幂等）。 */
    void cancel();

    /** 是否已被取消。 */
    boolean isCancelled();
}
