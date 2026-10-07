package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;

/**
 * 阵营关系的真值（纯静态、无平台依赖，因此可离线测试）。
 *
 * <p>生产实现（{@link FactionManager}）与测试共用这一份代码，
 * 因此测试校验的是生产真值本身，而不是"复述一份可能漂移的语义"。
 *
 * <h2>「无阵营」口径（本项目的有意约定）</h2>
 * 没有阵营的玩家**算敌人**：一方没阵营（或双方都没阵营）即视为敌对，
 * 因此未选角色的玩家会被技能当作敌人选中 / 伤害（自动索敌、"附近是否有敌人"同理）。
 *
 * <p>判据落点 = {@link #hasFaction(Faction)}：{@link Faction#UNKNOWN} 即"该玩家没有阵营"。
 * ★ <b>三态塌缩为两态</b>：「没有阵营组件」（未选角色 / 掉线 / 组件已被移除）与
 * 「组件在、阵营为 UNKNOWN」同属这一档 —— 读取侧（{@code FactionManager#factionOf}）把前者
 * 读成 {@code UNKNOWN}，因此本判据只有一个入口、判敌也只有一条实现。
 * 副作用：要问"有没有角色实例"不能再用本判据反推（那是另一件事，问实例表本身）。
 *
 * <p><b>本类只管阵营这一维</b>：另一维是"在场"（创造 / 旁观不参与，见 {@link CombatPresence}），
 * 由 {@link FactionManager#isHostile(Faction, java.util.UUID)} 合成，不落在这里
 * —— 本类因此保持零平台依赖、可直接断言真值表。
 *
 * <p><b>边界</b>：{@code null} 一律按"没有阵营"处理（与 {@link Faction#UNKNOWN} 同结果：敌对）。
 */
public final class FactionRelation {

    private FactionRelation() {
    }

    /**
     * 该阵营是否代表"有阵营"。
     *
     * @param faction 该玩家的阵营（{@code null} 视为未知）
     * @return {@code false} = 没有阵营（{@link Faction#UNKNOWN} 或 {@code null}）
     */
    public static boolean hasFaction(Faction faction) {
        return faction != null && faction != Faction.UNKNOWN;
    }

    /**
     * 两个玩家之间是否敌对（对称）。
     *
     * <p>判据：**双方都有阵营且阵营相同**才不敌对；其余全是敌对 —— 即"阵营不同"与
     * "任一方没有阵营"两条都属于敌对（后者是"没有阵营也算敌人"的落点，
     * 也是"对方没有阵营组件 ⇒ 恒为敌对"的落点：组件缺失在读取侧塌缩成 UNKNOWN）。
     *
     * @param selfFaction  第一个玩家的阵营（{@code null} 视为未知 = 没有阵营）
     * @param otherFaction 第二个玩家的阵营（{@code null} 视为未知 = 没有阵营）
     */
    public static boolean isHostile(Faction selfFaction, Faction otherFaction) {
        return !(hasFaction(selfFaction) && hasFaction(otherFaction) && selfFaction == otherFaction);
    }

    /**
     * 「某个阵营」与「某个玩家的阵营」是否敌对（自身相对）。
     *
     * <p>与 {@link #isHostile(Faction, Faction)} **同一口径、同一结果**：阵营关系是对称的，
     * 两种判定的参数个数只表达"谁问的"（自身相对 / 两玩家之间），不表达不同语义。
     * 保留本方法是因为调用点分两支：{@link FactionManager#isHostile(Faction, java.util.UUID)} 用它，
     * 两 UUID 那支用上面那个 —— 删掉任一都会让一方改名换义。
     *
     * @param selfFaction  己方阵营（没有阵营 ⇒ 己方也算敌人）
     * @param otherFaction 对方阵营（{@code null} 视为未知 = 没有阵营 ⇒ 敌对）
     */
    public static boolean isHostileTo(Faction selfFaction, Faction otherFaction) {
        return isHostile(selfFaction, otherFaction);
    }
}
