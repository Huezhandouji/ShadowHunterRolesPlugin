package com.shadowHunterRolesPlugin.core.ports;

/**
 * 冷却端口：构造期**已绑定本组件 id**（组件不再传 id）。
 * 正常路径组件不调 {@code start} —— 返回 {@code CAST} 让框架按声明值启动；
 * {@code start} 只作逃生舱（如引导期刷新）。
 */
public interface CooldownPort {

    boolean isReady();

    int remainingTicks();

    void start(int ticks);

    void end();
}
