package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.component.ComponentRegistry;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import com.shadowHunterRolesPlugin.core.ports.ComponentLookup;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
//：聚合根只读服务面 —— 阵营读取的唯一入口（框架侧读口 {@link #roleInfo()} 的类型）。
import com.shadowHunterRolesPlugin.core.ports.RoleInfo;

import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
//框架级服务组件的**清单**（类 + id + 构造顺序 + 接线 + 容器侧的服务取用入口都在那一件里）——
//本类只引用它的 `ID_*` 常量与静态服务入口。
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;

public class RoleInstance {


    private final Player player;
    private final Role role;

 // ───────── 内建组件：本类**不持有**它们，一律按 id 现取 ─────────
 // 它们与其他组件走**同一条装配路**（`Role` 模板里的描述符）⇒ 登记进实例容器后，
 // 本类与组件侧都经容器查取入口取它们（本类 = {@link #resolve(String)}，组件侧 = `svc().components()`）。
 //★ 状态归属（「状态唯一」）：能量 / SanTE 的真值、buff 记账表、药水账本、任务表**都在组件里**
 // ⇒ 本类既不持有这些状态，也**不持有组件本身**（构造期用局部变量装配，此后一律按 id 现取）。


 //实例是否仍然有效：clear() 之后置为 false，组件里的延时任务用它做"实例已失效"守卫
    private boolean valid = true;

 /**
 * **第二相是否已执行**（P6 两阶段构造）：构造器只做不可见的事，
 * 全部玩家可见的副作用在 {@link #activate()} 里，且**至多发生一次**。
 */
    private boolean activated = false;

 //：**框架侧冷却表已删除** —— 冷却状态与判断归组件实例（`ActiveComponent` 的实例字段）；
 //丢弃物品时，mc服务端会发送挥手数据包，这回导致触发左键交互事件，使用这个标记变量阻止按q时触发左键逻辑
    private boolean isDropping = false;
    public boolean isDropping() { return isDropping; }
    public void setDroppingState(boolean dropping) { isDropping = dropping; }


 //平台上下文（调度/日志/键/阵营查询）
    private final RolesContext platform;

 // ─────────：运行期组件异常的**故障隔离**状态（用户新设计） ─────────
 //**同实例只隔离一次**（A7）：一旦置 true，后续异常只记日志、不递归隔离；派发循环也就地退出。
    private volatile boolean quarantined = false;
 //拆卸中（clear() 起）：此时组件抛异常**只记日志**，不触发隔离 —— 实例本来就在被销毁，
 //把一次正常清角色里的 stop() 异常播成"某角色已停用"是假警报。
    private boolean tearingDown = false;
 //本次**派发边界内**待执行的隔离请求（首个异常胜出 ⇒ 日志点名的组件稳定）；
 //真正的四步在遍历窗口**之外**执行（窗口内禁止增删 ⇒ 见 withinIterationWindow）。
    private QuarantineRequest pendingQuarantine;
 //隔离的**对外处置**（日志/提醒/清空角色）由 RoleManager 在实例构造成功后绑定（它才知道 map 与玩家归属）
    private QuarantineHandler quarantineHandler;
 //动态删除路径入口（"移除全部组件"走它 ⇒ 与运行期增删同一条路径 + 删除守卫）
    private final ComponentLookup componentLookup;

    private ScheduledHandle updateTask;

 //★ 热键栏渲染组件的 **id 字面量**（纯数据 ⇒ 本类不算"认识组件"，只是按 id 取通用面）。
 // 取用后一律调**基类通用面**（`RoleComponent#requestRepaint` 等），不 cast、不写 `.class` ✓。
    private static final String HOTBAR_RENDER_ID = "hotbarRender";
    private static final String ENERGY_ID = "energy";
    private static final String VITALS_ID = "vitals";
    private static final String BUFFS_ID = "buffs";
    private static final String TIMERS_ID = "timers";

 //★ 生命上限修饰符的密钥**已随该状态迁入生命组件**（`VitalsComponent.HEALTH_MODIFIER_KEY`）——
 // 本类不再持有它（持有它 = 容器必须认识生命组件）⇒ 字段与本类内的取用一并删除 ✓。

 //组件注册表（组件集合 + 每组件资源表 + getComponent 查找）
    private final ComponentRegistry componentRegistry = new ComponentRegistry();
 /**
 * **聚合根只读服务面**：{@link Role} 的只读视图（id / 描述 / 阵营 / 两个行为）。
 * <p>阵营读取一律走本端口（组件侧 = `svc().roleInfo()`，框架侧 = {@link #roleInfo()}）。
 * <p>**只读，不带写面**：写入仍在聚合根 {@link Role#setFaction} / {@link Role#resetFaction}。
 */
    private final RoleInfo roleInfo = new RoleInfoImpl(this);
 /**
 * **组件侧"请求重绘"的唯一入口**（收进渲染组件）。
 * <p><b>现行形态</b>：组件与框架**都**经**渲染组件**提供的 {@code requestRepaint()} 请求重绘，
 * 置脏落点就是渲染组件自己的脏标记（帧末由它决定写不写物品）⇒ **只有一条重绘通道**
 * （两个并存的老通道已删除）。
 * <p>边界逐字未变：它**只置脏**、不写物品 ⇒ 组件**只能请求、不能写**，
 * 「空闲 tick 零 setItem」与"写入仍由帧末 flush 完成"两条口径不变。
 */
    private final Map<RoleComponent, ComponentServices> componentServices = new HashMap<>();

 //所有组件**无条件**走新管道（单一入口 = handleCast/handleAttack）。

    public RoleInstance(Player player, Role role, RolesContext platform){
        this.player = player;
        this.role = role;
        this.platform = platform;

 // ──：**服务的持有者先于角色组件存在** ────────────────────────────────
 //① buff 记账表：★ **已由 buff 组件自己创建**（账本与持有者成对建立）——
 // 容器不再 `new BuffManager`，也不再持有它 ✓（原 `getBuffManager()` 转发读口早已删除）。

 //② ★ **内建组件也走模板装配** —— 6 件由 `registry/RoleLoader#withBuiltIns(...)` 注册进装配表
 // ⇒ 与技能/被动**同一条路**；本类**不再构造任何组件**（原 `buildBuiltIns()` 已整体删除）✓。

 //③ 阵营：**原 FactionComponent 已整体删除** ——
 // 阵营的真值就是聚合根 `Role` 的 `faction` 字段（构造期由描述符写入）；
 // 关系表仍留平台（静态数据 ⇒ 外部单例许可，不进依赖图）：`RoleInfoImpl` / 平台自带 lookup 直接读它。
 // ⇒ 本相**不再构造任何阵营组件**，也不再登记任何阵营服务组件（阵营不是容器里的状态拥有者）。

 //④ 伤害：四个原语（由**生命组件**承载 ⇒ 伤害与生命只有一个持有者）
 // —— 原独立 DamageComponent 已删除，不再单独构造。

 //**动态删除路径**入口（隔离时"移除全部组件"走它 ⇒ 与运行期增删同一条路径 + 删除守卫）
        this.componentLookup = new ComponentLookupImpl(componentRegistry, this::createServices, platform.logger());

 //★ 装配 = 遍历模板工厂（**内建与角色内容一视同仁**，本类不认识任何组件类）
        initComponents();

 //装配完成 → 冻结注册表（此后按 id / 按类型查取才合法）
        componentRegistry.freeze();

 // ── 第一相到此结束（P6 两阶段构造）───────────────────────────────
 //构造器**只做不可见的事**：装配（组件 / 服务集 / 窄类型视图 / 服务组件登记）+ 注册表冻结
 //（依赖检查在 `Role#createInstance` 里、本构造器之前， 已有）。
 //**玩家可见**的副作用全部在第二相 {@link #activate()}：写生命修饰符 / 设置生命 / 生命周期广播 /
 //启动 ticker / 构造期同步首刷；`BuffManager` 的每 tick 更新同样推迟到那一相
 //（⇒ **构造期不创建任何任务**，构造失败不留下永久运行的 ticker）。
 //**为什么**：构造失败（含**非依赖类**的组件构造异常）必须在玩家身上**零痕迹**，
 //`RoleManager#selectRole` 才可能"先构造成功、再清旧角色"（那条残留）。
    }

 /**
 * **第二相：激活** —— 构造器只做不可见的事；**有玩家可见副作用**的语句全在这里
 * （语句、顺序、可见时机与既有实现逐字一致，差别只在**调用时机**）。
 *
 * <p><b>为什么必须拆两相</b>：早先"先 `clear()` 旧角色、再裸构造新实例"⇒ 构造一旦失败玩家**先丢角色**。
 * 而"先构造后清理"又会踩三条约束 —— 旧实例的 `clear()` 会 ① 按**共享 key** 移除新实例刚加的生命上限
 * 修饰符 ② 清空新实例刚渲染的热键栏 ③ 移除同类型药水。拆出本相后，"清旧"发生在
 * **新实例写任何可见状态之前** ⇒ 三条约束全部落空。
 *
 * <p><b>幂等</b>：重复调用只生效一次（`activated` 护栏）；已 `clear()` 的实例不得再激活。
 *
 * <p><b>异常</b>：本相**可能**抛 ⇒ 调用方**必须**自行 try/catch（见 `manager/RoleManager#selectRole`）。
 */
    public void activate(){
        if(activated) return;
        if(!valid) return;
        activated = true;

 //① buff 记账表的每 tick 更新：**已由 buff 组件在自己的 `start()` 里启动** ✓ ——
 // ★ 本类不再代劳（那会让容器必须认识 buff 组件）。顺序逐字不变：`start()` 由下面的
 //   `triggerLifecycleStart()` 按注册序广播，仍先于实例 ticker 提交 ⇒ 同一 tick 内先记账、再 update。

 //② 生命上限：**已由生命组件自己在 `start()` 里装** ✓ ——
 // ★ 本类**不再代劳**（那会让容器必须认识生命组件与其密钥）。
 // 时序安全性已核：`start()` 由下面的 `triggerLifecycleStart()` 广播，而它发生时旧实例已被清完。

 //③ 生命周期时序：全部组件创建完成 -> awake全部 -> start全部 -> 启动ticker -> 渲染热键栏
        triggerLifecycleAwake();
        triggerLifecycleStart();

        updateTask = platform.scheduler().runRepeating(
                this::triggerUpdate,
                1L,
                1L
        );

 //④ 同步首刷：**已由渲染组件在自己的 `start()` 里做** ✓ ——
 // ★ 本类不再代劳（那会让容器必须认识渲染组件）。
 // 可见时机逐字不变：`start()` 由上面的 `triggerLifecycleStart()` 广播，就在本处之前几行。
    }

 //平台上下文：组件取用入口（逐批收窄后服务集只剩三个成员）
    public RolesContext rolesContext() { return platform; }

 //组件注册表（框架内部：装配、资源兜底、getComponent 查找）
    public ComponentRegistry componentRegistry() { return componentRegistry; }

 /**
 * **角色信息服务面的框架侧读口**（新增；取代原 `factionComponent()` 读口）：
 * 阵营读取与两个行为（{@code isHostile} / {@code hasEnemyInRange}）都经它 —
 * 与组件侧拿到的 {@code svc().roleInfo()} **同一个实例**（{@code createServices} 交出去的就是它）。
 * <p><b>只读</b>：本端口不带写面；写侧在聚合根上（{@link Role#setFaction} / {@link Role#resetFaction}）。
 */
    public RoleInfo roleInfo() { return roleInfo; }



 /**
 * **按类型取本实例内的组件**（"按类型查找"读口）。
 * <p>语义：返回**添加顺序第一个**可赋值给 `type` 的组件（父类/接口查询命中子类实例）；
 * 未注册 ⇒ `null`；**装配完成之前**调用 ⇒ 抛 `IllegalStateException`。
 * <p>生产侧 0 调用点（组件侧取组件一律走 `RoleComponent#getComponent(Class)`）—— 本口保留给
 * 仓外探针与后续依赖注入路径。
 */
    public <T> T getByType(Class<T> type) {
        return componentRegistry.getByType(type);
    }

 /**
 * **按类型取本实例内的全部组件**（新增的公开读口）：
 * 返回全部可赋值给 {@code type} 的组件，顺序 = **添加顺序**；无人符合 ⇒ **空列表**（不是 null）。
 * <p>与 {@link #getByType(Class)} 同一条件、同一顺序 ⇒ 其首元素恒等于 {@code getByType(type)}；
 * 空列表 ⟺ {@code getByType(type) == null}。
 * <p>**调用点申报**：仓内 0 调用点（与 {@code getByType} 同类：公开读口，消费者是后续卡与仓外探针）。
 * **装配完成之前**调用 ⇒ 抛 {@code IllegalStateException}。
 * <p>类型形参**无上界**⇒ 支持**接口**查询；返回**不可变**列表。
 */
    public <T> java.util.List<T> getAllByType(Class<T> type) {
        return componentRegistry.getAll(type);
    }

 /**
 * **调试用读口**：取某组件一对一的服务集（调试探针按 id 定位组件用，例如 {@code /role debug sched}
 * 的组件链实测取请求者与计时组件）。
 * <p><b>服务集三成员</b>：服务集只剩三个成员（{@code self} / {@code components} / {@code roleInfo}）
 * ⇒ 本方法不再是"取端口实例"的手段（冷却自管理的冒烟入口已随其端口一并删除）。
 */
    public ComponentServices servicesOf(String componentId){
        RoleComponent component = componentRegistry.getById(componentId);
        return component != null ? componentServices.get(component) : null;
    }

 /**
 * 组件与其**一对一**的服务集。
 * <p><b>前置</b>：冷却表已合并为**单一命名空间** ⇒ 本方法**不再需要 kind**
 * （合并前"按 kind 选表"的构造期绑定，是"删 kind 枚举"的硬阻塞）。
 * kind 枚举已整个删掉 ⇒ 注册处也不再承载任何"权威种类"。
 * <p><b>形参保留</b>：{@code componentId} 形参**保留**（动态添加路径的服务集工厂签名不变：
 * {@code ComponentLookupImpl} 吃的就是 {@code Function<String, ComponentServices>}），
 * 但服务集本身**不再按 id 绑定任何资源** —— 资源归属一律由组件自己按请求者登记。
 */
    private ComponentServices createServices(String componentId){
        return new ComponentServices(
                new SelfImpl(this),
 //：组件服务 = 查找 + **动态添加** —— 服务集工厂传进去，运行期新增的组件
 //与装配期组件走**同一条**构造路径（同一服务集口径）；日志用于删除守卫的
 //"拒绝删除被依赖组件"的日志（点名被删组件 / 阻止者 / 缺的类型）
                new ComponentLookupImpl(componentRegistry, this::createServices, platform.logger()),
 //（A2）：角色信息服务（聚合根只读面）；
 //：构造点仍是**这一处** —— 本类持有同一实例并给出框架侧读口 {@link #roleInfo()}
 //（组件侧 `svc().roleInfo()` 与框架侧 `instance.roleInfo()` = **同一个实例**）。
                roleInfo
        );
    }

 /**
 * 组件创建之后的**紧邻登记**（五条件①④）：服务集与组件一对一进表，组件同时进注册表。
 * 服务集是在**构造期**交给组件的（{@code factory.create(id, services)}）⇒ 不存在"创建后尚未注入"的窗口。
 * <p>登记时把装配条目里的**依赖声明**（提供类型 + 必需依赖）一并交给注册表
 * —— 它是"删除前算反向依赖"的唯一数据来源，且**从描述符声明算出**（不手工维护）。
 */
    private void registerCreated(RoleComponent component, ComponentServices services, Role.ComponentEntry entry){
        componentServices.put(component, services);
        componentRegistry.register(component, new ComponentRegistry.Declaration(
                component.getId(), entry.getProvidedType(), entry.getRequiredTypes()));
    }

 // ───────── 组件取用：一律经**容器查取入口**（本类不持有任何组件） ─────────

 /**
 * 按 **id** 在容器里取组件；**未登记 ⇒ {@code null}**（不抛）。
 * <p>与 {@link #getByType(Class)} 同一实现点（同一注册表、同一"添加顺序第一个同 id 者"口径），
 * 取到的是**容器里那一个实例**（同一引用）。
 */
    private RoleComponent resolve(String id) {
        return componentRegistry.getById(id);
    }

    /**
     * **请求热键栏重绘**（派发边界之后的"无条件置脏一次"）。
     *
     * <p>★ 走**基类通用面** {@link RoleComponent#requestRepaint()}（默认空实现、由渲染组件覆写）
     * ⇒ 本类只用一个 **id 字面量**取到通用面，**不认识**是哪个组件提供的 ✓。
     * 未命中（id 不存在）⇒ 不做任何事（与既有静默语义逐字一致 ✓）。
     */
    private void requestRepaintOfHotbar() {
        RoleComponent repaintTarget = resolve(HOTBAR_RENDER_ID);
        if (repaintTarget != null) {
            repaintTarget.requestRepaint();
        }
    }

 // ───────── 组件取用：一律「按 id 取到通用面 + 调基类方法」（本类不 cast、不写 `.class`）─────────
 // `resolve(id)` 只负责"按 id 从容器里取到**通用面**"；"取到之后做什么"（置脏 / 帧末刷新 / 首刷 /
 // 取变化读数 / 读写能量 / 治疗 / 药水记账 / 取消计时 / 渲染通知扫描）全在框架级清单里完成
 // ⇒ 本类不需要 `Class<T>` 参数，也就不需要任何具体组件类的**类字面量** ✓。
 // 静默语义不变：id 取不到 ⇒ 不做任何事；读口回基类既定回退值（见 `RoleComponent` 各视图方法）。

 // ───────── 施放 / 攻击管道（单一入口 = handleCast/handleAttack） ─────────

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
 //：判据 = **物品支持组件本身** —— 原能力接口已随吸收删除；
 //接受集**逐字不变**（那个接口的唯一实现者就是本类），本处只用到 onCast。
        if(!(component instanceof ActiveComponent active)) return false;

 //冷却自管理（D1）：框架**不再**代启动冷却 —— 组件在施放成功处自行 startCooldown()（状态归组件、框架只转问）；
 //声明值仍是唯一真值来源，启动点与启动值都与旧框架代启动逐字一致 ⇒ 可观察行为不变。
 //：**唯一受保护调用**（施放是框架派发边界之一）⇒ 组件抛异常 = 整实例隔离
        guardedCall(component, "onCast", () -> active.onCast(new CastSignal(trigger)));
 //本入口不在遍历窗口内 ⇒ 立即执行待处理的隔离
        runPendingQuarantine();
 //**仍返回 true**：本次已由管道"处理"（组件确实被调用过，只是抛了）⇒ 若返回 false，
 //调用方会回落到旧路径 ⇒ **二次派发**（组件已被隔离，二次派发是新的错误面）
 //施放后**无条件**置脏一次（与既有实现逐字一致：这次置脏**不在**帧末入口条件里
 // ⇒ 即使本次施放没有可见变化，也照旧请求一次重绘）
 //★ 走**基类通用面**（`RoleComponent#requestRepaint`，默认空实现、由渲染组件覆写）
 // ⇒ 本类按 id 取到通用面即可请求，**不必认识**是哪个组件提供的 ✓
        requestRepaintOfHotbar();
        return true;
    }

 /** 新路径攻击入口（主武器）。语义同 {@link #handleCast}。 */
    public boolean handleAttack(Player victim, Player attacker){
        if(victim == null || attacker == null) return false;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        String id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
 //：判据与 {@link #handleCast} **同构** = **声明了主动入口的组件**（{@code ActiveComponent}）；
 //★ 原先此处是 {@code instanceof MainWeapon} 强转 ⇒ 框架点名具体家族（与 t6/t7 消灭的
 //「容器强转」同族 ✗）⇒ onAttack 已上提到 {@code ActiveComponent}，本处不再点名任何家族。
        if(!(component instanceof ActiveComponent hook)) return false;

 //冷却自管理（D1）：框架不再代启动冷却（同 handleCast）
 //：唯一受保护调用（攻击同属派发边界）
        guardedCall(component, "onAttack", () -> hook.onAttack(new AttackSignal(victim)));
        runPendingQuarantine();
 //同 handleCast：攻击后**无条件**置脏一次（这次置脏不在帧末入口条件里）
 //★ 同走基类通用面 ✓
        requestRepaintOfHotbar();
        return true;
    }

 /**
 * 组件初始化（统一装配）：**只遍历 {@code role.getComponents()} 一次** ——
 * 遍历顺序 = `Builder.add*` 的调用顺序 = **纯注册序**（早先的三段遍历
 * 「技能 → 被动 → 主武器」已删除，见交付说明的派发序申报）。
 * <p>**没有任何"种类"值**需要传递或读取（kind 枚举已删）；
 * **服务集构造也不再需要 kind**（冷却表已合并为单一命名空间）。
 */
    private void initComponents(){
        for(Map.Entry<String, Role.ComponentEntry> entry : role.getComponents().entrySet()){
            String componentId = entry.getKey();

            ComponentServices services = createServices(componentId);
            RoleComponent component = role.createComponent(componentId, services);
            if(component == null) continue;

 //：组件侧不再被绑定一条**独立**的重绘通道 —— 需要请求重绘的组件改为
 //经**渲染组件**这一条通道：`svc().components().get(...)`
 //（按 id 取 —— id 常量归组件自己：`HotbarRenderComponent.ID`）拿到它，再调 requestRepaint()。
 //⇒ 组件侧与框架侧**收敛到同一条通道**（禁两套并存）；
 // 旧 `RepaintRequestable` / `RepaintRequester` 两条通道**已删除**。
 //绑定时机的纪律不变：渲染组件本身在构造器里就已 bindRepaintSink（早于任何 awake/start）。
 //：原"创建后绑定"的**装配期落点已整体删除** （它唯一的绑定目标是计时端口，
 // 端口面 已清理 ⇒ 该调用早已是 no-op）—— 状态一律归**组件实例本身**，不需要任何绑定动作。


            registerCreated(component, services, entry.getValue());
        }
    }


 //原 `getBuffManager()` **转发访问器已删除** —— 消费者 0
 //（buff 记账表的持有者本来就是 **buff 组件**；需要它的人走组件本身，不经聚合根转发）。


 //技能相关
 /**
 * 就绪判定（**单一冷却命名空间**的视图）：无条目/已到期 ⇒ {@code true}。
 * <p><b>判定就地转问组件</b>：原实现经 `isCooldownReady(skillId)` 转发，现改为就地转问组件
 * ⇒ 框架不持有冷却状态，只转问（口径不变）。
 * 行为**逐字等价**：无冷却能力 ⇒ 恒就绪。
 */
    public boolean isSkillReady(String skillId){
        RoleComponent component = componentRegistry.getById(skillId);
        return !(component instanceof ActiveComponent active) || !active.isCoolingDown();
    }

 //冷却**剩余量视图**（技能 2 件 + 主武器 2 件）与它们唯一的下游
 //`remainingCooldownTicks` / `isCooldownReady` **转发访问器已删除** —— 消费者 0
 //（冷却状态自 归 `ActiveComponent`：读侧直接问组件，不经聚合根转发；
 // 就绪判定仍由**活码** `isSkillReady` / `isMainWeaponReady` 提供 ⇒ 能力未失去入口）。


 /** 就绪判定（：同 {@link #isSkillReady(String)} —— 就地转问组件，转发访问器已删）。 */
    public boolean isMainWeaponReady(String weaponId){
        RoleComponent component = componentRegistry.getById(weaponId);
        return !(component instanceof ActiveComponent active) || !active.isCoolingDown();
    }


 //（代码卫生）：原先这里的三个成员 —— 按 id 解析的**回落入口**、那条口径的
 // **纯判定函数**、以及"创建后绑定"的**空转落点** —— 已**整体删除**。
 //理由（逐条）：① 三者的生产消费者 **0 个**（端口面 清理后，"端口按 id 回落"这条口径再无使用者）；
 // ② 绑定落点自 就是 **no-op**（唯一绑定目标 = 计时端口，已删）；
 // ③ 留着它们会让"状态面按实例"看起来仍由框架兜底 —— 而事实是**状态一律归组件实例本身**。
 //★ 唯一仍在的同类语义 = 组件自己的 `start()` 里**按需/一次解析强类型组件**，与本处无关。




 //热键栏渲染：唯一写点在渲染组件持有的渲染器里（`roleComponent/builtin/hotbar` 内）；
 //本容器只提供查表与状态输入，**不持有**渲染器、也不对外提供任何渲染器 / 物品访问器。

    public Player getPlayer() { return player; }
    public Role getRole() { return role; }


 //生命
 //原三个**生命视图转发访问器**（`getCurrentHealth` / `setCurrentHealth` /
 //`getMaxHealth`）**已删除** —— 消费者 0（生命状态 = Bukkit 玩家属性，持有者是**生命组件**
 //⇒ 需要时走组件本身；`heal(double)` 仍为**活码**（视图口，组件在用））。

    public void heal(double amount){
 //★ 走**基类通用面**（`RoleComponent#heal`，默认空实现、由生命组件覆写）
 // ⇒ 本类按 id 取到通用面即可，**不必认识**生命组件 ✓（clamp 策略的唯一实现仍在组件里）
        RoleComponent vitals = resolve(VITALS_ID);
        if (vitals != null) {
            vitals.heal(amount);
        }
    }


 //能量（**视图**：真值与 clamp/检查扣减的行为都在能量组件里）
 //`getMaxEnergy` / `decreaseEnergy` / `increaseEnergy` 三个转发访问器
 //**已删除** （消费者 0：`api/` 侧 38 个老方法做空后，原先的
 //`instance.increaseEnergy(...)` 一类调用已消失）；`getCurrentEnergy` / `setCurrentEnergy`
 //仍是**活码**（`command/EnergyCommand` 的读数与设值路径）。

    public int getCurrentEnergy() {
        RoleComponent energy = resolve(ENERGY_ID);
        return energy == null ? 0 : energy.readCurrentEnergy();
    }

    public void setCurrentEnergy(int amount){
        RoleComponent energy = resolve(ENERGY_ID);
        if (energy != null) {
            energy.writeCurrentEnergy(amount);
        }
    }

 //SanTE（**视图**：真值与 clamp 都在 SanTE 组件里；派发边界由容器**给出的平台侧监听**触发）
 //`getCurrentSanTE` / `setCurrentSanTE` / `increaseSanTE` / `decreaseSanTE` /
 //`getMaxSanTE` 五个转发访问器**已删除** （消费者 0；真值与行为都在 **SanTE 组件**）。

 //实例是否有效：clear() 之后为 false，供组件里的延时任务做失效守卫
 //原 `isValid()` 公开读口**已删除**（消费者 0：容器内一律
 //直接读私有字段 `valid`；字段本身保留：clear() / activate() / 组件隔离守卫都在用它）。

 //药水施加入口（记账）：★ **已从容器删除** —— 账本的持有者 = buff 组件，
 // 调用方（`BuffManager`）改为**自己按 id 取到该组件**再调它自己的 `applyPotionEffect` ✓
 // ⇒ 容器不再需要这个转发视图，也不再认识 buff 组件。

 // ───────── 阵营（欠账 A 后半）：**读侧视图已删除** / 写侧视图保留 ─────────
 //★ 真值所在：聚合根 `Role` 的 `faction` 字段（原 `FactionComponent.faction` 组件字段已随组件删除）。
 //★ **读取唯一入口 = `roleInfo` 服务面**：组件侧 `svc().roleInfo()`、框架侧 {@link #roleInfo()}
 // ⇒ 本类**不再**提供 `getFaction()` / `isHostileTo(...)` 三个读视图 （调用点已改走 RoleInfo：
 // `internal/api/RoleAPIImpl#getFaction(*2)`、`manager/RoleManager#areHostile(*2)`）。
 //★ 查表语义（`FactionLookup#isHostile`，关系表仍留平台）= 旧 {@code isHostileTo(Faction)} 逐字等价。
 //★ ****：本类的**写视图也一并删除** （`setFaction(Faction)` / `resetFaction()` 两条 ——
 // 原为 `RoleAPI#setFaction/resetFaction` 的落点）⇒ 那两条 API 已**做空、不再生效** ⇒ 写视图**无消费者**；
 // 真值写入仍在**聚合根**（{@link Role#setFaction} / {@link Role#resetFaction}，**不由外部直改**）。

 //生命周期触发：**四个触发口收窄为 private** —— 消费者
 //**只有容器自己**（`activate()` 调 awake/start、`clear()` 调 stop、构造期 ticker 用 `this::triggerUpdate`） ⇒ 它们不是"直达钩子"而是**容器职责的实现细节** ⇒ 不再对外暴露；语义与调用序逐字未变
 //awake阶段：只解析跨组件依赖并缓存引用，必须幂等且不改动玩家可见状态
    private void triggerLifecycleAwake(){
        if(player == null ) return;

 //为**注册表内组件**广播新基类钩子 awake()。
 //广播给"全部注册组件"：所有组件的新钩子由各组件自行实现（基类提供默认空实现）；
 //按迁移状态分支会引入第二套判据。
 //遍历窗口：广播期间**禁止**增/删/插位（注册表在窗口内拒绝写口）
 //：窗口包装 + **唯一受保护调用**（异常 ⇒ 记下隔离请求，窗口关闭后执行四步）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "awake", component::awake);
            }
        });
    }

 //start阶段：开始生效，顺序与awake一致（技能/被动/武器）
    private void triggerLifecycleStart(){
        if(player == null ) return;

 //为注册表内组件广播新基类钩子 start()（顺序 = 注册表顺序；理由同 awake 处注释）
 //遍历窗口：同 awake 处；：同 awake 处（唯一受保护调用）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "start", component::start);
            }
        });
    }

 //stop阶段：停止生效（仅剩新钩子广播，按注册表顺序）
    private void triggerLifecycleStop(){
        if(player == null ) return;

 //为注册表内组件广播新基类钩子 stop()。
 //**顺序说明**：新钩子按**注册表顺序**停止。
 //两者不会对同一组件双触发同一逻辑 —— 组件**不再实现 legacy 生命周期接口**，
 //未迁移组件则对基类 stop() 是**默认空实现** ⇒ 任一组件在任一时刻只被"真实逻辑"处理一次。
 //**幂等说明**：若组件在 stop() 里自行取消任务，随后 clear() 的 cancelAllAndClear() 仍会取消其
 //资源表内的同一句柄 ⇒ 重复 cancel 幂等（Task.cancel() 对已取消句柄是 no-op）。
 //遍历窗口：同 awake 处
 //：唯一受保护调用 —— 但本方法**只**由 clear() 调用（tearingDown=true）⇒ 其中的异常
 //只记日志、**不**触发隔离（否则一次正常清角色里的 stop() 异常会播成"某角色已停用"= 假警报）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "stop", component::stop);
 //★ **Bukkit 任务由计时组件全权负责** —— 本类**不再**逐组件回收计时：
 // ① 需要即时取消的组件在自己的 `stop()` 里取消（组件自己知道它请求了什么）；
 // ② 兜底 = `clear()` 末尾的 `cancelAllAndClear()`（按每组件资源表逐个取消 ⇒ 不泄漏）。
 //  ⇒ 本类既不认识计时组件、也不再插手中途回收 ✓
            }
        });
    }

 //SanTE 派发的重入护栏状态。哨兵 Integer.MIN_VALUE = 无待发值；
 //派发期间的组件重入写入只记最新值（禁止嵌套），返回后合并补发一次。
    private boolean sanTEDispatching = false;
    private int sanTEPendingValue = Integer.MIN_VALUE;

 /**
 * SanTE 变更的**唯一派发点**（容器直派 + 重入护栏）：
 * <ul>
 * <li>**真变化才派发**（`pre == now` 直接返回）；</li>
 * <li>**禁止嵌套派发**：派发期间组件再次改写 ⇒ 只记最新待发值并立即返回；返回后对末次值**补发一次**；</li>
 * <li>★ `notified` 不能用 `currentSanTE` 代替：`setCurrentSanTE` **先写字段、后派发** ⇒ 派发期间字段已等于
 * 重入目标值 ⇒ 条件 `currentSanTE != target` **恒假**，补偿分支退化成死代码。故用局部 `notified` 比较。
 * 退出条件 = `sanTEPendingValue == Integer.MIN_VALUE`（哨兵）；</li>
 * <li>异常隔离走 {@link #guardedCall}。</li>
 * </ul>
 * ★ **护栏在当前组件集下不可达**（两个 `onSanTEChange` 实现都不在钩子内同步写 SanTE）⇒ 防御性设施。
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
 * **一条投递条目**（框架侧**通用**形态）：{@code owner} = 该条归属的组件；{@code action} = 该条的通知动作。
 * <p>提供变更的组件把"逐条投递"交回来时用它作载体 ⇒ 框架据此**逐个**做故障隔离，
 * **不需要**知道组件自己的登记类型（登记类型由组件自持）。
 */
    public record ChangeDelivery(RoleComponent owner, Runnable action) {
    }

 /**
 * **变更通知的通用来源面**：提供变更的组件实现它，把本轮变更**逐条**交给框架。
 * <p><b>分工</b>：组件只回答"有哪些条目"（遍历它自己的名单；平台侧那一条由组件自己排除 ⇒
 * 类型匹配在组件侧完成）；框架负责"怎么调、怎么护"（逐个经 {@link #guardedCall} 做故障隔离、
 * 整段在遍历窗口里、以及真变化闸门与重入合并）⇒ 框架**不点名任何具体组件类** ✓。
 */
    public interface ChangeListenerSource {

        /**
         * 逐条交回本轮变更的通知条目（**平台侧那一条不属于订阅者** ⇒ 实现方自行排除）。
         * @param previous 变化前的值
         * @param current  变化后的值
         * @param delivery 框架的投递口（实现方对**每一条**条目调用一次）
         */
        void forEachChangeListener(int previous, int current, Consumer<ChangeDelivery> delivery);
    }

 /**
 * 把 SanTE 真值变化**派发给订阅者**（顺序 = **订阅先后** = 组件装配序）。
 * <p><b>接受集由订阅表达</b>：遍历的是**提供者自持的订阅名单**（{@link ChangeListenerSource}），
 * **不是**容器注册表 ⇒ 「谁关心」由**订阅**表达，不再由接口/继承表达；框架只提供投递与保护。
 * <p><b>未改的两件</b>（已确立、原样保留）：**逐个**经 {@code guardedCall}
 * （异常 ⇒ 只隔离抛异常的那一个、其余照常收到）· 整段在 {@link #withinIterationWindow} 里
 * （⇒ 真四步在窗口关闭后执行）。
 * <p><b>平台侧通道</b>：**唯一入口 = 写入路径的直接通知**（写入组件在 {@code set} 里经 `notifyPlatform`
 * 直调它自己那条 owner 为自身的监听 ⇒ 交给容器登记的那条接收者 ⇒ 本方法）⇒ **一次真变化恰好一次** ✓。
 * 派发边界**不再**回调平台侧通道（那会造成同一监听被通知两次，而第二次的派发会被重入闸门吞掉 ⇒ 只是空转）。
 */
    private void broadcastSanTEChange(int preSanTE, int newSanTE){
        RoleComponent provider = resolve(SanTEComponent.ID);
        if (!(provider instanceof ChangeListenerSource source)) {
            return;
        }
 //遍历窗口：可嵌套（update() 广播期间改 SanTE ⇒ 本方法再次进入窗口）
 //：唯一受保护调用（异常 ⇒ 窗口关闭后执行隔离四步）
        withinIterationWindow(() -> {
 //条目由提供者逐个交回（归属组件由它随条目一并给出）⇒ 仍能**逐个**经 guardedCall 做故障隔离 ✓
            source.forEachChangeListener(preSanTE, newSanTE,
                    entry -> guardedCall(entry.owner(), "onSanTEChange", entry.action()));
        });
 //★ 到此为止：**不再**回调平台侧通道 —— 那次回调产生的第二次通知会被重入闸门收下、
 //补偿分支又因 `notified == target` 跳过 ⇒ 空转；同一监听被通知两次也会让"命中次数"失真。
    }

 /** 每 tick 派发（：**收窄为 private** ← —— 唯一消费者 = 构造期 ticker 的 `this::triggerUpdate`）。 */
    private void triggerUpdate(){
        if(player == null ) return;
 //：已隔离 ⇒ 本实例已死（组件已全部移除、角色已被清空）⇒ 不再派发
        if(quarantined) return;

 //为**注册表内组件**广播新基类钩子 update()。
 //**顺序说明**：按**注册表顺序**遍历（无先后关系）；
 //所有组件都对基类 update() 自行实现（基类默认空实现）；组件均已迁移（无迁移标记）
 //**不再实现 legacy 更新接口** ⇒ 只被这一条路径调用，不会双触发。
 //遍历窗口：**update() 广播期间禁止增/删/插位**（"禁止遍历中修改"的落点）
 //：唯一受保护调用 —— 组件在 update() 里抛 ⇒ 整实例隔离（窗口关闭后执行四步）
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "update", component::update);
            }
        });

 //：本 tick 里刚被隔离 ⇒ 到期扫描与帧末 flush 都不再对已死的实例做
        if(quarantined) return;


 //帧末 flush：**已由渲染组件在自己的 `update()` 里做** ✓ ——
 // ★ 本类不再代劳。时序逐字不变：服务组件在角色组件**之后**注册 ⇒ 渲染组件排在注册表**末位**
 //   ⇒ 它的 `update()` 天然最后跑，位置与既有"在 update 广播之后调 flush"等价 ✓
 // "本帧真的刷新了"的通知也归它自己扇出（名单 = 使用者 `addRenderListener` 登记的函数）。

    }


 // ─────────：**框架调用组件的唯一受保护入口** + 故障隔离（四步） ─────────

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
 * **框架调用组件的唯一受保护入口**：框架在**每一处**调用组件（`awake/start/stop/update` 广播 ·
 * `onSanTEChange` · `onCast` / `onAttack`）都必须经这里 —— **不在组件内部各自 try**。
 * <p>异常处置分三种：
 * <ol>
 * <li>**拆卸中 / 已隔离** ⇒ **只记日志**（不递归隔离）；</li>
 * <li>**本次派发里已有隔离请求** ⇒ 只记日志（首个异常胜出）；</li>
 * <li>**否则** ⇒ 记下隔离请求（真正的四步在遍历窗口之外执行，见 {@link #withinIterationWindow}）。</li>
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
 * **承受方钩子的交付口**：平台事件面把"受伤 / 受治疗"通知到**本实例**的组件。
 *
 * <p><b>为什么必须经这里</b>：钩子抛异常时要按 {@link #guardedCall} 的**故障隔离**语义处置，
 * 而那套语义只存在于本类 ⇒ 绕过它 = 开第二条调用路径。调用方按目标实例上实现了承受方标记的
 * 组件扇出后，逐个交给本方法。
 *
 * <p><b>为什么外面包 {@link #withinIterationWindow}</b>：`guardedCall` 只把异常**记成**
 * {@link #pendingQuarantine}，真四步在窗口的 `finally` 里跑 ⇒ 裸调 `guardedCall` 会让隔离请求
 * 被记下却**永不执行**。
 *
 * <p><b>★ 主线程前提</b>：必须主线程调用（钩子改动玩家状态）。非主线程 ⇒ **记 SEVERE 并放弃投递**
 * （响亮失败，不静默忽略）。基线是 Paper（非 Folia）；若要支持 Folia，这套断言与调度都要重审。
 *
 * @param component 用于隔离归因的组件
 * @param phase 阶段名（进日志与隔离消息）
 * @param action 扇出体（调用方负责遍历承受方标记）
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
 * **遍历窗口的唯一包装**：窗口关闭后立刻执行待处理的隔离。
 * <p>为什么隔离不能在窗口**内**执行：容器在遍历窗口内**拒绝写口**
 * ⇒ "移除全部组件"必须等窗口关闭；把四步放在窗口之外**仍属同一次派发调用**（不是延迟到下一 tick）。
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

 /** 若本派发边界内有隔离请求 ⇒ 执行它（**同一实例只隔离一次**）。 */
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
 * <li><b>先尝试执行所有组件的终止方法</b> —— 逐个 try（一个失败不阻断其余）；</li>
 * <li><b>然后将其所有组件移除</b> —— 整实例隔离，走动态删除路径 + 删除守卫；</li>
 * <li><b>记录 log</b> —— 点名 角色 / 玩家 / 组件 / 异常（含栈）；</li>
 * <li><b>给所有人发消息提醒</b> —— 全服简报 + OP 详情 + 限流去重（交给 {@link QuarantineHandler}）。</li>
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

 /** ①「先尝试执行所有组件的终止方法」：每个组件各自 try + 记日志，一个失败不阻断其余。 */
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
        }
        return terminated;
    }

 /**
 * ②「然后将其所有组件移除」（整实例隔离）。
 * <p><b>顺序策略</b>：每次挑一个"**当前无人声明为必需**"的组件删（装配期已禁止依赖环 ⇒ 一定能删完），
 * 这样删除守卫**不会**因为"还有依赖者"而拒绝 ⇒ 级联删除天然按依赖倒序完成。
 * <p><b>失败面干净</b>：若某次删除仍被拒绝（守卫拒绝，或该组件的 {@code stop()} 抛），
 * **显式记一条 SEVERE**，然后**强制移除**（{@code ComponentRegistry#remove}）——
 * 隔离的目的是"失败面干净"，留残留才是真正的问题。
 * @return 实际移除的组件数
 */
    private int removeAllComponents() {
        int removed = 0;
        int guard = 0;
        while (guard++ < 512) {
            RoleComponent pick = null;
            for (RoleComponent component : componentRegistry.all()) {
 //：反向依赖按**实例**（既有写法 requiredBy(id) 在重复 id 下算的是"第一个同 id 者"
 //⇒ 被检查的组件可能不是挑出来的那一个）。移除仍走动态删除路径（按 id ⇒ 第一个同 id 者）；
 //若因此被删除守卫拒绝，下面的 catch 会记 SEVERE 并强制移除 ⇒ 失败面仍然干净。
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

 //两条每 tick 轮询判定（"检测是否应该更新物品"）已删 ——
 //其中一条是恒假死路径，另一条的语义并入 triggerUpdate 末尾的帧末 flush 入口条件。

 //清除这个实例时使用，重置玩家状态
    public void clear(){
 //：先进入"拆卸中" ⇒ 之后组件在 stop() 里抛异常**只记日志**、不触发隔离
 //（实例本来就在被销毁；把正常清角色里的 stop() 异常播成"某角色已停用"是假警报）
        tearingDown = true;
        valid = false;

        triggerLifecycleStop();

 //框架兜底回收组件登记的全部资源（定时器等）——组件忘了取消也不会泄漏
        componentRegistry.cancelAllAndClear();

        if(updateTask != null){
            updateTask.cancel();
            updateTask = null;
        }

 //生命上限修饰符：**已由生命组件自己在 `stop()` 里摘掉** ✓ ——
 // ★ 本类不再代劳；上面的 `triggerLifecycleStop()` 就是它的执行时机（既有语句，未新增调用点）。

 //药水账本 + buff 记账表：**已由 buff 组件自己在 `stop()` 里回收** ✓ ——
 // ★ 本类不再代劳。顺序逐字不变（先移除本系统记账过的药水、再清账本），
 //   执行时机 = 上面的 `triggerLifecycleStop()`（既有语句，未新增调用点）。


    }

}
