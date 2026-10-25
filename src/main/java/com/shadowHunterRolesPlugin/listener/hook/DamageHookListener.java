package com.shadowHunterRolesPlugin.listener.hook;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * 受伤 / 受治疗的平台事件面（钩子挂平台事件）。
 *
 * <h2>为什么挂平台事件而不是挂调用点</h2>
 * 合并后的两个入口 {@code VitalsComponent.damage/heal} 的生产调用点覆盖不全（伤害调用点里
 * 大部分走的是服务集转发形态的单参原语），因此若把钩子挂在入口上，玩家砍人 / 流血掉血这些
 * 真实路径不会触发回调。挂平台事件则玩家受到的一切伤害与治疗都触发，且不需要改写任何调用点。
 *
 * <h2>两条口径</h2>
 * <ul>
 *   <li><b>{@code ignoreCancelled = true}</b> —— 被取消的事件没有真的发生，不算"受伤 / 受治疗"
 *       （否则会在"伤害被挡下"时误报回调）。</li>
 *   <li><b>只读不取消</b> —— 本监听器绝不 {@code setCancelled}、绝不改 {@code getDamage()}，
 *       与既有 {@code DamageTrackerListener} 同形态（只做记账）。</li>
 * </ul>
 *
 * <h2>投递路径（不得绕开）</h2>
 * <pre>
 *   事件 → 早退（非 Player 直接 return）→ 平台查找目标实例
 *        （{@code RoleManager.getRoleInstance(Player)}，复用既有 playerRoleMap）
 *        → 无实例：只不通知（伤害 / 治疗早已由 Bukkit 结算，本监听器不参与施加）
 *        → 有实例：取实例里的生命组件（按它自己的登记 id {@link VitalsComponent#ID}）
 *        → 按 {@code forEachDamagedListener} / {@code forEachHealedListener} 扇出到承受方订阅名单
 *        → 逐个经 {@link RoleInstance#deliverHook}（内部走唯一受保护入口 {@code guardedCall}）
 * </pre>
 *
 * <h2>通知顺序</h2>
 * 名单序 = 订阅序（各组件在自己的 {@code start()} 里登记 ⇒ 等于装配序）。
 * 与旧实现（按 {@code getAllByType(Participant.class)} 的注册表序扇出）在相对次序上等价 ——
 * 两者都是"按装配序"。
 *
 * <h2>本类边界</h2>
 * 不起服、不跑跨实例双玩家读数与 {@code was QUARANTINED} 反例，那些需另行取证。
 * 本类只保证：挂载点、注册、编译与单测闸门。
 *
 * <h2>非玩家伤害源（如实申报）</h2>
 * {@code EntityDamageEvent} 的 {@code getEntity()} 不是 {@code Player} 时直接早退，
 * 因此不触发任何钩子（不覆盖非玩家承受方）。将来要覆盖则加新重载 / 新事件面。
 */
public class DamageHookListener implements Listener {

    private final RoleManager roleManager;

    public DamageHookListener(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    /** 受伤：平台已结算，本处只负责把结果通知承受方实例的受伤订阅名单。 */
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
        deliverDamaged(instance, new VitalsComponent.Damaged(source, event.getDamage()));
    }

    /** 受治疗：平台已结算，本处只负责把结果通知受治疗方实例的受治疗订阅名单。 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player healed)) {
            return;
        }
        RoleInstance instance = roleManager.getRoleInstance(healed);
        if (instance == null) {
            return;
        }
        deliverHealed(instance, new VitalsComponent.Healed(event.getAmount()));
    }

    /**
     * 把一次受伤投给承受方实例的受伤订阅名单 —— 逐个经
     * {@link RoleInstance#deliverHook}，某个监听器抛异常时只隔离它、其余照常收到
     * （若把整个循环塞进一次 deliverHook，首个异常就会让"本次派发"后续监听器收不到）。
     * <p>该角色没装生命组件 ⇒ 没有名单可投（防御性早退，与 {@code PlayerKilledHookListener} 同款）。
     */
    private void deliverDamaged(RoleInstance instance, VitalsComponent.Damaged damaged) {
        if (!(instance.componentRegistry().getById(VitalsComponent.ID) instanceof VitalsComponent vitals)) {
            return;
        }
        vitals.forEachDamagedListener(entry ->
                instance.deliverHook(entry.owner(), "onDamaged", () -> entry.listener().accept(damaged)));
    }

    /** 把一次受治疗投给承受方实例的受治疗订阅名单（与 {@link #deliverDamaged} 同一纪律）。 */
    private void deliverHealed(RoleInstance instance, VitalsComponent.Healed healed) {
        if (!(instance.componentRegistry().getById(VitalsComponent.ID) instanceof VitalsComponent vitals)) {
            return;
        }
        vitals.forEachHealedListener(entry ->
                instance.deliverHook(entry.owner(), "onHealed", () -> entry.listener().accept(healed)));
    }
}
