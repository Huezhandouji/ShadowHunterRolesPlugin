package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * SanTE 组件（阶段 10 · t55 · A1）：系统级能力「SanTE」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>薄封装</b>：内部**转调既有端口** {@code SanTEPort} —— 读写语义（含"归零惩罚"的钉 0 行为）
 * 仍只有容器一处实现，本组件只是它的**可被依赖的组件面**。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * private SanTEComponent sante;
 * @Override public void awake() { sante = getComponent(SanTEComponent.class); }
 * @Override public void update() { if (sante.current() <= 0) return; sante.decrease(1); }
 * }</pre>
 * <p><b>装配示例</b>：{@code builder.addComponent("sante", new SanTEComponent.Specification());}（不占栏位）。
 * <p><b>本卡不改任何调用点</b>：既有组件仍走 {@code svc().sante()}。
 */
public class SanTEComponent extends RoleComponent {

    public SanTEComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 增加 SanTE（内部按上限 clamp）。 */
    public void gain(int amount) {
        svc().sante().gain(amount);
    }

    /** 减少 SanTE（内部按 0 下限 clamp；归零惩罚由既有组件监听真变化后触发）。 */
    public void decrease(int amount) {
        svc().sante().decrease(amount);
    }

    /** 直接写入 SanTE（内部 clamp）。 */
    public void set(int value) {
        svc().sante().set(value);
    }

    /** 当前 SanTE（读口）。 */
    public int current() {
        return svc().sante().current();
    }

    /** SanTE 上限（读口）。 */
    public int max() {
        return svc().sante().max();
    }

    /** 装配描述符：**不占栏位**；提供类型 = {@code SanTEComponent.class}。 */
    public static final class Specification extends RoleComponent.Specification<SanTEComponent> {

        public Specification() {
            super("SanTEComponent");
        }

        @Override
        public SanTEComponent create(String id, ComponentServices services) {
            return new SanTEComponent(id, services);
        }
    }
}
