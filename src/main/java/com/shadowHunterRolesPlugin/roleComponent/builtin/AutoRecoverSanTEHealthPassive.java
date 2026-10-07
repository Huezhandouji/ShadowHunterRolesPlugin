package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import java.util.List;

public class AutoRecoverSanTEHealthPassive extends PassiveSkill {

    /** 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。 */
    public static final String ID = "autoRecoverSanTEPassive";

    private SanTEComponent sante;

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    //生命能力改向组件本身取用，引用缓存在 start()。
    //服务集的白名单端口是纯转发（同一组件的同一方法），因此两种取用逐字等价。
    private VitalsComponent vitals;

    public AutoRecoverSanTEHealthPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符（被动走统一的 {@code addComponent} 入口，因此无栏位、天然不占热键栏）。
     * 表现数据（显示名 / 描述）逐字沿用原有文案（不新拟）；依赖 = 实取清单（{@code start()} 内的两个调用点）。
     */
    public static final class Specification extends PassiveSkill.Specification<AutoRecoverSanTEHealthPassive> {

        public Specification(){
            super(Component.text("自动恢复SanTE"), List.of(Component.text("当周围10格没有敌人五秒后, 开始自动恢复SanTE, 每秒3")));
            requires(SanTEComponent.class).requires(VitalsComponent.class);
            //"周围 10 格有没有敌人"读阵营组件 ⇒ 缺它则本被动不做判定，装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public AutoRecoverSanTEHealthPassive create(String id, ComponentServicesPort services){
            return new AutoRecoverSanTEHealthPassive(id, services, this);
        }
    }

    /**
     * 开始生效：把生命组件一次查好缓存进字段。
     * <p>必须在此取用、不得放 {@code awake()}：{@code awake()} 里禁止取用其他组件
     * （awake 只做构造期自检 / 只读自身）；{@code start()} 时容器已冻结，因此容器查找合法。
     * <p>缓存的理由：本被动每 tick 跑一次 {@code update()}，回血点在其内层判定里，重复查容器是纯浪费；
     * 端口引用本身也是构造期就持有的引用 ⇒ 缓存与经端口取用同族。
     * <p>等价性：该端口是纯转发（转发到本实例的同一个生命组件、同一个方法），因此逐字等价。
     */
    @Override
    public void start() {
        sante = svc().components().get(SanTEComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    /**
     * 容器在 tick 里对该组件广播 {@code update()} 钩子。
     * 数值/间隔逐字不变：半径 {@code 10}、累计上限 {@code 200} tick、每秒判定 {@code 20} tick、
     * {@code +3} SanTE、{@code +1} 生命；SanTE 为 0 时提前 return 的短路保持。
     * 阵营判定走 {@code svc().components().get(FactionComponent.class).hasEnemyInRange(10)}；SanTE 走 {@code sante.increase(3)}、
     * 生命走生命组件的回血入口（不再经服务集端口，直接用组件本身）。
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

        if(sante.current() <= 0) return;

        if(noEnemySurroundTime >= 200){
            tickSecondRecord++;
            if(tickSecondRecord >= 20){
                tickSecondRecord = 0;
                sante.increase(3);
                vitals.heal(1);
            }
        }
    }
}
