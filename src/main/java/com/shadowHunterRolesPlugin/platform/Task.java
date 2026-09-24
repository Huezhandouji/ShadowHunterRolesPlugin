package com.shadowHunterRolesPlugin.platform;

/**
 * 调度句柄：{@code platform} 层对底层调度任务句柄的**抽象**（调用方只依赖 {@link #cancel()} / {@link #isCancelled()}）。
 *
 * <p><b>现状口径（修订）</b>：本接口是**底层调度任务句柄的抽象**——由适配器统一包装
 * **Paper 提供的 {@code GlobalRegionScheduler} 所返回的 {@code ScheduledTask}**；**不再直接包装 {@code BukkitTask}**。
 * 唯一实现 = {@link BukkitSchedulerAdapter}（{@code GlobalRegionScheduler}，**宿主仍为 Paper**）；
 * **服务端仍为 Paper，不做 Folia 适配**（不引入 Region/Entity 调度器、不加分支或守卫）。
 * 两类句柄的形态差异在本接口边界内被吸收：
 * <ul>
 *   <li>{@link #cancel()} —— 调 {@code ScheduledTask.cancel()} 并**吞掉其 {@code CancelledState} 返回值**
 *       （{@code BukkitTask.cancel()} 则返回 {@code void}）⇒ 本签名保持 {@code void}，**调用方零改动** ✓；</li>
 *   <li>{@link #isCancelled()} —— **直接委托** {@code ScheduledTask.isCancelled()}（该方法是 default 实现）。</li>
 * </ul>
 *
 * <p>取消语义已实测（探针原始输出见 {@code debug-logs/测试记录/}）：首次 {@code cancel()} →
 * {@code NEXT_RUNS_CANCELLED}、重复 {@code cancel()} → {@code NEXT_RUNS_CANCELLED_ALREADY}（无异常 = 幂等），
 * {@code isCancelled()} 由 {@code false} 变 {@code true}。
 * <p>说明（不含原句，避免 grep 假阳性）：早期文档对本接口的包装对象有一处描述已被上面口径取代；
 * 早期文本提到的 {@code org.bukkit.scheduler.Cancellable} 在本版 paper-api **确实不存在**（已用 {@code javap} 复核）。
 */
public interface Task {

    void cancel();

    boolean isCancelled();
}
