package com.shadowHunterRolesPlugin.platform;

/**
 * 调度端口：领域层只依赖它，不直接碰 Bukkit 调度器。
 * 不预置 runOnEntity —— 当前唯一运行环境是普通 Paper，Folia 分支无法验证（见指南 §3.3 注 ②）。
 *
 * <p><b>⚠ 参数顺序易错点（与 {@code core.ports.TimerPort} 的<b>同名</b>方法恰好相反）</b>：
 * 本接口的 {@code task} 在**前** —— {@code runLater(Runnable task, long delayTicks)}、
 * {@code runRepeating(Runnable task, long initialDelayTicks, long periodTicks)}；
 * 而组件侧的 {@code TimerPort} 是 {@code task} 在**最后** —— {@code runLater(long delayTicks, Runnable task)}、
 * {@code runRepeating(long initialDelayTicks, long periodTicks, Runnable task)}。
 * {@code core.TimerPortImpl} 是两者之间**唯一**的转调点，已在那一行显式交换顺序；
 * 新增调用方时**必须**先确认自己写的是哪一侧的顺序（编译器<b>不会</b>报错：两边都是 {@code long} 与 {@code Runnable} 的合法组合）。
 *
 * <p>实现约定（当前实现 = {@link BukkitSchedulerAdapter}，底层 {@code GlobalRegionScheduler}）：
 * {@code initialDelayTicks <= 0} 由**实现侧归一为 1 tick**（{@code runAtFixedRate} 的硬约束）；
 * {@code core.TimerPortImpl} 侧另有一处同值归一（{@code Math.max} 幂等 ⇒ 双入口无害）
 * ⇒ **调用方无需自行钳制**，直接传业务值即可。
 */
public interface Scheduler {

    Task run(Runnable task);

    Task runLater(Runnable task, long delayTicks);

    Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks);
}
