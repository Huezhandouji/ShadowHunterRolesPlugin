package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.DamagePort;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import org.bukkit.entity.LivingEntity;

/**
 * {@link DamagePort} 的独立适配器（阶段 10 · t63 · A2；**t73 Part A 改指向**）：**纯转发**到
 * {@link VitalsComponent} ——
 * 四个伤害原语（含静态 {@code DamageUtil} 的 PDC 副作用）的实现已搬到组件，本类不持有任何状态 ✗。
 * <p><b>t73 Part A 取代指向（不静默改写）</b>：本类原转发到 {@code DamageComponent}，
 * 该组件已**删除**（职责并入 {@code VitalsComponent} ⇒ 伤害与生命只有一个持有者 ✓）；
 * {@code DamagePort} 的**端口面 4 个方法一字未动** ✓。
 * <p><b>构造签名变化（如实申报）</b>：旧实现在构造期**不需要**容器（它直接调静态工具）⇒ 无 owner；
 * 现在它要找到"本实例的伤害组件"⇒ 必须拿到容器。改动点只有 {@code RoleInstance#createServices}
 * 的一行（同一 inScope 文件内）。
 */
final class DamagePortImpl implements DamagePort {

    private final RoleInstance owner;

    DamagePortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    private VitalsComponent component() {
        return owner.vitalsComponent();
    }

    @Override
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount) {
        component().trueDamage(victim, source, amount);
    }

    @Override
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        component().trueDamage(victim, source, amount, knockbackStrength);
    }

    @Override
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount) {
        component().physicalDamage(victim, source, amount);
    }

    @Override
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        component().physicalDamage(victim, source, amount, knockbackStrength);
    }
}
