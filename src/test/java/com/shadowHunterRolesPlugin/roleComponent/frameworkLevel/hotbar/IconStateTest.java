package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.hotbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * T-2（t50 §4）：锁住 {@link IconState} 的两件事 —— **判定顺序** 与 **三态材质映射**。
 * <p>为什么是单测（t50 §1 第 4/5 条）：判定与映射都在 {@link IconState} 这个**纯值对象**上
 * （`of(...)` / `material(...)` 都不碰 Bukkit 注册表、不构物品）⇒ 离线可跑；运行侧只剩
 * "值出现在成品物品上"（外观取证，见 t43/t46 的三态 × 两 kind 六维对照）。
 * <p>**顺序是这里最贵的判据**：`of(true,false,5,10)` 同时满足"被禁用"与"能量不足"，
 * 断言它得 `DISABLED` 就把"禁用先于能量不足"钉住了（若两段次序被交换 ⇒ 立即红）。
 */
public class IconStateTest {

    /** 四段判定：冷却 → 禁用 → 能量不足 → 就绪。 */
    @Test
    public void decisionOrderIsCooldownThenDisabledThenEnergyLackThenReady() {
        assertEquals("ready=false 必须判冷却（第一段）", IconState.COOLDOWN, IconState.of(false, false, 0, 10));
        assertEquals("闸门不放行必须判禁用（第二段，先于能量）", IconState.DISABLED, IconState.of(true, false, 0, 10));
        assertEquals("能量不足必须判 ENERGY_LACK（第三段）", IconState.ENERGY_LACK, IconState.of(true, true, 5, 10));
        assertEquals("四段全过才是就绪", IconState.READY, IconState.of(true, true, 50, 0));
    }

    /** 边界：能量**恰好等于**消耗 ⇒ 就绪（判据是 `energy < energyCost`，不是 `<=`）。 */
    @Test
    public void energyExactlyAtCostIsReady() {
        assertEquals(IconState.READY, IconState.of(true, true, 10, 10));
    }

    /** 逐段独立读数（每段只让一个条件不满足）。 */
    @Test
    public void eachSegmentAloneIsSufficient() {
        assertEquals(IconState.COOLDOWN, IconState.of(false, true, 50, 10));
        assertEquals(IconState.DISABLED, IconState.of(true, false, 50, 10));
        assertEquals(IconState.ENERGY_LACK, IconState.of(true, true, 5, 10));
        assertEquals(IconState.READY, IconState.of(true, true, 50, 0));
    }

    /** 三态材质映射：就绪沿用传入材质；禁用 = BARRIER；冷却与能量不足共用 STRUCTURE_VOID。 */
    @Test
    public void materialMappingIsFrozen() {
        assertEquals("就绪态必须沿用基础物品材质（不覆盖）", org.bukkit.Material.STONE,
                IconState.READY.material(org.bukkit.Material.STONE));
        assertEquals(org.bukkit.Material.BARRIER, IconState.DISABLED.material(org.bukkit.Material.STONE));
        assertEquals(org.bukkit.Material.STRUCTURE_VOID, IconState.COOLDOWN.material(org.bukkit.Material.STONE));
        assertEquals("冷却与能量不足共用同一材质（既有形态）", org.bukkit.Material.STRUCTURE_VOID,
                IconState.ENERGY_LACK.material(org.bukkit.Material.STONE));
    }

    /** 映射不依赖传入的"就绪材质"具体是什么（禁用心智：别写成 `if (readyMaterial == STONE)`）。 */
    @Test
    public void nonReadyMappingIgnoresTheReadyMaterial() {
        assertEquals(org.bukkit.Material.BARRIER, IconState.DISABLED.material(org.bukkit.Material.DIAMOND));
        assertEquals(org.bukkit.Material.STRUCTURE_VOID, IconState.COOLDOWN.material(org.bukkit.Material.DIAMOND));
    }
}
