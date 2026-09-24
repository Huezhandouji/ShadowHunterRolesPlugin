package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent.CooldownBearing;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent.HotbarItem;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent.HotbarItemProviding;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarRenderer;
import com.shadowHunterRolesPlugin.core.ports.ComponentLookup;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
//阶段 13 · t123：聚合根只读服务面 —— 阵营读取的唯一入口（框架侧读口 {@link #roleInfo()} 的类型）。
import com.shadowHunterRolesPlugin.core.ports.RoleInfo;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.EnergyComponent;
//阶段 13 · t123（欠账 A 后半）：`frameworkLevel.FactionComponent` 的 **import 已删除** ✗ ——
//该组件本体已整体删除，阵营的真值改住聚合根 `core/Role` 的 `faction` 字段
//（读侧 = `roleInfo` 服务面，见下方 `roleInfo()` 读口；写侧 = `Role#setFaction/resetFaction`）。
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.TimerComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.logging.Level;

public class RoleInstance {


    private final Player player;
    private final Role role;

    // ───────── 阶段 10 · t63（A1 改正）：服务组件**每角色实例一个**，由本容器持有 ─────────
    //它们**不进 `Role` 模板**（`Role.getComponents()` 与装配表逐格不变 ✓），但会登记进**实例容器**
    //（`componentRegistry`）⇒ `svc().components().get(EnergyComponent.class)` 与 `RoleInstance#getByType(...)`
    //都能取到它们；**组件侧一律直接用组件本身**（阶段 13 · t106 起服务集不再转发这八件事 ✗）。
    //★ 状态归属（A3「状态唯一」）：能量 / SanTE 的真值、buff 记账表、药水账本、计时资源
    //  **都在组件里** ⇒ 本类**不再持有这些字段** ✗（旧字段已全部移除，见说明件的状态归属表）。
    //  **【已作废】原口径**（阶段 10 · t63 原文，逐字保留）：该清单里还有「阵营」一项 ✗ ——
    //  阶段 13 · t123（欠账 A 后半）把 `FactionComponent` 整体删除 ✗ ⇒ **阵营的真值改住聚合根
    //  `core/Role` 的 `faction` 字段** ✓（读 = `roleInfo` 服务面，写 = `Role#setFaction/resetFaction`）。
    private final EnergyComponent energyComponent;
    private final SanTEComponent santeComponent;
    private final VitalsComponent vitalsComponent;
    private final BuffComponent buffComponent;
    private final TimerComponent timerComponent;
    /**
     * **阶段 12 · t86**：框架级**物品渲染组件** —— 渲染**意图面**（意图登记 + 置脏）的拥有者 ✓。
     * <p><b>唯一写点未变</b> ✗：物品仍只由 {@link #hotbarRenderer} 在**帧末 flush** 里写；
     * 本组件**不写物品**、也拿不到库存写入面 ✓（见 {@code HotbarRenderComponent} 的边界说明）。
     * <p><b>阶段 13 · t123（欠账 A 后半 · 本字段上方的那一项已不存在）</b>：原
     * `frameworkLevel.FactionComponent` 的**每实例持有已删除** ✗ —— 阵营不再是"每实例一个组件"的状态，
     * 而是**聚合根**（{@link Role}）上的一个声明值 ✓ ⇒ 读侧一律经 {@code roleInfo} 服务面
     * （{@link #roleInfo()}）、写侧经 {@link #setFaction} / {@link #resetFaction}（两者都转调到聚合根）。
     * <p><b>【已作废】旧口径原文</b>（阶段 10 · t63 原文，逐字保留）：本处列的"能量 / SanTE / 阵营的真值
     * 都在组件里、本类不持有这些字段" ✗ —— 阶段 13 · t123 起 **faction 一项例外** ✓。
     */
    private final HotbarRenderComponent hotbarRenderComponent;

    /** 服务组件在**实例容器**里的 id（与 {@code ComponentServices} 的成员名同形 ⇒ 便于逐项对照）。 */
    private static final String SERVICE_ID_ENERGY = "energy";
    private static final String SERVICE_ID_SANTE = "sante";
    private static final String SERVICE_ID_VITALS = "vitals";
    private static final String SERVICE_ID_BUFFS = "buffs";
    private static final String SERVICE_ID_TIMERS = "timers";
    //阶段 13 · t123（欠账 A 后半）：原 `SERVICE_ID_FACTIONS`（"factions"）**已删除** ✗ ——
    //它是 FactionComponent 的容器 id，随组件本体一并删除（阵营不再是容器里的服务组件 ✓）。
    //阶段 10 · t73 Part A：`SERVICE_ID_DAMAGE` 已删除 —— 原 DamageComponent 并入 VitalsComponent
    //⇒ 框架服务组件由 7 个减为 6 个（`damage` 不再是独立服务组件；四个伤害原语改由生命组件承载）。
    //阶段 12 · t86：**物品渲染组件**的容器 id（框架级服务组件之一；与渲染器分工见 HotbarRenderComponent）
    private static final String SERVICE_ID_HOTBAR_RENDER = "hotbarRender";


    //实例是否仍然有效：clear() 之后置为 false，组件里的延时任务用它做"实例已失效"守卫
    private boolean valid = true;

    /**
     * **第二相是否已执行**（阶段 10 · t64 · P6 两阶段构造）：构造器只做不可见的事，
     * 全部玩家可见的副作用在 {@link #activate()} 里，且**至多发生一次**。
     */
    private boolean activated = false;

    //阶段 13 · t105：**框架侧冷却表已删除** ✗ —— 冷却状态与判断归组件实例（`ActiveComponent` 的实例字段）；
    //丢弃物品时，mc服务端会发送挥手数据包，这回导致触发左键交互事件，使用这个标记变量阻止按q时触发左键逻辑
    private boolean isDropping = false;
    public boolean isDropping() { return isDropping; }
    public void setDroppingState(boolean dropping) { isDropping = dropping; }

    private final Map<String, MainWeapon> mainWeaponMap = new HashMap<>();
    private final Map<String, Skill> skillMap = new HashMap<>();
    private final Map<String, PassiveSkill> passiveMap = new HashMap<>();

    //平台上下文（调度/日志/键/阵营查询）
    private final RolesContext platform;

    // ───────── 阶段 10 · t66：运行期组件异常的**故障隔离**状态（用户新设计） ─────────
    //**同实例只隔离一次**（A7）：一旦置 true，后续异常只记日志、不递归隔离；派发循环也就地退出。
    private volatile boolean quarantined = false;
    //拆卸中（clear() 起）：此时组件抛异常**只记日志**，不触发隔离 —— 实例本来就在被销毁，
    //把一次正常清角色里的 stop() 异常播成"某角色已停用"是假警报 ✗。
    private boolean tearingDown = false;
    //本次**派发边界内**待执行的隔离请求（首个异常胜出 ⇒ 日志点名的组件稳定）；
    //真正的四步在遍历窗口**之外**执行（窗口内禁止增删 ⇒ 见 withinIterationWindow）。
    private QuarantineRequest pendingQuarantine;
    //隔离的**对外处置**（日志/提醒/清空角色）由 RoleManager 在实例构造成功后绑定（它才知道 map 与玩家归属）
    private QuarantineHandler quarantineHandler;
    //裁定④ 的动态删除路径入口（"移除全部组件"走它 ⇒ 与运行期增删同一条路径 + P2 删除守卫）
    private final ComponentLookup componentLookup;

    private Task updateTask;

    private final NamespacedKey roleHealthModifierKey;

    //组件注册表（组件集合 + 每组件资源表 + getComponent 查找）与统一渲染器（**唯一渲染者**）
    private final ComponentRegistry componentRegistry = new ComponentRegistry();
    private final HotbarRenderer hotbarRenderer = new HotbarRenderer(this);
    /**
     * **聚合根只读服务面**（阶段 13 · t90 建立；**t123 起成为阵营读取的唯一入口** ✓）：
     * {@link Role} 的只读视图（id / 描述 / **阵营** / 两个行为）。
     * <p><b>本卡（t123 · 欠账 A 后半）</b>：原 `RoleInstance#factionComponent()` 读口与它的
     * **读侧视图**（{@code getFaction} + 三个 {@code isHostileTo}）**已删除** ✗ ⇒ 阵营读取一律走本端口 ✓
     * （组件侧 = {@code svc().roleInfo()}，框架侧 = {@link #roleInfo()}）。
     * <p><b>只读，不带写面</b>（R-1）：写入仍在**聚合根** {@link Role#setFaction} / {@link Role#resetFaction} ✓。
     */
    private final RoleInfo roleInfo = new RoleInfoImpl(this);
    /**
     * **唯一的方法引用持有者**（阶段 5 判据 C-03）：供三条"程序化刷新"路径共用 ——
     * 冷却到点（启动时预约）、每 tick 到期扫描、显式结束冷却（S3）。
     * 它们都**不**额外产生裸直呼点（阶段 5 判据 C-02 的计数守恒：5 处直呼 + 1 处方法引用）。
     * <p><b>阶段 12 · t86</b>：本 Runable 的实现从"直呼渲染器"改为经**渲染组件**的
     * {@code requestRepaint()} 转调 ⇒ 框架自身置脏与组件请求**收敛到同一条通道** ✓
     * （渲染组件再把置脏交给 {@code hotbarRenderer::markDirty} 这个 sink）✓。
     */
    private final Runnable markHotbarDirty = this::requestHotbarRepaint;
    /**
     * **组件侧"请求重绘"的唯一入口**（阶段 8 · t46 建立；**阶段 12 · t86 收进渲染组件**）。
     * <p><b>t86 的取代动作（不静默改写）</b>：旧形态是 <i>「组件实现 {@code RepaintRequestable}，
     * 框架在装配期把 {@code RepaintRequester} 绑给它」</i> —— 那是**两条并存的重绘通道** ✗
     * （组件侧一条 + 框架侧 {@link #markHotbarDirty} 一条）。t86 把它们**收敛为一条**：
     * 组件与框架**都**经 {@link HotbarRenderComponent#requestRepaint()} ✓，
     * 由该组件把置脏交给渲染器（{@code bindRepaintSink}）✓ ⇒ **禁两套并存** ✓。
     * 旧的两个类型（{@code RepaintRequestable} / {@code RepaintRequester}）**已删除** ✓。
     * <p>边界逐字未变：它**只置脏**、不写物品 ⇒ 组件**只能请求、不能写** ✓，
     * 「空闲 tick 零 setItem」与"写入仍由帧末 flush 完成"两条口径不变 ✓。
     */
    private final Map<RoleComponent, ComponentServices> componentServices = new HashMap<>();

    //T-2 ①③：迁移标记已删 —— 所有组件**无条件**走新管道（单一入口 = handleCast/handleAttack）。

    public RoleInstance(Player player, Role role, RolesContext platform){
        this.player = player;
        this.role = role;
        this.platform = platform;
        this.roleHealthModifierKey = platform.keys().of("role_health_modifier");

        // ── 阶段 10 · t63：**服务的持有者先于角色组件存在** ────────────────────────────────
        //① buff 管理器（原在 freeze() 之后构造）：它对实例的引用只在方法体里使用 ⇒ 提前构造零行为差异；
        //  它的**持有者**现在是 buff 组件（本类只保留读口 getBuffManager() 供兼容）。
        BuffManager buffManager = new BuffManager(player, this);

        //② 能量 / SanTE：真值（current）与上限（max，= 角色模板的声明值）都在组件里；
        //   "置脏 + 事件发布"这两件平台事由容器以 ChangeSink 注入 ⇒ 组件本身不需要任何旧端口。
        this.energyComponent = new EnergyComponent(SERVICE_ID_ENERGY, createServices(SERVICE_ID_ENERGY),
                role.getMaxEnergy(),
                (previous, current, max) -> {
                    //触点④（能量单一入口）：**无条件**置脏（不做"跨阈值才置脏"的优化 —— 那属阶段 5 性能项）
                    hotbarRenderer.markDirty();
                    Bukkit.getPluginManager().callEvent(new EnergyChangeEvent(player, this, previous, current, max));
                });
        this.santeComponent = new SanTEComponent(SERVICE_ID_SANTE, createServices(SERVICE_ID_SANTE),
                role.getMaxSanTE(),
                (previous, current, max) -> {
                    Bukkit.getPluginManager().callEvent(new SanTEChangeEvent(player, this, previous, current, max));
                    //I-15：容器**直派**（不再经 RoleEventListener 转发；上一行的事件发布保持不变）
                    dispatchSanTEChange(previous, current);
                });

        //③ 生命：clamp 策略的唯一实现在组件里（状态 = Bukkit 玩家属性，属外部平台状态）
        this.vitalsComponent = new VitalsComponent(SERVICE_ID_VITALS, createServices(SERVICE_ID_VITALS));

        //④ buff：记账表（BuffManager）与药水账本（原 appliedPotionTypes 字段）都归它持有
        this.buffComponent = new BuffComponent(SERVICE_ID_BUFFS, createServices(SERVICE_ID_BUFFS), buffManager);

        //⑤ 计时：任务的**创建**在组件里、**登记归属**按请求者；资源**存储**仍是容器的每组件资源表
        //  （TaskSink 就是 ComponentRegistry#track / #cancelAll ⇒ 既有回收机制一条都不改）
        this.timerComponent = new TimerComponent(SERVICE_ID_TIMERS, createServices(SERVICE_ID_TIMERS),
                platform.scheduler(), new TimerComponent.TaskSink() {
            @Override
            public void track(RoleComponent requester, Task task) {
                componentRegistry.track(requester, task);
            }

            @Override
            public int cancelAll(RoleComponent requester) {
                return componentRegistry.cancelAll(requester);
            }
        });

        //⑥ 阵营（阶段 13 · t123 · 欠账 A 后半）：**原 FactionComponent 已整体删除** ✗ ——
        //   阵营的真值就是聚合根 `Role` 的 `faction` 字段（构造期由描述符写入）✓；
        //   关系表仍留平台（静态数据 ⇒ 外部单例许可，不进依赖图）：`RoleInfoImpl` / 平台自带 lookup 直接读它。
        //   ⇒ 本相**不再构造任何阵营组件**，也不再登记任何阵营服务组件（阵营不是容器里的状态拥有者 ✓）。

        //⑦ 伤害：四个原语（阶段 10 · t73 Part A 起由 VitalsComponent 承载 ⇒ 伤害与生命只有一个持有者 ✓）
        //   —— 原独立 DamageComponent 已删除，不再单独构造。

        //⑧ 物品渲染（阶段 12 · t86）：**意图面**收进组件；置脏通道在装配期绑回渲染器 ⇒
        //   「组件只能请求、不能写」逐字保留（唯一写点仍是 hotbarRenderer 的帧末 flush）✓
        this.hotbarRenderComponent = new HotbarRenderComponent(SERVICE_ID_HOTBAR_RENDER,
                createServices(SERVICE_ID_HOTBAR_RENDER));
        this.hotbarRenderComponent.bindRepaintSink(hotbarRenderer::markDirty);

        //裁定④ 的**动态删除路径**入口（隔离时"移除全部组件"走它 ⇒ 与运行期增删同一条路径 + P2 守卫）
        this.componentLookup = new ComponentLookupImpl(componentRegistry, this::createServices, platform.logger());

        initComponents();

        //服务组件登记进**实例容器**（**不进 Role 模板** ⇒ 装配表/冻结 CELLS 逐格不变）
        //（阶段 12 · t86 起为 7 个：6 个原服务组件 + 物品渲染组件；
        //  阶段 13 · t123 起为 **6 个**：原"阵营"一项已随 FactionComponent 删除 ✗）
        registerServiceComponents();

        //装配完成 → 冻结注册表（此后 getComponent 才合法）
        componentRegistry.freeze();

        // ── 第一相到此结束（阶段 10 · t64 · P6 两阶段构造）───────────────────────────────
        //构造器**只做不可见的事**：装配（组件 / 服务集 / 窄类型视图 / 服务组件登记）+ 注册表冻结
        //（依赖检查在 `Role#createInstance` 里、本构造器之前，t54 已有）。
        //**玩家可见**的副作用全部在第二相 {@link #activate()}：写生命修饰符 / 设置生命 / 生命周期广播 /
        //启动 ticker / 构造期同步首刷；`BuffManager` 的每 tick 更新同样推迟到那一相
        //（⇒ **构造期不创建任何任务**，构造失败不留下永久运行的 ticker）。
        //**为什么**：构造失败（含**非依赖类**的组件构造异常）必须在玩家身上**零痕迹**，
        //`RoleManager#selectRole` 才可能"先构造成功、再清旧角色"（本卡要收口的那条残留）。
    }

    /**
     * **框架自身的置脏入口**（阶段 12 · t86）：经**物品渲染组件**转调 ⇒ 与组件侧请求
     * **收敛到同一条通道** ✓（旧的独立 {@code markHotbarDirty → hotbarRenderer::markDirty} 直连已废止 ✗）。
     * <p>用方法引用（{@code this::requestHotbarRepaint}）而不是 lambda：字段初始化式里**不能**读
     * 尚未在构造器里赋值的 final 字段（Java 的 definite-assignment 规则）⇒ 方法引用把读取推迟到调用时 ✓。
     */
    private void requestHotbarRepaint() {
        hotbarRenderComponent.requestRepaint();
    }

    /**
     * **第二相：激活**（阶段 10 · t64 · P6 两阶段构造）—— 把原先写在构造器里、**有玩家可见副作用**的
     * 那一段原样搬到这里：语句、顺序、可见时机与迁移前**逐字一致**，唯一差别是**调用时机**
     * （由调用方在"新实例已构造成功、旧角色已清理"之后调用）。
     *
     * <p><b>为什么必须拆两相</b>：旧写法是"先 {@code clear()} 旧角色、再裸构造新实例"⇒ 构造一旦失败，
     * 玩家**先丢角色**。而字面意义的"先构造后清理"又会踩 {@code t54} 实测的三条约束 ——
     * 旧实例的 {@code clear()} 会 ① 按**共享 key** 移除新实例刚加的 {@code role_health_modifier}
     * （最大生命掉回 20）② 清空新实例刚渲染的热键栏 ③ 移除同类型药水。那三条之所以成立，
     * 正是因为**旧写法的构造期就已经把这些可见状态写下去了** ✗；拆出本相后，"清旧"发生在
     * **新实例写任何可见状态之前** ⇒ 三条约束全部落空（逐条对照见交付说明的 A3 一节）✓。
     *
     * <p><b>幂等</b>：重复调用只生效一次（{@code activated} 护栏）—— 否则会重复启动 ticker、
     * 重复广播生命周期（{@code awake()} 约定幂等，但 {@code start()} 不约定）。
     * 已 {@link #clear()} 的实例（{@code valid == false}）**不得**再激活，直接返回。
     *
     * <p><b>异常</b>：本相**可能**抛（组件在 {@code awake()} / {@code start()} 里抛，
     * 或玩家属性 / 热键栏写入失败）⇒ 调用方**必须**自行 try/catch，见
     * {@code manager/RoleManager#selectRole} 的"激活失败"分支（该分支的残留已在交付说明里如实申报）。
     */
    public void activate(){
        if(activated) return;
        if(!valid) return;
        activated = true;

        //① buff 记账表的每 tick 更新（原在构造期由 `BuffManager` 构造器启动，本卡移到这里）：
        //  **提交顺序与迁移前相同** —— 先于实例 ticker 提交 ⇒ 同一 tick 内先跑记账、再跑组件 update。
        buffComponent.manager().startUpdater();

        //② 设置生命
        AttributeModifier am = new AttributeModifier(
                roleHealthModifierKey,
                role.getMaxHP() - 20,
                AttributeModifier.Operation.ADD_NUMBER

        );
        player.getAttribute(Attribute.MAX_HEALTH).removeModifier(am);
        player.getAttribute(Attribute.MAX_HEALTH).addModifier(am);
        player.setHealth(getMaxHealth());

        //③ 生命周期时序：全部组件创建完成 -> awake全部 -> start全部 -> 启动ticker -> 渲染热键栏
        triggerLifecycleAwake();
        triggerLifecycleStart();

        updateTask = platform.scheduler().runRepeating(
                this::triggerUpdate,
                1L,
                1L
        );

        //④ 阶段 5 · 4.4：构造期**同步首刷一次**（与迁移前的可见时机逐字一致 = 选角色瞬间热键栏即就绪、零延迟）；
        //首个 tick 因置脏初值为 true 还会再写一次同内容（不可见、且此后空闲 tick 不再写）。
        hotbarRenderer.render();
    }

    //平台上下文：阶段 2 的组件取用入口（阶段 4 起逐批收窄；阶段 13 · t106 后服务集只剩三个成员）
    public RolesContext rolesContext() { return platform; }

    //统一渲染器：阶段 5 · 4.4 起为**唯一渲染者**（写物品只发生在 core/hotbar 内）
    public HotbarRenderer hotbarRenderer() { return hotbarRenderer; }

    //组件注册表（框架内部：装配、资源兜底、getComponent 查找）
    public ComponentRegistry componentRegistry() { return componentRegistry; }

    /**
     * **角色信息服务面的框架侧读口**（阶段 13 · t123 新增；取代原 `factionComponent()` 读口 ✗）：
     * 阵营读取与两个行为（{@code isHostile} / {@code hasEnemyInRange}）都经它 —
     * 与组件侧拿到的 {@code svc().roleInfo()} **同一个实例**（{@code createServices} 交出去的就是它 ✓）。
     * <p><b>只读</b>：本端口不带写面（R-1）✓；写侧在聚合根上（{@link Role#setFaction} / {@link Role#resetFaction}）。
     */
    public RoleInfo roleInfo() { return roleInfo; }

    // ───────── 阶段 10 · t63：服务组件的读口（组件侧取用与取证都走这里） ─────────
    //它们是**每实例一个**的框架服务（不进 Role 模板），登记在实例容器里；这里给出强类型读口
    //（阶段 13 · t106 起这是组件侧取服务组件的**唯一**入口 —— 服务集不再转发它们 ✗）。
    //（阶段 13 · t123：原"阵营"一项的读口 `factionComponent()` **已删除** ✗ —— 组件本体没了；
    //  阵营读取改走上面的 `roleInfo()` 服务面 ✓）

    public EnergyComponent energyComponent() { return energyComponent; }

    public SanTEComponent santeComponent() { return santeComponent; }

    public VitalsComponent vitalsComponent() { return vitalsComponent; }

    public BuffComponent buffComponent() { return buffComponent; }

    public TimerComponent timerComponent() { return timerComponent; }

    /**
     * 把**框架级服务组件**登记进**实例容器**（阶段 10 · t63）。
     * <p><b>不进 {@code Role} 模板</b> ⇒ {@code role.getComponents()} = 装配表 = 冻结 CELLS **逐格不变** ✓；
     * 登记后它们可被 {@code svc().components().get(EnergyComponent.class)} / {@code getId} 取到
     * （= 用户计划里"角色实例 = 组件的容器"的落点）。
     * <p><b>阶段 13 · t123</b>：清单为 **6 个**（能量 / SanTE / 生命 / buff / 计时 / 物品渲染）——
     * 原"阵营"一项**已删除** ✗（阵营 = 聚合根上的声明值，不是容器里的服务组件 ✓）。
     * <p><b>id 冲突</b>（角色模板里恰好也有同名组件）：角色组件优先（模板是产品内容），服务组件**跳过登记**
     * 并记一条 WARNING —— 它仍由字段持有、强类型读口仍能取到它 ⇒ **能力不受影响**（只是容器按 id/类型查不到它）。
     */
    private void registerServiceComponents() {
        registerServiceComponent(energyComponent);
        registerServiceComponent(santeComponent);
        registerServiceComponent(vitalsComponent);
        registerServiceComponent(buffComponent);
        registerServiceComponent(timerComponent);
        registerServiceComponent(hotbarRenderComponent);
    }

    private void registerServiceComponent(RoleComponent component) {
        if (componentRegistry.declarationOf(component.getId()) != null) {
            platform.logger().warning("Role '" + role.getId() + "' already has a component with id '"
                    + component.getId() + "'; the framework service component is kept as an instance field "
                    + "but is NOT registered in the container (lookup by id/type will not find it).");
            return;
        }
        componentRegistry.register(component);
    }

    /**
     * **按类型取本实例内的组件**（阶段 10 · t55 · 冻结件 §4.6 的"按类型查找"读口；C-04）。
     * <p>与 {@code componentRegistry().getByType(...)} 同源（同一实现点），语义：
     * 返回**添加顺序第一个**可赋值给 {@code type} 的组件（父类/接口查询命中子类实例）；未注册 ⇒ {@code null}；
     * **装配完成之前**调用 ⇒ 抛 {@code IllegalStateException}（既有装配期护栏）。
     * <p><b>调用点申报</b>：仓内 0 调用点 —— 它是容器的公开读口（与 {@code hotbarItemOf(id)} 同类），
     * 消费者是**后续卡的依赖注入路径**与仓外探针（组件侧取组件一律走
     * {@code RoleComponent#getComponent(Class)} ⇒ {@code svc().components().get(...)}）。
     * <p><b>阶段 10 · t67</b>：本口**只增不改**（语义按真实行为写明 = "添加顺序第一个"）；
     * 新增的 {@link #getAllByType(Class)} 是它的"全部"版本。
     */
    public <T> T getByType(Class<T> type) {
        return componentRegistry.getByType(type);
    }

    /**
     * **按类型取本实例内的全部组件**（阶段 10 · t67 · 用户新路线图第 1 条新增的公开读口）：
     * 返回全部可赋值给 {@code type} 的组件，顺序 = **添加顺序**；无人符合 ⇒ **空列表**（不是 null）。
     * <p>与 {@link #getByType(Class)} 同一条件、同一顺序 ⇒ 其首元素恒等于 {@code getByType(type)} ✓；
     * 空列表 ⟺ {@code getByType(type) == null} ✓。
     * <p>**调用点申报**：仓内 0 调用点（与 {@code getByType} 同类：公开读口，消费者是后续卡与仓外探针）。
     * **装配完成之前**调用 ⇒ 抛 {@code IllegalStateException}。
     * <p>类型形参**无上界**（t67）⇒ 支持**接口**查询；返回**不可变**列表。
     */
    public <T> java.util.List<T> getAllByType(Class<T> type) {
        return componentRegistry.getAll(type);
    }

    /**
     * **调试用读口**：取某组件一对一的服务集（调试探针按 id 定位组件用，例如 {@code /role debug sched}
     * 的组件链实测取请求者与计时组件）。
     * <p><b>阶段 13 · t106</b>：服务集只剩三个成员（{@code self} / {@code components} / {@code roleInfo}）
     * ⇒ 本方法不再是"取端口实例"的手段（冷却自管理的冒烟入口已随其端口一并删除 ✗）。
     */
    public ComponentServices servicesOf(String componentId){
        RoleComponent component = componentRegistry.getById(componentId);
        return component != null ? componentServices.get(component) : null;
    }

    /**
     * 组件与其**一对一**的服务集（阶段 13 · t106 后为三个成员：玩家实例面 / 组件查找 / 聚合根只读面）。
     * <p><b>阶段 8 前置</b>：冷却表已合并为**单一命名空间** ⇒ 本方法**不再需要 kind**
     * （合并前"按 kind 选表"的构造期绑定，是"删 kind 枚举"的硬阻塞）。
     * 阶段 8 本卡把 kind 枚举整个删掉 ⇒ 注册处也不再承载任何"权威种类"。
     * <p><b>阶段 13 · t106</b>：{@code componentId} 形参**保留**（动态添加路径的服务集工厂签名不变：
     * {@code ComponentLookupImpl} 吃的就是 {@code Function<String, ComponentServices>}），
     * 但服务集本身**不再按 id 绑定任何资源** —— 资源归属一律由组件自己按请求者登记 ✓。
     */
    private ComponentServices createServices(String componentId){
        return new ComponentServices(
                new SelfImpl(this),
                //阶段 10 · t55：组件服务 = 查找 + **动态添加** —— 服务集工厂传进去，运行期新增的组件
                //与装配期组件走**同一条**构造路径（同一服务集口径）；日志用于 P2 的
                //"拒绝删除被依赖组件"留痕（点名被删组件 / 阻止者 / 缺的类型）
                new ComponentLookupImpl(componentRegistry, this::createServices, platform.logger()),
                //阶段 13 · t90（A2）：角色信息服务（聚合根只读面）；
                //阶段 13 · t123：构造点仍是**这一处** ✓ —— 本类持有同一实例并给出框架侧读口 {@link #roleInfo()}
                //（组件侧 `svc().roleInfo()` 与框架侧 `instance.roleInfo()` = **同一个实例** ✓）。
                roleInfo
        );
    }

    /**
     * 组件创建之后的**紧邻登记**（五条件①④）：服务集与组件一对一进表，组件同时进注册表。
     * 服务集是在**构造期**交给组件的（{@code factory.create(id, services)}）⇒ 不存在"创建后尚未注入"的窗口。
     * <p><b>阶段 10 · t55（P2）</b>：登记时把装配条目里的**依赖声明**（提供类型 + 必需依赖）一并交给注册表
     * —— 它是"删除前算反向依赖"的唯一数据来源，且**从描述符声明算出**（不手工维护）。
     */
    private void registerCreated(RoleComponent component, ComponentServices services, Role.ComponentEntry entry){
        componentServices.put(component, services);
        componentRegistry.register(component, new ComponentRegistry.Declaration(
                component.getId(), entry.getProvidedType(), entry.getRequiredTypes()));
    }

    // ───────── 阶段 4：施放 / 攻击管道（新旧路径并存；开关默认旧路径 ⇒ 行为不变） ─────────

    /**
     * 新路径施放入口。返回 {@code true} = 本次已由管道处理（旧路径不再插手）；
     * 开关关闭、或该 id 尚未迁移到 {@link RoleComponent} 时返回 {@code false}，交回旧路径。
     */
    public boolean handleCast(CastTrigger trigger, Player caster){
        if(caster == null) return false;

        ItemStack item = caster.getInventory().getItemInMainHand();
        String id = Skill.Utils.getSkillId(item);
        if(id == null) id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
        //阶段 13 · t108：判据 = **物品支持组件本身**（裁定⑤ 的"吸收"落点）—— 原能力接口已随吸收删除 ✗；
        //接受集**逐字不变**（那个接口的唯一实现者就是本类），本处只用到 onCast。
        if(!(component instanceof ActiveComponent active)) return false;

        //冷却自管理（D1）：框架**不再**代启动冷却 —— 组件在施放成功处自行 startCooldown()（阶段 13 · t105：状态归组件、框架只转问）；
        //声明值仍是唯一真值来源（4.7/O-13），启动点与启动值都与旧框架代启动逐字一致 ⇒ 可观察行为不变。
        //阶段 10 · t66：**唯一受保护调用**（施放是框架派发边界之一）⇒ 组件抛异常 = 整实例隔离
        guardedCall(component, "onCast", () -> active.onCast(new CastSignal(trigger)));
        //本入口不在遍历窗口内 ⇒ 立即执行待处理的隔离
        runPendingQuarantine();
        //**仍返回 true**：本次已由管道"处理"（组件确实被调用过，只是抛了）⇒ 若返回 false，
        //调用方会回落到旧路径 ⇒ **二次派发**（组件已被隔离，二次派发是新的错误面）✗
        hotbarRenderer.markDirty();
        return true;
    }

    /** 新路径攻击入口（主武器）。语义同 {@link #handleCast}。 */
    public boolean handleAttack(Player victim, Player attacker){
        if(victim == null || attacker == null) return false;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        String id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
        //阶段 13 · t108：同 handleCast —— 判据 = **声明 onAttack 的那个组件**（原能力接口已随吸收删除 ✗；
        //接受集逐字不变：那个接口的唯一实现者就是本类）。
        if(!(component instanceof MainWeapon hook)) return false;

        //冷却自管理（D1）：框架不再代启动冷却（同 handleCast）
        //阶段 10 · t66：唯一受保护调用（攻击同属派发边界）
        guardedCall(component, "onAttack", () -> hook.onAttack(new AttackSignal(victim)));
        runPendingQuarantine();
        hotbarRenderer.markDirty();
        return true;
    }

    /**
     * 组件初始化（阶段 6 · 统一装配）：**只遍历 {@code role.getComponents()} 一次** ——
     * 遍历顺序 = `Builder.add*` 的调用顺序 = **纯注册序**（旧的三段遍历
     * 「技能 → 被动 → 主武器」已删除，见交付说明的派发序申报）。
     * <p>阶段 8：**没有任何"种类"值**需要传递或读取（kind 枚举已删）；
     * **服务集构造也不再需要 kind**（阶段 8 前置：冷却表已合并为单一命名空间）。
     */
    private void initComponents(){
        for(Map.Entry<String, Role.ComponentEntry> entry : role.getComponents().entrySet()){
            String componentId = entry.getKey();

            ComponentServices services = createServices(componentId);
            RoleComponent component = role.createComponent(componentId, services);
            if(component == null) continue;

            //阶段 12 · t86：组件侧不再被绑定一条**独立**的重绘通道 ✗ —— 需要请求重绘的组件改为
            //经**渲染组件**这一条通道：`svc().components().get(HotbarRenderComponent.class)`
            //（或按 id "hotbarRender"）拿到它，再调 requestRepaint() ✓。
            //⇒ 框架侧（markHotbarDirty）与组件侧**收敛到同一条通道**（禁两套并存 ✓）；
            //  旧 `RepaintRequestable` / `RepaintRequester` 两条通道**已删除** ✓。
            //绑定时机的纪律不变：渲染组件本身在构造器里就已 bindRepaintSink（早于任何 awake/start）✓。
            //阶段 13 · t118：原"创建后绑定"的**装配期落点已整体删除** ✗（它唯一的绑定目标是计时端口，
            //  端口面 t106 起已清理 ⇒ 该调用早已是 no-op）—— 状态一律归**组件实例本身**，不需要任何绑定动作。

            //旧窄类型视图（供既有公共访问器使用）：按**具体类型**归位，不按任何"种类"猜测
            if(component instanceof Skill skill) skillMap.put(componentId, skill);
            if(component instanceof MainWeapon weapon) mainWeaponMap.put(componentId, weapon);
            if(component instanceof PassiveSkill passive) passiveMap.put(componentId, passive);

            registerCreated(component, services, entry.getValue());
        }
    }


    /** buff 记账表（**持有者 = buff 组件**；本读口保留以兼容既有调用点）。 */
    public BuffManager getBuffManager() { return buffComponent.manager(); }





    //技能相关
    /** 就绪判定（**单一冷却命名空间**的视图）：无条目/已到期 ⇒ {@code true}。 */
    public boolean isSkillReady(String skillId){
        return isCooldownReady(skillId);
    }

    public int getRemainingSkillCooldownTicks(String skillId){
        return remainingCooldownTicks(skillId);
    }

    public float getRemainingSkillCooldownSeconds(String skillId){
        return getRemainingSkillCooldownTicks(skillId) / 20f;
    }


    //技能释放
    public boolean castSkillLeftClick(String skillId, Player caster){
        if(runComponentPipeline(CastTrigger.LEFT_CLICK, caster)) return true;
        //T-1 (4)：旧派发入口（onLeftClick）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(skillMap.get(skillId) == null){
            caster.sendMessage(Component.text("unknown skill!"));
        }
        return false;
    }

    public boolean castSkillRightClick(String skillId, Player caster){
        if(runComponentPipeline(CastTrigger.RIGHT_CLICK, caster)) return true;
        Skill skill = skillMap.get(skillId);
        if(skill == null){
            caster.sendMessage(Component.text("unknown skill!"));
        }
        //T-2c：T-1 的未迁移回退已删（组件侧一律走新管道）；可见刷新由 handleCast 的置脏 + 帧末 flush 保证。
        return false;
    }

    public boolean castSkillQDrop(String skillId, Player caster){
        if(runComponentPipeline(CastTrigger.DROP, caster)) return true;
        //T-1 (4)：旧派发入口（onDrop）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(skillMap.get(skillId) == null){
            caster.sendMessage(Component.text("unknown skill!"));
        }
        return false;
    }

    /** T-2 (3)：**纯委派**（迁移标记已删）—— 组件一律走新管道，单一入口 = {@link #handleCast}。 */
    private boolean runComponentPipeline(CastTrigger trigger, Player caster){
        return handleCast(trigger, caster);
    }


    //主武器相关
    public MainWeapon getMainWeaponById(String weaponId){
        return mainWeaponMap.getOrDefault(weaponId, null);
    }

    public boolean isMainWeaponReady(String weaponId){
        return isCooldownReady(weaponId);
    }

    public int getRemainingMainWeaponCooldownTicks(String weaponId){
        return remainingCooldownTicks(weaponId);
    }

    public float getRemainingMainWeaponCooldownSeconds(String weaponId){
        return getRemainingMainWeaponCooldownTicks(weaponId) / 20f;
    }

    // ───────── 冷却自管理（阶段 4 追补 D1/D2/D3/D4）· 阶段 8 前置：**单一命名空间** ─────────

    /** 就绪判定（阶段 13 · t105：**转问组件** —— 框架不持有冷却状态 ✓）：无冷却能力 ⇒ 恒就绪 ✓。 */
    public boolean isCooldownReady(String componentId){
        RoleComponent component = componentRegistry.getById(componentId);
        return !(component instanceof ActiveComponent active) || !active.isCoolingDown();
    }
    /** 剩余刻（阶段 13 · t105：**转问组件**）：无冷却能力（无冷却这回事）⇒ {@code 0} ✓。 */
    public int remainingCooldownTicks(String componentId){
        RoleComponent component = componentRegistry.getById(componentId);
        return component instanceof ActiveComponent active ? active.remainingCooldownTicks() : 0;
    }



    //阶段 13 · t118（代码卫生）：原先这里的三个成员 —— 按 id 解析的**回落入口**、那条口径的
    //  **纯判定函数**（t84 的 F-1/F-2/F-3 修复点）、以及"创建后绑定"的**空转落点** —— 已**整体删除** ✗。
    //理由（逐条现算）：① 三者的生产消费者 **0 个**（端口面 t106 清理后，"端口按 id 回落"这条口径再无使用者）；
    //  ② 绑定落点自 t106 起就是 **no-op**（唯一绑定目标 = 计时端口，已删）；
    //  ③ 留着它们会让"状态面按实例"看起来仍由框架兜底 —— 而事实是**状态一律归组件实例本身** ✓。
    //★ 唯一仍在的同类语义 = 组件自己的 `start()` 里**按需/一次解析强类型组件**（R-4 ✓），与本处无关。








    //释放主武器技能
    public boolean castMainWeaponLeftClick(String weaponId, Player caster){
        if(runComponentPipeline(CastTrigger.LEFT_CLICK, caster)) return true;
        //T-1 (4)：旧派发入口（onLeftClick）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(mainWeaponMap.get(weaponId) == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
        }
        return false;
    }

    public boolean castMainWeaponRightClick(String weaponId, Player caster){
        if(runComponentPipeline(CastTrigger.RIGHT_CLICK, caster)) return true;
        //T-1 (4)：旧派发入口（onRightClick）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(mainWeaponMap.get(weaponId) == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
        }
        return false;
    }

    public boolean castMainWeaponQDrop(String weaponId, Player caster){
        if(runComponentPipeline(CastTrigger.DROP, caster)) return true;
        //T-1 (4)：旧派发入口（onDrop）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(mainWeaponMap.get(weaponId) == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
        }
        return false;
    }



    //热键栏渲染：阶段 5 · 4.4 起**唯一渲染者 = HotbarRenderer**（写物品只发生在 core/hotbar 内）；
    //本容器只提供查表与状态输入，旧的"更新物品栏 / 更新元数据"方法（连同其两条调用路径）已随本批删除。

    /**
     * 统一渲染器的查表入口：按槽位表里的 id 取可渲染组件（未注册 id ⇒ {@code null}，渲染器跳过该槽位）。
     * <p>**适配点（阶段 6）**：优先取 {@link HotbarPresentable#asHotbarItem()} 的规格视图 ——
     * 这样「只 `extends RoleComponent` + 实现 `HotbarPresentable`」的新式组件同样可被渲染；
     * 旧式实现（自身即 `HotbarRenderComponent.HotbarItem`）原样返回。
     * <p><b>阶段 8</b>：渲染器**不再经本方法取值**（它直接取组件实例调
     * {@code HotbarItemProviding#buildItem()}）；本方法保留为描述符视图的公开访问器，仓内 0 调用点
     * （已申报）。行为分支（技能/主武器）**不再由任何"种类"决定**。
     */
    public HotbarItem hotbarItemOf(String id){
        //阶段 10 · t67（重复 id 的口径，**显式读出**）：本口按 id 解析 ⇒ 重复 id 下取**添加顺序第一个**同 id 者
        //（= 与 getById / getByType 同口径）。运行期追加的同 id 副本**不会**顶替先加的那个 ⇒
        //既有装配表（模板先加）渲染出来的仍是模板那一个 ⇒ 冻结外观逐字不变 ✓。
        RoleComponent component = componentRegistry.getById(id);
        if(component == null) return null;
        if(component instanceof HotbarPresentable presentable) return presentable.asHotbarItem();
        return component instanceof HotbarItem item ? item : null;
    }

    //清除主武器，技能占用的快捷栏
    public void clearHotbar(){
        Inventory inv = player.getInventory();
        for(int i = 0; i < 9; i++){
            ItemStack item = inv.getItem(i);
            if(Skill.Utils.isSkillItem(item) ||
                    MainWeapon.Utils.isMainWeapon(item)){
                inv.setItem(i, null);
            }
        }
    }

    public static void clearHotbar(Player player){
        Inventory inv = player.getInventory();
        for(int i = 0; i < 9; i++){
            ItemStack item = inv.getItem(i);
            if(Skill.Utils.isSkillItem(item) ||
                    MainWeapon.Utils.isMainWeapon(item)){
                inv.setItem(i, null);
            }
        }
    }

    public Player getPlayer() { return player; }
    public Role getRole() { return role; }



    //生命
    public double getCurrentHealth(){
        return player.getHealth();
    }

    public void setCurrentHealth(double health){
        double clamped = Math.max(0d, Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        player.setHealth(clamped);
    }

    public double getMaxHealth(){
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    public void heal(double amount){
        //阶段 10 · t63：clamp 策略的唯一实现已搬到生命组件（本方法保留为**视图**，调用点一字未动）
        vitalsComponent.heal(amount);
    }

    public void damage(double amount){
        player.damage(amount);
    }
    public void damage(double amount, Entity source){
        player.damage(amount, source);
    }

    public int getMaxEnergy(){
        //阶段 10 · t63：上限的真值在能量组件里（构造期由本容器从角色模板给出）
        return energyComponent.max();
    }

    //能量（**视图**：真值与 clamp/检查扣减的行为都在能量组件里，外部调用点一字未动）
    public int getCurrentEnergy() { return energyComponent.current(); }

    public void setCurrentEnergy(int amount){
        energyComponent.set(amount);
    }

    public void decreaseEnergy(int amount){
        energyComponent.decrease(amount);
    }

    public void increaseEnergy(int amount){
        energyComponent.gain(amount);
    }

    //SanTE（**视图**：真值与 clamp 都在 SanTE 组件里；事件 + 派发由容器以 ChangeSink 注入）
    public int getCurrentSanTE() { return santeComponent.current(); }

    public void setCurrentSanTE(int amount){
        santeComponent.set(amount);
    }

    public void increaseSanTE(int amount){
        santeComponent.gain(amount);
    }

    public void decreaseSanTE(int amount){
        santeComponent.decrease(amount);
    }

    public int getMaxSanTE() { return santeComponent.max(); }

    //实例是否有效：clear() 之后为 false，供组件里的延时任务做失效守卫
    public boolean isValid(){
        return valid;
    }

    //药水施加入口（记账）：施加到本实例玩家身上的效果记入账本，clear() 时只回收账本里的类型（O-7）
    //阶段 10 · t63：**账本的持有者 = buff 组件** ⇒ 本方法保留为视图（BuffManager 与端口都走它）。
    public void applyPotionEffect(PotionEffect effect){
        buffComponent.applyPotionEffect(effect);
    }

    // ───────── 阵营（阶段 13 · t123 · 欠账 A 后半）：**读侧视图已删除** ✗ / 写侧视图保留 ✓ ─────────
    //★ 真值所在：聚合根 `Role` 的 `faction` 字段（原 `FactionComponent.faction` 组件字段已随组件删除 ✗）。
    //★ **读取唯一入口 = `roleInfo` 服务面**：组件侧 `svc().roleInfo()`、框架侧 {@link #roleInfo()} ✓
    //  ⇒ 本类**不再**提供 `getFaction()` / `isHostileTo(...)` 三个读视图 ✗（调用点已改走 RoleInfo：
    //  `internal/api/RoleAPIImpl#getFaction(*2)`、`manager/RoleManager#areHostile(*2)`）。
    //★ 查表语义（`FactionLookup#isHostile`，关系表仍留平台）= 旧 {@code isHostileTo(Faction)} 逐字等价 ✓。
    //★ **阶段 13 · t126**：本类的**写视图也一并删除** ✗（`setFaction(Faction)` / `resetFaction()` 两条 ——
    //  原为 `RoleAPI#setFaction/resetFaction` 的落点）⇒ 那两条 API 已**做空、不再生效** ✗ ⇒ 写视图**无消费者** ✓；
    //  真值写入仍在**聚合根**（{@link Role#setFaction} / {@link Role#resetFaction} ✓，**不由外部直改** ✗）。

    //生命周期触发
    //awake阶段：只解析跨组件依赖并缓存引用，必须幂等且不改动玩家可见状态
    public void triggerLifecycleAwake(){
        if(player == null ) return;

        //阶段 4（B②-c）：为**注册表内组件**广播新基类钩子 awake()。
        //广播给"全部注册组件"：所有组件的新钩子由各组件自行实现（基类提供默认空实现）；
        //按迁移状态分支会引入第二套判据（与硬约束 §20 删总闸的教训同类）。legacy 生命周期扇出已在 T-1 ④ 删除。
        //遍历窗口（阶段 10 · t55）：广播期间**禁止**增/删/插位（注册表在窗口内拒绝写口）
        //阶段 10 · t66：窗口包装 + **唯一受保护调用**（异常 ⇒ 记下隔离请求，窗口关闭后执行四步）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "awake", component::awake);
            }
        });
    }

    //start阶段：开始生效，顺序与awake一致（技能/被动/武器）
    public void triggerLifecycleStart(){
        if(player == null ) return;

        //阶段 4（B②-c）：为注册表内组件广播新基类钩子 start()（顺序 = 注册表顺序；理由同 awake 处注释）
        //遍历窗口（阶段 10 · t55）：同 awake 处；阶段 10 · t66：同 awake 处（唯一受保护调用）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "start", component::start);
            }
        });
    }

    //stop阶段：停止生效（T-1 ④ 后仅剩新钩子广播，按注册表顺序）
    public void triggerLifecycleStop(){
        if(player == null ) return;

        //阶段 4（B②-c）：为注册表内组件广播新基类钩子 stop()。
        //**顺序说明**：新钩子按**注册表顺序**停止（legacy 逆序扇出已在 T-1 ④ 删除）。
        //两者不会对同一组件双触发同一逻辑 —— 迁移后的组件**不再实现 legacy 生命周期接口**，
        //未迁移组件则对基类 stop() 是**默认空实现** ⇒ 任一组件在任一时刻只被"真实逻辑"处理一次。
        //**幂等说明**：若组件在 stop() 里自行取消任务，随后 clear() 的 cancelAllAndClear() 仍会取消其
        //资源表内的同一句柄 ⇒ 重复 cancel 幂等（Task.cancel() 对已取消句柄是 no-op）。
        //遍历窗口（阶段 10 · t55）：同 awake 处
        //阶段 10 · t66：唯一受保护调用 —— 但本方法**只**由 clear() 调用（tearingDown=true）⇒ 其中的异常
        //只记日志、**不**触发隔离（否则一次正常清角色里的 stop() 异常会播成"某角色已停用"= 假警报 ✗）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "stop", component::stop);
                //阶段 10 · t63（A6 · 队长裁定②）：**调用方 stop() ⇒ 其请求的计时全部取消** ✓
                //任务按请求者登记（TimerComponent 的请求者语义）⇒ 这里逐组件回收，堵住
                //"单独 stop() 不清理 ⇒ 生命周期泄漏"的缺口；clear() 末尾的 cancelAllAndClear()
                //仍是最后的兜底（两者幂等，重复 cancel 对已取消句柄是 no-op）。
                timerComponent.cancelAllOf(component);
            }
        });
    }

    //I-14：SanTE 派发的重入护栏状态。哨兵 Integer.MIN_VALUE = 无待发值；
    //派发期间的组件重入写入只记最新值（禁止嵌套），返回后合并补发一次。
    private boolean sanTEDispatching = false;
    private int sanTEPendingValue = Integer.MIN_VALUE;

    /**
     * SanTE 变更的**唯一派发点**（阶段 4 追补 I-15 容器直派 + I-14 重入护栏）。
     * <ul>
     *   <li><b>真变化才派发</b>（{@code pre == now} 直接返回）—— B⑨ 口径不变：SanTE 已为 0 时再扣不再通知组件；</li>
     *   <li><b>禁止嵌套派发</b>：派发期间组件再次改写 SanTE ⇒ 只把最新值记为待发并立即返回；</li>
     *   <li><b>合并成末次一次</b>：本次派发返回后，若期间有重入写入，则对"末次待发值"补发**一次**（中间态被合并掉）；</li>
     *   <li><b>`notified` 机制（**为什么不能拿 `currentSanTE` 比**）</b>：{@code setCurrentSanTE} 是**先写字段、后派发**，
     *       所以派发期间字段值已经等于重入写入的目标值 —— 若把补偿条件写成 {@code currentSanTE != target}，该条件**恒假**，
     *       补偿分支会退化成**不可达死代码**（且给人"已实现合并"的假象）。故这里改用局部 {@code notified}
     *       （初值 = 本次 {@code newSanTE}；每补发一次更新为 {@code target}）与 {@code target} 比较：
     *       **无重入 ⇒ 不补发（与旧行为逐字一致）**；**有重入 ⇒ 恰好补发末次一次**；
     *       循环退出条件 = {@code sanTEPendingValue == Integer.MIN_VALUE}（哨兵 = 无待发值）；</li>
     *   <li>异常隔离走 {@link #guardedCall}（阶段 10 · t66 起：**唯一受保护调用** ⇒ 抛异常 = 故障隔离）。</li>
     * </ul>
     * 现存两个实现者（{@code DefaultSanTEZeroPunishment} / {@code RedDeeplySorrowSkill} 的
     * {@code onSanTEChange}）都**不在钩子内同步写 SanTE**（前者只调度任务、后者只起冷却）
     * ⇒ 护栏在当前组件集下**不可达**，属防御性设施。
     */
    private void dispatchSanTEChange(int preSanTE, int newSanTE){
        if(player == null ) return;
        if(preSanTE == newSanTE) return;

        if(sanTEDispatching){
            sanTEPendingValue = newSanTE;
            return;
        }

        sanTEDispatching = true;
        try{
            //notified = "上一次已广播的 now"。**不要**改成与 currentSanTE 比较：
            //setCurrentSanTE 先写字段、后派发 ⇒ 重入时 currentSanTE 已等于 target，比较恒假 ⇒ 补偿永不发生（死代码）。
            int notified = newSanTE;
            broadcastSanTEChange(preSanTE, newSanTE);

            while(sanTEPendingValue != Integer.MIN_VALUE){
                int target = sanTEPendingValue;
                sanTEPendingValue = Integer.MIN_VALUE;
                if(notified != target){
                    broadcastSanTEChange(notified, target);
                    notified = target;
                }
            }
        }
        finally{
            sanTEDispatching = false;
        }
    }

    /**
     * 把 SanTE 真值变化**派发给订阅者**（顺序 = **订阅先后** = 组件装配序 ✓）。
     *
     * <p><b>阶段 12 · t89 · C2（回退 `t87` 的方向错误）</b>：接受集的决定方式从
     * 「**实现了某个能力接口的组件**」✗ 改为
     * 「**向 {@code SanTEComponent} 订阅过的组件**」✓ —— 依据用户硬规矩 **R-1**：
     * **凡关注点已是组件 ⇒ 不得再为它新增能力接口** ✗（SanTE 的家就是 `SanTEComponent`）。
     * <p>遍历的是**订阅名单**（`santeComponent.forEachSubscriber`），**不是**容器注册表 ✓
     * ⇒ 「谁关心」由**订阅**表达 ✓，不再由接口/继承表达 ✗。
     * <p><b>未改的两件</b>（`t87` 已确立、本卡原样保留 ✓）：**逐个**经 {@code guardedCall}
     * （异常 ⇒ 只隔离抛异常的那一个、其余照常收到 ✓）· 整段在 {@link #withinIterationWindow} 里
     * （⇒ 真四步在窗口关闭后执行 ✓）。
     * <p><b>顺带</b>：派发完再调 {@code broadcastChange} —— 「订阅者派发」与「平台事件发布」都归本组件
     * （后者经它持有的 {@code ChangeSink}）✓。
     */
    private void broadcastSanTEChange(int preSanTE, int newSanTE){
        //遍历窗口（阶段 10 · t55）：可嵌套（update() 广播期间改 SanTE ⇒ 本方法再次进入窗口）
        //阶段 10 · t66：唯一受保护调用（异常 ⇒ 窗口关闭后执行隔离四步）
        withinIterationWindow(() -> {
            santeComponent.forEachSubscriber(subscriber -> {
                //阶段 12 · t89：**订阅判据**（不是接口判据）—— 只通知"订阅过"的组件
                RoleComponent component = subscriber instanceof RoleComponent rc ? rc : null;
                guardedCall(component, "onSanTEChange", () -> subscriber.onSanTEChange(preSanTE, newSanTE));
            });
        });
        //平台侧"事件发布"仍归本组件持有的 ChangeSink —— 与迁移前逐字一致 ✓
        santeComponent.broadcastChange(preSanTE, newSanTE);
    }

    public void triggerUpdate(){
        if(player == null ) return;
        //阶段 10 · t66：已隔离 ⇒ 本实例已死（组件已全部移除、角色已被清空）⇒ 不再派发
        if(quarantined) return;

        //阶段 4（B⑤）：为**注册表内组件**广播新基类钩子 update()。
        //**顺序说明**：按**注册表顺序**遍历（legacy 三段扇出已在 T-1 ④ 删除，无先后关系）；
        //所有组件都对基类 update() 自行实现（基类默认空实现）；本批组件均已迁移（T-2 ① 后无迁移标记）
        //**不再实现 legacy 更新接口** ⇒ 只被这一条路径调用，不会双触发。
        //遍历窗口（阶段 10 · t55）：**update() 广播期间禁止增/删/插位**（"禁止遍历中修改"的落点）
        //阶段 10 · t66：唯一受保护调用 —— 组件在 update() 里抛 ⇒ 整实例隔离（窗口关闭后执行四步）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "update", component::update);
            }
        });

        //阶段 10 · t66：本 tick 里刚被隔离 ⇒ 到期扫描与帧末 flush 都不再对已死的实例做
        if(quarantined) return;


        //阶段 5 · 4.4 帧末 flush（落点 = tick 末尾，紧接组件更新与到期扫描之后）：
        //① 判脏 → ② 写物品（唯一写点 = HotbarRenderer.render）→ ③ 清脏（此顺序不可交换）
        //入口条件并入 B-2（阶段 8 口径，**与冻结口径等价**）：**外观含秒数**的占栏位组件在冷却
        //⇒ 每 tick 至少刷一次（否则技能名里的 " x.xs" 不再逐 tick 递减 = 可见行为变化）。
        //**主武器不让入口因它而变**（冷却名不带秒数 ⇒ 冻结差异；见 hasCoolingTickingComponent）。
        if(hotbarRenderer.isDirty() || hasCoolingTickingComponent()){
            hotbarRenderer.render();
            hotbarRenderer.clearDirty();
            //阶段 12 · t88 · B3：**渲染回调**（读侧、只通知）—— 触发点 = 上面"真正完成一次刷新之后" ✓
            //★ 变化判据：只有**本帧真的改了东西**才回调（consumeChanged 一次性读取并清除）⇒ 无变化的那一帧 **0 次** ✗
            //★ 派发：按能力接口扇出，**逐个**经 deliverHook（内含 guardedCall + 外裹 withinIterationWindow）✓
            //  —— 不在此处裸调组件方法（否则抛异常时隔离四步不会被安排 ✗）
            if(hotbarRenderer.consumeChanged()){
                dispatchHotbarRendered();
            }
        }

    }

    /**
     * **热键栏"本帧真的变了"的通知**（阶段 12 · t88 · B3）：按
     * {@link HotbarRenderComponent.RenderCallback} **扇出**，逐个经 {@link #deliverHook} 调用 ✓。
     * <p><b>只通知、不可否决</b> ✗：回调返回 {@code void} ⇒ 改不了这一帧的渲染结果 ✓。
     * <p><b>为何逐个 deliverHook 而不是把整个循环塞进一次调用</b>：那样首个异常会让"本次派发"里
     * 排在其后的组件**收不到通知** ✗；逐个 ⇒ 只隔离抛异常的那个，其余照常收到 ✓。
     */
    private void dispatchHotbarRendered(){
        for(HotbarRenderComponent.RenderCallback callback : getAllByType(HotbarRenderComponent.RenderCallback.class)){
            deliverHook((RoleComponent) callback, "onHotbarRendered", callback::onHotbarRendered);
        }
    }

    /**
     * B-2 谓词（阶段 8 口径；**与冻结口径等价**）：是否存在**外观依赖活状态**的**占栏位**组件正在冷却。
     * <p>作用 = 让"冷却中每刻至少刷一次"成立：技能名里的 {@code x.xs} 才会逐刻递减
     * （装饰搬进组件之后，框架只剩 {@link CooldownBearing#isCooling()} 这条读口）。
     * <p><b>阶段 8 · t46（A8）：判据由「{@code instanceof Skill}」下沉为「能力」</b> ——
     * {@link HotbarItemProviding#dependsOnLiveState()}。理由（C-15 第三个实例测试）：
     * 旧写法把"外观含秒数"**写死成具体类** ⇒ ① 第三类带倒计时外观的组件加进来**必须改框架文件** ✗；
     * ② 覆写掉秒数外观的 {@code Skill} 子类**仍被每 tick 重绘** ✗。现在框架**不再点名任何具体组件类**，
     * 接受集由组件自报 ⇒ 新组件只加新文件（默认 {@code false}，需要就覆写 {@code true}）✓。
     * <p><b>等价性（与旧判据逐字相同）</b>：{@code core/Skill} 覆写为 {@code true}，主武器与被动保持默认
     * {@code false} ⇒ 既有 16 个组件的真值表不变（两侧对拍见交付说明）。
     * <p><b>边界（A12）</b>：主武器**不得**让帧入口因它而变 —— {@code core/MainWeapon} 家族的冷却名
     * **不带**秒数（冻结差异）⇒ 能力为 {@code false} ⇒ 本谓词对它恒 {@code false} ⇒ 主武器冷却不驱动
     * 每 tick 刷新，与迁移前一致。
     * <p>接受集成立性：占栏位组件全是 {@code HotbarPresentable}（⊇ {@link HotbarItemProviding} ⊇
     * {@link CooldownBearing}）⇒ 被本谓词检查到的组件一定能回答 {@code dependsOnLiveState()} 与 {@code isCooling()}。
     */
    private boolean hasCoolingTickingComponent(){
        for(Map.Entry<String, Role.ComponentEntry> entry : role.getComponents().entrySet()){
            if(!entry.getValue().hasSlot()) continue;
            RoleComponent component = componentRegistry.getById(entry.getKey());
            //「外观是否依赖活状态」= 组件自报的能力（框架**不点名**任何具体组件类）
            if(!(component instanceof HotbarItemProviding providing) || !providing.dependsOnLiveState()) continue;
            if(component instanceof CooldownBearing bearing && bearing.isCooling()) return true;
        }
        return false;
    }

    // ───────── 阶段 10 · t66：**框架调用组件的唯一受保护入口** + 故障隔离（四步） ─────────

    /**
     * 隔离的**对外处置**接入口（由 {@code RoleManager} 在实例构造成功后绑定）：日志点名的四件、
     * 全服/OP 提醒、以及"清空该玩家角色"都在管理器侧（它才知道 {@code playerRoleMap} 与玩家归属）。
     */
    public interface QuarantineHandler {

        void onQuarantined(RoleInstance instance, String componentId, String phase, Throwable failure);
    }

    /** 一条待执行的隔离请求（**首个异常胜出**：同一次派发里的第二个异常只记日志）。 */
    private record QuarantineRequest(String componentId, String phase, Throwable failure) {
    }

    /** 绑定隔离处置（{@code RoleManager#selectRole} 在 {@code activate()} 之前调用 ⇒ 生命周期钩子里的异常也能被隔离）。 */
    public void bindQuarantineHandler(QuarantineHandler handler) {
        this.quarantineHandler = handler;
    }

    /** 本实例是否已被隔离（{@code RoleManager} 用它判断"激活期被隔离"的失败面）。 */
    public boolean isQuarantined() {
        return quarantined;
    }

    /**
     * **框架调用组件的唯一受保护入口**（阶段 10 · t66 · A1）。
     *
     * <p>框架在**每一处**调用组件（{@code awake/start/stop/update/onSanTEChange} 广播 ·
     * {@code onCast}/{@code onAttack}）都必须经这里 ——
     * **不在组件内部各自 try** ✗（否则第三个组件又要重写一遍 ⇒ C-15 第三个实例测试不合格 ✗）。
     *
     * <p>异常处置分三种：
     * <ol>
     *   <li><b>拆卸中 / 已隔离</b> ⇒ **只记日志**（不递归隔离 ✓ A7）；</li>
     *   <li><b>本次派发里已有隔离请求</b> ⇒ 只记日志（首个异常胜出）；</li>
     *   <li><b>否则</b> ⇒ 记下隔离请求（真正的四步在遍历窗口之外执行，见 {@link #withinIterationWindow}）。</li>
     * </ol>
     */
    private void guardedCall(RoleComponent component, String phase, Runnable action) {
        if (component == null || action == null) {
            return;
        }
        try {
            action.run();
        } catch (Throwable failure) {
            if (tearingDown || quarantined || pendingQuarantine != null) {
                logQuarantineSuppressed(component, phase, failure);
                return;
            }
            pendingQuarantine = new QuarantineRequest(component.getId(), phase, failure);
        }
    }

    /**
     * **承受方钩子的交付口**（阶段 11 · t83 · B-静态半）：平台事件面（{@code EntityDamageEvent} /
     * {@code EntityRegainHealthEvent}）经它把"受伤 / 受治疗"通知到**本实例**的组件。
     *
     * <p><b>★ 为什么必须经这里、而不能从施动方实例直接调目标组件</b>：钩子抛异常时要按
     * {@link #guardedCall} 的**故障隔离**语义处置（真四步）—— 那套语义只存在于本类 ⇒ 绕过它就等于
     * 开第二条调用路径 ✗（违反"唯一受保护入口"）。调用方按
     * {@code instance.getAllByType(Participant.class)} **扇出**后逐个交给本方法 ✓。
     *
     * <p><b>为什么外面还要包一层 {@link #withinIterationWindow}</b>：{@code guardedCall} 只把异常**记成**
     * {@link #pendingQuarantine}，真四步是在窗口的 {@code finally} 里跑的（{@code runPendingQuarantine}）⇒
     * 若从事件面裸调 {@code guardedCall}，隔离请求会被记下却**永远不执行** ✗ ⇒ 必须自建窗口边界 ✓。
     * 窗口内禁止增删组件（t55）⇒ 四步照旧落在窗口**之外**执行 ✓。
     *
     * <p><b>主线程前提（B-静态半的申报项）</b>：本方法**必须在主线程**调用 —— 钩子会改动玩家状态
     * （生命 / 回调副作用），而 Bukkit 只允许主线程改动世界状态。非主线程 ⇒ **响亮记 SEVERE 并放弃投递** ✓
     * （把静默损坏变成可见错误；**不**静默忽略 ✗）。本工程基线是 **Paper**（非 Folia）⇒
     * {@code isPrimaryThread} 成立 ✓；**若将来要支持 Folia，这套断言与调度都要重审** ✗。
     *
     * @param component 用于隔离归因的组件（点名"哪个组件抛的"）
     * @param phase     阶段名（进日志与隔离消息，如 {@code onDamaged} / {@code onHealed}）
     * @param action    扇出体（调用方负责遍历 {@code Participant}）
     */
    public void deliverHook(RoleComponent component, String phase, Runnable action) {
        if (component == null || action == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            platform.logger().severe("Role '" + role.getId() + "' received hook '" + phase
                    + "' OFF the primary thread; delivery SKIPPED (hooks mutate player state and must run "
                    + "on the main thread). This is a loud failure, not a silent drop.");
            return;
        }
        withinIterationWindow(() -> guardedCall(component, phase, action));
    }

    /** 被抑制的异常：只记日志（**不**递归隔离、**不**再播报）。 */
    private void logQuarantineSuppressed(RoleComponent component, String phase, Throwable failure) {
        platform.logger().log(Level.SEVERE,
                "Role '" + role.getId() + "' component '" + (component == null ? "?" : component.getId())
                        + "' threw in " + phase + " while the instance was already "
                        + (quarantined ? "quarantined" : "being torn down")
                        + "; logged only, no recursive quarantine.", failure);
    }

    /**
     * **遍历窗口的唯一包装**（阶段 10 · t66）：窗口关闭后立刻执行待处理的隔离。
     * <p>为什么隔离不能在窗口**内**执行：容器在遍历窗口内**拒绝写口**（t55 的"禁止遍历中修改"）
     * ⇒ "移除全部组件"必须等窗口关闭；把四步放在窗口之外**仍属同一次派发调用**（不是延迟到下一 tick）✓。
     */
    private void withinIterationWindow(Runnable body) {
        componentRegistry.beginIteration();
        try {
            body.run();
        } finally {
            componentRegistry.endIteration();
            runPendingQuarantine();
        }
    }

    /** 若本派发边界内有隔离请求 ⇒ 执行它（**同一实例只隔离一次** ✓）。 */
    private void runPendingQuarantine() {
        QuarantineRequest request = pendingQuarantine;
        if (request == null || quarantined) {
            return;
        }
        pendingQuarantine = null;
        quarantine(request);
    }

    /**
     * **故障隔离四步（顺序不可颠倒 · 用户新设计）**：
     * <ol>
     *   <li><b>先尝试执行所有组件的终止方法</b> —— 逐个 try（一个失败不阻断其余 ✓）；</li>
     *   <li><b>然后将其所有组件移除</b> —— 整实例隔离，走裁定④ 动态删除路径 + P2 删除守卫；</li>
     *   <li><b>记录 log</b> —— 点名 角色 / 玩家 / 组件 / 异常（含栈）；</li>
     *   <li><b>给所有人发消息提醒</b> —— 全服简报 + OP 详情 + 限流去重（交给 {@link QuarantineHandler}）。</li>
     * </ol>
     * 第 4 步之后由管理器**清空该玩家角色**（A6，复用既有 {@code clearRole} 清理链）。
     */
    private void quarantine(QuarantineRequest request) {
        if (quarantined) {
            return;
        }
        quarantined = true;

        //① 终止：逐个隔离地执行所有组件的 stop()
        int terminated = terminateAllQuietly();
        //② 移除：整实例隔离（不是只摘掉出错的那一个）
        int removed = removeAllComponents();
        //③ 日志：点名四件 + 栈
        platform.logger().log(Level.SEVERE,
                "Role '" + role.getId() + "' (player " + playerName() + ") was QUARANTINED: component '"
                        + request.componentId() + "' threw in " + request.phase()
                        + ". Terminated " + terminated + " component(s), removed " + removed
                        + " component(s); the player's role is cleared.", request.failure());
        //④ 提醒：全服简报 + OP 详情 + 限流去重
        if (quarantineHandler != null) {
            try {
                quarantineHandler.onQuarantined(this, request.componentId(), request.phase(), request.failure());
            } catch (Throwable notifierFailure) {
                platform.logger().log(Level.SEVERE,
                        "Role '" + role.getId() + "' quarantine notification failed (the isolation itself is complete).",
                        notifierFailure);
            }
        }
    }

    /** ①「先尝试执行所有组件的终止方法」：每个组件各自 try + 记日志，一个失败不阻断其余 ✓。 */
    private int terminateAllQuietly() {
        int terminated = 0;
        for (RoleComponent component : componentRegistry.all()) {
            try {
                component.stop();
                terminated++;
            } catch (Throwable failure) {
                platform.logger().log(Level.SEVERE,
                        "Role '" + role.getId() + "' component '" + component.getId()
                                + "' threw while terminating during quarantine; the remaining components are still terminated.",
                        failure);
            }
            try {
                timerComponent.cancelAllOf(component);
            } catch (Throwable ignored) {
                //终止阶段不得让"回收计时时的异常"打断其余组件的终止
            }
        }
        return terminated;
    }

    /**
     * ②「然后将其所有组件移除」（整实例隔离）。
     * <p><b>顺序策略</b>：每次挑一个"**当前无人声明为必需**"的组件删（装配期已禁止依赖环 ⇒ 一定能删完），
     * 这样 P2 删除守卫**不会**因为"还有依赖者"而拒绝 ⇒ 级联删除天然按依赖倒序完成 ✓。
     * <p><b>失败面干净</b>：若某次删除仍被拒绝（守卫拒绝，或该组件的 {@code stop()} 抛），
     * **显式记一条 SEVERE**，然后**强制移除**（{@code ComponentRegistry#remove}）——
     * 隔离的目的是"失败面干净"，留残留才是真正的问题 ✓。
     *
     * @return 实际移除的组件数
     */
    private int removeAllComponents() {
        int removed = 0;
        int guard = 0;
        while (guard++ < 512) {
            RoleComponent pick = null;
            for (RoleComponent component : componentRegistry.all()) {
                //阶段 10 · t67：反向依赖按**实例**现算（旧写法 requiredBy(id) 在重复 id 下算的是"第一个同 id 者"
                //⇒ 被检查的组件可能不是挑出来的那一个 ✗）。移除仍走动态删除路径（按 id ⇒ 第一个同 id 者）；
                //若因此被 P2 拒绝，下面的 catch 会记 SEVERE 并强制移除 ⇒ 失败面仍然干净。
                if (componentRegistry.requiredBy(component).isEmpty()) {
                    pick = component;
                    break;
                }
            }
            if (pick == null) {
                break;
            }
            try {
                if (componentLookup.remove(pick.getId())) {
                    removed++;
                }
            } catch (Throwable failure) {
                platform.logger().log(Level.SEVERE,
                        "Role '" + role.getId() + "' could not remove component '" + pick.getId()
                                + "' through the dynamic-removal path during quarantine (P2 delete guard or a throwing stop()); "
                                + "forcing the removal so that no residue is left.", failure);
                if (componentRegistry.remove(pick)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    /** 玩家名（日志点名用；离线/空玩家 ⇒ {@code "<unknown>"}）。 */
    private String playerName() {
        return player == null || player.getName() == null ? "<unknown>" : player.getName();
    }

    //阶段 5 · 4.4：旧的两条每 tick 轮询判定（"检测是否应该更新物品"）已删 ——
    //其中一条是恒假死路径，另一条的语义（B-2）并入 triggerUpdate 末尾的帧末 flush 入口条件。

    //清除这个实例时使用，重置玩家状态
    public void clear(){
        //阶段 10 · t66：先进入"拆卸中" ⇒ 之后组件在 stop() 里抛异常**只记日志**、不触发隔离
        //（实例本来就在被销毁；把正常清角色里的 stop() 异常播成"某角色已停用"是假警报 ✗）
        tearingDown = true;
        valid = false;

        triggerLifecycleStop();

        //阶段 4：框架兜底回收组件登记的全部资源（定时器等）——组件忘了取消也不会泄漏
        componentRegistry.cancelAllAndClear();

        if(updateTask != null){
            updateTask.cancel();
            updateTask = null;
        }

        clearHotbar();

        player.getAttribute(Attribute.MAX_HEALTH).removeModifier(roleHealthModifierKey);

        //药水记账（O-7 / D6）：**账本随 buff 组件持有** ⇒ 由它只移除本系统记账过的效果，
        //不再无条件清空玩家身上的所有药水效果（返回移除的类型数，供诊断）
        buffComponent.clearAppliedPotionEffects();

        buffComponent.manager().clearAll();


    }

}
