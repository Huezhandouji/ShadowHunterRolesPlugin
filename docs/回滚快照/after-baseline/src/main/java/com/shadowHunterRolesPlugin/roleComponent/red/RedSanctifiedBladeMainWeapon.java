package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class RedSanctifiedBladeMainWeapon extends MainWeapon {

    //每次普攻施加的流血层数
    private static final int BLEED_STACKS_PER_HIT = 15;

    public RedSanctifiedBladeMainWeapon() {
        super(
                "red_mainWeapon_sanctifiedBlade",
                Component.text("至洁之刃"),
                Component.text("攻击施加流血效果"),
                Material.IRON_SWORD,
                100
        );
    }

    @Override @SuppressWarnings("unchecked")
    public void onAttack(Player attacker, Player victim, RoleInstance instance){
        //流血记录由 RedBleedPassive 放进上下文，拿不到时只跳过流血结算，普攻伤害依然生效(不能直接return)
        //已经死亡(含死亡界面)的玩家不再施加流血，避免尸体继续吃流血结算
        Map<UUID, Integer> playerBleedRecord = instance.getContext(RedBleedPassive.BLEED_RECORD_CONTEXT_KEY, Map.class);
        if(playerBleedRecord != null && RedBleedPassive.canReceiveBleed(victim)){
            UUID victimPid = victim.getUniqueId();
            int curVictimBleed = playerBleedRecord.getOrDefault(victimPid, 0);
            int newVictimBleed = Math.clamp(curVictimBleed + BLEED_STACKS_PER_HIT, 0, RedBleedPassive.MAX_BLEED_STACK);
            playerBleedRecord.put(victimPid, newVictimBleed);
        }

        DamageUtil.dealtPhysicalDamage(victim, attacker, 8, 1);

        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1, 1.8f);

        //INSTANT_EFFECT 的数据类型是 Particle.Spell，必须传数据，否则 Paper 会抛 "missing required data"
        victim.spawnParticle(Particle.INSTANT_EFFECT, victim.getLocation().clone().add(0, 1, 0), 20, 1, 1, 1, new Particle.Spell(Color.RED, 1f));

        instance.startMainWeaponCooldown(getId(), getCooldown());
    }

}
