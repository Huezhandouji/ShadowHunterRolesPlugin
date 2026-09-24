package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.EnergyComponent;

public class AutoRecoverEnergyPassive extends PassiveSkill {

    private EnergyComponent energy;

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    public AutoRecoverEnergyPassive(String id, ComponentServices services) {
        super(id, services, Component.text("自动恢复能量"), Component.text("周围10格没有敌人时，每秒恢复3点能量"));
    }

    /**
     * 批次⑤（B⑤）迁移：旧 `update(Player, RoleInstance)` 的**逐条等价**新写法。
     * 数值/间隔**逐字不变**：半径 `10`、无敌人累计上限 `200` tick、每秒判定 `20` tick、`+3` 能量。
     * 阵营判定改走 `svc().roleInfo().hasEnemyInRange(10)`（其语义 = 原 `SkillUtil.hasEnemyInRange`，
     * 含"未选角色的玩家也算敌人"）；能量改走 `energy.increase(3)` ⇒ **同一条记账/真值路径**。
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
                energy.increase(3);
            }
        }
    }
    /**
     * **开始生效**：把协作组件**一次查好**缓存进字段 ✓（与全仓统一形态一致）。
     * <p>R-4：取组件只能在本钩子里做 ✗ —— 不得放 `awake()`；注册表装配期后冻结 ⇒ 与按需解析**恒等** ✓。
     */
    @Override
    public void start(){
        energy = svc().components().get(EnergyComponent.class);
    }

}
