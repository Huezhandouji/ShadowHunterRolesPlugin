package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.PassiveSkill;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.UpdateAware;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RedBleedPassive extends PassiveSkill implements LifecycleAware, UpdateAware {

    //流血记录保存在 RoleInstance 上下文里的键，红的主武器等其它组件通过它读写同一份数据
    public static final String BLEED_RECORD_CONTEXT_KEY = "player_bleed_record";
    //其他技能请求结算流血的记录
    public static final String BLEED_RESOLVE_REQUESTS_KEY = "player_bleed_resolve_requests";

    //最大流血层数
    public static final int MAX_BLEED_STACK = 15;

    //每层流血每秒造成的真伤
    private static final double BLEED_DAMAGE_PER_SECOND = 2d;

    //每次结算流血给红回复的SanTE
    private static final int BLEED_SANTE_RECOVER = 4;

    //结算流血时赋予红的抗性持续时间
    private static final int BLEED_RESISTANCE_DURATION_TICKS = 100;

    //流血结算间隔(游戏刻)，20刻 = 1秒
    private static final int BLEED_SETTLE_INTERVAL_TICKS = 20;

    private final Map<UUID, Integer> playerBleedRecord = new HashMap<>();
    private final Map<UUID, Integer> playerBleedResolveRequests = new HashMap<>();

    private int secondCountdown = 0;

    public RedBleedPassive() {
        super("red_bleed_passive", Component.text("流血"), Component.text("红的流血被动"));
    }

    /**技能初始化时，在角色实例上下文中初始化流血记录**/
    @Override
    public void start(Player player, RoleInstance instance) {
        instance.setContext(BLEED_RECORD_CONTEXT_KEY, playerBleedRecord);
        instance.setContext(BLEED_RESOLVE_REQUESTS_KEY, playerBleedResolveRequests);
    }

    @Override
    public void stop(Player player, RoleInstance instance) {
        playerBleedRecord.clear();
        playerBleedResolveRequests.clear();
    }

    /**供其它组件(技能)登记"结算流血"请求：受害者的UUID -> 请求结算的层数**/
    @SuppressWarnings("unchecked")
    public static void requestBleedResolve(RoleInstance instance, Player victim, int stacks){
        if(instance == null || victim == null || stacks <= 0) return;

        Map<UUID, Integer> requests = instance.getContext(BLEED_RESOLVE_REQUESTS_KEY, Map.class);
        //流血被动没注册或者上下文被清空时直接忽略，不能把调用方的技能搞崩
        if(requests == null) return;

        //同一个目标被多个技能同时请求时把层数叠加起来
        requests.merge(victim.getUniqueId(), stacks, Integer::sum);
    }

    /**
     * 这个受害者现在还能不能吃到流血。
     * 注意不能用 Player#isDead() 判断死亡：它是 CraftEntity 的 !entity.isAlive()，
     * 也就是"实体是否已经被移除"，躺在死亡界面上的玩家它依然返回 false，血量才是 0。
     **/
    public static boolean canReceiveBleed(Player victim){
        return victim != null
                && victim.isOnline()
                && !victim.isDead()
                && victim.getHealth() > 0d;
    }

    @Override
    public void update(Player player, RoleInstance instance) {
        //每tick先清掉失效记录：受害者退出游戏、死亡(含死亡界面)或者层数已经结算完
        //死亡必须每tick检查，否则尸体还会继续吃流血，红还能一直从尸体身上拿SanTE
        purgeInvalidBleedRecords();

        //再处理其它技能登记的结算请求(一次性，处理完就移除)，不受每秒结算节奏限制
        resolveRequestedBleed(player, instance);

        //每秒结算一次流血
        secondCountdown++;
        if(secondCountdown < BLEED_SETTLE_INTERVAL_TICKS) return;
        secondCountdown = 0;

        //遍历过程中不能直接修改map(会抛ConcurrentModificationException)，先收集需要移除的条目
        List<UUID> toRemove = new ArrayList<>();

        for(Map.Entry<UUID, Integer> entry : playerBleedRecord.entrySet()) {
            UUID pid = entry.getKey();
            Integer recorded = entry.getValue();
            int curBleed = recorded != null ? recorded : 0;

            Player victim = Bukkit.getPlayer(pid);
            //双保险：上面的清理已经跑过，这里再挡一次
            if(!canReceiveBleed(victim) || curBleed <= 0) {
                toRemove.add(pid);
                continue;
            }

            //结算这名受害者的一层流血
            int newBleed = Math.clamp(curBleed - 1, 0, MAX_BLEED_STACK);
            playerBleedRecord.put(pid, newBleed);

            //粒子
            player.spawnParticle(Particle.DUST, victim.getLocation().clone().add(0, 0.5, 0), 1, 1, 1, 1, new Particle.DustOptions(Color.RED, 1f));
            DamageUtil.dealtTrueDamage(victim, player, BLEED_DAMAGE_PER_SECOND);
            //赋予 红 5秒抗性1, 恢复4点SanTE
            player.addPotionEffect(PotionEffectType.RESISTANCE.createEffect(BLEED_RESISTANCE_DURATION_TICKS, 1));
            instance.increaseSanTE(BLEED_SANTE_RECOVER);

            if(newBleed <= 0) {
                toRemove.add(pid);
            }
        }

        for(UUID pid : toRemove) {
            playerBleedRecord.remove(pid);
        }
    }

    //清理已经不能继续流血的记录：受害者退出游戏、死亡(死亡界面里 isDead() 仍为false，要用血量判断)或者层数已归零
    private void purgeInvalidBleedRecords(){
        if(playerBleedRecord.isEmpty()) return;

        //遍历过程中不能直接修改map，先收集需要移除的条目
        List<UUID> toRemove = new ArrayList<>();

        for(Map.Entry<UUID, Integer> entry : playerBleedRecord.entrySet()){
            Integer recorded = entry.getValue();
            int curBleed = recorded != null ? recorded : 0;

            if(curBleed <= 0 || !canReceiveBleed(Bukkit.getPlayer(entry.getKey()))){
                toRemove.add(entry.getKey());
            }
        }

        for(UUID pid : toRemove){
            playerBleedRecord.remove(pid);
        }
    }

    //处理其它技能登记的流血结算请求，请求是一次性的：无论有没有真的结算成功，处理完就从请求表里移除
    private void resolveRequestedBleed(Player caster, RoleInstance casterInstance){
        if(playerBleedResolveRequests.isEmpty()) return;

        //遍历过程中不能直接修改map，先收集已经处理过的键
        List<UUID> handled = new ArrayList<>();

        for(Map.Entry<UUID, Integer> entry : playerBleedResolveRequests.entrySet()){
            handled.add(entry.getKey());

            Player victim = Bukkit.getPlayer(entry.getKey());
            if(victim == null) continue;

            Integer amount = entry.getValue();
            resolveBleed(victim, caster, casterInstance, amount != null ? amount : 0);
        }

        for(UUID pid : handled){
            playerBleedResolveRequests.remove(pid);
        }
    }

    private void resolveBleed(Player victim, Player caster, RoleInstance casterInstance, int amount){
        if(victim == null || caster == null || casterInstance == null || amount <= 0) return;

        UUID pid = victim.getUniqueId();

        //已经死亡的受害者：请求作废，记录一并清掉，不能从尸体上结算出伤害和SanTE
        if(!canReceiveBleed(victim)){
            playerBleedRecord.remove(pid);
            return;
        }

        Integer recorded = playerBleedRecord.get(pid);
        int curBleed = recorded != null ? recorded : 0;
        if(curBleed <= 0) return;

        int finalResolveBleedAmount = Math.min(amount, curBleed);
        int newBleed = curBleed - finalResolveBleedAmount;

        //结算完就把记录删掉，避免留下0层的空记录
        if(newBleed <= 0){
            playerBleedRecord.remove(pid);
        }
        else{
            playerBleedRecord.put(pid, newBleed);
        }

        DamageUtil.dealtTrueDamage(victim, caster, finalResolveBleedAmount * BLEED_DAMAGE_PER_SECOND);

        //赋予 红 5秒抗性1, 恢复4点SanTE
        caster.addPotionEffect(PotionEffectType.RESISTANCE.createEffect(BLEED_RESISTANCE_DURATION_TICKS, 1));
        casterInstance.increaseSanTE(BLEED_SANTE_RECOVER);

        victim.spawnParticle(Particle.DUST, victim.getLocation().clone().add(0, 0.5, 0), 1, 1, 1, 1, new Particle.DustOptions(Color.RED, 1f));
    }
}
