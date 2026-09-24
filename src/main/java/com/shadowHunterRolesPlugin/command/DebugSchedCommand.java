package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import com.shadowHunterRolesPlugin.platform.Task;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 子指令 {@code debug sched}：{@code /role debug sched [all]} —— **调度器实证探针**。
 * <p>
 * 入口与输出（自 t59 起为正式子指令；原先是 {@code RoleCommand} 里的内联临时入口，用户要求**整合而非删除**）：
 * **不新增注册路径、不在 onEnable 做常驻副作用**；全部探针任务在第 3 次触发时自取消 ⇒ 单次命令不留常驻任务
 * （状态全部是本方法局部变量）。**op 门控由 {@link DebugCommand} 在调试树入口承担**（可见行为与原内联实现相同）。
 * <p>
 * 逐条打印原始值：
 * ① {@code execute} 回调内线程名；
 * ② {@code run / runDelayed / runAtFixedRate} 的线程名 + 实际触发 tick（与声明值并排）+ {@code initialDelay=0}
 * 边界实测（{@code runAtFixedRate} vs {@code runTaskTimer}，异常原文照打）；
 * ③ 同周期 {@code Bukkit.getScheduler().runTaskTimer} 对照任务的线程名 + 实际触发 tick（声明值逐字相同）
 * + ③b {@code runTaskLater} 延时对照；
 * ③c/③d **新实现验证**：对**生产声明值 0/10** 做 A/B —— ③c 原生 Bukkit {@code runTaskTimer(0,10)} vs
 * ③d 生产适配器 {@code BukkitSchedulerAdapter.runRepeating(0,10)}（内含 0→1 归一）；另测适配器
 * {@code run()} / {@code runLater(20)} 与 {@code Task} 句柄取消；
 * ④m **归一化矩阵**：8 组声明值（5 个生产形态 (1,1)/(0,1)/(0,2)/(0,40)/(1,6) + (0,10)/(1,2)/(1,10)）
 *     分别走原生 Bukkit 与适配器，逐组打印首/次触发 tick ⇒ 判据 = 两路径首/次触发逐字相同；
 * ④p **端口链**：走组件侧真入口（**经服务集端口的转发形态**：`runRepeating(0L, 10L, …)`，请求者在**末位**）
 *     ⇒ 覆盖 TimerPortImpl 的转调 + 双入口归一；与 ④m 的 (0,10)=1/11 比对（无角色时打印 SKIPPED）；
 * ④ {@code ScheduledTask.cancel()} 返回值 / {@code isCancelled()} / {@code getExecutionState()} / 重复 cancel；
 * ⑤ 一句话结论（由本次原始值现算，不只给结论）。
 * <p>
 * <b>关键输出行是既有证据的引用锚</b>（如 {@code [sched] ⑤ verdict | …} 里的 {@code matrixAllPairsMatch=}、
 * {@code portLegEquivalent=}、{@code CONCLUSION=}），迁移时**逐字保留**，不得改写键名或措辞。
 */
public class DebugSchedCommand implements SubCommand {

    private final RoleManager roleManager;

    public DebugSchedCommand(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    @Override
    public String getName(){
        return "sched";
    }

    @Override
    public String getUsage(){
        return "Usage: /role debug sched [all]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)) return true;

        if(args.length >= 1 && !"all".equals(args[0])){
            sendKey(player, getUsage());
            return true;
        }

        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if(plugin == null){
            sendKey(player, "[sched] plugin instance unavailable; probe aborted.");
            return true;
        }

        //观测位（局部）：str = 线程名/取消原始值/边界值；tick = 实际触发 tick 相对命令 tick 的差值
        final String[] str = new String[9];
        final int[] tick = new int[13];
        for(int i = 0; i < tick.length; i++) tick[i] = -1;
        final int[] grrCount = {0};
        final int[] bukkitCount = {0};
        final int baseTick = Bukkit.getCurrentTick();
        final GlobalRegionScheduler grs = Bukkit.getGlobalRegionScheduler();
        final BukkitTask[] bukkitHolder = new BukkitTask[1];

        sendKey(player, "[sched] probe start | commandThread=" + Thread.currentThread().getName()
                + " | commandTick=" + baseTick + " | primaryThread=" + Bukkit.isPrimaryThread()
                + " | declared: runDelayed=20t, runAtFixedRate=1/10t, bukkit runTaskTimer=1/10t, boundary=0/10t");

        //① execute(Plugin, Runnable)：无返回值/无句柄 ⇒ 只测线程与相位
        grs.execute(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            sendKey(player, "[sched] ① execute | thread=" + Thread.currentThread().getName()
                    + " | tick=" + now + " | delta=" + (now - baseTick)
                    + " | primaryThread=" + Bukkit.isPrimaryThread());
        });

        //② run(Plugin, Consumer<ScheduledTask>)：句柄可达性
        grs.run(plugin, task -> {
            int now = Bukkit.getCurrentTick();
            str[0] = Thread.currentThread().getName();
            player.sendMessage(Component.text("[sched] ② run | thread=" + Thread.currentThread().getName()
                    + " | tick=" + now + " | delta=" + (now - baseTick)
                    + " | owningPluginIsUs=" + (task.getOwningPlugin() == plugin)
                    + " | isRepeatingTask=" + task.isRepeatingTask()
                    + " | getExecutionState=" + task.getExecutionState()
                    + " | isCancelled=" + task.isCancelled()));
        });

        //② runDelayed(..., 20L)
        grs.runDelayed(plugin, task -> {
            int now = Bukkit.getCurrentTick();
            tick[0] = now - baseTick;
            player.sendMessage(Component.text("[sched] ② runDelayed | declaredDelay=20 | tick=" + now
                    + " | delta=" + tick[0] + " | thread=" + Thread.currentThread().getName()
                    + " | isRepeatingTask=" + task.isRepeatingTask()
                    + " | getExecutionState=" + task.getExecutionState()
                    + " | isCancelled=" + task.isCancelled()));
        }, 20L);

        //② 边界实测：runAtFixedRate 的 initialDelay 允许范围（0 = 生产调用点实际传入值；try/catch 就地取异常原文，不抛给命令层）
        try {
            ScheduledTask zeroGlobal = grs.runAtFixedRate(plugin, t -> { }, 0L, 10L);
            zeroGlobal.cancel();
            str[4] = "initialDelay0=ACCEPTED";
        }
        catch (IllegalArgumentException e){
            str[4] = "initialDelay0=REJECTED(" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
        try {
            BukkitTask zeroBukkit = Bukkit.getScheduler().runTaskTimer(plugin, () -> { }, 0L, 10L);
            zeroBukkit.cancel();
            str[5] = "initialDelay0=ACCEPTED";
        }
        catch (IllegalArgumentException e){
            str[5] = "initialDelay0=REJECTED(" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
        sendKey(player, "[sched] ② boundary | GlobalRegionScheduler.runAtFixedRate(0,10) -> " + str[4]
                + " | Bukkit.getScheduler().runTaskTimer(0,10) -> " + str[5]);

        //② runAtFixedRate(..., 1L, 10L)：打印 #1/#2/#3 实际触发 tick；第 3 次自取消并打印 ④ 取消语义
        grs.runAtFixedRate(plugin, task -> {
            int now = Bukkit.getCurrentTick();
            grrCount[0]++;
            int n = grrCount[0];
            if(n == 1) tick[1] = now - baseTick;
            if(n == 2) tick[2] = now - baseTick;
            if(n == 3) tick[3] = now - baseTick;
            player.sendMessage(Component.text("[sched] ② runAtFixedRate #" + n
                    + " | declared initialDelay=1 period=10 | tick=" + now + " | delta=" + (now - baseTick)
                    + " | thread=" + Thread.currentThread().getName()
                    + " | isRepeatingTask=" + task.isRepeatingTask()
                    + " | getExecutionState=" + task.getExecutionState()
                    + " | isCancelled=" + task.isCancelled()));
            if(n == 3){
                ScheduledTask.CancelledState cancel1 = task.cancel();
                boolean cancelled1 = task.isCancelled();
                ScheduledTask.ExecutionState state1 = task.getExecutionState();
                ScheduledTask.CancelledState cancel2 = task.cancel();
                boolean cancelled2 = task.isCancelled();
                ScheduledTask.ExecutionState state2 = task.getExecutionState();
                str[2] = "cancel1=" + cancel1 + ",isCancelled1=" + cancelled1 + ",state1=" + state1
                        + ",cancel2=" + cancel2 + ",isCancelled2=" + cancelled2 + ",state2=" + state2;
                sendKey(player, "[sched] ④ cancel() #1 -> " + cancel1
                        + " | isCancelled()=" + cancelled1 + " | getExecutionState()=" + state1);
                sendKey(player, "[sched] ④ cancel() #2 (repeat) -> " + cancel2
                        + " | isCancelled()=" + cancelled2 + " | getExecutionState()=" + state2
                        + " | no exception = idempotent");
            }
        }, 1L, 10L);

        //③ 对照：同周期 Bukkit 任务，**声明值与全局侧逐字相同**（delay=1 period=10；BukkitTask 句柄；第 3 次自取消）
        bukkitHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            bukkitCount[0]++;
            int n = bukkitCount[0];
            if(n == 1) tick[4] = now - baseTick;
            if(n == 2) tick[5] = now - baseTick;
            if(n == 3) tick[6] = now - baseTick;
            str[1] = Thread.currentThread().getName();
            player.sendMessage(Component.text("[sched] ③ bukkit runTaskTimer #" + n
                    + " | declared delay=1 period=10 | tick=" + now + " | delta=" + (now - baseTick)
                    + " | thread=" + Thread.currentThread().getName()));
            if(n == 3){
                boolean before = bukkitHolder[0].isCancelled();
                bukkitHolder[0].cancel();
                boolean after = bukkitHolder[0].isCancelled();
                str[3] = "isCancelledBefore=" + before + ",isCancelledAfter=" + after;
                sendKey(player, "[sched] ④(BukkitTask) cancel() -> void | isCancelled() before="
                        + before + " | after=" + after + " | no exception = idempotent");
            }
        }, 1L, 10L);

        //③b 延时段对照：runDelayed(20) vs runTaskLater(20)（声明值相同；一次性任务，无残留）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            tick[7] = now - baseTick;
            player.sendMessage(Component.text("[sched] ③b bukkit runTaskLater | declaredDelay=20 | tick=" + now
                    + " | delta=" + tick[7] + " | thread=" + Thread.currentThread().getName()));
        }, 20L);

        //③c 对照（**生产声明值 0/10** 的原生 Bukkit 行为）：runTaskTimer(0,10) → 首次与第三次触发 tick
        final int[] rawZeroCount = {0};
        final BukkitTask[] rawZeroHolder = new BukkitTask[1];
        rawZeroHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            rawZeroCount[0]++;
            if(rawZeroCount[0] == 1) tick[8] = now - baseTick;
            player.sendMessage(Component.text("[sched] ③c raw bukkit runTaskTimer #" + rawZeroCount[0]
                    + " | declared delay=0 period=10 | tick=" + now + " | delta=" + (now - baseTick)
                    + " | thread=" + Thread.currentThread().getName()));
            if(rawZeroCount[0] == 3){
                tick[9] = now - baseTick;
                rawZeroHolder[0].cancel();
            }
        }, 0L, 10L);

        //③d **新实现验证**：直接调**生产适配器**（BukkitSchedulerAdapter = GlobalRegionScheduler 唯一实现），
        //    与 ③c 用同一生产声明值 0/10 做 A/B（适配器内部把 0 归一为 1）；另测 run() / runLater(20) 与 Task 句柄取消
        final BukkitSchedulerAdapter probeAdapter = new BukkitSchedulerAdapter(plugin);
        probeAdapter.run(() -> {
            int now = Bukkit.getCurrentTick();
            player.sendMessage(Component.text("[sched] ③d adapter.run() | thread=" + Thread.currentThread().getName()
                    + " | tick=" + now + " | delta=" + (now - baseTick)));
        });
        probeAdapter.runLater(() -> {
            int now = Bukkit.getCurrentTick();
            tick[12] = now - baseTick;
            player.sendMessage(Component.text("[sched] ③d adapter.runLater(20) | declaredDelay=20 | tick=" + now
                    + " | delta=" + tick[12] + " | thread=" + Thread.currentThread().getName()));
        }, 20L);
        final int[] adapterCount = {0};
        final Task[] adapterHolder = new Task[1];
        adapterHolder[0] = probeAdapter.runRepeating(() -> {
            int now = Bukkit.getCurrentTick();
            adapterCount[0]++;
            if(adapterCount[0] == 1) tick[10] = now - baseTick;
            player.sendMessage(Component.text("[sched] ③d adapter.runRepeating #" + adapterCount[0]
                    + " | declared initialDelay=0 period=10 (adapter normalises 0 -> 1) | tick=" + now
                    + " | delta=" + (now - baseTick) + " | thread=" + Thread.currentThread().getName()));
            if(adapterCount[0] == 3){
                tick[11] = now - baseTick;
                boolean before = adapterHolder[0].isCancelled();
                adapterHolder[0].cancel();
                boolean after = adapterHolder[0].isCancelled();
                adapterHolder[0].cancel();
                str[6] = "isCancelledBefore=" + before + ",isCancelledAfter=" + after
                        + ",isCancelledAfterSecondCancel=" + adapterHolder[0].isCancelled();
                sendKey(player, "[sched] ③d adapter Task.cancel() -> void | " + str[6]
                        + " | repeated cancel: no exception = idempotent");
            }
        }, 0L, 10L);

        //④m 归一化矩阵（队长裁定要求的实测）：8 组声明值 —— 含**全部 5 个生产形态** (1,1)/(0,1)/(0,2)/(0,40)/(1,6)
        //    与队长网格 (0,10)/(1,2)/(1,10)。每组**用同一声明值分别走原生 Bukkit 与生产适配器**，
        //    打印各自第 1/第 2 次触发 tick ⇒ 判据 = 两路径首/次触发**逐字相同**（第 2 次还须 > 第 1 次）
        final long[][] mPairs = {{0,1},{0,2},{0,10},{0,40},{1,1},{1,2},{1,6},{1,10}};
        final int[][] mTicks = new int[8][4];
        final int[] mCount = new int[16];
        final BukkitTask[] mRawHolders = new BukkitTask[8];
        final Task[] mAdpHolders = new Task[8];
        for(int i = 0; i < mPairs.length; i++){
            final int idx = i;
            final long d = mPairs[i][0];
            final long p = mPairs[i][1];
            mRawHolders[idx] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                int now = Bukkit.getCurrentTick();
                mCount[idx * 2]++;
                int k = mCount[idx * 2];
                if(k <= 2){
                    mTicks[idx][k - 1] = now - baseTick;
                    player.sendMessage(Component.text("[sched] ④m rawBukkit delay=" + d + " period=" + p + " #" + k
                            + " | tick=" + now + " | delta=" + (now - baseTick)));
                }
                if(k == 2) mRawHolders[idx].cancel();
            }, d, p);
            mAdpHolders[idx] = probeAdapter.runRepeating(() -> {
                int now = Bukkit.getCurrentTick();
                mCount[idx * 2 + 1]++;
                int k = mCount[idx * 2 + 1];
                if(k <= 2){
                    mTicks[idx][k + 1] = now - baseTick;
                    player.sendMessage(Component.text("[sched] ④m adapter   delay=" + d + " period=" + p + " #" + k
                            + " | tick=" + now + " | delta=" + (now - baseTick)));
                }
                if(k == 2) mAdpHolders[idx].cancel();
            }, d, p);
        }
        grs.runDelayed(plugin, task -> {
            DebugCommand.log("[sched] ④m matrix: per-row tick detail for the 8 declared pairs x 2 paths stays player-side only; the key verdict line follows");
            boolean matrixAllMatch = true;
            for(int i = 0; i < mPairs.length; i++){
                boolean firstIdentical = mTicks[i][0] == mTicks[i][2] && mTicks[i][0] > 0;
                boolean secondIdentical = mTicks[i][1] == mTicks[i][3] && mTicks[i][1] > mTicks[i][0];
                if(!firstIdentical || !secondIdentical) matrixAllMatch = false;
                player.sendMessage(Component.text("[sched] ④m pair delay=" + mPairs[i][0] + " period=" + mPairs[i][1]
                        + " | rawBukkitFirstSecond=" + mTicks[i][0] + "/" + mTicks[i][1]
                        + " | adapterFirstSecond=" + mTicks[i][2] + "/" + mTicks[i][3]
                        + " | firstIdentical=" + firstIdentical + " | secondIdentical=" + secondIdentical));
            }
            str[7] = "matrixAllPairsMatch=" + matrixAllMatch;
            sendKey(player, "[sched] ④m verdict | " + str[7]);
        }, 50L);

        //④p **端口链实测**（规格 B③"双入口归一"：TimerPortImpl → Scheduler → 适配器 → GlobalRegionScheduler）：
        //    走组件侧真入口（**经服务集端口的转发形态**：请求者在**末位**）⇒
        //    与 ④m 的 (0,10) 期望值 **1/11** 比对。**仅当玩家已有角色时可测**（服务集构造期注入）；
        //    无角色 ⇒ 明确打印 SKIPPED（不伪造）
        final int[] portTicks = {-1, -1};
        final int[] portCount = {0};
        final Task[] portHolder = new Task[1];
        ComponentServices portServices = null;
        if(roleManager.hasRole(player)){
            RoleInstance portInstance = roleManager.getRoleInstance(player);
            String[] candidates = {"autoRecoverEnergy_passive", "autoRecoverSanTEPassive", "default_san_te_zero_punishment",
                    "meiqihezi_mainWeapon_juejue", "meiqihezi_equippments_passive", "red_equippments_passive"};
            if(portInstance != null){
                for(String candidate : candidates){
                    if(portInstance.servicesOf(candidate) != null){
                        portServices = portInstance.servicesOf(candidate);
                        str[8] = candidate;
                        break;
                    }
                }
            }
        }
        if(portServices == null){
            str[8] = "portLeg=SKIPPED(no role or no bound services)";
            sendKey(player, "[sched] ④p port leg | " + str[8]);
        }
        else{
            final ComponentServices portSvc = portServices;
            portHolder[0] = portSvc.timers().runRepeating(0L, 10L, () -> {
                int now = Bukkit.getCurrentTick();
                portCount[0]++;
                int k = portCount[0];
                if(k <= 2){
                    portTicks[k - 1] = now - baseTick;
                    player.sendMessage(Component.text("[sched] ④p port(TimerPortImpl) delay=0 period=10 #" + k
                            + " | tick=" + now + " | delta=" + (now - baseTick) + " | component=" + str[8]));
                }
                if(k == 2){
                    portHolder[0].cancel();
                    str[8] = str[8] + ",portFirstSecond=" + portTicks[0] + "/" + portTicks[1];
                    sendKey(player, "[sched] ④p port leg | component=" + str[8]
                            + " | expectedByMatrix(0,10)=1/11 | identical=" + (portTicks[0] == 1 && portTicks[1] == 11));
                }
            });
        }

        //⑤ 结论：70 tick 后（全部周期任务均已自取消）以本次原始值现算"能否直切"
        grs.runDelayed(plugin, task -> {
            int globalPeriod = (tick[1] >= 0 && tick[3] >= 0) ? tick[3] - tick[1] : -1;
            int bukkitPeriod = (tick[4] >= 0 && tick[6] >= 0) ? tick[6] - tick[4] : -1;
            boolean sameThread = str[0] != null && str[0].equals(str[1]);
            boolean periodEquivalent = globalPeriod > 0 && globalPeriod == bukkitPeriod;
            boolean delayedEquivalent = tick[0] >= 0 && tick[0] == tick[7];
            boolean cancelSemanticsOk = str[2] != null && str[2].contains("isCancelled1=true");
            boolean rawInitialDelayPolicySame = str[4] != null && str[5] != null
                    && str[4].startsWith("initialDelay0=ACCEPTED") == str[5].startsWith("initialDelay0=ACCEPTED");
            boolean clampEquivalent = tick[8] >= 0 && tick[9] >= 0 && tick[8] == tick[10] && tick[9] == tick[11];
            boolean adapterDelayedEquivalent = tick[12] >= 0 && tick[12] == tick[0];
            boolean adapterCancelOk = str[6] != null && str[6].contains("isCancelledAfter=true")
                    && str[6].contains("isCancelledAfterSecondCancel=true");
            boolean matrixEquivalent = str[7] != null && str[7].contains("matrixAllPairsMatch=true");
            boolean portLegCovered = str[8] != null && str[8].contains("portFirstSecond=");
            boolean portLegEquivalent = portLegCovered && str[8].endsWith("portFirstSecond=1/11");
            boolean adapterEquivalent = sameThread && periodEquivalent && delayedEquivalent && cancelSemanticsOk
                    && clampEquivalent && adapterDelayedEquivalent && adapterCancelOk && matrixEquivalent
                    && (!portLegCovered || portLegEquivalent);
            sendKey(player, "[sched] ⑤ raw | globalThread=" + str[0]
                    + " | bukkitThread=" + str[1]
                    + " | globalDeltas=" + tick[1] + "/" + tick[2] + "/" + tick[3] + " (declared 1/10)"
                    + " | bukkitDeltas=" + tick[4] + "/" + tick[5] + "/" + tick[6] + " (declared 1/10)"
                    + " | globalRunDelayedDelta=" + tick[0] + " | bukkitRunTaskLaterDelta=" + tick[7] + " (declared 20)"
                    + " | adapterRunLaterDelta=" + tick[12]
                    + " | A/B(declared 0/10) rawBukkitFirstThird=" + tick[8] + "/" + tick[9]
                    + " adapterFirstThird=" + tick[10] + "/" + tick[11]
                    + " | globalCancel=" + str[2] + " | bukkitCancel=" + str[3] + " | adapterCancel=" + str[6]);
            sendKey(player, "[sched] ⑤ verdict | sameThread=" + sameThread
                    + " | periodEquivalent=" + periodEquivalent + " (global=" + globalPeriod + " bukkit=" + bukkitPeriod + ", declared 20)"
                    + " | delayedEquivalent=" + delayedEquivalent
                    + " | cancelSemanticsOk=" + cancelSemanticsOk
                    + " | rawInitialDelayPolicySame=" + rawInitialDelayPolicySame + " (global: " + str[4] + " / bukkit: " + str[5] + ")"
                    + " | clampEquivalent=" + clampEquivalent
                    + " | adapterDelayedEquivalent=" + adapterDelayedEquivalent
                    + " | adapterCancelOk=" + adapterCancelOk
                    + " | matrixEquivalent=" + matrixEquivalent
                    + " | portLeg=" + str[8]
                    + " | portLegEquivalent=" + portLegEquivalent
                    + " | CONCLUSION=" + (adapterEquivalent
                        ? "CAN swap the platform adapter to GlobalRegionScheduler with zero visible difference (raw APIs differ only on initialDelay<=0; the adapter normalises 0 -> 1 and the A/B on the production declaration 0/10 is tick-identical)"
                        : "CANNOT swap the adapter as-is: at least one measured item differs (see the raw values above)"));
        }, 60L);
        return true;
    }

    /**
     * 关键行双写：**玩家侧**（Adventure {@code Component}，文本与既有实现逐字相同）+ **服务端日志**
     * （{@link DebugCommand#log}，带 {@code [command-debug]} 前缀）。
     * <p>
     * 判定"关键行"的口径：结论/判据行与语义原始值行（probe start · ① execute · ② boundary · ④ cancel 三态 ·
     * ③d adapter cancel · ④m verdict · ④p port leg · ⑤ raw · ⑤ verdict）；**逐行刷屏明细**
     * （矩阵逐行 tick、逐次触发 #n、逐 tick 端口链）仍只发玩家侧 —— 日志保持可 grep、不刷屏。
     */
    private void sendKey(Player player, String text){
        player.sendMessage(Component.text(text));
        DebugCommand.log(text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args){
        if(args.length == 1) return SubCommand.filter(List.of("all"), args[0]);
        return List.of();
    }
}
