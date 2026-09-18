package com.shadowHunterRolesPlugin.platform;

/**
 * 调度句柄：统一包装 {@code BukkitTask} / Folia {@code ScheduledTask}
 * （两者无共同父类型，故不能直接用 org.bukkit.scheduler.Cancellable —— 该类型在本版 API 中不存在）。
 */
public interface Task {

    void cancel();

    boolean isCancelled();
}
