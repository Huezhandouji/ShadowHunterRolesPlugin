package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.List;

/**
 * 组件服务端口（`ComponentServices` 只提供「玩家实例」与「**组件服务（组件查找 + 动态组件添加）**」）。
 * <p>本端口就是那"组件服务"的**全部内容**：
 * <ul>
 * <li><b>查找</b>：{@link #get(Class)}（按类型，**第一个**）· {@link #getAll(Class)}（按类型，**全部**）·
 * {@link #getById(String)}（按 id，**第一个**）· {@link #all()}（当前序快照）；</li>
 * <li><b>动态添加</b>（"运行期可增 / 可删 / 插位"）：
 * {@link #add(String, RoleComponent.Specification)} ·
 * {@link #insertAt(int, String, RoleComponent.Specification)} · {@link #remove(String)}。</li>
 * </ul>
 * <p><b>查询语义</b>：类型条件 = **可赋值性**
 * （父类/接口查询命中子类实例），顺序 = **添加顺序**：
 * <ul>
 * <li>{@link #get(Class)} = 第一个符合条件的（**不是"具体类优先"** —— 已按真实语义改正）；</li>
 * <li>{@link #getAll(Class)} = 全部符合条件的（添加顺序；无人符合 ⇒ **空列表**）；</li>
 * <li>{@link #getById(String)} = 第一个 id 相等的，**id 可重复**。</li>
 * </ul>
 * <p><b>实现点唯一</b>：{@code core/ComponentLookupImpl}（把 {@code core.component.ComponentRegistry} 与
 * 容器的服务集工厂、日志接起来）。{@link RoleComponent#getComponent(Class)} 走本端口的 {@link #get(Class)}。
 * <p><b>动态添加的生命周期</b>（与装配期同一顺序）：构造（构造期注入服务集）→ {@code awake()} → {@code start()}；
 * 失败一律**回滚**（不残留半初始化组件）。<b>动态删除</b>：{@code stop()} → 回收该组件资源 → 移出容器。
 * <p><b>依赖面（两个方向都不得静默失效）</b>：
 * <ol>
 * <li><b>添加时</b>：候选声明的必需依赖若在容器内**无人提供** ⇒ 拒绝（与装配期检查同一口径）；</li>
 * <li><b>删除时</b>：先算**反向依赖**（谁把它声明为 {@code requires}）—— 非空 ⇒
 * **拒绝删除** + **记一条日志**（点名：被删组件 / 阻止者 / 缺的类型），因为删掉之后
 * "必需"就会静默失效。</li>
 * </ol>
 * <p><b>禁止遍历中修改</b>：框架正在广播组件钩子时（{@code update()}/{@code onSanTEChange()}/生命周期），
 * 三个写口一律抛 {@code IllegalStateException}（须等本次广播结束）。
 */
public interface ComponentLookup {

 /**
 * 取本角色实例内的另一个组件（**按类型**）：**添加顺序第一个**满足可赋值性者
 * （父类/接口查询命中子类/实现类实例）。未注册 → {@code null}；**注册表冻结前调用 → 抛 {@code IllegalStateException}**。
 * <p><b>语义修正</b>：旧 javadoc 写"具体类优先"，而实现一直是纯线性扫描 ⇒ 那是对行为
 * 撒谎的值 ⇒ 已按真实语义（**添加顺序第一个**）改写。{@link #getAll(Class)} 的首元素恒等于本方法的结果。
 * <p><b>类型形参无上界</b>：旧签名 {@code <T extends RoleComponent>} 让**纯接口**无法作为实参，
 * 与"父类**或接口**查询"冲突 ⇒ 改为无上界 + 匹配时 {@code type.cast(...)}（安全）。
 * 既有调用点源码级不变。
 */
    <T> T get(Class<T> type);

 /**
 * **取全部符合条件的组件**：类型条件 = 可赋值性，
 * 顺序 = **添加顺序**；无人符合 ⇒ **空列表**（不是 null）；返回不可变列表。
 * <p>与 {@link #get(Class)} 同一条件、同一顺序，只是不截断 ⇒ `getAll(T).isEmpty()` ⟺ `get(T) == null`。
 * 支持**接口**查询（`getAll(Tag.class)` 返回全部实现者）。
 * 冻结前调用 → 抛 {@code IllegalStateException}；{@code type == null} → 抛 {@code NullPointerException}。
 */
    <T> List<T> getAll(Class<T> type);

 /**
 * 按**组件 id** 取组件（id 是资源表键与热键栏查表键）。
 * 未注册 → {@code null}；**注册表冻结前调用 → 抛 {@code IllegalStateException}**。
 * <p><b>id 可重复</b>：id **可重复** ⇒ 本口返回**添加顺序第一个**同 id 者
 * （与 {@link #remove(String)} 同目标；要拿**全部**同 id 者请用 {@link #getAllById(String)}）。
 */
    RoleComponent getById(String id);

 /**
 * **按 id 取全部**（"`getById()` 返回找到的第一个，新增一个 `getAllById()`，返回符合条件的组件的列表，**和 `get()` 和 `getAll()` 一样**"）。
 * <p>返回**全部** id 相等的组件，顺序 = **添加顺序**（容器当前序）；无人符合 ⇒ **空列表**（不是 null）；
 * 返回**不可变**列表。
 * <p>与 {@link #getById(String)} **同一条件、同一顺序**，只是不截断 ⇒
 * {@code getAllById(id).isEmpty()} ⟺ {@code getById(id) == null}，且首元素恒等于 {@code getById(id)}
 * —— 即与「{@link #get(Class)} / {@link #getAll(Class)}」这一对**完全对称**。
 * <p>{@code id == null} ⇒ **空列表**（与 {@code getById(null) == null} 同口径：都不抛）。
 */
    List<RoleComponent> getAllById(String id);

 /**
 * 容器内组件的**不可变快照**（顺序 = 容器内当前序 = **动态序** = 渲染序与派发序）。
 * 冻结前调用 → 抛 {@code IllegalStateException}。
 */
    List<RoleComponent> all();

 /**
 * **运行期动态添加**（追加到容器末尾）：声明 → 依赖预检 → 构造 → 注册 → {@code awake()} → {@code start()}。
 * <p>吃**装配期描述符**（与 {@code Role.Builder.addComponent} 同一个口径）：描述符同时给出
 * "造哪个类"与"依赖声明" ⇒ 容器能把声明登记进反向依赖表。
 * 没有描述符的组件（例如经 {@code Role.Builder.addPassive} 装配的被动）**没有**运行期添加入口 ——
 * 与装配期同一分工（要按需添加就先给它补一个嵌套描述符）。
 * @param id 组件 id（** 起可重复**：同一个 id 可以添加多次 ⇒ 容器内会有多个同 id 组件）
 * @param specification 装配期描述符（会被 {@code bindId(id)} 绑定并冻结）
 * @return 新建并已生效的组件实例
 * @throws IllegalStateException 框架正在遍历组件表；或必需依赖无人提供（消息点名缺的类型）
 * @throws IllegalArgumentException id 为空 / 描述符为 null
 */
    <T extends RoleComponent> T add(String id, RoleComponent.Specification<T> specification);

 /**
 * **运行期动态添加（插位）**：语义同 {@link #add}，但插入到下标 {@code index} 处
 * （{@code index == all().size()} 等价于追加）。
 * @throws IndexOutOfBoundsException 下标越界
 */
    <T extends RoleComponent> T insertAt(int index, String id, RoleComponent.Specification<T> specification);

 /**
 * **运行期动态删除**：先算**反向依赖** —— 若仍有组件把它声明为必需 ⇒
 * **拒绝删除** + **记日志** + 抛异常；否则 {@code stop()} → 回收该组件资源 → 移出容器。
 * <p><b>（id 可重复）</b>：本口删的是**添加顺序第一个**同 id 者，
 * 反向依赖表也**按那一个**实例计算（见 {@code ComponentRegistry#requiredBy(String)}）；
 * 其余同 id 者留在容器里。
 * @return 是否确实删除了一个组件（未注册 ⇒ {@code false}，无副作用）
 * @throws IllegalStateException 框架正在遍历组件表；或存在把本组件声明为必需的阻止者
 */
    boolean remove(String id);
}
