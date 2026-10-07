package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import java.util.List;

public class AutoRecoverEnergyPassive extends PassiveSkill {

    /** 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。 */
    public static final String ID = "autoRecoverEnergy_passive";

    private EnergyComponent energy;

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    public AutoRecoverEnergyPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符（被动走统一的 {@code addComponent} 入口，因此无栏位、天然不占热键栏）。
     * 表现数据（显示名 / 描述）逐字沿用原有文案（不新拟）；依赖 = 实取清单（{@code start()} 内的
     * {@code svc().components().get(...)} 调用点）。
     */
    public static final class Specification extends PassiveSkill.Specification<AutoRecoverEnergyPassive> {

        public Specification(){
            super(Component.text("自动恢复能量"), List.of(Component.text("周围10格没有敌人时，每秒恢复3点能量")));
            requires(EnergyComponent.class);
            //"周围 10 格有没有敌人"读阵营组件 ⇒ 缺它则本被动不做判定，装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public AutoRecoverEnergyPassive create(String id, ComponentServicesPort services){
            return new AutoRecoverEnergyPassive(id, services, this);
        }
    }

    /**
     * 容器在 tick 里对该组件广播 {@code update()} 钩子。
     * 数值/间隔逐字不变：半径 {@code 10}、无敌人累计上限 {@code 200} tick、每秒判定 {@code 20} tick、
     * {@code +3} 能量。阵营判定走 {@code svc().components().get(FactionComponent.class).hasEnemyInRange(10)}，含
     * 「未选角色的玩家也算敌人」这条口径；能量走 {@code energy.increase(3)} ⇒ 同一条记账 / 真值路径。
     */
    @Override
    public void update() {
        if(svc().components().get(FactionComponent.class).hasEnemyInRange(10)){
            if(noEnemySurroundTime != 0) noEnemySurroundTime = 0;
        }
        else{
            if(noEnemySurroundTime < 200){
                noEnemySurroundTime++;
            }
        }

        if(noEnemySurroundTime >= 200){
            tickSecondRecord++;
            if(tickSecondRecord >= 20){
                tickSecondRecord = 0;
                energy.increase(3);
            }
        }
    }
    /**
     * 开始生效：把协作组件一次查好缓存进字段（与全仓统一形态一致）。
     * <p>取组件只能在本钩子里做，不得放 {@code awake()}；注册表装配期后冻结，因此与按需解析恒等。
     */
    @Override
    public void start(){
        energy = svc().components().get(EnergyComponent.class);
    }

}
