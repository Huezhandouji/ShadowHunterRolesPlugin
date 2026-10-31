package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;

/**
 * 「须狂命」的 **SanTE 消耗账本**：鹤子每次**对敌人造成伤害**（普攻 / 技能 / 投掷），自己掉 3 点 SanTE。
 *
 * <h2>为什么单独一个组件</h2>
 * 消耗方有四个（主武器、三技能、投掷物），而被动的十档判定又在另一个组件里。
 * 若让"技能 requires 被动"，就会与"被动 requires 三个技能（为了 100 档减 CD）"形成**依赖环**
 * ⇒ 整个角色静默消失。因此把"掉 SanTE"这件事拆成只依赖 {@link SanTEComponent} 的小账本，
 * 四个消耗方都只依赖它，环自然不存在。
 *
 * <p><b>口径</b>：一次"攻击命中" = 一次法球/刀/投掷造成的伤害事件；**多目标只记一次**
 * （避免一发 AOE 把 100 点 SanTE 一次清空）。被动自己的爆发伤害不计入（需求明确排除）。
 */
public class MikomakoSanDrainComponent extends RoleComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_san_drain";

    /** 每次命中扣多少 SanTE（需求：3）。 */
    public static final int SANTE_PER_HIT = 3;

    private SanTEComponent sante;

    public MikomakoSanDrainComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services);
    }

    /** 描述符：只依赖 SanTE。 */
    public static final class Specification extends RoleComponent.Specification<MikomakoSanDrainComponent> {

        public Specification() {
            super("MikomakoSanDrain");
            requires(SanTEComponent.class);
        }

        @Override
        public MikomakoSanDrainComponent create(String id, ComponentServicesPort services) {
            return new MikomakoSanDrainComponent(id, services, this);
        }
    }

    @Override
    public void start() {
        sante = getComponent(SanTEComponent.class);
    }

    /** 一次命中：扣 {@value #SANTE_PER_HIT} 点 SanTE。 */
    public void onHit() {
        if (sante != null) {
            sante.decrease(SANTE_PER_HIT);
        }
    }

    /**
     * 多次命中（保留口子：目前所有调用点都按"一次攻击记一次"处理）。
     *
     * @param times 次数（{@code <= 0} 无操作）
     */
    public void onHit(int times) {
        if (sante != null && times > 0) {
            sante.decrease(SANTE_PER_HIT * times);
        }
    }
}
