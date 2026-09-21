package com.shadowHunterRolesPlugin.core.hotbar;

import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一热键栏渲染器（**阶段 5 · 4.4 起为唯一渲染者**）。
 * <p>
 * 契约（指南 §3.5 / §3.5.1）：
 * <ul>
 *   <li><b>状态判定顺序固定</b>：冷却 → 禁用 → 能量不足 → 就绪（{@link #stateOf}）；</li>
 *   <li><b>空闲 tick 零 setItem</b>：只有 {@link #markDirty()} 被调用后，帧末 flush 才会写物品
 *       （冷却中的技能例外 —— 秒数文本必须每 tick 刷新一次，由 flush 的入口条件承担）；</li>
 *   <li><b>刷新触发</b>：施放 / 冷却启动 / 冷却到点 / 能量变化 / buff 移除五类事件都必须置脏；</li>
 *   <li><b>唯一写物品点</b> = {@link #render()} 内的那次槽位写入；</li>
 *   <li>组件**不参与**渲染：没有 HotbarPort，也没有组件可调用的 markDirty。</li>
 * </ul>
 * <p>
 * <b>4.4 接管说明</b>：旧的基类渲染方法（`Skill` / `MainWeapon` 各自的物品构建与带参名称）已随本批删除；
 * 材质 / 名称（含颜色与装饰）/ 后缀 / lore 行序 / PDC 键均**逐字沿用**其实现
 * （逐维对照见 `debug-logs/测试记录/阶段5-需求与验收.md` §5 的 S1–S10 来源表与两张状态表）。
 * 由此消失的旧分支只有两条**不可达路径**：`instance == null` 的短路与其文案（渲染器只对已注册组件生效）。
 */
public final class HotbarRenderer {

    /** 本渲染器所属容器：槽位表、就绪态、能量与 buff 状态都由它提供。 */
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
     * 按 {@link #stateOf} 的四段顺序重绘。
     * <p><b>阶段 7 · B 步</b>：栏位随组件自己的描述符走 ⇒ 本渲染器不再读 `Role.getSlotMap()`（那张表
     * 已降级为派生视图），而是遍历 {@code Role.getComponents()}（`LinkedHashMap` = 装配调用序）
     * 并读条目里的栏位值。**每个栏位至多被写一次**（装配期已禁止重复栏位）⇒ 落位结果与旧实现逐格相同；
     * 由此「注册序 = 渲染序」在代码上直接可见（不再依赖槽位表的迭代顺序）。
     * <p>只对**已注册**的组件生效（未知 id 跳过）。**kind 一律取注册处的权威值**（条目里的 kind）
     * ——组件自述 kind 不参与任何行为分支。
     * <p>第三分支（{@code PASSIVE}）：被动**不占热键栏、不参与渲染**；即使被塞进栏位表也在此显式跳过。
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
            ItemKind kind = component.getKind();
            if (kind == null || kind == ItemKind.PASSIVE) continue;
            String id = entry.getKey();
            HotbarItem item = owner.hotbarItemOf(id);
            if (item == null) continue;
            inv.setItem(component.getSlot(), buildIcon(id, item, kind));
        }
    }

    /**
     * 按四段状态构建单个热键栏物品（材质 / 名称 / 后缀 / lore / PDC 与旧基类实现逐字一致）。
     * <p>{@code id} = **注册处的组件 id**（阶段 7 · B 步起显式传入：表现描述符由组件自带，
     * 它的 id 字段不再由构造实参提供，因此权威 id 只能取自注册表——本参数就是它）。
     * <p>{@code kind} = **注册处的权威 kind**（形参传入，不从组件自述读）。
     * <p><b>阶段 7 · C 步 · 取值路径</b>：**基础物品由组件/描述符提供**（{@link HotbarItem#baseItem(String)}），
     * 框架只在其上套**状态装饰** —— 材质按状态覆盖（就绪态沿用基础物品的材质）、名称套颜色/加粗/后缀、
     * 状态行 lore、分隔线、两个 PDC 键。**三态判定 / 六条文案 / x.xs 差异 / PDC / 帧末 flush 全部留在本方法**，
     * 组件覆写 {@code baseItem} 影响不到它们。
     * <p>技能与主武器的差异是**冻结差异**：技能冷却名带 `" x.xs"` 秒数，主武器冷却名**不带**任何追加段；
     * 主武器 `energyCost ≡ 0`（且能量被 clamp 到 ≥ 0）⇒ 永不进入 {@link IconState#ENERGY_LACK}。
     */
    public ItemStack buildIcon(String id, HotbarItem item, ItemKind kind) {
        boolean skill = kind == ItemKind.SKILL;
        boolean ready = skill ? owner.isSkillReady(id) : owner.isMainWeaponReady(id);
        boolean canCast = skill ? owner.getBuffManager().canCastSkill() : owner.getBuffManager().canUseMainWeapon();
        IconState state = stateOf(ready, canCast, owner.getCurrentEnergy(), item.getEnergyCost());

        //① 基础物品：**向组件（经其描述符）取**（阶段 7 · C 步）—— 材质/显示名/描述都由它给
        ItemStack base = item.baseItem(id);
        ItemMeta baseMeta = base.getItemMeta();
        Material baseMaterial = base.getType();
        Component baseName = baseMeta != null && baseMeta.displayName() != null
                ? baseMeta.displayName() : item.getDisplayName();
        List<Component> baseLore = baseMeta != null && baseMeta.lore() != null && !baseMeta.lore().isEmpty()
                ? baseMeta.lore() : List.of(item.getDescription());

        //② 状态装饰（框架侧 · 冻结面）：材质由状态决定，就绪态沿用基础物品的材质
        Material material;
        if (state == IconState.READY) {
            material = baseMaterial;
        } else if (state == IconState.DISABLED) {
            material = Material.BARRIER;
        } else {
            //COOLDOWN 与 ENERGY_LACK 共用 STRUCTURE_VOID（旧实现即如此）
            material = Material.STRUCTURE_VOID;
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        List<Component> lore = new ArrayList<>();
        if (skill) {
            applySkill(meta, lore, id, baseName, state);
        } else {
            applyMainWeapon(meta, lore, baseName, state);
        }

        //公共两行 lore 对**所有**状态与**两种** kind 都追加（含"能量不足态只有这两行"这条非对称）
        lore.add(Component.text("===================="));
        lore.addAll(baseLore);
        meta.lore(lore);

        if (skill) {
            meta.getPersistentDataContainer().set(Skill.Utils.SKILL_KEY, PersistentDataType.STRING, id);
        } else {
            meta.getPersistentDataContainer().set(MainWeapon.Utils.MAIN_WEAPON_KEY, PersistentDataType.STRING, id);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 技能侧文案与 lore（**唯一一处**秒数格式串，且只在技能冷却分支）。
     * <p>秒数按 {@code id}（**注册处的组件 id**）查 —— 阶段 7 · B 步起表现描述符由组件自带，
     * 它的 `id` 字段不再由构造实参提供，因此**不得**再用 {@code item.getId()} 查冷却表
     * （那是本批第一轮实测到的真实回归：查到 `null` ⇒ 冷却名恒显示 `0.0s`）。
     */
    private void applySkill(ItemMeta meta, List<Component> lore, String id, Component baseName, IconState state) {
        switch (state) {
            case COOLDOWN -> {
                meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                        .append(Component.text(" " + String.format("%.1f", owner.getRemainingSkillCooldownSeconds(id)) + "s")
                                .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)));
                lore.add(Component.text("Skill is on cooldown."));
            }
            case DISABLED -> {
                meta.displayName(baseName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .append(Component.text(" DISABLED")).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
                lore.add(Component.text("Skill has been disabled."));
            }
            case ENERGY_LACK -> meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    .append(Component.text(" ENERGY LACK")).color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
            case READY -> {
                meta.displayName(baseName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                lore.add(Component.text("Skill is ready."));
            }
        }
    }

    /** 主武器侧文案与 lore（**无秒数后缀**：冻结差异，不得"顺手统一"成技能形态）。 */
    private void applyMainWeapon(ItemMeta meta, List<Component> lore, Component baseName, IconState state) {
        switch (state) {
            case COOLDOWN -> {
                meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
                lore.add(Component.text("MainWeapon is on cooldown."));
            }
            case DISABLED -> {
                meta.displayName(baseName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .append(Component.text(" DISABLED")).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
                lore.add(Component.text("MainWeapon has been disabled."));
            }
            case READY -> {
                meta.displayName(baseName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                lore.add(Component.text("MainWeapon is ready."));
            }
            case ENERGY_LACK -> {
                //不可达：主武器 energyCost ≡ 0 且能量被 clamp 到 ≥ 0 ⇒ 不为其造新外观
            }
        }
    }

    /**
     * 状态判定（顺序与今天的渲染代码逐字一致）。
     * 主武器的 {@code energyCost} 恒为 {@code 0} ⇒ 永不进入 {@link IconState#ENERGY_LACK}
     * （该分支今天只存在于两个技能覆写里，见设计 §4.3 陷阱①）。
     */
    public static IconState stateOf(boolean ready, boolean canCast, int energy, int energyCost) {
        if (!ready) {
            return IconState.COOLDOWN;
        }
        if (!canCast) {
            return IconState.DISABLED;
        }
        if (energy < energyCost) {
            return IconState.ENERGY_LACK;
        }
        return IconState.READY;
    }
}
