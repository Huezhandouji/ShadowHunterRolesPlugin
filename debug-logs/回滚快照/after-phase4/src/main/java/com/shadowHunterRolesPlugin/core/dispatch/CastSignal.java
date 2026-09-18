package com.shadowHunterRolesPlugin.core.dispatch;

/** 施放信号：只带这一次的数据（不可变）。施动者永远是 {@code svc.self().player()}。 */
public record CastSignal(CastTrigger trigger) {
}
