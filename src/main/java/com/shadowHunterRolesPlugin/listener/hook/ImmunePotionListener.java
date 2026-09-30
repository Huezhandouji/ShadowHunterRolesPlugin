package com.shadowHunterRolesPlugin.listener.hook;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

/**
 * IMMUNE 期间的"原版负面药水"免疫（平台事件面，<b>取消型保护</b>）。
 *
 * <h2>为什么需要本类（真缺口）</h2>
 * buff 账本只挡得住<b>本系统自己的</b>负面 buff：{@code BuffManager#addBuff} 见到 IMMUNE 即早退，
 * 因此 STUN / SILENCE 上不去。但玩家身上的原版药水效果有两条绕过账本的施加路径：
 * <ol>
 *   <li><b>原版来源</b>：喝药 / 溅射药水 / 生物（洞穴蜘蛛、凋灵、流浪者…）/ 环境（潮涌、信标、区域效果云）
 *       / 指令 {@code /effect}；</li>
 *   <li><b>插件来源</b>：本工程自己的技能直接调 {@code victim.addPotionEffect(...)}
 *       （例如 {@code sinThorn} 技能给敌人上的缓慢 / 失明），这类调用不经过 buff 组件。</li>
 * </ol>
 * 两条都走同一个服务端事件，因此本类在事件面上补上"原版那一半"的免疫。
 *
 * <h2>判据（两件，缺一不可）</h2>
 * <ol>
 *   <li><b>目标</b>：事件实体是玩家，且该玩家<b>此刻处于 IMMUNE 下</b>
 *       （走 {@code RoleManager.getRoleInstance(Player)} 取实例 → 按 {@code BuffComponent.ID} 取组件 →
 *       {@code has(IMMUNE)}；未选角色 / 无该组件 ⇒ 不拦）；</li>
 *   <li><b>效果</b>：被修改的效果类型属于 {@link PotionEffectTypeCategory#HARMFUL} 分类
 *       （判据来自服务端注册表，本类不维护类型名单）。</li>
 * </ol>
 * 增益 / 中性一律放行（{@code BAD_OMEN} 是 {@code NEUTRAL} ⇒ 不在免疫范围内）。
 *
 * <h2>为何是"取消"而不是"只读"</h2>
 * 与 {@code HotbarItemProtectionListener} 同一族（保护侧）：目标是"不让这次施加生效"，
 * 手段只能是 {@code setCancelled(true)}；照"只读不取消"写就是一个假功能。
 *
 * <h2>只处理 ADDED / CHANGED</h2>
 * {@code CLEARED} / {@code REMOVED}（牛奶、到期、指令清除…）没有"要被施加的效果"，本类一律不碰
 * —— 否则会变成"免疫期间连自己的效果都清不掉"。
 *
 * <h2>覆盖范围（有意为之）</h2>
 * <b>不按 {@code Cause} 过滤</b>：插件技能（{@code Cause.PLUGIN}）施加的负面药水同样被挡，
 * 与原版来源一视同仁 —— 否则"免疫"在 PvP 里对技能无效，形同半个免疫。
 *
 * <h2>本类边界（如实申报）</h2>
 * 不起服、不喝药、不写证据件 ⇒ "取消是否真的让效果没上"（{@code addPotionEffect} 回 {@code false}、
 * 玩家身上确实查不到该效果）<b>无运行级读数</b>。离线也测不了：{@code PotionEffectType} 需要活服务端的
 * 注册表才能初始化（实测：离线引用任意常量即 {@code ExceptionInInitializerError}），
 * 而 {@link EntityPotionEffectEvent} 是具体类、其构造又必须吃真实的 {@code PotionEffect}。
 * 因此本类只保证：挂载点、注册、编译与单测闸门。需要取证时的最小步骤：起服 → 给 IMMUNE → 喝一瓶
 * 负面药水 / 用 {@code /effect give} → 观察效果是否没上。
 */
public class ImmunePotionListener implements Listener {

    private final RoleManager roleManager;

    public ImmunePotionListener(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    /**
     * 原版药水效果的施加 / 覆盖入口：IMMUNE 下的负面（{@code HARMFUL}）效果一律取消。
     *
     * <p>主线程前提天然满足：该事件由服务端在自己的效果结算路径上同步触发。
     * <p>{@code ignoreCancelled = true}：已经被别的监听器挡下的施加不再处理（效果本来就上不去）。
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }

        PotionEffectType type = event.getModifiedType();
        if (type == null || type.getCategory() != PotionEffectTypeCategory.HARMFUL) {
            return;
        }

        if (!isImmune(player)) {
            return;
        }

        event.setCancelled(true);
    }

    /**
     * 该玩家此刻是否处于 IMMUNE 下。
     * <p>取实例走既有 {@code playerRoleMap}（不新增缓存）；按 id 取组件复用既有查取入口
     * （注册表在实例激活前已冻结，事件只可能来自已入表的实例 ⇒ 读口不会抛"未冻结"）。
     * <p>未选角色 / 容器内没有 buff 组件 ⇒ {@code false}（不拦）。
     */
    private boolean isImmune(Player player) {
        RoleInstance instance = roleManager.getRoleInstance(player);
        if (instance == null) {
            return false;
        }
        return instance.componentRegistry().getById(BuffComponent.ID) instanceof BuffComponent buffs
                && buffs.has(BuffType.IMMUNE);
    }
}
