package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * **「请求重绘」入口**（阶段 8 · t46）：组件手里唯一的、也是**最窄**的一条重绘通道 ——
 * **只有一个方法**。
 * <p>
 * <b>硬边界（本卡的全部意义）</b>：组件**只能请求，不能写**。
 * <ul>
 *   <li>它**不暴露** {@link HotbarRenderer}（组件拿不到渲染器，也就拿不到"什么时候写、写哪一格"）；</li>
 *   <li>它**不暴露**任何 Bukkit 库存对象（{@code Inventory} / {@code ItemStack} 的写入面）—— 组件仍只能
 *       通过 {@link HotbarItemProviding#buildItem()} **产出**物品，**写入仍由框架在帧末 flush 完成**；</li>
 *   <li>因此「<b>空闲 tick 零 setItem</b>」逐字不变：请求只把脏标记置上，**下一次帧末 flush** 才写，
 *       且每个栏位每帧至多写一次。</li>
 * </ul>
 * <b>怎么拿到它</b>：组件实现 {@link RepaintRequestable}，框架在**装配期**（构造之后、{@code awake()} 之前）
 * 把本接口的实例绑给它 ⇒ 「组件不参与渲染调度」这条边界是**有控制地打开一半**：可以请求，不能写。
 * <p>
 * <b>什么时候该请求</b>：**只有"自己的外观会随自身状态变"时**才需要（充能 / 弹药数 / 层数 / 蓄力条）。
 * 框架已经会在这些时机自己置脏：施放管道 / 冷却启动 / 冷却到点 / 能量变化 / buff 移除，
 * 以及"外观依赖活状态且正在冷却"的每刻刷新（见 {@link HotbarItemProviding#dependsOnLiveState()}）。
 * 组件若在这些时机之外改了外观，就必须自己请求 —— 否则玩家看到的是**陈旧外观**。
 */
@FunctionalInterface
public interface RepaintRequester {

    /**
     * 请求重绘：**只置脏**。返回后物品**尚未**被改写 —— 真正的写入发生在**本 tick 的帧末 flush**
     * （与框架自身的置脏同一条路径、同一个写点）。
     * <p>本方法**幂等**：同一 tick 内多次请求与一次请求等价（脏标记是布尔量）。
     */
    void requestRepaint();
}
