package com.shadowHunterRolesPlugin.listener.hook;

import com.shadowHunterRolesPlugin.roleComponent.HotbarItems;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 热键栏物品的"不可动"保护（平台事件面）。
 *
 * <h2>本类与 {@code DamageHookListener} 的关键不对称（别照抄错形态）</h2>
 * 两个监听器都落在平台事件面上，但对事件的处理姿态相反，不是同一族：
 * <ul>
 *   <li><b>{@code DamageHookListener}（读侧 / 回调侧）</b>：伤害早已由平台结算，因此监听器只读、绝不取消；
 *       它只把结果通知给组件（{@code Participant}，{@code void}）。</li>
 *   <li><b>本类（保护侧）</b>：目标是"不让物品被拿走"，实现手段只能是 {@code setCancelled(true)}。
 *       若照"只读不取消"写，则什么都保护不了 —— 那是假功能。</li>
 * </ul>
 * 一句话："只读不取消"适用于"事件已发生、我只通知"；"取消"适用于"我要阻止事件生效"。
 * 本类采用"取消"的全部理由就在这里。
 *
 * <h2>为什么需要本类（真缺口）</h2>
 * 既有保护只覆盖两类，且只覆盖这两类：
 * <ul>
 *   <li>{@code InventoryClickEvent} —— {@code SkillListener} / {@code MainWeaponListener} /
 *       {@code BowWeaponListener} 内有（会取消）</li>
 *   <li>{@code PlayerDropItemEvent}（Q 丢）—— 同上（会取消，且被复用为"Q 释放技能"；
 *       弓弩管道只取消、不复用为任何入口）</li>
 * </ul>
 * 而下面四类事件全仓零命中，玩家可用它们把热键栏物品弄走：
 * <ol>
 *   <li>{@link InventoryDragEvent} —— 拖拽（鼠标按住铺过格子）</li>
 *   <li>{@link PlayerSwapHandItemsEvent} —— F 键与副手交换</li>
 *   <li>{@link InventoryMoveItemEvent} —— 漏斗 / 其他容器搬运</li>
 *   <li>{@link PrepareItemCraftEvent} —— 放进合成格</li>
 * </ol>
 * 本类只补这四类；既有的两类不重复实现（避免两个监听器对同一事件各取消一次的无谓重复）。
 *
 * <h2>判据 = 唯一的"本系统物品"判定点（复用，不新增第二套）</h2>
 * 三个调用点统一走 {@link HotbarItems#isSystemItem(ItemStack)} —— 那是三支家族识别键的**唯一判定点**，
 * 它内部读的就是 {@link Skill.Utils#isSkillItem(ItemStack)} ·
 * {@link MainWeapon.Utils#isMainWeapon(ItemStack)} 与 {@link BowWeapon.Utils#isBowWeapon(ItemStack)}
 * （键名 = {@code SKILL_KEY} / {@code MAIN_WEAPON_KEY} / {@code BOW_WEAPON_KEY}，由各组件的
 * {@code buildItem()} 最后一步写入）。三者都自带 null / 空气判空，因此可直接吃事件给的物品。
 * <p>★ <b>凡在物品栏里放东西的组件家族，其识别键都必须出现在 {@code HotbarItems#isSystemItem}</b>
 * （与 {@code HotbarItems#clearFrom} 同一份"家族键清单"，只此一处），否则该家族的物品可以被拖拽 / F 键 /
 * 漏斗 / 合成格弄走 —— 漏一条就是保护上的真缺口。
 * <p>本处理器**不再自己抄一份键清单**：历史形态是这里与 {@code HotbarItems} 各持一份三键判据，
 * 属"同一判据两种说法"（加家族时漏改任一处都不会报错）。
 * <p><b>为何不自己写判据</b>：那会变成"两套识别逻辑"，与本工程"单一实现点"的纪律冲突。
 *
 * <h2>只对"真正在热键栏里"的物品保护（边界）</h2>
 * {@link InventoryMoveItemEvent} 的监听器有两个方向：本类的意图是"物品离开玩家的受保护容器"，
 * 因此取 {@link InventoryMoveItemEvent#getSource()}（来源）判据；来源是其它容器（如漏斗自身）时不动。
 * <p>{@link PrepareItemCraftEvent} 的 {@code getInventory()} 即合成矩阵，矩阵里出现受保护物品即取消。
 *
 * <h2>本类边界（如实申报）</h2>
 * 不起服、不进档、不写证据件，因此上述四个事件在真实交互下是否真的被拦（拖拽 / F 键 / 漏斗 / 合成格），
 * 以及"取消是否会影响既有 Q 丢施法链路"，均无运行级读数，需另行取证。
 */
public class HotbarItemProtectionListener implements Listener {

    /**
     * 受保护判定：本系统写进物品栏的东西。
     * <p>判据本体**只有一处** —— {@link HotbarItems#isSystemItem(ItemStack)}（三支家族识别键的唯一判定点），
     * 本处理器的三个调用点都走它。历史形态是在这里把三支键各写一遍（与 {@code HotbarItems} 各持一份），
     * 那正是"同一判据两种说法"：加一个家族时漏改这里，物品就能被拖走（漏改那边则清不掉）。
     */
    private static boolean isProtected(ItemStack item) {
        return HotbarItems.isSystemItem(item);
    }

    /**
     * 拖拽：拖拽经过的每一格都在 {@code getNewItems()} 里，任一格含受保护物品即取消整次拖拽。
     * <p>整次取消而不是"只挡那一格"：Bukkit 的拖拽是一次事务，逐格部分挡会让物品换位而不是不动，
     * 与"不可动"的目标不符。
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (isProtected(entry.getValue())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** F 键：主手 ↔ 副手交换。任一侧含受保护物品即取消。 */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isProtected(event.getMainHandItem()) || isProtected(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * 容器间搬运（漏斗 / 投掷器 / 其它容器）：来源侧含受保护物品即取消。
     * <p>只看来源、不看目的地，既有"把普通物品搬进玩家容器"的行为不受影响。
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isProtected(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * 合成格（有真实限制，如实申报）：{@link PrepareItemCraftEvent} 不是 {@code Cancellable}，
     * 因此无法用它阻止合成。本处理器因此只做一件事：把结果设为 {@code null}
     * （该事件的 setter 不算取消 —— 结果可能被重新计算）。
     * <p>本项只算"尽力而为"，不构成硬保护；真正的硬保护需要
     * ① 在热键栏物品上禁止它作为合成材料（配方层面，本工程无自定义配方，不适用），或
     * ② 监听 {@code CraftItemEvent} 并取消（该事件可取消，但本类未做，列入未覆盖项）。
     * <p><b>为何仍然留下这个方法</b>：它把"结果置空"这一手做了（在能被拦截的时机里减少误产出），
     * 且不谎称已经拦住，这比留白更可审计。
     */
    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (isProtected(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
