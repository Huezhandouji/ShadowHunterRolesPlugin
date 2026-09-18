package com.shadowHunterRolesPlugin.core.ports;

/**
 * 冷却端口：构造期**已绑定本组件 id**（组件不再传 id）。
 * <p>
 * 阶段 4 追补（冷却自管理）后的语义：
 * <ul>
 *   <li>{@link #start(int)}：**由组件在施放成功处显式调用**（框架不再代启动）；以**本次调用时刻**重算到期 tick
 *       ⇒ 重复开启 = 覆盖旧值（**不叠加、不取最大**）；旧段**尚未到期**时会通知组件
 *       {@code onCooldownEnd(RESTARTED)}；</li>
 *   <li>{@link #end()}：**仅当本组件处于冷却中**才生效 ⇒ 清除条目 + 通知
 *       {@code onCooldownEnd(ENDED_BY_COMPONENT)} + 一次可见刷新，并返回 {@code true}；
 *       不在冷却中 ⇒ 返回 {@code false} 且**无副作用**（幂等）；</li>
 *   <li>{@link #isReady()} / {@link #remainingTicks()}：查询语义不变。</li>
 * </ul>
 */
public interface CooldownPort {

    boolean isReady();

    int remainingTicks();

    void start(int ticks);

    boolean end();
}