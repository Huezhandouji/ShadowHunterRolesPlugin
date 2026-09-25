package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.Scheduler;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * **服务组件的装配端口** —— 把「构造哪些服务组件、以什么顺序」**移出框架层**。
 *
 * <h2>★ 本端口存在的唯一理由</h2>
 * 框架层**禁止知道任何组件的存在**（见 `docs/ai-generated/约束-框架禁止认识组件.md`）。
 * 而此前 `RoleInstance` **自己调用** {@code ServiceComponents.build(...)} 构造 6 个服务组件
 * ⇒ 它既是「装配方」又是「使用者」⇒ **必然**要指名每一个具体组件类与它们的 {@code ID_*}
 * （实测违规 **29 处**）。**在这个形状下，任何"把清单搬个地方"都是补丁** ——
 * 本端口把它根治为：**装配由外部完成，框架只收一份 {@code List<RoleComponent>}` 数据**。
 *
 * <h2>契约</h2>
 * <ul>
 *   <li><b>实现者 = 装配层</b>（与 {@code registry/RoleLoader} 同层、位于 {@code core} 与
 *       {@code roleComponent/builtin} 之上）—— 只有它是「知道全部具体组件」的合法位置；</li>
 *   <li><b>返回顺序 = 契约的一部分</b>：调用方（容器）**不重排**。★ 特别地，
 *       <b>物品渲染组件必须排在任何会置脏的组件之前</b>（它的置脏通道要先存在）；</li>
 *   <li><b>不得返回 {@code null}</b>；空列表合法（= 本实例没有任何服务组件）。</li>
 * </ul>
 *
 * <h2>为什么形参是这些</h2>
 * 服务组件在构造期需要接线到容器与平台；这些接线**以数据/函数的形式**从容器传入，
 * 于是装配层**不需要**读容器的私有成员：
 * <table border="1">
 *   <caption>形参用途</caption>
 *   <tr><th>形参</th><th>用途</th></tr>
 *   <tr><td>{@code role}</td><td>角色模板（只读：装配期可能需要模板数据）</td></tr>
 *   <tr><td>{@code servicesFor}</td><td>按 id 取该组件的 {@link ComponentServices} 上下文</td></tr>
 *   <tr><td>{@code scheduler}</td><td>平台调度（计时组件用）</td></tr>
 *   <tr><td>{@code buffManager}</td><td>buff 记账表（**与容器共享同一实例**）</td></tr>
 *   <tr><td>{@code taskTrack} / {@code taskCancelAll}</td><td>任务归属登记与取消（计时组件用）</td></tr>
 *   <tr><td>{@code changeReporter}</td><td>「某组件报告了一次三值变更」的通用上报口
 *       （★ 容器据此做**真变化闸门 / 逐监听器隔离 / 重入合并**，实现细节仍在框架侧 ✓）</td></tr>
 * </table>
 *
 * <h2>失败形态</h2>
 * 实现抛异常 ⇒ 传播到容器构造期（装配失败 = 实例不可用），**不吞**。
 */
public interface ServiceComponentFactory {

    /**
     * 一次变更的**最小充分载荷**（★ record 不是接口 ⇒ 不违反「不新增自定义接口」的纪律）。
     * <p>{@code componentId} = 哪个组件报的（用于点名与日志）；三值 = 变更前 / 变更后 / 上限。
     */
    record Change(String componentId, int previous, int current, int max) {
    }

    /**
     * 构造本实例所需的**全部服务组件**。
     *
     * @param input 接线数据（见类 javadoc 的形参表）
     * @return **有序**列表；顺序即装配顺序，调用方不重排（渲染组件须最先）
     */
    List<RoleComponent> build(Input input);

    /** {@link #build(Input)} 的接线数据。**record ⇒ 纯数据，无行为**。 */
    record Input(
            Role role,
            java.util.function.Function<String, ComponentServices> servicesFor,
            Scheduler scheduler,
            BuffManager buffManager,
            Consumer<Task> taskTrack,
            java.util.function.ToIntFunction<RoleComponent> taskCancelAll,
            Consumer<Change> changeReporter
    ) {
    }
}
