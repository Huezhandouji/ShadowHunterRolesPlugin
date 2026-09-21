package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.CooldownAware;
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
 * <b>阶段 7 · A 步</b>：表现规格类改全拼（`HotbarSpec` → {@link HotbarSpecification}），
 * 同时把它升格为**装配期描述符**（{@link RoleComponent.Specification}）的"带栏位"分支。
 * 本类持有的这一份是**声明值对象**：它没有栏位（栏位由装配器在描述符上设置），
 * 也从不由装配入口消费 —— 因此这条路径与阶段 6 逐字等价。
 * <p>
 * <b>阶段 8</b>：
 * <ul>
 *   <li>{@link #isCooling()} 在这里给出**唯一实现**（走 {@code svc().cooldowns()}）；
 *       框架的"秒数刷新节拍"读的就是它；</li>
 *   <li>{@code buildItem()}（{@link HotbarPresentable} ⊇ {@code HotbarItemProviding}）**在本类保持抽象**：
 *       默认画法由两个**家族基类**给出（`core/Skill` 带秒数、`core/MainWeapon` 不带）——
 *       画物品要读运行期状态，做不到在这里按家族分叉；</li>
 *   <li>旧的 kind 形参构造器（7/8 参 `@Deprecated` 别名）已随 kind 枚举删除；
 *       行为分支不再按种类分叉（无 kind 可言）。</li>
 * </ul>
 */
public abstract class ActiveComponent extends RoleComponent
        implements HotbarItem, HotbarActionable, HotbarPresentable, CooldownAware {

    private final HotbarSpecification<?> specification;

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
     * **冷却状态读数**（阶段 8 新增；{@link com.shadowHunterRolesPlugin.core.hotbar.CooldownBearing} 的唯一实现）：
     * 本组件在**单一冷却表**里是否存在未到期的条目。
     * <p>取反自 {@code CooldownPort#isReady()}（同一张表的两种读法）：无条目（例如被动从不写表）或已到期
     * ⇒ {@code false}。框架的帧末 flush 用它驱动"冷却中每 tick 至少刷一次"（技能名里的秒数才会逐刻递减）。
     */
    @Override
    public final boolean isCooling() {
        return !svc().cooldowns().isReady();
    }

    /**
     * 默认：不做事、也**不**进冷却（与今天 listener 的行为一致：未重写的热键栏触发只做就绪预检）。
     * <p>阶段 8：返回值改为 {@code void}（旧的施放结果枚举已删 —— 它今天**没有任何消费点**，
     * 见交付说明的零行为变化论证）。
     */
    @Override
    public void onCast(CastSignal signal) {
    }

    //冷却结束通知与 CooldownEndReason 已迁到能力接口 CooldownAware（阶段 6 · 派发面能力化第二刀）：
    //  · 本类通过 implements CooldownAware 继续获得"默认空实现"的同一语义（既有组件零改动、运行时等价）；
    //  · 需要响应冷却结束的组件改为**覆写** CooldownAware#onCooldownEnd，不再依赖继承本类。
}
