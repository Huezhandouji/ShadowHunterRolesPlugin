package com.shadowHunterRolesPlugin.core.hotbar;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * 统一热键栏渲染器（**阶段 5 · 4.4 起为唯一渲染者**；**阶段 8 起只保留管道职责**）。
 * <p>
 * 契约（指南 §3.5 / §3.5.1，阶段 8 修订）：
 * <ul>
 *   <li><b>什么时候写</b>：只有 {@link #markDirty()} 被调用后（或"有占栏位组件在冷却"，见下）帧末 flush
 *       才会写物品；<b>空闲 tick 零 setItem</b>（无脏、无冷却）；</li>
 *   <li><b>写到哪一格</b>：装配条目里的栏位（{@code Role.ComponentEntry#getSlot()}），
 *       按**注册序**遍历组件表 ⇒ 「注册序 = 渲染序」在代码上直接可见；</li>
 *   <li><b>写什么</b>：向组件要 —— {@code HotbarItemProviding#buildItem()}（阶段 8：物品**完全由组件控制**，
 *       三态材质 / 文案 / 秒数 / 识别键都在组件的默认画法里，渲染器一概不判不问）；</li>
 *   <li><b>唯一写物品点</b> = {@link #render()} 内的那次槽位写入；</li>
 *   <li>组件**不参与**渲染调度：没有 HotbarPort，也没有组件可调用的 markDirty。</li>
 * </ul>
 * <p>
 * <b>阶段 8 · 归属迁移（对照）</b>：本类原来自己做六步装饰（{@code buildIcon} + {@code applySkill} /
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

    public HotbarRenderer(RoleInstance owner) {
        this.owner = owner;
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
     * 物品由组件自己的 {@link HotbarItemProviding#buildItem()} 产出。
     * <p>遍历的是 {@code Role.getComponents()}（`LinkedHashMap` = 装配调用序）：**每个栏位至多被写一次**
     * （装配期已禁止重复栏位）⇒ 落位结果与旧实现逐格相同；「注册序 = 渲染序」因此不依赖槽位表的迭代顺序。
     * <p>只对**已注册**的组件生效（未知 id 跳过）；**不占栏位者跳过**（被动天然走这一支）；
     * **不实现 {@link HotbarItemProviding} 者跳过**（仓内 = 只 extends RoleComponent 且不上热键栏的组件）。
     * <p>本方法**不判断状态、不拼文案、不写识别键、不读任何表现 getter** —— 那些都是组件画法的一部分。
     */
    public void render() {
        Player player = owner.getPlayer();
        if (player == null) return;

        Inventory inv = player.getInventory();
        Map<String, Role.ComponentEntry> components = owner.getRole().getComponents();
        if (components == null || components.isEmpty()) return;

        for (Map.Entry<String, Role.ComponentEntry> entry : components.entrySet()) {
            Role.ComponentEntry component = entry.getValue();
            //不占栏位 ⇒ 不渲染（被动天然走这一支）
            if (!component.hasSlot()) continue;
            RoleComponent instance = owner.componentRegistry().getById(entry.getKey());
            //物品完全由组件控制：拿不到"会画物品"的组件就跳过（不住栏位的组件不会出现在这里）
            if (!(instance instanceof HotbarItemProviding providing)) continue;
            inv.setItem(component.getSlot(), providing.buildItem());
        }
    }
}
