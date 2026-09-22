package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * 能量组件（阶段 10 · t55 · A1）：系统级能力「能量」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>薄封装</b>（用户澄清「组件的实现**不必**不依赖任何外部的东西」）：内部**转调既有端口**
 * {@code EnergyPort} —— 本组件不重新实现能量语义，只把"谁提供这项能力"从
 * {@code ComponentServices.energy} 搬到**一个可以被依赖的组件类型**上：其他组件用
 * {@code requires(EnergyComponent.class)} 声明依赖，装配期检查（{@code Role#verifyDependencies()}）
 * 就会拦住缺它的角色。
 * <p><b>每实例一个</b>：实例由容器在构造期创建（服务集由 {@code ComponentFactory} 注入），
 * 一个角色实例一份 ⇒ 状态天然隔离（能量真值仍在容器实例字段里，见已知限制申报）。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * public class MySkill extends Skill {
 *     private EnergyComponent energy;
 *     @Override public void awake() { energy = getComponent(EnergyComponent.class); } // 装配期解析
 *     @Override public void start() { energy.gain(10); }        // == svc().energy().gain(10)
 *     @Override public void update() { if (!energy.tryConsume(2)) return; ... }
 * }
 * }</pre>
 * <p><b>装配示例（角色模板内）</b>：{@code builder.addComponent("energy", new EnergyComponent.Specification());}
 * —— 本描述符**不占栏位**（没有 {@code setSlot}），因此不会被渲染进热键栏。
 * <p><b>本卡不改任何调用点</b>（只增不改）：既有 16 组件仍走 {@code svc().energy()}，
 * 重接由后续卡承接。
 */
public class EnergyComponent extends RoleComponent {

    public EnergyComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 当前能量（读口）。 */
    public int current() {
        return svc().energy().current();
    }

    /** 检查 + 扣减合一；能量不足 ⇒ {@code false} 且不扣。 */
    public boolean tryConsume(int amount) {
        return svc().energy().tryConsume(amount);
    }

    /** 增加能量（内部按上限 clamp）。 */
    public void gain(int amount) {
        svc().energy().gain(amount);
    }

    /**
     * 装配描述符：**不占栏位**；提供类型由泛型实参推导 = {@code EnergyComponent.class}
     * （⇒ {@code requires(EnergyComponent.class)} 能命中本组件）。
     */
    public static final class Specification extends RoleComponent.Specification<EnergyComponent> {

        public Specification() {
            super("EnergyComponent");
        }

        @Override
        public EnergyComponent create(String id, ComponentServices services) {
            return new EnergyComponent(id, services);
        }
    }
}
