package com.shadowHunterRolesPlugin.api.event;

import java.util.UUID;

/**
 * 「角色玩家死亡」：**持有角色**的玩家死亡时发布（无角色者死亡不发）。
 *
 * <p><b>载荷语义</b>：玩家 + **死亡那一刻**所持角色的登记 id——本插件"死亡即清角色"，
 * 本事件赶在清理之前发布，正是为了让订阅方拿到"死时是谁"；此后紧接着必然有一条
 * {@link RoleClearEvent}（清角色沿既有路径）⇒ 订阅方按到达顺序
 * {@code Death(死者) → Clear(死者)} 维护视图。
 *
 * <p><b>不含击杀者</b>：本事件只陈述"哪个角色死了"这一事实；"谁杀了谁"的击杀侧通知
 * 是生命组件内部的订阅名单（{@code PlayerKilledHookListener}），口径互不重叠。
 *
 * <p>只读事件（见 {@link RoleEvent} 类注）；由本插件内部构造发布，下游只消费。
 */
public final class RoleDeathEvent extends RoleEvent {

    /**
     * @param playerUuid 死亡的玩家
     * @param roleId     死亡那一刻所持角色的登记 id（防御性口径：实例上取不到角色时为 {@code null}，
     *                   正常路径恒非 null）
     */
    public RoleDeathEvent(UUID playerUuid, String roleId) {
        super(playerUuid, roleId);
    }
}
