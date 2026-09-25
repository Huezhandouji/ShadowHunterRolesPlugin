package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务组件：**本实例全部 Bukkit 任务的唯一排定者与回收者**（每角色实例一个）。
 *
 * <h2>★ 本组件的目的：让内存安全，而不是「提供计时工具」</h2>
 * <ul>
 *   <li><b>组件编写者忘了取消</b> ⇒ 不泄漏：任务登记在**本组件自己的表**里，
 *       实例销毁时 {@link #stop()} 一次全清 ✓；</li>
 *   <li><b>玩家死亡 / 掉线 / 换角色</b> ⇒ 不残留：这三条路都经
 *       {@code RoleManager.clearRole(...)} → {@code RoleInstance.clear()} → 生命周期 {@code stop()}
 *       ⇒ 本组件的 {@code stop()} **必经** ✓。</li>
 * </ul>
 * <p>⇒ 组件侧**不需要**自己维护「记得取消」这件事；需要提前取消时用
 * {@link #cancelAllOf(RoleComponent)} 按请求者清。
 *
 * <h2>零外部依赖</h2>
 * 本组件**不接收任何注入**（构造签名与普通组件一字不差：{@code (id, ComponentServices)}）：
 * <ul>
 *   <li>调度器 = {@code Bukkit.getGlobalRegionScheduler()}（**静态可取**，不需要插件实例）；</li>
 *   <li>插件实例 = {@code Bukkit.getPluginManager().getPlugin(...)}（仅为满足 Paper 的
 *       {@code run*(Plugin, …)} 形参；★ 插件名取自 {@code plugin.yml} 的 {@code name}）。</li>
 * </ul>
 * <p><b>不做 Folia 适配</b>：宿主仍是 Paper（{@code GlobalRegionScheduler} 由 Paper 提供，非 Folia 专属）。
 *
 * <h2>归属按请求者</h2>
 * 每个句柄连同**请求者**一起登记 ⇒ 可以「按组件点名取消」与诊断，而不是只知道自己持有一堆任务。
 */
public class TaskComponent extends RoleComponent {

    /**
     * **本组件的登记 id**（★ 知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     */
    public static final String ID = "tasks";

    /**
     * 插件名（★ 与 {@code plugin.yml} 的 {@code name} 逐字一致）。
     * <p>只为满足 Paper 的 {@code run*(Plugin, …)} 形参；**本组件不持有插件引用**。
     */
    private static final String PLUGIN_NAME = "ShadowHunterRolesPlugin";

    /** 本实例已排定且**尚未取消**的任务（★ 登记序 = 排定序，便于诊断复现）。 */
    private final Map<ScheduledTask, RoleComponent> live = new LinkedHashMap<>();

    public TaskComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /**
     * **本组件的装配描述符**（★ 与技能/被动同规 ⇒ 它就是一个普通组件）。
     *
     * <p>不带栏位（{@code requiresSlot()} 默认 {@code false}）⇒ 天然不占热键栏；
     * 构造只需 `id + services` ⇒ 描述符极简、无额外依赖 ✓。
     */
    public static final class Specification extends RoleComponent.Specification<TaskComponent> {

        public Specification() {
            super("Task");
        }

        @Override
        public TaskComponent create(String id, ComponentServices services) {
            return new TaskComponent(id, services);
        }
    }

    // ───────── 排定面 ─────────

    /** 下一 tick 执行一次。 */
    public ScheduledHandle addSchedule(RoleComponent requester, Runnable task) {
        return schedule(requester, scheduler().run(plugin(), ignored -> task.run()));
    }

    /** 延迟 {@code delayTicks} 刻执行一次。 */
    public ScheduledHandle addScheduleLater(RoleComponent requester, long delayTicks, Runnable task) {
        return schedule(requester, scheduler().runDelayed(plugin(), ignored -> task.run(), delayTicks));
    }

    /**
     * 周期执行（首次延迟 {@code initialDelayTicks}、周期 {@code periodTicks}）。
     * <p>★ {@code initialDelayTicks <= 0} **归一为 1**：与既有实现同值归一（{@code Math.max} 幂等）；
     * 理由 = Paper 的 {@code runAtFixedRate} 对 {@code initialDelay <= 0} 会抛，
     * 而原生 Bukkit 的 {@code 0L} 语义就是"下一 tick 首次执行"，与归一后的 {@code 1L} **同 tick** ✓。
     */
    public ScheduledHandle addScheduleRepeating(RoleComponent requester, long initialDelayTicks,
                                                long periodTicks, Runnable task) {
        return schedule(requester, scheduler().runAtFixedRate(plugin(),
                ignored -> task.run(), Math.max(1L, initialDelayTicks), periodTicks));
    }

    /** 登记一个句柄到 {@code requester} 名下（统一入口：查空 + 入表 + 返回通用面）。 */
    private ScheduledHandle schedule(RoleComponent requester, ScheduledTask handle) {
        if (handle == null) {
            return null;
        }
        live.put(handle, requesterOf(requester));
        return new Handle(handle);
    }

    // ───────── 回收面 ─────────

    /**
     * **取消 {@code requester} 名下的全部任务**（用于「某个组件要提前停手」的场合）。
     *
     * @return 实际取消的句柄数
     */
    public int cancelAllOf(RoleComponent requester) {
        RoleComponent owner = requesterOf(requester);
        List<ScheduledTask> doomed = new ArrayList<>();
        for (Map.Entry<ScheduledTask, RoleComponent> entry : live.entrySet()) {
            if (entry.getValue() == owner) {
                doomed.add(entry.getKey());
            }
        }
        for (ScheduledTask handle : doomed) {
            handle.cancel();
            live.remove(handle);
        }
        return doomed.size();
    }

    /**
     * **停止生效：取消本实例的全部任务**（★ 内存安全的落点）。
     *
     * <p>调用时机 = 实例销毁链（死亡 / 掉线 / 换角色 / 隔离 / 插件卸载）都必经的生命周期钩子
     * ⇒ **组件编写者忘了取消也不会泄漏** ✓。
     */
    @Override
    public void stop() {
        for (ScheduledTask handle : new ArrayList<>(live.keySet())) {
            handle.cancel();
        }
        live.clear();
    }

    /** 当前仍在册的任务数（诊断读口；不参与行为决策）。 */
    public int liveTaskCount() {
        return live.size();
    }

    // ───────── 内部 ─────────

    private GlobalRegionScheduler scheduler() {
        return Bukkit.getGlobalRegionScheduler();
    }

    private org.bukkit.plugin.Plugin plugin() {
        return ShadowHunterRolesPlugin.getInstance();
    }

    /**
     * 请求者归一：{@code null} ⇒ **本组件自己**（不制造"无人认领的任务"，那样 {@code stop()} 仍会清到它）。
     */
    private RoleComponent requesterOf(RoleComponent requester) {
        return requester != null ? requester : this;
    }

    /** 交给组件侧的**通用句柄**：只暴露"取消"与"是否已取消"（组件不认识 Paper 的 {@code ScheduledTask}）。 */
    private static final class Handle implements ScheduledHandle {

        private final ScheduledTask delegate;

        private Handle(ScheduledTask delegate) {
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
    }
}
