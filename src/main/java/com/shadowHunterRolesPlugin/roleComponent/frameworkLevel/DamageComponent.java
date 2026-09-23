package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.LivingEntity;

/**
 * 伤害组件（阶段 10 · t63 · A1 改正）：系统级能力「真伤 / 物伤」的**组件形态**
 * （每角色实例一个；用户裁定 **D-1 = A 案**）。
 * <p><b>★ 本组件持有行为</b>：四个伤害原语直接落到**静态伤害工具** {@link DamageUtil}
 * （含 PDC 副作用）—— **不再转调任何框架端口** ✗。
 * <p><b>为什么这里可以直调静态工具</b>：用户明文裁定「组件的实现设计**不必**不依赖任何外部的东西，
 * 比如一个伤害组件，它仍然可以**依赖我原来的静态伤害工具**」；队长更正后的口径把
 * 「薄封装」许可**收窄为"仅限真正外部的东西"**（静态工具 / 单例 / Bukkit API ✓），
 * **不适用于框架自己的服务端口** ✗ —— {@code DamageUtil} 属**真正外部**（它不依赖
 * {@code ComponentServices}，也不把"谁提供能力"这件事藏起来）。
 * <p><b>状态归属</b>：伤害**没有**本组件私有的可变状态（真值 = 服务端的生命值 + PDC 标记），
 * 本组件只持有"做这件事"的行为 ⇒ 见状态归属表的「无私有状态」一行。
 */
public class DamageComponent extends RoleComponent {

    public DamageComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 真实伤害（无视护甲；含既有 PDC 副作用）。 */
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount) {
        DamageUtil.dealtTrueDamage(victim, source, amount);
    }

    /** 真实伤害 + 击退强度。 */
    public void trueDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        DamageUtil.dealtTrueDamage(victim, source, amount, knockbackStrength);
    }

    /** 物理伤害（走护甲/减伤）。 */
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount) {
        DamageUtil.dealtPhysicalDamage(victim, source, amount);
    }

    /** 物理伤害 + 击退强度。 */
    public void physicalDamage(LivingEntity victim, LivingEntity source, double amount, double knockbackStrength) {
        DamageUtil.dealtPhysicalDamage(victim, source, amount, knockbackStrength);
    }
}
