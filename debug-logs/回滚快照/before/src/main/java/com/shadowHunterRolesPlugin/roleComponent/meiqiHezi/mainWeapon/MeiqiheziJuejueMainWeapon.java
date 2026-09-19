package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.mainWeapon;

import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.command.RoleCommand;
import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.script.CompiledScript;
import java.util.Collection;

public class MeiqiheziJuejueMainWeapon extends MainWeapon {


    public MeiqiheziJuejueMainWeapon() {
        super(
                "meiqihezi_mainWeapon_juejue",
                Component.text("Jue Jue"),
                Component.text("A ShadowHunter mainWeapon"),
                Material.DIAMOND_HOE,
                20
        );
    }

    @Override
    public void onAttack(Player attacker, Player victim, RoleInstance instance){

        //如果能量大于20，则进行范围伤害，写在onLeftClick，不进入这个逻辑
        if(instance.getCurrentEnergy() >= 20) return;
        instance.startMainWeaponCooldown(getId(), getCooldown());
        DamageUtil.dealtPhysicalDamage(victim, attacker, 8, 0.5);
    }

    @Override
    public void onLeftClick(Player player, RoleInstance instance){
        player.sendMessage(Component.text("111"));
        player.sendMessage(Component.text("The current energy level is: " + instance.getCurrentEnergy()));
        if(instance.getCurrentEnergy() < 20) return;
        instance.startMainWeaponCooldown(getId(), getCooldown());
        instance.decreaseEnergy(5);

        Location loc = player.getLocation();

        ParticleBuilder pb = Particle.DUST.builder()
                .count(1)
                .color(Color.RED)
                .offset(0, 0, 0);

        ParticleUtil.drawCircle(loc.clone().add(0, 1, 0), 5, pb, 50);

        Collection<? extends Player> victims = loc.getNearbyPlayers(5);

        for(Player victim : victims){
            if (!instance.isHostileTo(victim)) continue;
            DamageUtil.dealtPhysicalDamage(victim, player, 14);
        }



        loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
    }



}
