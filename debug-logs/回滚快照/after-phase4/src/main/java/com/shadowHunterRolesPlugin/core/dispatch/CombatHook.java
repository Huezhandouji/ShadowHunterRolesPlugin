package com.shadowHunterRolesPlugin.core.dispatch;

/** 能力接口：能参与攻击结算（仅主武器实现；编译期能力，不是 kind 的运行期判断）。 */
public interface CombatHook {

    CastResult onAttack(AttackSignal signal);
}
