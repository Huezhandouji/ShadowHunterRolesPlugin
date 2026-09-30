package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.platform.Scheduler;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.platform.Task;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.*;

public class BuffManager {

    public static final NamespacedKey BUFF_MOVEMENT_SPEED_MODIFIER_KEY = KeyFactory.Registry.of(
            "buff_movement_speed_modifier"
    );

    private final Player player;
    private final Map<BuffType, Buff> activeBuffs = new HashMap<>();
    private Task updaterTask;

    private final RoleComponent owner;

    /**
     * buff 真被移除后的通知（函数式回调；默认什么都不做）。
     *
     * <p>用回调而不是持有渲染组件：本类是记账器，不该认识热键栏/渲染 —— "移除后要不要重绘、找谁重绘"
     * 是调用方（`BuffComponent`）的事。本类只在语义正确的时机（buff 真的从账本里删掉那一刻）喊一声。
     */
    private Runnable onBuffRemoved = () -> { };

    /**
     * 登记"buff 真被移除"的通知（由认识本类的组件调用 —— 例如渲染组件在它的 `start()` 里订阅）。
     * <p>传 {@code null} ⇒ 复位为空操作（幂等；重复登记 ⇒ 后手覆盖前手）。
     */
    public void onBuffRemoved(Runnable listener) {
        this.onBuffRemoved = listener != null ? listener : () -> { };
    }

    /** 调度端口（平台面，不是容器）—— 本类不再依赖 `RoleInstance`。 */
    private final Scheduler scheduler;

    public BuffManager(Player player, RoleComponent owner, Scheduler scheduler){
        this.player = player;
        this.owner = owner;
        this.scheduler = scheduler;
        //构造期不启动每 tick 更新 —— 第一相（构造）不得创建任何任务，
        //否则构造中途抛错（例如某个组件构造器抛）会泄漏一个永久运行的 ticker。
        //启动点改为 buff 组件的 `start()`（第二相）⇒ {@link #startUpdater()}。
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

        //IMMUNE = 净化 + 免疫：进场先清掉全部负面效果（插件侧 STUN / SILENCE + 原版 HARMFUL 药水），
        //随后新来的负面效果由两道闸门挡住：
        //  ① 插件侧：本账本 —— {@code addBuff} 见到 IMMUNE 直接早退（不施加 STUN / SILENCE）；
        //  ② 原版侧：{@code listener/hook/ImmunePotionListener} 取消 IMMUNE 期间施加的负面药水。
        //净化先于 put：清负面效果与"此刻是否 IMMUNE"无关，顺序只影响可读性。
        clearDebuffs();

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

        if(owner != null){
            //buff 移除 ⇒ 请求重绘（添加时不请求 —— "添加后无刷新"的既有语义不变）
            //走基类通用面 `RoleComponent#requestRepaint`（默认空实现、由渲染组件覆写）
            // ⇒ 本类只持有"账本持有者"这个通用引用，不认识渲染组件（也不再按 id 去取）
            //本类不认识渲染组件 ⇒ 只喊一声"buff 被移除了"，找谁重绘由调用方决定
            onBuffRemoved.run();
        }
    }

    //加原版药水效果（经账本持有者记账；clear() 时只回收本系统施加的效果）
    private void applyPotionEffect(BuffType type, int durationTicks){
        if(owner == null) return;
        switch (type){
            case STUN:
                applyToOwner(new PotionEffect(
                        PotionEffectType.BLINDNESS, durationTicks, 1, false, true
                ));
                applyToOwner(new PotionEffect(
                        PotionEffectType.DARKNESS, durationTicks, 1, false, true
                ));
                break;
        }
    }

    /** 把药水交给账本持有者记账（它在自己的 `stop()` 里只回收自己记过的类型）。 */
    private void applyToOwner(PotionEffect effect){
        if(owner instanceof BuffComponent buffs){
            buffs.applyPotionEffect(effect);
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
     * 清空负面效果：<b>插件侧</b>（本账本内的全部 {@link BuffType#isDebuff() 负面 buff} —— 各自带出的
     * 原版药水与属性修饰符由 {@link #removeBuff(BuffType)} 一并回收）+ <b>原版侧</b>（玩家身上全部
     * {@code HARMFUL} 分类的药水效果）。
     *
     * <p><b>与 {@link #removeBuff(BuffType)} 的关键差别</b>：本方法只在账本里<b>真有</b>该 buff 时才调
     * {@code removeBuff} —— 后者是无条件回收（{@code removeBuff(STUN)} 会连带移除玩家身上的失明 / 黑暗，
     * 并按共享 key 摘掉移速修饰符），对"本来没被眩晕"的玩家调它就会误伤原版效果。
     *
     * <p><b>免疫类不动</b>：{@code IMMUNE} 的 {@code isDebuff()} 为 {@code false} ⇒ 本方法不清它
     * （否则"给自己上免疫"后第一次净化就把免疫本身清掉了）。
     *
     * <p><b>账本口径不变</b>：本方法只回收"效果本身"，不重写任何记账 —— 药水账本仍是"本系统施加过的
     * 类型"集合，它本就与"此刻身上有什么"不同口径（两者不随本方法漂移）。
     *
     * @return 被清掉的条数 = 插件侧每个被移除的负面 buff 记 1（<b>该 buff 自己带出的原版药水随它计入，
     *         不重复计数</b>；例如 {@code STUN} 的失明 / 黑暗算在 {@code STUN} 那 1 条里）
     *         + 枚举时仍在身上、随后被移除的 {@code HARMFUL} 类型各记 1（即
     *         {@link #clearHarmfulPotionEffects()} 的读数）
     */
    public int clearDebuffs(){
        int cleared = 0;

        for(BuffType type : BuffType.values()){
            if(!type.isDebuff() || !activeBuffs.containsKey(type)){
                continue;
            }
            removeBuff(type);
            cleared++;
        }

        return cleared + clearHarmfulPotionEffects();
    }

    /**
     * 移除玩家身上全部 {@code HARMFUL} 分类的原版药水效果（= 原版"负面药水效果"）。
     *
     * <p><b>判据来自服务端注册表</b>：用 {@link PotionEffectType#getCategory()}
     * （返回 {@link PotionEffectTypeCategory}，非废弃口 —— 废弃的是 {@code getEffectCategory()}），
     * 因此本类不维护第二张"哪些算负面"的类型名单，新增原版效果也无需改代码。
     * 注意 {@code NEUTRAL} 不属于负面（例如 {@code BAD_OMEN} 是 NEUTRAL，本方法不动它）。
     *
     * <p><b>先收类型、再逐个移除</b>：{@code getActivePotionEffects()} 的返回集合不保证在移除过程中
     * 仍可安全遍历。
     *
     * @return 被移除的药水类型数
     */
    private int clearHarmfulPotionEffects(){
        List<PotionEffectType> harmfulTypes = new ArrayList<>();
        for(PotionEffect effect : player.getActivePotionEffects()){
            PotionEffectType type = effect.getType();
            if(type.getCategory() == PotionEffectTypeCategory.HARMFUL){
                harmfulTypes.add(type);
            }
        }

        for(PotionEffectType type : harmfulTypes){
            player.removePotionEffect(type);
        }

        return harmfulTypes.size();
    }

    /**
     * 启动记账表的每 tick 更新（两阶段构造的第二相）。
     * <p>调用方 = buff 组件的 `start()`（第二相），不再是本类构造器 —— 见构造器处注释。
     * <p>幂等：重复调用只保留一个任务（`clearAll()` 会把句柄置回 `null`，此后可再次启动）。
     */
    public void startUpdater(){
        if(updaterTask != null) return;
        updaterTask = scheduler.runRepeating(
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
