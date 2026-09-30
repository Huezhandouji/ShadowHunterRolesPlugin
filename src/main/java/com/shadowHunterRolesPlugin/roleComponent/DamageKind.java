package com.shadowHunterRolesPlugin.roleComponent;

/**
 * 伤害类型：统一入口 {@code VitalsComponent.damage(Player, double, DamageKind)} 的第三个参数。
 * <p>既有调用点里 {@code physicalDamage}（走护甲 / 减伤）与 {@code trueDamage}（无视护甲）各占一半，
 * 两参入口只固定一种就会悄悄改掉另一半调用点的语义。做成参数后，入口仍然只有 `damage` 与 `heal`
 * 两个（不做 {@code damagePhysical}/{@code damageTrue} 这类组合入口 —— 那是组合爆炸）。
 * <p>取值与既有原语的对应关系（逐条对齐，不新增语义）：
 * <ul>
 *   <li>{@link #PHYSICAL} = {@code DamageUtil.dealtPhysicalDamage(...)}（走护甲 / 减伤）</li>
 *   <li>{@link #TRUE} = {@code DamageUtil.dealtTrueDamage(...)}（无视护甲；含既有 PDC 副作用）</li>
 * </ul>
 * <p>命名口径：{@code TRUE} 沿用工程既有说法"真伤"（= {@code trueDamage}），
 * 不叫 {@code UNRESISTED} 之类的新词 —— 不给同一件事起第二个名字。
 */
public enum DamageKind {

    /** 物理伤害：走护甲与减伤（= 既有 {@code physicalDamage} 原语）。 */
    PHYSICAL,

    /** 真实伤害：无视护甲（= 既有 {@code trueDamage} 原语）。 */
    TRUE
}
