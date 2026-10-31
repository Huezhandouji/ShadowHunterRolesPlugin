package com.shadowHunterRolesPlugin.roleComponent;

/**
 * 伤害类型：统一入口 {@code VitalsComponent.damage(Player, double, DamageKind)} 的第三个参数。
 * <p>既有调用点里 {@code physicalDamage}（走护甲 / 减伤）与 {@code trueDamage}（无视护甲）各占一半，
 * 两参入口只固定一种就会悄悄改掉另一半调用点的语义。做成参数后，入口仍然只有 `damage` 与 `heal`
 * 两个（不做 {@code damagePhysical}/{@code damageTrue} 这类组合入口 —— 那是组合爆炸）。
 *
 * <p><b>取值与原语的对应关系（逐条对齐）</b>：
 * <ul>
 *   <li>{@link #PHYSICAL} = {@code DamageUtil.dealtPhysicalDamage(...)}（走护甲 / 减伤；
 *       走原版**通用**伤害类型 —— 不指向任何保护附魔的伤害标签）</li>
 *   <li>{@link #TRUE} = {@code DamageUtil.dealtTrueDamage(...)}（无视护甲；含既有 PDC 副作用）</li>
 *   <li>{@link #EXPLOSION} = {@code DamageUtil.dealtExplosionDamage(...)}
 *       （2026-10-31 新增；走原版 {@code minecraft:explosion} 伤害类型 ⇒ 护甲 + **爆炸保护**等
 *       按原版伤害类型标签减免，死亡信息也是爆炸那一条）</li>
 *   <li>{@link #PROJECTILE} = {@code DamageUtil.dealtProjectileDamage(...)}
 *       （2026-10-31 新增；走原版 {@code minecraft:arrow} 伤害类型 ⇒ 护甲 + **弹射物保护**等
 *       按原版伤害类型标签减免，死亡信息是弹射物那一条）</li>
 * </ul>
 *
 * <p><b>为什么新两种不并进 {@code PHYSICAL}</b>：原版的保护附魔按**伤害类型标签**减伤
 * （{@code is_explosion} / {@code is_projectile}），而通用伤害类型两个标签都不在；
 * 把爆炸 / 弹射物硬算成物理，会让"爆炸保护 / 弹射物保护"在这两类伤害上完全失效 —— 那是玩法口径错误。
 *
 * <p>命名口径：{@code TRUE} 沿用工程既有说法"真伤"（= {@code trueDamage}），
 * 不叫 {@code UNRESISTED} 之类的新词 —— 不给同一件事起第二个名字。
 * 新两种按**原版伤害类型名**取（{@code EXPLOSION} / {@code PROJECTILE}），同样不另起词。
 */
public enum DamageKind {

    /** 物理伤害：走护甲与减伤（= 既有 {@code physicalDamage} 原语；原版通用伤害类型）。 */
    PHYSICAL,

    /** 真实伤害：无视护甲（= 既有 {@code trueDamage} 原语）。 */
    TRUE,

    /**
     * 爆炸伤害：走原版 {@code minecraft:explosion} 伤害类型。
     * <p>与 {@link #PHYSICAL} 的差别：护甲**与爆炸保护**按原版标签减伤，死亡信息为爆炸口径。
     */
    EXPLOSION,

    /**
     * 弹射物伤害：走原版 {@code minecraft:arrow} 伤害类型。
     * <p>与 {@link #PHYSICAL} 的差别：护甲**与弹射物保护**按原版标签减伤，死亡信息为弹射物口径。
     */
    PROJECTILE
}
