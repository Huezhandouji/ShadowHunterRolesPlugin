package com.shadowHunterRolesPlugin.core.hotbar;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 「这个组件能出现在热键栏」的能力接口（阶段 6 立、阶段 7 · A 步改全拼）：
 * **唯一实现点是 {@link #specification()}**，
 * 其余访问器（{@link HotbarItem} 的 7 个表现 getter + 冷却/能量两个能力 getter）都由本接口的
 * `default` 方法委托给它 ⇒ 新组件**只写 specification()**，不写任何委托。
 * <p>
 * 与继承的关系（阶段 6 冻结）：新组件只需 `extends RoleComponent` + 按需实现能力接口
 * （本接口 / {@link CooldownBearing} / {@link EnergyCosting} / `HotbarActionable` / `CombatHook`），
 * **不必**继承 `Skill` / `MainWeapon` / `PassiveSkill`。
 * <p>
 * 层次说明：本接口**继承** {@link HotbarItem}（渲染器读写面，本批保留）与两个能力接口 ——
 * 能出现在热键栏的组件天然带有"冷却 / 能量成本"两个字段（今天即如此，值可为 0）；
 * 反过来，两个能力接口仍可**单独**实现（用于"有冷却但不上热键栏"的新式组件）。
 * 这样继承树内不会出现"抽象 + 默认值来自互不相关的接口"的冲突，调用方也不必手写委托。
 * <p>
 * 注意：本接口提供的只是**表现**数据；行为分支（冷却表 / 闸门 / PDC 键 / 文案表）一律由**注册处**的
 * kind 决定（见 {@code Role#componentKindOf(String)}）——组件自述 kind 不参与任何行为分支。
 */
public interface HotbarPresentable extends HotbarItem, CooldownBearing, EnergyCosting {

    /** **唯一实现点**：表现规格（阶段 7 · A 步改全拼；旧短名 `spec()` 保留为 `@Deprecated` 别名）。 */
    HotbarSpecification<?> specification();

    /**
     * 旧短名的兼容别名（**只增不改**）：与 {@link #specification()} 是同一个值，**没有**第二套实现。
     *
     * @deprecated 改用 {@link #specification()}（新名 = 全拼）。返回类型放宽到父类型
     *             {@link HotbarSpecification}（旧声明为子类型 {@code HotbarSpec}）——
     *             实例本就是同一个对象，不需要任何转换代码。
     */
    @Deprecated
    default HotbarSpecification<?> spec() {
        return specification();
    }

    default String getId() {
        return specification().getId();
    }

    default Component getDisplayName() {
        return specification().getDisplayName();
    }

    default Component getDescription() {
        return specification().getDescription();
    }

    default Material getIcon() {
        return specification().getIcon();
    }

    default int getCooldownTicks() {
        return specification().getCooldownTicks();
    }

    default int getEnergyCost() {
        return specification().getEnergyCost();
    }

    default ItemKind getKind() {
        return specification().getKind();
    }

    /** 渲染器读写面视图（`HotbarItem` 在本批保留，不删）。 */
    default HotbarItem asHotbarItem() {
        return specification();
    }

    /**
     * **基础物品**（阶段 7 · C 步）：委托给唯一实现点 {@link #specification()} 的同名方法
     * ⇒ 组件只写一处（描述符），渲染器读到的就是它；**状态装饰仍由框架施加**。
     */
    default ItemStack baseItem(String id) {
        return specification().baseItem(id);
    }
}
