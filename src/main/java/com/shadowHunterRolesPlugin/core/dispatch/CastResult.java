package com.shadowHunterRolesPlugin.core.dispatch;

/**
 * 框架与组件之间唯一的结果语言（设计 §10）。
 * 不设"自定义消息"通道：反馈文本由框架持有，组件只表达原因。
 */
public enum CastResult {

    /** 施放成功 → 框架按 {@code HotbarItem#getCooldownTicks()} 启动冷却。 */
    SUCCEED,

    /** 组件已自行处理（含"不做事"）→ 框架不动冷却。 */
    NO_COOLDOWN,

    /** 被禁用（沉默/眩晕）→ 框架给反馈（action bar，冷却中静默）。 */
    REJECTED_DISABLED,

    /** 能量不足 → 框架给反馈。 */
    REJECTED_NO_ENERGY
}
