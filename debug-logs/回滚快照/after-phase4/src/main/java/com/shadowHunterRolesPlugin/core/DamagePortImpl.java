package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.DamagePort;
import org.bukkit.entity.LivingEntity;

/** {@link DamagePort} 的独立适配器：把静态 {@code DamageUtil.*} 收在一处（含 PDC 副作用）。 */
final class DamagePortImpl implements DamagePort {

    @Override
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount) {
        DamageUtil.dealtTrueDamage(victim, source, amount);
    }

    @Override
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        DamageUtil.dealtTrueDamage(victim, source, amount, knockbackStrength);
    }

    @Override
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount) {
        DamageUtil.dealtPhysicalDamage(victim, source, amount);
    }

    @Override
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        DamageUtil.dealtPhysicalDamage(victim, source, amount, knockbackStrength);
    }
}
