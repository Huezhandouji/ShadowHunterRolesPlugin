package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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

    // ───────── 阶段 13 · t113：本组件承载的**能力接口**（原顶层 core/hotbar/HotbarItemProviding） ─────────

    /**
     * 「这个组件自己产出热键栏物品」的能力接口（阶段 8 立 · **阶段 13 · t113 迁入本组件**）：
     * **唯一实现点是 {@link #buildItem()}**。
     * <p><b>为什么嵌在本组件里</b>（用户裁定的能力归属原则：与使用它的组件同处、只由它持有 ✓）：
     * 本组件是**渲染意图面**的拥有者（"要重绘"归它），而本接口是**物品产出面**的声明
     * （"物品长什么样"归组件自己）—— 两者是同一件事的两半，收在同一个类型里最清楚 ✓；
     * 顶层文件已删 ✗（`core/hotbar/` 下不再有共享能力袋）。
     * <p><b>分工（阶段 8 冻结，逐字不变）</b>：组件侧**自己判定状态**并产出**完整已装饰**的物品；框架侧只保留
     * <b>管道职责</b> —— <b>什么时候写</b>（脏标记 / 帧末 flush）与 <b>写到哪一格</b>
     * （装配条目里的栏位，见 {@code Role.ComponentEntry#getSlot()}）。
     * <p><b>为什么默认实现在组件基类而不是描述符</b>：画物品要读<b>运行期状态</b>（冷却剩余刻数 / 闸门 / 当前能量），
     * 而描述符（{@link HotbarSpecification}）是**装配期对象**、拿不到 {@code svc()} ⇒ 默认画法写在
     * {@code core/Skill} 与 {@code core/MainWeapon} 这两个**组件基类**里（合成指标：三态材质 · 六条状态文案 ·
     * 技能带 {@code x.xs} 而主武器不带 · 两个 PDC 键 · 声明数据取自描述符）。描述符只保留**声明数据**
     * （图标 / 显示名 / 描述 / 冷却 / 耗能）。
     * <p><b>能力簇（用户裁定 C-14：强依赖的能力应合并）</b>：本接口被 {@link HotbarPresentable} 收进热键栏能力簇
     * （{@code HotbarPresentable extends HotbarItem, EnergyComponent.EnergyCosting, HotbarItemProviding}）
     * ⇒ <b>实现者集合按构造相同</b>（凡"能出现在热键栏"的组件都必须给出 buildItem），且 {@code buildItem()}
     * 的语义**必须读**簇内状态（冷却读口见 {@code RoleComponent.CooldownBearing} /
     * {@link EnergyComponent.EnergyCosting#getEnergyCost()} / {@link HotbarItem} 的声明面）
     * ⇒ 两条合并判据同时成立。不占热键栏的组件（被动）**不在**本簇内，
     * 因此不会被强制实现一个永远不会被调用的方法。
     * <p><b>覆写者须知（用户裁定：PDC 键与六条文案**均允许组件覆写**，覆写者自负其责）</b>：
     * <ul>
     *   <li><b>键写错</b>（写入的键与框架读取的键不一致）⇒ 该物品在监听器前置闸门
     *       （{@code listener/SkillListener} 的 {@code isSkillItem} / {@code listener/MainWeaponListener} 的
     *       {@code isMainWeapon}）处不被识别 ⇒ <b>点击该物品无任何反应</b>（玩家感知为"技能坏了"）；</li>
     *   <li><b>键缺失</b>（完全不写键）⇒ 角色清除时 {@code RoleInstance.clearHotbar()} 扫不到它
     *       ⇒ <b>物品残留在背包/热键栏</b>（可继续拿在手上）。</li>
     * </ul>
     * 框架**不再**为此提供保障（不再"写完回读校验"）—— 这两条是**已申报的代价**，不是缺陷。
     * <p><b>【已作废】旧路径口径逐字保留</b>：「{@code core.hotbar.HotbarItemProviding}`（顶层接口，
     * {@code core/hotbar/} 下）」—— 那描述的是**共享能力袋**的旧形态 ⇒ 已作废 ✗（现为本组件的嵌套类型）。
     */
    public interface HotbarItemProviding {

        /**
         * 产出本组件在当前时刻的热键栏物品（**完整形态**：材质 / 名称 / 后缀 / lore / 识别键都已就位）。
         * <p>
         * 由框架在**帧末 flush** 的写物品段调用（每帧至多一次/组件），并与**注册序**同序遍历；
         * 组件**不得**在此方法里写玩家背包（写物品的唯一落点仍是 {@code HotbarRenderer#render()}）。
         */
        ItemStack buildItem();

        /**
         * **本组件的外观是否依赖"活状态"**（阶段 8 · t46 新增的能力，A8）：{@code true} = 它的外观会在
         * **没有框架置脏事件**的情况下自己变（例如技能冷却名里的 {@code x.xs} 秒数每刻都在变）
         * ⇒ 只要它在冷却中，框架就必须**每 tick** 至少刷一次，否则玩家看到的是陈旧外观。
         * <p>
         * <b>为什么这是一个能力、而不是框架里的一句 {@code instanceof Skill}</b>（C-15 第三个实例测试）：
         * 旧判据把「外观含秒数」**写死成具体类** ⇒ ① 第三类"带倒计时外观"的组件加进来时**必须改框架文件** ✗；
         * ② 覆写 {@link #buildItem()} 去掉秒数外观的 {@code Skill} 子类**仍会被每 tick 重绘**（白写）✗。
         * 下沉为能力后：新组件**只加新文件**即可（默认 {@code false}，需要就覆写 {@code true}）✓，
         * 而"覆写掉活状态外观"的子类可以覆写成 {@code false} ⇒ **不再每 tick 重绘** ✓。
         * <p>
         * <b>默认值 = {@code false}</b>（不依赖活状态 ⇒ 不驱动每 tick 刷新）：这与"只有技能家族的默认画法
         * 带秒数"这一既有事实一致 —— {@code core/Skill} 覆写为 {@code true}，主武器与被动保持 {@code false}
         * ⇒ **既有 16 个组件的接受集逐字不变**。
         * <p>注意：本能力只回答"**要不要**每刻刷"；"**写不写**"仍由帧末 flush 决定（空闲 tick 零 setItem 不变）。
         * 外观依赖活状态、但变化**不是每刻**的组件（例如自己按需刷新计数的组件）应返回 {@code false}，
         * 并在状态真的变了时用 {@link HotbarRenderComponent#requestRepaint()} **主动请求**
         * —— 那才是它的刷新节拍（阶段 12 · t86 起请求走**渲染组件**这一条通道；
         * 旧的 {@code RepaintRequester} 类型已删除 ✓）。
         */
        default boolean dependsOnLiveState() {
            return false;
        }
    }

    // ───────── 阶段 13 · t114：本组件承载的第二个**能力接口**（原顶层 core/hotbar/HotbarItem） ─────────

    /**
     * 热键栏物品的**声明面**（设计 §4.2）：图标 / 显示名 / 描述 / 冷却声明值 / 耗能声明值。
     * 命名沿用工程的 JavaBean 风格（设计 §4.3 命名约定：不引入 record 风格访问器）。
     * <p><b>阶段 8</b>：本接口**只陈述声明数据**（由描述符提供），**不再**自述种类
     * （旧的 {@code getKind()} 已随 kind 枚举一起删除 —— 表现面与行为分支都不再需要它）；
     * "物品长什么样（含运行期状态）"改由 {@link HotbarItemProviding#buildItem()} 回答，
     * 默认画法在 `core/Skill` / `core/MainWeapon` 两个**组件基类**里。
     * <p><b>阶段 13 · t114 迁入本组件</b>：原顶层 `core/hotbar/HotbarItem` 已删 ✗ —— 声明面与产出面
     * （{@link HotbarItemProviding}）是同一件事的两半，收在**同一个类型**里最清楚 ✓；
     * 唯一的实现者仍是描述符（{@code HotbarSpecification}，冻结面）及其子类 ✓（只改类型限定名，语义一字未动）。
     * <p><b>【已作废】旧路径口径逐字保留</b>：「{@code core.hotbar.HotbarItem}`（顶层接口，
     * {@code core/hotbar/} 下）」—— 那描述的是**共享能力袋**的旧形态 ⇒ 已作废 ✗（现为本组件的嵌套类型）。
     */
    public interface HotbarItem {

        String getId();

        Component getDisplayName();

        Component getDescription();

        Material getIcon();

        int getCooldownTicks();

        int getEnergyCost();

        /**
         * **基础物品**（阶段 7 · C 步）：由描述符给出的热键栏物品**底稿**（材质 / 显示名 / 描述）。
         * <p>默认实现在 {@link HotbarSpecification#baseItem(String)}（由
         * {@link #getIcon()} / {@link #getDisplayName()} / {@link #getDescription()} 生成）；
         * 需要特殊底稿的组件**在自己的 `Specification` 里覆写**即可。
         * <p><b>阶段 8 起</b>：它不再是"框架施加装饰的输入"，而是**基类默认画法的输入**
         * （{@code Skill#buildItem()} / {@code MainWeapon#buildItem()} 读它取材质 / 名称 / 描述）。
         *
         * @param id 注册处的组件 id（覆写者可用于区分同类的不同实例；默认实现不读它）
         */
        ItemStack baseItem(String id);
    }
}
