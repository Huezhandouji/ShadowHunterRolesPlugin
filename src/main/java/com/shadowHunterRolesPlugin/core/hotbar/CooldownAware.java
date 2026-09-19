package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 「这个组件关心冷却结束」的能力接口（阶段 6 · 派发面能力化第二刀）。
 * <p>承载两件迁出物（语义与常量名**逐字不变**）：
 * <ul>
 *   <li>{@link #onCooldownEnd(CooldownEndReason)} —— 冷却结束回调（默认空实现，与迁移前
 *       {@code ActiveComponent} 的默认实现逐字等价 ⇒ 既有组件零改动即编译）；</li>
 *   <li>嵌套枚举 {@link CooldownEndReason} —— 冷却结束的原因（D4 三分法）。</li>
 * </ul>
 * <p>为什么放在这里：派发点 {@code RoleInstance#dispatchCooldownEnd} 过去要求 {@code ActiveComponent}
 * （继承关系），现在只要求本接口 ⇒ 「不必继承、按需实现能力」在**派发面**同样成立。
 * <p>契约沿用迁移前：允许在回调里再 {@code start(...)}（例如"到点立刻接下一段"）；容器**先移除冷却条目、
 * 再回调** ⇒ 回调内再 {@code end()} 只会得到 {@code false}（不会同步递归重入）。
 */
public interface CooldownAware {

    /** 冷却结束通知。默认空实现（等价于迁移前基类的默认实现）。 */
    default void onCooldownEnd(CooldownEndReason reason) {
    }

    /** 冷却结束的原因（D4 三分法）。 */
    enum CooldownEndReason {
        /** 自然到期。 */
        EXPIRED,
        /** 被组件/外部显式 {@code end()} 结束（"冷却中结束冷却"的情形）。 */
        ENDED_BY_COMPONENT,
        /** 在冷却中被再次 {@code start(...)} 顶替（重复开启按新的来）。 */
        RESTARTED
    }
}
