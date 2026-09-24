package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * SanTE 组件（阶段 10 · t63 · A1 改正）：系统级能力「SanTE」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>★ 本组件持有状态与行为</b>：SanTE 真值 {@code current} 与上限 {@code max} 都在本组件里，
 * clamp 与 gain/decrease/set 的语义全在本组件内实现 —— **不再转调任何旧端口** ✗。
 * <p><b>与容器的分工</b>：写后对平台说两句话 —— 发布 {@code SanTEChangeEvent} 与
 * 向组件广播 {@code onSanTEChange(pre, now)}（含 I-14 重入护栏）—— 由容器以 {@link ChangeSink}
 * 注入；组件只负责真值怎么变。
 * <p><b>状态唯一</b>：容器侧**不再**持有 {@code currentSanTE} 字段 ✗（只保留视图方法）。
 * <p><b>归零惩罚的钉 0 语义不变</b>：惩罚组件（{@code DefaultSanTEZeroPunishment}）仍按既有方式
 * 调 {@code svc().sante().set(0)} 逐 tick 钉 0 ⇒ 走的还是这一条 clamp + 派发路径。
 */
public class SanTEComponent extends RoleComponent {

    /** 变更通知（容器在构造期注入）：事件发布 + {@code onSanTEChange} 派发（含重入护栏）都在容器侧。 */
    public interface ChangeSink {

        /** SanTE 真值发生变化后调用（**无条件**调用：与既有"无条件事件 + 无条件派发"逐字一致）。 */
        void onSanTEChanged(int previous, int current, int max);
    }

    /**
     * **SanTE 变更的订阅者**（阶段 12 · t89 · C2 的**替代方案 (b)** · 用户裁定 ✓）。
     *
     * <h2>为什么是这个形态（而不是能力接口）</h2>
     * 用户硬规矩 **R-1**：**凡关注点已是组件 ⇒ 一律不得再为它新增能力接口** ✗。
     * SanTE 的**家**就是本组件（真值 {@code current} + 全部行为都在这里）✓ ⇒
     * 关心"SanTE 变了"的组件**向本组件订阅** ✓，而**不是**让容器按某个接口去判能力 ✗。
     * <p>（此前 `t87` 一度让容器按**能力接口**判接受集 —— 那是**接口替代组件** ✗，
     * 正是用户点名的方向错误 ⇒ 本卡把它回退 ✓。）
     *
     * <h2>契约</h2>
     * <ul>
     *   <li><b>只通知、不可否决</b> ✗：返回 {@code void} ⇒ 订阅者改不了 SanTE 的写入结果 ✓；</li>
     *   <li><b>不保证"真变化"</b>：本组件既有口径是 {@code ChangeSink} **无条件**回调 ⇒ 订阅者也**无条件**被通知 ✓
     *       —— 需要"只在真变化时动作"的订阅者**自己比较 {@code pre}/{@code now}** ✓（两个既有消费者本来就自带守卫 ✓）；</li>
     *   <li><b>顺序</b>：按**订阅先后** ⇒ 订阅者在 `awake()` 注册 ⇒ 顺序 = **组件装配序** ✓。</li>
     * </ul>
     *
     * <h2>为什么给默认空实现</h2>
     * 与迁移前 {@code RoleComponent} 上那个空实现**逐字等价** ⇒ 订阅者只覆写真正关心的那一个方法 ✓。
     */
    public interface Subscriber {

        /** SanTE 值发生变化（本组件**无条件**通知，见接口 javadoc）。默认空实现。 */
        default void onSanTEChange(int pre, int now) {
        }
    }

    /** **订阅者名单** —— 顺序 = 订阅先后 ✓（迭代序稳定 ⇒ "按装配序通知"可复现 ✓）。 */
    private final java.util.List<Subscriber> subscribers = new java.util.ArrayList<>();

    /** **订阅**（订阅者在自己的 {@code awake()} 里调用 ⇒ 时机 = 组件装配序 ✓）。幂等：重复订阅不重复登记 ✓。 */
    public void subscribe(Subscriber subscriber) {
        if (subscriber != null && !subscribers.contains(subscriber)) {
            subscribers.add(subscriber);
        }
    }

    /** **退订**（订阅者在自己的 {@code stop()} 里调用 ⇒ 与装配/回收对称 ✓）。 */
    public void unsubscribe(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    /** 当前订阅者数（诊断读口；供探针与运行级取证使用）。 */
    public int subscriberCount() {
        return subscribers.size();
    }

    /**
     * **把变更派发给订阅者**（容器在 SanTE 真值写入后调用）——
     * **取代**原先"按能力接口判接受集"的做法 ✗。
     * <p>遍历的是**名单**（不是容器注册表）⇒ 「谁关心」由**订阅**表达 ✓，不再由接口/继承表达 ✓。
     * <p><b>无条件通知</b>：与迁移前 {@code ChangeSink} 的"无条件事件 + 无条件派发"**逐字一致** ✓
     * （"只在真变化时动作"由订阅者自己比较 {@code pre}/{@code now} ✓）。
     */
    public void broadcastChange(int pre, int now) {
        for (Subscriber subscriber : subscribers) {
            subscriber.onSanTEChange(pre, now);
        }
    }

    /**
     * **逐个**把订阅者交给调用方（阶段 12 · t89）—— 供容器**保持"逐订阅者故障隔离"** ✓。
     * <p><b>为什么需要它</b>：`broadcastChange` 是一趟循环 ⇒ 若某个订阅者抛异常，
     * **排在它后面的订阅者就收不到通知** ✗。容器侧原本用的是"**逐个**经 {@code guardedCall} 调用"
     * ⇒ 只隔离抛异常的那一个、其余照常收到 ✓ —— `t87` 引入的那条性质**必须保住** ✗。
     * <p>所以这里**不**替调用方做派发决策，只提供**遍历**：由容器决定"怎么调、怎么护" ✓。
     */
    public void forEachSubscriber(java.util.function.Consumer<Subscriber> action) {
        if (action == null) {
            return;
        }
        for (Subscriber subscriber : subscribers) {
            action.accept(subscriber);
        }
    }

    private final int max;
    private final ChangeSink sink;

    /** ★ 真值：当前 SanTE（唯一持有处）。 */
    private int current;

    public SanTEComponent(String id, ComponentServices services, int max, ChangeSink sink) {
        super(id, services);
        this.max = Math.max(0, max);
        this.sink = sink != null ? sink : (previous, value, limit) -> { };
        //与既有 RoleInstance 构造期逐字一致：选角色即满 SanTE
        this.current = this.max;
    }

    /** 当前 SanTE（读口）。 */
    public int current() {
        return current;
    }

    /** SanTE 上限（构造期由容器给出）。 */
    public int max() {
        return max;
    }

    /** 直接写入（组件内 clamp；写后通知容器）。 */
    public void set(int value) {
        int previous = current;
        current = Math.clamp(value, 0, max);
        sink.onSanTEChanged(previous, current, max);
    }

    /** 增加 SanTE（内部按上限 clamp）。 */
    public void gain(int amount) {
        set(current + amount);
    }

    /** 减少 SanTE（内部按 0 下限 clamp；归零惩罚由既有组件监听真变化后触发）。 */
    public void decrease(int amount) {
        set(current - amount);
    }
}
