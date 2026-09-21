package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 「这个组件有冷却」的能力接口（阶段 6；**阶段 8 增补状态读数**）。
 * <p>本接口承载**冷却的声明值与状态**两件事：
 * <ul>
 *   <li>{@link #getCooldownTicks()} —— 声明时长（由描述符给出；{@code 0} = 不进冷却）；</li>
 *   <li>{@link #isCooling()} —— **冷却状态读数**（阶段 8 新增，供框架驱动"秒数刷新"的节拍）。</li>
 * </ul>
 * <b>为什么 <code>isCooling()</code> 落在本接口、而不是冷却结束回调接口 {@link CooldownAware}</b>
 * （用户裁定 C-14：强依赖的能力应合并；只满足"实现者相同"而语义独立者保留分离）：
 * {@code isCooling()} 是**状态**，它读的正是本接口的面（"有没有冷却这回事"）；而 {@code CooldownAware}
 * 是**回调**（"冷却结束时通知我"）—— 语义独立 ⇒ 二者**不合并**，但本 javadoc 写明关系。
 * <p>实现方式：{@code roleComponent/ActiveComponent}（组件基类）用 `svc().cooldowns()` 给出唯一实现，
 * 只写 {@link HotbarPresentable#specification()} 的组件无需手写本方法。
 */
public interface CooldownBearing {

    /** 冷却声明时长（tick）；{@code 0} = 不进冷却。 */
    int getCooldownTicks();

    /**
     * 冷却是否**正在进行**（条目存在且未到期）。
     * <p>与 {@code CooldownPort#isReady()} 互补：后者对"无条目/已到期"都返回 {@code true}
     * ⇒ 本方法等价于 {@code !isReady()}（同一个单一冷却表的两种读法）。
     * <p>框架的唯一消费者 = 帧末 flush 的**入口条件**（"有占栏位组件在冷却 ⇒ 本 tick 至少刷一次"）：
     * 装饰搬进组件之后，框架只剩下这条读口来维持技能名里 {@code x.xs} 的逐刻递减（不补则秒数停刷 = 可见回归）。
     */
    boolean isCooling();
}
