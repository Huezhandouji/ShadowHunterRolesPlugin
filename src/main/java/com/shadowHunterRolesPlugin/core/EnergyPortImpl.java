package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.EnergyPort;
import com.shadowHunterRolesPlugin.frameworkLevel.EnergyComponent;

/**
 * {@link EnergyPort} 的独立适配器（阶段 10 · t63 · A2）：**纯转发**到 {@link EnergyComponent} ——
 * 真值与 clamp/检查扣减的行为都在组件里，本类**不持有任何状态** ✗（旧实现转发到
 * {@code RoleInstance} 的字段方法，那正是"A1 未达成"的形态）。
 * <p>本类只是第 3 步（删掉 {@code ComponentServices} 的 8 个旧成员）之前的**临时兼容层**：
 * 既有 15 个组件的调用点一字未动。
 */
final class EnergyPortImpl implements EnergyPort {

    private final RoleInstance owner;

    EnergyPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    private EnergyComponent component() {
        return owner.energyComponent();
    }

    @Override
    public int current() {
        return component().current();
    }

    @Override
    public boolean tryConsume(int amount) {
        return component().tryConsume(amount);
    }

    @Override
    public void gain(int amount) {
        component().gain(amount);
    }
}
