package com.shadowHunterRolesPlugin.core.hotbar;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 能出现在热键栏里的组件（设计 §4.2）。
 * 命名沿用工程的 JavaBean 风格（设计 §4.3 命名约定：不引入 record 风格访问器）。
 * <p><b>阶段 7 · C 步</b>：本接口是渲染器的**读写面**，除七个表现 getter 之外，还要求提供
 * {@link #baseItem(String)} —— 热键栏物品的**基础形态**（材质 / 显示名 / 描述）。框架在它之上
 * 套**状态装饰**（三态材质覆盖 · 名称颜色与加粗 · 冷却秒数或 `DISABLED`/`ENERGY LACK` 后缀 ·
 * 状态行 lore · 分隔线 · 两个 PDC 键）⇒ 组件只决定"物品长什么样"，不决定"处于什么状态"。
 */
public interface HotbarItem {

    String getId();

    Component getDisplayName();

    Component getDescription();

    Material getIcon();

    int getCooldownTicks();

    int getEnergyCost();

    ItemKind getKind();

    /**
     * **基础物品**（阶段 7 · C 步）：由组件（经其描述符）给出的热键栏物品底稿。
     * <p>默认实现在 {@link HotbarSpecification#baseItem(String)}（由
     * {@link #getIcon()} / {@link #getDisplayName()} / {@link #getDescription()} 生成）；
     * 需要特殊渲染逻辑的组件**在自己的 `Specification` 里覆写**即可 ——
     * 覆写只影响基础物品，状态装饰仍由框架施加。
     *
     * @param id 注册处的组件 id（覆写者可用于区分同类的不同实例；默认实现不读它）
     */
    ItemStack baseItem(String id);
}
