package com.shadowHunterRolesPlugin.listener.hook;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 「玩家被玩家击杀」的平台事件面 —— 把一次击杀投递给**击杀者那一侧**的生命组件订阅名单。
 *
 * <h2>为什么挂平台事件（{@code PlayerDeathEvent}）而不是挂伤害调用点</h2>
 * 与 {@link DamageHookListener} 同源的理由：击杀者的判定要覆盖"一切真实伤害"，而本系统的伤害
 * 有一半绕过原版事件、另一半走原版事件 ⇒ 只有平台事件面能一次覆盖两侧。
 *
 * <h2>为什么用 {@code LOWEST} 优先级</h2>
 * 被杀者的角色 id 必须**在清角色之前**读出来：{@code listener/PlayerListener#onPlayerDeath}
 * （无显式优先级 ⇒ {@code NORMAL}）会 {@code roleManager.clearRole(player)} 把被杀者的角色实例
 * 连同角色归属一起抹掉。{@code LOWEST} 严格早于 {@code NORMAL}（优先级是跨插件的全序，不依赖
 * 注册先后）⇒ 本监听器读到的被杀者实例与角色 id 一定是"死前那一刻"的。
 *
 * <h2>投递路径（不得绕开）</h2>
 * <pre>
 *   PlayerDeathEvent → 早退（被杀者已在监听器里限定为 Player）
 *      → 击杀者 = {@link DamageUtil#getLastDamager(Player)}（**非**原版 getKiller）
 *      → 早退：无击杀者 / 自杀（killer == victim）/ 击杀者无角色实例
 *      → 取击杀者实例里的生命组件（按它自己的登记 id {@link VitalsComponent#ID}）
 *      → 构造 {@link VitalsComponent.PlayerKilledEvent}（两个玩家 + 两个角色 id）
 *      → 逐个经 {@link RoleInstance#deliverHook}（内部走唯一受保护入口 guardedCall）
 * </pre>
 *
 * <h2>逐个投递而不是"一趟循环全调"</h2>
 * 与 {@code DamageHookListener#deliver} 同一纪律：每条登记各包一次 {@code deliverHook} ⇒ 某个订阅者
 * 抛异常时只隔离它、其余照常收到（若把整趟塞进一次 {@code deliverHook}，首个异常就会让后面的收不到）。
 *
 * <h2>已申报的边界</h2>
 * <ul>
 *   <li><b>只投击杀者一侧</b>：被杀者那一侧的实例在同一个 tick 里就被清掉（见上方优先级一节），
 *       通知它没有意义；"我被谁杀了"请自行挂 {@code PlayerDeathEvent}。</li>
 *   <li><b>击杀者必须已装角色</b>：没有角色实例就没有本组件的名单可投 ⇒ 不产生事件
 *       （"无角色者杀人是否算击杀"是产品口径，本类不擅自替它定）。</li>
 *   <li><b>被杀者可以无角色</b>：此时 {@code getVictimRoleId()} 为 {@code null}，事件照常产生。</li>
 *   <li><b>不做敌对 / 阵营过滤</b>：误伤、同阵营互杀同样产生事件 —— 要不要计由订阅者自己判
 *       （与外部消费方 {@code SHDFGamePlugin} 把过滤放在自己的 DeathHandler 里同源）。</li>
 *   <li><b>击杀归属继承 {@link DamageUtil#getLastDamager} 的已知边界</b>：那条 PDC 在死亡时不清、
 *       且只由"玩家直接打玩家"更新 ⇒ ① 死在怪物 / 环境手里时可能读到本命里上一次的玩家伤害者；
 *       ② 弹射物（箭矢）击杀不入账（写账侧只认"直接是玩家"的 damager）。两件事都不在本类里补 ——
 *       补了就是动既有记账口径，且会让 {@code RoleAPI#getLastDamagerUuid} 的语义跟着变。</li>
 * </ul>
 */
public class PlayerKilledHookListener implements Listener {

    private final RoleManager roleManager;

    public PlayerKilledHookListener(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    /** 死亡 ⇒ 通知击杀者实例里的击杀订阅名单（优先级口径见类注释）。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        Player killer = DamageUtil.getLastDamager(victim);
        if (killer == null || killer.equals(victim)) {
            return;     // 无击杀者（怪物 / 环境 / 离线）：不该产生击杀事件；自杀同理（不给自己发"击杀"）
        }

        RoleInstance killerInstance = roleManager.getRoleInstance(killer);
        if (killerInstance == null) {
            return;     // 击杀者没有角色 ⇒ 没有名单可投
        }

        if (!(killerInstance.componentRegistry().getById(VitalsComponent.ID) instanceof VitalsComponent vitals)) {
            return;     // 该角色没装生命组件（当前装配表里不会发生，防御性早退）
        }

        VitalsComponent.PlayerKilledEvent killed = new VitalsComponent.PlayerKilledEvent(
                killer,
                victim,
                roleIdOf(killerInstance),
                roleIdOf(roleManager.getRoleInstance(victim))     // 被杀者无角色 / 已不在表里 ⇒ null
        );

        vitals.forEachPlayerKilledListener(entry ->
                killerInstance.deliverHook(entry.owner(), "onPlayerKilled",
                        () -> entry.listener().accept(killed)));
    }

    /** 该实例所装角色的登记 id；实例为空（或角色为空）回 {@code null}。 */
    private static String roleIdOf(RoleInstance instance) {
        if (instance == null || instance.getRole() == null) {
            return null;
        }
        return instance.getRole().getId();
    }
}
