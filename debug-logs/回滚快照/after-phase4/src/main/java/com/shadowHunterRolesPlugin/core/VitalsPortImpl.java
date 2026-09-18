package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.VitalsPort;

/** {@link VitalsPort} 的独立适配器：clamp 策略仍只有 {@code RoleInstance.heal} 一份实现。 */
final class VitalsPortImpl implements VitalsPort {

    private final RoleInstance owner;

    VitalsPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public void heal(double amount) {
        owner.heal(amount);
    }
}
