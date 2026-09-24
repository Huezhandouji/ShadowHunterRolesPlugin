package com.shadowHunterRolesPlugin.core.ports;

/**
 * 组件上下文：新侧组件的**唯一**平台入口（设计 §5）。
 * <b>阶段 13 · t90 起为 11 个成员</b>：在既有 10 个之外**只增** {@code roleInfo}（聚合根的只读服务面 ✓）。
 * 更早的三个成员（key/logger/hotbar）已被删除：零使用者。
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
        ComponentLookup components,
        //阶段 13 · t90（A2）：角色信息服务 —— 聚合根（Role）的只读服务面（阵营读取 + 两个行为）。
        //**本卡纯加性**：不删任何既有成员 ✗（10 → 11）；删除 factions 成员与消费者接线归后续卡。
        RoleInfo roleInfo
) {
}
