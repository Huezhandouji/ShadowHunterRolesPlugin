package com.shadowHunterRolesPlugin.api.event;

import java.util.UUID;

/**
 * 「角色已清除」：一个**已存在**的角色实例被移除之后发布（无角色者再清不发——那不是状态变化）。
 *
 * <p><b>触发路径</b>（全部汇于 RoleManager 的清理链，订阅方无需区分也无法区分）：
 * 手动清除（{@code /role clear} · {@code RoleAPI#clearPlayerRole}）、换角色时旧角色的回收、
 * 玩家死亡（在 {@link RoleDeathEvent} **之后**）、玩家掉线、组件故障隔离、本插件禁用时的批量回收。
 *
 * <p><b>状态一致性</b>：发布时清除已落地（实例已从表里摘除、资源已回收）——处理器里经
 * {@code RoleAPI#hasRole} 现查即得 {@code false}。
 *
 * <p><b>换角色 = Clear + Set</b>：订阅方若同时消费 {@link RoleSetEvent}，按到达顺序
 * {@code Clear(旧) → Set(新)} 维护视图即可，两条事件描述的是同一次换装的两个事实。
 *
 * <p>只读事件（见 {@link RoleEvent} 类注）；由本插件内部构造发布，下游只消费。
 */
public final class RoleClearEvent extends RoleEvent {

    /**
     * @param playerUuid 被清除角色的玩家
     * @param roleId     被清除角色的登记 id（防御性口径：实例上取不到角色时为 {@code null}，
     *                   正常路径恒非 null）
     */
    public RoleClearEvent(UUID playerUuid, String roleId) {
        super(playerUuid, roleId);
    }
}
