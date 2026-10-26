package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「红」的流血账本 + 「饮血」+「何足挂齿」（2026-10-21 重做版）。
 *
 * <h2>流血</h2>
 * <ul>
 *   <li>层数上限 15（进化 1 档后 20）；</li>
 *   <li>每秒**消耗 1 层**（4 档后 2 层），每层造成 <b>1 点直接伤害</b>
 *       （{@code physicalDamage} ⇒ 会打断疾跑、会改变 FOV —— 需求逐字）；</li>
 *   <li>技能可以 {@link #requestResolve(UUID, int)} 立刻结算指定层数（不超过当前层数）。</li>
 * </ul>
 *
 * <h2>饮血</h2>
 * 自身 15 格内每有一名"挂有流血"的敌人，每秒回复 <b>1 生命 + 1 SanTE</b>
 * （4 档后结算两遍）。
 *
 * <h2>何足挂齿</h2>
 * 每 **3 次命中**（一次技能/普攻无论打中几个只记 1 次）⇒ 回复 <b>16 SanTE + 4 生命</b>、
 * 所有技能 CD **-1 秒**；2 档后额外 +4 SanTE / +2 生命，并结算所有流血敌人 4 层。
 *
 * <p><b>为什么 CD 减缩用"登记回调"而不是 {@code requires} 三个技能</b>：技能要 {@code requires}
 * 本组件才能加流血；若本组件再 {@code requires} 技能，就成环 ⇒ 整个角色静默消失。
 * 技能在 {@code start()} 里把"我怎么减 CD"登记进来，本组件只负责在触发时调它们。
 */
public class RedBleedPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "red_bleed_passive";

    /** 基础流血层数上限。 */
    public static final int BASE_MAX_BLEED = 15;

    /** 每秒基础结算层数。 */
    public static final int BASE_SETTLE_PER_SECOND = 1;

    /** 流血结算间隔（刻）。 */
    public static final int SETTLE_INTERVAL_TICKS = 20;

    /** 饮血半径（格）。 */
    public static final double DRINK_RADIUS = 15d;

    /** 何足挂齿：每几次命中触发一次。 */
    public static final int HIT_STREAK = 3;

    /** 何足挂齿：回复的 SanTE。 */
    public static final int FRUGAL_SANTE = 16;

    /** 何足挂齿：回复的生命。 */
    public static final double FRUGAL_HEAL = 4d;

    /** 何足挂齿：减少的技能冷却（刻）。 */
    public static final int FRUGAL_CD_REFUND = 20;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private RedEvolutionPassive evolution;

    private final Map<UUID, Integer> bleed = new HashMap<>();
    private final Map<UUID, Integer> resolveRequests = new HashMap<>();
    private final List<Runnable> cooldownReducers = new ArrayList<>();

    private int secondTimer;
    private int hitCount;

    public RedBleedPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends PassiveSkill.Specification<RedBleedPassive> {

        public Specification() {
            super(Component.text("流血 / 饮血 / 何足挂齿"),
                    List.of(
                            Component.text("命中挂 5 层流血（上限 15，进化后 20）"),
                            Component.text("每秒结算 1 层，每层 1 点直接伤害"),
                            Component.text("15 格内每个流血敌人：每秒回 1 生命 + 1 特殊值"),
                            Component.text("每 3 次命中：回 16 特殊值 + 4 生命，技能 CD -1 秒")
                    ));
            requires(VitalsComponent.class).requires(SanTEComponent.class).requires(RedEvolutionPassive.class);
        }

        @Override
        public RedBleedPassive create(String id, ComponentServicesPort services) {
            return new RedBleedPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        sante = getComponent(SanTEComponent.class);
        evolution = getComponent(RedEvolutionPassive.class);
    }

    @Override
    public void stop() {
        bleed.clear();
        resolveRequests.clear();
        cooldownReducers.clear();
        hitCount = 0;
    }

    // ───────── 公开写入口 ─────────

    /** 加流血（按进化后的上限 clamp）。 */
    public void applyStacks(UUID victimId, int amount) {
        if (victimId == null || amount <= 0) {
            return;
        }
        int current = bleed.getOrDefault(victimId, 0);
        bleed.put(victimId, Math.clamp(current + amount, 0, cap()));
    }

    /** 当前层数。 */
    public int stacksOf(UUID victimId) {
        return victimId == null ? 0 : bleed.getOrDefault(victimId, 0);
    }

    /** 立刻结算（不超过当前层数；由技能调用）。 */
    public void requestResolve(UUID victimId, int stacks) {
        if (victimId != null && stacks > 0) {
            resolveRequests.put(victimId, stacks);
        }
    }

    /** 结算**所有**流血敌人若干层（进化 2 档 / 大招用）。 */
    public void settleAll(Player caster, int stacks) {
        if (caster == null || stacks <= 0) {
            return;
        }
        for (UUID id : new ArrayList<>(bleed.keySet())) {
            Player victim = Bukkit.getPlayer(id);
            if (canReceiveBleed(victim)) {
                settle(victim, caster, stacks);
            }
        }
    }

    /** 半径内挂有流血的敌人数（饮血 / 诊断共用）。 */
    public int bleedingNear(Player caster, double radius) {
        if (caster == null) {
            return 0;
        }
        int count = 0;
        for (Player candidate : caster.getLocation().getNearbyPlayers(radius)) {
            if (candidate != null && !candidate.equals(caster) && canReceiveBleed(candidate)
                    && stacksOf(candidate.getUniqueId()) > 0) {
                count++;
            }
        }
        return count;
    }

    /** 技能登记"减 CD"回调（{@code start()} 登记、{@code stop()} 撤销）。 */
    public void registerCooldownReducer(Runnable reducer) {
        if (reducer != null) {
            cooldownReducers.add(reducer);
        }
    }

    public void unregisterCooldownReducer(Runnable reducer) {
        cooldownReducers.remove(reducer);
    }

    /**
     * **一次命中**（普攻或技能，无论打中几个敌人只调一次）。
     * 每 {@value #HIT_STREAK} 次触发「何足挂齿」。
     */
    public void onPlayerHit(Player caster) {
        if (caster == null) {
            return;
        }
        hitCount++;
        if (hitCount < HIT_STREAK) {
            return;
        }
        hitCount = 0;

        int santeGain = FRUGAL_SANTE + (evolution == null ? 0 : evolution.frugalSanTEBonus());
        double heal = FRUGAL_HEAL + (evolution == null ? 0d : evolution.frugalHealBonus());
        sante.increase(santeGain);
        vitals.heal(caster, heal);
        for (Runnable reducer : new ArrayList<>(cooldownReducers)) {
            reducer.run();
        }
        if (evolution != null && evolution.frugalSettleStacks() > 0) {
            settleAll(caster, evolution.frugalSettleStacks());
        }
        RedFx.heal(caster);
    }

    // ───────── 每秒 ─────────

    @Override
    public void update() {
        Player player = svc().self().player();
        if (player == null || !player.isOnline()) {
            return;
        }
        purge();
        drainRequests(player);

        if (++secondTimer < SETTLE_INTERVAL_TICKS) {
            return;
        }
        secondTimer = 0;

        settleSecond(player);
        drink(player);
    }

    private void settleSecond(Player caster) {
        int perSecond = BASE_SETTLE_PER_SECOND + (evolution == null ? 0 : evolution.extraSettlePerSecond());
        for (UUID id : new ArrayList<>(bleed.keySet())) {
            Player victim = Bukkit.getPlayer(id);
            if (!canReceiveBleed(victim)) {
                bleed.remove(id);
                continue;
            }
            settle(victim, caster, perSecond);
        }
    }

    /** 饮血：15 格内每个流血敌人回 1 生命 + 1 SanTE（4 档结算两遍）。 */
    private void drink(Player caster) {
        int times = 1 + (evolution == null ? 0 : evolution.extraDrinkTimes());
        for (int i = 0; i < times; i++) {
            int count = bleedingNear(caster, DRINK_RADIUS);
            if (count <= 0) {
                return;
            }
            vitals.heal(caster, count);
            sante.increase(count);
        }
    }

    // ───────── 内部 ─────────

    private void drainRequests(Player caster) {
        if (resolveRequests.isEmpty()) {
            return;
        }
        List<UUID> handled = new ArrayList<>(resolveRequests.keySet());
        for (UUID id : handled) {
            Integer amount = resolveRequests.remove(id);
            Player victim = Bukkit.getPlayer(id);
            if (victim != null && amount != null) {
                settle(victim, caster, amount);
            }
        }
    }

    /** 结算 {@code amount} 层：每层 1 点直接伤害。 */
    private void settle(Player victim, Player caster, int amount) {
        UUID id = victim.getUniqueId();
        int current = bleed.getOrDefault(id, 0);
        if (current <= 0 || amount <= 0) {
            return;
        }
        int settled = Math.min(amount, current);
        int left = current - settled;
        if (left <= 0) {
            bleed.remove(id);
        } else {
            bleed.put(id, left);
        }
        // 直接伤害：会打断疾跑、改变 FOV（需求逐字）；不额外制造击退
        vitals.physicalDamage(victim, caster, settled, 0d);
        RedFx.bleedTick(victim);
    }

    private void purge() {
        if (bleed.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(bleed.keySet())) {
            Integer value = bleed.get(id);
            if (value == null || value <= 0 || !canReceiveBleed(Bukkit.getPlayer(id))) {
                bleed.remove(id);
            }
        }
    }

    private int cap() {
        return evolution == null ? BASE_MAX_BLEED : evolution.bleedCap();
    }

    /** 死亡用血量判断：死亡界面里 {@code isDead()} 仍可能是 false。 */
    public static boolean canReceiveBleed(Player victim) {
        return victim != null && victim.isOnline() && !victim.isDead() && victim.getHealth() > 0d;
    }
}
