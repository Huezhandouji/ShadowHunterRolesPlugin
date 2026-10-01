package com.shadowHunterRolesPlugin.roleComponent.base;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 弓弩组件基类（继承 {@link Skill}）：以弓 / 弩为形态的主手武器。
 *
 * <h2>为什么不能是 {@link MainWeapon}（本族独立的唯一根因）</h2>
 * 近战主武器的**右键 = 施放入口**：{@code MainWeaponListener.onRightClick} 按识别键判物后**无条件**
 * {@code setCancelled(true)}，再派发 {@code onCast(RIGHT_CLICK)}。而弓 / 弩的右键是**原版武器动作**
 * —— 拉弓 · 装填 · 击发 —— 一旦被取消，这把武器**永远拉不开弓**。所以弓弩不能挂在主武器那条管道上。
 *
 * <h2>为什么是 {@link Skill}（如实申报：这是"借用能力面"，不是"弓弩是一张技能卡"）</h2>
 * 把右键拿掉之后，弓弩剩下的两条输入（左键 / Q）与技能家族的入口 **完全同形**：
 * "取消原版行为 + 派发 {@code onCast(trigger)}"。于是直接复用技能家族的三样东西：
 * <ol>
 *   <li><b>入口词汇</b>：{@link CastSignal} / {@link CastTrigger} 与它的闸门口径（冷却中不派发）；</li>
 *   <li><b>能力面</b>：描述符（{@code HotbarSpecification}）· 冷却（{@code startCooldown()} 等）·
 *       栏位登记（{@code awake()} 是 {@code final}，本族一行都不用重写）· 提交面（{@code submitToHotbar()}）；</li>
 *   <li><b>默认画法</b>：技能侧画法（{@code Skill#buildItem()}）—— 冷却态名称**带 {@code " x.xs"} 秒数**，
 *       对一个有射速的武器是有效读数。</li>
 * </ol>
 * 装配期不变量也不破：「产出物品者 = 技能家族 ∪ 主武器家族」是装配级不变量
 * （{@code CapabilityDispatchTest#providersAreExactlyTheActiveComponentFamilies}），
 * 本类 {@code extends Skill} ⇒ 仍落在该集合内。
 *
 * <h2>★ 两处家族级修正（不这么做就会原地复现事故）</h2>
 * <ol>
 *   <li><b>{@link #identifyKey()} 覆写</b>：物品写**弓弩自己的键**而不是 {@code SKILL_KEY}。若不覆写，
 *       物品就带 {@code SKILL_KEY} ⇒ {@code SkillListener} 把它当技能卡 ⇒ **右键被它取消** ⇒
 *       弓永远拉不开 —— 正是本族存在的那个根因。键是"哪条输入管道认领这个物品"的判据，一族一键。</li>
 *   <li><b>{@link #onAttack(AttackSignal)} 封成 {@code final} 空实现</b>：本族的近战命中由 listener
 *       **直接拦截**（取消原版伤害，不派发任何入口）。不封的话，组件作者在弓弩里覆写 {@code onAttack}
 *       会**静默永不触发**（本族没有任何路径调它）；封死把"运行期惊喜"变成"编译错误"。</li>
 * </ol>
 *
 * <h2>入口对照（冻结口径）</h2>
 * <table border="1">
 *   <caption>输入事件 → 组件入口</caption>
 *   <tr><th>玩家动作</th><th>近战主武器（{@code MainWeapon}）</th><th>弓弩（本类）</th></tr>
 *   <tr><td>右键 · 拉弓 / 装填 / 击发</td><td>{@code onCast(RIGHT_CLICK)}（原版事件被取消）</td>
 *       <td><b>不派发任何入口</b>：原版动作照常（冷却中才取消，见 {@code BowWeaponListener}）</td></tr>
 *   <tr><td>左键 · 挥空 / 挖方块</td><td>原版交互被取消 + {@code onCast(LEFT_CLICK)}</td>
 *       <td>原版交互被取消 + {@code onCast(LEFT_CLICK)}（<b>同规</b>）</td></tr>
 *   <tr><td>左键 · 打人（近战命中）</td><td>{@code onAttack(AttackSignal)}</td>
 *       <td><b>直接拦截</b>：取消原版伤害，不派发任何入口（{@link #onAttack(AttackSignal)} 已封死）</td></tr>
 *   <tr><td>Q 丢弃</td><td>{@code onCast(DROP)}（原版丢弃被取消）</td>
 *       <td>{@code onCast(DROP)}（<b>同规</b>，原版丢弃同样被取消）</td></tr>
 *   <tr><td><b>箭矢离弦</b></td><td>—（不存在这样的时刻）</td>
 *       <td><b>{@link #onShoot(ShootSignal)}</b>（弓弩专属）</td></tr>
 * </table>
 *
 * <h2>钩子清单</h2>
 * <ul>
 *   <li><b>沿技能家族</b>：{@code awake / onAwake / start / stop / update}、
 *       {@link #canUse()}（抽象，具体弓回答）、{@link #onCast(CastSignal)}
 *       （<b>只会收到 {@code LEFT_CLICK} 与 {@code DROP}</b>）、{@code buildItem()}、
 *       {@code dependsOnLiveState()}（技能侧 = {@code true}）、
 *       {@code startCooldown()} / {@code stopCooldown()} / {@code isCoolingDown()} /
 *       {@code remainingCooldownTicks()}、{@code submitToHotbar()}；</li>
 *   <li><b>本类新增</b>：{@link #onShoot(ShootSignal)}（弓弩专属：箭矢离弦那一刻）、
 *       {@link #identifyKey()} 覆写（弓弩物品的识别键）；</li>
 *   <li><b>本类封死</b>：{@link #currentEnergy()}（能量维度由类型封死 ≡ 0）与
 *       {@link #onAttack(AttackSignal)}（近战命中归 listener 直接拦截）。</li>
 * </ul>
 *
 * <h2>能量维度（规则进类型，与主武器侧同一条做法）</h2>
 * {@link Specification} 没有 {@code setEnergyCost} —— 能量消耗在构造期以字面量 {@code 0} 交给
 * {@link Skill.Specification}，且 {@link #currentEnergy()} 被本类用 {@code final} 一次性回答
 * （回声明值 = "恰好够"）⇒ {@code ENERGY_LACK} 态对弓弩**不可达**（不得为它造新外观）。
 * <p>要把"耗能弓"做成可配置：① {@link Specification} 把 {@code energyCost} 加回参数表并转发，
 * ② 删掉本类的 {@link #currentEnergy()}（重新变抽象，由具体弓用自己的能量字段回答）——
 * 两处同改，缺一处就会出现"声明了耗能却永远不判能量"的静默错误。
 */
public abstract class BowWeapon extends Skill {

    /**
     * 射击信号：只带这一次射击的数据（不可变）。射手永远是 {@code svc().self().player()}，
     * 因此信号里没有射手，只有射出物与这一次射击的参数。
     *
     * @param projectile 射出的**箭矢本体**（弓 / 弩路径恒为 {@code Projectile}：射箭时是
     *                   {@code AbstractArrow}，弩装填烟花火箭时是 {@code Firework}（两者都实现
     *                   {@code Projectile}）。要读箭矢专有面 —— 伤害 / 暴击 / 拾取状态等 ——
     *                   在组件里自行 {@code instanceof AbstractArrow} 收窄）
     * @param weapon     这一次射击所使用的弓 / 弩物品（原样交回，便于按材质区分弓与弩、
     *                   或按耐久 / 附魔分叉；{@code null} = 平台未给出，调用方须自行判空）
     * @param force      拉弓力度（{@code 0..1}；弩恒为 {@code 1.0}）—— 箭矢初速与伤害都随它走，
     *                   需要"按蓄力分级"的效果直接读这里
     */
    public record ShootSignal(Projectile projectile, ItemStack weapon, float force) {
    }

    /**
     * 描述符口径的构造：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事（弓弩的能量消耗由类型恒为 0）。
     */
    public BowWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 冷却：由本组件实例自持（继承自 ActiveComponent，类注释已申报不再重复） ─────────

    /**
     * 弓弩物品的识别键 = {@link BowWeapon.Utils#BOW_WEAPON_KEY}（**不是** {@code Skill.Utils.SKILL_KEY}）。
     * <p>识别键是"哪条输入管道认领这个物品"的判据。技能家族的键被 {@code SkillListener} 认领，而它认领
     * 的方式是"取消右键 + 派发 {@code onCast(RIGHT_CLICK)}"；弓弩的右键必须归原版 ⇒ 必须落在它的判据之外。
     * <p>画法与技能家族共用同一份（继承 {@code Skill#buildItem()}），差异只在这一个键值上。
     */
    @Override
    protected NamespacedKey identifyKey() {
        return BowWeapon.Utils.BOW_WEAPON_KEY;
    }

    /**
     * 当前能量（技能家族下放给子类的那个口，本族在此**一次性回答并封死**）：本族的声明耗能由类型封死
     * ≡ {@code 0}（{@link Specification} 不暴露 {@code energyCost}）⇒ 直接回声明值 = "恰好够"，
     * 因此 {@code ENERGY_LACK} 态对弓弩不可达（与 {@link MainWeapon} 的既有口径逐字相同）。
     * <p>为什么是 {@code final}：本族的能量规则是**家族不变量**，不是扩展点 —— 若某个弓偷偷回一个更大的
     * 当前值，就会出现"画法按 0 耗能判定、组件却以为自己在耗能"的分裂。要做耗能弓就按类注释的两步一起改。
     */
    @Override
    protected final int currentEnergy() {
        return getEnergyCost();
    }

    /**
     * 物品使用入口（施放）：**本族只会收到 {@link CastTrigger#LEFT_CLICK} 与 {@link CastTrigger#DROP}**，
     * 由 {@code listener/BowWeaponListener} 分别在左键与 Q 两条路径上派发（两条路径的原版行为都先被取消：
     * 左键不许挖方块、Q 不许把物品丢出去）。
     * <p><b>{@link CastTrigger#RIGHT_CLICK} 对弓弩永不派发</b> —— 弓 / 弩的右键是原版武器动作
     * （拉弓 · 装填 · 击发），归原版；它对应的"用了一次武器"是箭矢离弦那一刻，那件事由
     * {@link #onShoot(ShootSignal)} 承接。因此覆写本方法时**只需处理左键与 Q 两种 trigger**，
     * 写不出"右键"分支。
     * <p>与技能 / 近战主武器逐条同规的三件事：① 左键 / Q 的原版行为都被取消；② 冷却中 listener
     * **不派发**本入口（闸门口径 = {@code SkillListener#cast} / {@code MainWeaponListener#cast} 的
     * "冷却中不派发"），因此本方法里不必再自查 {@code isCoolingDown()}；③ 冷却由组件在"确实做了事"
     * 之后自启（{@code startCooldown()}）。
     * <p><b>为什么保留本入口</b>：左键与 Q 是两件**真实存在**的玩家输入，取消原版行为之后若不给组件一个
     * 落点，这两次输入就只剩"被吃掉"一种结果。归到同一个 {@link CastSignal} 上，家族之间只差
     * "trigger 的可能取值集合"，不差"有没有这个口"。
     */
    @Override
    public void onCast(CastSignal signal) {
    }

    /**
     * 物品使用入口（射击）：**每射出一支箭后**执行一次 —— 由 {@code listener/BowWeaponListener} 在
     * {@code EntityShootBowEvent} 上派发（原版事件已经放行，箭矢正在飞）。
     * <p>本入口与 {@link #onCast(CastSignal)} 的分工 = "哪件事算用了一次弓"：**箭矢离弦**（本方法）是真正的
     * 射击，而拉弓与装填只是准备动作，不派发任何入口（因此"按住右键"不会把入口刷爆）；
     * {@code onCast} 收的是另外两条输入（左键 / Q）。
     * <p>冷却由组件在"射击成功处"自启（与技能 / 主武器侧同一条口径：框架与 listener 都**不**代启动，
     * 典型写法 = {@code startCooldown()}）；冷却中"拉不开弓"由 listener 在右键入口把住，见
     * {@code BowWeaponListener} 的类注释。
     * <p>本方法不是覆写任何接口（本组件家族的单独声明），因此没有 {@code @Override}。
     */
    public void onShoot(ShootSignal signal) {
    }

    /**
     * **封印继承来的攻击入口**（本族的近战命中直接拦截）。
     * <p>本族的近战命中（左键打人）由 {@code BowWeaponListener} **取消原版伤害后不派发任何入口** ——
     * 弓弩物品不造成原版近战伤害，也不提供"近战伤害"这个扩展点（伤害该由射击那边给）。
     * <p>为什么封成 {@code final} 空实现、而不是"留着不管"：留着的话，组件作者在弓弩里覆写本方法会
     * **静默永不触发**（没有任何路径调它）。封死把"运行期惊喜"变成"编译错误"，与
     * {@code ActiveComponent#awake()} 用 {@code final} 堵住"漏调 super"是同一条纪律。
     * <p>为什么是空体而不是抛异常：本方法仍属 {@code ActiveComponent} 的通用面，若被误调，静默什么都不做
     * **优于**让一次派发把组件打成故障隔离（故障隔离的语义是"组件坏了"，不是"调错了口"）。
     */
    @Override
    public final void onAttack(AttackSignal signal) {
    }

    /**
     * 弓弩描述符（纯声明）：带栏位（继承 {@link Skill.Specification} 的那一支），
     * 参数顺序 = 技能侧去掉被本族封死的 {@code energyCost} 之后的原样顺序
     * （{@code displayName, description, cooldownTicks, icon}）。
     * <p><b>为什么再开一支</b>：① 家族描述符是"这一族怎么造"的声明点（与
     * {@code Skill} / {@link MainWeapon} / {@code PassiveSkill} 三族同构）；② 泛型上界收窄
     * （{@code <B extends BowWeapon>}）⇒ 别的家族**不可能**误用本描述符。
     * <p><b>能量消耗在这里被封死</b>：构造期以字面量 {@code 0} 转发给 {@link Skill.Specification}，
     * 本类型没有 {@code energyCost} 参数 ⇒ {@code ENERGY_LACK} 态对弓弩不可达（冻结面口径）。
     * 要开成可配置的耗能弓，见类注释末尾的两步改法。
     * <p>泛型化理由与技能侧同：参数化后 {@code providedType()} 推导落到**具体武器类**（不是
     * {@code BowWeapon} 族级），别的组件才能 {@code requires(某具体弓.class)}。
     * <p>本类型不实现 {@link #create(String, ComponentServicesPort)}，具体组件必须自己声明嵌套
     * {@code Specification} 并覆写它（编译期强制）。
     */
    public abstract static class Specification<B extends BowWeapon> extends Skill.Specification<B> {

        /** 声明式构造（推荐）：id 属于注册处，不写进组件描述符。 */
        protected Specification(Component displayName, List<Component> description, int cooldownTicks,
                                Material icon) {
            this(null, displayName, description, cooldownTicks, icon);
        }

        /** 带 id 的构造（表现面需要 id 时用；{@code null} = 由注册处给出）。 */
        protected Specification(String id, Component displayName, List<Component> description, int cooldownTicks,
                                Material icon) {
            super(id, displayName, description, cooldownTicks, 0, icon);
        }

        /** 具体组件必须给出创建逻辑（协变返回 {@code B} ⇒ 推导落到具体类）。 */
        @Override
        public abstract B create(String id, ComponentServicesPort services);
    }

    //弓弩物品识别工具（键名 / 读取面；形态与 Skill.Utils / MainWeapon.Utils 逐字同形）
    //  本嵌套类**遮蔽**了继承来的 Skill.Utils（同名成员类型），因此本类内引用一律写全
    //  `BowWeapon.Utils.…`；外部调用点写 `BowWeapon.Utils.isBowWeapon(item)` —— 与
    //  `Skill.Utils.isSkillItem(item)` / `MainWeapon.Utils.isMainWeapon(item)` 三个读口同规。
    public static class Utils {

        /**
         * 弓弩物品的识别键（本家族的物品身份）。
         * <p>与 {@link Skill.Utils#SKILL_KEY} **必须是两个键**：键是"哪条输入管道认领这个物品"的判据 ——
         * 弓弩物品一旦带上技能键，就会被 {@code SkillListener} 当技能卡对待（右键被它取消）
         * ⇒ 永远拉不开弓。
         */
        public static final NamespacedKey BOW_WEAPON_KEY = KeyFactory.Registry.of(
                "bow_weapon_id"
        );

        public static boolean isBowWeapon(ItemStack item) {
            if (item == null || item.getType().isAir()) return false;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            return meta.getPersistentDataContainer().has(BOW_WEAPON_KEY, PersistentDataType.STRING);
        }

        public static String getWeaponId(ItemStack item) {
            if (item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            return meta.getPersistentDataContainer().get(BOW_WEAPON_KEY, PersistentDataType.STRING);
        }
    }

    //getters 在 ActiveComponent（getId / getDisplayName / getDescription / getCooldownTicks /
    // getEnergyCost）；图标（getIcon）在表现规格 HotbarSpecification。
}
