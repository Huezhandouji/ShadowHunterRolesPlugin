package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.DamageKind;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 生命组件：生命 / 治疗 / 伤害的持有者（每角色实例一个）。
 *
 * <h2>状态归属</h2>
 * 生命的真值是 Bukkit 玩家属性（`player.getHealth()` / `Attribute.MAX_HEALTH`），属外部平台状态。
 * 本组件不复制一份生命字段（那会与真实生命值不同步，属"会撒谎的值"）。
 *
 * <h2>行为归属</h2>
 * clamp 策略（`min(当前 + amount, Attribute.MAX_HEALTH)`）的唯一实现，以及四个伤害原语
 * （伤害与生命只有一个持有者）。
 * <p>可以直调静态工具（如 {@code DamageUtil}）：它属真正外部（不依赖服务集、也不藏"谁提供能力"）。
 *
 * <h2>两个统一入口</h2>
 * <ul>
 *   <li>{@link #damage(Player, double)} / {@link #heal(Player, double)}：自己也是一种目标 ——
 *       传自己的 player 即"伤害 / 治疗自己"；</li>
 *   <li>不提供 `healSelf` / `healOther` / `damageSelf` / `damageOther` 四个组合入口
 *       （理由：组合爆炸 —— 将来加"群体治疗"还要再加方法）；</li>
 *   <li>`target` 类型 = {@link Player}（不是 `LivingEntity`）：与平台既有惯例一致，且只有 `Player`
 *       能保证找到角色实例。</li>
 * </ul>
 *
 * <h2>边界</h2>
 * `damage` / `heal` 只做结算：不投递回调 —— 受伤 / 受治疗由
 * {@code listener/hook/DamageHookListener} 在平台事件面派发（见 {@link Participant}）。
 *
 * <h2>击杀订阅面</h2>
 * 「玩家被玩家击杀」也由本组件广播（见 {@link #addPlayerKilledListener(RoleComponent, Consumer)}）：
 * 本组件是"死亡"这条链的终点（生命的唯一写入者），而**要发增益的是击杀者** ⇒ 投递对象 =
 * **击杀者那一侧的**本组件。检测与投递写在 {@code listener/hook/PlayerKilledHookListener}
 * （击杀者经 {@link DamageUtil#getLastDamager(Player)} 定出）；本组件只持有名单与载荷。
 */
public class VitalsComponent extends RoleComponent {

    /**
     * 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器在装配期只读这个 id 与工厂（{@code Role.ComponentEntry}），不点名组件类。
     */
    public static final String ID = "vitals";

    /**
     * 玩家未装角色时的原版生命上限（= 既有实现里那个字面量 {@code 20} 的命名化）。
     * <p>它不是"角色配置"，而是平台基线：上限修饰符的值 = {@code cap - BASE_MAX_HEALTH}，
     * 因此本常量变则所有人的上限一起变 —— 只在这里出现一次，不得散落。
     */
    public static final double BASE_MAX_HEALTH = 20d;

    /**
     * 角色的生命上限（组件侧配置 · 占位值）。
     *
     * <p>真值应来自角色配置；本值是在「角色模板不再持有 HP/SanTE/Energy」之后，为不丢失既有行为
     * 而落的占位（可玩的 {@code meiqihezi} 与 {@code red} 的声明值都是 40）。
     * 逐角色差异（见 {@link #applyHealthModifier} 的行为申报）需由组件侧配置另行恢复。
     *
     * <p><b>为什么常量落在组件而不是容器</b>：上限的持有权已归本组件（生命的唯一持有者）；
     * 落在容器会让"上限"重新出现两个持有者。
     */
    public static final double ROLE_HEALTH_CAP = 40d;

    /**
     * 本组件自己的上限修饰符键（自己的状态自己管）。
     *
     * <p><b>为什么键归本组件</b>：上限修饰符是本组件的状态（上限的唯一持有者就是本组件）；
     * 由本组件自持后，容器不必认识它。
     *
     * <p><b>键值逐字不变</b>（`"role_health_modifier"`）：既有的属性修饰符仍被同一个键识别与移除。
     */
    public static final NamespacedKey HEALTH_MODIFIER_KEY =
            KeyFactory.Registry.of("role_health_modifier");

    public VitalsComponent(String id, ComponentServicesPort services) {
        super(id, services);
    }

    /** 本组件的装配描述符（与技能/被动同规；不带栏位、无额外依赖）。 */
    public static final class Specification extends RoleComponent.Specification<VitalsComponent> {

        public Specification() {
            super("Vitals");
        }

        @Override
        public VitalsComponent create(String id, ComponentServicesPort services) {
            return new VitalsComponent(id, services);
        }
    }

    /**
     * 开始生效：把生命上限装到玩家身上（本组件自己实现）。
     *
     * <p><b>为什么放在 {@code start()} 而不是 {@code awake()}</b>：写上限是玩家可见的副作用，
     * 而 `awake()` 的契约是"不得改动任何玩家可见状态"，因此只能放这里。
     *
     * <p><b>时序安全性（已核）</b>：`start()` 由 `RoleInstance.activate()` 广播，而
     * `RoleManager.selectRole` 的次序是「构造（不可见）→ 清旧角色 → `activate()`」，
     * 因此本方法写入时旧实例已被清完，不会重新踩上「旧实例按共享键误伤新实例」那三条坑
     * （逐条：① 上限修饰符 ② 热键栏 ③ 同类型药水）。
     */
    @Override
    public void start() {
        Player target = self();
        if (target == null) {
            return;
        }
        applyHealthModifier(HEALTH_MODIFIER_KEY, ROLE_HEALTH_CAP);
        //就地读属性（同一读数，行为逐字不变）—— 写入经本组件（生命的唯一持有者）
        restoreFull(target);
    }

    /**
     * 停止生效：把生命上限修饰符摘下来（本组件自己回收自己的状态）。
     *
     * <p><b>时序</b>：`stop()` 由 `RoleInstance.clear()` 经 `triggerLifecycleStop()` 广播
     * （`clear()` 的既有语句，本组件不新增任何框架侧调用点），与"实例被销毁"同一时机、与既有行为逐字一致。
     *
     * <p>只移除本组件自己登记的那把键（值逐字不变），不会误伤别人的修饰符。
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
     * 可参与"承受方"回调的组件：实现本接口的组件在自己被伤害 / 被治疗时收到通知。
     * <p>两个方法都是 {@code void}，因此改量与否决在类型上不可表达（只通知、不可否决）。
     * <p><b>调用者</b>：{@code listener/hook/DamageHookListener}（平台事件面）—— 它按
     * {@code targetInstance.getAllByType(Participant.class)} 扇出（容器的组件查取入口），
     * 且整段扇出经 {@code RoleInstance.deliverHook}，内部走唯一受保护入口 {@code guardedCall}。
     * <p><b>顺序</b>：先结算、后通知（量已定、账已结，回调改不了）。
     */
    public interface Participant {

        /**
         * 受伤通知（在承受方一侧触发，结算之后调用）。
         *
         * @param source 伤害来源玩家；可为 {@code null}（非玩家源）—— 实现方必须自己判空
         * @param amount 已结算的伤害量（只读：改它不影响结果）
         */
        void onDamaged(Player source, double amount);

        /**
         * 受治疗通知（在承受方一侧触发，结算之后调用）。
         *
         * @param amount 已结算的治疗量（只读：改它不影响结果）
         */
        void onHealed(double amount);
    }

    // ───────────── 两个统一入口（结算；含伤害类型与钩子）─────────────

    /**
     * 造成伤害（按 {@link DamageKind} 选原语；含 {@link DamageUtil} 既有的 PDC 副作用与守卫）。
     * <p>语义：{@code victim = target}、{@code source = 本组件所属玩家}；走既有 {@code DamageUtil}
     * 路径，与合并前逐字等价（{@code PHYSICAL} → {@code dealtPhysicalDamage}；
     * {@code TRUE} → {@code dealtTrueDamage}）。
     * <p><b>本入口只负责结算</b>：它不自己投递回调 —— 承受方的
     * {@link Participant#onDamaged(Player, double)} 由 {@code listener/hook/DamageHookListener}
     * 在平台事件（{@code EntityDamageEvent}）里按目标实例派发
     * （这样"一切真实伤害"都触发，而不只是走本入口的那部分）。
     * <p>用法（两例）：{@code damage(self(), 5, DamageKind.TRUE)}（对自己，真伤）/
     * {@code damage(otherPlayer, 14, DamageKind.PHYSICAL)}（对他人，物伤）。
     *
     * @param target 承受方玩家（可为自己）
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
     * 两参签名的兼容入口（保留不破公开面）：逐字等价于
     * {@code damage(target, amount, DamageKind.TRUE)}。
     * <p>保留理由：它已作为公开面发布，删它属 API 收缩；而它的语义（真伤）与 {@code TRUE}
     * 完全一致，委托即可，没有第二套实现。
     *
     * @deprecated 改用 {@link #damage(Player, double, DamageKind)}（显式写出伤害类型）。
     */
    @Deprecated
    public void damage(Player target, double amount) {
        damage(target, amount, DamageKind.TRUE);
    }

    /**
     * 治疗（内部按目标自己的最大生命 clamp）。
     * <p>语义：{@code target} 可为自己（= {@link #heal(double)} 的行为，clamp 语义不变）或他人。
     * <p><b>本入口只负责结算</b>：承受方的 {@link Participant#onHealed(double)} 由
     * {@code listener/hook/DamageHookListener} 在平台事件（{@code EntityRegainHealthEvent}）里派发。
     * <p>用法（两例）：{@code heal(self(), 4)}（对自己）/ {@code heal(otherPlayer, 4)}（对他人）。
     *
     * @param target 受治疗方玩家（可为自己）
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

    // ───────────── 单参入口（= target 为自己）─────────────

    //本方法保留为本组件自己的公开口：消费者直接持有强类型 VitalsComponent 调它（与 heal(Player, double) 同源）。

    /**
     * 治疗自己（内部按最大生命 clamp）—— 与 {@link #heal(Player, double)} 同源。
     */
    public void heal(double amount) {
        heal(self(), amount);
    }

    /**
     * 恢复满血（把当前生命置为 {@code Attribute.MAX_HEALTH} 的当前值）。
     * <p>语义 = 激活期「把新上限就地应用给当前生命」那一步（原实现直接调 Bukkit 的 {@code setHealth}，
     * 生命的写入点越过了组件）。现收进本组件：生命的一切写入（治疗 / 恢复满血 / 伤害）
     * 只有本组件一个持有者。
     * <p>读的是属性当前值（而非角色模板声明值），与既有行为逐字一致：上限的修改由调用方
     * 在此之前经 {@code AttributeModifier} 施加，本入口只负责"读到什么就设成什么"。
     * @param target 目标玩家；{@code null} 则无操作
     */
    public void restoreFull(Player target) {
        if (target == null) {
            return;
        }
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    /**
     * 角色的生命上限修饰符（= 既有 {@code role_health_modifier}）—— 由本组件负责施加与移除。
     *
     * <p><b>为什么归本组件</b>：本组件的职责是「生命的一切写入与上限」。原先这一段住在
     * {@code RoleInstance#activate()} 里、且上限值取自角色模板（当时写作"模板上限 − 20"）；
     * 角色模板不再持有生命上限后，若把常量留在容器侧，上限的持有权就仍在容器，与已定的
     * 「上限归组件」相反。故连键带应用/移除一起收进本组件。
     *
     * <p><b>作用对象 = 本组件自己的玩家</b>（{@link #self()}）—— 不接 {@code Player} 参数：
     * 上限永远只作用在"本实例的那个玩家"身上，传入可变目标会开一个「给别人的上限加修正」的口子
     * （既有调用点也只传 {@code player} 且从不传别人）。
     *
     * <p><b>值语义（与既有行为逐字相同）</b>：{@code mod = cap - BASE_MAX_HEALTH}，
     * 口径 = {@code ADD_NUMBER} 加在上限上；{@code BASE_MAX_HEALTH} = 玩家未装角色时的原版上限
     * （既有实现里那个字面量 {@code 20} 就是它的旧写法，现在给它一个名字）。
     *
     * <p><b>一处已变更的行为（如实申报）</b>：上限值不再来自角色模板。可玩的三个角色里
     * {@code meiqihezi}=40 / {@code red}=40 / {@code selfUpdateExample}=20；统一取
     * {@link #ROLE_HEALTH_CAP} 后，前两者行为逐字不变，而 {@code selfUpdateExample} 的 {@code mod}
     * 由 {@code 0} 变为 {@code 20}（满血 20 → 40）。该角色是演示角色（唯一目的 = 给「组件可请求
     * 重绘」一个生产使用点），故按「统一上限」处理；若日后要恢复逐角色上限，须由组件侧配置
     * 重新引入（不得回到角色模板字段）。
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
     * 移除角色的生命上限修饰符（= 既有 {@code RoleInstance#clear()} 里那一处）。
     *
     * <p>与 {@link #applyHealthModifier} 同属「上限」这一件事，一并归本组件，
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

    // ───────────── 击杀订阅面（玩家被玩家击杀）─────────────

    /**
     * 「玩家被玩家击杀」的载荷（**自定义 Bukkit 事件**）—— 订阅者（JDK {@code Consumer}）收到的那一个对象。
     *
     * <h2>为什么用事件承载</h2>
     * 一次击杀天然带四个值（两个玩家引用 + 两个角色 id），摊成四个入参就没法塞进一个 {@code Consumer}；
     * 用事件承载则一次交清，访问器名也沿用 Bukkit 惯例（{@code getKiller()} / {@code getVictim()}）。
     *
     * <h2>它<b>不</b>进 Bukkit 事件总线（与工程口径一致）</h2>
     * 本插件**不再发布 Bukkit 事件**（组件自己的监听器列表是唯一的变更通道）⇒ 本对象只经
     * {@link #addPlayerKilledListener(RoleComponent, Consumer)} 的名单直接交给订阅者，
     * **不会**被 {@code Bukkit.getPluginManager().callEvent(...)} 广播；{@link #getHandlers()} 那条
     * HandlerList 因此空转（留着只是让它仍是一个合法的 {@code Event}）。若日后确要"供别的插件监听"，
     * 那是「发布平台事件」这个决定本身，得另行裁定 —— 不在这里顺手 callEvent。
     *
     * <h2>字段口径</h2>
     * <ul>
     *   <li>{@code killer} —— 由 {@link DamageUtil#getLastDamager(Player)} 定出的击杀者
     *       （**不是**原版 {@code getKiller()}：本系统一部分伤害绕过原版事件，只有那条 PDC 覆盖两侧）；</li>
     *   <li>{@code victim} —— 死者（{@code PlayerDeathEvent} 的承受方，取值时其角色实例尚未被清）；</li>
     *   <li>{@code killerRoleId} —— 击杀者当时所装角色的登记 id（如 {@code "red"}）；</li>
     *   <li>{@code victimRoleId} —— 被杀者当时所装角色的 id；被杀者当时**没有**角色则为 {@code null}。</li>
     * </ul>
     * 四个字段一律不可变、且没有 setter（要改口径就在生产处改，不在载荷上开写口）。
     */
    public static final class PlayerKilledEvent extends Event {

        /** 本事件的 HandlerList（只为满足 {@link Event} 的抽象面；本事件不上总线，故它空转）。 */
        private static final HandlerList HANDLERS = new HandlerList();

        private final Player killer;
        private final Player victim;
        private final String killerRoleId;
        private final String victimRoleId;

        /**
         * @param killer       击杀者（不得为 {@code null} —— 没有击杀者就不会产生本事件）
         * @param victim       被杀者（不得为 {@code null}）
         * @param killerRoleId 击杀者所装角色的 id
         * @param victimRoleId 被杀者所装角色的 id；被杀者无角色时为 {@code null}
         */
        public PlayerKilledEvent(Player killer, Player victim, String killerRoleId, String victimRoleId) {
            this.killer = killer;
            this.victim = victim;
            this.killerRoleId = killerRoleId;
            this.victimRoleId = victimRoleId;
        }

        /** 击杀者（经 {@link DamageUtil#getLastDamager(Player)} 定出，非原版 {@code getKiller()}）。 */
        public Player getKiller() {
            return killer;
        }

        /** 被杀者。 */
        public Player getVictim() {
            return victim;
        }

        /** 击杀者所装角色的登记 id。 */
        public String getKillerRoleId() {
            return killerRoleId;
        }

        /** 被杀者所装角色的登记 id；被杀者当时无角色则为 {@code null}。 */
        public String getVictimRoleId() {
            return victimRoleId;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        /** 与 {@link #getHandlers()} 同源（Bukkit 事件类的惯例；本事件不上总线，故当前无消费者）。 */
        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /**
     * 一条击杀监听登记：{@code owner}（谁订阅）+ {@code listener}（怎么通知）成对持有
     * （与 {@link EnergyComponent.Listener} / {@link SanTEComponent.Listener} 同形）。
     *
     * <h2>为什么把 owner 一起登记</h2>
     * 投递纪律是「逐个经 {@code RoleInstance#deliverHook} 调用」：某个监听器抛异常时只隔离抛异常的
     * 那一个、其余照常收到。这条纪律需要每一条登记都知道自己属于哪个组件，而 {@code Consumer} 闭包
     * 没有身份 ⇒ 由注册方在 {@link #addPlayerKilledListener(RoleComponent, Consumer)} 里显式给出。
     * <p>record 不是接口（不违反"不新增自定义接口"）。
     */
    public record PlayerKilledListener(RoleComponent owner, Consumer<PlayerKilledEvent> listener) {
    }

    /** 击杀监听名单 —— 顺序 = 添加先后（迭代序稳定，因此"按装配序通知"可复现）。 */
    private final List<PlayerKilledListener> playerKilledListeners = new ArrayList<>();

    /** 逐条故障隔离的日志（与 SanTE 组件同规：报出是谁抛了）。 */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("ShadowHunterRoles.vitals");

    /**
     * 添加「击杀监听」—— 唯一的订阅入口。
     *
     * <h2>投给谁（本挂载点最容易误解的一点）</h2>
     * 名单挂在**本组件所属的那一个角色实例**上，而投递对象 = **击杀者那一侧**的本组件：
     * 击杀者 K 杀了人 ⇒ 通知的是 **K 自己实例**里的这条名单（不是死者的）。
     * 因此"杀人就给自己增益"的写法是：在**击杀者角色**的组件里（通常在它自己的 {@code start()} 里）
     * <pre>{@code
     * VitalsComponent vitals = getComponent(VitalsComponent.class);
     * killedEntry = vitals.addPlayerKilledListener(this, event -> {
     *     if (event.getKiller() != self()) return;     // 双保险：本名单只会在"我是击杀者"时被通知
     *     heal(4);                                     // 例：击杀回血
     * });
     * }</pre>
     * （被杀者一侧不会收到：那个实例在同一个 tick 里就被 {@code PlayerListener#onPlayerDeath} 清掉了，
     * 通知它没有意义；"我被谁杀了"请自行挂 {@code PlayerDeathEvent}。）
     *
     * <h2>时机与线程</h2>
     * 投递发生在 {@code PlayerDeathEvent}（主线程、优先级 {@code LOWEST} ⇒ 早于清角色）里，
     * 每次击杀**恰好一次**（自杀 / 无击杀者 / 击杀者无角色 / 击杀者不是玩家都不产生通知）。
     *
     * <h2>幂等与移除</h2>
     * 同一 {@code owner} + 同一 {@code listener} 重复添加不重复登记；{@code owner} 或 {@code listener}
     * 为 {@code null} 则忽略（回 {@code null}）。返回值 = 本次登记对应的 {@link PlayerKilledListener}，
     * 需要撤销时把它存进私有字段、用 {@link #removePlayerKilledListener(PlayerKilledListener)} 按引用移除
     * （{@code Consumer} 闭包没有身份标识，故"按身份移除"在类型上不可表达）。
     */
    public PlayerKilledListener addPlayerKilledListener(RoleComponent owner, Consumer<PlayerKilledEvent> listener) {
        if (owner == null || listener == null) {
            return null;
        }
        PlayerKilledListener entry = new PlayerKilledListener(owner, listener);
        if (!playerKilledListeners.contains(entry)) {
            playerKilledListeners.add(entry);
        }
        return entry;
    }

    /**
     * 移除击杀监听（按引用相等；不在名单里则 no-op 且返回 {@code false}）——
     * 调用方必须持有同一个 {@link PlayerKilledListener} 实例（把
     * {@link #addPlayerKilledListener(RoleComponent, Consumer)} 的返回值存进私有字段即可）。
     */
    public boolean removePlayerKilledListener(PlayerKilledListener entry) {
        return entry != null && playerKilledListeners.remove(entry);
    }

    /** 当前击杀监听数（诊断读口；供探针与运行级取证使用）。 */
    public int playerKilledListenerCount() {
        return playerKilledListeners.size();
    }

    /**
     * 逐个把击杀登记交给调用方（"逐监听器故障隔离"的承载面）—— 投递方（
     * {@code listener/hook/PlayerKilledHookListener}）用它把每一条套进
     * {@code RoleInstance#deliverHook}：某个监听器抛异常时只隔离那一个、其余照常收到。
     *
     * <p>本方法不替调用方做派发决策，只提供遍历。遍历前对名单取快照（{@code List.copyOf}）⇒
     * 遍历途中添加 / 移除监听器既不抛 {@code ConcurrentModificationException}，也不影响本趟
     * （本次通知的接受集在进入时已定，新加的从下一趟起收到）。
     */
    public void forEachPlayerKilledListener(Consumer<PlayerKilledListener> action) {
        if (action == null) {
            return;
        }
        for (PlayerKilledListener entry : List.copyOf(playerKilledListeners)) {
            action.accept(entry);
        }
    }

    /**
     * 通知全部击杀监听（一趟直调）。
     * <p><b>生产路径不走它</b>：真实投递在 {@code listener/hook/PlayerKilledHookListener} 里，
     * 逐条经 {@code RoleInstance#deliverHook}（"逐监听器故障隔离"的唯一实现点）。本方法的存在理由是
     * 给"没有容器"的离线单元测试一条与生产同源的派发路径（否则测试只能自己写循环，验的就成了测试自己的循环）。
     * <p>与 {@code SanTEComponent#notifyListeners} 同规：逐条 try/catch，某个监听器抛异常时只记日志并继续。
     * <p><b>不是第二条变更通道</b>：本方法只读名单并调监听器，不改任何状态、不产生事件。
     */
    public void notifyPlayerKilledListeners(PlayerKilledEvent event) {
        if (event == null) {
            return;
        }
        forEachPlayerKilledListener(entry -> {
            try {
                entry.listener().accept(event);
            } catch (RuntimeException listenerFailure) {
                LOG.warning("[vitals] playerKilled listener failed (owner=" + entry.owner().getId() + "): "
                        + listenerFailure);
            }
        });
    }
}
