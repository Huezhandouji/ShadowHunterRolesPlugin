package com.shadowHunterRolesPlugin.core.ports;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 本组件所依附的对象（= Unity 的 {@code this.gameObject}），唯一被允许把"玩家"交给组件的地方。
 * 纪律：不用于读写状态；不把 {@code player()} 的结果存进字段或长任务闭包（每次现取）。
 * 掉线即销毁（§9.1）⇒ 实例存活期内 {@code player()} 恒非空。
 */
public interface Self {

    Player player();

    UUID id();
}
