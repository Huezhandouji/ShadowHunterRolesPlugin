package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.LivingEntity;

/**
 * 伤害组件（阶段 10 · t55 · A1）：系统级能力「真伤 / 物伤」的**组件形态**（每角色实例一个；
 * 用户裁定 **D-1 = A 案**：`DamagePort` 移出为伤害组件）。
 * <p><b>薄封装</b>（用户澄清「一个伤害组件，它仍然可以依赖我原来的静态伤害工具」）：内部**转调既有端口**
 * {@code DamagePort} ⇒ 静态 {@code DamageUtil}（含 PDC 副作用）仍是唯一实现，本组件只是它的**可被依赖的
 * 组件面**；静态工具与单例**不进依赖图**（用户澄清第 3 条）。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * private DamageComponent damage;
 * @Override public void awake() { damage = getComponent(DamageComponent.class); }
 * @Override public void onAttack(AttackSignal signal) {
 *     damage.physicalDamage(signal.victim(), getPlayer(), 6.0d, 0.4d);
 * }
 * }</pre>
 * <p><b>装配示例</b>：{@code builder.addComponent("damage", new DamageComponent.Specification());}（不占栏位）。
 * <p><b>已知限制（申报）</b>：本卡只**新增**组件面，`RoleAPI` 的伤害入口与既有调用点**一字不动**
 * （只增不改）⇒ 对外行为逐项不变；重接由后续卡承接。
 */
public class DamageComponent extends RoleComponent {

    public DamageComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 真实伤害（无视护甲；含既有 PDC 副作用）。 */
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount) {
        svc().damage().trueDamage(victim, source, amount);
    }

    /** 真实伤害 + 击退强度。 */
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        svc().damage().trueDamage(victim, source, amount, knockbackStrength);
    }

    /** 物理伤害（走护甲/减伤）。 */
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount) {
        svc().damage().physicalDamage(victim, source, amount);
    }

    /** 物理伤害 + 击退强度。 */
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        svc().damage().physicalDamage(victim, source, amount, knockbackStrength);
    }

    /** 装配描述符：**不占栏位**；提供类型 = {@code DamageComponent.class}。 */
    public static final class Specification extends RoleComponent.Specification<DamageComponent> {

        public Specification() {
            super("DamageComponent");
        }

        @Override
        public DamageComponent create(String id, ComponentServices services) {
            return new DamageComponent(id, services);
        }
    }
}
