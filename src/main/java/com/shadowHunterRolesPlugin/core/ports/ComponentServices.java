package com.shadowHunterRolesPlugin.core.ports;

/**
 * 组件上下文：新侧组件的**唯一**平台入口（设计 §5）。
 * <b>恰好 10 个成员，一个都不许多</b>（key/logger/hotbar 三个成员已被删除：零使用者）。
 * <p>
 * 由容器经 {@code roleComponent.ComponentFactory} 在**构造期**注入（阶段 4 收尾批⑤）：
 * 组件在 {@code awake()} 之前即持有本记录，且不存在"创建后再注入"的中间态。
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
