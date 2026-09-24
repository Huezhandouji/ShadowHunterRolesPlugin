package com.shadowHunterRolesPlugin.core.hotbar;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;

/**
 * 热键栏物品的**声明面**（设计 §4.2）：图标 / 显示名 / 描述 / 冷却声明值 / 耗能声明值。
 * 命名沿用工程的 JavaBean 风格（设计 §4.3 命名约定：不引入 record 风格访问器）。
 * <p><b>阶段 8</b>：本接口**只陈述声明数据**（由描述符提供），**不再**自述种类
 * （旧的 {@code getKind()} 已随 kind 枚举一起删除 —— 表现面与行为分支都不再需要它）；
 * "物品长什么样（含运行期状态）"改由 {@link HotbarRenderComponent.HotbarItemProviding#buildItem()} 回答，
 * 默认画法在 `core/Skill` / `core/MainWeapon` 两个**组件基类**里。
 */
public interface HotbarItem {

    String getId();

    Component getDisplayName();

    Component getDescription();

    Material getIcon();

    int getCooldownTicks();

    int getEnergyCost();

    /**
     * **基础物品**（阶段 7 · C 步）：由描述符给出的热键栏物品**底稿**（材质 / 显示名 / 描述）。
     * <p>默认实现在 {@link HotbarSpecification#baseItem(String)}（由
     * {@link #getIcon()} / {@link #getDisplayName()} / {@link #getDescription()} 生成）；
     * 需要特殊底稿的组件**在自己的 `Specification` 里覆写**即可。
     * <p><b>阶段 8 起</b>：它不再是"框架施加装饰的输入"，而是**基类默认画法的输入**
     * （{@code Skill#buildItem()} / {@code MainWeapon#buildItem()} 读它取材质 / 名称 / 描述）。
     *
     * @param id 注册处的组件 id（覆写者可用于区分同类的不同实例；默认实现不读它）
     */
    ItemStack baseItem(String id);
}
