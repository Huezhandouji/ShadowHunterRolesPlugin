package com.shadowHunterRolesPlugin.api.event;

import java.util.UUID;

/**
 * 「角色已设置」：一次成功的角色装配（{@code /role set} · {@code RoleAPI#setPlayerRole}）
 * 之后发布——只有装配成功才发（预检失败 / 构造失败 / 激活失败都不发，玩家保持原状）。
 *
 * <p><b>载荷语义</b>：玩家 + **新**角色的登记 id。若此前已有旧角色，本事件**之前**会先发布一条
 * {@link RoleClearEvent}（旧角色沿既有清理链先行回收）⇒ 订阅方按到达顺序维护
 * 「玩家 → 角色」视图即可：{@code Clear(旧) → Set(新)}。
 *
 * <p><b>状态一致性</b>：发布时装配已全部落地（新实例已入表）——处理器里经
 * {@code RoleAPI#getPlayerRoleId} 现查即得新角色 id。
 *
 * <p>只读事件（见 {@link RoleEvent} 类注）；由本插件内部构造发布，下游只消费。
 */
public final class RoleSetEvent extends RoleEvent {

    /**
     * @param playerUuid 被设置角色的玩家
     * @param roleId     新角色的登记 id（本事件语义下恒非 null）
     */
    public RoleSetEvent(UUID playerUuid, String roleId) {
        super(playerUuid, roleId);
    }
}
