package com.shadowHunterProbe;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.component.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * **运行级探针**（工装件 · 阶段 13 · t129）：给「④ 窗口半」的三处读数提供观测手段 ✓。
 *
 * <p><b>为什么必须存在</b>：{@link VitalsComponent.Participant} 的**生产实现者 = 0** ✗
 * （`t83` 说明件 §8④ 在案 ✓）⇒ 钩子投递在无探针时**不可观测**（产品不打印 ✓）⇒ 只能由探针注册一个实现 ✓。
 *
 * <p><b>五个能力</b>（逐条对应 `t128` 的 F-2 ✓）：① 注册 {@link ProbeParticipant}（`Participant` 实现 ✓）；
 * ② 每次回调**自增并落一行结构化日志** ✓（`damaged`/`healed` 计数 ⇒ 供"隔离条数"读数 ✓）；
 * ③ `/probe throw on` ⇒ 回调**故意抛** ⇒ 主插件 `guardedCall` 记 `pendingQuarantine`、窗口 `finally` 跑四步
 * ⇒ 日志应出现 `was QUARANTINED` ✓（反例 ✓）；④ `/probe dup` ⇒ **同 id 两份**插进同一实例 ✓；
 * ⑤ 全部日志一行式：{@code [probe] at=<ms> event=<…> seq=<n> …} ✓。
 *
 * <p><b>构建隔离</b>：本插件在 {@code probe/}（**独立 Gradle 构建** ✓）⇒ 主 `jar` 任务的输入集里没有它 ✓
 * ⇒ **不可能**进发布 jar ✓（拓扑保证 ✓）。
 *
 * <p><b>已知让步（工装件性质 · 如实申报）</b>：主插件只公开 {@code getRoleAPI()} ✗（无 manager 入口 ✗），
 * 而探针**必须**拿到实例容器才能插组件 ✓ ⇒ 本类经 {@link RoleAPI} 实现类的**私有 {@code roleManager} 字段**
 * 反射取 {@link RoleManager} ✓ —— **只读该字段、不改它** ✓；**产品代码不反射** ✓（设计定案 §5 的"禁止反射"是对产品的约束 ✓）。
 */
public final class ShadowHunterProbe extends JavaPlugin {

    private static final String PREFIX = "[probe]";

    /** `onDamaged` 累计次数（**可计数序列** ✓）。 */
    private static final AtomicInteger DAMAGED = new AtomicInteger();
    /** `onHealed` 累计次数 ✓。 */
    private static final AtomicInteger HEALED = new AtomicInteger();
    /** 已插入的探针组件份数 ✓。 */
    private static final AtomicInteger INSTALLED = new AtomicInteger();
    /** 故意抛异常开关 ✓（`/probe throw on|off`）。 */
    private static volatile boolean throwMode = false;

    private static ShadowHunterProbe instance;

    @Override
    public void onEnable() {
        instance = this;
        log("enable", 0, "version=" + getPluginMeta().getVersion()
                + " depend=" + getPluginMeta().getPluginDependencies());
        if (getCommand("probe") != null) {
            getCommand("probe").setExecutor(this);
        } else {
            log("enable", 0, "command 'probe' missing from plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        log("disable", 0, "damaged=" + DAMAGED.get() + " healed=" + HEALED.get()
                + " installed=" + INSTALLED.get() + " throwMode=" + throwMode);
    }

    // ───────── 命令面（给【读数卡】用 ✓） ─────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "install", "dup" -> {
                if (args.length < 2) { usage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { reply(sender, "no online player named '" + args[1] + "'"); return true; }
                String id = args.length >= 3 ? args[2] : (args[0].equalsIgnoreCase("dup") ? "probeDup" : "probe");
                RoleInstance instance = instanceOf(target);
                if (instance == null) { reply(sender, "'" + args[1] + "' has no role instance"); return true; }
                int count = args[0].equalsIgnoreCase("dup") ? 2 : 1;
                int inserted = install(instance, id, count, args[0].toLowerCase(java.util.Locale.ROOT));
                reply(sender, "inserted " + inserted + " component(s) with id '" + id + "' into " + target.getName()
                        + " (container size=" + instance.componentRegistry().size() + ")");
            }
            case "async" -> {
                //阶段 13 · t132（AY2）：**只读**异步触发入口 —— 在**非主线程**调产品公开的 deliverHook，
                //观察其"非主线程 ⇒ 响亮 SEVERE 并放弃投递"的守卫是否真的生效（本入口**不改任何状态** ✓：
                //action 只写一行探针日志；产品侧在断言处就放弃投递 ⇒ 零副作用 ✓）。
                if (args.length < 2) { usage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                RoleInstance instance = target == null ? null : instanceOf(target);
                if (instance == null) { reply(sender, "'" + args[1] + "' has no role instance"); return true; }
                List<RoleComponent> all = instance.componentRegistry().all();
                RoleComponent carrier = all.isEmpty() ? null : all.get(all.size() - 1);
                if (carrier == null) { reply(sender, "empty container"); return true; }
                final RoleComponent hookCarrier = carrier;
                final RoleInstance targetInstance = instance;
                Thread thread = new Thread(() -> {
                    log("async-trigger", 0, "thread=" + Thread.currentThread().getName()
                            + " primaryThread=" + Bukkit.isPrimaryThread() + " carrier=" + hookCarrier.getId());
                    try {
                        targetInstance.deliverHook(hookCarrier, "probeAsync", () -> log("async-action", 0,
                                "thread=" + Thread.currentThread().getName() + " (should NOT run if the guard works)"));
                    } catch (RuntimeException failure) {
                        log("async-trigger", 0, "threw=" + failure.getClass().getSimpleName() + ": " + failure.getMessage());
                    }
                }, "probe-async-thread");
                thread.start();
                reply(sender, "async delivery triggered on thread 'probe-async-thread' (carrier=" + hookCarrier.getId() + ")");
            }
            case "throw" -> {
                boolean on = args.length >= 2 && args[1].equalsIgnoreCase("on");
                throwMode = on;
                log("throw", 0, "throwMode=" + on);
                reply(sender, "throwMode=" + on);
            }
            case "status" -> {
                log("status", 0, "damaged=" + DAMAGED.get() + " healed=" + HEALED.get()
                        + " installed=" + INSTALLED.get() + " throwMode=" + throwMode);
                reply(sender, "damaged=" + DAMAGED.get() + " healed=" + HEALED.get()
                        + " installed=" + INSTALLED.get() + " throwMode=" + throwMode);
            }
            case "list" -> {
                if (args.length < 2) { usage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                RoleInstance instance = target == null ? null : instanceOf(target);
                if (instance == null) { reply(sender, "'" + args[1] + "' has no role instance"); return true; }
                List<String> ids = new ArrayList<>();
                for (RoleComponent component : instance.componentRegistry().all()) {
                    ids.add(component.getId());
                }
                log("list", 0, "player=" + args[1] + " size=" + ids.size() + " ids=" + String.join("|", ids));
                reply(sender, "size=" + ids.size() + " ids=" + String.join(", ", ids));
            }
            default -> usage(sender);
        }
        return true;
    }

    private static void usage(CommandSender sender) {
        reply(sender, "usage: /probe <install|dup> <player> [id] | /probe throw <on|off> | /probe status | /probe list <player>");
    }

    private static void reply(CommandSender sender, String text) {
        sender.sendMessage(text);
        log("reply", 0, "to=" + sender.getName() + " text=\"" + text + "\"");
    }

    // ───────── 容器操作（只经公开写口 ✓） ─────────

    /** 把探针组件插进实例容器 ✓（`insert` 是**写口** ⇒ 冻结后仍合法 ✓；随后 `awake()`/`start()` 与容器同序 ✓）。 */
    private static int install(RoleInstance instance, String id, int count, String action) {
        ComponentRegistry registry = instance.componentRegistry();
        int inserted = 0;
        for (int i = 0; i < count; i++) {
            ProbeParticipant component = new ProbeParticipant(id, action + "#" + i);
            int index = registry.size();
            registry.insert(index, component, ComponentRegistry.Declaration.of(component));
            component.awake();
            component.start();
            inserted++;
            INSTALLED.incrementAndGet();
            log("install", i + 1, "action=" + action + " id=" + id + " index=" + index
                    + " containerSize=" + registry.size());
        }
        return inserted;
    }

    /** 目标玩家实例（经 `RoleAPI` 实现类的私有 `roleManager` 字段反射取得 ✓ —— 见类 javadoc 的"已知让步" ✓）。 */
    private static RoleInstance instanceOf(Player player) {
        RoleAPI api = Bukkit.getServicesManager().load(RoleAPI.class);
        if (api == null) {
            log("instance", 0, "RoleAPI not registered in ServicesManager");
            return null;
        }
        try {
            Field field = api.getClass().getDeclaredField("roleManager");
            field.setAccessible(true);
            Object manager = field.get(api);
            if (!(manager instanceof RoleManager roleManager)) {
                log("instance", 0, "reflected field is not a RoleManager: " + (manager == null ? "null" : manager.getClass().getName()));
                return null;
            }
            return roleManager.getRoleInstance(player);
        } catch (ReflectiveOperationException failure) {
            log("instance", 0, "reflection failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return null;
        }
    }

    // ───────── 探针组件：Participant 实现 ✓ ─────────

    /**
     * 探针的 {@link VitalsComponent.Participant} 实现（**生产实现者 = 0** 的那个缺口 ✓）。
     * <p>回调：**自增计数 + 一行结构化日志** ✓；`throwMode` 开 ⇒ **故意抛** ✓（反例 ⇒ `was QUARANTINED` ✓）。
     * <p>服务面用**空桩**（`ComponentServices` 是 3 成员 record ⇒ 三个 null ✓）：本组件**不**经 `svc()` 取任何东西 ✓，
     * 因此不需要真实服务 ✓（插入后 `awake()`/`start()` 均无副作用 ✓）。
     */
    public static final class ProbeParticipant extends RoleComponent implements VitalsComponent.Participant {

        private final String tag;

        ProbeParticipant(String id, String tag) {
            super(id, new ComponentServices(null, null, null));
            this.tag = tag;
        }

        @Override
        public void onDamaged(Player source, double amount) {
            int seq = DAMAGED.incrementAndGet();
            log("damaged", seq, "tag=" + tag + " id=" + getId()
                    + " source=" + (source == null ? "<null>" : source.getName()) + " amount=" + amount);
            if (throwMode) {
                throw new IllegalStateException("probe: intentional onDamaged failure seq=" + seq + " tag=" + tag);
            }
        }

        @Override
        public void onHealed(double amount) {
            int seq = HEALED.incrementAndGet();
            log("healed", seq, "tag=" + tag + " id=" + getId() + " amount=" + amount);
            if (throwMode) {
                throw new IllegalStateException("probe: intentional onHealed failure seq=" + seq + " tag=" + tag);
            }
        }
    }

    // ───────── 一行式结构化日志 ✓ ─────────

    /** {@code [probe] at=<ms> event=<…> seq=<n> <extra…>} ✓（前缀与主插件 `[command-access]` 同风格 ✓）。 */
    private static void log(String event, int seq, String extra) {
        ShadowHunterProbe plugin = instance;
        if (plugin == null) return;
        plugin.getLogger().info(PREFIX + " at=" + System.currentTimeMillis()
                + " event=" + event + " seq=" + seq + " " + extra);
    }
}
