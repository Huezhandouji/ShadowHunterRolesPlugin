package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;

/**
 * 红的流血被动。
 * <p>流血账本（`playerBleedRecord` / `playerBleedResolveRequests`）为私有，外部经
 * `getComponent(...)` 取到本组件后，再走下面三个公开入口读写同一份账本。
 * <p>`update()` 保持无参形态与全部端口化，数值 / 结算节奏 / 记账路径逐字不变。
 *
 * <h2>进化联动（红的两档都落在这里，因为账本在这里）</h2>
 * <ul>
 *   <li><b>1 级 · 红月落下</b>：每秒结算时，对每个**负有流血**的受害者额外扣
 *       {@code RedEvolutionPassive#bleedSanTEDrainPerSecond()} 点特殊值（基线 0 ⇒ 未进化时这一行是 no-op）；</li>
 *   <li><b>5 级 · 猩红已至</b>：每秒**结算的层数**由 1 变 3
 *       （{@code RedEvolutionPassive#bleedSettleStacksPerSecond()}）。伤害仍按"每层
 *       {@link #BLEED_DAMAGE_PER_SECOND} 点真伤"缩放（3 层 = 6 点），而抗性与
 *       {@link #BLEED_SANTE_RECOVER} 仍是**每次结算各一次**（与既有的 {@code resolveBleed}
 *       口径一致：它们按结算次数给，不按层数给 —— 这是照抄既有口径，不是新口径）。</li>
 * </ul>
 * 档位经 `getComponent(RedEvolutionPassive.class)` 在 `start()` 里取好缓存；
 * 取不到时退化为基线值（装配期已声明 `requires(RedEvolutionPassive.class)` ⇒ 生产上取不到不会发生），
 * 与既有"流血被动未注册时只跳过流血、不抛"的容错口径同源。
 */
public class RedBleedPassive extends PassiveSkill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "red_bleed_passive";

    private VitalsComponent vitals;
    private BuffComponent buff;
    private SanTEComponent sante;

    /** 红的进化被动（档位读口的来源）；{@code start()} 里一次取好。 */
    private RedEvolutionPassive evolution;

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

    public RedBleedPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符：被动经统一 {@code addComponent} 入口装配且无栏位，因此天然不占热键栏。
     * 显示名与描述只在本描述符里声明一处；依赖 = {@code start()} 实取的那四个组件
     * （进化被动只为读两档生效值，不持有任何账本）。
     */
    public static final class Specification extends PassiveSkill.Specification<RedBleedPassive> {

        public Specification(){
            super(Component.text("流血"), List.of(Component.text("红的流血被动")));
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(SanTEComponent.class)
                    .requires(RedEvolutionPassive.class);
        }

        @Override
        public RedBleedPassive create(String id, ComponentServicesPort services){
            return new RedBleedPassive(id, services, this);
        }
    }

    /**
     * **写流血结算请求的唯一公开入口**。
     * <p>写入本组件私有的 {@code playerBleedResolveRequests}（不另建并行存储）：
     * 键 = 受害者 UUID、值 = 待结算层数；结算时点由 {@code update()} 的
     * {@code resolveRequestedBleed(...)} 决定。
     */
    public void requestResolve(UUID victimId, int stacks) {
        if (victimId == null) {
            return;
        }
        playerBleedResolveRequests.put(victimId, stacks);
    }

    /**
     * **累加流血层数**（唯一写入口之一；外部只允许调
     * `requestResolve` / `applyStacks` / `stacksOf` 这三个公开入口，不得触碰账本内部结构）。
     * <p>语义：键 = 受害者 `UUID`；累加后 `Math.clamp(…, 0, MAX_BLEED_STACK = 15)`；
     * 结算时点仍由 {@code update()} 里 `secondCountdown >= BLEED_SETTLE_INTERVAL_TICKS = 20` 后触发。
     * <b>一经冻结不得再改。</b>
     */
    public void applyStacks(UUID victimId, int amount) {
        if (victimId == null) {
            return;
        }
        int current = playerBleedRecord.getOrDefault(victimId, 0);
        playerBleedRecord.put(victimId, Math.clamp(current + amount, 0, MAX_BLEED_STACK));
    }

    /**
     * **只读查询某受害者当前流血层数**（公开入口之一；无副作用）。一经冻结不得再改。
     */
    public int stacksOf(UUID victimId) {
        if (victimId == null) {
            return 0;
        }
        return playerBleedRecord.getOrDefault(victimId, 0);
    }

    /**
     * **停止生效**（无参钩子）：清空两个账本 Map。
     * <p>本类另覆写 `start()`（把协作组件缓存进字段），两者成对。
     */
    @Override
    public void stop() {
        playerBleedRecord.clear();
        playerBleedResolveRequests.clear();
    }

    /**
     * **开始生效**：把协作组件一次查好缓存进字段（与本族模型一致）。
     * <p>取组件只能在本钩子里做，不得放 `awake()`；
     * 注册表在装配期后冻结，因此缓存引用与按需解析恒等。
     */
    @Override
    public void start(){
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        evolution = getComponent(RedEvolutionPassive.class);
    }

    /**
     * 这个受害者现在还能不能吃到流血。
     * 不能用 Player#isDead() 判断死亡：它是 CraftEntity 的 !entity.isAlive()，
     * 也就是"实体是否已经被移除"，躺在死亡界面上的玩家它依然返回 false，血量才是 0。
     */
    public static boolean canReceiveBleed(Player victim){
        return victim != null
                && victim.isOnline()
                && !victim.isDead()
                && victim.getHealth() > 0d;
    }

    @Override
    public void update() {
        //本组件与角色实例一对一，因此 `svc()` 恒指向所属实例
        Player player = svc().self().player();

        //每tick先清掉失效记录：受害者退出游戏、死亡(含死亡界面)或者层数已经结算完
        //死亡必须每tick检查，否则尸体还会继续吃流血，红还能一直从尸体身上拿SanTE
        purgeInvalidBleedRecords();

        //再处理其它技能登记的结算请求(一次性，处理完就移除)，不受每秒结算节奏限制
        resolveRequestedBleed(player);

        //每秒结算一次流血
        secondCountdown++;
        if(secondCountdown < BLEED_SETTLE_INTERVAL_TICKS) return;
        secondCountdown = 0;

        //本秒的进化档位读数（每秒读一次即可：升级发生在下一秒之前都不会漏）
        //5 级 猩红已至：每秒结算 3 层（基线 1）；1 级 红月落下：每个流血受害者每秒再扣 1 点特殊值（基线 0）
        int settleStacks = evolution != null
                ? evolution.bleedSettleStacksPerSecond()
                : RedEvolutionPassive.BASE_BLEED_SETTLE_STACKS_PER_SECOND;
        int santeDrainPerSecond = evolution != null
                ? evolution.bleedSanTEDrainPerSecond()
                : RedEvolutionPassive.BASE_BLEED_SANTE_DRAIN_PER_SECOND;

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

            //结算这名受害者的 settleStacks 层流血（基线 1 层 = 既有行为；5 级起 3 层）
            //实际结算层数以手上还剩的层数为上限（剩 2 层时按 2 层算，不能凭空多结算）
            int settledStacks = Math.min(settleStacks, curBleed);
            int newBleed = Math.clamp(curBleed - settleStacks, 0, MAX_BLEED_STACK);
            playerBleedRecord.put(pid, newBleed);

            //粒子
            BlockData bd = Bukkit.createBlockData(Material.RED_CONCRETE);
            player.spawnParticle(Particle.BLOCK, victim.getLocation().clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3,0.1, bd);
            //伤害随"实际结算层数"走（每层 2 点真伤）：1 层 = 既有的 2 点，3 层 = 6 点
            vitals.trueDamage(victim, player, settledStacks * BLEED_DAMAGE_PER_SECOND);
            //赋予 红 5秒抗性1, 恢复4点SanTE（药水记账：经 Buff 组件的入口，与框架同一条已记账路径）
            //这两项按"每次结算"各一次给（与既有的 resolveBleed 口径一致：不随层数翻倍）
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, BLEED_RESISTANCE_DURATION_TICKS, 1);
            sante.increase(BLEED_SANTE_RECOVER);
            //1 级 红月落下：这个负有流血的敌人同时被扣特殊值（走 SanTE 组件的跨实例入口，与「律法之言」同一条路）
            if(santeDrainPerSecond > 0){
                sante.decreaseSanTE(pid, santeDrainPerSecond);
            }

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
    private void resolveRequestedBleed(Player caster){
        if(playerBleedResolveRequests.isEmpty()) return;

        //遍历过程中不能直接修改map，先收集已经处理过的键
        List<UUID> handled = new ArrayList<>();

        for(Map.Entry<UUID, Integer> entry : playerBleedResolveRequests.entrySet()){
            handled.add(entry.getKey());

            Player victim = Bukkit.getPlayer(entry.getKey());
            if(victim == null) continue;

            Integer amount = entry.getValue();
            resolveBleed(victim, caster, amount != null ? amount : 0);
        }

        for(UUID pid : handled){
            playerBleedResolveRequests.remove(pid);
        }
    }

    private void resolveBleed(Player victim, Player caster, int amount){
        if(victim == null || caster == null || amount <= 0) return;

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

        vitals.trueDamage(victim, caster, finalResolveBleedAmount * BLEED_DAMAGE_PER_SECOND);

        //赋予 红 5秒抗性1, 恢复4点SanTE（药水记账：经 Buff 组件的入口，与框架同一条已记账路径）
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, BLEED_RESISTANCE_DURATION_TICKS, 1);
        sante.increase(BLEED_SANTE_RECOVER);

        victim.spawnParticle(Particle.DUST, victim.getLocation().clone().add(0, 0.5, 0), 1, 1, 1, 1, new Particle.DustOptions(Color.RED, 1f));
    }
}
