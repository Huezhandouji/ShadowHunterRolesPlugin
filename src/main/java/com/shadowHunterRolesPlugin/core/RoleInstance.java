package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.component.ComponentRegistry;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.core.ports.ComponentLookupPort;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
 //聚合根只读服务面：角色模板的只读信息（id / 描述），不含阵营。
import com.shadowHunterRolesPlugin.core.ports.RoleInfoPort;
//★ 明列例外（见 docs/ai-generated/约束-框架禁止认识组件.md 的裁决记录）：本容器允许按 id + 类型
// 取回阵营组件的通用面（读写阵营的唯一落点），并引用它的 ID 常量。阵营真值住组件、不在本类。
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;

import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
 //框架级服务组件的清单（类 + id + 构造顺序 + 接线 + 容器侧的服务取用入口都在那一件里）；
 //本类只引用它的 `ID_*` 常量与静态服务入口。
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

// ───────── 内建组件：本类不持有它们，一律按 id 现取 ─────────
// 它们与其他组件走同一条装配路（`Role` 模板里的描述符）⇒ 登记进实例容器后，
// 本类与组件侧都经容器查取入口取它们（本类 = {@link #resolve(String)}，组件侧 = `svc().components()`）。
//状态归属（「状态唯一」）：能量 / SanTE 的真值、buff 记账表、药水账本、任务表都在组件里
// ⇒ 本类既不持有这些状态，也不持有组件本身（构造期用局部变量装配，此后一律按 id 现取）。


 //实例是否仍然有效：clear() 之后置为 false，组件里的延时任务用它做「实例已失效」守卫。
    private boolean valid = true;

 /**
 * 第二相是否已执行：构造器只做不可见的事，
 * 全部玩家可见的副作用在 {@link #activate()} 里，且至多发生一次。
 */
    private boolean activated = false;

 //丢弃物品时，mc服务端会发送挥手数据包，这回导致触发左键交互事件，使用这个标记变量阻止按q时触发左键逻辑。
 //冷却状态与判断归组件实例（`ActiveComponent` 的实例字段），框架侧冷却表已删除。
    private boolean isDropping = false;
    public boolean isDropping() { return isDropping; }
    public void setDroppingState(boolean dropping) { isDropping = dropping; }


 //平台上下文（调度 / 日志 / 键 / 阵营查询）。
    private final RolesContext platform;

 // ───────── 运行期组件异常的故障隔离状态 ─────────
 //同实例只隔离一次：一旦置 true，后续异常只记日志、不递归隔离；派发循环也就地退出。
    private volatile boolean quarantined = false;
 //拆卸中（clear() 起）：此时组件抛异常只记日志，不触发隔离 —— 实例本来就在被销毁，
 //把一次正常清角色里的 stop() 异常播成「某角色已停用」是假警报。
    private boolean tearingDown = false;
 //本次派发边界内待执行的隔离请求（首个异常胜出 ⇒ 日志点名的组件稳定）；
 //真正的四步在遍历窗口之外执行（窗口内禁止增删，见 {@link #withinIterationWindow}）。
    private QuarantineRequest pendingQuarantine;
//隔离的对外处置（日志 / 提醒 / 清空角色）由 RoleManager 在实例构造成功后绑定（它才知道 map 与玩家归属）
    private QuarantineHandler quarantineHandler;
 //动态删除路径入口（「移除全部组件」走它 ⇒ 与运行期增删同一条路径 + 删除守卫）。
    private final ComponentLookupPort componentLookup;

    private ScheduledHandle updateTask;

 //热键栏渲染组件的 id 字面量（纯数据 ⇒ 本类不算「认识组件」，只是按 id 取通用面）。
 // 取用后一律调基类通用面（`RoleComponent#requestRepaint` 等），不 cast、不写 `.class`。
 //只保留本类真正要用的 id：置脏目标与两个能量视图。

 //生命上限修饰符的密钥已随该状态迁入生命组件（组件自己持有）：本类不再持有它
 //（持有它 = 容器必须认识生命组件），字段与本类内的取用一并删除。

 //组件注册表（组件集合 + 每组件资源表 + getComponent 查找）。
    private final ComponentRegistry componentRegistry = new ComponentRegistry();
 /**
 * 角色信息服务面：**本实例**的只读视图（角色模板的 id / 描述）。
 * <p><b>不含阵营</b>：阵营的真值与判定都在阵营组件（{@code roleComponent/builtin/FactionComponent}），
 * 读取走组件、写入经本类的 {@link #setFaction(Faction)} / {@link #resetFaction()} 转发
 * （公开入口 = `RoleAPI` 的两条玩家级方法）。
 */
    private final RoleInfoPort roleInfo = new RoleInfoImpl(this);

 /** 每组件一份的服务集（构造期建立，此后只读）。 */
    private final Map<RoleComponent, ComponentServicesPort> componentServices = new HashMap<>();

 //施放 / 攻击的管道已整体移出容器（归 listener）：容器不认识「技能 / 主武器」这两类东西。

    public RoleInstance(Player player, Role role, RolesContext platform){
        this.player = player;
        this.role = role;
        this.platform = platform;

// ── 服务的持有者先于角色组件存在 ────────────────────────────────
//① buff 记账表由 buff 组件自己创建（账本与持有者成对建立）：容器既不 `new BuffManager`、也不持有它。

//② 内建组件也走模板装配（含阵营组件）：由 `registry/RoleLoader#withBuiltIns(...)` 注册进装配表，
// 与技能 / 被动同一条路 ⇒ 本类不构造任何组件。

//③ 阵营的真值是**每实例一份**的状态 —— 它住阵营组件（见下方「阵营」一节），
// 初值 = 角色模板声明的默认阵营（`Role#getDefaultFaction()`，装配期由描述符携带进组件）。
// 关系表仍留平台（静态数据 ⇒ 外部单例许可，不进依赖图）：它按 UUID 查到该玩家的阵营组件后取值
// ⇒ 生产路径不读任何模板级可变值。
// ⇒ 本类不持有任何阵营字段，只按 id + 类型取回组件并转发读写。

//④ 伤害：四个原语由生命组件承载（伤害与生命只有一个持有者），没有独立的伤害组件。
 //动态删除路径入口（隔离时「移除全部组件」走它 ⇒ 与运行期增删同一条路径 + 删除守卫）。
        this.componentLookup = new ComponentLookupImpl(componentRegistry, this::createServices, platform.logger());

 //装配 = 遍历模板工厂（内建与角色内容一视同仁，本类不认识任何组件类）。
        initComponents();

 //装配完成 → 冻结注册表（此后按 id / 按类型查取才合法）。
        componentRegistry.freeze();

// ── 第一相到此结束 ───────────────────────────────
//构造器只做不可见的事：装配（组件 / 服务集 / 窄类型视图 / 服务组件登记）+ 注册表冻结
//（依赖检查在 `Role#createInstance` 里、本构造器之前）。
//玩家可见的副作用全部在第二相 {@link #activate()}：写生命修饰符 / 设置生命 / 生命周期广播 /
//启动 ticker / 构造期同步首刷。
//因此构造期不创建任何任务，构造失败不留下永久运行的 ticker；且构造失败（含非依赖类的
//组件构造异常）在玩家身上零痕迹，`RoleManager#selectRole` 才能先构造成功、再清旧角色。
    }

 /**
 * 第二相：激活 —— 构造器只做不可见的事，有玩家可见副作用的语句全在这里
 * （语句、顺序、可见时机与既有实现逐字一致，差别只在调用时机）。
 *
 * <p>必须拆两相的理由：早先「先 `clear()` 旧角色、再裸构造新实例」时，构造一旦失败玩家先丢角色；
 * 而「先构造后清理」又会踩三条约束 —— 旧实例的 `clear()` 会 ① 按共享 key 移除新实例刚加的生命上限
 * 修饰符 ② 清空新实例刚渲染的热键栏 ③ 移除同类型药水。拆出本相后，「清旧」发生在新实例写任何
 * 可见状态之前，三条约束全部落空。
 *
 * <p>幂等：重复调用只生效一次（`activated` 护栏）；已 `clear()` 的实例不得再激活。
 *
 * <p>异常：本相可能抛，因此调用方必须自行 try/catch（见 `manager/RoleManager#selectRole`）。
 */
    public void activate(){
        if(activated) return;
        if(!valid) return;
        activated = true;

//① buff 记账表的每 tick 更新已由 buff 组件在自己的 `start()` 里启动，本类不再代劳
// （那会让容器必须认识 buff 组件）。顺序逐字不变：`start()` 由下面的 `triggerLifecycleStart()`
// 按注册序广播，仍先于实例 ticker 提交 ⇒ 同一 tick 内先记账、再 update。

 //② 生命上限已由生命组件自己在 `start()` 里装，本类不再代劳（那会让容器必须认识生命组件与其密钥）。
 // 时序安全性已核：`start()` 由下面的 `triggerLifecycleStart()` 广播，而它发生时旧实例已被清完。

 //③ 生命周期时序：全部组件创建完成 -> awake全部 -> start全部 -> 启动ticker -> 渲染热键栏。
        triggerLifecycleAwake();
        triggerLifecycleStart();

        updateTask = platform.scheduler().runRepeating(
                this::triggerUpdate,
                1L,
                1L
        );

 //④ 同步首刷已由渲染组件在自己的 `start()` 里做，本类不再代劳（那会让容器必须认识渲染组件）。
 // 可见时机逐字不变：`start()` 由上面的 `triggerLifecycleStart()` 广播，就在本处之前几行。
    }

 //平台上下文：组件取用入口（逐批收窄后服务集只剩三个成员）。
    public RolesContext rolesContext() { return platform; }

 //组件注册表（框架内部：装配、资源兜底、getComponent 查找）。
    public ComponentRegistry componentRegistry() { return componentRegistry; }

 /**
 * 角色信息服务面的框架侧读口：角色模板的只读信息（id / 描述）经它，
 * 与组件侧拿到的 {@code svc().roleInfo()} 是同一个实例（{@code createServices} 交出去的就是它）。
 * <p><b>不含阵营</b>：阵营的真值与判定都在阵营组件；容器上的两个写口
 * （{@link #setFaction(Faction)} / {@link #resetFaction()}）只由组件操作面转发进来
 * （{@code RoleAPI#executeComponentOperation} + 组件 id {@code faction} 的 {@code set} / {@code reset}）。
 */
    public RoleInfoPort roleInfo() { return roleInfo; }



 /**
 * 按类型取本实例内的全部组件：返回全部可赋值给 `type` 的组件，顺序 = 添加顺序；
 * 无人符合 ⇒ 空列表（不是 null）；装配完成之前调用 ⇒ 抛 `IllegalStateException`。
 * <p>类型形参无上界 ⇒ 支持接口查询；返回不可变列表。
 * <p>消费者 = `listener/hook/DamageHookListener`（承受方扇出）与生命组件（内部读口）；
 * 组件侧取组件走 `RoleComponent#getComponent(Class)`。
 */
    public <T> java.util.List<T> getAllByType(Class<T> type) {
        return componentRegistry.getAll(type);
    }

 /**
 * 调试用读口：取某组件一对一的服务集（调试探针按 id 定位组件用，例如 {@code /role debug sched}
 * 的组件链实测取请求者与计时组件）。
 * <p>服务集只剩三个成员（{@code self} / {@code components} / {@code roleInfo}），因此本方法不再是
 * 「取端口实例」的手段。
 */
    public ComponentServicesPort servicesOf(String componentId){
        RoleComponent component = componentRegistry.getById(componentId);
        return component != null ? componentServices.get(component) : null;
    }

 /**
 * 组件与其一对一的的服务集。
 * <p>冷却表已合并为单一命名空间，本方法因此不再需要 kind；kind 枚举已整个删掉，
 * 注册处也不再承载任何「权威种类」。
 * <p>{@code componentId} 形参保留，因为动态添加路径的服务集工厂签名不变
 * （{@code ComponentLookupImpl} 吃的就是 {@code Function<String, ComponentServicesPort>}），
 * 但服务集本身不再按 id 绑定任何资源 —— 资源归属一律由组件自己按请求者登记。
 */
    private ComponentServicesPort createServices(String componentId){
        return new ComponentServicesPort(
                new SelfImpl(this),
 //组件服务 = 查找 + 动态添加：服务集工厂传进去，运行期新增的组件与装配期组件走同一条
 //构造路径（同一服务集口径）；日志用于删除守卫的「拒绝删除被依赖组件」那条日志
 //（点名被删组件 / 阻止者 / 缺的类型）。
                new ComponentLookupImpl(componentRegistry, this::createServices, platform.logger()),
 //角色信息服务（聚合根只读面）；构造点仍是这一处 —— 本类持有同一实例并给出框架侧读口
 //{@link #roleInfo()}（组件侧 `svc().roleInfo()` 与框架侧 `instance.roleInfo()` = 同一个实例）。
                roleInfo
        );
    }

 /**
 * 组件创建之后的紧邻登记：服务集与组件一对一进表，组件同时进注册表。
 * 服务集是在构造期交给组件的（{@code factory.create(id, services)}），不存在「创建后尚未注入」的窗口。
 * <p>登记时把装配条目里的依赖声明（提供类型 + 必需依赖）一并交给注册表 ——
 * 它是「删除前算反向依赖」的唯一数据来源，且从描述符声明算出（不手工维护）。
 */
    private void registerCreated(RoleComponent component, ComponentServicesPort services, Role.ComponentEntry entry){
        componentServices.put(component, services);
        componentRegistry.register(component, new ComponentRegistry.Declaration(
                component.getId(), entry.getProvidedType(), entry.getRequiredTypes()));
    }

 // ───────── 组件取用：一律经容器查取入口（本类不持有任何组件） ─────────

 /**
 * 按 id 在容器里取组件；未登记 ⇒ {@code null}（不抛）。
 * <p>与 {@link #getAllByType(Class)} 同一实现点（同一注册表、同一「添加顺序第一个同 id 者」口径），
 * 取到的是容器里那一个实例（同一引用）。
 */
    private RoleComponent resolve(String id) {
        return componentRegistry.getById(id);
    }

 //渲染组件的 `ID` 由调用方（listener / 命令）自己持有，从 `componentRegistry()` 取通用面；
 //容器内不存任何组件、不写任何组件 id（`hotbarRender()` 那条指名渲染组件的路径已删除）。

 // ───────── 派发入口（通用面：容器只提供「受保护调用 + 立即隔离」这一件事）─────────

 /**
 * 受保护地调用一次组件钩子（施放 / 攻击等派发边界都用它）。
 *
 * <p>容器不认识具体组件：调用方自己按 id 取到通用面、自己决定调哪个钩子，
 * 本方法只负责两件框架级的事：① 经 {@link #guardedCall} 调用（异常 ⇒ 故障隔离）
 * ② 调用后立即执行待处理的隔离（本入口不在遍历窗口内）。
 *
 * @param component 目标组件（通用面）
 * @param phase 阶段名（进日志与隔离消息，如 `onCast` / `onAttack`）
 * @param action 实际调用体
 */
    public void invokeComponentHook(RoleComponent component, String phase, Runnable action) {
        guardedCall(component, phase, action);
        runPendingQuarantine();
    }

// ───────── 组件取用：一律「按 id 取到通用面 + 调基类方法」（本类不 cast、不写 `.class`）─────────
// `resolve(id)` 只负责「按 id 从容器里取到通用面」；「取到之后做什么」（置脏 / 帧末刷新 / 首刷 /
// 取变化读数 / 读写能量 / 治疗 / 药水记账 / 取消计时 / 渲染通知扫描）全在框架级清单里完成
// ⇒ 本类不需要 `Class<T>` 参数，也就不需要任何具体组件类的类字面量。
// 静默语义不变：id 取不到 ⇒ 不做任何事；读口回基类既定回退值（见 `RoleComponent` 各视图方法）。

//施放 / 攻击管道已整体删除（那两个入口让容器认识「技能 / 主武器」）。现在：listener 自己读物品 id
// → 按 id 取通用面 → 判冷却 → 经 {@link #invokeComponentHook} 受保护调用 → 自己按渲染组件的 ID
// 取通用面并请求重绘（容器只提供通用设施，不认识任何具体组件）。


 /**
 * 组件初始化（统一装配）：只遍历 {@code role.getComponents()} 一次 ——
 * 遍历顺序 = `Builder.add*` 的调用顺序 = 纯注册序（没有「技能 → 被动 → 主武器」那样的分段）。
 * <p>没有任何「种类」值需要传递或读取（kind 枚举已删）；
 * 服务集构造也不再需要 kind（冷却表已合并为单一命名空间）。
 */
    private void initComponents(){
        for(Map.Entry<String, Role.ComponentEntry> entry : role.getComponents().entrySet()){
            String componentId = entry.getKey();

            ComponentServicesPort services = createServices(componentId);
            RoleComponent component = role.createComponent(componentId, services);
            if(component == null) continue;

//组件侧不再被绑定一条独立的重绘通道：需要请求重绘的组件经渲染组件这一条通道
//（`svc().components().get(...)` 按 id 取到它 —— id 常量归组件自己 —— 再调 requestRepaint()）
// ⇒ 组件侧与框架侧收敛到同一条通道，禁止两套并存。
//渲染组件自持脏标记（`requestRepaint()` 直接置自己的字段），因此不需要任何注入 / 绑定动作，
//容器也不再参与「置脏通道」的装配（`bindRepaintSink` 注入点与旧的两条重绘通道接口均已删除）。
//原先「创建后绑定」的装配期落点也已整体删除 —— 它唯一的绑定目标是计时端口，
//端口面清理后该调用早已是 no-op。状态一律归组件实例本身，不需要任何绑定动作。


            registerCreated(component, services, entry.getValue());
        }
    }


 //buff 记账表的持有者就是 buff 组件：需要它的人走组件本身，不经聚合根转发。


 //就绪判定归组件：调用方按 id 取到通用面后直接问组件（`ActiveComponent#isCoolingDown()`），
 //不经容器转发（`isSkillReady` / `isMainWeaponReady` 那类指名技能与主武器的判定已删除）。



 //原先这里的三个成员（按 id 解析的回落入口、那条口径的纯判定函数、创建后绑定的空转落点）已整体删除：
 //① 三者的生产消费者 0 个（端口面清理后，「端口按 id 回落」这条口径再无使用者）；
 //② 绑定落点本身就是 no-op（唯一绑定目标 = 计时端口，已删）；
 //③ 留着它们会让「状态面按实例」看起来仍由框架兜底，而事实是状态一律归组件实例本身。
 //仍在的同类语义只有组件自己的 `start()` 里按需解析强类型组件，与本处无关。




 //热键栏渲染：唯一写点在渲染组件持有的渲染器里（`roleComponent/builtin/hotbar` 内）；
 //本容器只提供查表与状态输入，不持有渲染器，也不对外提供任何渲染器 / 物品访问器。

    public Player getPlayer() { return player; }
    public Role getRole() { return role; }

 //生命的持有者是生命组件，需要时按组件 id 取到它、调它自己的方法（如 `heal(double)`）；
 //容器不再提供 `getCurrentHealth` / `setCurrentHealth` / `getMaxHealth` / `heal` 这组生命视图。


 //能量视图归能量组件：调用方自己按能量组件的 `ID` 从 `componentRegistry()` 取到它，
 //再调它自己的 `current()` / `set(int)`（本基类不设任何通用视图）；
 //容器不再指名能量组件（`getCurrentEnergy` / `setCurrentEnergy` 两个视图与更早的转发访问器均已删除）。

 //SanTE（视图：真值与 clamp 都在 SanTE 组件里；派发边界由容器给出的平台侧监听触发）：
 //`getCurrentSanTE` / `setCurrentSanTE` / `increaseSanTE` / `decreaseSanTE` 等五个转发访问器已删除，
 //真值与行为都在 SanTE 组件里。

 //实例是否有效：clear() 之后为 false，供组件里的延时任务做失效守卫。
 //字段本身保留（clear() / activate() / 组件隔离守卫都在用它），但不再有公开读口。

 //药水施加入口（记账）归 buff 组件（账本的持有者）：调用方（`BuffManager`）自己按 id 取到
 //该组件，再调它自己的 `applyPotionEffect` ⇒ 容器既不需要转发视图，也不认识 buff 组件。

 // ───────── 阵营：真值与判定都在阵营组件（本类只转发读写）─────────
 //真值所在 = 本实例的阵营组件（`roleComponent/builtin/FactionComponent` 的 `current` 字段）：
 // 初值 = 角色模板声明的默认阵营（{@link Role#getDefaultFaction()}，装配期经描述符写进组件），
 // 此后可被组件操作面（`executeComponentOperation` + 组件 id `faction` 的 `set` / `reset`）按玩家改写
 // ⇒ 同一个角色的两份实例互不影响。
 //组件侧的读取与判定入口就是组件本身（`svc().components().get(FactionComponent.class)`），
 // 跨实例读取走 `platform/FactionManager` 的注册表 ⇒ 本类不提供任何阵营读视图。
 //实例随「清角色 / 死亡 / 掉线」销毁 ⇒ 组件注销 ⇒ 阵营不跨局留存，下次选角色从默认阵营重新起步。

 /**
 * 写本实例的阵营（**只影响这一份实例**）。
 * <p>外部入口 = 组件操作面（{@code RoleAPI#executeComponentOperation} + 组件 id {@code faction}
 * 的 {@code set <阵营名>}）：它解析到本实例后转发到这里，再转发给阵营组件。
 *
 * @param faction 目标阵营
 * @return {@code true} = 已写入；{@code false} = 未写入（本实例没有阵营组件 = 装配不变量被破坏，
 *         或 {@code faction} 为 {@code null}）
 */
    public boolean setFaction(Faction faction){
        FactionComponent component = factionComponent();
        if (component == null || faction == null) {
            return false;
        }
        component.set(faction);
        return true;
    }

 /**
 * 复位为本角色<b>声明</b>的默认阵营（= {@link Role#getDefaultFaction()}）—— 幂等：
 * 已复位时再调用不改变任何值。
 * <p>外部入口 = 组件操作面（{@code RoleAPI#executeComponentOperation} + 组件 id {@code faction}
 * 的 {@code reset}）。
 *
 * @return {@code true} = 已复位；{@code false} = 未复位（本实例没有阵营组件）
 */
    public boolean resetFaction(){
        FactionComponent component = factionComponent();
        if (component == null) {
            return false;
        }
        component.reset();
        return true;
    }

 /**
 * 本实例的阵营组件（按 {@link FactionComponent#ID} 取回通用面再收窄类型）；
 * 容器里没有它 ⇒ {@code null}（装配不变量被破坏：内核服务组件由 `withBuiltIns` 无条件加）。
 */
    private FactionComponent factionComponent(){
        RoleComponent component = componentRegistry.getById(FactionComponent.ID);
        return component instanceof FactionComponent faction ? faction : null;
    }

//生命周期触发（四个口全部 private）：消费者只有容器自己（`activate()` 调 awake/start、
//`clear()` 调 stop、构造期 ticker 用 `this::triggerUpdate`）⇒ 它们是容器职责的实现细节，
//不是「直达钩子」，不再对外暴露；语义与调用序逐字未变。
//awake阶段：只解析跨组件依赖并缓存引用，必须幂等且不改动玩家可见状态
    private void triggerLifecycleAwake(){
        if(player == null ) return;

 //为注册表内组件广播基类钩子 awake()，广播给全部注册组件：新钩子由各组件自行实现
 //（基类提供默认空实现）；按迁移状态分支会引入第二套判据。
 //遍历窗口：广播期间禁止增 / 删 / 插位（注册表在窗口内拒绝写口）。
 //窗口包装 + 唯一受保护调用（异常 ⇒ 记下隔离请求，窗口关闭后执行四步）。
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "awake", component::awake);
            }
        });
    }

 //start阶段：开始生效，顺序与 awake 一致（技能 / 被动 / 武器）。
    private void triggerLifecycleStart(){
        if(player == null ) return;

 //为注册表内组件广播基类钩子 start()（顺序 = 注册表顺序；理由同 awake 处）。
 //遍历窗口与唯一受保护调用同 awake 处。
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "start", component::start);
            }
        });
    }

 //stop阶段：停止生效（仅剩钩子广播，按注册表顺序）。
    private void triggerLifecycleStop(){
        if(player == null ) return;

 //为注册表内组件广播基类钩子 stop()，按注册表顺序停止。
 //不会对同一组件双触发同一逻辑：组件不再实现 legacy 生命周期接口，对基类 stop() 是默认空实现
 // ⇒ 任一组件在任一时刻只被真实逻辑处理一次。
 //幂等：组件在 stop() 里自行取消任务后，clear() 的 `cancelAllAndClear()` 仍会取消其资源表内的
 //同一句柄 ⇒ 重复 cancel 幂等（`Task.cancel()` 对已取消句柄是 no-op）。
 //遍历窗口同 awake 处；唯一受保护调用 —— 但本方法只由 clear() 调用（tearingDown=true），
 //其中的异常只记日志、不触发隔离（否则一次正常清角色里的 stop() 异常会播成「某角色已停用」= 假警报）。
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "stop", component::stop);
 //Bukkit 任务由计时组件全权负责：① 需要即时取消的组件在自己的 `stop()` 里取消
 //（组件自己知道它请求了什么）；② 兜底 = `clear()` 末尾的 `cancelAllAndClear()`
 //（按每组件资源表逐个取消 ⇒ 不泄漏）。本类既不认识计时组件、也不再插手中途回收。
            }
        });
    }

 //SanTE 派发的重入护栏状态。哨兵 Integer.MIN_VALUE = 无待发值；
 //派发期间的组件重入写入只记最新值（禁止嵌套），返回后以最新值合并补发一次。
    private boolean sanTEDispatching = false;
    private int sanTEPendingValue = Integer.MIN_VALUE;

 /**
 * SanTE 变更的唯一派发点（容器直派 + 重入护栏）：
 * <ul>
 * <li>真变化才派发（`pre == now` 直接返回）；</li>
 * <li>禁止嵌套派发：派发期间组件再次改写 ⇒ 只记最新待发值并立即返回；返回后对末次值补发一次；</li>
 * <li>`notified` 不能用「当前值」代替：SanTE 的写入是先写字段、后派发（在组件里），派发期间字段已等于
 * 重入目标值 ⇒ 条件「当前值 != target」恒假，补偿分支退化成死代码。故用局部 `notified` 比较。
 * 退出条件 = `sanTEPendingValue == Integer.MIN_VALUE`（哨兵）；</li>
 * <li>异常隔离走 {@link #guardedCall}。</li>
 * </ul>
 * 护栏在当前组件集下不可达（两个 `onSanTEChange` 实现都不在钩子内同步写 SanTE）⇒ 防御性设施。
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
 //notified = 「上一次已广播的 now」，不得改成与「当前值」比较：
 //写入方先写字段、后派发 ⇒ 重入时字段已等于 target，比较恒假 ⇒ 补偿永不发生（死代码）。
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
 * 一条投递条目（框架侧通用形态）：{@code owner} = 该条归属的组件；{@code action} = 该条的通知动作。
 * <p>提供变更的组件把「逐条投递」交回来时用它作载体 ⇒ 框架据此逐个做故障隔离，
 * 不需要知道组件自己的登记类型（登记类型由组件自持）。
 */
    public record ChangeDelivery(RoleComponent owner, Runnable action) {
    }

 /**
 * 变更通知的通用来源面：提供变更的组件实现它，把本轮变更逐条交给框架。
 * <p>分工：组件只回答「有哪些条目」（遍历它自己的名单；平台侧那一条由组件自己排除，
 * 类型匹配在组件侧完成）；框架负责「怎么调、怎么护」（逐个经 {@link #guardedCall} 做故障隔离、
 * 整段在遍历窗口里、以及真变化闸门与重入合并）⇒ 框架不点名任何具体组件类。
 */
    public interface ChangeListenerSource {

        /**
        * 逐条交回本轮变更的通知条目（平台侧那一条不属于订阅者 ⇒ 实现方自行排除）。
        * @param previous 变化前的值
        * @param current  变化后的值
        * @param delivery 框架的投递口（实现方对每一条条目调用一次）
        */
        void forEachChangeListener(int previous, int current, Consumer<ChangeDelivery> delivery);
    }

 /**
 * 把 SanTE 真值变化派发给订阅者（顺序 = 订阅先后 = 组件装配序）。
 * <p>接受集由订阅表达：遍历的是提供者自持的订阅名单（{@link ChangeListenerSource}），
 * 不是容器注册表 ⇒「谁关心」由订阅表达，不再由接口 / 继承表达；框架只提供投递与保护。
 * <p>平台侧同样是订阅者：它那条监听由组件在写入路径上直接通知（owner 为组件自身），
 * 与其余订阅者走同一条通道 ⇒ 一次真变化恰好一次。
 * <p>未改的两件：逐个经 {@code guardedCall}（异常 ⇒ 只隔离抛异常的那一个、其余照常收到），
 * 整段在 {@link #withinIterationWindow} 里（⇒ 真四步在窗口关闭后执行）。
 * <p>派发边界不再回调平台侧通道：那会造成同一监听被通知两次，而第二次的派发会被重入闸门吞掉，只是空转。
 */
    private void broadcastSanTEChange(int preSanTE, int newSanTE){
 //容器不指名任何组件：按能力面（{@link ChangeListenerSource}）在注册表里找提供者 ——
 //谁是提供者由组件自己是否实现该接口决定，不由容器写死 id。
        List<ChangeListenerSource> providers = new ArrayList<>();
        for (RoleComponent candidate : componentRegistry.all()) {
            if (candidate instanceof ChangeListenerSource source) {
                providers.add(source);
            }
        }
        if (providers.isEmpty()) {
            return;
        }
 //遍历窗口可嵌套（update() 广播期间改 SanTE ⇒ 本方法再次进入窗口）；
 //唯一受保护调用（异常 ⇒ 窗口关闭后执行隔离四步）。
        withinIterationWindow(() -> {
            for (ChangeListenerSource source : providers) {
 //条目由提供者逐个交回（归属组件由它随条目一并给出）⇒ 仍能逐个经 guardedCall 做故障隔离。
                source.forEachChangeListener(preSanTE, newSanTE,
                        entry -> guardedCall(entry.owner(), "onSanTEChange", entry.action()));
            }
        });
 //到此为止不再回调平台侧通道：那次回调产生的第二次通知会被重入闸门收下，补偿分支又因
 //`notified == target` 跳过 ⇒ 空转；同一监听被通知两次也会让「命中次数」失真。
    }

/** 每 tick 派发（private）：唯一消费者 = 构造期 ticker 的 `this::triggerUpdate`。 */
    private void triggerUpdate(){
        if(player == null ) return;
//已隔离 ⇒ 本实例已死（组件已全部移除、角色已被清空）⇒ 不再派发
        if(quarantined) return;

 //为注册表内组件广播基类钩子 update()，按注册表顺序遍历（彼此无先后关系）。
 //所有组件都对基类 update() 自行实现（基类默认空实现），且不再实现 legacy 更新接口 ⇒
 //只被这一条路径调用，不会双触发。
 //遍历窗口：update() 广播期间禁止增 / 删 / 插位（「禁止遍历中修改」的落点）。
 //唯一受保护调用 —— 组件在 update() 里抛 ⇒ 整实例隔离（窗口关闭后执行四步）。
        withinIterationWindow(() -> {
            for(RoleComponent component : componentRegistry.all()){
                guardedCall(component, "update", component::update);
            }
        });

//本 tick 里刚被隔离 ⇒ 到期扫描与帧末 flush 都不再对已死的实例做
        if(quarantined) return;


 //帧末 flush 已由渲染组件在自己的 `update()` 里做，本类不再代劳。
 //时序逐字不变：服务组件在角色组件之后注册 ⇒ 渲染组件排在注册表末位 ⇒ 它的 `update()` 天然最后跑，
 //位置与「在 update 广播之后调 flush」等价；「本帧真的刷新了」的通知也归它自己扇出
 //（名单 = 使用者 `addRenderListener` 登记的函数）。

    }


 // ───────── 框架调用组件的唯一受保护入口 + 故障隔离（四步） ─────────
 /**
 * 隔离的对外处置接入口（由 {@code RoleManager} 在实例构造成功后绑定）：日志点名的四件、
 * 全服 / OP 提醒、以及「清空该玩家角色」都在管理器侧（它才知道 {@code playerRoleMap} 与玩家归属）。
 */
    public interface QuarantineHandler {

        void onQuarantined(RoleInstance instance, String componentId, String phase, Throwable failure);
    }

 /** 一条待执行的隔离请求（首个异常胜出：同一次派发里的第二个异常只记日志）。 */
    private record QuarantineRequest(String componentId, String phase, Throwable failure) {
    }

 /** 绑定隔离处置（{@code RoleManager#selectRole} 在 {@code activate()} 之前调用 ⇒ 生命周期钩子里的异常也能被隔离）。 */
    public void bindQuarantineHandler(QuarantineHandler handler) {
        this.quarantineHandler = handler;
    }

 /** 本实例是否已被隔离（{@code RoleManager} 用它判断「激活期被隔离」的失败面）。 */
    public boolean isQuarantined() {
        return quarantined;
    }

 /**
 * 框架调用组件的唯一受保护入口：框架在每一处调用组件（`awake/start/stop/update` 广播 ·
 * `onSanTEChange` · 逐监听器隔离的投递）都必须经这里 —— 不在组件内部各自 try。
 * <p>异常处置分三种：
 * <ol>
 * <li>拆卸中 / 已隔离 ⇒ 只记日志（不递归隔离）；</li>
 * <li>本次派发里已有隔离请求 ⇒ 只记日志（首个异常胜出）；</li>
 * <li>否则 ⇒ 记下隔离请求（真正的四步在遍历窗口之外执行，见 {@link #withinIterationWindow}）。</li>
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
 * 承受方钩子的交付口：平台事件面把「受伤 / 受治疗」通知到本实例的组件。
 *
 * <p>必须经这里：钩子抛异常要按 {@link #guardedCall} 的故障隔离语义处置，而那套语义只存在于本类，
 * 绕过它 = 开第二条调用路径。
 *
 * <p>外面必须包 {@link #withinIterationWindow}：`guardedCall` 只把异常记成
 * {@link #pendingQuarantine}，真四步在窗口的 `finally` 里跑 ⇒ 裸调会让隔离请求永不执行。
 *
 * <p>主线程前提：必须主线程调用（钩子改动玩家状态）。非主线程 ⇒ 记 SEVERE 并放弃投递
 * （响亮失败，不静默忽略）。
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

 /** 被抑制的异常：只记日志（不递归隔离、不再播报）。 */
    private void logQuarantineSuppressed(RoleComponent component, String phase, Throwable failure) {
        platform.logger().log(Level.SEVERE,
                "Role '" + role.getId() + "' component '" + (component == null ? "?" : component.getId())
                        + "' threw in " + phase + " while the instance was already "
                        + (quarantined ? "quarantined" : "being torn down")
                        + "; logged only, no recursive quarantine.", failure);
    }

 /**
 * 遍历窗口的唯一包装：窗口关闭后立刻执行待处理的隔离。
 * <p>隔离不能在窗口内执行：容器在遍历窗口内拒绝写口 ⇒「移除全部组件」必须等窗口关闭；
 * 把四步放在窗口之外仍属同一次派发调用（不是延迟到下一 tick）。
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

 /** 若本派发边界内有隔离请求 ⇒ 执行它（同一实例只隔离一次）。 */
    private void runPendingQuarantine() {
        QuarantineRequest request = pendingQuarantine;
        if (request == null || quarantined) {
            return;
        }
        pendingQuarantine = null;
        quarantine(request);
    }

 /**
 * 故障隔离四步（顺序不可颠倒）：
 * <ol>
 * <li>先尝试执行所有组件的终止方法 —— 逐个 try（一个失败不阻断其余）；</li>
 * <li>然后将其所有组件移除 —— 整实例隔离，走动态删除路径 + 删除守卫；</li>
 * <li>记录 log —— 点名 角色 / 玩家 / 组件 / 异常（含栈）；</li>
 * <li>给所有人发消息提醒 —— 全服简报 + OP 详情 + 限流去重（交给 {@link QuarantineHandler}）。</li>
 * </ol>
 * 第 4 步之后由管理器清空该玩家角色（复用既有 {@code clearRole} 清理链）。
 */
    private void quarantine(QuarantineRequest request) {
        if (quarantined) {
            return;
        }
        quarantined = true;

 //① 终止：逐个隔离地执行所有组件的 stop()。
        int terminated = terminateAllQuietly();
 //② 移除：整实例隔离（不是只摘掉出错的那一个）。
        int removed = removeAllComponents();
 //③ 日志：点名四件 + 栈。
        platform.logger().log(Level.SEVERE,
                "Role '" + role.getId() + "' (player " + playerName() + ") was QUARANTINED: component '"
                        + request.componentId() + "' threw in " + request.phase()
                        + ". Terminated " + terminated + " component(s), removed " + removed
                        + " component(s); the player's role is cleared.", request.failure());
 //④ 提醒：全服简报 + OP 详情 + 限流去重。
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
 * <p>顺序策略：每次挑一个「当前无人声明为必需」的组件删（装配期已禁止依赖环 ⇒ 一定能删完），
 * 这样删除守卫不会因为「还有依赖者」而拒绝 ⇒ 级联删除天然按依赖倒序完成。
 * <p>失败面干净：若某次删除仍被拒绝（守卫拒绝，或该组件的 {@code stop()} 抛），
 * 显式记一条 SEVERE，然后强制移除（{@code ComponentRegistry#remove}）——
 * 隔离的目的是「失败面干净」，留残留才是真正的问题。
 * @return 实际移除的组件数
 */
    private int removeAllComponents() {
        int removed = 0;
        int guard = 0;
        while (guard++ < 512) {
            RoleComponent pick = null;
            for (RoleComponent component : componentRegistry.all()) {
 //反向依赖按实例算（{@code requiredBy(id)} 在重复 id 下算的是「第一个同 id 者」）
 // ⇒ 被检查的组件可能不是挑出来的那一个。移除仍走动态删除路径（按 id ⇒ 第一个同 id 者）；
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

 /** 玩家名（日志点名用；离线 / 空玩家 ⇒ {@code "<unknown>"}）。 */
    private String playerName() {
        return player == null || player.getName() == null ? "<unknown>" : player.getName();
    }

 //原先两条每 tick 轮询判定（「检测是否应该更新物品」）已删：其中一条是恒假死路径，
 //另一条的语义并入 triggerUpdate 末尾的帧末 flush 入口条件。

 //清除这个实例时使用，重置玩家状态。
    public void clear(){
 //先进入「拆卸中」⇒ 之后组件在 stop() 里抛异常只记日志、不触发隔离
 //（实例本来就在被销毁；把正常清角色里的 stop() 异常播成「某角色已停用」是假警报）。
        tearingDown = true;
        valid = false;

        triggerLifecycleStop();

 //框架兜底回收组件登记的全部资源（定时器等）——组件忘了取消也不会泄漏。
        componentRegistry.cancelAllAndClear();

        if(updateTask != null){
            updateTask.cancel();
            updateTask = null;
        }

//生命上限修饰符已由生命组件自己在 `stop()` 里摘掉，本类不再代劳；执行时机 = 上面的
//`triggerLifecycleStop()`。

//药水账本 + buff 记账表已由 buff 组件自己在 `stop()` 里回收，本类不再代劳。顺序逐字不变
//（先移除本系统记账过的药水、再清账本），执行时机同样是上面的 `triggerLifecycleStop()`。


    }

}
