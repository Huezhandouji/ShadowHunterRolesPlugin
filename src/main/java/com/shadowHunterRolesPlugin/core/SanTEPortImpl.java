package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.SanTEPort;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;

/**
 * {@link SanTEPort} 的独立适配器（阶段 10 · t63 · A2）：**纯转发**到 {@link SanTEComponent} ——
 * SanTE 真值与 clamp 在组件里，本类不持有任何状态 ✗。
 * <p>第 3 步之前的**临时兼容层**；既有调用点（{@code svc().sante()}）一字未动。
 */
final class SanTEPortImpl implements SanTEPort {

    private final RoleInstance owner;

    SanTEPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    private SanTEComponent component() {
        return owner.santeComponent();
    }

    @Override
    public void gain(int amount) {
        component().gain(amount);
    }

    @Override
    public void decrease(int amount) {
        component().decrease(amount);
    }

    @Override
    public void set(int value) {
        component().set(value);
    }

    @Override
    public int current() {
        return component().current();
    }

    @Override
    public int max() {
        return component().max();
    }
}
