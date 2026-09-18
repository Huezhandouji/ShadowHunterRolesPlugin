package com.shadowHunterRolesPlugin.core.dispatch;

/** 能力接口：能被 L/R/Q 触发（技能与主武器实现）。 */
public interface HotbarActionable {

    CastResult onCast(CastSignal signal);
}
