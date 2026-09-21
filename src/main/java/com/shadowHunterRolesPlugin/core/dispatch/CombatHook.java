package com.shadowHunterRolesPlugin.core.dispatch;

/**
 * 能力接口：能参与攻击结算（仅主武器实现；编译期能力，不是任何运行期的"种类"判断）。
 * <p><b>阶段 8</b>：返回值由旧的施放结果枚举改为 {@code void}（理由同 {@link HotbarActionable}）。
 */
public interface CombatHook {

    void onAttack(AttackSignal signal);
}
