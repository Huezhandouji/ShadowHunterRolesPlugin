package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.DamageKind;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 生命组件：
 * 系统级能力「生命 / 治疗 / 伤害」的**组件形态**（每角色实例一个）。
 * <p><b>★ 本组件持有行为</b>：clamp 策略（{@code min(当前 + amount, Attribute.MAX_HEALTH)}）的**唯一实现**，
 * 以及**四个伤害原语**（Part A 从原 {@code DamageComponent} 并入 ⇒ 该类**已删除** ✓
 * ⇒ 伤害与生命**只有一个持有者** ✓）。
 * <p><b>状态归属</b>：生命的真值是 **Bukkit 玩家属性**（{@code player.getHealth()} /
 * {@code Attribute.MAX_HEALTH}）⇒ 不属"组件内部字段"而是**外部平台状态**（
 * 组件**允许**依赖真正外部的东西 = Bukkit API ✓）。本组件**不复制**一份生命字段 ✗（那会立刻
 * 与客户端/服务端的真实生命值不同步 ⇒ 属"会撒谎的值"）。
 * <p><b>为什么这里可以直调静态工具</b>：组件的实现设计**不必**不依赖任何
 * 外部的东西，比如一个伤害组件，它仍然可以**依赖既有的静态伤害工具**；而"薄封装"许可被
 * **收窄为"仅限真正外部的东西"**（静态工具 / 单例 / Bukkit API ✓），
 * **不适用于框架自己的服务端口** ✗ —— {@code DamageUtil} 属**真正外部**（它不依赖
 * {@code ComponentServices}，也不把"谁提供能力"这件事藏起来）。
 *
 * <h2>两个统一入口</h2>
 * <ul>
 *   <li>{@link #damage(Player, double)} / {@link #heal(Player, double)}：**自己也是一种目标** ——
 *       传自己的 {@code player} 即"伤害自己 / 治疗自己"，传别人的即"他人" ✓</li>
 *   <li>**不提供** {@code healSelf} / {@code healOther} / {@code damageSelf} / {@code damageOther}
 *       四个组合入口 ✗ —— 理由是**组合爆炸**：将来加"群体治疗"还要再加方法 ✗</li>
 *   <li>{@code target} 类型 = <b>{@link Player}</b>（**不是** {@code LivingEntity}）：与平台既有惯例一致
 *       （{@code RoleAPI.healPlayer(Player|UUID, …)}）✓，且为 Part B 的**按实例路由**预留了前提
 *       （只有 {@code Player} 能保证找到角色实例）✓；{@code UUID} 重载日后可**再补** ✓</li>
 * </ul>
 *
 * <h2>★ 本入口的边界（逐条）</h2>
 * {@code damage} / {@code heal} **只做结算**；其中一项**仍然成立**，其余已解除：
 * <ul>
 *   <li><b>【仍成立】本入口不投递回调</b> ✓ —— 受伤 / 受治疗由
 *       {@code listener/DamageHookListener} 在**平台事件**面派发（见 {@link Participant}）✓</li>
 *   <li><b>【已解除】两钩子</b> ⇒ {@link Participant#onDamaged(Player, double)} /
 *       {@link Participant#onHealed(double)} 已提供（经目标实例的受保护入口派发）✓</li>
 *   <li><b>【已解除】伤害类型</b> ⇒ 三参 {@link #damage(Player, double, DamageKind)} ✓</li>
 *   <li><b>【未做】跨实例运行级读数与主线程前提</b> ⇒ 需运行级取证 ✗</li>
 * </ul>
 */
public class VitalsComponent extends RoleComponent {

    /**
     * **本组件的登记 id**（★ 知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器装配时只读这个 **id + 工厂**（{@code data}），不点名组件类 ✓。
     */
    public static final String ID = "vitals";

    /**
     * **玩家未装角色时的原版生命上限**（= 既有实现里那个字面量 {@code 20} 的命名化）。
     * <p>它不是"角色配置"，而是**平台基线**：上限修饰符的值 = {@code cap - BASE_MAX_HEALTH} ⇒
     * 本常量变则所有人的上限一起变 ⇒ **只在这里出现一次**，不得散落。
     */
    public static final double BASE_MAX_HEALTH = 20d;

    /**
     * **★ 角色的生命上限（组件侧配置 · 占位值）**。
     *
     * <p><b>真值应来自角色配置</b>——本值是在「角色模板不再持有 HP/SanTE/Energy」之后，
     * 为不丢失既有行为而落的**占位**（可玩的 {@code meiqihezi} 与 {@code red} 的声明值都是 40）。
     * 逐角色差异（见 {@link #applyHealthModifier} 的行为申报）需另立卡由**组件侧配置**恢复。
     *
     * <p><b>为什么常量落在组件而不是容器</b>：上限的持有权已归本组件（生命的唯一持有者）；
     * 落在容器会让"上限"重新出现两个持有者。
     */
    public static final double ROLE_HEALTH_CAP = 40d;

    /**
     * **本组件自己的上限修饰符键**（★ 自己的状态自己管）。
     *
     * <p><b>为什么键归本组件</b>：上限修饰符是**本组件的状态**（上限的唯一持有者就是本组件）。
     * 此前这把键由容器持有（`RoleInstance.roleHealthModifierKey`）再转手交进来
     * ⇒ 容器因此不得不认识本组件；现改为**本组件自持** ⇒ 容器不必认识它 ✓。
     *
     * <p><b>键值逐字不变</b>（`"role_health_modifier"`）⇒ 既有的属性修饰符仍被同一个键识别与移除 ✓。
     */
    public static final NamespacedKey HEALTH_MODIFIER_KEY =
            KeyFactory.Registry.of("role_health_modifier");

    public VitalsComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** **本组件的装配描述符**（与技能/被动同规；不带栏位、无额外依赖）。 */
    public static final class Specification extends RoleComponent.Specification<VitalsComponent> {

        public Specification() {
            super("Vitals");
        }

        @Override
        public VitalsComponent create(String id, ComponentServices services) {
            return new VitalsComponent(id, services);
        }
    }

    /**
     * **开始生效：把生命上限装到玩家身上**（★ 本组件自己实现，不再由容器代劳）。
     *
     * <p><b>为什么放在 {@code start()} 而不是 {@code awake()}</b>：写上限是**玩家可见**的副作用，
     * 而 `awake()` 的契约是"不得改动任何玩家可见状态"⇒ 只能放这里 ✓。
     *
     * <p><b>时序安全性（已核）</b>：`start()` 由 `RoleInstance.activate()` 广播，而
     * `RoleManager.selectRole` 的次序是
     * 「构造（不可见）→ 清旧角色 → `activate()`」⇒ 本方法写入时**旧实例已被清完**
     * ⇒ 不会重新踩上「旧实例按共享键误伤新实例」那三条坑 ✓
     * （逐条：① 上限修饰符 ② 热键栏 ③ 同类型药水）。
     */
    @Override
    public void start() {
        Player target = self();
        if (target == null) {
            return;
        }
        applyHealthModifier(HEALTH_MODIFIER_KEY, ROLE_HEALTH_CAP);
        //就地读属性（**同一读数**，行为逐字不变）—— 写入经本组件（生命的唯一持有者）
        restoreFull(target);
    }

    /**
     * **停止生效：把生命上限修饰符摘下来**（★ 本组件自己回收自己的状态）。
     *
     * <p><b>时序</b>：`stop()` 由 `RoleInstance.clear()` 经 `triggerLifecycleStop()` 广播
     * （`clear()` 的既有语句，本组件**不新增**任何框架侧调用点）⇒ 与"实例被销毁"同一时机，
     * 与既有行为逐字一致 ✓。
     *
     * <p>★ 只移除**本组件自己登记的**那把键（值逐字不变）⇒ 不会误伤别人的修饰符 ✓。
     */
    @Override
    public void stop() {
        Player target = self();
        if (target == null) {
            return;
        }
        removeHealthModifier(HEALTH_MODIFIER_KEY);
    }

    /** 本组件的"自己"（= 生命真值所在的那个玩家）。 */
    private Player self() {
        return svc().self().player();
    }

    // ───────────── 承受方回调 ─────────────

    /**
     * **可参与"承受方"回调的组件**：实现本接口的组件在**自己被伤害 / 被治疗**时收到通知。
     * <p>两个方法都是 <b>{@code void}</b> ⇒ **改量与否决在类型上不可表达** ✓
     * （只通知、不可否决）。
     * <p><b>调用者</b>：{@code listener/DamageHookListener}（平台事件面）—— 它按
     * {@code targetInstance.getAllByType(Participant.class)} **扇出**（容器的组件查取入口 ✓），
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

    // ───────────── 两个统一入口（结算；含伤害类型与钩子）─────────────

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
     * **两参签名的兼容入口**（**保留不破公开面** ✓）：
     * 逐字等价于 {@code damage(target, amount, DamageKind.TRUE)}。
     * <p>保留理由：它已作为公开面发布 ⇒ 删它属 API 收缩 ✗；
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

    // ───────────── 单参入口（= target 为自己；★ 同时是基类通用面的实现）─────────────

    /**
     * 治疗**自己**（内部按最大生命 clamp）—— 与原 {@code RoleInstance#heal} 逐字等价。
     *
     * <p>★ **本方法覆写 {@link RoleComponent#heal(double)}** ⇒ 框架按 id 取到通用面即可治疗，
     * **不必认识本组件** ✓（签名与语义与既有实现逐字相同）。
     */
    @Override
    public void heal(double amount) {
        heal(self(), amount);
    }

    /**
     * **恢复满血**（把当前生命置为 {@code Attribute.MAX_HEALTH} 的当前值）。
     * <p>语义 = 激活期「把新上限**就地**应用给当前生命」那一步（原实现住在
     * {@code RoleInstance#activate()} 内，直接调 Bukkit 的 {@code setHealth} ⇒
     * **生命的写入点越过了组件** ✗）。现收进本组件 ⇒ 生命的一切写入（治疗 / 恢复满血 / 伤害）
     * **只有本组件一个持有者** ✓。
     * <p>读的是**属性当前值**（而非角色模板声明值）⇒ 与既有行为逐字一致：上限的修改由调用方
     * 在此之前经 {@code AttributeModifier} 施加，本入口只负责"读到什么就设成什么" ✓。
     * @param target 目标玩家；{@code null} ⇒ 无操作
     */
    public void restoreFull(Player target) {
        if (target == null) {
            return;
        }
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    /**
     * **角色的生命上限修饰符**（= 既有 {@code role_health_modifier}）—— 由**本组件**负责施加与移除。
     *
     * <p><b>为什么归本组件</b>：本组件的职责是「**生命的一切写入与上限**」。原先这一段住在
     * {@code RoleInstance#activate()} 里、且**上限值取自角色模板**（当时写作"模板上限 − 20"）；
     * 角色模板不再持有生命上限后，若把常量留在容器侧，上限的持有权就仍在容器 ⇒ 与已定的
     * 「上限归组件」相反。故连**键**带**应用/移除**一起收进本组件。     *
     * <p><b>作用对象 = 本组件自己的玩家</b>（{@link #self()}）—— **不接 {@code Player} 参数**：
     * 上限永远只作用在"本实例的那个玩家"身上，传入可变目标会开一个「给别人的上限加修正」的口子 ✗
     * （既有调用点也只传 {@code player} 且从不传别人）。
     *
     * <p><b>值语义（与既有行为逐字相同的部分）</b>：{@code mod = cap - BASE_MAX_HEALTH}，
     * 口径 = {@code ADD_NUMBER} 加在上限上；{@code BASE_MAX_HEALTH} = 玩家**未装角色时的原版上限**
     * （既有实现里那个字面量 {@code 20} 就是它的旧写法 ⇒ 现在给它一个名字）。
     *
     * <p><b>★ 一处已变更的行为（如实申报）</b>：上限值不再来自角色模板。可玩的三个角色里
     * {@code meiqihezi}=40 / {@code red}=40 / {@code selfUpdateExample}=20 ⇒ 统一取 {@link #ROLE_HEALTH_CAP}
     * 后，**前两者行为逐字不变**，而 {@code selfUpdateExample} 的 {@code mod} 由 {@code 0} 变为
     * {@code 20}（满血 20 → 40）。该角色是**演示角色**（唯一目的 = 给「组件可请求重绘」一个生产使用点），
     * 故本次按「统一上限」处理；若日后要恢复逐角色上限，须由组件侧配置重新引入（**不得**回到角色模板字段）。
     *
     * @param key 上限修饰符的键（容器侧经平台 keys 生成，与既有实现同一个键名）
     * @param cap 角色生命上限（当前 = {@link #ROLE_HEALTH_CAP}）
     */
    public void applyHealthModifier(NamespacedKey key, double cap) {
        Player target = self();
        if (target == null || key == null) {
            return;
        }
        AttributeModifier am = new AttributeModifier(
                key,
                cap - BASE_MAX_HEALTH,
                AttributeModifier.Operation.ADD_NUMBER
        );
        target.getAttribute(Attribute.MAX_HEALTH).removeModifier(am);
        target.getAttribute(Attribute.MAX_HEALTH).addModifier(am);
    }

    /**
     * **移除角色的生命上限修饰符**（= 既有 {@code RoleInstance#clear()} 里那一处）。
     *
     * <p>与 {@link #applyHealthModifier} 同属「上限」这一件事 ⇒ 一并归本组件，
     * 使「上限的施加与撤销」不再分散在容器与本组件两处。
     */
    public void removeHealthModifier(NamespacedKey key) {
        Player target = self();
        if (target == null || key == null) {
            return;
        }
        target.getAttribute(Attribute.MAX_HEALTH).removeModifier(key);
    }

    // ───────────── 四个伤害原语（与生命同属本组件）─────────────

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
