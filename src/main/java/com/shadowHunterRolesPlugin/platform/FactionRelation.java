package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;

/**
 * **阵营关系的真值**（★ **纯静态、无平台依赖** ⇒ 可**离线**测试）。
 *
 * <p><b>为什么单独成类</b>：真值原本住在插件主类的 {@link FactionLookup} **匿名实现**里，
 * 而主类引 {@code JavaPlugin}（离线测不了）⇒ 那条口径**永远无法被测试覆盖**。
 * 现在生产实现与测试**共用同一份代码** ✓（测试不再"复述一份可能漂移的语义"）。
 *
 * <h2>★★ 「无角色」口径（本项目的有意约定）</h2>
 * <b>一个已选角色的玩家，与一个没选角色的玩家，视为【不敌对】</b>
 * ⇒ 未选角色的玩家**不会**被技能当作敌人选中/伤害（自动索敌、"附近是否有敌人"同理）。
 *
 * <p>判据落点 = {@link #hasRole(Faction)}：{@link Faction#UNKNOWN} 即"没有角色"
 * —— 每个角色模板在注册期都必须声明真实阵营（{@code Role.Builder#faction(...)}）
 * ⇒ "阵营未知"与"没有角色"在生产数据里是同一件事。
 *
 * <p><b>边界</b>：{@code null} 一律按"不敌对 / 无角色"处理（比照"查不到就安全"）。
 */
public final class FactionRelation {

    private FactionRelation() {
    }

    /**
     * **该阵营是否代表"已选角色"**。
     *
     * @param faction 该玩家的阵营（{@code null} 视为未知）
     * @return {@code false} = 没有角色（{@link Faction#UNKNOWN} 或 {@code null}）
     */
    public static boolean hasRole(Faction faction) {
        return faction != null && faction != Faction.UNKNOWN;
    }

    /**
     * **两个玩家之间是否敌对**（**对称**）。
     *
     * <p>判据：**双方都有角色** 且 **阵营不同** ⇒ {@code true}；
     * 任一方没有角色 ⇒ {@code false}。
     *
     * @param selfFaction  第一个玩家的阵营（{@code null} 视为未知）
     * @param otherFaction 第二个玩家的阵营（{@code null} 视为未知）
     */
    public static boolean isHostile(Faction selfFaction, Faction otherFaction) {
        if (!hasRole(selfFaction) || !hasRole(otherFaction)) {
            return false;
        }
        return selfFaction != otherFaction;
    }

    /**
     * **「某个阵营」与「某个玩家的阵营」是否敌对**（自身相对）。
     *
     * <p>判据：对方**有角色** 且 阵营与 {@code selfFaction} 不同 ⇒ {@code true}；
     * 对方没有角色 ⇒ {@code false}。
     *
     * <p>★ 本条也要求 {@code selfFaction} **有角色** —— 口径是"**有角色者与没角色者互不敌对**"
     * （方向无关）。实践中"自身无角色"不可达（没角色就没有组件、跑不了技能），
     * 但口径必须一致：否则 {@code isHostileTo(UNKNOWN, SHADOW)} 会答"敌对"，
     * 与"没角色者不当敌人"自相矛盾 ✗（★ 这条正是被 {@code FactionRelationTest} 抓出来的）。
     *
     * @param selfFaction  己方阵营（**无角色 ⇒ 不敌对**）
     * @param otherFaction 对方阵营（{@code null} 视为未知 ⇒ 不敌对）
     */
    public static boolean isHostileTo(Faction selfFaction, Faction otherFaction) {
        return isHostile(selfFaction, otherFaction);
    }
}
