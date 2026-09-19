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

    /**
     * 施加药水效果（**标志位保真版**，设计文档 §5.1 R-1 方法族）：当旧写法显式指定了
     * {@code ambient}/{@code particles}（如 {@code new PotionEffect(type, dur, amp, true, false)}）时必须用本重载，
     * 否则会引入**未申报的可见变化**（粒子漩涡/图标明暗）。实现同样走已记账路径：
     * {@code owner.applyPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles))}。
     */
    void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles);
}
