package com.shadowHunterRolesPlugin.platform;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** 普通 Paper 上的 {@link Scheduler} 实现：把 BukkitTask 包成 {@link Task} 句柄。 */
public final class BukkitSchedulerAdapter implements Scheduler {

    private final Plugin plugin;

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Task run(Runnable task) {
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public Task runLater(Runnable task, long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks));
    }

    private static Task wrap(BukkitTask bukkitTask) {
        return new Task() {
            @Override
            public void cancel() {
                bukkitTask.cancel();
            }

            @Override
            public boolean isCancelled() {
                return bukkitTask.isCancelled();
            }
        };
    }
}
