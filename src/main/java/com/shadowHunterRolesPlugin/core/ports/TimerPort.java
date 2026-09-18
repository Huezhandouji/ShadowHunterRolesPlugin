package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.platform.Task;

/**
 * 协程端口（= Unity 的 {@code StartCoroutine}）：任务登记进**该组件专属**的资源表，
 * {@code stop()} 返回后框架兜底取消全部未取消的任务 —— 组件忘了取消也不会泄漏。
 *
 * <p><b>⚠ 参数顺序易错点（与 {@code platform.Scheduler} 的<b>同名</b>方法恰好相反）</b>：
 * 本接口的 {@code task} 在**最后** —— {@code runLater(long delayTicks, Runnable task)}、
 * {@code runRepeating(long initialDelayTicks, long periodTicks, Runnable task)}；
 * 而平台侧 {@code Scheduler} 是 {@code task} 在**前** —— {@code runLater(Runnable task, long delayTicks)}、
 * {@code runRepeating(Runnable task, long initialDelayTicks, long periodTicks)}。
 * 两者由 {@code core.TimerPortImpl} **唯一**转调（那一行显式交换顺序）。编译器**不会**替你发现顺序写反。
 *
 * <p>边界约定：{@code initialDelayTicks} 传 {@code 0}（= "尽快首次触发"）是**合法**的 ——
 * 归一为 1 tick 由实现侧完成（{@code TimerPortImpl} 与 {@code platform.BukkitSchedulerAdapter} 两处、
 * {@code Math.max} 幂等）；测得的两侧首次触发 tick **相同**（t51 探针 ④m/④p，见
 * {@code debug-logs/测试记录/阶段5-调度器迁移-归一化矩阵-停服物证.txt}）。
 */
public interface TimerPort {

    Task run(Runnable task);

    Task runLater(long delayTicks, Runnable task);

    Task runRepeating(long initialDelayTicks, long periodTicks, Runnable task);

    /** 登记一个已由别处创建的句柄，使其纳入本组件的资源表。 */
    void track(Task task);
}
