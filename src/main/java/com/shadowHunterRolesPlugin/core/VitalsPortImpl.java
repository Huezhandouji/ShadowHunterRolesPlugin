package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.VitalsPort;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;

/**
 * {@link VitalsPort} 的独立适配器（阶段 10 · t63 · A2）：**纯转发**到 {@link VitalsComponent} ——
 * clamp 策略的唯一实现已搬到组件，本类不持有任何状态 ✗。
 * <p>第 3 步之前的**临时兼容层**；既有调用点一字未动。
 */
final class VitalsPortImpl implements VitalsPort {

    private final RoleInstance owner;

    VitalsPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public void heal(double amount) {
        owner.vitalsComponent().heal(amount);
    }
}
