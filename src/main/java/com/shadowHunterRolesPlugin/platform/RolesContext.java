package com.shadowHunterRolesPlugin.platform;

import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 平台上下文：领域层与组件层需要的全部平台能力的唯一入口（替代插件主类单例）。
 * 由主类在 {@code onEnable} 构造并注入到 RoleManager → RoleInstance → 组件。
 *
 * <p><b>不含阵营</b>：阵营的唯一权威是 {@link FactionManager} 的注册表（纯静态、由组件自己注册与
 * 注销）⇒ 本上下文不需要携带任何阵营查询通道，也就没有"角色管理器 ↔ 平台口 ↔ 角色管理器"的环。
 */
public record RolesContext(Plugin plugin, Logger logger, Scheduler scheduler,
                           KeyFactory keys) {
}
