package com.shadowHunterRolesPlugin.platform;

/**
 * 调度端口：领域层只依赖它，不直接碰 Bukkit 调度器。
 * 不预置 runOnEntity —— 当前唯一运行环境是普通 Paper，Folia 分支无法验证（见指南 §3.3 注 ②）。
 */
public interface Scheduler {

    Task run(Runnable task);

    Task runLater(Runnable task, long delayTicks);

    Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks);
}
