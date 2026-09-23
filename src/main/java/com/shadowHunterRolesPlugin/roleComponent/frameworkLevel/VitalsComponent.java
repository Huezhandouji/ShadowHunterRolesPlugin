package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 生命组件（阶段 10 · t63 · A1 改正；**t73 Part A 合并**）：
 * 系统级能力「生命 / 治疗 / 伤害」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>★ 本组件持有行为</b>：clamp 策略（{@code min(当前 + amount, Attribute.MAX_HEALTH)}）的**唯一实现**，
 * 以及**四个伤害原语**（Part A 从原 {@code DamageComponent} 并入 ⇒ 该类**已删除** ✓
 * ⇒ 伤害与生命**只有一个持有者** ✓）。
 * <p><b>状态归属如实申报</b>：生命的真值是 **Bukkit 玩家属性**（{@code player.getHealth()} /
 * {@code Attribute.MAX_HEALTH}）⇒ 不属"组件内部字段"而是**外部平台状态**（冻结件『〇之八』：
 * 组件**允许**依赖真正外部的东西 = Bukkit API ✓）。本组件**不复制**一份生命字段 ✗（那会立刻
 * 与客户端/服务端的真实生命值不同步 ⇒ 属"会撒谎的值"）。
 * <p><b>为什么这里可以直调静态工具</b>（沿 t63 的申报）：用户明文裁定「组件的实现设计**不必**不依赖任何
 * 外部的东西，比如一个伤害组件，它仍然可以**依赖我原来的静态伤害工具**」；队长更正后的口径把
 * 「薄封装」许可**收窄为"仅限真正外部的东西"**（静态工具 / 单例 / Bukkit API ✓），
 * **不适用于框架自己的服务端口** ✗ —— {@code DamageUtil} 属**真正外部**（它不依赖
 * {@code ComponentServices}，也不把"谁提供能力"这件事藏起来）。
 *
 * <h2>阶段 10 · t73 Part A：两个统一入口（用户裁定）</h2>
 * <ul>
 *   <li>{@link #damage(Player, double)} / {@link #heal(Player, double)}：**自己也是一种目标** ——
 *       传自己的 {@code player} 即"伤害自己 / 治疗自己"，传别人的即"他人" ✓</li>
 *   <li>**不提供** {@code healSelf} / {@code healOther} / {@code damageSelf} / {@code damageOther}
 *       四个组合入口 ✗ —— 理由是**组合爆炸**：将来加"群体治疗"还要再加方法 ✗</li>
 *   <li>{@code target} 类型 = <b>{@link Player}</b>（**不是** {@code LivingEntity}）：与平台既有惯例一致
 *       （{@code RoleAPI.healPlayer(Player|UUID, …)}）✓，且为 Part B 的**按实例路由**预留了前提
 *       （只有 {@code Player} 能保证找到角色实例）✓；{@code UUID} 重载日后可**只增** ✓</li>
 * </ul>
 *
 * <h2>★ Part A 的边界（如实申报，避免被读成把用户要求做完）</h2>
 * 本卡的 {@code damage} / {@code heal} **只做结算**（直接对传入的 {@link Player} 施加）：
 * <ul>
 *   <li><b>不含任何钩子投递</b> ✗ —— 受伤 / 受治疗两个回调（{@code onDamaged} / {@code onHealed}）未实现；</li>
 *   <li><b>不经平台查找</b> ✗ —— 跨实例（"他人"）的**按实例路由**未实现（卡面明许本卡不含）；</li>
 *   <li><b>不含主线程断言</b> ✗ —— 前提申报与断言属 Part B。</li>
 * </ul>
 * 以上各项全部**移交 Part B**（另立卡，依赖本卡）⇒ 本卡因此可**独立编译 / 独立过闸门 / 独立归档** ✓。
 */
public class VitalsComponent extends RoleComponent {

    public VitalsComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 本组件的"自己"（= 生命真值所在的那个玩家）。 */
    private Player self() {
        return svc().self().player();
    }

    // ───────────── 两个统一入口（t73 Part A）─────────────

    /**
     * **造成真实伤害**（无视护甲；含 {@link DamageUtil} 既有的 PDC 副作用与守卫）。
     * <p>语义：{@code victim = target}、{@code source = 本组件所属玩家}；走**既有**
     * {@code DamageUtil.dealtTrueDamage} 路径 ⇒ 与合并前逐字等价 ✓。
     * <p>用法：**自己也是一种目标** —— {@code damage(self(), 5)}（对自己）/
     * {@code damage(otherPlayer, 5)}（对他人）。
     * <p><b>本卡只结算</b>：不投递任何回调、不做平台查找（见类注释的边界申报）。
     *
     * @param target 承受方玩家（**可为自己**）
     * @param amount 伤害量
     */
    public void damage(Player target, double amount) {
        if (target == null) {
            return;
        }
        DamageUtil.dealtTrueDamage(target, self(), amount);
    }

    /**
     * **治疗**（内部按目标自己的最大生命 clamp）。
     * <p>语义：{@code target} 可为自己（= 旧 {@link #heal(double)} 的行为，clamp 语义**不变** ✓）或他人。
     * <p>用法：{@code heal(self(), 4)}（对自己）/ {@code heal(otherPlayer, 4)}（对他人）。
     * <p><b>本卡只结算</b>：不投递任何回调、不做平台查找（见类注释的边界申报）。
     *
     * @param target 受治疗方玩家（**可为自己**）
     * @param amount 治疗量
     */
    public void heal(Player target, double amount) {
        if (target == null) {
            return;
        }
        double newHealth = Math.min(target.getHealth() + amount,
                target.getAttribute(Attribute.MAX_HEALTH).getValue());
        target.setHealth(newHealth);
    }

    // ───────────── 旧单参入口（保留兼容；= target 为自己）─────────────

    /** 治疗**自己**（内部按最大生命 clamp）—— 与原 {@code RoleInstance#heal} 逐字等价。 */
    public void heal(double amount) {
        heal(self(), amount);
    }

    // ───────────── 四个伤害原语（t73 Part A 从 DamageComponent 并入）─────────────

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
