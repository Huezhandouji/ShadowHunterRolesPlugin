package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * **框架级物品渲染组件**。
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
 * 渲染器的**管道职责**只剩"按注册序遍历 → 取 {@code buildItem()} → 唯一写点落位"；
 * 而**意图面**（谁在什么时候要求重绘）此前**没有组件形态**，只能挂在 `RoleInstance` 的私有字段上
 * （{@code markHotbarDirty} / {@code repaintRequester} 两处）。把意图面收进一个**框架级组件**后：
 * <ul>
 *   <li>它在**容器里可见、可查询**（{@code getAllByType} / {@code getComponent}）✓ ——
 *       后续 (b) 的"不可动物品保护"与回调有明确的挂载点 ✓；</li>
 *   <li>它与其他框架级服务组件**同构** ✓（同包同族：{@code VitalsComponent} / {@code EnergyComponent} /
 *       {@code SanTEComponent} / {@code BuffComponent} / {@code TimerComponent}）✓ ——
 *       同族服务组件共 **5** 个（阵营**不是**容器里的服务组件 ——
 *       它的真值住在聚合根 {@code core/Role}）。</li>
 *   <li>它是 {@code RoleComponent} 的子类 ⇒ 受既有**生命周期**与**故障隔离**（{@code guardedCall}）管辖 ✓。</li>
 * </ul>
 *
 * <h2>本组件不做的事（边界）</h2>
 * <ul>
 *   <li><b>不可动物品的保护</b>（Inventory 指定位置不可动）—— 不在本组件的职责内 ✗；</li>
 *   <li><b>受伤 / 治疗之外的渲染回调面</b>（"物品被点击 / 被移动"等）—— 同上 ✗；</li>
 *   <li><b>冻结面迁移与代际对拍</b> —— 不在本组件的职责内 ✗。</li>
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
     * **装配期绑定**（构造之后、{@code awake()} 之前）—— 与既有【装配期解析】同一条纪律
     * （『创建后绑定』机制已整体删除 ✗：绑定一律在构造期完成）。
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
     * **渲染回调（读侧：只通知、不可否决）**。
     *
     * <h2>形态（照 {@code VitalsComponent.Participant}）</h2>
     * 方法**返回 {@code void}** ⇒ "**改量**"与"**否决**"**在类型上不可表达** ✓ ——
     * 实现者只能观察，不能干预渲染结果 ✗。
     *
     * <h2>★ 触发点：{@code HotbarRenderer.render()} **真正完成一次刷新之后**</h2>
     * 这是**唯一**能覆盖"**内容变了但槽位没变**"的时机 ✓（例：同一个技能格从"可用"变"冷却中" ——
     * 槽位没动、物品换了 ⇒ 按槽位变化判会漏 ✗）。
     *
     * <h2>★ 变化判据（**无变化不得回调**）</h2>
     * 触发**不**等于"每帧都回调" ✗：渲染器维护**上一帧实际写入的槽位内容**基线，
     * 只有"本帧与上一帧**真的不同**"才算一次变化 ✓；**无变化的那一帧回调 0 次** ✗。
     * <ul>
     *   <li>判据 = {@code HotbarRenderer} 的逐槽位内容比较（记 slot + {@code ItemStack}）✓</li>
     *   <li>**不能**拿脏标记当判据 ✗：脏标记只表示"有人请求过重绘"，请求之后重建的物品**可能逐字相同** ⇒ 会**多报** ✓</li>
     *   <li>首帧（无基线）视作"有变化" ⇒ 首刷**会**回调 1 次 ✓</li>
     * </ul>
     *
     * <h2>顺序（逐条，不可交换）</h2>
     * <ol>
     *   <li>判脏 ⇒ ② **写物品**（唯一写点）⇒ ③ **清脏** ✓</li>
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

    // ───────── 热键栏能力的**读侧契约**：渲染组件按组件读数据（组件不再实现能力接口） ─────────
    //旧形态：本组件嵌套声明三个能力接口（声明面 / 物品产出面 / 能上热键栏），由主动组件基类实现。
    //新形态：这三件事都是**数据** —— 组件用**自己的公开方法**表达，本组件提供三个静态读口去读它。
    //★ 依据 R-8：接口只在"表达的东西不能成为组件"时才有理由；这三件事都能由组件自己的方法表达
    //  ⇒ 不必是接口（也就不必让每个组件去 implements 一个嵌套类型 ✗）。
    //★ **不新增任何接口**（R-1 / R-8）：读侧就是下面三个静态方法（本类无实例状态 ⇒ 静态）。
    //★ **接受集与旧形态逐字相同**：旧接口的唯一实现者是主动组件基类及其子类（技能家族 ∪ 主武器家族）
    //  ⇒ `instanceof ActiveComponent` 命中**同一个集合**；被动组件（PassiveSkill 及其子类）两侧都不在内 ✓。

    /**
     * **读声明面**（装配期数据：图标 / 显示名 / 描述 / 冷却声明值 / 耗能声明值 / 基础物品）：
     * 该组件持有的 {@link HotbarSpecification}；没有声明面（不在热键栏家族）⇒ {@code null} ✓。
     * <p>数据源 = 描述符（装配期对象，`freeze()` 之后只读）；组件侧经它自己的 `specification()` 暴露 ✓。
     */
    public static HotbarSpecification<?> specificationOf(RoleComponent component) {
        return component instanceof ActiveComponent active ? active.specification() : null;
    }

    /**
     * **读"本组件这一帧的物品"**（**完整形态**：材质 / 名称 / 后缀 / lore / 识别键都已就位）——
     * 由框架在**帧末 flush** 的写物品段调用（每帧至多一次/组件），并与**注册序**同序遍历；
     * 组件**不得**在此方法里写玩家背包（写物品的唯一落点仍是渲染器的那一次槽位写入）。
     * <p>不产出物品的组件（不在热键栏家族）⇒ {@code null} ✓（渲染器据此跳过该槽位）。
     * <p><b>为什么默认画法在组件基类而不是描述符</b>：画物品要读<b>运行期状态</b>（冷却剩余刻数 / 闸门 /
     * 当前能量），而描述符是**装配期对象**、拿不到 {@code svc()} ⇒ 默认画法写在技能与主武器这两个
     * **组件基类**里（三态材质 · 六条状态文案 · 技能带 {@code x.xs} 而主武器不带 · 两个 PDC 键 ·
     * 声明数据取自描述符）。
     * <p><b>覆写者须知</b>（PDC 键与六条文案**均允许组件覆写**，覆写者自负其责）：**键写错** ⇒
     * 监听器前置闸门识别不到该物品（点击它无任何反应）；**键缺失** ⇒ 角色清除时扫不到它
     * （物品残留在背包/热键栏里）。框架**不再**为此提供保障（不再"写完回读校验"）—— 这两条是
     * **已申报的代价**，不是缺陷。
     */
    public static ItemStack buildItemOf(RoleComponent component) {
        return component instanceof ActiveComponent active ? active.buildItem() : null;
    }

    /**
     * **读"外观是否依赖活状态"**：{@code true} = 它的外观会在**没有框架置脏事件**的情况下自己变
     * （例如技能冷却名里的 {@code x.xs} 秒数每刻都在变）⇒ 只要它在冷却中，框架就必须**每 tick**
     * 至少刷一次，否则玩家看到的是陈旧外观。
     * <p><b>为什么是自报值、而不是框架里的一句 {@code instanceof Skill}</b>：把「外观含秒数」
     * **写死成具体类** ⇒ ① 第三类"带倒计时外观"的组件加进来时**必须改框架文件** ✗；
     * ② 覆写掉秒数外观的子类**仍会被每 tick 重绘**（白写）✗。改为自报后：新组件**只加新文件**即可
     * （默认 {@code false}，需要就覆写 {@code true}）✓，而"覆写掉活状态外观"的子类可以覆写成
     * {@code false} ⇒ **不再每 tick 重绘** ✓。
     * <p><b>默认值 = {@code false}</b>：只有技能家族的默认画法带秒数 ⇒ 该家族覆写为 {@code true}，
     * 主武器与被动保持 {@code false} ⇒ **既有组件的真值表逐字不变**。
     * <p>不产出物品的组件（不在热键栏家族）⇒ {@code false} ✓（与旧形态"接口默认值 false"同义）。
     * <p>注意：本读数只回答"**要不要**每刻刷"；"**写不写**"仍由帧末 flush 决定（空闲 tick 零 setItem 不变）。
     * 外观依赖活状态、但变化**不是每刻**的组件应返回 {@code false}，并在状态真的变了时用
     * {@link #requestRepaint()} **主动请求** —— 那才是它的刷新节拍。
     */
    public static boolean dependsOnLiveStateOf(RoleComponent component) {
        return component instanceof ActiveComponent active && active.dependsOnLiveState();
    }
}
