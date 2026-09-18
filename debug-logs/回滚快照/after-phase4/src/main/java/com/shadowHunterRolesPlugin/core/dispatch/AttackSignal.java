package com.shadowHunterRolesPlugin.core.dispatch;

import org.bukkit.entity.Player;

/** 攻击信号：攻击者永远是自己（{@code svc.self().player()}），这里只带受害者。 */
public record AttackSignal(Player victim) {
}
