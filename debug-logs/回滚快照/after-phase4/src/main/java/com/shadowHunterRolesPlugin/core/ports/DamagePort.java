package com.shadowHunterRolesPlugin.core.ports;

import org.bukkit.entity.LivingEntity;

/**
 * 伤害原语端口：唯一的 PDC 副作用点（替代静态 {@code DamageUtil.*}）。
 * <b>施动者可空</b>：{@code DefaultSanTEZeroPunishment} 传的是 {@code null} source，
 * 为空时跳过创造/旁观检查与"记录最后伤害者"——写成 {@code @NonNull} 会直接改行为。
 */
public interface DamagePort {

    void trueDamage(LivingEntity victim, LivingEntity source, double amount);

    void trueDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength);

    void physicalDamage(LivingEntity victim, LivingEntity source, double amount);

    void physicalDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength);
}
