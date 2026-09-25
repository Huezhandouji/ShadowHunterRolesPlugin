package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.hotbar;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热键栏渲染器：**按本帧计划把物品落位** —— **全仓唯一写点**就在 {@link #render} 里那一次槽位写入。
 *
 * <h2>谁持有它、谁驱动它</h2>
 * 持有者与驱动者都是 {@link com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent}：
 * 本类**不持有任何状态**（脏标记 / 帧入口条件 / 变化基线全在那个组件里），只吃它交来的计划并把
 * 结果（**本帧新基线** + **本帧是否真的改了东西**）交回去。
 *
 * <h2>本帧计划（{@link Plan}）</h2>
 * 计划由渲染组件按**注册序**给出 {@code (槽位, 物品)} 对：
 * <ul>
 * <li><b>写什么</b>由计划定 —— 物品来自组件自己的物品读口，渲染器**一概不判不问**
 * （三态材质 / 文案 / 秒数 / 识别键都在组件的默认画法里）；</li>
 * <li><b>谁上热键栏、写到哪一格</b>也由计划定 ⇒ 本类不需要角色模板，也**不遍历**任何组件表；</li>
 * <li>计划里 {@code item} 为 {@code null} 的条目不写（与"不产出物品者跳过"同义）。</li>
 * </ul>
 * <p>计划里同一槽位**至多出现一次**（装配期已禁止重复栏位）⇒ 落位结果逐格确定。
 *
 * <h2>什么时候写</h2>
 * 只有渲染组件的帧末活动判定"要刷"（**脏标记已置**，或**外观依赖活状态**的占栏位组件**正在冷却**）
 * 时本方法才会被调到 ⇒ <b>空闲 tick 零 {@code setItem}</b>（无脏、无冷却）✓。
 */
public final class HotbarRenderer {

    /**
     * **本帧要落位的一条**：装配条目里的**栏位** + 组件侧产出的**物品**。
     * <p>渲染器据此写入，不需要角色模板、也不需要组件实例本身。
     */
    public record Entry(int slot, ItemStack item) {
    }

    /**
     * **本帧计划供给面**：由渲染组件提供，按**注册序**返回本帧全部占栏位条目。
     * <p>{@code null} 或空计划 ⇒ 本帧不写任何槽位。
     */
    @FunctionalInterface
    public interface Plan {
        List<Entry> entries();
    }

    /**
     * **一次渲染的结果**（渲染组件据此更新自己的状态）。
     * @param baseline 本帧真正写入的内容（**下一帧的比较基线**）；计划为空或无可写项 ⇒ {@code null}
     * @param changed 本帧是否**真的改了东西**（写入前逐槽位与上一帧基线比较的结论；
     *                无可写项 ⇒ {@code false}，与"没写任何槽位"同义）
     */
    public record Result(Map<Integer, ItemStack> baseline, boolean changed) {

        /** 本帧没写任何槽位（计划为空 / 无可写项 / 无玩家）。 */
        public static final Result NOTHING = new Result(null, false);
    }

    private final Plan plan;

    public HotbarRenderer(Plan plan) {
        this.plan = plan;
    }

    /** 写入前判定：该槽位的新内容与上一帧基线是否不同。 */
    private static boolean differsFromLast(Map<Integer, ItemStack> lastRendered, int slot, ItemStack incoming) {
        if (lastRendered == null) {
            return true; //首次渲染 ⇒ 视作有变化（首刷应被通知）
        }
        ItemStack before = lastRendered.get(slot);
        if (before == null) {
            return true; //该槽位上一帧没有东西 ⇒ 现在有了
        }
        return !before.equals(incoming); //按"类型 + 数量 + meta"比较 ⇒ 对本用途已充足
    }

    /**
     * **纯变化判据（判据骨架）**：**不含任何 Bukkit 类型** ⇒ 可**离线**单测。
     * <p>它只回答**判据的决策形状**，把"两个内容是否相等"留给调用方（{@code incomingDifferent}）
     * —— 因为"相等"必须用项目既有的 L2 值语义（{@link ItemStack#equals}）判定，那属**运行期**，
     * 不该为了好测而在生产代码里塞一个假的等于号。
     * <p>语义（逐条，与 {@link #differsFromLast} **逐字同构**）：
     * <ul>
     * <li>{@code previous == null}（**尚无基线**，首次渲染）⇒ **true** ⇒ 首刷应被通知</li>
     * <li>该槽位上一帧**不在基线里** ⇒ **true** ⇒ 现在有了</li>
     * <li>否则 ⇒ 由 {@code incomingDifferent} 回答</li>
     * </ul>
     * @param previous 上一帧基线（{@code null} = 首帧）
     * @param slot 槽位下标
     * @param incomingDifferent "本槽位新内容与上一帧不同吗"（由调用方按 L2 值语义回答）
     */
    public static boolean contentDiffers(Map<Integer, ?> previous, int slot, boolean incomingDifferent) {
        if (previous == null) {
            return true;
        }
        if (!previous.containsKey(slot)) {
            return true;
        }
        return incomingDifferent;
    }

    /**
     * **帧末 flush 的写物品段**（全仓唯一渲染写点）：把本帧计划逐条落位，并与
     * {@code lastRendered}（上一帧真正写入的内容）比较，回答"**本帧是否真的改了东西**"。
     * <p>本方法**不判断状态、不拼文案、不写识别键、不读任何表现 getter** —— 那些都是组件画法的一部分。
     * <p>遍历顺序 = 计划给出的顺序；写入次数 = 可写条目数（每条恰一次）。
     * @param player 写入目标；{@code null} ⇒ 不写（离线无玩家时返回 {@link Result#NOTHING}）
     * @param lastRendered 上一帧基线（{@code null} = 首帧 ⇒ 视作有变化）
     * @return 本帧新基线 + 变化结论（见 {@link Result}）
     */
    public Result render(Player player, Map<Integer, ItemStack> lastRendered) {
        List<Entry> entries = (plan == null) ? null : plan.entries();
        if (entries == null || entries.isEmpty() || player == null) {
            return Result.NOTHING;
        }

        Inventory inv = player.getInventory();
        Map<Integer, ItemStack> rendered = new HashMap<>();
        for (Entry entry : entries) {
            if (entry == null || entry.item() == null) continue;
            rendered.put(entry.slot(), entry.item());
        }
        if (rendered.isEmpty()) {
            return Result.NOTHING;
        }

        boolean changed = false;
        for (Map.Entry<Integer, ItemStack> written : rendered.entrySet()) {
            if (differsFromLast(lastRendered, written.getKey(), written.getValue())) {
                changed = true;
            }
            inv.setItem(written.getKey(), written.getValue());
        }
        return new Result(rendered, changed);
    }
}
