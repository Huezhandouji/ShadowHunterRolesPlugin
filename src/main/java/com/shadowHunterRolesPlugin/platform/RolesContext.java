package com.shadowHunterRolesPlugin.platform;

import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 平台上下文：领域层与组件层需要的全部平台能力的唯一入口（替代插件主类单例）。
 * 由主类在 {@code onEnable} 构造并注入到 RoleManager → RoleInstance → 组件。
 */
public record RolesContext(Plugin plugin, Logger logger, Scheduler scheduler,
                           KeyFactory keys, FactionLookup factions) {
}
