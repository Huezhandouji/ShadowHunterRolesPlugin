package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.Scheduler;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * **框架级服务组件的清单**：类 → 组件 id 的**唯一落点**。
 *
 * <h2>它解决什么</h2>
 * 容器（{@code RoleInstance}）需要**框架级服务组件**（能量 / SanTE / 生命 / buff / 计时 / 物品渲染），
 * 但这些组件的**类名、构造顺序与接线**不该散在容器里 —— 否则"框架核心知道具体组件集合"。
 * 本类把那部分知识**收进一个文件**：
 * <ul>
 *   <li><b>哪些 id、按什么顺序构造</b> —— {@link #build} 的语句顺序；</li>
 *   <li><b>构造后必须做的接线</b> —— 同样在 {@link #build} 里，紧跟在对应组件之后；</li>
 *   <li><b>构造期需要的外部输入</b> —— {@link Input} 显式列出，{@code null} 会在构造前被拒绝 ✓。</li>
 * </ul>
 * <p>容器侧则**只经 id 驱动**：{@link #build} 回一份"**构造顺序**的不可变列表"，
 * 容器逐个按 {@code component.getId()} 登记 —— 它**不点名任何具体组件类** ✓。
 *
 * <h2>构造顺序不可交换</h2>
 * <b>渲染组件必须最先构造</b>：它的置脏通道要在**任何会置脏的组件**之前绑好
 * （能量组件在它之后的构造期即可请求重绘）⇒ 本方法里"先渲染、后其余"的顺序**是有意的** ✗ 勿调换。
 *
 * <h2>与容器既有入口的关系</h2>
 * 登记（{@code registerServiceComponents(String...)}）与取用（按 id 的查取入口）
 * **都保持原样** ✓ —— 本类只承接**构造侧**。
 */
public final class ServiceComponents {

    /**
     * **构造期输入**（容器在装配期一次性给出）。
     *
     * @param role                 本实例的角色模板（能量 / SanTE 的上限取自它 ✓）
     * @param servicesFor          组件的**服务集工厂**：容器按组件 id 现给（与既有口径逐字相同 ✓）
     * @param scheduler            调度器（计时组件用）
     * @param buffManager          记账表（buff 组件持有它 —— **与容器共享同一个实例** ✓）
     * @param taskTrack            任务归属登记（容器侧 = 每组件资源表 ✓）
     * @param taskCancelAll        按请求者取消该名下的全部句柄（返回实际取消数 ✓）
     * @param santeChangeReporter  平台侧通知的**接收者**：收到变更后**由容器**完成派发 ✓
     *
     * <p><b>为什么任务登记用两个函数、而不直接给资源表</b>：那些操作归容器所有；
     * 本类只承"怎么装"，不了"装到哪" ✗。
     */
    public record Input(
            Role role,
            Function<String, ComponentServices> servicesFor,
            Scheduler scheduler,
            BuffManager buffManager,
            TaskTrack taskTrack,
            TaskCancelAll taskCancelAll,
            Consumer<Change3> santeChangeReporter) {

        public Input {
            if (role == null) throw new NullPointerException("role");
            if (servicesFor == null) throw new NullPointerException("servicesFor");
            if (scheduler == null) throw new NullPointerException("scheduler");
            if (buffManager == null) throw new NullPointerException("buffManager");
            if (taskTrack == null) throw new NullPointerException("taskTrack");
            if (taskCancelAll == null) throw new NullPointerException("taskCancelAll");
            if (santeChangeReporter == null) throw new NullPointerException("santeChangeReporter");
        }
    }

    /**
     * **一次变更的三个值**（接收者据此完成派发）。
     *
     * @param componentId 发生变更的组件 id
     * @param previous    变化前的值
     * @param current     变化后的值
     */
    public record Change3(String componentId, int previous, int current) {
    }

    /** 任务归属登记（容器侧实现）。 */
    @FunctionalInterface
    public interface TaskTrack {
        void track(RoleComponent requester, Task task);
    }

    /** 按请求者取消全部任务（容器侧实现；返回实际取消数）。 */
    @FunctionalInterface
    public interface TaskCancelAll {
        int cancelAll(RoleComponent requester);
    }

    /**
     * id：**物品渲染组件**（改动它需同时看容器侧的登记清单 ✓）。
     */
    public static final String ID_HOTBAR_RENDER = "hotbarRender";
    /** id：能量组件。 */
    public static final String ID_ENERGY = "energy";
    /** id：SanTE 组件。 */
    public static final String ID_SANTE = "sante";
    /** id：生命组件。 */
    public static final String ID_VITALS = "vitals";
    /** id：buff 组件。 */
    public static final String ID_BUFFS = "buffs";
    /** id：计时组件。 */
    public static final String ID_TIMERS = "timers";

    private ServiceComponents() {
    }

    /**
     * **构造全部框架级服务组件**（顺序见下方注释；返回**构造顺序**的不可变列表 ✓）。
     * <p>返回后各组件**已接线完成**、但**尚未登记**进容器 ⇒ 登记由调用方按 id 逐个完成 ✓
     * （既有的"冻结后才登记"口径由此保持 ✓）。
     */
    public static List<RoleComponent> build(Input input) {
        Input in = Objects.requireNonNull(input, "input");

        // ① 物品渲染：**最先**构造 —— 它的置脏通道必须先于任何会置脏的组件存在
        //    （能量组件在它之后的构造期即可请求重绘）。
        HotbarRenderComponent hotbarRender = new HotbarRenderComponent(ID_HOTBAR_RENDER,
                in.servicesFor().apply(ID_HOTBAR_RENDER));
        hotbarRender.bindRepaintSink(hotbarRender::markDirty);

        // ② 能量：上限取角色模板的声明值；**一次变更 ⇒ 请求重绘一次**（无条件 ✓）。
        EnergyComponent energy = new EnergyComponent(ID_ENERGY, in.servicesFor().apply(ID_ENERGY),
                in.role().getMaxEnergy(),
                change -> hotbarRender.markDirty());

        // ③ SanTE：以**本组件自身**为 owner 登记平台侧监听 ⇒ 写入路径直调这一条；
        //    订阅者仍归容器的派发边界（真变化闸门 / 逐监听器隔离 / 重入合并都在那里 ✓）。
        SanTEComponent sante = new SanTEComponent(ID_SANTE, in.servicesFor().apply(ID_SANTE),
                in.role().getMaxSanTE());
        sante.addListener(sante, change -> in.santeChangeReporter()
                .accept(new Change3(ID_SANTE, change.previous(), change.current())));

        // ④ 生命：钳位策略的唯一实现在组件里（状态 = 玩家属性，属外部平台状态）。
        VitalsComponent vitals = new VitalsComponent(ID_VITALS, in.servicesFor().apply(ID_VITALS));

        // ⑤ buff：记账表（**与容器共享同一个实例**）与药水账本都归它持有。
        BuffComponent buffs = new BuffComponent(ID_BUFFS, in.servicesFor().apply(ID_BUFFS),
                in.buffManager());

        // ⑥ 计时：任务的创建在组件里、登记归属按请求者；资源存储仍是容器的每组件资源表。
        TimerComponent timers = new TimerComponent(ID_TIMERS, in.servicesFor().apply(ID_TIMERS),
                in.scheduler(), new TimerComponent.TaskSink() {
            @Override
            public void track(RoleComponent requester, Task task) {
                in.taskTrack().track(requester, task);
            }

            @Override
            public int cancelAll(RoleComponent requester) {
                return in.taskCancelAll().cancelAll(requester);
            }
        });

        List<RoleComponent> ordered = new ArrayList<>(6);
        ordered.add(hotbarRender);
        ordered.add(energy);
        ordered.add(sante);
        ordered.add(vitals);
        ordered.add(buffs);
        ordered.add(timers);
        return List.copyOf(ordered);
    }
}
