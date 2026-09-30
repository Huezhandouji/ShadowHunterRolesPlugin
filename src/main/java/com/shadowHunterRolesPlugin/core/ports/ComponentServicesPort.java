package com.shadowHunterRolesPlugin.core.ports;

/**
 * 组件上下文：新侧组件的唯一平台入口。
 * <p>现为 3 个成员：{@code self}（玩家实例面）· {@code components}（组件查找 + 动态增删）
 * · {@code roleInfo}（聚合根只读服务面）。原先白名单里的能量 / SanTE / 生命 / 冷却 / buff / 阵营 /
 * 伤害 / 计时八件事一律直接用组件本身（{@code svc().components().get(...)} 或基类/组件的强类型读口），服务集不再转发它们。
 * <p>由容器经 {@code roleComponent.ComponentFactory} 在构造期注入：组件在 {@code awake()} 之前
 * 即持有本记录，且不存在"创建后再注入"的中间态。
 */
public record ComponentServicesPort(
        SelfPort self,
        ComponentLookupPort components,
 //角色信息服务 —— 聚合根（Role）的只读服务面（阵营读取 + 两个行为）。
        RoleInfoPort roleInfo
) {
}
