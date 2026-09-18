package com.shadowHunterRolesPlugin.platform;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 普通 Paper / Folia 上的 {@link Scheduler} 实现：**以 {@link GlobalRegionScheduler} 为唯一实现**（Folia-ready），
 * 把 {@link ScheduledTask} 包成 {@link Task} 句柄。
 *
 * <p>接口面（{@link Scheduler} / {@link Task}）与全部调用方**零改动**：两者句柄的差异
 * （{@code ScheduledTask.cancel()} 返回 {@code CancelledState} 而非 {@code void}）在本类内吞掉。
 *
 * <p>迁移依据 = t51 实证探针的原始输出（`debug-logs/测试记录/阶段4-schedprobe-停服物证.txt` §3.3）：
 * 回调线程（{@code Server thread}）、周期实际触发（声明 1/10 ⇒ 1/11/21）、延时段（声明 20 ⇒ 20）
 * 与 {@code BukkitScheduler} 的对照任务**逐字相同**；取消语义等价
 * （{@code NEXT_RUNS_CANCELLED} → 重复 {@code NEXT_RUNS_CANCELLED_ALREADY}、{@code isCancelled()=true}、
 * {@code getExecutionState()=CANCELLED_RUNNING}、重复 cancel 无异常 = 幂等）。
 *
 * <p><b>唯一差异（已申报）</b>：{@code runAtFixedRate} 拒绝 {@code initialDelayTicks <= 0}
 * （{@code IllegalArgumentException: Initial delay ticks may not be <= 0}），而
 * {@code BukkitScheduler.runTaskTimer(…, 0L, …)} 接受 0。生产侧有 **3 个调用点传 0**
 * （{@code BuffManager} / {@code DefaultSanTEZeroPunishment} / {@code MeiqiheziBloodySlashSkill}）⇒
 * 本类把 {@code initialDelayTicks} **归一为 1 tick**（{@code Math.max(1L, …)}）；首触发相位最多相差 1 tick、
 * 周期不受影响 —— 探针 ③c/③d 段对**同一生产声明值 0/10**做 A/B 实测（原生 Bukkit vs 本适配器），
 * 首次与第三次触发的 tick **已由实测确认逐字相同**。
 */
public final class BukkitSchedulerAdapter implements Scheduler {

    private final Plugin plugin;

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Task run(Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run()));
    }

    @Override
    public Task runLater(Runnable task, long delayTicks) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks));
    }

    @Override
    public Task runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        //initialDelay <= 0 归一为 1：GlobalRegionScheduler 的硬约束（见类 javadoc「唯一差异（已申报）」）。
        //用 run(...) 取代 runAtFixedRate(...) 会丢掉 ScheduledTask 句柄 ⇒ 任务无法回收，故不可取。
        long normalisedInitialDelay = Math.max(1L, initialDelayTicks);
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, scheduledTask -> task.run(), normalisedInitialDelay, periodTicks));
    }

    private static Task wrap(ScheduledTask scheduledTask) {
        return new Task() {
            @Override
            public void cancel() {
                scheduledTask.cancel(); //CancelledState 返回值在此吞掉（Task 接口签名为 void）
            }

            @Override
            public boolean isCancelled() {
                return scheduledTask.isCancelled();
            }
        };
    }
}
