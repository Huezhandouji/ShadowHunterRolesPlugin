package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RedEvilShockSkill extends Skill{

    public RedEvilShockSkill() {
        super("red_evilShock_skill", Component.text("煞气震赫"),
                Component.text("对周围5格范围内的敌人造成3秒致盲和缓慢III，结算他们5层流血。恢复[红]的10点TE值"),
                120,0, Material.REDSTONE);
    }

    @Override @SuppressWarnings("unchecked")
    public void onRightClick(Player caster, RoleInstance instance){
        if(!instance.getBuffManager().canCastSkill()) return;
        Collection<? extends Player> victims = caster.getLocation().getNearbyPlayers(5);
        for(Player p : victims){
            if(instance.isHostileTo(p)){
                p.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(61, 1));
                p.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(61, 3));
                //结算5层流血
                Map<UUID, Integer> resolveRequests = instance.getContext(RedBleedPassive.BLEED_RESOLVE_REQUESTS_KEY, Map.class);
                //O-9：流血被动未注册或上下文被清空时直接跳过，不能让本技能抛 NPE
                if(resolveRequests == null) continue;
                resolveRequests.put(p.getUniqueId(), 5);
            }
        }
        instance.increaseSanTE(10);

        instance.startSkillCooldown(getId(), getCooldownTicks());

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);
    }
}
