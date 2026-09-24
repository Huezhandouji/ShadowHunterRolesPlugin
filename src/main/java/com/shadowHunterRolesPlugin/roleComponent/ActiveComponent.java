package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItem;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;

/**
 * 主动组件基类（设计 §4.3）：= 原 `Skill` + `MainWeapon` 去重后的并集，不多一个成员。
 * <p>
 * <b>阶段 6 · 统一装配</b>：本类降级为**可选的便利实现** —— 它只做一件事：把构造参数装进
 * {@link HotbarSpecification} 并实现 {@link #specification()}（**唯一实现点**）。表现访问器全部由
 * {@link HotbarPresentable} 的 `default` 方法提供 ⇒ 子类不再需要（也不应）逐个手写委托。
 * <p>
 * <b>阶段 7 · A 步</b>：表现规格类改全拼（`HotbarSpec` → {@link HotbarSpecification}；**旧短名类已于阶段 10 · t71 删除** ✓），
 * 同时把它升格为**装配期描述符**（{@link RoleComponent.Specification}）的"带栏位"分支。
 * 本类持有的这一份是**声明值对象**：它没有栏位（栏位由装配器在描述符上设置），
 * 也从不由装配入口消费 —— 因此这条路径与阶段 6 逐字等价。
 * <p>
 * <b>阶段 8</b>：
 * <ul>
 *   <li>{@link #isCooling()} 在这里给出**唯一实现**（读本组件的冷却实例状态，见下）；
 *       框架的"秒数刷新节拍"读的就是它；</li>
 *   <li>{@code buildItem()}（{@link HotbarPresentable} ⊇ {@code HotbarItemProviding}）**在本类保持抽象**：
 *       默认画法由两个**家族基类**给出（`core/Skill` 带秒数、`core/MainWeapon` 不带）——
 *       画物品要读运行期状态，做不到在这里按家族分叉；</li>
 *   <li>旧的 kind 形参构造器（7/8 参 `@Deprecated` 别名）已随 kind 枚举删除；
 *       行为分支不再按种类分叉（无 kind 可言）。</li>
 * </ul>
 * <p>
 * <b>阶段 13 · t105（用户裁定：组件自持冷却、框架不持有）</b>：冷却的**状态与判断**整体落在本类 ——
 * 每实例一份 `cooldownUntilTick`（同 id 的两个实例**各自独立** ✓），框架既不登记、也不派发、更不落表；
 * 框架侧只在需要读数时**转问组件**（`isCooling()` / 读数口），公开面一条不删 ✓。
 * <p>旧的"冷却结束回调"（原 `CooldownAware#onCooldownEnd`）随该能力接口一并删除 —— 全库**零覆写点**
 * （现算：`onCooldownEnd` 仅剩框架派发点与该接口自身的默认空实现）⇒ 删除**零行为变化** ✓。
 */
public abstract class ActiveComponent extends RoleComponent
        implements HotbarItem, HotbarActionable, HotbarPresentable {

    private final HotbarSpecification<?> specification;

    /**
     * **冷却实例状态**（阶段 13 · t105 从两个家族基类上提到本类，两份副本合一 ✓）：
     * 到期刻（`Bukkit.getCurrentTick()` 口径）；`0` = 无冷却 ⇒ 与"从未进过冷却"同义。
     * <p>状态**只属于本实例**：同 id 的两个实例各自持有自己的字段 ⇒ 不共享、不串扰 ✓
     * （用户裁定：组件自己持有冷却与判断，框架不知道冷却）。
     */
    private int cooldownUntilTick = 0;

    /**
     * **描述符口径的构造**（阶段 7 · B 步）：表现规格**不再由构造实参内联**，而是由组件自己的
     * 嵌套 `Specification` 声明、经装配入口 {@code Role.Builder.addComponent(id, specification)}
     * 交给容器，容器再经 {@code Specification.create(id, services)} 把它交给本构造器。
     * <p>本类持有的这一份同时是**基类默认画法的读面**（{@link #specification()}）；它由装配期
     * {@code freeze()} 置为只读 ⇒ 同一实例被多个玩家实例共享也不会被串改。
     */
    protected ActiveComponent(String id, ComponentServices services, HotbarSpecification<?> specification) {
        super(id, services);
        this.specification = specification;
    }

    /** **唯一实现点**：表现规格（`getDisplayName` / `getIcon` / … 等访问器由接口 default 委托到本方法）。 */
    @Override
    public final HotbarSpecification<?> specification() {
        return specification;
    }

    /**
     * **开始（或覆盖式重启）本组件的冷却**：时长取**声明值**（{@link #getCooldownTicks()}，唯一真值来源）。
     * <p>等价于 {@link #startCooldown(int) startCooldown(getCooldownTicks())} —— 保留无参形态是因为
     * 全部产品调用点都是"按声明值启动" ✓。
     *
     * @return 是否真的写入了冷却状态：声明值 ≤ 0（"没有冷却这回事"，如被动）⇒ **不写状态并返回 `false`**，
     *         与迁移前的无声语义逐字一致 ✓
     */
    public boolean startCooldown() {
        return startCooldown(getCooldownTicks());
    }

    /**
     * **按给定时长开始（或覆盖式重启）冷却**（阶段 13 · t105 新增重载）。
     * <p>语义：从**当前刻**重算到期（覆盖旧值，不做"取较大值"的续期 ✗）—— 与迁移前"以本次调用时刻重算"
     * 的覆盖式重启**逐字等价** ✓。
     * <p>存在理由：兼容薄壳（原冷却端口）与调试命令需要按**显式刻数**驱动冷却（旧端口签名是
     * `start(int ticks)`）⇒ 本重载让那条路径成为**纯委托**，不再需要框架侧的表 ✓。
     *
     * @param ticks 冷却时长（tick）；≤ 0 ⇒ 不写状态并返回 `false`
     * @return 是否真的写入了冷却状态
     */
    public boolean startCooldown(int ticks) {
        if (ticks <= 0) {
            cooldownUntilTick = 0;
            return false;
        }
        cooldownUntilTick = org.bukkit.Bukkit.getCurrentTick() + ticks;
        return true;
    }

    /** **结束冷却**（幂等）：清空本实例的状态；已不在冷却中也**不报错、不产生副作用** ✓。 */
    public void stopCooldown() {
        cooldownUntilTick = 0;
    }

    /** **冷却状态读数**：本实例的冷却是否**正在进行**（未到期）。 */
    public boolean isCoolingDown() {
        return org.bukkit.Bukkit.getCurrentTick() < cooldownUntilTick;
    }

    /** **剩余冷却刻数**：不在冷却中 ⇒ `0`（**不返回负数** ✓）。 */
    public int remainingCooldownTicks() {
        return Math.max(0, cooldownUntilTick - org.bukkit.Bukkit.getCurrentTick());
    }

    /**
     * **冷却状态读数**（阶段 8 新增；{@link com.shadowHunterRolesPlugin.core.hotbar.CooldownBearing} 的唯一实现）：
     * 逐字等价于 {@link #isCoolingDown()}（阶段 13 · t105：改为读**本组件实例**的状态，不再经任何端口 ✗）。
     * <p>框架的帧末 flush 用它驱动"冷却中每 tick 至少刷一次"（技能名里的秒数才会逐刻递减）——
     * 这条**节拍链**与展示链（{@code %.1f} 秒数）读的是同一份状态 ✓。
     * <p>旧的"冷却启动即置脏"动作已随之消失：冷却期间本读数恒为 `true` ⇒ flush 入口条件每 tick 成立
     * ⇒ **仍会重绘** ✓（等价性论证见交付说明的置脏语义一节）。
     */
    @Override
    public final boolean isCooling() {
        return isCoolingDown();
    }

    /**
     * 默认：不做事、也**不**进冷却（与今天 listener 的行为一致：未重写的热键栏触发只做就绪预检）。
     * <p>阶段 8：返回值改为 {@code void}（旧的施放结果枚举已删 —— 它今天**没有任何消费点**，
     * 见交付说明的零行为变化论证）。
     */
    @Override
    public void onCast(CastSignal signal) {
    }

    //阶段 13 · t105：冷却结束回调（原 CooldownAware）与框架侧派发点已整体删除 ——
    //  组件自持冷却状态后，"到期/被结束/被重启"都不再由框架通知（框架不持有、也不派发 ✓）。
}
