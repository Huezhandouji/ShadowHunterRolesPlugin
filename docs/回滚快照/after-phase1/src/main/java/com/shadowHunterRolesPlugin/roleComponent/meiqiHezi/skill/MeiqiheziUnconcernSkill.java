package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;


public class MeiqiheziUnconcernSkill extends Skill {

    public MeiqiheziUnconcernSkill(){
        super(
                "meiqihezi_skill_unconcern",
                Component.text("漫不经心"),
                Component.text("获得2秒速度5"),
                100,
                0,
                Material.BLAZE_POWDER
        );
    }

    @Override
    public void onRightClick(Player caster, RoleInstance instance) {
        if(!instance.getBuffManager().canCastSkill()) return;
        instance.startSkillCooldown(getId(), getCooldownTicks());

        PotionEffect speedEffect = new PotionEffect(
                PotionEffectType.SPEED,
                40,
                4,
                false,
                true
        );
        //药水记账（O-7）：经 RoleInstance 施加，clear() 时只回收本系统施加的效果
        instance.applyPotionEffect(speedEffect);

        caster.getWorld().playSound(
                caster.getLocation(),
                Sound.ITEM_TRIDENT_RIPTIDE_1,
                1f,
                1f
        );

        caster.getWorld().spawnParticle(Particle.EXPLOSION, caster.getLocation(), 1);
    }

}
