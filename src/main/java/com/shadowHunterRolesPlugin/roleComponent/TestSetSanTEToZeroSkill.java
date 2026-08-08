package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class TestSetSanTEToZeroSkill extends Skill {

    public TestSetSanTEToZeroSkill(){
        super(
                "test_skill_setSanTEToZero",
                Component.text("测试技能, 将你的SanTE设为0"),
                Component.text("这是一个测试技能"),
                100,
                0,
                Material.COMMAND_BLOCK
        );
    }

    @Override
    public void onRightClick(Player caster, RoleInstance instance){
        if(!instance.getBuffManager().canCastSkill()) return;

        instance.startSkillCooldown(getId(), getCooldown());

        instance.setCurrentSanTE(0);
    }

}
