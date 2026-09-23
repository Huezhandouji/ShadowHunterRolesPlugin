package com.shadowHunterRolesPlugin.roleComponent;

/**
 * **伤害类型**（阶段 11 · t83 · B-静态半 · 用户裁定）：
 * 统一入口 {@code VitalsComponent.damage(Player, double, DamageKind)} 的第三个参数。
 * <p><b>为什么需要它</b>：合并前的调用点里 {@code physicalDamage}（走护甲/减伤）与
 * {@code trueDamage}（无视护甲）**各占一半** ⇒ 若两参入口只固定一种，就会**悄悄改掉一半调用点的语义** ✗。
 * 把它做成参数 ⇒ **仍然只有 `damage` 与 `heal` 两个入口** ✓（不做
 * {@code damagePhysical}/{@code damageTrue} 这类组合入口 —— 那是组合爆炸 ✗）。
 * <p><b>取值与既有原语的对应关系**（逐条对齐，不新增语义）：
 * <ul>
 *   <li>{@link #PHYSICAL} ⇒ {@code DamageUtil.dealtPhysicalDamage(...)}（**走护甲 / 减伤**）</li>
 *   <li>{@link #TRUE} ⇒ {@code DamageUtil.dealtTrueDamage(...)}（**无视护甲**；含既有 PDC 副作用）</li>
 * </ul>
 * <p><b>命名口径</b>：{@code TRUE} 沿用工程既有说法"真伤"（= {@code trueDamage}），
 * 不叫 {@code UNRESISTED} 之类的新词 —— 避免给同一件事起第二个名字 ✗。
 */
public enum DamageKind {

    /** **物理伤害**：走护甲与减伤（= 既有 {@code physicalDamage} 原语）。 */
    PHYSICAL,

    /** **真实伤害**：无视护甲（= 既有 {@code trueDamage} 原语）。 */
    TRUE
}
