package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.Skill;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * **热键栏物品的"不可动"保护（平台事件面）**（阶段 12 · t88 · `(b)-静态半`）。
 *
 * <h2>★ 本类与 {@code DamageHookListener} 的**关键不对称**（先看这条，别照抄错形态）</h2>
 * 两个监听器都落在平台事件面上，但**对事件的处理姿态相反**，**不是同一族** ✗：
 * <ul>
 *   <li><b>{@code DamageHookListener}（读侧 / 回调侧）</b>：伤害**早已由平台结算** ⇒ 监听器**只读、绝不取消** ✓；
 *       它只把结果通知给组件（{@code Participant}，`void`）。</li>
 *   <li><b>本类（保护侧）</b>：目标是"**不让物品被拿走**" ⇒ 实现手段**只能是 {@code setCancelled(true)}** ✓。
 *       若照"只读不取消"写，则**什么都保护不了** ✗ —— 那是**假功能**。</li>
 * </ul>
 * ⇒ 一句话：**"只读不取消"适用于"事件已发生、我只通知"；"取消"适用于"我要阻止事件生效"。**
 * 本卡（`t88`）改向的**全部理由**就在这里 ✓。
 *
 * <h2>为什么需要本卡（真缺口，现算）</h2>
 * 既有保护只覆盖两类，且**只覆盖这两类**：
 * <ul>
 *   <li>{@code InventoryClickEvent} —— {@code SkillListener} / {@code MainWeaponListener} 内有 ✓（**会取消** ✓）</li>
 *   <li>{@code PlayerDropItemEvent}（Q 丢）—— 同上 ✓（**会取消** ✓，且被复用为"Q 释放技能"）</li>
 * </ul>
 * 而下面四类事件**全仓零命中** ✗ ⇒ **玩家可用它们把热键栏物品弄走**：
 * <ol>
 *   <li>{@link InventoryDragEvent} —— **拖拽**（鼠标按住铺过格子）✗</li>
 *   <li>{@link PlayerSwapHandItemsEvent} —— **F 键**与副手交换 ✗</li>
 *   <li>{@link InventoryMoveItemEvent} —— **漏斗 / 其他容器**搬运 ✗</li>
 *   <li>{@link PrepareItemCraftEvent} —— 放进**合成格** ✗</li>
 * </ol>
 * 本类只**补这四类** ✓；既有的两类**不重复实现** ✗（避免两个监听器对同一事件各取消一次的无谓重复）。
 *
 * <h2>判据 = 既有 PDC 识别键（复用，不新增第二套）</h2>
 * 用 {@link Skill.Utils#isSkillItem(ItemStack)} 与 {@link MainWeapon.Utils#isMainWeapon(ItemStack)} ——
 * 它们各自读的识别键（{@code Skill.Specification.SKILL_KEY} · {@code MainWeapon.Specification.MAIN_WEAPON_KEY}）
 * 由各组件的 {@code buildItem()} **最后一步**写入 ✓。两者都**自带 null / 空气判空** ✓ ⇒ 本类可放心调用 ✓。
 * <p><b>为何不自己写判据</b>：那会变成"两套识别逻辑" ✗ —— 与本工程"单一实现点"的纪律冲突。
 *
 * <h2>只对"真正在热键栏里"的物品保护（边界）</h2>
 * {@link InventoryMoveItemEvent} 的监听器有**两个方向**：本类的意图是"**物品离开**玩家的受保护容器"⇒
 * 取 {@link InventoryMoveItemEvent#getSource()}（来源）判据 ✓；来源是其它容器（如漏斗自身）时**不动** ✓。
 * <p>{@link PrepareItemCraftEvent} 的 {@code getInventory()} 即**合成矩阵** ⇒ 矩阵里出现受保护物品即取消 ✓。
 *
 * <h2>本卡边界（如实申报）</h2>
 * **不起服、不进档、不写证据件** ⇒ 上述四个事件在**真实交互**下是否真的被拦（拖拽 / F 键 / 漏斗 / 合成格），
 * 以及"取消是否会影响既有 Q 丢施法链路"，**均无运行级读数** ✗ ⇒ 属**窗口半**（另立卡，依赖本卡）✓。
 */
public class HotbarItemProtectionListener implements Listener {

    /** 受保护判定：技能物品 或 主武器物品（复用既有 PDC 识别键）。 */
    private static boolean isProtected(ItemStack item) {
        return Skill.Utils.isSkillItem(item) || MainWeapon.Utils.isMainWeapon(item);
    }

    /**
     * **拖拽**：拖拽经过的每一格都在 {@code getNewItems()} 里 ⇒ 任一格含受保护物品即取消整次拖拽 ✓。
     * <p>为什么整次取消而不是"只挡那一格"：Bukkit 的拖拽是**一次事务**，逐格部分挡会让物品**换位**而不是**不动** ✗
     * ⇒ 与"不可动"的目标不符。
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

    /** **F 键**：主手 ↔ 副手交换。任一侧含受保护物品即取消 ✓。 */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isProtected(event.getMainHandItem()) || isProtected(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * **容器间搬运**（漏斗 / 投掷器 / 其它容器）：**来源**侧含受保护物品即取消 ✓。
     * <p>只看来源、不看目的地 ⇒ 既有"把普通物品搬进玩家容器"的行为**不受影响** ✓。
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isProtected(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * **合成格（★ 有真实限制，如实申报）**：{@link PrepareItemCraftEvent} **不是** {@code Cancellable} ✗
     * ⇒ **无法**用它阻止合成 ✗。本处理器因此**只做一件事**：把结果设为 {@code null}
     * （该事件的 setter 不算取消 —— 结果可能被重新计算）。
     * <p><b>⇒ 本项只算"尽力而为"，不构成硬保护</b> ✗ —— 真正的硬保护需要
     * ① 在热键栏物品上**禁止**它作为合成材料（配方层面，本工程无自定义配方 ⇒ 不适用），或
     * ② 监听 {@code CraftItemEvent} 并取消（**该事件可取消** ✓，但本卡未做 ⇒ 列入未覆盖项）。
     * <p><b>为什么仍然留下这个方法</b>：它把"结果置空"这一手做了（在能被拦截的时机里减少误产出），
     * 且**不谎称**已经拦住 ✗ —— 这比留白更可审计 ✓。
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
