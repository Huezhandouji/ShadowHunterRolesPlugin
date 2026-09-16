package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.ports.BuffPort;

/** {@link BuffPort} 的独立适配器：闸门语义仍由 {@code BuffManager} 单一实现。 */
final class BuffPortImpl implements BuffPort {

    private final RoleInstance owner;

    BuffPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public boolean canCastSkill() {
        return owner.getBuffManager().canCastSkill();
    }

    @Override
    public boolean canUseMainWeapon() {
        return owner.getBuffManager().canUseMainWeapon();
    }

    @Override
    public void add(BuffType type, int durationTicks) {
        owner.getBuffManager().addBuff(type, durationTicks);
    }

    @Override
    public boolean has(BuffType type) {
        return owner.getBuffManager().hasBuff(type);
    }

    @Override
    public long remainingTicks(BuffType type) {
        return owner.getBuffManager().getRemainingTicks(type);
    }
}
