package com.shadowHunterRolesPlugin.api;

/**
 * 事件订阅令牌（{@code RoleAPI#subscribeEvents} 的返回值）：唯一用途是退订。
 *
 * <p><b>为什么要有令牌</b>：外部插件的订阅必须能随它自己停用而解除——不退订的
 * {@code Consumer} 会被一直回调（本插件只隔离其异常，不替它判断"该不该继续收"）。
 * 订阅方应在自己的 {@code onDisable} 里 {@link #unsubscribe()}。
 *
 * <p>{@link #unsubscribe()} 幂等：重复调用为空操作。令牌只对产生它的那次订阅有效。
 */
public interface RoleEventSubscription {

    /** 退订；已退订再调用为空操作。 */
    void unsubscribe();
}
