package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
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
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;

/**
 * 红的流血被动。
 * <p><b>迁移口径</b>：
 * <ul>
 *   <li>**删除过渡桥**（两个上下文键常量 + 两处上下文写入）与 **legacy 生命周期接口声明** ——
 *       账本与其唯一外部使用者（`RedSanctifiedBladeMainWeapon`）都已改为经 `getComponent(...)` 读写
 *       **同一份私有账本** ⇒ 桥的最后使用者已消失（§2.0 第 ⑦ 条）。</li>
 *   <li>legacy 的带参 `start/stop` → **无参 `start()/stop()`**（新钩子；`stop()` 仍清空两个账本 Map，
 *       与原 `stop()` 语义一致；`start()` 无需再做任何事 ⇒ 不再覆写）。</li>
 * <li>保留 `update()` 的无参形态与全部端口化；**数值/结算节奏/记账路径逐字不变**。</li>
 * </ul>
 */
public class RedBleedPassive extends PassiveSkill {

    /** **本组件的登记 id**（★ 知识归属：组件自己 —— 谁是什么 id 由谁说了算）。 */
    public static final String ID = "red_bleed_passive";

    private VitalsComponent vitals;
    private BuffComponent buff;
    private SanTEComponent sante;

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

    public RedBleedPassive(String id, ComponentServices services) {
        super(id, services, Component.text("流血"), Component.text("红的流血被动"));
    }

    /**
     * 本组件的**被动描述符**（迁移后被动走统一的 {@code addComponent} 入口 ⇒ 无栏位 ⇒ 天然不占热键栏）。
     * 表现数据**逐字沿用**组件构造器里那一对文案（不新拟）；依赖 = 实取清单（`start()` 内的三个调用点）。
     */
    public static final class Specification extends PassiveSkill.Specification {

        public Specification(){
            super(Component.text("流血"), Component.text("红的流血被动"));
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(SanTEComponent.class);
        }

        @Override
        public RedBleedPassive create(String id, ComponentServices services){
            return new RedBleedPassive(id, services);
        }
    }

    /**
     * **写流血结算请求的唯一公开入口**（跨批 API 前移落地）。
     * <p>语义与旧路径**逐条等价**：写入的是 `start()` 里发布出去的那**同一份** {@code playerBleedResolveRequests}
     * （**不另建并行存储**），键 = 受害者 UUID、值 = 待结算层数；结算时点仍由 {@code update()} 的
     * {@code resolveRequestedBleed(...)} 决定。保留本方法并完成账本私有化。
     */
    public void requestResolve(UUID victimId, int stacks) {
        if (victimId == null) {
            return;
        }
        playerBleedResolveRequests.put(victimId, stacks);
    }

    /**
     * **累加流血层数**（唯一写入口之一；两个主武器子类只允许调
     * `requestResolve` / `applyStacks` / `stacksOf` 这三个公开入口，不得再碰账本内部结构）。
     * <p>语义：键 = 受害者 `UUID`；累加后 `Math.clamp(…, 0, MAX_BLEED_STACK = 15)`（上限与旧逻辑一致）；
     * 结算时点不变（仍由 {@code update()} 里 `secondCountdown >= BLEED_SETTLE_INTERVAL_TICKS = 20` 后触发）。
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
     * **只读查询某受害者当前流血层数**（公开入口之一；无副作用）。
     * <b>一经冻结不得再改。</b>
     */
    public int stacksOf(UUID victimId) {
        if (victimId == null) {
            return 0;
        }
        return playerBleedRecord.getOrDefault(victimId, 0);
    }

    /**
     * 停止生效（新钩子，无参）：清空两个账本 Map。
     * <p>与旧 `stop(Player, RoleInstance)` **语义一致**（原实现只做这两件清空）；
     * 本类另覆写 `start()`（协作组件缓存进字段 ✓），两者成对 ✓。
     */
    @Override
    public void stop() {
        playerBleedRecord.clear();
        playerBleedResolveRequests.clear();
    }

    /**
     * **开始生效**：把协作组件**一次查好**缓存进字段 ✓（与本族模型一致）。
     * <p>取组件只能在本钩子（或新写/既有 `start()`）里做 ✗ —— 不得放 `awake()`；
     * 注册表在装配期后冻结 ⇒ 缓存引用与按需解析**恒等** ✓。
     */
    @Override
    public void start(){
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
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
    public void update() {
        //等价说明：本组件与角色实例一对一 ⇒ `svc()` 恒定指向所属实例（旧签名里的 instance/player 由它代替）
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
            vitals.trueDamage(victim, player, BLEED_DAMAGE_PER_SECOND);
            //赋予 红 5秒抗性1, 恢复4点SanTE（药水记账：经 **Buff 组件**的入口（与框架**同一条已记账路径**））
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, BLEED_RESISTANCE_DURATION_TICKS, 1);
            sante.increase(BLEED_SANTE_RECOVER);

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

        //赋予 红 5秒抗性1, 恢复4点SanTE（药水记账：经 **Buff 组件**的入口（与框架**同一条已记账路径**））
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, BLEED_RESISTANCE_DURATION_TICKS, 1);
        sante.increase(BLEED_SANTE_RECOVER);

        victim.spawnParticle(Particle.DUST, victim.getLocation().clone().add(0, 0.5, 0), 1, 1, 1, 1, new Particle.DustOptions(Color.RED, 1f));
    }
}
