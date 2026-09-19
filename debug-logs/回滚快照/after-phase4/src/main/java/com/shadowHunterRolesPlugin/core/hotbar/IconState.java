package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 统一渲染器的状态判定结果（设计 §3.5 / 指南 §3.5）。
 * 判定顺序与今天的渲染代码逐字一致：冷却 → 禁用 → 能量不足 → 就绪。
 */
public enum IconState {
    COOLDOWN,
    DISABLED,
    ENERGY_LACK,
    READY
}
