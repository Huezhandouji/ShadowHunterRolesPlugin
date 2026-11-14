package com.shadowHunterRolesPlugin.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 本插件对外事件流的公共基类：角色生命周期事件（{@link RoleSetEvent} / {@link RoleClearEvent} /
 * {@link RoleDeathEvent}）的只读载体。
 *
 * <p><b>只读契约</b>：事件是"已经发生的事实"的不可变快照——只有 getter、没有 setter、
 * 没有取消位；载荷是 {@link UUID}（不可变）与角色 id 字符串（不可变）。
 * 订阅方不应（也无法）改写事件；需要更多角色信息时经 {@code RoleAPI} 的查询面现查。
 *
 * <p><b>为什么继承 Bukkit 的 {@link Event} 却不走 Bukkit 分发</b>：继承是为了让下游按
 * {@code Event} 这一个类型统一消费本插件的事件流（{@code RoleAPI#subscribeEvents(Consumer<Event>)}），
 * 并对齐 Bukkit 生态的事件类型习惯（同步位、事件名）。但发布**只走本插件自己的事件总线**，
 * 不调 {@code Bukkit.getPluginManager().callEvent} ⇒ 用 {@code @EventHandler} 挂 Bukkit
 * 监听器**收不到**这些事件——要订阅请走 {@code RoleAPI#subscribeEvents}。
 * 下面的 HandlerList 仅为满足 Bukkit 事件类型的形制要求而保留的样板（空表、永不投递）。
 *
 * <p>线程契约：同步事件（{@code isAsynchronous() == false}）；订阅与发布都约定在服务器主线程。
 */
public abstract class RoleEvent extends Event {

    /** 共享空 HandlerList：形制样板，不经 Bukkit 分发（见类注）。 */
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final UUID playerUuid;
    private final String roleId;

    /**
     * 玩家标识与角色载荷在构造期一次性写入，此后只读。
     *
     * @param playerUuid 事件所属玩家
     * @param roleId     相应时刻该玩家所持角色的登记 id；玩家无角色 / 角色不可得时为 {@code null}
     *                   （本基类的三个子类在各自语义下都不会发出"无角色"的事件，
     *                   {@code null} 仅是防御性口径，见各子类的如实申报）
     */
    protected RoleEvent(UUID playerUuid, String roleId) {
        this.playerUuid = playerUuid;
        this.roleId = roleId;
    }

    /** 事件所属玩家。 */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /** 相应时刻该玩家所持角色的登记 id（语义见各子类；不可变字符串）。 */
    public String getRoleId() {
        return roleId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    /** Bukkit 形制样板（见类注：本插件的事件不经 Bukkit 分发）。 */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
