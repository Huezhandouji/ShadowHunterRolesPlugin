package com.shadowHunterRolesPlugin.platform;

/**
 * 调度句柄：{@code platform} 层对底层调度任务句柄的**抽象**（调用方只依赖 {@link #cancel()} / {@link #isCancelled()}）。
 *
 * <p><b>现状口径（t53 修订）</b>：当前唯一实现 = {@link BukkitSchedulerAdapter}（底层 {@code GlobalRegionScheduler}）
 * ⇒ 本接口包装的是**Folia 语义的 {@code ScheduledTask}**，**不再直接包装 {@code BukkitTask}**
 * （早期文档"统一包装 {@code BukkitTask} / Folia {@code ScheduledTask}（两者无共同父类型）"的表述**已作废** ✗）。
 * 两者句柄形态不同，其差异在本接口边界内被吸收：
 * <ul>
 *   <li>{@link #cancel()} —— 调 {@code ScheduledTask.cancel()} 并**吞掉其 {@code CancelledState} 返回值**
 *       （{@code BukkitTask.cancel()} 则返回 {@code void}）⇒ 本签名保持 {@code void}，**调用方零改动** ✓；</li>
 *   <li>{@link #isCancelled()} —— **直接委托** {@code ScheduledTask.isCancelled()}（该方法是 default 实现）。</li>
 * </ul>
 *
 * <p>取消语义已实测（t51 探针原始输出见 {@code debug-logs/测试记录/}）：首次 {@code cancel()} →
 * {@code NEXT_RUNS_CANCELLED}、重复 {@code cancel()} → {@code NEXT_RUNS_CANCELLED_ALREADY}（无异常 = 幂等），
 * {@code isCancelled()} 由 {@code false} 变 {@code true}。
 * <p>注：早期文本提到的 {@code org.bukkit.scheduler.Cancellable} 在本版 paper-api **确实不存在**（已用 {@code javap} 复核）。
 */
public interface Task {

    void cancel();

    boolean isCancelled();
}
