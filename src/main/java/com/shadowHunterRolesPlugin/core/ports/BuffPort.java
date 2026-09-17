package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.BuffType;
import org.bukkit.potion.PotionEffectType;

/**
 * 禁用/减益闸门端口（{@code canCastSkill} = 非 STUN 且非 SILENCE；{@code canUseMainWeapon} = 非 STUN）。
 * <p><b>R-1（硬约束 §18/§20 同期裁定）</b>：{@link #applyPotionEffect} 是本端口**唯一新增的方法**
 * （方法数 5 → 6），{@code ComponentServices} **成员数仍为 10**。它替代组件侧直接摸
 * {@code RoleInstance.applyPotionEffect}，且**必须走同一条"已记账"路径**（阶段 1 的药水记账：
 * 效果登记以便角色清理时回收）—— **绕过记账 = 药水泄漏 = 回归**。
 */
public interface BuffPort {

    boolean canCastSkill();

    boolean canUseMainWeapon();

    void add(BuffType type, int durationTicks);

    boolean has(BuffType type);

    long remainingTicks(BuffType type);

    /** 施加药水效果（**组件侧唯一入口**；实现委托 {@code RoleInstance.applyPotionEffect} 的已记账路径）。 */
    void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier);
}
