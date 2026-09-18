package com.shadowHunterRolesPlugin.platform;

/**
 * 调度端口：领域层只依赖它，不直接碰 Bukkit 调度器。
 * 不预置 runOnEntity / RegionScheduler / EntityScheduler —— **用户裁定：服务端仍为 Paper，不做 Folia 适配** ✗
 * （既不加 Folia 分支/守卫，也不做 Folia 真机验证；见指南 §3.3 注 ②）。当前实现 = Paper 提供的
 * {@code GlobalRegionScheduler}（Paper 自己实现的同一套 API，**非 Folia 专属**）。
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
 * {@code initialDelayTicks <= 0} 由**实现侧归一为 {@code Math.max(1, …)}** —— 理由 = {@code runAtFixedRate} 对
 * {@code initialDelay <= 0} **直接抛 {@code IllegalArgumentException}**（探针实测原文：{@code Initial delay ticks may not be <= 0}），
 * 而 {@code BukkitScheduler.runTaskTimer(…, 0L, …)} 合法；
 * 该归一**不构成可见差异**：原生 Bukkit 的 {@code 0L} 语义就是"**下一个 tick 首次执行**"，与归一后的 {@code 1L} **同 tick**
 * （t51 探针 ④m：八组声明值两条路径的首/次触发 tick **逐字相同**，已实测）。
 * {@code core.TimerPortImpl} 侧另有一处同值归一（{@code Math.max} 幂等 ⇒ 双入口无害）
 * ⇒ **调用方无需自行钳制**，直接传业务值即可。
 */
public interface Scheduler {

    Task run(Runnable task);

    Task runLater(Runnable task, long delayTicks);

    Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks);
}
