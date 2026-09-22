package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * **「这个组件要能请求重绘」的能力接口**（阶段 8 · t46）：组件实现它 ⇒ 框架在**装配期**
 * （组件被构造之后、{@code awake()} 之前）把 {@link RepaintRequester} 交给它。
 * <p>
 * <b>为什么用能力接口、而不是给 {@code ComponentServices} 加第 11 个成员</b>（本卡的形态裁定）：
 * <ul>
 *   <li>{@code ComponentServices} 的 javadoc 明写「**恰好 10 个成员，一个都不许多**」，且注明
 *       {@code hotbar} 成员**曾被删除**（零使用者）⇒ 那是一次**有意的边界收缩**；加回第 11 个成员
 *       等于把一次深思熟虑的决策反着做回去 ✗；</li>
 *   <li>本能力是**按需 opt-in**：不关心外观自变的组件**不需要**看见它，白名单保持 10 ✓；</li>
 *   <li>符合能力簇口径（C-14）：能力按**语义**成簇，不按"方便"塞进服务集 ✓。</li>
 * </ul>
 * <b>绑定时机</b>：框架在装配循环里对每个组件做一次 {@code instanceof} 判定后立即绑定
 * ⇒ 组件在自己的任何钩子（含 {@code awake()} / {@code start()} / {@code update()}）里都能安全使用；
 * 未实现本接口的组件**永远不会**拿到 requester（也就**不可能**请求）。
 * <p>
 * <b>与「框架已代劳」的关系</b>：施放 / 冷却启动 / 冷却到点 / 能量变化 / buff 移除这五类时机框架
 * **已经**置脏；本接口补的是**其余**时机（组件自己的状态变化）。
 */
public interface RepaintRequestable {

    /**
     * 框架在装配期把请求入口交给实现者（**构造之后、{@code awake()} 之前**，且只交一次）。
     * <p>实现者应当只把它存进字段（例如 {@code private RepaintRequester repaintRequester;}），
     * **不得**转交给别的对象、也不得据此拿到任何写入能力（{@link RepaintRequester} 只有请求一个方法）。
     */
    void bindRepaintRequester(RepaintRequester requester);
}
