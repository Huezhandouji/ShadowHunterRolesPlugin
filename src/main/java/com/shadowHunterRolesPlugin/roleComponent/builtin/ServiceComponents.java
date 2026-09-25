package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.Scheduler;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * **提前写好的内建服务组件的清单**：类 → 组件 id 的**唯一落点**。
 *
 * <p>★ **命名与层级纪律（本类所在的 `builtin/` 就是为此改名）**：这些组件叫「**内建**」
 * （提前写好的、随框架一起发布的组件），**不是「框架级」** —— 它们与 `custom/` 里的**角色内容组件**
 * **没有层级之别**：都是 `RoleComponent` 的普通实现、都按角色实例装配、都只能经自己的服务集取用。
 * ⇒ ★ **不得因为「名字带框架」就把它们当特例去碰聚合根 `Role`** —— 它们不持有也不得直改聚合根
 * （这条曾是本仓库一处真实误判的来源：**本包改名前那个带「框架」字样的旧包名**曾让人以为它们可以越权，
 * 并因此制造了一条 `core → 旧包` 的反向依赖 ✗ —— 改名就是为了让这类误判不再可能）。
 *
 * <h2>它解决什么</h2>
 * 容器（{@code RoleInstance}）需要**内建服务组件**（能量 / SanTE / 生命 / buff / 计时 / 物品渲染），
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

    /**
     * **「框架必然提供的服务类型」清单**（原住在 `core/Role.FRAMEWORK_PROVIDED_TYPES`，已迁来 —— 本类
     * 才是**内建服务组件**的**唯一落点**，这份知识不该留在角色模板里）。
     * <p>★ **字段名保留**（`FRAMEWORK_PROVIDED_TYPES` = 既有公开面，不改名）；但**语义上它不是「框架级」**：
     * 这些类型与角色内容组件**无层级之别**，本清单只用于「供给面判定」⇒ **不可据此碰聚合根** ✓。
     *
     * <p><b>它解决什么问题</b>：{@code core/Role#verifyDependencies()} 只看**模板的组件表**；而内建
     * 服务组件是**按角色实例**装配的（每实例一份）⇒ 模板侧永远看不见它们。于是任何组件写
     * {@code requires(EnergyComponent.class)} 这类声明都会被误报成"缺必需依赖" ⇒ 该角色**被错误地拒绝注册**。
     *
     * <p><b>语义（写死）</b>：列在本集合里的类型 = **框架保证在角色实例上必然提供**的类型 ⇒ 声明它们
     * 的组件**不算缺依赖**（检查放行）。它**只影响"供给面"的判定**，不影响任何运行期行为：组件仍
     * 按 {@code svc().components().get(...)} 自己去取（取不到是另一回事，属运行期）。
     *
     * <p><b>与本类的关系</b>：清单**逐条对应**上面 {@code ID_*} 所标识的内建服务组件（技能 / 被动 /
     * 主武器等**角色内容**一律不在此列 ⇒ 它们之间的依赖声明照旧按模板组件表判定）。
     */
    public static final Set<Class<? extends RoleComponent>> FRAMEWORK_PROVIDED_TYPES = Set.of(
            EnergyComponent.class,
            SanTEComponent.class,
            VitalsComponent.class,
            BuffComponent.class,
            TimerComponent.class);

    /**
     * **能量上限（组件侧配置 · 占位值）**。
     *
     * <p><b>真值应来自角色配置</b>：本值是在「角色模板不再持有 HP/SanTE/Energy」之后，
     * 为不丢失既有行为而落的**占位**（可玩的 {@code meiqihezi} 声明值是 100 ⇒ 逐字不变）。
     *
     * <p><b>为什么统一取 100 没有可观察差异</b>（逐条论证，不只是一个结论）：
     * <ol>
     *   <li>只有 {@code meiqihezi} 声明了 100；{@code red} 与 {@code selfUpdateExample} 声明的是 <b>0</b>。</li>
     *   <li>{@code EnergyComponent} 的构造是 {@code this.max = Math.max(0, max)} 且**构造期即置满**
     *       （{@code current = this.max}）⇒ 上限定的是"满值"。</li>
     *   <li>能量**只在被消耗时可见**：{@code red} 的四个主动件声明耗能全为 0，且
     *       {@code energyCost} 的声明值**不参与** {@code tryConsume}（那是 {@code Skill} 家族的数据源）
     *       ⇒ red 从不扣能 ⇒ 它的 {@code current} 恒为初值 ⇒ <b>0 → 100 不可观察</b>。</li>
     *   <li>{@code selfUpdateExample} 只有一件自刷新示例技能（耗能 0）⇒ 同理不可观察。</li>
     *   <li>{@code meiqihezi} 原本就是 100 ⇒ 逐字不变。</li>
     * </ol>
     * ⇒ 行为面：**无可观察差异**；配置面：逐角色上限需另立卡由组件侧配置恢复。
     */
    public static final int ENERGY_MAX = 100;

    /**
     * **SanTE 上限（组件侧配置 · 占位值）**。
     *
     * <p>三个角色的声明值**都是 100** ⇒ 统一取 100 ⇒ **行为逐字不变**（无任何差异可比）；
     * 真值日后由组件侧配置提供。
     */
    public static final int SANTE_MAX = 100;

    private ServiceComponents() {
    }

    /**
     * **构造全部内建服务组件**（顺序见下方注释；返回**构造顺序**的不可变列表 ✓）。
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

        // ② 能量：上限取**组件侧配置**（角色模板不再持有能量上限）；**一次变更 ⇒ 请求重绘一次**（无条件 ✓）。
        EnergyComponent energy = new EnergyComponent(ID_ENERGY, in.servicesFor().apply(ID_ENERGY),
                ENERGY_MAX,
                change -> hotbarRender.markDirty());

        // ③ SanTE：以**本组件自身**为 owner 登记平台侧监听 ⇒ 写入路径直调这一条；
        //    订阅者仍归容器的派发边界（真变化闸门 / 逐监听器隔离 / 重入合并都在那里 ✓）。
        SanTEComponent sante = new SanTEComponent(ID_SANTE, in.servicesFor().apply(ID_SANTE),
                SANTE_MAX);
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

    // ───────── 容器侧的**服务取用入口**：容器只按 id 取到通用面，具体动作在这里完成 ─────────
    // ★ 为什么放在本文件：它是**唯一**知道"哪个 id 是哪个组件类"的地方（组件集合的清单）⇒
    //   容器侧因此不再出现任何具体组件类的 `.class` 字面量，也不再有第二份同值 id 常量 ✓。
    // ★ 语义与容器此前的"按 id 取 + 类型不符即跳过"**逐字等价**：id 取不到 / 类型不符 ⇒ 不做任何事；
    //   读口在未命中时回各自既定的回退值（见各方法的 {@code @return}）✓。

    /** 置脏渲染（幂等；**不写物品**）。未命中 ⇒ 无操作。 */
    public static void renderMarkDirty(RoleComponent component) {
        if (component instanceof HotbarRenderComponent render) {
            render.markDirty();
        }
    }

    /** 帧末刷新（判脏 → 写物品 → 清脏；顺序不可交换）。未命中 ⇒ 无操作。 */
    public static void renderFlush(RoleComponent component) {
        if (component instanceof HotbarRenderComponent render) {
            render.flush();
        }
    }

    /** 同步首刷（选角色瞬间热键栏即就绪、零延迟）。未命中 ⇒ 无操作。 */
    public static void renderFirstFlush(RoleComponent component) {
        if (component instanceof HotbarRenderComponent render) {
            render.firstFlush();
        }
    }

    /** 取"本帧真的改了东西"这一读数并**清除**它。未命中 ⇒ {@code false}。 */
    public static boolean renderConsumeChanged(RoleComponent component) {
        return component instanceof HotbarRenderComponent render && render.consumeChanged();
    }

    /** 当前能量读数。未命中 ⇒ {@code 0}（与容器既有回退值相同）。 */
    public static int energyCurrent(RoleComponent component) {
        return component instanceof EnergyComponent energy ? energy.current() : 0;
    }

    /** 写当前能量（clamp 在组件内）。未命中 ⇒ 无操作。 */
    public static void energySet(RoleComponent component, int value) {
        if (component instanceof EnergyComponent energy) {
            energy.set(value);
        }
    }

    /** 治疗（clamp 策略在生命组件内）。未命中 ⇒ 无操作。 */
    public static void heal(RoleComponent component, double amount) {
        if (component instanceof VitalsComponent vitals) {
            vitals.heal(amount);
        }
    }

    /**
     * **恢复满血**（读到什么就设成什么 —— 上限的修改由调用方在此之前施加）。
     * <p>供**框架侧**在激活期调用，使「生命的一切写入」都经生命组件（见
     * {@link VitalsComponent#restoreFull(Player)})。未命中该组件 ⇒ 无操作。
     * @param component 生命组件的通用面（按 {@link #ID_VITALS} 取到）
     * @param target    目标玩家
     */
    public static void vitalsRestoreFull(RoleComponent component, Player target) {
        if (component instanceof VitalsComponent vitals) {
            vitals.restoreFull(target);
        }
    }

    /**
     * **施加角色的生命上限修饰符**（上限归生命组件；值取组件侧配置）。
     * <p>作用对象 = 该组件自己的玩家（见 {@link VitalsComponent#applyHealthModifier})。未命中 ⇒ 无操作。
     * @param component 生命组件的通用面（按 {@link #ID_VITALS} 取到）
     * @param key       上限修饰符的键
     */
    public static void vitalsApplyHealthModifier(RoleComponent component, NamespacedKey key) {
        if (component instanceof VitalsComponent vitals) {
            vitals.applyHealthModifier(key, VitalsComponent.ROLE_HEALTH_CAP);
        }
    }

    /**
     * **移除角色的生命上限修饰符**（与施加同属"上限"⇒ 一并归生命组件）。未命中 ⇒ 无操作。
     * @param component 生命组件的通用面（按 {@link #ID_VITALS} 取到）
     * @param key       上限修饰符的键
     */
    public static void vitalsRemoveHealthModifier(RoleComponent component, NamespacedKey key) {
        if (component instanceof VitalsComponent vitals) {
            vitals.removeHealthModifier(key);
        }
    }

    /** 启动 buff 记账表的每 tick 更新。未命中 ⇒ 无操作。 */
    public static void startBuffUpdater(RoleComponent component) {
        if (component instanceof BuffComponent buffs) {
            buffs.manager().startUpdater();
        }
    }

    /** 施加药水效果并记入账本。未命中 ⇒ 无操作。 */
    public static void applyPotionEffect(RoleComponent component, PotionEffect effect) {
        if (component instanceof BuffComponent buffs) {
            buffs.applyPotionEffect(effect);
        }
    }

    /** 只回收本系统记账过的药水效果。未命中 ⇒ 无操作。 */
    public static void clearAppliedPotionEffects(RoleComponent component) {
        if (component instanceof BuffComponent buffs) {
            buffs.clearAppliedPotionEffects();
        }
    }

    /** 清空 buff 记账表。未命中 ⇒ 无操作。 */
    public static void clearBuffLedger(RoleComponent component) {
        if (component instanceof BuffComponent buffs) {
            buffs.manager().clearAll();
        }
    }

    /** 按请求者取消其名下的全部计时句柄（返回值沿用既有调用点的"不看返回值"口径）。未命中 ⇒ 无操作。 */
    public static void cancelTimersOf(RoleComponent component, RoleComponent requester) {
        if (component instanceof TimerComponent timers) {
            timers.cancelAllOf(requester);
        }
    }

    /**
     * **请求重绘**（组件侧的"我要重绘"入口；**只置脏、不写物品** —— 写物品仍只由帧末 flush 完成）。
     * <p>与 {@link #renderMarkDirty} 的落点相同（置脏通道的终点是渲染组件自己的脏标记），
     * 差别只在入口形态：本入口对应"请求重绘"这一语义，供**框架侧**调用。未命中 ⇒ 无操作。
     */
    public static void renderRequestRepaint(RoleComponent component) {
        if (component instanceof HotbarRenderComponent render) {
            render.requestRepaint();
        }
    }

    /**
     * 以**计时组件**登记一个重复任务（未命中该组件 ⇒ 回 {@code null}）。
     * @param component         计时组件的通用面（按 {@link #ID_TIMERS} 取到）
     * @param requester         任务归属的请求者（资源按请求者登记）
     * @param initialDelayTicks 首次延迟（刻）
     * @param periodTicks       周期（刻）
     * @param task              任务体
     * @return 任务句柄；未命中计时组件 ⇒ {@code null}
     */
    public static Task scheduleRepeating(RoleComponent component, RoleComponent requester,
                                         long initialDelayTicks, long periodTicks, Runnable task) {
        return component instanceof TimerComponent timers
                ? timers.runRepeating(requester, initialDelayTicks, periodTicks, task)
                : null;
    }
}
