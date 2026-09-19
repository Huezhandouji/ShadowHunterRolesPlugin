package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 「这个组件耗能量」的能力接口（阶段 6）。
 * <p>{@code MainWeapon} 的 {@code energyCost ≡ 0} 不变量由本接口承载（构造器第 6 位恒传 0）；
 * 非零能量成本只有两个技能（`MeiqiheziBloodySlashSkill` = 8 / `MeiqiheziCircleSlashSkill` = 15）。
 * <p>实现方式同 {@link CooldownBearing}：由 {@link HotbarPresentable} 的 `default` 满足。
 */
public interface EnergyCosting {

    /** 施放所需能量（点）；{@code 0} = 不耗能（{@code ENERGY_LACK} 态不可达）。 */
    int getEnergyCost();
}
