package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.EnergyPort;

/** {@link EnergyPort} 的独立适配器：{@code tryConsume} = 检查 + 扣减合一。 */
final class EnergyPortImpl implements EnergyPort {

    private final RoleInstance owner;

    EnergyPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public int current() {
        return owner.getCurrentEnergy();
    }

    @Override
    public boolean tryConsume(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (owner.getCurrentEnergy() < amount) {
            return false;
        }
        owner.decreaseEnergy(amount);
        return true;
    }

    @Override
    public void gain(int amount) {
        owner.increaseEnergy(amount);
    }
}
