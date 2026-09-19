package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 「这个组件有冷却」的能力接口（阶段 6）。
 * <p>今天唯一读取点是 {@code ActiveComponent.getCooldownTicks()}（已由基类上移）；本接口把它作为**可组合的能力**
 * 暴露出来 ⇒ 组件按需实现，不必通过继承基类来"顺带"获得冷却能力。
 * <p>实现方式：{@code ActiveComponent} 同时实现 {@link HotbarPresentable}，其 `default` 实现即满足本接口；
 * 只写 {@link HotbarPresentable#spec()} 的组件无需手写本方法。
 */
public interface CooldownBearing {

    /** 冷却声明时长（tick）；{@code 0} = 不进冷却。 */
    int getCooldownTicks();
}
