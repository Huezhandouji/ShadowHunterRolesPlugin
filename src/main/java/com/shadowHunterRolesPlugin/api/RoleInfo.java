package com.shadowHunterRolesPlugin.api;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;

/**
 * 角色的只读快照（{@code RoleAPI.getRoles()} 的返回单元）。
 *
 * <p>刻意做成 record：下游（{@code SHDFGamePlugin} 等）只需要"看到有哪些角色、叫什么、长什么样"，
 * 不需要、也不应该拿到 {@code Role} / {@code RoleInstance} 这类内部对象 —— 后者会立刻把内部实现细节
 * 变成外部契约。
 *
 * @param id          角色 id（如 {@code meiqihezi} / {@code red}）
 * @param displayName 显示名（Adventure Component）
 * @param description 描述行（只读）
 * @param icon        图标材质
 * @param faction     所属阵营
 */
public record RoleInfo(String id, Component displayName, List<Component> description, Material icon, Faction faction) { }
