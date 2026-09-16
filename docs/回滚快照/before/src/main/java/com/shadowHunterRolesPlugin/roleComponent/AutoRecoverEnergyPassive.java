package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.PassiveSkill;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.UpdateAware;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class AutoRecoverEnergyPassive extends PassiveSkill implements UpdateAware {

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    public AutoRecoverEnergyPassive() {
        super("autoRecoverEnergy_passive", Component.text("自动恢复能量"), Component.text("周围10格没有敌人时，每秒恢复3点能量"));
    }

    @Override
    public void update(Player player, RoleInstance instance) {
        if(SkillUtil.hasEnemyInRange(instance.getFaction(), player.getLocation(), 10)){
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
                instance.increaseEnergy(3);
            }
        }
    }
}
