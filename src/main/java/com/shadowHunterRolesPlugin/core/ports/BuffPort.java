package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.BuffType;

/** 禁用/减益闸门端口（{@code canCastSkill} = 非 STUN 且非 SILENCE；{@code canUseMainWeapon} = 非 STUN）。 */
public interface BuffPort {

    boolean canCastSkill();

    boolean canUseMainWeapon();

    void add(BuffType type, int durationTicks);

    boolean has(BuffType type);

    long remainingTicks(BuffType type);
}
