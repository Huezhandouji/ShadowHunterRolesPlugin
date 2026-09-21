package com.shadowHunterRolesPlugin.core.dispatch;

/**
 * 能力接口：能被 L/R/Q 触发（技能与主武器实现）。
 * <p><b>阶段 8</b>：返回值由旧的施放结果枚举改为 {@code void} —— 旧枚举**没有任何消费点**
 * （"结果枚举 x = …" / {@code instanceof} / {@code ==} / {@code switch} 四种消费形态全仓 0 行；
 * 两个调用点 {@code RoleInstance.handleCast} / {@code handleAttack} 调完即丢）。
 * 语义等价论证见交付说明：组件的 {@code if(!canCastSkill()) return;} 跳过的是**组件后续语句**，
 * 改 {@code void} 后逐字不变。
 */
public interface HotbarActionable {

    void onCast(CastSignal signal);
}
