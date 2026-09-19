package com.shadowHunterRolesPlugin.core.hotbar;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 热键栏**表现规格**（阶段 6 · 统一装配的数据对象）：把过去分散在基类里的 7 个表现字段收敛成一个不可变值对象。
 * <p>
 * 分工（阶段 6 冻结）：
 * <ul>
 *   <li>{@link HotbarPresentable#spec()} = **唯一实现点** —— 组件只写这一处；</li>
 *   <li>{@code HotbarSpec} 自身即 {@link HotbarItem} 的读写面（渲染器形参类型在本批保留），
 *       因此 {@link HotbarPresentable#asHotbarItem()} 直接返回本对象，无需适配代码；</li>
 *   <li>本对象里的 {@code kind} **只作表现用途**；行为分支（冷却表 / 闸门 / PDC / 文案）一律读
 *       **注册处**给出的 kind（{@code Role#componentKindOf(String)}）。</li>
 * </ul>
 * 命名沿用工程的 JavaBean 风格（设计 §4.3：不引入 record 风格访问器）。
 */
public final class HotbarSpec implements HotbarItem {

    private final String id;
    private final Component displayName;
    private final Component description;
    private final Material icon;
    private final int cooldownTicks;
    private final int energyCost;
    private final ItemKind kind;

    private HotbarSpec(String id, Component displayName, Component description, Material icon,
                       int cooldownTicks, int energyCost, ItemKind kind) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = energyCost;
        this.kind = kind;
    }

    /** 唯一的构造入口（不可变 ⇒ 组件可在构造期一次建好）。 */
    public static HotbarSpec of(String id, Component displayName, Component description, Material icon,
                                int cooldownTicks, int energyCost, ItemKind kind) {
        return new HotbarSpec(id, displayName, description, icon, cooldownTicks, energyCost, kind);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }

    @Override
    public Component getDescription() {
        return description;
    }

    @Override
    public Material getIcon() {
        return icon;
    }

    @Override
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int getEnergyCost() {
        return energyCost;
    }

    @Override
    public ItemKind getKind() {
        return kind;
    }

    /**
     * 内部委托访问器：供 {@link HotbarPresentable} 的 default 方法读取本对象的自述种类，
     * 使 hotbar 包内不出现「读自述 kind」的调用点（阶段 6 判据 C-04 的口径：行为分支只认注册 kind）。
     */
    public ItemKind kind() {
        return kind;
    }
}
