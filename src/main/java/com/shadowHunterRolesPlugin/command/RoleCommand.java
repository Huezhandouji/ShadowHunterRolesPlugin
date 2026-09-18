package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class RoleCommand implements CommandExecutor {

    private final RoleManager roleManager;
    //阶段 4（⑤）：RoleRegistry 改为构造注入（D-2 静态桥已删）
    private final RoleRegistry roleRegistry;

    public RoleCommand(RoleManager roleManager, RoleRegistry roleRegistry){
        this.roleManager = roleManager;
        this.roleRegistry = roleRegistry;
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(!(sender instanceof Player player)){
            sender.sendMessage("Only players can execute this command!");
            return true;
        }

        if(args.length == 0){
            //sendHelp(player);
            return true;
        }

        switch (args[0]){
            case "set":
                if(args.length == 2){
                    handleSet(player, args[1], null);
                    return true;
                }
                else if(args.length == 3){
                    handleSet(player, args[1], args[2]);
                    return true;
                }
                break;

            case "clear":
                if(args.length == 1){
                    handleClear(player, null);
                    return true;
                }
                else if(args.length == 2){
                    handleClear(player, args[1]);
                    return true;
                }
                break;

            case "energy":
                if(args.length < 2){
                    player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
                    return true;
                }
                switch (args[1]){
                    case "get":
                        if(args.length == 2) {
                            handleGetEnergy(player, null);
                            return true;
                        }
                        if(args.length == 3){
                            handleGetEnergy(player, args[2]);
                            return true;
                        }
                        break;
                    case "set":
                        if(args.length == 3){
                            handleSetEnergy(player, args[2], null);
                            return true;
                        }
                        if(args.length == 4){
                            handleSetEnergy(player, args[2], args[3]);
                            return true;
                        }
                        break;
                }
                sendHelp(player);
                return true;

            case "debug":
                //临时调试入口（冷却自管理冒烟）：/role debug cooldown <status|end|restart> <slot|componentId>
                if(handleDebugCooldown(player, args)) return true;
                //临时调度探针（schedprobe）：/role debug sched [all] —— 取证后可整段删除（删除清单见 handleDebugSched 上方注释）
                if(handleDebugSched(player, args)) return true;
                player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
                return true;

            case "help":
                sendHelp(player);
                return true;

            default:
                player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
                return true;
        }
        return true;
    }

    private void handleGetEnergy(Player sender, String targetName){
        Player target;

        if(targetName == null) target = sender;
        else target = Bukkit.getPlayer(targetName);
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
            return;
        }
        if(!roleManager.hasRole(target)) {
            sender.sendMessage(Component.text(target.getName() + " has no role!"));
            return;
        }
        sender.sendMessage(Component.text("The current energy level of [" + target.getName() + "] is: " + roleManager.getRoleInstance(target).getCurrentEnergy()));
    }

    private void handleSetEnergy(Player sender, String energyLevel, String targetName){
        Player target;

        if(targetName == null) target = sender;
        else target = Bukkit.getPlayer(targetName);
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
            return;
        }
        if(!roleManager.hasRole(target)) {
            sender.sendMessage(Component.text(target.getName() + " has no role!"));
            return;
        }

        try{
            int el = Integer.parseInt(energyLevel);
            roleManager.getRoleInstance(target).setCurrentEnergy(el);
        }
        catch (NumberFormatException e){
            return;
        }


    }

    private void handleSet(Player sender, String roleId, String targetName){
        if(!roleRegistry.contains(roleId)){
            sender.sendMessage(Component.text("Role '" + roleId + "' not exist!"));
            return;
        }

        //选择角色
        Player target;
        if(targetName == null){
            target = sender; // 默认是自己
        }
        else{
            target = Bukkit.getPlayer(targetName);
            if(target == null || !target.isOnline()){
                sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
                return;
            }
        }

        boolean success = roleManager.selectRole(target, roleId);
        if(success){
            if(targetName == null) sender.sendMessage(Component.text("Your role has been set: " + roleRegistry.get(roleId).getId()));
            else sender.sendMessage(Component.text("The role of player [ " + targetName + "] has been set: " + roleRegistry.get(roleId).getId()));
        }
        else{
            sender.sendMessage(Component.text("Role set operation failed."));
        }
    }

    private void handleClear(Player sender, String targetName){
        // 确定目标玩家
        Player target;
        if(targetName == null){
            target = sender; // 默认自己
        }
        else{
            target = Bukkit.getPlayer(targetName);
            if(target == null || !target.isOnline()){
                sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
                return;
            }
        }

        //检查是否有角色
        if(!roleManager.hasRole(target)){
            if(target.equals(sender)){
                sender.sendMessage(Component.text("You have no role yet!"));
            }
            else{
                sender.sendMessage(Component.text("[" + targetName + "] has no role yet!"));
            }
            return;
        }

        //移除角色
        boolean success = roleManager.clearRole(target);
        if(success){
            if(target.equals(sender)){
                sender.sendMessage(Component.text("Your role has been cleared."));
            }
            else{
                sender.sendMessage(Component.text("The role of [" + targetName + "] has been cleared."));
                target.sendMessage(Component.text("Your role has been cleared."));
            }
        }
        else {
            sender.sendMessage(Component.text("Failed to clear the role."));
        }
        return;
    }

    // ═════════ 临时调试入口（冷却自管理冒烟用；**冒烟结束可整段删除**）═════════
    // 删除清单：本段（到本类末尾前的全部 DEBUG 方法）+ 上面 switch 的 case "debug" + 本行上方 5 个 import
    //           + RoleInstance.servicesOf(...)（同一段注释里也有删除说明）。

    private boolean handleDebugCooldown(Player player, String[] args){
        //门控：仅 op（对普通玩家零可见行为）
        if(!player.isOp()){
            player.sendMessage(Component.text("You do not have permission to use this command."));
            return true;
        }
        if(args.length < 2 || !"cooldown".equals(args[1])) return false;
        if(args.length < 4){
            player.sendMessage(Component.text("Usage: /role debug cooldown <status|end|restart> <slot|componentId>"));
            return true;
        }
        if(!roleManager.hasRole(player)){
            player.sendMessage(Component.text("You have no role yet!"));
            return true;
        }

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null){
            player.sendMessage(Component.text("Role instance not found for you."));
            return true;
        }

        String action = args[2];
        String target = args[3];
        String componentId = resolveComponentId(instance, target);
        if(componentId == null){
            player.sendMessage(Component.text("No component found for: " + target));
            return true;
        }
        RoleComponent component = instance.componentRegistry().getById(componentId);
        if(!(component instanceof ActiveComponent active)){
            player.sendMessage(Component.text("Not an active component (skill/main weapon): " + componentId));
            return true;
        }
        ComponentServices services = instance.servicesOf(componentId);
        if(services == null){
            player.sendMessage(Component.text("No services bound for component: " + componentId));
            return true;
        }
        CooldownPort cooldowns = services.cooldowns();
        int declared = active.getCooldownTicks();

        switch (action){
            case "status":{
                int remaining = cooldowns.remainingTicks();
                boolean cooling = remaining > 0;
                player.sendMessage(Component.text("[cooldown] " + componentId
                        + " | cooling=" + cooling
                        + " | remainingTicks=" + remaining
                        + " | remainingSeconds=" + String.format("%.2f", remaining / 20.0)
                        + " | declaredTicks=" + declared));
                return true;
            }
            case "end":{
                boolean wasCooling = cooldowns.remainingTicks() > 0;
                boolean ended = cooldowns.end();
                player.sendMessage(Component.text("[cooldown] end(" + componentId + ") returned=" + ended
                        + " | wasCooling=" + wasCooling
                        + (ended ? " | ENDED_BY_COMPONENT dispatched (onCooldownEnd)" : " | no-op (was not cooling)")));
                return true;
            }
            case "restart":{
                boolean wasCooling = cooldowns.remainingTicks() > 0;
                cooldowns.start(declared);
                int remaining = cooldowns.remainingTicks();
                player.sendMessage(Component.text("[cooldown] restart(" + componentId + ") wasCooling=" + wasCooling
                        + (wasCooling ? " | RESTARTED dispatched (old segment dropped)" : " | no old segment was cooling")
                        + " | newRemainingTicks=" + remaining));
                return true;
            }
            default:
                player.sendMessage(Component.text("Usage: /role debug cooldown <status|end|restart> <slot|componentId>"));
                return true;
        }
    }

    /** 目标解析：纯数字 = 热键栏槽位（读 {@code Role.getSlotMap()}，不猜）；否则按组件 id（须在注册表内）。 */
    private String resolveComponentId(RoleInstance instance, String target){
        if(target.matches("\\d+")){
            Integer slot = Integer.parseInt(target);
            return instance.getRole().getSlotMap().get(slot);
        }
        return instance.componentRegistry().getById(target) != null ? target : null;
    }

    // ═════════ 临时调度探针（schedprobe；**取证后可整段删除**）═════════
    // 入口：`/role debug sched [all]` —— op 门控；不新增注册路径、不在 onEnable 做常驻副作用；
    // 全部探针任务在第 3 次触发时自取消 ⇒ 单次命令不留常驻任务（状态全部是本方法局部变量）。
    // 逐条打印原始值：① execute 回调内线程名；② run / runDelayed / runAtFixedRate 的线程名 + 实际触发 tick（与声明值并排）
    // + initialDelay=0 边界实测（runAtFixedRate vs runTaskTimer，异常原文照打）；
    // ③ 同周期 Bukkit.getScheduler().runTaskTimer 对照任务的线程名 + 实际触发 tick（声明值逐字相同）+ ③b runTaskLater 延时对照；
    // ③c/③d **新实现验证**：对**生产声明值 0/10** 做 A/B —— ③c 原生 Bukkit runTaskTimer(0,10) vs
    // ③d 生产适配器 BukkitSchedulerAdapter.runRepeating(0,10)（内含 0→1 归一）；另测适配器 run() / runLater(20) 与 Task 句柄取消；
    // ④ ScheduledTask.cancel() 返回值 / isCancelled() / getExecutionState() / 重复 cancel；⑤ 一句话结论（由本次原始值现算，不只给结论）。
    // 删除清单（逐条；判据 = `git grep -n "sched" -- src/main/java` 归零）：
    //   ① 本段整体：本注释块 + handleDebugSched(Player, String[])（本类唯一新增方法）；
    //   ② onCommand 的 case "debug" 内新增的 1 行 `if(handleDebugSched(player, args)) return true;`（含其上方 1 行注释）；
    //   ③ 本段新增的 6 个 import：com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin、
    //      com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter、com.shadowHunterRolesPlugin.platform.Task、
    //      io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler、
    //      io.papermc.paper.threadedregions.scheduler.ScheduledTask、org.bukkit.scheduler.BukkitTask；
    //   ④ 无新增字段、无新增注册路径、plugin.yml 与 onEnable 零改动。
    private boolean handleDebugSched(Player player, String[] args){
        //门控：仅 op（对普通玩家零可见行为；与 handleDebugCooldown 同款、先于参数判定）
        if(!player.isOp()){
            player.sendMessage(Component.text("You do not have permission to use this command."));
            return true;
        }
        if(args.length < 2 || !"sched".equals(args[1])) return false;
        if(args.length >= 3 && !"all".equals(args[2])){
            player.sendMessage(Component.text("Usage: /role debug sched [all]"));
            return true;
        }

        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if(plugin == null){
            player.sendMessage(Component.text("[sched] plugin instance unavailable; probe aborted."));
            return true;
        }

        //观测位（局部 ⇒ 删除本方法即无残留）：str = 线程名/取消原始值/边界值；tick = 实际触发 tick 相对命令 tick 的差值
        final String[] str = new String[7];
        final int[] tick = new int[13];
        for(int i = 0; i < tick.length; i++) tick[i] = -1;
        final int[] grrCount = {0};
        final int[] bukkitCount = {0};
        final int baseTick = Bukkit.getCurrentTick();
        final GlobalRegionScheduler grs = Bukkit.getGlobalRegionScheduler();
        final BukkitTask[] bukkitHolder = new BukkitTask[1];

        player.sendMessage(Component.text("[sched] probe start | commandThread=" + Thread.currentThread().getName()
                + " | commandTick=" + baseTick + " | primaryThread=" + Bukkit.isPrimaryThread()
                + " | declared: runDelayed=20t, runAtFixedRate=1/10t, bukkit runTaskTimer=1/10t, boundary=0/10t"));

        //① execute(Plugin, Runnable)：无返回值/无句柄 ⇒ 只测线程与相位
        grs.execute(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            player.sendMessage(Component.text("[sched] ① execute | thread=" + Thread.currentThread().getName()
                    + " | tick=" + now + " | delta=" + (now - baseTick)
                    + " | primaryThread=" + Bukkit.isPrimaryThread()));
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
        player.sendMessage(Component.text("[sched] ② boundary | GlobalRegionScheduler.runAtFixedRate(0,10) -> " + str[4]
                + " | Bukkit.getScheduler().runTaskTimer(0,10) -> " + str[5]));

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
                player.sendMessage(Component.text("[sched] ④ cancel() #1 -> " + cancel1
                        + " | isCancelled()=" + cancelled1 + " | getExecutionState()=" + state1));
                player.sendMessage(Component.text("[sched] ④ cancel() #2 (repeat) -> " + cancel2
                        + " | isCancelled()=" + cancelled2 + " | getExecutionState()=" + state2
                        + " | no exception = idempotent"));
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
                player.sendMessage(Component.text("[sched] ④(BukkitTask) cancel() -> void | isCancelled() before="
                        + before + " | after=" + after + " | no exception = idempotent"));
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
                player.sendMessage(Component.text("[sched] ③d adapter Task.cancel() -> void | " + str[6]
                        + " | repeated cancel: no exception = idempotent"));
            }
        }, 0L, 10L);

        //⑤ 结论：60 tick 后（全部周期任务均已自取消）以本次原始值现算"能否直切"
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
            boolean adapterEquivalent = sameThread && periodEquivalent && delayedEquivalent && cancelSemanticsOk
                    && clampEquivalent && adapterDelayedEquivalent && adapterCancelOk;
            player.sendMessage(Component.text("[sched] ⑤ raw | globalThread=" + str[0]
                    + " | bukkitThread=" + str[1]
                    + " | globalDeltas=" + tick[1] + "/" + tick[2] + "/" + tick[3] + " (declared 1/10)"
                    + " | bukkitDeltas=" + tick[4] + "/" + tick[5] + "/" + tick[6] + " (declared 1/10)"
                    + " | globalRunDelayedDelta=" + tick[0] + " | bukkitRunTaskLaterDelta=" + tick[7] + " (declared 20)"
                    + " | adapterRunLaterDelta=" + tick[12]
                    + " | A/B(declared 0/10) rawBukkitFirstThird=" + tick[8] + "/" + tick[9]
                    + " adapterFirstThird=" + tick[10] + "/" + tick[11]
                    + " | globalCancel=" + str[2] + " | bukkitCancel=" + str[3] + " | adapterCancel=" + str[6]));
            player.sendMessage(Component.text("[sched] ⑤ verdict | sameThread=" + sameThread
                    + " | periodEquivalent=" + periodEquivalent + " (global=" + globalPeriod + " bukkit=" + bukkitPeriod + ", declared 20)"
                    + " | delayedEquivalent=" + delayedEquivalent
                    + " | cancelSemanticsOk=" + cancelSemanticsOk
                    + " | rawInitialDelayPolicySame=" + rawInitialDelayPolicySame + " (global: " + str[4] + " / bukkit: " + str[5] + ")"
                    + " | clampEquivalent=" + clampEquivalent
                    + " | adapterDelayedEquivalent=" + adapterDelayedEquivalent
                    + " | adapterCancelOk=" + adapterCancelOk
                    + " | CONCLUSION=" + (adapterEquivalent
                        ? "CAN swap the platform adapter to GlobalRegionScheduler with zero visible difference (raw APIs differ only on initialDelay<=0; the adapter normalises 0 -> 1 and the A/B on the production declaration 0/10 is tick-identical)"
                        : "CANNOT swap the adapter as-is: at least one measured item differs (see the raw values above)")));
        }, 60L);
        return true;
    }

    private void sendHelp(Player player){
        player.sendMessage(Component.text("=== ROLE SYSTEM COMMAND ==="));
        player.sendMessage(Component.text("/role set <roleId> <playerName>  --set role"));
        player.sendMessage(Component.text("/role set <roleId>  --set role for yourself"));
        player.sendMessage(Component.text("/role clear <playerName>  --clear role"));
        player.sendMessage(Component.text("/role clear  --clear your role"));
    }
}
