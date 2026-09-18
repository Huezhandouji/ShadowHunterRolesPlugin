package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.SanTEPort;

/** {@link SanTEPort} 的独立适配器。 */
final class SanTEPortImpl implements SanTEPort {

    private final RoleInstance owner;

    SanTEPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public void gain(int amount) {
        owner.increaseSanTE(amount);
    }

    @Override
    public void decrease(int amount) {
        owner.decreaseSanTE(amount);
    }

    @Override
    public void set(int value) {
        owner.setCurrentSanTE(value);
    }

    @Override
    public int current() {
        return owner.getCurrentSanTE();
    }

    @Override
    public int max() {
        return owner.getMaxSanTE();
    }
}
