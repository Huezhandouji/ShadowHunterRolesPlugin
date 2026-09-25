package com.shadowHunterRolesPlugin.platform;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 普通 Paper 上的 {@link Scheduler} 实现：**以 Paper 提供的 {@link GlobalRegionScheduler} 为唯一实现**
 * （宿主仍为 Paper；**本工程不做 Folia 适配** ✗ —— 该 API 是 Paper 自己实现的同一套接口，并非 Folia 专属），
 * 把 {@link ScheduledTask} 包成 {@link Task} 句柄。
 *
 * <p>接口面（{@link Scheduler} / {@link Task}）与全部调用方**零改动**：两者句柄的差异
 * （{@code ScheduledTask.cancel()} 返回 {@code CancelledState} 而非 {@code void}）在本类内吞掉。
 *
 * <p>迁移依据 = 调度器实证探针的原始输出（物证件 §3.3）：
 * 回调线程（{@code Server thread}）、周期实际触发（声明 1/10 ⇒ 1/11/21）、延时段（声明 20 ⇒ 20）
 * 与 {@code BukkitScheduler} 的对照任务**逐字相同**；取消语义等价
 * （{@code NEXT_RUNS_CANCELLED} → 重复 {@code NEXT_RUNS_CANCELLED_ALREADY}、{@code isCancelled()=true}、
 * {@code getExecutionState()=CANCELLED_RUNNING}、重复 cancel 无异常 = 幂等）。
 *
 * <p><b>与原生 API 的唯一差异（已申报，且已实测"无相位差"）</b>：{@code runAtFixedRate} 拒绝 {@code initialDelayTicks <= 0}
 * （{@code IllegalArgumentException: Initial delay ticks may not be <= 0}），而
 * {@code BukkitScheduler.runTaskTimer(…, 0L, …)} 接受 0。生产侧有 **3 个调用点传 0**
 * （{@code BuffManager} / {@code DefaultSanTEZeroPunishment} / {@code MeiqiheziBloodySlashSkill}）⇒
 * 本类把 {@code initialDelayTicks} **归一为 1 tick**（{@code Math.max(1L, …)}）。
 * <p><b>该归一不产生相位差（经实测未见偏移）</b>：原生 Bukkit 的 {@code 0L} 语义本就是"**下一个 tick 首次执行**" ⇒
 * **归一后首个触发 tick 与原生 {@code runTaskTimer(0L, …)} 逐字相同**（已由探针矩阵实测：
 * **8 组声明值 × 两条路径的首/次触发 tick 全等** —— (0,1)=1/2、(0,2)=1/3、(0,10)=1/11、(0,40)=1/41、
 * (1,1)=1/2、(1,2)=1/3、(1,6)=1/7、(1,10)=1/11）；周期、延时、线程与取消语义亦逐组逐字相同。
 * <p><b>口径（两说并存）</b>：上述**实测结论进证据** ✓；同时本项**按保守口径仍登记为"已申报项"** ✓
 * —— 即"经实测未见偏移，但不把它当作不可观测的等价项"。前提声明：当前实测仅覆盖 **Paper**（宿主 = Paper）；
 * 若未来在其它平台出现"声明 {@code 0L} 与 {@code 1L} 首触发不同"的实证 ⇒ **须重新申报** ✗。
 * <p>（说明：本注释此前对**归一的相位影响**有过一处论证性表述，该说法已由归一化矩阵**实测推翻并回收** ✗ ——
 * 此处**不复述原句**，以免裸 grep 把它误读为现状口径。）
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
