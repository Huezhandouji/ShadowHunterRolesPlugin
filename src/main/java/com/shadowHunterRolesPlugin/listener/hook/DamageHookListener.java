package com.shadowHunterRolesPlugin.listener.hook;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * **受伤 / 受治疗的平台事件面**（钩子挂平台事件）。
 *
 * <h2>为什么挂平台事件而不是挂调用点</h2>
 * 合并后的两个入口 {@code VitalsComponent.damage/heal} 的生产调用点覆盖不全（当时的伤害调用点里
 * 大部分走的是**服务集转发形态**的单参原语）⇒ 若把钩子挂在入口上，**玩家砍人 / 流血掉血**这些
 * 真实路径**不会**触发回调 ✗。挂平台事件 ⇒ **玩家受到的一切伤害与治疗都触发** ✓，且
 * **不需要改写任何调用点** ✓。
 *
 * <h2>两条口径</h2>
 * <ul>
 *   <li><b>{@code ignoreCancelled = true}</b> ✓ —— **被取消的事件没有真的发生** ⇒ 不算"受伤 / 受治疗"
 *       ✗（否则会在"伤害被挡下"时误报回调）。</li>
 *   <li><b>只读不取消</b> ✓ —— 本监听器**绝不** {@code setCancelled}、**绝不**改 {@code getDamage()}，
 *       与既有 {@code DamageTrackerListener} 同形态（只做记账）。</li>
 * </ul>
 *
 * <h2>投递路径（不得绕开）</h2>
 * <pre>
 *   事件 → 早退（非 Player 直接 return）→ 平台查找目标实例
 *        （{@code RoleManager.getRoleInstance(Player)}，复用既有 playerRoleMap）
 *        → 无实例 ⇒ **只不通知**（伤害/治疗早已由 Bukkit 结算，本监听器不参与施加 ✓）
 *        → 有实例 ⇒ 按 {@code getAllByType(Participant.class)} 扇出
 *        → 逐个经 {@link RoleInstance#deliverHook}（内部走唯一受保护入口 {@code guardedCall}）✓
 * </pre>
 *
 * <h2>本类边界</h2>
 * **不起服**、**不跑**跨实例双玩家读数与 {@code was QUARANTINED} 反例 ⇒ 那些属 **B-窗口半**（另立卡）✗。
 * 本类只保证：挂载点、注册、编译与单测闸门 ✓。
 *
 * <h2>非玩家伤害源（如实申报）</h2>
 * {@code EntityDamageEvent} 的 {@code getEntity()} 不是 {@code Player} 时**直接早退** ⇒
 * **不触发**任何钩子（不覆盖非玩家承受方）✗。将来要覆盖 ⇒ 加新重载/新事件面 ✓。
 */
public class DamageHookListener implements Listener {

    private final RoleManager roleManager;

    public DamageHookListener(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    /** **受伤**：平台已结算，本处只负责把结果通知**承受方实例**。 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        RoleInstance instance = roleManager.getRoleInstance(victim);
        if (instance == null) {
            return;
        }
        Player source = null;
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player damager) {
            source = damager;
        }
        deliver(instance, "onDamaged", victim, source, event.getDamage());
    }

    /** **受治疗**：平台已结算，本处只负责把结果通知**受治疗方实例**。 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player healed)) {
            return;
        }
        RoleInstance instance = roleManager.getRoleInstance(healed);
        if (instance == null) {
            return;
        }
        deliver(instance, "onHealed", healed, null, event.getAmount());
    }

    /**
     * 按 {@code Participant} 扇出到**目标实例**的每个组件 —— **逐个**经
     * {@link RoleInstance#deliverHook} ⇒ 某个组件抛异常时只隔离它、其余照常收到 ✓
     * （若把整个循环塞进**一次** deliverHook，首个异常就会让"本次派发"后续组件收不到 ✗）。
     */
    private void deliver(RoleInstance instance, String phase, Player target, Player source, double amount) {
        for (VitalsComponent.Participant participant : instance.getAllByType(VitalsComponent.Participant.class)) {
            instance.deliverHook((com.shadowHunterRolesPlugin.roleComponent.RoleComponent) participant,
                    phase, () -> {
                        if ("onDamaged".equals(phase)) {
                            participant.onDamaged(source, amount);
                        } else {
                            participant.onHealed(amount);
                        }
                    });
        }
    }
}
