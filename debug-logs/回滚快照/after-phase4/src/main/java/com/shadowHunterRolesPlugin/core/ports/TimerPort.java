package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.platform.Task;

/**
 * 协程端口（= Unity 的 {@code StartCoroutine}）：任务登记进**该组件专属**的资源表，
 * {@code stop()} 返回后框架兜底取消全部未取消的任务 —— 组件忘了取消也不会泄漏。
 */
public interface TimerPort {

    Task run(Runnable task);

    Task runLater(long delayTicks, Runnable task);

    Task runRepeating(long initialDelayTicks, long periodTicks, Runnable task);

    /** 登记一个已由别处创建的句柄，使其纳入本组件的资源表。 */
    void track(Task task);
}
