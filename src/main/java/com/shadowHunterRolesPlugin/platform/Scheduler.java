package com.shadowHunterRolesPlugin.platform;

/**
 * 调度端口：领域层只依赖它，不直接碰 Bukkit 调度器。
 * 不预置 runOnEntity / RegionScheduler / EntityScheduler —— 服务端仍为 Paper，不做 Folia 适配
 * （既不加 Folia 分支 / 守卫，也不做 Folia 真机验证）。当前实现 = Paper 提供的
 * {@code GlobalRegionScheduler}（Paper 自己实现的同一套 API，非 Folia 专属）。
 *
 * <p><b>参数顺序（当前只有一种）</b>：本接口的 {@code task} 在前 ——
 * {@code runLater(Runnable task, long delayTicks)}、
 * {@code runRepeating(Runnable task, long initialDelayTicks, long periodTicks)}；
 * 组件侧经计时组件调用时，请求者同样在首位（{@code runLater(requester, delayTicks, task)} 一形），
 * 两侧顺序一致，不存在"写错侧"的易错点。
 * 新增调用方时仍建议确认一次自己写的是哪一侧的重载（两边都存在 {@code long} 与 {@code Runnable}
 * 的合法组合，编译器不会替你区分）。
 *
 * <p>实现约定（当前实现 = {@link BukkitSchedulerAdapter}，底层 {@code GlobalRegionScheduler}）：
 * {@code initialDelayTicks <= 0} 由实现侧归一为 {@code Math.max(1, …)}，理由 = {@code runAtFixedRate} 对
 * {@code initialDelay <= 0} 直接抛 {@code IllegalArgumentException}
 * （原文：{@code Initial delay ticks may not be <= 0}），而
 * {@code BukkitScheduler.runTaskTimer(…, 0L, …)} 合法；
 * 该归一不构成可见差异：原生 Bukkit 的 {@code 0L} 语义就是"下一个 tick 首次执行"，与归一后的 {@code 1L} 同 tick
 * （八组声明值下两条路径的首 / 次触发 tick 逐字相同，已实测）。
 * 因此调用方无需自行钳制，直接传业务值即可。
 */
public interface Scheduler {

    Task run(Runnable task);

    Task runLater(Runnable task, long delayTicks);

    Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks);
}
