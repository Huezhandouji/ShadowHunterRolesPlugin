package com.shadowHunterRolesPlugin.listener;

import org.bukkit.event.Listener;

/**
 * 角色事件监听器（阶段 4 追补 I-15 / F6 后**不再承担转发**）：
 * <ul>
 *   <li><b>SanTE 变更</b>：改由容器**直派**（{@code RoleInstance.setCurrentSanTE} → {@code dispatchSanTEChange}），
 *       不再经事件总线绕行；{@code SanTEChangeEvent} 的对外发布保持不变（第三方挂点）；</li>
 *   <li><b>能量变更</b>：组件侧钩子确认**无实现者**（F6 死路径）⇒ 转发与容器空壳入口一并删除；
 *       {@code EnergyChangeEvent} 的对外发布保持不变。</li>
 * </ul>
 * 因此本类当前**没有 handler**；类与注册点保留（主类里的注册行不动）。
 */
public class RoleEventListener implements Listener {

}