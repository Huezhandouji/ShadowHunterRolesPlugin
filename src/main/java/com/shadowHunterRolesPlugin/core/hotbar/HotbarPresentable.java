package com.shadowHunterRolesPlugin.core.hotbar;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 「这个组件能出现在热键栏」的能力簇（阶段 6 立、阶段 7 · A 步改全拼、**阶段 8 收簇**）：
 * **唯一实现点是 {@link #specification()}**（声明数据），
 * 其余访问器都由本接口的 `default` 方法委托给它 ⇒ 新组件**只写 specification()**，不写任何委托。
 * <p>
 * <b>阶段 8 · 能力簇（用户裁定 C-14：互相强依赖的能力应合并）</b>：本接口把
 * {@link HotbarItem}（声明面）· {@link CooldownBearing}（冷却状态）· {@link EnergyCosting}（耗能声明）
 * 与 {@link HotbarItemProviding}（自己画物品）**四合一**，理由 = 两条合并判据同时成立：
 * <ul>
 *   <li><b>① 实现者集合相同（按构造）</b>：实现本接口者**必然**要实现 {@code buildItem()}；
 *       反过来，仓内唯一的 {@code buildItem()} 默认实现就在本簇的实现者链上
 *       （{@code roleComponent/ActiveComponent} ⇒ {@code core/Skill} / {@code core/MainWeapon}）；</li>
 *   <li><b>② 一方方法语义必须读另一方的状态</b>：{@code buildItem()} 要读冷却状态
 *       （{@link CooldownBearing#isCooling()} / 剩余刻）、闸门与能量（`svc()` 端口），
 *       并读声明面（{@link HotbarItem} 的图标 / 显示名 / 描述 / 耗能）—— 语义上离不开。</li>
 * </ul>
 * <b>不占热键栏的组件（被动）不在本簇内**（{@code PassiveSkill} 只继承 {@code RoleComponent}）：它们
 * 既不被渲染，也就**不会**被强制实现一个永远不被调用的 {@code buildItem()}（避免"能被读却没人读"的能力）。
 * <p>
 * 与继承的关系（阶段 6 冻结 + 阶段 8 不变）：新组件只需 `extends RoleComponent` + 按需实现能力接口，
 * **不必**继承 `Skill` / `MainWeapon` / `PassiveSkill`；只是"默认画法"这一份便利实现长在那两个基类上。
 * <p>
 * 注意：本接口提供的只是**声明**数据；行为分支（冷却表 / 闸门 / 识别键 / 文案表）一律由组件自己的
 * {@code buildItem()} 与框架管道决定 —— 阶段 8 起仓内**没有** kind 这个运行期概念。
 */
public interface HotbarPresentable extends HotbarItem, CooldownBearing, EnergyCosting, HotbarItemProviding {

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

    /** 声明面视图（`HotbarItem` 在本批保留，不删）。 */
    default HotbarItem asHotbarItem() {
        return specification();
    }

    /**
     * **基础物品**（阶段 7 · C 步）：委托给唯一实现点 {@link #specification()} 的同名方法
     * ⇒ 组件只写一处（描述符），基类默认画法读到的就是它。
     */
    default ItemStack baseItem(String id) {
        return specification().baseItem(id);
    }
}
