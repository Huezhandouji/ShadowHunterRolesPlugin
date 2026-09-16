package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.RoleComponentAware.SanTEChangeAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.UpdateAware;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class RedDeeplySorrowSkill extends Skill implements UpdateAware, SanTEChangeAware {

    //该技能是否在执行中
    private boolean running = false;

    private int secondTickCount = 0;

    public RedDeeplySorrowSkill() {
        super("red_deeplySorrow_skill", Component.text("黯然销魂"),
                Component.text("持续扣减[红]的TE值，每秒10点，在TE值归零前获得持续的生命恢复5与力量2，在TE值归零后结束这个技能"),
                600,0, Material.REDSTONE_BLOCK);
    }

    @Override
    public void onRightClick(Player caster, RoleInstance instance) {
        if(!instance.getBuffManager().canCastSkill()) return;
        running = true;
        instance.startSkillCooldown(getId(), getCooldownTicks());
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_DEATH, 2, 1);

    }

    @Override
    public void update(Player caster, RoleInstance instance) {
        if(!running) return;

        secondTickCount++;
        if(secondTickCount < 20) return;
        secondTickCount = 0;

        instance.decreaseSanTE(10);
        //药水记账（O-7）：经 RoleInstance 施加，clear() 时只回收本系统施加的效果
        instance.applyPotionEffect(PotionEffectType.REGENERATION.createEffect(45, 5));
        instance.applyPotionEffect(PotionEffectType.STRENGTH.createEffect(45, 2));
        instance.startSkillCooldown(getId(), getCooldownTicks());

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);
    }

    @Override
    public void onSanTEChange(Player player, RoleInstance instance, int preSanTE, int newSanTE) {
        if(newSanTE <= 0){
            running = false;
            instance.startSkillCooldown(getId(), getCooldownTicks());
        }
    }
}
