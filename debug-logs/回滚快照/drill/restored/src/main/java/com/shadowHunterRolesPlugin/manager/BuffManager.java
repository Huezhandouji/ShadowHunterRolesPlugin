package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.Buff;
import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.checkerframework.checker.units.qual.N;

import java.util.*;

public class BuffManager {

    public static final NamespacedKey BUFF_MOVEMENT_SPEED_MODIFIER_KEY = new NamespacedKey(
            ShadowHunterRolesPlugin.getInstance(),
            "buff_movement_speed_modifier"
    );

    private final Player player;
    private final Map<BuffType, Buff> activeBuffs = new HashMap<>();
    private int updaterTaskId = -1;

    private final RoleInstance instance;

    public BuffManager(Player player, RoleInstance instance){
        this.player = player;
        this.instance = instance;
        startUpdater();
    }

    public void addBuff(BuffType type, int durationTicks){
        //处理免疫效果
        if(type == BuffType.IMMUNE){
            addImmune(durationTicks);
            return;
        }

        if(hasBuff(BuffType.IMMUNE)) return;

        if(activeBuffs.containsKey(type)){
             Buff existing = activeBuffs.get(type);
             if(durationTicks > existing.getRemainingTicks()){
                 activeBuffs.put(type, new Buff(type, durationTicks));
             }
        }
        else{
            activeBuffs.put(type, new Buff(type, durationTicks));
            if(type == BuffType.STUN){
                    player.getAttribute(Attribute.MOVEMENT_SPEED).addModifier(
                            new AttributeModifier(BUFF_MOVEMENT_SPEED_MODIFIER_KEY, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                    );
            }
            applyPotionEffect(type, durationTicks);
        }




    }

    private void addImmune(int durationTicks){
        //取最大时间
        if(activeBuffs.containsKey(BuffType.IMMUNE)){
            Buff existing = activeBuffs.get(BuffType.IMMUNE);
            if(durationTicks > existing.getRemainingTicks()){
                activeBuffs.put(BuffType.IMMUNE, new Buff(BuffType.IMMUNE, durationTicks));
            }
            return;
        }

        removeBuff(BuffType.SILENCE);
        removeBuff(BuffType.STUN);

        activeBuffs.put(BuffType.IMMUNE, new Buff(BuffType.IMMUNE, durationTicks));
    }

    public boolean hasBuff(BuffType type){
        Buff buff = activeBuffs.get(type);
        return buff != null && !buff.isExpired();
    }

    public boolean canCastSkill(){
        return !hasBuff(BuffType.STUN) && !hasBuff(BuffType.SILENCE);
    }

    public boolean canUseMainWeapon(){
        return !hasBuff(BuffType.STUN);
    }

    public long getRemainingTicks(BuffType type){
        Buff buff = activeBuffs.get(type);
        return buff != null ? buff.getRemainingTicks() : 0;
    }

    public void removeBuff(BuffType type){
        activeBuffs.remove(type);
        removePotionEffect(type);

        if(type == BuffType.STUN){
            player.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(BUFF_MOVEMENT_SPEED_MODIFIER_KEY);
        }

        if(instance != null){
            instance.updateHotbar();
        }
    }

    //加原版药水效果
    private void applyPotionEffect(BuffType type, int durationTicks){
        switch (type){
            case STUN:
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, durationTicks, 1, false, true
                ));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.DARKNESS, durationTicks, 1, false, true
                ));
                break;
        }
    }

    private void removePotionEffect(BuffType type){
        switch (type){
            case STUN:
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.DARKNESS);
                break;
        }
    }

    private void startUpdater(){
        updaterTaskId = Bukkit.getScheduler().runTaskTimer(
                ShadowHunterRolesPlugin.getInstance(),
                this::tickAllBuffs,
                0L,
                1L
        ).getTaskId();
    }

    private void tickAllBuffs(){
        List<BuffType> toRemove = new ArrayList<>();

        for(Map.Entry<BuffType, Buff> entry : activeBuffs.entrySet()){
            Buff buff = entry.getValue();
            buff.tick();

            if(buff.isExpired()){
                toRemove.add(entry.getKey());
            }
        }

        for(BuffType type : toRemove){
            removeBuff(type);
        }
    }

    public void clearAll(){
        for(BuffType type : new ArrayList<>(activeBuffs.keySet())){
            removeBuff(type);
        }
        activeBuffs.clear();

        if(updaterTaskId != -1){
            Bukkit.getScheduler().cancelTask(updaterTaskId);
            updaterTaskId = -1;
        }
    }

    public Set<BuffType> getActiveBuffTypes(){
        Set<BuffType> result = new HashSet<>();
        for(Map.Entry<BuffType, Buff> entry : activeBuffs.entrySet()){
            if(!entry.getValue().isExpired()){
                result.add(entry.getKey());
            }
        }

        return result;
    }
}
