package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * "最后伤害者"的**原版侧写账**（另一条写账路是 {@code DamageUtil#dealtPhysicalDamage}，两路共用
 * {@link DamageUtil#LAST_DAMAGER_KEY} 这一条键）；击杀归属读口 = {@code DamageUtil#getLastDamager}。
 *
 * <h2>伤害方归属（写账口径）</h2>
 * <ul>
 *   <li><b>直接是玩家</b>（近战）：记该玩家 —— 既有口径，不变；</li>
 *   <li><b>弹射物</b>（箭矢 / 三叉戟 / 雪球…）：按 {@code Projectile#getShooter()} 记到**射手**头上。
 *       补的是已申报的边界"弹射物命中时本监听器看到的是弹射物而非射手 ⇒ 弓弩击杀不入账"；
 *       非玩家射手（发射器、无主弹射物等）不入账 —— 口径与"怪物伤害不入账"一致。</li>
 * </ul>
 *
 * <p>姿态与近战路径一致：只做记账，不取消事件、不改伤害值。{@code shooter == victim}
 * （自己射中自己）照记 —— 与 {@code dealtPhysicalDamage} 的自伤记账同口径，
 * "自杀不算击杀"归读侧调用方判（见 {@code DamageUtil#getLastDamager} 的 javadoc）。
 */
public class DamageTrackerListener implements Listener {

    @EventHandler
    public void onPlayerDamaged(EntityDamageByEntityEvent event){
        if(!(event.getEntity() instanceof Player victim)) return;

        Player damager = resolveDamagingPlayer(event.getDamager());
        if(damager == null) return;

        victim.getPersistentDataContainer().set(
                DamageUtil.LAST_DAMAGER_KEY,
                PersistentDataType.STRING,
                damager.getUniqueId().toString()
        );
    }

    /** 伤害方归属：直接是玩家 ⇒ 本人；弹射物 ⇒ 射手；其余（含非玩家射手）⇒ null（不入账）。 */
    private Player resolveDamagingPlayer(Entity damager){
        if(damager instanceof Player player) return player;
        if(damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter){
            return shooter;
        }
        return null;
    }
}
