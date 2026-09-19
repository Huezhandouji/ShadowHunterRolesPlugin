package com.shadowHunterRolesPlugin.core.hotbar;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 能出现在热键栏里的组件（设计 §4.2）。
 * 命名沿用工程的 JavaBean 风格（设计 §4.3 命名约定：不引入 record 风格访问器）。
 */
public interface HotbarItem {

    String getId();

    Component getDisplayName();

    Component getDescription();

    Material getIcon();

    int getCooldownTicks();

    int getEnergyCost();

    ItemKind getKind();
}
