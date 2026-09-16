package com.shadowHunterRolesPlugin.core.ports;

/**
 * 组件上下文：新侧组件的**唯一**平台入口（设计 §5）。
 * <b>恰好 10 个成员，一个都不许多</b>（key/logger/hotbar 三个成员已被删除：零使用者）。
 * <p>
 * 过渡期（阶段 4 批次迁移）由容器在 {@code supplier.get()} 之后立刻
 * {@code RoleComponent.bind(services)} 注入；终态改为 {@code ComponentFactory} 构造期注入并删除 bind。
 */
public record ComponentServices(
        Self self,
        EnergyPort energy,
        SanTEPort sante,
        VitalsPort vitals,
        CooldownPort cooldowns,
        BuffPort buffs,
        FactionPort factions,
        DamagePort damage,
        TimerPort timers,
        ComponentLookup components
) {
}
