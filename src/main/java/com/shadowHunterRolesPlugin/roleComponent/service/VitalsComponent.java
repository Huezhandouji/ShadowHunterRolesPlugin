package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * 生命组件（阶段 10 · t55 · A1）：系统级能力「生命 / 治疗」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>薄封装</b>：内部**转调既有端口** {@code VitalsPort} —— clamp 策略仍只有容器一处实现。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * private VitalsComponent vitals;
 * @Override public void awake() { vitals = getComponent(VitalsComponent.class); }
 * @Override public void onSanTEChange(int pre, int now) { vitals.heal(2.0d); }
 * }</pre>
 * <p><b>装配示例</b>：{@code builder.addComponent("vitals", new VitalsComponent.Specification());}（不占栏位）。
 * <p><b>本卡不改任何调用点</b>：既有组件仍走 {@code svc().vitals()}。
 */
public class VitalsComponent extends RoleComponent {

    public VitalsComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 治疗（内部按最大生命 clamp）。 */
    public void heal(double amount) {
        svc().vitals().heal(amount);
    }

    /** 装配描述符：**不占栏位**；提供类型 = {@code VitalsComponent.class}。 */
    public static final class Specification extends RoleComponent.Specification<VitalsComponent> {

        public Specification() {
            super("VitalsComponent");
        }

        @Override
        public VitalsComponent create(String id, ComponentServices services) {
            return new VitalsComponent(id, services);
        }
    }
}
