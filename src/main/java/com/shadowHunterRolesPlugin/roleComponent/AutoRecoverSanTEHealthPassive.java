package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.PassiveSkill;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.UpdateAware;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class AutoRecoverSanTEHealthPassive extends PassiveSkill implements UpdateAware {

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    public AutoRecoverSanTEHealthPassive() {
        super("autoRecoverSanTEPassive", Component.text("自动恢复SanTE"), Component.text("当周围10格没有敌人五秒后, 开始自动恢复SanTE, 每秒3"));
    }

    @Override
    public void update(Player player, RoleInstance instance) {
        if(SkillUtil.hasEnemyInRange(instance, player.getLocation(), 10)){
            if(noEnemySurroundTime != 0) noEnemySurroundTime = 0;
        }
        else{
            if(noEnemySurroundTime < 200){
                noEnemySurroundTime++;
            }
        }

        if(instance.getCurrentSanTE() <= 0) return;

        if(noEnemySurroundTime >= 200){
            tickSecondRecord++;
            if(tickSecondRecord >= 20){
                tickSecondRecord = 0;
                instance.increaseSanTE(3);
                instance.heal(1);
            }
        }
    }
}
