package com.shadowHunterRolesPlugin.core.hotbar;

import org.bukkit.Material;

/**
 * 热键栏物品的状态判定结果（设计 §3.5 / 指南 §3.5）。
 * <p>判定顺序与接管前的渲染代码逐字一致：**冷却 → 禁用 → 能量不足 → 就绪**（见 {@link #of}）。
 * <p>判定与材质映射在本枚举上（**单点**，避免两个组件基类各写一份而漂移）；
 * 装饰序列的其余各步（名称着色 / 后缀 / 状态行 / 分隔线 / 识别键）由
 * {@code core/Skill#buildItem()} 与 {@code core/MainWeapon#buildItem()} 各自给出。
 */
public enum IconState {
    COOLDOWN,
    DISABLED,
    ENERGY_LACK,
    READY;

 /**
 * **状态判定（顺序冻结）**：冷却 → 禁用 → 能量不足 → 就绪。
 * @param ready 冷却是否就绪（{@code false} ⇒ 冷却中）
 * @param canCast 是否放行（技能 = 非 STUN 且非 SILENCE；主武器 = 非 STUN）
 * @param energy 当前能量
 * @param energyCost 声明耗能（主武器恒 {@code 0} ⇒ {@link #ENERGY_LACK} 对它不可达）
 */
    public static IconState of(boolean ready, boolean canCast, int energy, int energyCost) {
        if (!ready) {
            return IconState.COOLDOWN;
        }
        if (!canCast) {
            return IconState.DISABLED;
        }
        if (energy < energyCost) {
            return IconState.ENERGY_LACK;
        }
        return IconState.READY;
    }

 /**
 * **三态材质映射（冻结）**：就绪态沿用基础物品的材质；禁用 = {@link Material#BARRIER}；
 * 冷却与能量不足**共用** {@link Material#STRUCTURE_VOID}（接管前即如此）。
 */
    public Material material(Material readyMaterial) {
        return switch (this) {
            case READY -> readyMaterial;
            case DISABLED -> Material.BARRIER;
            case COOLDOWN, ENERGY_LACK -> Material.STRUCTURE_VOID;
        };
    }
}
