package com.shadowHunterRolesPlugin.core.ports;

/**
 * 组件上下文：新侧组件的**唯一**平台入口（设计 §5）。
 * <p><b> 起为 3 个成员</b>：{@code self}（玩家实例面）· {@code components}（组件查找 + 动态增删）
 * · {@code roleInfo}（聚合根只读服务面）。此前的八个白名单成员（能量 / SanTE / 生命 / 冷却 / buff / 阵营 /
 * 伤害 / 计时）**已随其端口一并删除** —— 那八件事一律**直接用组件本身**
 * （{@code svc().components().get(...)} 或基类/组件的强类型读口），服务集不再转发它们。
 * <p>更早的三个成员（key/logger/hotbar）也早已删除：零使用者。
 * <p>
 * 由容器经 {@code roleComponent.ComponentFactory} 在**构造期**注入：
 * 组件在 {@code awake()} 之前即持有本记录，且不存在"创建后再注入"的中间态。
 */
public record ComponentServices(
        Self self,
        ComponentLookup components,
 //（A2）：角色信息服务 —— 聚合根（Role）的只读服务面（阵营读取 + 两个行为）。
        RoleInfo roleInfo
) {
}
