package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 「这个组件有冷却」的能力接口（阶段 6；**阶段 8 增补状态读数**）。
 * <p>本接口承载**冷却的声明值与状态**两件事：
 * <ul>
 *   <li>{@link #getCooldownTicks()} —— 声明时长（由描述符给出；{@code 0} = 不进冷却）；</li>
 *   <li>{@link #isCooling()} —— **冷却状态读数**（阶段 8 新增，供框架驱动"秒数刷新"的节拍）。</li>
 * </ul>
 * <b>为什么 <code>isCooling()</code> 落在本接口</b>（用户裁定 C-14：强依赖的能力应合并）：
 * {@code isCooling()} 是**状态**，它读的正是本接口的面（"有没有冷却这回事"）；
 * **阶段 13 · t105 起，冷却结束回调（原独立能力接口）已整体删除** ⇒ 本接口只剩"声明时长 + 状态读数"两件事 ✓。
 * <p>实现方式（阶段 13 · t105）：{@code roleComponent/ActiveComponent}（组件基类）用**每实例的冷却字段**
 * 给出唯一实现（状态归组件、框架只**转问** ✗），
 * 只写 {@link HotbarPresentable#specification()} 的组件无需手写本方法。
 */
public interface CooldownBearing {

    /** 冷却声明时长（tick）；{@code 0} = 不进冷却。 */
    int getCooldownTicks();

    /**
     * 冷却是否**正在进行**（本实例的冷却未到期）。
     * <p>与组件侧的 `isCoolingDown()` **同义**（阶段 13 · t105 起，本读数就是它的对外别名 ✓）；
     * "没有冷却这回事"（声明值 ≤ 0 的被动）⇒ 恒 {@code false} ✓。
     * <p>框架的唯一消费者 = 帧末 flush 的**入口条件**（"有占栏位组件在冷却 ⇒ 本 tick 至少刷一次"）：
     * 装饰搬进组件之后，框架只剩下这条读口来维持技能名里 {@code x.xs} 的逐刻递减（不补则秒数停刷 = 可见回归）。
     */
    boolean isCooling();
}
