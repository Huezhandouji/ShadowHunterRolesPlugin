package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.DamageKind;
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
 * <h2>★ t73 Part A 的边界（**阶段 11 · t83 起已部分解除**）</h2>
 * Part A 落地时本卡的 {@code damage} / {@code heal} **只做结算**；其中一项**仍然成立**，其余已由 t83 解除：
 * <ul>
 *   <li><b>【仍成立】本入口不投递回调</b> ✓ —— 受伤 / 受治疗由
 *       {@code listener/DamageHookListener} 在**平台事件**面派发（见 {@link Participant}）✓</li>
 *   <li><b>【t83 已解除】两钩子</b> ⇒ {@link Participant#onDamaged(Player, double)} /
 *       {@link Participant#onHealed(double)} 已提供（经目标实例的受保护入口派发）✓</li>
 *   <li><b>【t83 已解除】伤害类型</b> ⇒ 三参 {@link #damage(Player, double, DamageKind)} ✓</li>
 *   <li><b>【t83 未做】跨实例运行级读数与主线程前提</b> ⇒ 属 **B-窗口半**（另立卡）✗</li>
 * </ul>
 */
public class VitalsComponent extends RoleComponent {

    public VitalsComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 本组件的"自己"（= 生命真值所在的那个玩家）。 */
    private Player self() {
        return svc().self().player();
    }

    // ───────────── 承受方回调（t83 · B-静态半）─────────────

    /**
     * **可参与"承受方"回调的组件**（阶段 11 · t83）：实现本接口的组件在**自己被伤害 / 被治疗**时收到通知。
     * <p>两个方法都是 <b>{@code void}</b> ⇒ **改量与否决在类型上不可表达** ✓
     * （用户裁定：只通知、不可否决）。
     * <p><b>调用者</b>：{@code listener/DamageHookListener}（平台事件面）—— 它按
     * {@code targetInstance.getAllByType(Participant.class)} **扇出**（`RoleInstance:352` 现成）✓，
     * 且整段扇出经 {@code RoleInstance.deliverHook} ⇒ 内部走唯一受保护入口 {@code guardedCall} ✓。
     * <p><b>顺序</b>：**先结算、后通知** ✓（量已定、账已结，回调改不了 ✗）。
     */
    public interface Participant {

        /**
         * **受伤通知**（在**承受方**一侧触发，**结算之后**调用）。
         *
         * @param source 伤害来源玩家；**可为 {@code null}**（非玩家源）—— 实现方必须自己判空 ✓
         * @param amount 已结算的伤害量（**只读**：改它不影响结果 ✗）
         */
        void onDamaged(Player source, double amount);

        /**
         * **受治疗通知**（在**承受方**一侧触发，**结算之后**调用）。
         *
         * @param amount 已结算的治疗量（**只读**：改它不影响结果 ✗）
         */
        void onHealed(double amount);
    }

    // ───────────── 两个统一入口（t73 Part A 结算；t83 加类型 + 钩子）─────────────

    /**
     * **造成伤害**（按 {@link DamageKind} 选原语；含 {@link DamageUtil} 既有的 PDC 副作用与守卫）。
     * <p>语义：{@code victim = target}、{@code source = 本组件所属玩家}；走**既有** {@code DamageUtil}
     * 路径 ⇒ 与合并前逐字等价 ✓（{@code PHYSICAL} → {@code dealtPhysicalDamage}；
     * {@code TRUE} → {@code dealtTrueDamage}）。
     * <p><b>★ 本入口只负责结算</b>：它**不**自己投递回调 ✗ —— 承受方的
     * {@link Participant#onDamaged(Player, double)} 由 {@code listener/DamageHookListener}
     * 在**平台事件**（{@code EntityDamageEvent}）里按**目标实例**派发 ✓
     * （这样"一切真实伤害"都触发，而不只是走本入口的那部分 ✓）。
     * <p>用法（两例）：{@code damage(self(), 5, DamageKind.TRUE)}（对自己，真伤）/
     * {@code damage(otherPlayer, 14, DamageKind.PHYSICAL)}（对他人，物伤）。
     *
     * @param target 承受方玩家（**可为自己**）
     * @param amount 伤害量
     * @param kind   伤害类型（{@link DamageKind#PHYSICAL} / {@link DamageKind#TRUE}）
     */
    public void damage(Player target, double amount, DamageKind kind) {
        if (target == null) {
            return;
        }
        if (kind == DamageKind.PHYSICAL) {
            DamageUtil.dealtPhysicalDamage(target, self(), amount);
        } else {
            DamageUtil.dealtTrueDamage(target, self(), amount);
        }
    }

    /**
     * **旧两参签名的兼容入口**（阶段 10 · t73 Part A 落地；**保留不破公开面** ✓）：
     * 逐字等价于 {@code damage(target, amount, DamageKind.TRUE)}。
     * <p>保留理由（P4 同族）：Part A 已把它作为公开面发布 ⇒ 删它属 API 收缩 ✗；
     * 而它的语义（真伤）与 {@code TRUE} 完全一致 ⇒ 委托即可，**没有第二套实现** ✓。
     *
     * @deprecated 改用 {@link #damage(Player, double, DamageKind)}（显式写出伤害类型）。
     */
    @Deprecated
    public void damage(Player target, double amount) {
        damage(target, amount, DamageKind.TRUE);
    }

    /**
     * **治疗**（内部按目标自己的最大生命 clamp）。
     * <p>语义：{@code target} 可为自己（= 旧 {@link #heal(double)} 的行为，clamp 语义**不变** ✓）或他人。
     * <p><b>★ 本入口只负责结算</b>：承受方的 {@link Participant#onHealed(double)} 由
     * {@code listener/DamageHookListener} 在**平台事件**（{@code EntityRegainHealthEvent}）里派发 ✓。
     * <p>用法（两例）：{@code heal(self(), 4)}（对自己）/ {@code heal(otherPlayer, 4)}（对他人）。
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
