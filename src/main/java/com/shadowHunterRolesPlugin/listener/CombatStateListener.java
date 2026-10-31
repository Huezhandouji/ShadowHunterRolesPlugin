package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import com.shadowHunterRolesPlugin.platform.FactionManager;
import com.shadowHunterRolesPlugin.roleComponent.builtin.CombatStateComponent;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * **战斗状态的伤害侧规则**（2026-10-31 需求：修改进入战斗 / 脱离战斗的框架）。
 *
 * <h2>本监听器负责的四条规则</h2>
 * <ol>
 *   <li><b>规则 3</b>：玩家受到<b>敌方玩家</b>（直接攻击 / 弹射物 / 插件技能伤害）的攻击 ⇒
 *       刷新双方的战斗状态（受害者按"被攻击"进入战斗；攻击者也因主动出手刷新）；</li>
 *   <li><b>规则 4</b>：被<b>伤害性方块</b>伤害（仙人掌 / 岩浆 / 火 / 岩浆块 / 营火 / 落方块 / 后续点燃）
 *       ⇒ <b>不进入战斗</b>，改为进入 {@code CombatStateComponent} 的回血间隔期（3 秒）；</li>
 *   <li><b>规则 5</b>：<b>持续伤害</b>（中毒 / 凋零 / 龙息）不刷新战斗 —— 只算"被施加的那一次"，
 *       那一次由规则 2（BuffComponent 施加状态）或规则 3（初始直接伤害）负责；后续跳数在这里被排除；</li>
 *   <li><b>规则 1</b>（周围 4 格有敌人一直算战斗中）不在这里：它由
 *       {@code CombatStateComponent#update()} 每刻自检。</li>
 * </ol>
 *
 * <h2>伤害方归属（与既有写账同源）</h2>
 * <ul>
 *   <li>优先读 Paper {@link DamageSource} 的 causing / direct entity（弹射物按射手归属）；</li>
 *   <li>其次读 {@link EntityDamageByEntityEvent} 的 damager（弹射物再按射手归属）；</li>
 *   <li>最后：<b>插件主动伤害</b>走 {@code victim.damage(...)}，事件里没有伤害实体，但
 *       {@code DamageUtil} 会先把 last_damager 写进 PDC ⇒ 仅在 {@code DamageCause.CUSTOM} 时回退读它
 *       （其余自然伤害原因不读，避免旧 PDC 造成误刷新）。</li>
 * </ul>
 *
 * <h2>边界（如实申报）</h2>
 * <ul>
 *   <li>非玩家承伤者直接早退（与 {@code DamageHookListener} 同口径）；</li>
 *   <li>被取消的伤害不刷新（{@code ignoreCancelled = true}：取消 = 没有真的发生）；</li>
 *   <li>插件周期性物伤若走 {@code DamageUtil.dealtPhysicalDamage}，会以 CUSTOM 事件按 last_damager 刷新 ——
 *       长时段跳伤请改用真伤（{@code dealtTrueDamage}，走 setHealth、不产生伤害事件）；</li>
 *   <li>THORNS / 环境爆炸等间接来源：能解析到敌方玩家就刷新，解析不到就静默跳过。</li>
 * </ul>
 */
public class CombatStateListener implements Listener {

    /** 规则 4：伤害性方块（方块造成的伤害原因集合）。 */
    private static final Set<EntityDamageEvent.DamageCause> BLOCK_DAMAGE_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.CONTACT,       // 仙人掌 / 甜浆果
            EntityDamageEvent.DamageCause.FIRE,          // 站在火里
            EntityDamageEvent.DamageCause.FIRE_TICK,     // 被点燃后的持续灼烧
            EntityDamageEvent.DamageCause.LAVA,          // 岩浆
            EntityDamageEvent.DamageCause.HOT_FLOOR,     // 岩浆块
            EntityDamageEvent.DamageCause.CAMPFIRE,      // 营火
            EntityDamageEvent.DamageCause.FALLING_BLOCK // 落方块
    );

    /** 规则 5：长时间伤害性药水 / 效果类伤害原因（只算施加那一刻，后续跳数不刷新）。 */
    private static final Set<EntityDamageEvent.DamageCause> PERIODIC_EFFECT_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.POISON,
            EntityDamageEvent.DamageCause.WITHER,
            EntityDamageEvent.DamageCause.DRAGON_BREATH
    );

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID victimId = victim.getUniqueId();
        EntityDamageEvent.DamageCause cause = event.getCause();

        //规则 4：方块伤害 ⇒ 不进入战斗，进入回血间隔期
        if (isBlockDamage(event, cause)) {
            CombatStateComponent.beginHealingIntervalFor(victimId);
            return;
        }

        //规则 5：持续伤害的后续跳数不刷新战斗状态
        if (PERIODIC_EFFECT_CAUSES.contains(cause)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null && cause == EntityDamageEvent.DamageCause.CUSTOM) {
            attacker = resolvePluginAttacker(victim);
        }
        if (attacker == null || attacker.getUniqueId().equals(victimId)) {
            return;
        }
        if (!FactionManager.isHostile(attacker.getUniqueId(), victimId)) {
            return;
        }

        //规则 3：被敌方攻击 ⇒ 双方进入/延续战斗
        CombatStateComponent.refreshFor(victimId);
        CombatStateComponent.refreshFor(attacker.getUniqueId());
    }

    /** 方块伤害：事件类型是"被方块伤害"，或伤害原因属于方块类。 */
    private static boolean isBlockDamage(EntityDamageEvent event, EntityDamageEvent.DamageCause cause) {
        return event instanceof EntityDamageByBlockEvent || BLOCK_DAMAGE_CAUSES.contains(cause);
    }

    /**
     * **规则 2 / 5（原版药水线）**：敌方投掷的喷溅药水落到玩家身上时，刷新双方战斗状态<b>一次</b>。
     *
     * <p>为什么需要它：中毒 / 凋零 / 瞬间伤害这类效果由原版施加时<b>没有伤害事件</b>可以挂靠：
     * 伤害事件只出现在后续 DoT 跳数（被规则 5 排除）。若不在"药水落地"这一瞬间记一次，
     * 被泼药的玩家就永远不会因为这次敌方攻击进入战斗。
     * <p>只处理<b>玩家投掷者</b>与<b>敌对关系</b>；受影响者里的非玩家实体直接跳过。
     */
    @EventHandler(ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower)) {
            return;
        }
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player victim) || victim.getUniqueId().equals(thrower.getUniqueId())) {
                continue;
            }
            if (!FactionManager.isHostile(thrower.getUniqueId(), victim.getUniqueId())) {
                continue;
            }
            CombatStateComponent.refreshFor(victim.getUniqueId());
            CombatStateComponent.refreshFor(thrower.getUniqueId());
        }
    }

    /** 解析伤害方玩家：DamageSource（causing / direct、弹射物射手）→ 事件 damager → null。 */
    private static Player resolveAttacker(EntityDamageEvent event) {
        Player fromSource = playerFromSource(event.getDamageSource());
        if (fromSource != null) {
            return fromSource;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Player player) {
                return player;
            }
            if (damager instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }

    /** 从 Paper DamageSource 解析玩家：causing 优先，其次 direct（弹射物按射手）。 */
    private static Player playerFromSource(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity causing = source.getCausingEntity();
        if (causing instanceof Player player) {
            return player;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player) {
            return player;
        }
        if (direct instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (causing instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /** 插件主动伤害的归属回退：{@code DamageUtil} 已把"最后伤害者"写进 PDC（仅 CUSTOM 原因使用）。 */
    private static Player resolvePluginAttacker(Player victim) {
        UUID lastDamager = DamageUtil.getLastDamagerUUID(victim);
        if (lastDamager == null) {
            return null;
        }
        return Bukkit.getPlayer(lastDamager);
    }
}
