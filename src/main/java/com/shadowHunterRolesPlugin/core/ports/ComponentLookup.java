package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.List;

/**
 * 组件服务端口（用户计划第一段：`ComponentServices` 只提供「玩家实例」与「**组件服务（组件查找 + 动态组件添加）**」）。
 * <p>本端口就是那"组件服务"的**全部内容**：
 * <ul>
 *   <li><b>查找</b>：{@link #get(Class)}（按类型）· {@link #getById(String)}（按 id）· {@link #all()}（当前序快照）；</li>
 *   <li><b>动态添加</b>（阶段 10 · t55 · 裁定④"运行期可增 / 可删 / 插位"）：
 *       {@link #add(String, RoleComponent.Specification)} ·
 *       {@link #insertAt(int, String, RoleComponent.Specification)} · {@link #remove(String)}。</li>
 * </ul>
 * <p><b>实现点唯一</b>：{@code core/ComponentLookupImpl}（把 {@code core.dispatch.ComponentRegistry} 与
 * 容器的服务集工厂、日志接起来）。{@link RoleComponent#getComponent(Class)} 走本端口的 {@link #get(Class)}。
 * <p><b>动态添加的生命周期</b>（与装配期同一顺序）：构造（构造期注入服务集）→ {@code awake()} → {@code start()}；
 * 失败一律**回滚**（不残留半初始化组件）。<b>动态删除</b>：{@code stop()} → 回收该组件资源 → 移出容器。
 * <p><b>依赖面（两个方向都不得静默失效）</b>：
 * <ol>
 *   <li><b>添加时</b>：候选声明的必需依赖若在容器内**无人提供** ⇒ 拒绝（与装配期检查同一口径）；</li>
 *   <li><b>删除时</b>（队长裁定 P2）：先算**反向依赖**（谁把它声明为 {@code requires}）—— 非空 ⇒
 *       **拒绝删除** + **记一条日志**（点名：被删组件 / 阻止者 / 缺的类型），因为删掉之后
 *       "必需"就会静默失效。</li>
 * </ol>
 * <p><b>禁止遍历中修改</b>：框架正在广播组件钩子时（{@code update()}/{@code onSanTEChange()}/生命周期），
 * 三个写口一律抛 {@code IllegalStateException}（须等本次广播结束）。
 */
public interface ComponentLookup {

    /**
     * 取本角色实例内的另一个组件（**按类型**，具体类优先）。
     * 未注册 → {@code null}；**注册表冻结前调用 → 抛 {@code IllegalStateException}**。
     */
    <T extends RoleComponent> T get(Class<T> type);

    /**
     * 按**组件 id** 取组件（id 是资源表键与热键栏查表键）。
     * 未注册 → {@code null}；**注册表冻结前调用 → 抛 {@code IllegalStateException}**。
     */
    RoleComponent getById(String id);

    /**
     * 容器内组件的**不可变快照**（顺序 = 容器内当前序 = **动态序** = 渲染序与派发序）。
     * 冻结前调用 → 抛 {@code IllegalStateException}。
     */
    List<RoleComponent> all();

    /**
     * **运行期动态添加**（追加到容器末尾）：声明 → 依赖预检 → 构造 → 注册 → {@code awake()} → {@code start()}。
     * <p>吃**装配期描述符**（与 {@code Role.Builder.addComponent} 同一个口径）：描述符同时给出
     * "造哪个类"与"依赖声明" ⇒ 容器能把声明登记进反向依赖表（P2 的数据来源）。
     * 没有描述符的组件（例如经 {@code Role.Builder.addPassive} 装配的被动）**没有**运行期添加入口 ——
     * 与装配期同一分工（要按需添加就先给它补一个嵌套描述符）。
     *
     * @param id            组件 id（**容器内唯一**；重复 ⇒ {@code IllegalArgumentException}）
     * @param specification 装配期描述符（会被 {@code bindId(id)} 绑定并冻结）
     * @return 新建并已生效的组件实例
     * @throws IllegalStateException 框架正在遍历组件表；或必需依赖无人提供（消息点名缺的类型）
     * @throws IllegalArgumentException id 已存在 / id 为空 / 描述符为 null
     */
    <T extends RoleComponent> T add(String id, RoleComponent.Specification<T> specification);

    /**
     * **运行期动态添加（插位）**：语义同 {@link #add}，但插入到下标 {@code index} 处
     * （{@code index == all().size()} 等价于追加）。
     *
     * @throws IndexOutOfBoundsException 下标越界
     */
    <T extends RoleComponent> T insertAt(int index, String id, RoleComponent.Specification<T> specification);

    /**
     * **运行期动态删除**：先算**反向依赖**（P2）—— 若仍有组件把它声明为必需 ⇒
     * **拒绝删除** + **记日志** + 抛异常；否则 {@code stop()} → 回收该组件资源 → 移出容器。
     *
     * @return 是否确实删除了一个组件（未注册 ⇒ {@code false}，无副作用）
     * @throws IllegalStateException 框架正在遍历组件表；或存在把本组件声明为必需的阻止者
     */
    boolean remove(String id);
}
