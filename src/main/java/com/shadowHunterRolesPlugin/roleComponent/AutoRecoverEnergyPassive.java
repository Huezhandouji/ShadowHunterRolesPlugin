package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.EnergyComponent;

public class AutoRecoverEnergyPassive extends PassiveSkill {

    /**
     * **EnergyComponent 取用入口**（阶段 13 · t102）：向**组件本身**取用（R-6），不再经服务集的白名单端口成员。
     * <p>按需解析（**不缓存**）：R-4 只禁 `awake()`；不缓存引用 ⇒ 不引入生命周期耦合
     * （基类/子类各自覆写 `start()` 时，缓存的引用可能静默为空 ✗）。
     */
    private final EnergyComponent energyComponent(){
        return svc().components().get(EnergyComponent.class);
    }

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    public AutoRecoverEnergyPassive(String id, ComponentServices services) {
        super(id, services, Component.text("自动恢复能量"), Component.text("周围10格没有敌人时，每秒恢复3点能量"));
    }

    /**
     * 批次⑤（B⑤）迁移：旧 `update(Player, RoleInstance)` 的**逐条等价**新写法。
     * 数值/间隔**逐字不变**：半径 `10`、无敌人累计上限 `200` tick、每秒判定 `20` tick、`+3` 能量。
     * 阵营判定改走 `svc().roleInfo().hasEnemyInRange(10)`（其语义 = 原 `SkillUtil.hasEnemyInRange`，
     * 含"未选角色的玩家也算敌人"）；能量改走 `energyComponent().gain(3)` ⇒ **同一条记账/真值路径**。
     * 容器在 tick 里对该组件广播 `update()`（B⑤ 第 1 步，`:802`）。
     */
    @Override
    public void update() {
        if(svc().roleInfo().hasEnemyInRange(10)){
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
                energyComponent().gain(3);
            }
        }
    }
}
