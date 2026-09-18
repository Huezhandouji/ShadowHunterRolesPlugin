package com.shadowHunterRolesPlugin.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 子指令接口：主指令 {@link RoleCommand} 把第一级参数路由到对应的子指令实现。
 * <p>
 * 风格逐条对齐下游同工作区工程 {@code SHDFGamePlugin}
 * （{@code com.sHDFGamePlugin.command.SubCommand}）：**接口 + 一个子指令一个类 + 主分发器只做路由/统一报错/补全**。
 * 本工程的既有顶层命令 {@code /role} **不迁移到 Brigadier**（见 `docs/插件文档/开发指南-新增角色或组件.md` §4.5.1），
 * 仍走 `plugin.yml` + `setExecutor`，只是把中央 `switch` 的分支改为子指令类。
 * <p>
 * 约定：
 * <ul>
 *     <li>{@link #getName()}：子指令名（{@code /role} 后的第一个参数，不区分大小写）；</li>
 *     <li>{@link #execute(CommandSender, String[])}：收到的 {@code args} 中**子指令名已被剥离**；</li>
 *     <li>{@link #onTabComplete(CommandSender, String[])}：同一口径（子指令名已剥离），默认不补全；</li>
 *     <li>返回值沿用既有语义：**任何已命中的分支都返回 {@code true}**（"已处理"）。</li>
 * </ul>
 */
public interface SubCommand {

    /** 子指令名称（主指令的第一个参数，不区分大小写） */
    String getName();

    /** 子指令用法说明（不含主指令前缀，用于帮助信息与自检） */
    String getUsage();

    /** 执行子指令；返回 true 表示已处理 */
    boolean execute(CommandSender sender, String[] args);

    /** 子指令的 Tab 补全（args 为剥离子指令名后的剩余参数） */
    default List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    /**
     * 前缀过滤（不区分大小写）—— 各子指令的补全共用，避免每个类各抄一份。
     * <p>
     * 与 {@code SHDFGamePlugin} 各命令类里的同名私有工具**逐字同语义**（大小写均以 {@link Locale#ROOT} 归一下界）。
     */
    static List<String> filter(Collection<String> candidates, String prefix) {
        List<String> result = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
