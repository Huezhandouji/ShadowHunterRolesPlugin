package com.shadowHunterRolesPlugin.core.hotbar;
import com.shadowHunterRolesPlugin.core.component.ComponentRegistry;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一热键栏渲染器（**唯一渲染者**；**只保留管道职责**）。
 * <p>
 * 契约：
 * <ul>
 * <li><b>什么时候写</b>：只有 {@link #markDirty()} 被调用后（或"有占栏位组件在冷却"，见下）帧末 flush
 * 才会写物品；<b>空闲 tick 零 setItem</b>（无脏、无冷却）；</li>
 * <li><b>写到哪一格</b>：装配条目里的栏位（{@code Role.ComponentEntry#getSlot()}），
 * 按**注册序**遍历组件表 ⇒ 「注册序 = 渲染序」在代码上直接可见；</li>
 * <li><b>写什么</b>：向组件要 —— 渲染组件的读口 {@code buildItemOf(component)}（物品**完全由组件控制**，
 * 三态材质 / 文案 / 秒数 / 识别键都在组件的默认画法里，渲染器一概不判不问）；</li>
 * <li><b>唯一写物品点</b> = {@link #render()} 内的那次槽位写入；</li>
 * <li>组件**不参与**渲染调度：没有 HotbarPort，也没有组件可调用的 markDirty。</li>
 * </ul>
 * <p>
 * <b>归属迁移（对照）</b>：本类原来自己做六步装饰（{@code buildIcon} + {@code applySkill} /
 * {@code applyMainWeapon} + {@code stateOf}）；现在这些**全部搬进组件基类**
 * （`core/Skill#buildItem()` 与 `core/MainWeapon#buildItem()`），本类退化为
 * 「按注册序遍历 → 取 {@code buildItem()} → 唯一写点落位」。由此，八串冻结字面量
 * （六条状态文案 + 秒数格式串 + 分隔线）在本文件内 = **0**（归属判据 C-13）。
 */
public final class HotbarRenderer {

 /** 本渲染器所属容器：栏位与组件实例都由它提供。 */
    private final RoleInstance owner;

 /** 置脏标志。初值 = true ⇒ 即便无人置脏，首位 flush 也会完成首刷（构造器另有一次同步首刷）。 */
    private boolean dirty = true;

 /**
 * **本帧是否真的改了东西**（ · B3 的变化判据；由 {@link #render()} 置位、
 * 由 {@link #consumeChanged()} 读取并清除）。
 */
    private boolean changed;

 /**
 * **上一帧真正写入的槽位内容**（B3 的**变化基线**）。
 * <p>键 = 槽位下标，值 = 该槽位当时写入的 {@link ItemStack}。用来回答
 * "本次 {@link #render()} 与上一次相比**是否真的改了东西**" —— 这是 B3 要求的
 * "**无变化的那次渲染不得回调**" 的**唯一**依据。
 * <p><b>为什么不能拿 {@code dirty} 当判据</b>：脏标记只表示"**有人请求过重绘**"，
 * 而请求之后重建出来的物品**可能逐字相同**（例：冷却开始置脏，但该槽位这一帧的外观没变）
 * ⇒ 用 {@code dirty} 判"有变化"会**多报**回调。
 * <p>{@code null} = 尚无基线（首次渲染）⇒ 视作"有变化"（首刷应当被通知）。
 */
    private Map<Integer, ItemStack> lastRendered;

    public HotbarRenderer(RoleInstance owner) {
        this.owner = owner;
    }

 /**
 * **本帧是否发生了真实变化**（B3 的取值点）：读取**并清除**（一次性，"读过即消费"）。
 * <p>清除语义很重要：若不清除，下一帧即使什么都没变，也会沿用上一帧的 {@code true} 而**多报**。
 */
    public boolean consumeChanged() {
        boolean value = this.changed;
        this.changed = false;
        return value;
    }

 /** 写入前判定：该槽位的新内容与上一帧是否不同。 */
    private boolean differsFromLast(int slot, ItemStack incoming) {
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
 * **变化判据的纯函数（判据骨架）**（ · B3）：**不含任何 Bukkit 类型** ⇒ 可**离线**单测
 * （它是"基线 + 新增"用例的被测对象）。
 * <p>它只回答**判据的决策形状**，把"两个内容是否相等"留给调用方（`incomingDifferent`）——
 * 因为"相等"必须用项目既有的 L2 值语义（{@link ItemStack#equals}）判定，那属**运行期**，
 * 不该为了好测而在生产代码里塞一个假的等于号。
 * <p>语义（逐条，与 {@link #differsFromLast} **逐字同构**）：
 * <ul>
 * <li>{@code previous == null}（**尚无基线**，首次渲染）⇒ **true** ⇒ 首刷应被通知 </li>
 * <li>该槽位上一帧**不在基线里** ⇒ **true** ⇒ 现在有了 </li>
 * <li>否则 ⇒ 由 {@code incomingDifferent} 回答 </li>
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
 * 置脏。由框架内部调用（施放管道 / 冷却启动 / 冷却到点 / 能量单一入口 / buff 移除）；组件侧没有这条通道。
 */
    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

 /** 帧末 flush 完成后清脏。**唯一消费者 = flush 尾部**；端口 / 组件 / 渲染器内部都不得调用（会吞掉本 tick 的可见更新）。 */
    public void clearDirty() {
        this.dirty = false;
    }

 /**
 * 帧末 flush 的**写物品段**（全仓唯一渲染写点）：**按注册序遍历组件表**，遇带栏位者落位，
 * 物品由渲染组件的读口 {@code buildItemOf(component)} 产出。
 * <p>遍历的是 {@code Role.getComponents()}（`LinkedHashMap` = 装配调用序）：**每个栏位至多被写一次**
 * （装配期已禁止重复栏位）⇒ 落位结果与既有实现逐格相同；「注册序 = 渲染序」因此不依赖槽位表的迭代顺序。
 * <p>只对**已注册**的组件生效（未知 id 跳过）；**不占栏位者跳过**（被动天然走这一支）；
 * **不产出物品者跳过**（读口回 {@code null}；仓内 = 只 extends RoleComponent 且不上热键栏的组件）。
 * <p>本方法**不判断状态、不拼文案、不写识别键、不读任何表现 getter** —— 那些都是组件画法的一部分。
 * <p><b> · B3</b>：本方法**顺带**维护"**本帧是否真的改了东西**"这个判据 ——
 * 写入前逐槽位与{@link #lastRendered 上一帧基线}比较，**任一处不同**就把 {@link #changed} 置位；
 * 写完记录新基线。**唯一写点仍是下面那次槽位写入** （本改动只加"比较 + 记账"）。
 * <p><b>（测量口径注）</b>本 javadoc **不**把那次写入的**完整调用形态**逐字写出来 —— 否则按
 * "槽位写入方法的调用"做 grep 时，会把**本注释行**也算进去、让"恰 3 行"的不变量凭空多 1 行
 * （这正是本工程记过的 **T-AD**：注释会骗计数；本行的措辞即为遵守该口径而改）。
 */
    public void render() {
        Player player = owner.getPlayer();
        if (player == null) return;

        Inventory inv = player.getInventory();
        Map<String, Role.ComponentEntry> components = owner.getRole().getComponents();
        if (components == null || components.isEmpty()) return;

        Map<Integer, ItemStack> rendered = new HashMap<>();
        for (Map.Entry<String, Role.ComponentEntry> entry : components.entrySet()) {
            Role.ComponentEntry component = entry.getValue();
 //不占栏位 ⇒ 不渲染（被动天然走这一支）
            if (!component.hasSlot()) continue;
            RoleComponent instance = owner.componentRegistry().getById(entry.getKey());
 //物品完全由组件控制：拿不到"会画物品"的组件就跳过（不住栏位的组件不会出现在这里）
 //读侧契约 = 渲染组件按组件读数据（组件不再实现能力接口）⇒ 判据与"旧接口实现者集合"逐字相同 ✓
            ItemStack item = HotbarRenderComponent.buildItemOf(instance);
            if (item == null) continue;
            int slot = component.getSlot();
 // · B3：写入**之前**判"与本帧基线是否不同"（比较不改变写入行为）
            if (differsFromLast(slot, item)) {
                this.changed = true;
            }
            inv.setItem(slot, item);
            rendered.put(slot, item);
        }
 //本帧基线替换为刚写入的内容（与 lastRendered 的 null 语义配合 ⇒ 首帧必然"有变化"）
        this.lastRendered = rendered;
    }
}
