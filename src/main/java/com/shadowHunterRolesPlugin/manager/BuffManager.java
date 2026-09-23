package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.core.Buff;
import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.platform.Task;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class BuffManager {

    public static final NamespacedKey BUFF_MOVEMENT_SPEED_MODIFIER_KEY = KeyFactory.Registry.of(
            "buff_movement_speed_modifier"
    );

    private final Player player;
    private final Map<BuffType, Buff> activeBuffs = new HashMap<>();
    private Task updaterTask;

    private final RoleInstance instance;

    public BuffManager(Player player, RoleInstance instance){
        this.player = player;
        this.instance = instance;
        //阶段 10 · t64（P6 两阶段构造）：**构造期不再启动每 tick 更新** —— 第一相（构造）不得创建任何任务，
        //否则构造中途抛错（例如某个组件构造器抛）会泄漏一个永久运行的 ticker。
        //启动点改为 `RoleInstance#activate()`（第二相），由它调用 {@link #startUpdater()}。
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
            //触点⑤（buff 移除）：置脏 + 帧末 flush（`addBuff` 保持不置脏 —— 与迁移前"添加后无刷新"逐字一致）
            instance.hotbarRenderer().markDirty();
        }
    }

    //加原版药水效果（经 RoleInstance 记账，clear() 时只回收本系统施加的效果）
    private void applyPotionEffect(BuffType type, int durationTicks){
        switch (type){
            case STUN:
                instance.applyPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, durationTicks, 1, false, true
                ));
                instance.applyPotionEffect(new PotionEffect(
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

    /**
     * 启动记账表的每 tick 更新（阶段 10 · t64 · P6 两阶段构造）。
     * <p>**调用方 = {@code core/RoleInstance#activate()}（第二相）**，不再是本类构造器 —— 见构造器处注释。
     * <p>**幂等**：重复调用只保留一个任务（`clearAll()` 会把句柄置回 `null`，此后可再次启动）。
     */
    public void startUpdater(){
        if(updaterTask != null) return;
        updaterTask = instance.rolesContext().scheduler().runRepeating(
                this::tickAllBuffs,
                0L,
                1L
        );
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

        if(updaterTask != null){
            updaterTask.cancel();
            updaterTask = null;
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
