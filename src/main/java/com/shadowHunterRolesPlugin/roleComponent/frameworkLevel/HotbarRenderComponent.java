package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * **框架级物品渲染组件**（阶段 12 · t86 · 拆分 (a) 的骨架）。
 *
 * <h2>它是什么 / 不是什么（★ 边界，先看这条）</h2>
 * 本组件把过去散在框架里、"由渲染器与角色实例各自持有"的**渲染意图面**收拢成一个**组件形态**：
 * <ul>
 *   <li><b>是</b>：<b>意图登记 + 置脏</b>的唯一归属点 ✓ —— 组件（以及框架自身）要"请求重绘"时，
 *       走的是<b>本组件</b>登记的这条通道 ✓；</li>
 *   <li><b>不是</b>：它<b>不写物品</b> ✗ —— <b>帧末 flush 仍是唯一的 {@code setItem} 写点</b>
 *       （{@code core/hotbar/HotbarRenderer#render()} 内那一次槽位写入）✓。本组件**拿不到**
 *       {@code Inventory} / {@code ItemStack} 的写入面，也**不暴露**渲染器本身 ⇒
 *       「组件只能请求、不能写」这条硬边界**逐字保留** ✓。</li>
 * </ul>
 * <p>换句话说：本组件是"**要重绘**"这件事的拥有者，而"**怎么写**"仍归渲染器 ✗ ——
 * 二者以 {@code RoleInstance} 的一条置脏通道相连（见 {@link #requestRepaint()}）。
 *
 * <h2>为什么要有它（归属理由，非"为整洁而重构"）</h2>
 * 阶段 8 起，渲染器的**管道职责**只剩"按注册序遍历 → 取 {@code buildItem()} → 唯一写点落位"；
 * 而**意图面**（谁在什么时候要求重绘）此前**没有组件形态**，只能挂在 `RoleInstance` 的私有字段上
 * （{@code markHotbarDirty} / {@code repaintRequester} 两处）。把意图面收进一个**框架级组件**后：
 * <ul>
 *   <li>它在**容器里可见、可查询**（{@code getAllByType} / {@code getComponent}）✓ ——
 *       后续 (b) 的"不可动物品保护"与回调有明确的挂载点 ✓；</li>
 *   <li>它与其他框架级服务组件**同构** ✓（同包同族：{@code VitalsComponent} / {@code EnergyComponent} /
 *       {@code SanTEComponent} / {@code BuffComponent} / {@code TimerComponent} / {@code FactionComponent}）✓；</li>
 *   <li>它是 {@code RoleComponent} 的子类 ⇒ 受既有**生命周期**与**故障隔离**（{@code guardedCall}）管辖 ✓。</li>
 * </ul>
 *
 * <h2>本卡未做的（逐条申报，见说明件）</h2>
 * <ul>
 *   <li><b>不可动物品的保护</b>（Inventory 指定位置不可动）—— 属拆分 **(b)** ✗；</li>
 *   <li><b>受伤 / 治疗之外的渲染回调面</b>（"物品被点击 / 被移动"等）—— 属 **(b)** ✗；</li>
 *   <li><b>冻结面迁移与代际对拍</b> —— 属 **(c)** ✗。</li>
 * </ul>
 */
public class HotbarRenderComponent extends RoleComponent {

    /**
     * **渲染意图的拥有者**（本组件委派的置脏通道）。
     * <p>由 {@code RoleInstance} 在装配期注入（它同时持有渲染器与帧末 flush 调度）⇒
     * 本组件**不自己找渲染器**、也**不持有**任何 Bukkit 库存对象 ✓。
     * <p>{@code null} 是**合法**状态：无渲染器时（例如单元测试直接构造组件）请求重绘是**静默无操作** ✓
     * —— 与"未装配 ⇒ 无事可做"同义，**不是**错误 ✗。
     */
    @FunctionalInterface
    public interface RepaintSink {
        /** 置脏（幂等：同一 tick 多次请求与一次等价）。**不写物品** ✗。 */
        void markDirty();
    }

    /** 置脏通道；装配期注入，未注入时为 {@code null}（见 {@link RepaintSink} 的 null 语义）。 */
    private RepaintSink repaintSink;

    public HotbarRenderComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /**
     * **装配期绑定**（构造之后、{@code awake()} 之前）—— 与既有"创建后绑定"同一条纪律。
     * <p>由 {@code RoleInstance} 在 {@code initComponents} 里调用（见该处注释）。
     */
    public void bindRepaintSink(RepaintSink sink) {
        this.repaintSink = sink;
    }

    /**
     * **请求重绘**：把"要重绘"这件事登记到本组件，并置脏 ⇒ **下一次帧末 flush** 才会写物品 ✓。
     * <p>这是**唯一**的组件侧渲染意图入口 ✓ —— 取代此前并存的
     * {@code RepaintRequestable} / {@code RepaintRequester} 两条老通道（**禁两套并存** ✗）。
     * <p><b>幂等</b>：脏标记是布尔量 ⇒ 同一 tick 内多次调用与一次等价 ✓。
     * <p><b>不写物品</b> ✗：返回后物品**尚未**被改写 ✓（「空闲 tick 零 {@code setItem}」逐字不变 ✓）。
     */
    public void requestRepaint() {
        if (repaintSink != null) {
            repaintSink.markDirty();
        }
    }

    /** 是否已装配置脏通道（诊断读口；供探针与运行级取证使用）。 */
    public boolean hasRepaintSink() {
        return repaintSink != null;
    }

    /**
     * **渲染回调（读侧：只通知、不可否决）**（阶段 12 · t88 · B3）。
     *
     * <h2>形态（照 {@code VitalsComponent.Participant}）</h2>
     * 方法**返回 {@code void}** ⇒ "**改量**"与"**否决**"**在类型上不可表达** ✓ ——
     * 实现者只能观察，不能干预渲染结果 ✗。
     *
     * <h2>★ 触发点：{@code HotbarRenderer.render()} **真正完成一次刷新之后**（用户裁定 A）</h2>
     * 这是**唯一**能覆盖"**内容变了但槽位没变**"的时机 ✓（例：同一个技能格从"可用"变"冷却中" ——
     * 槽位没动、物品换了 ⇒ 按槽位变化判会漏 ✗）。
     *
     * <h2>★ 变化判据（**无变化不得回调**）</h2>
     * 触发**不**等于"每帧都回调" ✗：渲染器维护**上一帧实际写入的槽位内容**基线，
     * 只有"本帧与上一帧**真的不同**"才算一次变化 ✓；**无变化的那一帧回调 0 次** ✗。
     * <ul>
     *   <li>判据来源 = {@code HotbarRenderer} 的逐槽位内容比较（记 slot + {@code ItemStack}）✓</li>
     *   <li>**不能**拿脏标记当判据 ✗：脏标记只表示"有人请求过重绘"，请求之后重建的物品**可能逐字相同** ⇒ 会**多报** ✓（已在该处 javadoc 写明）</li>
     *   <li>首帧（无基线）视作"有变化" ⇒ 首刷**会**回调 1 次 ✓</li>
     * </ul>
     *
     * <h2>顺序（逐条，不可交换）</h2>
     * <ol>
     *   <li>判脏 ⇒ ② **写物品**（唯一写点）⇒ ③ **清脏**（既有三段，**本卡未改** ✗）</li>
     *   <li>然后：**若本帧有真实变化** ⇒ 按 {@code getAllByType(RenderCallback.class)} **扇出**，
     *       每个实现者**逐个**经 {@code RoleInstance.deliverHook} 调用 ✓（某个抛异常 ⇒ 按**故障隔离**语义只隔离它、
     *       其余照常收到 ✓）</li>
     *   <li><b>只读不取消</b> ✗：回调**不**参与"写什么"的决策 ⇒ 它**改不了**这一帧的渲染结果 ✓</li>
     * </ol>
     *
     * <h2>边界（如实申报）</h2>
     * 回调**只在"有变化"时**响 ✓ ⇒ 想"每帧都做事"的组件**不该**用它（那不是它的语义）✗。
     * 另：回调**不**保证"物品已被玩家看到" —— 它紧跟写入，客户端同步由平台负责 ✓。
     */
    public interface RenderCallback {

        /**
         * **本帧热键栏内容真的变了**（在写入完成之后调用）。
         * <p>**只通知**：改不了这一帧的渲染结果（返回 {@code void}）✗。
         */
        void onHotbarRendered();
    }
}
