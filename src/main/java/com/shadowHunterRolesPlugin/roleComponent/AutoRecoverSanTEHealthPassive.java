package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;

public class AutoRecoverSanTEHealthPassive extends PassiveSkill {

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    public AutoRecoverSanTEHealthPassive(String id, ComponentServices services) {
        super(id, services, Component.text("自动恢复SanTE"), Component.text("当周围10格没有敌人五秒后, 开始自动恢复SanTE, 每秒3"));
    }

    /**
     * 批次⑤（B⑤）迁移：旧 `update(Player, RoleInstance)` 的**逐条等价**新写法。
     * 数值/间隔**逐字不变**：半径 `10`、累计上限 `200` tick、每秒判定 `20` tick、`+3` SanTE、`+1` 生命；
     * SanTE 为 0 时提前 return 的短路**保持**。阵营判定走 `svc().roleInfo().hasEnemyInRange(10)`
     * （语义 = 原 `SkillUtil.hasEnemyInRange`）；SanTE/生命改走 `svc().sante().gain(3)` / `svc().vitals().heal(1)`。
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

        if(svc().sante().current() <= 0) return;

        if(noEnemySurroundTime >= 200){
            tickSecondRecord++;
            if(tickSecondRecord >= 20){
                tickSecondRecord = 0;
                svc().sante().gain(3);
                svc().vitals().heal(1);
            }
        }
    }
}
