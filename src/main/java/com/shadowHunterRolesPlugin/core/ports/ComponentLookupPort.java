package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.List;

/**
 * 组件服务端口（{@code ComponentServicesPort} 只提供「玩家实例」与「组件服务（组件查找 + 动态组件添加）」）。
 * <p>本端口就是那"组件服务"的全部内容：
 * <ul>
 *   <li>查找：{@link #get(Class)}（按类型，第一个）· {@link #getAll(Class)}（按类型，全部）·
 *       {@link #getById(String)}（按 id，第一个）· {@link #all()}（当前序快照）；</li>
 *   <li>动态添加（运行期可增 / 可删 / 插位）：
 *       {@link #add(String, RoleComponent.Specification)} ·
 *       {@link #insertAt(int, String, RoleComponent.Specification)} · {@link #remove(String)} ·
 *       {@link #remove(Class)}（按类型删第一个）· {@link #remove(Class, String)}（按类型 + id 删第一个）·
 *       {@link #removeAll(Class, String)}（删光同条件者）。</li>
 * </ul>
 * <p><b>查询语义</b>：类型条件 = 可赋值性（父类/接口查询命中子类实例），顺序 = 添加顺序：
 * <ul>
 *   <li>{@link #get(Class)} = 第一个符合条件的；</li>
 *   <li>{@link #getAll(Class)} = 全部符合条件的（添加顺序；无人符合则空列表）；</li>
 *   <li>{@link #getById(String)} = 第一个 id 相等的，id 可重复。</li>
 * </ul>
 * <p><b>实现点唯一</b>：{@code core/ComponentLookupImpl}（把 {@code core.component.ComponentRegistry} 与
 * 容器的服务集工厂、日志接起来）。{@link RoleComponent#getComponent(Class)} 走本端口的 {@link #get(Class)}。
 * <p><b>动态添加的生命周期</b>（与装配期同一顺序）：构造（构造期注入服务集）→ {@code awake()} → {@code start()}；
 * 失败一律回滚（不残留半初始化组件）。动态删除：{@code stop()} → 回收该组件资源 → 移出容器。
 * <p><b>依赖面（两个方向都不得静默失效）</b>：
 * <ol>
 *   <li>添加时：候选声明的必需依赖若在容器内无人提供，则拒绝（与装配期检查同一口径）；</li>
 *   <li>删除时：先算反向依赖（谁把它声明为 {@code requires}），非空则拒绝删除 + 记一条日志
 *       （点名：被删组件 / 阻止者 / 缺的类型），因为删掉之后"必需"就会静默失效。</li>
 * </ol>
 * <p><b>禁止遍历中修改</b>：框架正在广播组件钩子时（{@code update()}/{@code onSanTEChange()}/生命周期），
 * 三个写口一律抛 {@code IllegalStateException}（须等本次广播结束）。
 */
public interface ComponentLookupPort {

    /**
     * 取本角色实例内的另一个组件（按类型）：添加顺序第一个满足可赋值性者（父类/接口查询命中子类/实现类实例）。
     * 未注册 → {@code null}；注册表冻结前调用 → 抛 {@code IllegalStateException}。
     * <p>类型形参无上界，因此纯接口也能作为实参（父类或接口查询都命中子类/实现类实例；
     * 匹配时 {@code type.cast(...)}，安全）。{@link #getAll(Class)} 的首元素恒等于本方法的结果。
     */
    <T> T get(Class<T> type);

    /**
     * 取全部符合条件的组件：类型条件 = 可赋值性，顺序 = 添加顺序；无人符合则返回空列表（不是 null）；返回不可变列表。
     * <p>与 {@link #get(Class)} 同一条件、同一顺序，只是不截断：{@code getAll(T).isEmpty()}
     * 等价于 {@code get(T) == null}；支持接口查询（{@code getAll(Tag.class)} 返回全部实现者）。
     * 冻结前调用 → 抛 {@code IllegalStateException}；
     * {@code type == null} → 抛 {@code NullPointerException}。
     */
    <T> List<T> getAll(Class<T> type);

    /**
     * 按组件 id 取组件（id 是资源表键与热键栏查表键）。未注册 → {@code null}；注册表冻结前调用 → 抛 {@code IllegalStateException}。
     * <p>id 可重复：本口返回添加顺序第一个同 id 者（与 {@link #remove(String)} 同目标；
     * 要拿全部同 id 者用 {@link #getAllById(String)}）。
     */
    RoleComponent getById(String id);

    /**
     * 按 id 取全部：返回全部 id 相等的组件，顺序 = 添加顺序（容器当前序）；无人符合则返回空列表（不是 null）；返回不可变列表。
     * <p>与 {@link #getById(String)} 同一条件、同一顺序，只是不截断，因此
     * {@code getAllById(id).isEmpty()} 等价于 {@code getById(id) == null}，且首元素恒等于 {@code getById(id)}
     * —— 即与「{@link #get(Class)} / {@link #getAll(Class)}」这一对完全对称。
     * <p>{@code id == null} → 空列表（与 {@code getById(null) == null} 同口径：都不抛）。
     */
    List<RoleComponent> getAllById(String id);

    /**
     * 容器内组件的不可变快照（顺序 = 容器内当前序 = 动态序 = 渲染序与派发序）；冻结前调用 → 抛 {@code IllegalStateException}。
     */
    List<RoleComponent> all();

    /**
     * 运行期动态添加（追加到容器末尾）：声明 → 依赖预检 → 构造 → 注册 → {@code awake()} → {@code start()}。
     * <p>吃装配期描述符（与 {@code Role.Builder.addComponent} 同一个口径）：描述符同时给出
     * "造哪个类"与"依赖声明"，因此容器能把声明登记进反向依赖表。没有描述符的组件没有运行期添加入口，
     * 这与装配期同一分工（要按需添加就先给它补一个嵌套描述符）。
     * @param id 组件 id（id 可重复：同一个 id 可以添加多次，容器内会有多个同 id 组件）
     * @param specification 装配期描述符（会被 {@code bindId(id)} 绑定并冻结）
     * @return 新建并已生效的组件实例
     * @throws IllegalStateException 框架正在遍历组件表；或必需依赖无人提供（消息点名缺的类型）
     * @throws IllegalArgumentException id 为空 / 描述符为 null
     */
    <T extends RoleComponent> T add(String id, RoleComponent.Specification<T> specification);

    /**
     * 运行期动态添加（插位）：语义同 {@link #add}，但插入到下标 {@code index} 处（{@code index == all().size()} 等价于追加）。
     * @throws IndexOutOfBoundsException 下标越界
     */
    <T extends RoleComponent> T insertAt(int index, String id, RoleComponent.Specification<T> specification);

    /**
     * 运行期动态删除：先算反向依赖，若仍有组件把它声明为必需，则拒绝删除 + 记日志 + 抛异常；否则 {@code stop()} → 回收该组件资源 → 移出容器。
     * <p>id 可重复：本口删的是添加顺序第一个同 id 者，反向依赖表也按那一个实例计算
     * （见 {@code ComponentRegistry#requiredBy(String)}）；其余同 id 者留在容器里。
     * @return 是否确实删除了一个组件（未注册 → {@code false}，无副作用）
     * @throws IllegalStateException 框架正在遍历组件表；或存在把本组件声明为必需的阻止者
     */
    boolean remove(String id);

    /**
     * 按（类型 + id）删除第一个匹配的组件：两个条件同时满足才算匹配（类型条件 = 可赋值性，与 {@link #get(Class)} 同一把尺，父类/接口查询命中子类实例）。
     * <p>顺序 = 添加顺序，故删的是第一个匹配者；同条件的其余实例留在容器里。
     * 想一次删光同条件的所有实例用 {@link #removeAll(Class, String)}。
     * <p>语义与既有 {@link #remove(String)} 逐条一致：
     * ① 反向依赖守卫 —— 若仍有组件把它声明为必需，则拒绝删除 + 记日志 + 抛异常；
     * ② 通过则 {@code stop()} → 回收该组件资源 → 移出容器；
     * ③ 遍历窗口内（框架正在广播组件钩子）则抛 {@code IllegalStateException}。
     * @param type 组件类型（可为接口；{@code null} → 抛 {@code NullPointerException}）
     * @param id   组件 id（{@code null} → 无匹配，回 {@code false}）
     * @return 是否确实删除了一个组件（无匹配 → {@code false}，无副作用）
     * @throws IllegalStateException 框架正在遍历组件表；或存在把该组件声明为必需的阻止者
     */
    <T> boolean remove(Class<T> type, String id);

    /**
     * 按类型删除添加顺序第一个匹配的组件（不看 id）：类型条件 = 可赋值性（与 {@link #get(Class)} 同一把尺，父类/接口查询命中子类实例）。
     * <p>与 {@link #remove(Class, String)} 的关系：同一条实现，只是把 id 条件放宽为"任意 id"，
     * 故守卫、顺序、失败形态逐条一致。想删光同类型的所有实例用 {@link #removeAll(Class, String)}
     * 并传 {@code id = null}（或按具体 id 收窄）。
     * <p>重载消歧：本口与 {@link #remove(String)} 同名不同参 —— 传 {@code null} 字面量会歧义
     * （编译期报错），故须显式转型（{@code remove((String) null)} / {@code remove((Class<?>) null)}）
     * 或改用别的方法。
     * @param type 组件类型（可为接口；{@code null} → 抛 {@code NullPointerException}）
     * @return 是否确实删除了一个组件（无匹配 → {@code false}，无副作用）
     * @throws IllegalStateException 框架正在遍历组件表；或存在把该组件声明为必需的阻止者
     */
    <T> boolean remove(Class<T> type);

    /**
     * 按（类型 + id）删除全部匹配的组件：条件与 {@link #remove(Class, String)} 相同，
     * 但遍历整张表、把所有匹配者逐个删除（顺序 = 添加顺序），故同条件的实例一个不留。
     * <p><b>与单数版的关键差异（必有失败面，故不抛异常）</b>：
     * <ul>
     *   <li>逐个先查反向依赖：有阻止者的那一个被跳过（记日志点名"被删者 / 阻止者 / 缺的类型"），
     *       其余照常删除，不因一个删不掉而整体失败；</li>
     *   <li>返回被跳过者的不可变列表（空列表 = 全部删成功）；调用方由此知道哪些没删掉；</li>
     *   <li>遍历窗口内仍抛 {@code IllegalStateException}（那次一个都不删 —— 检查先于任何删除）。</li>
     * </ul>
     * @param type 组件类型（可为接口；{@code null} → 抛 {@code NullPointerException}）
     * @param id   组件 id（{@code null} → 无匹配，回空列表）
     * @return 被跳过（因反向依赖）的组件，顺序 = 添加顺序；全部删成功 → 空列表
     * @throws IllegalStateException 框架正在遍历组件表
     */
    <T> List<RoleComponent> removeAll(Class<T> type, String id);
}
