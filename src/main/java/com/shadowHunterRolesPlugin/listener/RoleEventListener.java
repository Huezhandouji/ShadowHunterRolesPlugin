package com.shadowHunterRolesPlugin.listener;

import org.bukkit.event.Listener;

/**
 * 角色事件监听器的**空壳**：本类当前**没有任何 handler**，也不转发任何东西。
 * <ul>
 *   <li><b>SanTE 变更</b>：由组件**直派** —— {@code SanTEComponent} 的写入路径 → 容器构造期登记的
 *       平台侧监听器 → 容器注入的 {@code dispatchSanTEChange}（真变化闸门 / 逐监听器故障隔离 / 重入合并
 *       三条都在那条边界上）。{@code RoleInstance.setCurrentSanTE} 转发视图
 *       **已删除** ⇒ 真实路径只剩上面这一条。</li>
 *   <li><b>能量变更</b>：组件侧钩子**无实现者** ⇒ 转发与容器空壳入口一并删除。</li>
 * </ul>
 * <p>变更通知一律**只经组件自己的监听器列表**（{@code addListener} + JDK {@code Consumer}）✓
 * ⇒ 不再发布任何平台事件，本类也无事可做。
 * <p>类与主类里的注册点保留（注册一个无 handler 的监听器是 no-op）✓。
 */
public class RoleEventListener implements Listener {

}