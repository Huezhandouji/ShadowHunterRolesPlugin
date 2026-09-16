# 组件系统设计（Unity 组件脚本风格）· 最终版

> **定位**：`docs/最终重构指南.md` 第 3 章（目标架构）的展开与替换。指南里 §3.3 的接口草案、§3.6 的生命周期契约以本文为准。
> **编制方式**：逐文件实读当前工作树 + 逐条实测组件对聚合根的调用面（20 个方法）+ 在 `paper-api-1.21.11-R0.1-SNAPSHOT.jar` 上核实 API。
> **本文回答一个问题**：组件系统应该长什么样，才能让组件**根本拿不到** `RoleInstance`。

---

## 0. 结论摘要

1. **`Aware` 接口应当取消**，改为 `RoleComponent` 基类上的 **default 空钩子**（Unity 的 `MonoBehaviour` 消息模型）。理由不是性能（性能差异可忽略），而是：今日 4 个 `Aware` 接口里有 1 个**零生产实现者**，而每加一个新的 `Aware` 接口就要同步改容器里的 3 段 `instanceof` 循环；基类 default 方法让"加钩子"退化成一行、且不会产生空壳接口。
2. **但"能力接口"必须保留**：`HotbarItem` / `HotbarActionable` / `CombatHook` 表达的是"**我是什么**"，框架靠它决定组件**放在哪、按什么分派**；这与 `Aware` 表达的"**何时通知我**"是两件事。判据见 §3。
3. **组件拿不到 `RoleInstance` 靠三件事**：构造期注入**端口**（独立适配器，非上转型）+ 回调只传**信号** + 组件之间用 **`getComponent(具体类.class)` 直接调公开方法**（早期设想的 `contract/` 接口包已取消，§4.4）。
4. **基类钩子集合最终为 5 个**：`awake` / `start` / `stop` / `update` / `onSanTEChange`。数值钩子里只有 SanTE 有真实使用者（2 个）；**能量与生命钩子经分析后不加**——理由与"将来要加时的三种取舍"记在 §2.1 末尾，避免重复讨论。
5. **断线语义已裁决（§9.1）**：**掉线即销毁角色实例** —— `PlayerQuitEvent` → 立即完整 `clearRole(uuid)`，不挂起、不保留、**不引入配置文件**；语义与死亡一致（掉线 = 死亡 = `clearRole`）。这是可见的玩法变化，已登记进指南 §6.2；收益是 O-25 登出泄漏被彻底消除、`Self` 的失效引用问题从根上消失。
6. **顺带修掉的实测缺陷**：`EnergyChangeAware` 整条链路是死机制（§3.4）；组件分派顺序今天依赖 `HashMap` 迭代序、**未定义**（§7）；`getCooldown()` 是死数据（声明值从未被读取）；两个消费方**直接读写提供方内部 Map**（`Map<String,Object>` 上下文整条删除，§4.4.1）。

---

## 1. Unity → 本项目的映射

| Unity | 本项目 | 备注 |
|---|---|---|
| `GameObject`（容器） | `RoleInstance`（每玩家每局一个） | 容器**只**做：持有组件、按固定顺序广播、提供端口、托管资源、渲染 |
| `MonoBehaviour`（脚本基类） | `RoleComponent`（抽象基类） | 只载**钩子 + `svc`**；不载任何领域状态与方法 |
| `Awake()` | `awake()` | 解析跨组件引用。Unity 明确要求"用 Awake 建立组件间引用、用 Start 传数据"——与本项目既有约定一致 |
| `OnEnable()` | `start()` | 开始生效（发装备、注册任务） |
| `Update()` | `update()` | 服务端 20 Hz 固定 tick = Unity 的帧 |
| `FixedUpdate()` | **不引入** | 服务端只有一个固定 tick，没有独立的物理帧 |
| `LateUpdate()` | **不暴露给组件** | 帧末热键栏 flush 是框架内部行为 |
| `OnDisable()` / `OnDestroy()` | `stop()` | 对称拆卸；之后框架自动回收该组件的全部登记资源 |
| `GetComponent<T>()` | **`getComponent(T.class)`**（`RoleComponent` 基类方法，§4.4） | 拿到同角色实例里的组件本体，直接调公开方法；`awake()` 里解析一次并缓存 |
| `StartCoroutine()`（随组件销毁自动停） | `svc.timers().runRepeating(...)` | 登记到该组件自己的资源表；`stop()` 后框架兜底取消 |
| `[SerializeField]` / Inspector 拖引用 | **构造期端口注入** | Unity 里"依赖"靠序列化引用，本项目靠构造参数 |
| `[RequireComponent(typeof(X))]` | `RequiresComponents`（§4.4，可选） | 注册期校验，而不是运行时 NPE |
| Script Execution Order | **固定阶段顺序 + 注册顺序**（§7） | 不提供优先级数字（YAGNI） |
| Prefab + `Instantiate()` | `Role` 模板 + `role.createInstance(player)` | 已有设计，保留 |
| `ScriptableObject`（共享数据资产） | `Role`（将来的 `roles.yml`） | 跨玩家共享、不可变 |
| `SendMessage("Method")` | **不提供** | 字符串消息把耦合推到运行期，且慢 |
| 反射式消息分发 | **不采纳**（用虚方法） | Java 里没有理由为"消息"付反射成本 |
| 同一 GO 挂两个同名脚本 | **明确禁止** | 组件 id 在角色内唯一（顺手修掉跨类型去重失效的缺陷） |
| `DontDestroyOnLoad` | 见 §15 裁决点 1 | "角色是否跨断线重连存活" = 是不是 `DontDestroyOnLoad` |

---

## 2. 组件基类：钩子集合（final）

```java
package com.shadowHunterRolesPlugin.roleComponent;

public abstract class RoleComponent {

    /** 构造期注入；组件与它一对一，随组件存活。 */
    protected final ComponentServices svc;
> **过渡期注入方式（2026-09-16 队长裁定，阶段 4）**：本节规定的「**构造期注入**」是**终态形态**。**过渡期**（组件仍由无参 `Supplier` 创建、`ComponentFactory` 尚未落地）允许以 **`bind(ComponentServices)` 等价实现**，但**必须同时满足五条**：
> ① `bind` 由**容器**在 `supplier.get()` 之后**立刻**调用，且**在任何注册/钩子（含 `awake`）之前**；
> ② `bind` **只允许调用一次**：重复调用或 `awake` 之后调用 = **抛异常**；
> ③ 对外只暴露受保护访问器（如 `protected ComponentServices svc()`），**未绑定时抛 `IllegalStateException`**（**不允许静默 null**）；
> ④ **组件构造点唯一**（容器内单一创建路径），使 `bind` 不可能被遗漏；
> ⑤ **终态收尾**必须切回构造期注入（`ComponentFactory`），**删除 `bind`** —— 那一步本来就要动这 10 个组件的构造行。
> 过渡期偏差须在小结里申报，并由 t16 按「可观察行为等价」复核。

    protected RoleComponent(ComponentServices svc) {
        this.svc = Objects.requireNonNull(svc, "ComponentServices");
    }

    // ───────── 生命周期：框架按固定顺序**无条件**广播 ─────────

    /** 装配阶段：只解析跨组件依赖并缓存引用。幂等；不得改动任何玩家可见状态。 */
    public void awake() {}

    /** 开始生效：初始化数据、发放装备、登记定时器。 */
    public void start() {}

    /** 停止生效：与 start 严格对称。返回后框架自动回收本组件登记的资源。 */
    public void stop() {}

    // ───────── 每 tick ─────────

    /** 20 Hz。禁止阻塞、禁止直接写热键栏（渲染由框架负责，组件不参与）。 */
    public void update() {}

    // ───────── 领域事件 ─────────

    public void onSanTEChange(int pre, int now) {}

    // 钩子按需增补：用到再加（基类加 default 方法 = 一行改动，不需要新接口）
    // public void onEnergyChange(int pre, int now) {}
    // public void onBuffChange(BuffType type, boolean gained) {}
    // ⚠️ 生命变化不要照抄上面两行 —— 先读 §2.1 末尾"谁改的血"那一段
}
```

**为什么是这四个 + 一个事件钩子？** 依据实测——生产代码里的实现者数量：

| 钩子 | 生产实现者 | 处置 |
|---|---|---|
| `LifecycleAware`（awake/start/stop） | **5** 个（`DefaultSanTEZeroPunishment`、两个 `*EquipmentsPassive`、`RedBleedPassive`、`TestSend*`） | 进基类 |
| `UpdateAware.update` | **4** 个（两个 `AutoRecover*`、`RedBleedPassive`、`RedDeeplySorrowSkill`） | 进基类 |
| `SanTEChangeAware.onSanTEChange` | **2** 个（`DefaultSanTEZeroPunishment`、`RedDeeplySorrowSkill`） | 进基类 |
| `EnergyChangeAware.onEnergyChange` | **0** 个（只有那个已解除注册的测试桩） | **不进基类**，整条链路按 §3.4 处置 |
| 生命变化 | **0** 个（今天没有任何机制，也没有使用者） | **不加**（判据三）。将来要用时：基类加一行 `onHealthChange` 即可，但**必须先解决"谁改的血"** —— 生命不是框架独占的数值，原版伤害/药水/命令都能改它（详见 §2.1 末尾的说明） |

**命名约定（冻结）**：生命周期 = `awake` / `start` / `stop` / `update`（**无 `on` 前缀**，沿用上一轮已冻结的命名）；事件回调 = `onXxx`（`onSanTEChange` 保持现状）。

**基类纪律**：`RoleComponent` 只允许出现 ① `svc` 字段 ② 钩子方法。任何"顺手加个 helper"（例如 `healSelf`、`isEnemyNearby`）都属于越界——那些是端口或组件私有方法的职责。这条纪律是"fat base class"不变成上帝对象的唯一保险。

### 2.1 `onSanTEChange` 的派发契约（唯一保留的数值钩子）

**状态：SanTE 由框架独占真值** → 所有写入都经 `sante.*` 端口 → 钩子**不可能漏报**。

| 规则 | 说明 |
|---|---|
| **只在真变化时派发** | `pre == now` 不派发。今天 `setCurrentSanTE` 是**无条件** `callEvent`（`RoleInstance:510-516`），新设计把**钩子**收紧为"值变了才通知"。副作用是**顺带消除 O-6 的重复任务路径**：今天 `decreaseSanTE(10)` 在 SanTE 已为 0 时会再次通知 `DefaultSanTEZeroPunishment` → 又起一个惩罚任务并覆盖 `taskId`（旧任务永久泄漏）；收紧后 0→0 不再通知。实测无组件依赖"重复通知"这一行为 |
| **事件发布维持原样** | `SanTEChangeEvent` 的对外发布**不变**（仍是外部挂点，仍然无条件发）—— 收紧的只是组件侧钩子。这一点必须写清，否则会被误当成 API 变更 |
| **不经 Bukkit 事件总线派发给组件** | 今天是 `setCurrentSanTE` → `SanTEChangeEvent` → `RoleEventListener:20` → `triggerSanTEChange` → `*Aware`（为桥接到组件绕了一圈事件总线）。新设计**容器直接派发钩子**，删掉 `RoleEventListener` 的转发；事件本身照发 |
| **派发顺序** | 与其它运行期钩子一致：`SKILL → PASSIVE → MAIN_WEAPON` 分组，组内注册顺序（§7） |
| **异常策略** | 属**运行期**钩子 → **隔离**（不是 fail-fast）：单个组件抛异常只跳过它，并沿用"同一组件只报一次 + 恢复后提示一次"（§8） |
| **可重入（合并式）** | 若某个 `onSanTEChange` 内又写入 SanTE，**不递归派发**：记录"派发中"，待本轮遍历结束后按**最终值**补发一次（只会补发一次，天然不会无限递归）。今天没有同步重入的实例（`RedDeeplySorrowSkill.update:43` 的 `decreaseSanTE` 在 `update` 里；`DefaultSanTEZeroPunishment` 的"回到满值"在 40t 延迟任务里），但"在 `onSanTEChange` 里立刻返还 5 点"是合理写法，禁止重入会逼作者绕路 —— 所以选合并而不是抛异常 |

> **将来若要给能量/生命也加钩子**（基类加一行即可，这是判据一/二的红利），有两件事必须先想清楚：
> 1. **能量钩子的使用者今天为 0** —— 加了就是预置（判据三）；今天想反应能量变化的可行写法是在 `update()` 里自己比较上次值。
> 2. **生命不是框架独占的数值** —— 原版伤害、再生、药水、`/kill`、饥饿、摔落、其它插件都能改它。真要加，三种实现的取舍是：① 只报"经 `vitals` 端口的修改"（**不完整**，原版伤害完全不报，但零成本）；② 监听 `EntityDamageEvent`/`EntityRegainHealthEvent`（覆盖多数离散事件，能拿到 `DamageCause`，仍漏药水回血/属性变化/命令）；③ ticker 差分（**完整**，但 1 tick 延迟且不知道原因，每玩家每 tick 一次 `getHealth()`）。**这是"完整 vs 精确"的取舍，不能两者都要**，所以不适合现在顺手加。

---

## 3. `Aware` 取消与否：三条判据

### 3.1 判据一：**框架无条件广播的 → 基类钩子；框架按能力分派的 → 接口**

| 类型 | 语义 | 形态 | 例 |
|---|---|---|---|
| 通知型 | "框架每 tick/每个事件都通知你，你自己决定要不要做事" | **基类 default 空方法** | `awake` `start` `stop` `update` `onSanTEChange` |
| 能力型 | "你声明自己**能**被这样使用，框架据此把你登记到某个分派表里" | **接口** | `HotbarItem`（能进热键栏）、`HotbarActionable`（能被施放）、`CombatHook`（能参与攻击） |

Unity 也是这个分工：`Update` 是 `MonoBehaviour` 上的消息；`IPointerClickHandler`、`IDamageable` 是要显式实现的接口。

### 3.2 判据二：**接口数量不随钩子数量增长**

今天加一个"组件想知道自己被打死了"，要新增 `DeathAware` 接口 + 改容器 3 处 `instanceof` 循环。基类方案下是**一行** `public void onDeath() {}`，容器分派循环一行都不用改（统一遍历）。

### 3.3 判据三：**不许预置没有使用者的钩子**

项目自己在上一轮已确立"不为单一实现抽接口 / 不做投机抽象"（`架构重构意见.md` §6.1、§8）。同一条纪律适用于钩子：`onEnergyChange` 不是"以后可能有用"，而是**实测零使用者**，所以不进基类。而基类方案让"以后要用"的代价降到一行，因此"先不加"是零风险的。

### 3.4 实证：`EnergyChangeAware` 整条链路在生产里是死机制

链路：`RoleInstance.setCurrentEnergy():491` → `new EnergyChangeEvent` → Bukkit 事件总线 → `RoleEventListener:12-15` → `triggerEnergyChange` → 遍历 skills/passives/weapons 三个 map 做 `instanceof EnergyChangeAware` → **0 个生产组件命中**。

同时实测：**下游 `SHDFGamePlugin` 完全不引用这两个事件类**。

**处置**：
- **保留** `EnergyChangeEvent` 的发布（每次能量变化 1 个事件对象的成本可忽略；它是第三方插件可能监听的 Bukkit 级挂点，删除属未经确认的 API 破坏）。
- **删除**组件级的 `EnergyChangeAware` **接口** + `triggerEnergyChange` 的 `instanceof` 分派 + `RoleEventListener:12-15` 的事件转发。**`onEnergyChange` 钩子也不进基类**（零使用者，判据三）。
- `SanTEChangeEvent` 保留（2 个真实使用者 + 同类挂点价值）；`RoleEventListener:18-21` 的 SanTE 转发删除，改由容器直接派发 `onSanTEChange` 钩子（§2.1）。
- 生命变化今天**完全没有机制**，且没有使用者 → **不加**（将来要加的先决条件见 §2 表格下方说明）。

> 这一条不是洁癖：删掉的是**事件总线那一圈绕行 + 3 次 map 遍历 + `instanceof` 判断**（性能与可读性），而组件真正需要的那一个通知（SanTE）继续以基类钩子形式提供。

---

## 4. 组件身份

### 4.1 id：角色内全局唯一（跨类型）

今天 `Role.Builder` 的跨类型去重失效（`addPassive` 查的是 `skillSuppliers`、`addMainWeapon` 也查 `skillSuppliers`）→ 同名被动/武器可静默注册两次；而冷却表按 id 索引，重名会互相踩。

**新规则**：`Role.Builder.add*()` 对 `id` 做**跨三类 + 槽位**的唯一性校验，冲突 → 注册失败（fail-fast）。这条同时是"合并 skill/weapon 冷却为单一 `CooldownRegistry`"的前置条件。

### 4.2 能力接口（组件"是什么"）

```java
public interface HotbarItem {              // 能出现在热键栏里
    String id();
    Component displayName();
    Component description();
    Material icon();
    int cooldownTicks();
    int energyCost();                      // 主武器返回 0
    ItemKind kind();                       // SKILL / MAIN_WEAPON，仅用于渲染文案词
}
public interface HotbarActionable {        // 能被 L/R/Q 触发
    CastResult onCast(CastSignal signal);
}
public interface CombatHook {              // 能参与攻击结算（仅主武器）
    CastResult onAttack(AttackSignal signal);
}
```

三个基类的装配关系（**技能与主武器合并到 `ActiveComponent`，被动保持独立**）：

```java
public abstract class Skill        extends ActiveComponent { }                                   // kind = SKILL
public abstract class MainWeapon   extends ActiveComponent implements CombatHook { }             // kind = MAIN_WEAPON
public abstract class PassiveSkill extends RoleComponent { }                                     // 无热键栏、无冷却、无图标
```

**裁决：技能与主武器合并，被动不合并。**

- **合并它们的理由**（实测）：`Role.Builder.addSkill`/`addMainWeapon` **都写同一个 `slotMap`**（`Role.java:181`/`:216`），`updateHotbar():369-387` 对两者做的是同一件事，只用"id 在哪个 map 里"当类型标签（该分支里两个 `isReady` 局部变量**算了却没用**）；两张冷却表 + 6 个方法在"组件 id 角色内唯一"前提下纯冗余；两个 listener 共 7 个同形 handler；**`RoleAPI` 对外早就只暴露 `isSkillReady(player, String id)` 这种通用 id 查询，没有主武器版本** —— 对外契约早已统一，只有内部还分两套。两个 PDC key 也**实测对下游零引用**（`SHDFGamePlugin` 未使用 `SKILL_KEY`/`MAIN_WEAPON_KEY`/`*Utils`）。
- **不合并被动的理由**：`PassiveSkill` 不实现 `HotbarItem`/`HotbarActionable`，于是"这个组件能不能被施放"**依旧是编译期事实**。
- **保留两个薄子类**：`Skill`/`MainWeapon` 只声明 `kind()`（与 `CombatHook`），于是 **10 个具体组件文件与 `RoleRegistry` 的装配代码一个字都不用改**，领域词汇也保住。彻底删掉这两个子类要改 10 个文件却只省两个 5 行类，**不做**。
- **`CombatHook` 仍是编译期能力**，不是 `kind` 的运行期判断：模板武器 implements 它，技能不 implements。
- **装配 API 不变**：`addSkill(ComponentFactory<Skill>, int slot)` / `addMainWeapon(ComponentFactory<MainWeapon>, int slot)` 内部都委托给 `addHotbarItem(ItemKind, ComponentFactory<? extends ActiveComponent>, int slot)`。

**必须逐字保留的 kind 差异（合并的行为等价清单）**：

| 差异 | 今天 | 合并后 |
|---|---|---|
| 主武器参与攻击结算 | 类型 `MainWeapon.onAttack` | 能力接口 `CombatHook`（编译期） |
| 技能受沉默+眩晕；主武器只受眩晕 | listener/框架硬编码 | `kind()` 声明数据 |
| 文案词 Skill / MainWeapon | 两份渲染代码 | 统一渲染器读 `kind()` |
| **技能冷却名带秒数，主武器不带** | `Skill.getDisplayName` 追加、`MainWeapon` 不追加 | 保留（属玩家可见差异，进冻结清单） |
| 主武器槽 0 / 技能槽 1..3 | `slotMap` 数据 | 不变，仍是数据 |

**申报的行为变化**：`RoleAPI.isSkillReady` / `getSkillCooldownTick` 传入**主武器 id** 时，今天是"表里查不到 → 恒为就绪 / 哨兵值"（等于静默说谎），合并到单一冷却命名空间后**返回真实冷却**。属修正而非破坏（下游零调用），但必须在阶段报告里申报。

**将来的扩展点**：若出现第三种热键栏物品（如可堆叠的消耗品，带数量/消耗语义），**不要往 `ItemKind` 里塞** —— 那种差异不是 `kind` 能表达的，应抽 `Stackable` 之类的能力接口，而不是加第三个基类。

### 4.3 `ActiveComponent` 的成员（final）

`ActiveComponent` = 当前 `Skill` + `MainWeapon` **去重后的并集**，不多一个成员：

```java
public abstract class ActiveComponent extends RoleComponent
        implements HotbarItem, HotbarActionable {

    private final Component displayName;
    private final Component description;
    private final Material  icon;
    private final int       cooldownTicks;   // 原 getCooldown()，改名见下
    private final int       energyCost;
    private final ItemKind  kind;

    protected ActiveComponent(ComponentServices svc, String id, Component displayName,
                             Component description, int cooldownTicks, int energyCost,
                             Material icon, ItemKind kind) {
        super(svc, id);                       // id 上移到 RoleComponent（被动也要 id）
        ...
    }

    @Override public final Component getDisplayName()  { return displayName; }
    @Override public final Component getDescription()  { return description; }
    @Override public final Material  getIcon()         { return icon; }
    @Override public final int       getCooldownTicks(){ return cooldownTicks; }
    @Override public final int       getEnergyCost()   { return energyCost; }
    @Override public final ItemKind  getKind()         { return kind; }

    /** 默认：不做事、也**不**进冷却（与今天 listener 的行为一致，见下表）。 */
    @Override public CastResult onCast(CastSignal signal) { return CastResult.NO_COOLDOWN; }
}
```

两个薄子类，**保留今天的构造参数顺序**（`Skill` = cooldown→energyCost→icon；`MainWeapon` = icon→cooldown），因此 10 个具体组件的 `super(...)` 实参顺序无需调整：

```java
public abstract class Skill      extends ActiveComponent { }                       // kind = SKILL
public abstract class MainWeapon extends ActiveComponent implements CombatHook { }  // kind = MAIN_WEAPON，energyCost 恒传 0
```

**命名约定**：沿用工程的 JavaBean 风格（`getId`/`getDisplayName`/…），不引入 record 风格访问器。唯一改名：`getCooldown()` → **`getCooldownTicks()`**（今天同时存在 `getRemainingSkillCooldownTicks` 与 `...Seconds`，`getCooldown()` 的 tick/秒 歧义是真实风险；改名影响面仅 `MainWeaponListener:54` 一行）。

**默认返回值由实测代码定出**（`castSkillXxx` / `castMainWeaponXxx` 只回调、不启动冷却；只有攻击路径由 listener 无条件启动）：

| 路径 | 今天谁启动冷却 | 证据 | 合并后 |
|---|---|---|---|
| 技能：热键栏右/左/Q | 组件自己 | `RoleInstance:212-228`、`:230-246` | 返回 `CAST` → 框架启 |
| 主武器：热键栏左/右/Q | 组件自己 | `RoleInstance:286-329` | 同上 |
| 主武器：**攻击** | **listener 无条件启** | `MainWeaponListener:54` | `CombatHook.onAttack` 默认 `CAST` |
| **未重写的热键栏触发** | **不启**（只做就绪预检） | `SkillListener:61`、`MainWeaponListener:85,109,141` | 默认 `NO_COOLDOWN` ✓ |

所以 `ActiveComponent.onCast` 的默认对**两种 kind 统一**为 `NO_COOLDOWN`，不需要按 kind 分支。实测支撑：**7 个技能清一色只重写 `onRightClick`**，**2 个武器只重写 `onAttack`/`onLeftClick`**。

**明确不在 `ActiveComponent` 上的东西**：

| 不在这里 | 去哪 | 依据 |
|---|---|---|
| `createIconItem` / `getDisplayName(RoleInstance)` | 统一 `HotbarRenderer` | 4 个调用点全在渲染路径（`RoleInstance:377,385,412` + 两个技能覆写）；**刷新时机（push/poll）契约见指南 §3.5.1** |
| `Skill.Utils` / `MainWeapon.Utils` / 两个 PDC key | 框架侧单一 `HotbarTag` | 20 个调用点全在 `RoleInstance:429,430,440,441` 与两个 listener |
| 冷却读法 | `CooldownPort`（构造时绑定本组件 id） | 组件不该摸聚合根 |
| 能量档位常量（20 / 5） | 留在 `MeiqiheziJuejueMainWeapon` 的 `static final` | D5 |

**三个迁移陷阱（必读）**：

1. **主武器的 `energyCost` 必须传 `0`**：今天 `MainWeapon` 没有 energyCost 字段，武器图标也没有 `ENERGY LACK` 状态（该分支只存在于两个技能覆写）。把 Juejue 的 `5` 填进去会让武器图标多出一个**今天不存在的可见状态** —— 属可见行为变化。
2. **`getCooldown()` 是死数据（本次审计新发现的缺陷）**：全仓 `.getCooldown()` 只有 **1 个调用点**（`MainWeaponListener:54`，武器侧）；技能侧**零调用** —— 7 个技能全部自己写 `startSkillCooldown(getId(), 160/200/100/...)` 字面量，构造参数里的冷却值从未被读取。今天数值恰好都相等，但**声明值不是真值来源**，只改一处即静默不一致。新设计下 `CAST` → 框架用 `getCooldownTicks()` 启动，**声明值成为唯一真值来源**，该缺陷从机制上消失。迁移时必须逐个核对字面量与声明值一致（附录 A 的冻结清单）。
3. **合并本身不给组件文件增加改动**：`Skill`/`MainWeapon` 的构造参数顺序不变，因此 10 个具体组件只会经历阶段 4 本来就要做的 `svc` 注入（构造函数加一个参数 + `super(...)` 加 `svc`，每文件两行）；`RoleRegistry` 的 `::new` 方法引用形态不变。

### 4.4 组件之间怎么说话：`getComponent` 直接调公开方法

**最终裁决（作者指定，取代早期的 `contract/` 接口方案）**：组件通过基类方法 `getComponent(Class<T>)` 拿到**本角色实例里的另一个组件本体**，直接调用它的 public 方法。**不引入跨组件接口包，不引入协作者注册表**。

```java
public abstract class RoleComponent {
    protected final ComponentServices svc;

    /**
     * 取本角色实例内的另一个组件（按具体类优先）。
     * 未注册 → 返回 null；声明在 requires() 里的依赖已由 RoleLoader 在注册期校验，故非空。
     */
    protected final <T extends RoleComponent> T getComponent(Class<T> type) {
        return svc.components().get(type);
    }
}

/** roleComponent/red/RedBleedPassive.java —— 账本仍然是它的私有字段，对外只暴露方法 */
public class RedBleedPassive extends PassiveSkill {
    private final Map<UUID, Integer> bleedRecord = new HashMap<>();      // 私有，不再外泄
    private final Map<UUID, Integer> resolveRequests = new HashMap<>();

    public void applyStacks(Player victim, int stacks) { ... }           // 原 RedSanctifiedBladeMainWeapon:35-39 的逻辑搬进来
    public void requestResolve(Player victim, int stacks) { ... }        // 原 RedEvilShockSkill:33-34 的逻辑搬进来
    public int  stacksOf(Player victim) { ... }
    public static boolean canReceiveBleed(Player p) { ... }              // 纯判定，保持静态
}

// 消费方（红主武器）
RedBleedPassive bleed = getComponent(RedBleedPassive.class);
if (bleed != null) bleed.applyStacks(victim, BLEED_STACKS_PER_HIT);
```

**四条纪律**：

1. **按具体类查找优先**（`getComponent(RedBleedPassive.class)`）。按接口查找在"多个实现"时语义不明确（返回第一个），因此不作为推荐用法。
2. **构造期与字段初始化器内不得调用**：装配时组件列表仍在增长，此时查询会漏。容器在 `awake()` 之前把注册表冻结，冻结前调用直接抛 `IllegalStateException`（把"过早查询"变成响亮的错误，而不是"拿到 null → 静默降级"）。
3. **`awake()` 里解析一次并缓存到字段**（canonical form）：虽然 `getComponent` 随时可调（线性扫描 ≤6 个组件，成本可忽略），但缓存是约定 —— 也让 `stop()` 能对称地把引用置空。**热路径（`update`/事件）不要反复查**。
4. **耦合方向受限**：组件之间只允许**同角色包内**的具体类耦合（`roleComponent/red/*` 内部，`roleComponent/meiqiHezi/*` 内部）。**跨角色**引用（如 meiqihezi 的组件 import 红角色的组件）必须禁止 —— 那是真正的设计错误。

**为什么这样可以删掉聚合根上的组件通信支持**：通信完全发生在组件之间，容器只提供"取组件"这一个查找入口，因此 `RoleInstance` 不再需要承载任何通信设施 —— 见下节的删除清单。

**声明式依赖校验（对应 Unity 的 `[RequireComponent]`，可选保留）**：

```java
public interface RequiresComponents {
    List<Class<? extends RoleComponent>> requires();   // 用 List，避免 Class<?>[] 的 unchecked 写法
}
// RedSanctifiedBladeMainWeapon implements RequiresComponents → requires() = List.of(RedBleedPassive.class)
```

`RoleLoader` 在**角色注册时**（不是运行时）校验：该角色下每个组件声明的依赖，是否真有组件提供。缺失 → 拒绝注册该角色并打 `SEVERE` 日志。

- **价值**：把"装配缺 `RedBleedPassive` → 一局游戏打到一半 NPE"提前到**服务器启动时**报出来。
- **成本**：1 个接口 + `RoleLoader` 里一个校验循环 + 每个消费方 1 行。
- **如果你认为多余**：删掉它，让消费方自行判空即可（`if (bleed != null)`），`getComponent` 本身不受影响。目前全项目只有**一个**真实依赖（`RedBleedPassive` 被 2 个组件消费），所以这一条属于"便宜就留着"的性质。

### 4.4.1 从 `RoleInstance` 删除的组件通信支持（**全部**）

| 位置（今天） | 内容 | 处置 |
|---|---|---|
| `RoleInstance.java:53` | `private final Map<String, Object> context = new HashMap<>();` | **删除** |
| `:135-137` | `setContext(String, Object)` | **删除** |
| `:139-143` | `getContext(String, Class<T>)` | **删除** |
| `:145-147` | `removeContext(String)` | **删除** |
| `:149-151` | `hasContext(String)` | **删除** |
| `:795` | `clear()` 里的 `context.clear()` | **删除** |
| `RedBleedPassive` 的 `BLEED_RECORD_CONTEXT_KEY` / `BLEED_RESOLVE_REQUESTS_KEY` | 两个 public 字符串键 | **删除**（账本回归私有字段） |
| `RedBleedPassive.requestBleedResolve(RoleInstance, Player, int)`（`:67-76`） | 静态方法，**实测零调用点** | **删除**（其逻辑并入 `requestResolve(Player, int)`） |
| 早期设计里的 `Collaborators` 端口 + `roleComponent/contract/` 包 + `BleedLedger` 接口 | 未落地 | **取消**（本方案取代） |

**顺带修掉的两处真实缺陷**：今天两个消费方是**直接操作提供方的内部 Map** —— `RedSanctifiedBladeMainWeapon:35-39` 读写 `BLEED_RECORD_CONTEXT_KEY`、`RedEvilShockSkill:33-34` 往 `resolveRequests` 里 `put`，两者都绕过任何方法边界（`RedEvilShockSkill` 还没判空 → O-9 的 NPE）。改成 `getComponent(...).applyStacks/requestResolve` 之后，**数据归提供方所有、写入必须经它的方法**，这条耦合从"共享可变 Map"升级为"方法调用"。

---

## 5. 端口与服务（`ComponentServices`）

```java
public record ComponentServices(          // 共 10 个成员，见下方"为什么是 10 个"
        Self self,                 // 服务对象（见 §15 裁决点 1）
        EnergyPort energy,
        SanTEPort sante,
        VitalsPort vitals,
        CooldownPort cooldowns,    // 构造时已绑定本组件 id
        BuffPort buffs,
        FactionPort factions,
        DamagePort damage,
        TimerPort timers,          // = Unity 的 StartCoroutine
        ComponentLookup components // 唯一查找入口：RoleComponent.getComponent(T.class) 的实现（§4.4）
) {}

/** 组件查找：线性扫描容器内的组件列表（≤6 个，成本可忽略），按具体类优先。 */
public interface ComponentLookup {
    <T extends RoleComponent> T get(Class<T> type);   // 未注册 → null；注册表冻结前调用 → 抛异常
}
```

**为什么是 10 个（早期草稿里的三个成员已被删）** —— 逐个都按"有没有真实使用者"过了一遍：

| 被删成员 | 证据 | 理由 |
|---|---|---|
| `KeyFactory keys` | `roleComponent` 全树 `NamespacedKey`/`KeyFactory` **零命中** | 用它的全是框架侧（`Skill.Utils`/`MainWeapon.Utils`/`DamageUtil`/`BuffManager`），组件不创建 key |
| `Logger log` | 全树 `Logger`/`getLogger` **零命中** | 给组件 logger 会鼓励"就地 catch 吞异常"，与 §8"运行期隔离由框架统一处理（同组件只报一次）+ 恢复后提示"冲突 |
| `HotbarPort hotbar` | 组件侧**零路径需要它** | 渲染器的全部状态输入（`cooldowns` / `buffs` / `energy` vs 声明 `energyCost`）都是**框架可见**的：冷却变化、buff 变化、能量变化分别发生在各自的**端口适配器内部**，由适配器自己置脏即可；声明数据是 `final`，不可能变化。组件既不需要、也不应该控制渲染时机（那正是 6 份拷贝的成因）。**`markDirty()` 保留为框架内部 API（`HotbarRenderer`），不进 `ComponentServices`** |

> 与 §3.3 判据三（"不许预置没有使用者的钩子/成员"）一致：零使用者 = 不加。真出现"组件改了渲染器读不到的东西"这种情形时再加回一行即可。

**端口方法是白名单，1:1 映射实测的 20 个调用，一个都不许多**：

| 今天（实测调用次数） | 新归属 |
|---|---|
| `getBuffManager().canCastSkill()` (11) / `addBuff(STUN,100)` (1) | `buffs`（`canCastSkill` = 非 STUN 且非 SILENCE；`canUseMainWeapon` = 非 STUN。11 次里约 4 次来自两份渲染覆写，随渲染器删除，改由**渲染器**读同一个端口） |
| `getCurrentEnergy()` (10) / `decreaseEnergy(n)` (3) / `increaseEnergy(n)` (1) | `energy`（`current/tryConsume/gain`） |
| `startSkillCooldown(id,ticks)` (9) / `startMainWeaponCooldown(id,ticks)` (3) | `cooldowns`（`isReady/remainingTicks/start(ticks)/end`） |
| `isSkillReady(id)` (4) / `getRemainingSkillCooldownSeconds(id)` (4) | **组件不再需要**（全部来自两个图标渲染覆写，随统一渲染器删除） |
| `increaseSanTE` (4) / `setCurrentSanTE` (2) / `decreaseSanTE` (1) / `getCurrentSanTE()` (1) / `getMaxSanTE()` (1) | `sante` |
| `heal(n)` (2) | `vitals`（保留今天 `RoleInstance.heal` 的 clamp 语义：`min(当前+amount, Attribute.MAX_HEALTH)`）。**调用者**：`AutoRecoverSanTEHealthPassive:36 heal(1)`、`RedSolitaryArroganceSkill:65 heal(4)` |
| `isHostileTo(Player)` (5) / `getFaction()` (3) | `factions`。**调用者**：`Juejue:55`、`BloodySlash:61`、`CircleSlash:75`、`EvilShock:29`、`SolitaryArrogance:58`；`getFaction()` 三处都是把它转手传给 `SkillUtil` |
| `getContext` (3) / `setContext` (2) | **聚合根上的通信设施整体删除**；改为 `getComponent(RedBleedPassive.class)` 直接调方法（§4.4.1） |
| `setIsInSanTEPunishmentState` (3) | 组件私有字段（本来就不该上聚合根） |
| `getPlayer()` (1) | `self` |
| `DamageUtil.*`（静态，7 处） | `damage`（真伤/物理伤/击退；PDC 副作用收在一处，可单测）。**⚠️ 施动者可空**：`DefaultSanTEZeroPunishment:106` 调的是 `dealtTrueDamage(player, null, …)`，端口签名必须接受 `null` source（为空时跳过创造/旁观检查与"记录最后伤害者"），写成 `@NonNull` 会直接改行为 |
| `SkillUtil.hasEnemyInRange` (2) | `factions.hasEnemyInRange(radius)`。**必须保留今天的语义**：`SkillUtil:24` 把**未选角色的玩家也算敌人**（`other == null \|\| other.isHostileTo(faction)`） |
| `SkillUtil.getPlayersInSightLine` (1) | **保持静态几何工具**（`RedSolitaryArroganceSkill:54` 传 `5, 0.4`）。它做的是纯射线几何、**不查阵营**、零插件依赖 → 与 `ParticleUtil` 同类，**不要塞进 `factions`**（会误导成"阵营相关"） |
| `ParticleUtil.*` (2) | **保持静态工具**（纯 Bukkit 几何，无状态、无副作用） |
| 裸 `BukkitRunnable` / `Bukkit.getScheduler()`（3 + 5 处） | `timers` |

### 5.1 每个成员暴露什么（新侧 API 白名单）

| 成员 | 新 API（白名单，多一个都不许） | 职责 | 今天的对应调用者 |
|---|---|---|---|
| `self` | `player()`（可空；若裁决点 1 取方案 B 再加 `id()`） | 施动者本人，**取代回调签名里的 `Player` 参数**，且重连安全 | 显式调用仅 `DefaultSanTEZeroPunishment:39`；真正价值是消灭每个方法签名上的 `Player` |
| `energy` | `current()` / `tryConsume(n)` / `gain(n)` | 能量真值。`tryConsume` = **检查+扣减合一**，取代今天"先 `getCurrentEnergy()` 判断、再 `decreaseEnergy()`"的两步写法 | 读 10 处；扣 3 处（BloodySlash 8 / CircleSlash 15 / Juejue 5）；回 1 处 |
| `sante` | `gain(n)` / `decrease(n)` / `set(v)` / `current()` / `max()` | SanTE 真值 | `AutoRecoverSanTEHealth:29,35`、`RedBleed:128,213`、`EvilShock:37`、`DeeplySorrow:43`、`DefaultSanTEZero:110` |
| `vitals` | `heal(amount)` | 生命；clamp 策略的唯一实现 | `AutoRecoverSanTEHealth:36 heal(1)`、`SolitaryArrogance:65 heal(4)` |
| `cooldowns` | `isReady()` / `remainingTicks()` / `start(ticks)` / `end()` | 冷却，**构造期已绑定本组件 id**（不再传 id） | 9+3 处 `start*Cooldown`。**正常路径组件不调 `start`** —— 返回 `CAST` 让框架按声明值启动；`start` 只作逃生舱②（`DeeplySorrow` 引导期刷新） |
| `buffs` | `canCastSkill()` / `canUseMainWeapon()` / `add(type,ticks)` / `has(type)` / `remainingTicks(type)` | 禁用/减益闸门（`canCastSkill` = 非 STUN 且非 SILENCE；`canUseMainWeapon` = 非 STUN） | 逻辑约 7 处 + 渲染 4 处（后者归渲染器）；`DefaultSanTEZero` 负责加 STUN |
| `factions` | `isHostile(victim)` / `faction()` / `hasEnemyInRange(radius)` | 阵营与敌对判定，**取代 `RoleManager.getInstance()`** | 5 处 `isHostileTo`；`DefaultSanTEZeroPunishment:85` 用 `faction() == Faction.SHADOW` 选标题；两个 `AutoRecover*` 用 `hasEnemyInRange(10)` |
| `damage` | `trueDamage(v,s,a)` / `trueDamage(v,s,a,kb)` / `physicalDamage(v,s,a)` / `physicalDamage(v,s,a,kb)` | 伤害原语；**唯一的 PDC 副作用点** | 7 处 `DamageUtil.*`；**`s` 可为 `null`** |
| `timers` | `run(r)` / `runLater(ticks,r)` / `runRepeating(delay,period,r)` / `track(task)` | 协程：按组件登记，`stop` 后框架兜底取消 | 4 处裸 `runnable` + 5 处调度器 |
| `components` | `get(Class<T>)`（经 `RoleComponent.getComponent`） | 组件查找（§4.4） | `RedBlade` / `EvilShock` → `RedBleedPassive` |

> **不在 `ComponentServices` 里的两类东西**：① 无状态、无插件依赖的纯几何工具 —— `ParticleUtil`、`SkillUtil.getPlayersInSightLine`（保持静态类直接调用，与今天一致）；② 框架内部机制 —— 渲染器与 `markDirty()`（组件不参与渲染，见 §5 上方删除说明与指南 §3.5.1）。

**核心机制：端口必须是独立适配器，不能让 `RoleInstance` 直接 `implements` 它们**——

```java
final class EnergyPortImpl implements EnergyPort {     // 包级私有，无聚合根访问器
    private final RoleInstance owner;                  // 反向引用只存在这里
    EnergyPortImpl(RoleInstance o) { this.owner = o; }
    @Override public int current() { return owner.currentEnergy; }
    @Override public boolean tryConsume(int n) { return owner.tryConsumeEnergy(n); }
}
```

否则组件写一句 `(RoleInstance) svc.energy()` 就能拿回聚合根，而 grep 检查完全看不出来 —— 设计意图会被悄悄破坏。

**`timers` = 协程语义**：`svc.timers().runRepeating(task, 0, 20)` 登记进**该组件专属**的资源表；`stop()` 返回后框架兜底 `cancel()` 全部未取消的任务。组件忘了取消也不会泄漏（这正是今天 3 处僵尸伤害的根因）。

### 5.2 `Self` 契约（唯一玩家句柄）

```java
/** 本组件所依附的对象。不是"玩家查询工具"，而是 this.gameObject 的等价物。 */
public interface Self {
    Player player();      // 可空性取决于裁决点 1（§15）
    UUID id();            // 方案 B 必备（实时查询用）；方案 A 下可留可去
}
```

**职责**：回答"我是谁"。它是一个**方法**，不是构造时固定的值 —— 这一点是全部意义所在。

**为什么需要它（三条，缺一不可）**：

1. **取代回调签名里的 `Player player` 参数**。今天每个钩子都是 `update(Player player, RoleInstance instance)`；新设计里 `player` 改为 `svc.self().player()` 实时取，`instance` 被删除，于是签名收敛成 `update()` / `onCast(CastSignal)` / `onAttack(AttackSignal)`。
2. **重连安全（核心动机）**。今天的事实是"最坏组合"：`RoleManager.playerRoleMap` 是 **`UUID` 键**（`RoleManager.java:12,63-68`）→ 重连后 `getRoleInstance(新Player)` **仍能查到同一个 `RoleInstance`**（角色关联存活）；但 `RoleInstance.player` 是 **`private final`、构造时赋值一次、永不刷新**（`:29,69`）→ 实例内部的 `Player` 引用指向**登出前的旧对象**。把"取玩家"从**字段读取**变成**方法调用**后，"跨重连存活"只需要改 `Self` 的实现（`Bukkit.getPlayer(id)`，`selectRole` 里已有这个现成用法 `RoleManager:46`），**不需要改任何组件**。
3. **它是 I-7 与"组件不 import `RoleManager`"能成立的前提**：`Self` 是**唯一**被允许把"玩家"交给组件的地方。

**禁止**（三条）：
1. 不用它读/写状态 —— `setHealth` 走 `vitals`（裁决点 6），能量/SanTE/Buff 各走自己的端口。`Self` 只回答"人是谁"。
2. 不把 `player()` 的结果存进字段或长任务闭包 —— 每次现取（纪律 5）。**存了就等于把今天这个 bug 搬进新架构。**
3. 不把它当作"离线也能用"的句柄（见下）。

**`player()` 的可空性（已随裁决点 1 定案）**：采用**"掉线即销毁"**（§9.1）。因此：

- **实例存活期内 `player()` 恒非空** —— 不在线就不存在实例（掉线即 `clearRole`）。
- 组件里的 `if (self == null || ...)` 判空**从"防御失效引用"退化为"防御死亡瞬间"**：`update()` 与事件钩子仍可能在一 tick 内先于 `clearRole` 执行，而那时 `isDead()` 可能为 true。所以样例里的守卫保留，但语义变了 —— 它挡的是"同一 tick 内的死亡竞态"，不是"离线"。
- `id()` 仍**必需**（`clearRole(uuid)`、日志、以及任何按 UUID 的查询）。
- **不再存在**"挂起期间 `player()` 返回 null"这一情形，也不再需要"离线时暂停 ticker"的框架规则 —— 那两条都随保留期方案一起作废。

**今天重连后的预期症状（未实测，需在真服上确认一次）**：角色关联还在（UUID 键命中）→ 玩家重连后仍"有角色"；但实例里的旧 `Player` 对象已脱离世界 → 位置类判定用**登出点坐标**、粒子/音效/消息发不到本人、热键栏渲染写进**已失效的背包对象**（表现为冷却倒计时不再刷新）。验证方式：选角色 → 施放技能使进入冷却 → 掉线重连 → 观察倒计时是否继续刷新、技能是否按新位置结算。

---

## 6. 容器职责（`RoleInstance` = GameObject + UpdateManager）

**必须做**：
1. 持有组件集合与 `byId` 索引；
2. 按固定顺序广播钩子（§7）；
3. 提供端口适配器与 `ComponentServices`；
4. 作为能量 / SanTE / 生命 / 阵营 / 冷却 / Buff 的**唯一真值**；
5. 热键栏构建与脏刷新（组件不参与渲染）；
6. 唯一 ticker（合并 `BuffManager` 的 buff tick）；
7. 生命周期与资源回收；
8. 对外只保留 `RoleAPI` 需要的聚合查询。

**必须不做**：
- 不再向组件暴露 20 个方法（对外只剩 `RoleAPI` 路径 + 端口）；
- 不再持有 `Map<String,Object> context`；
- 不再持有"惩罚状态"这类**某个组件专属**的标志位。

---

## 7. 分派顺序（含一处必须申报的确定性修正）

**今天的实际顺序**（实读）：

| 阶段 | 顺序 |
|---|---|
| `awake` / `start` | skills → passives → weapons |
| `stop` | **weapons → passives → skills**（逆序） |
| `onSanTEChange` | skills → passives → weapons |
| `update` | skills → passives → weapons，**每个组件独立 try/catch** |

**问题**：`skillMap` / `passiveMap` / `mainWeaponMap` 都是 `HashMap`（`RoleInstance.java:45-47`）→ **同类型内部的顺序由哈希决定，即"未定义"**。

**新规则（确定性）**：
1. **存储合并、分派分组**：热键栏主动组件（技能 + 主武器）存进**一张** `LinkedHashMap`（`I-12`），但**分派仍按今天的三组进行** —— `SKILL → PASSIVE → MAIN_WEAPON`，`stop` 整体逆序（`MAIN_WEAPON → PASSIVE → SKILL`）。存储的合并**不得**改变分派顺序，否则无法证明与今天等价（今天主武器在被动之后，合并成"主动组件一起跑"会把它挪到被动之前）。
2. 组内顺序 = 注册顺序（`LinkedHashMap`）。
3. 装配顺序 = `Role` 模板里的注册顺序（`Role.Builder` 已是 `LinkedHashMap`，`RoleRegistry` 的装配顺序即最终顺序）。
4. 实现方式：容器在构造后按 `kind()` 建两个视图列表（`skills` / `weapons` + `passives`），分派走视图，存储仍是单表。

**这是一处行为变化，必须申报**：今天组内顺序未定义，所以无法证明"顺序敏感的组合"在新旧实现下表现一致。实测顺序敏感的组合只有**一组**：红角色的 `DefaultSanTEZeroPunishment`（被动的 `onSanTEChange`）与 `RedDeeplySorrowSkill`（技能的 `onSanTEChange`）都会在 SanTE ≤ 0 时动作。新实现的组序是技能先于被动（与今天一致），需在验收时用"红角色把 TE 打到 0"这一场景人工确认。
（另：**今天没有任何主武器实现 `update`/`onSanTEChange`**，所以主武器在组序里的位置目前无可观测影响 —— 但规则仍按上述固定，不依赖这一点侥幸。）

---

## 8. 异常隔离策略（有意区分两类）

| 阶段 | 策略 | 理由 |
|---|---|---|
| `awake` / `start` | **不隔离，fail-fast**（异常向上抛，角色选择失败） | 装配错误必须在开局就响，不能"带着半装配的角色进场" |
| `update` | **逐组件隔离**（保持今天 `runComponentUpdate` 语义：只跳过该组件、首次报 `SEVERE`、恢复后报一次 INFO） | 一个组件的 bug 不该让其他组件停摆 |
| `onSanTEChange` 等事件 | **扩展为逐组件隔离**（今天**未隔离**，一个组件抛异常会中断整轮广播） | 事件广播是"通知"，一个听众炸掉不该影响别人。**属有意变更，需申报** |

隔离实现统一走一个 `dispatch(component, action)` helper（今天的 `runComponentUpdate` 泛化），日志前缀带 role + component id。

---

## 9. 生命周期与资源回收（`clear()` = `OnDestroy`）

```
构造(RoleInstance):
  services = buildServices(player)              // ComponentLookup 此时不可用
  components = 按模板工厂创建（构造期拿到 svc）
  components.freeze()                           // 之后 getComponent() 才合法
  for c: c.awake()                              // 解析跨组件依赖并缓存到字段
  for c: c.start()                              // 登记资源（timers 归各自组件）
  startTicker(); flushHotbar()

销毁(clear()):
  for c in reversed: c.stop()                   // 逆序，对称（stop 里把缓存的组件引用置空）
  for c: timers.cancelAll(c)                    // 框架兜底
  stopTicker()
  清理：热键栏 / 本系统施加的属性修饰符 / **只回收本系统施加的药水效果**（记账）
```

**装配期校验**（阶段 4 落地，与 §4.4 配套）：注册表冻结前调 `getComponent()` → 抛 `IllegalStateException`；若组件声明的 `requires()` 在角色里无提供者 → 角色注册阶段就报错。

### 9.1 掉线即销毁角色实例（作者最终裁决，取代早期的"保留期"方案）

**裁决**：玩家掉线 → **立即完整销毁角色实例**，不做挂起、不做保留、不引入配置文件。语义上与死亡一致：**掉线 = 死亡 = `clearRole`**。

**为什么这条取代了"配置化保留期"**：早期方案（挂起 / 恢复 / 保留期计时 / `config.yml`）的唯一目的是"让角色跨重连存活"。裁决改为"掉线即销毁"后，挂起、恢复、计时器、`pendingCleanup` 的**主路径**全部变成死代码；同时因为不再有第二个取值使用者，**`config.yml` 也不再需要引入**（本工程继续没有配置文件）。实现因此收缩为一条事件 + 一次现有调用。

**今天的事实（实测，属泄漏，本裁决彻底修掉）**：全仓无 `PlayerQuitEvent`；`playerRoleMap` 是 `UUID` 键且**从不移除**；`updateTaskId` 只在 `clear()` 里取消（`:780`），而 `clear()` 只在**死亡/换角色**时调用 → 每个曾选过角色的玩家登出后实例永久驻留，且 ticker 与 `BuffManager` updater **各 1 个 1-tick 任务继续跑**，每 tick 对失效 `Player` 执行一次热键栏全量重建（O-25）。

**契约**：

| 事件 | 动作 |
|---|---|
| **掉线**（`PlayerQuitEvent`） | **立即 `clearRole(uuid)`** → 走完整的 `clear()`：`stop()` 全部组件（逆序）→ 取消全部组件资源 → 停 ticker → 清热键栏 → 移除 `MAX_HEALTH` 属性修饰符 → **只回收本系统施加的药水效果**（记账）→ 清冷却/Buff 表 → 从 `playerRoleMap` 移除 |
| **重连** | 与"新玩家"完全一致：**没有角色**，需重新选角色。不刷新引用、不恢复状态（旧实例已不存在） |
| **死亡** | 保持现状（`PlayerListener:21-27` 已 `clearRole`）—— 与掉线同一条路径 |

**必须实测的一个未知项（决定是否还需要兜底机制）**：`PlayerQuitEvent` 触发时 `Player` 对象通常仍有效，而玩家的存档写入发生在事件处理**之后**（Bukkit 的实现顺序），因此**在事件内调用 `clear()` 应当能真正把属性修饰符与药水清除持久化**。但这依赖服务端实现顺序，**必须实测确认**：掉线 → 重新登录 → 检查最大生命值是否回到 20、药水是否已清。

- **若实测通过**（预期）：无需任何兜底机制，实现就是"一个事件 + 一次 `clearRole`"。
- **若实测发现未持久化**（即存盘早于事件处理）：才补 **`pendingCleanup`**（`Map<UUID, Set<CleanupKind>>`，`CleanupKind ∈ {ROLE_HEALTH_MODIFIER, ROLE_POTION_EFFECTS, HOTBAR_ITEMS}`）在 `PlayerJoinEvent` 里先清理再允许选角色。该兜底同时可复用于 O-8（`onDisable` 不做完整 `clear()` 的残留）。**不预先实现**——不为尚未证实的假设写机制。

**代价与已知取舍（明文记录）**：

- **这是可见的玩法变化**：今天掉线重连角色还在（虽然绑在失效对象上），改后**掉线即掉角色**。已登记进指南 §6.2 的"有意行为变化"清单。
- 服务器重启的语义不变：`playerRoleMap` 不持久化，重启后玩家无角色。
- 收益：O-25 登出泄漏被彻底消除（不再需要"保留期有多长、内存峰值多高"这类权衡），`Self` 的失效引用问题也从根上消失 —— 实例永不跨越会话，`player()` 在实例存活期内**非空**。

**`Self` 契约随之简化（见 §5.2）**：不再有"挂起期间返回 null"的情形；`id()` 仍需要（`clearRole(uuid)` 与日志使用）。

---

## 10. 施放 / 攻击管道（能力分派）

**回调的输入与输出**（组件的两个"语言"）：

```java
// 输入：本次调用特有的信号（不可变 record，只带这一次的数据）
public record CastSignal(CastTrigger trigger) { }     // trigger ∈ { RIGHT_CLICK, LEFT_CLICK, DROP }
public record AttackSignal(Player victim) { }         // 施动者永远是自己：svc.self().player()

// 输出：框架与组件之间唯一的结果语言
public enum CastResult {
    CAST,                 // 施放成功 → 框架按 getCooldownTicks() 启动冷却
    NO_COOLDOWN,          // 组件已自行处理（含"不做事"）→ 框架不动冷却
    REJECTED_DISABLED,    // 被禁用（沉默/眩晕）→ 框架给反馈
    REJECTED_NO_ENERGY    // 能量不足 → 框架给反馈
}
```

> **不设"自定义消息"通道**（早期草稿里的 `Rejected(msg)` 已取消）：全项目零个组件今天会发送反馈文本，四个常量足够；需要时再加常量，而不是先开一个 `Component` 参数的口子。反馈文本由**框架**持有（单一出处、措辞一致），组件只表达"为什么被拒绝"。

```java
handleCast(CastTrigger trigger):          // listener 只做"事件 → trigger"翻译
  item = slotRegistry.get(trigger)        // 无对应物品 → return
  if (!precheck(item).ok()) { hotbar.markDirty(); return; }   // 只读声明字段
  result = ((HotbarActionable) item).onCast(new CastSignal(trigger))
  switch (result) { CAST        → cooldowns.start(item.getId(), item.getCooldownTicks());
                    NO_COOLDOWN → ;                       // 组件自己处理完了
                    REJECTED_*  → 反馈（文本由框架持有） }
  hotbar.markDirty()                      // 刷新时机契约见指南 §3.5.1

handleAttack(victim):
  event.setCancelled(true);               // ← 必须最先（现状如此）
  weapon = 手持主武器；无 → return
  if (!precheck(weapon).ok()) return;
  result = ((CombatHook) weapon).onAttack(new AttackSignal(victim))
  ...同 CastResult 分派
```

`precheck` 只读声明值：冷却 / 沉默（技能）/ 眩晕（技能与武器）。**能量不是硬闸门**（否则 `MeiqiheziJuejueMainWeapon` 的"能量不足分支"永远看不到）。

> 行为等价性论证表见 `docs/最终重构指南.md` §3.4，本次设计不改其结论，只把"上下文"换成"信号"。

---

## 11. 组件迁移映射（10 个组件 → 新写法）

| 组件 | 今天依赖 | 迁移后 | 批次 |
|---|---|---|---|
| `RedEvilShockSkill` | `canCastSkill`、`isHostileTo`、`getContext`(写请求)、`increaseSanTE`、`startSkillCooldown` | `buffs`、`factions`、`getComponent(RedBleedPassive.class).requestResolve`（**判空**）、`sante`、`cooldowns` | ① |
| `MeiqiheziUnconcernSkill` | `canCastSkill`、`startSkillCooldown` | `buffs`、`cooldowns` | ② |
| `RedDeeplySorrowSkill` | `canCastSkill`、`startSkillCooldown`、`decreaseSanTE`、`SanTEChangeAware` | `buffs`、`cooldowns`、`sante`、`onSanTEChange` 覆写 | ② |
| `RedSolitaryArroganceSkill` | 全局调度器、`isHostileTo`、`heal`、`startSkillCooldown` | `timers`（不再有僵尸伤害）、`factions`、`vitals`、`cooldowns` | ③ |
| `MeiqiheziEquipmentsPassive` / `RedEquipmentsPassive` | `LifecycleAware` | `start/stop` 覆写 + `svc.self()`；**stop 必须对称回收** | ④ |
| `AutoRecoverEnergyPassive` / `AutoRecoverSanTEHealthPassive` | `update`、`getFaction` + `SkillUtil` | `update` 覆写 + `factions.hasEnemyInRange` / `energy.gain` / `sante.gain` / `vitals.heal` | ⑤ |
| `RedBleedPassive` | `update`、`getContext`×2、`getPlayer`、`DamageUtil` | `update` 覆写 + **公开 `applyStacks`/`requestResolve`/`stacksOf`**（账本私有化，删两个 `*_CONTEXT_KEY` 与零调用的静态方法）+ `damage` + `self` | ⑥ |
| `RedSanctifiedBladeMainWeapon` | `getContext`、`DamageUtil`、`startMainWeaponCooldown` | `getComponent(RedBleedPassive.class)`（`awake` 解析并缓存）、`damage`、`cooldowns` | ⑦ |
| `MeiqiheziJuejueMainWeapon` | `getCurrentEnergy`、`decreaseEnergy`、`startMainWeaponCooldown`、`isHostileTo`、`DamageUtil` | `energy`、`cooldowns`、`factions`、`damage` | ⑦ |
| `MeiqiheziBloodySlashSkill` / `MeiqiheziCircleSlashSkill` | 全局调度器、能量门槛、**两份图标渲染覆写** | `timers`、`energy`、`cooldowns`；**删除两组覆写**（统一渲染器） | ⑧ |
| `DefaultSanTEZeroPunishment` | `SanTEChangeAware`、`getFaction`、`getBuffManager().addBuff`、`setIsInSanTEPunishmentState`、`DamageUtil`、全局调度器 | `onSanTEChange` 覆写 + `factions`、`buffs`、**私有字段**、`damage`、`timers` | ⑨ |

---

## 12. 组件写作模板（Unity 风格：一个文件一个组件）

```java
package com.shadowHunterRolesPlugin.roleComponent.red;

/** 煞气震赫：5 格内敌人致盲+缓慢，结算 5 层流血，回 10 点 SanTE。 */
public final class RedEvilShockSkill extends Skill implements RequiresComponents {

    private static final int COOLDOWN_TICKS = 120;
    private static final double RADIUS = 5;
    private static final int BLIND_TICKS = 61, SLOW_TICKS = 61;
    private static final int RESOLVE_STACKS = 5, SANTE_GAIN = 10;

    private RedBleedPassive bleed;                 // awake 解析，stop 置空

    public RedEvilShockSkill(ComponentServices svc) {
        super(svc, "red_evilShock_skill", Component.text("煞气震赫"),
              Component.text("对周围5格范围内的敌人造成3秒致盲和缓慢III，结算他们5层流血。恢复[红]的10点TE值"),
              COOLDOWN_TICKS, 0, Material.REDSTONE);
    }

    @Override public List<Class<? extends RoleComponent>> requires() { return List.of(RedBleedPassive.class); }

    @Override public void awake() { bleed = getComponent(RedBleedPassive.class); }
    @Override public void stop()  { bleed = null; }

    @Override
    public CastResult onCast(CastSignal signal) {
        if (!svc.buffs().canCastSkill()) return CastResult.REJECTED_DISABLED;

        for (Player victim : svc.self().player().getLocation().getNearbyPlayers(RADIUS)) {
            if (!svc.factions().isHostile(victim)) continue;
            victim.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(BLIND_TICKS, 1));
            victim.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(SLOW_TICKS, 3));
            if (bleed != null) bleed.requestResolve(victim, RESOLVE_STACKS);   // 契约要求判空
        }
        svc.sante().gain(SANTE_GAIN);
        svc.self().player().getWorld().playSound(svc.self().player().getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
        return CastResult.CAST;      // 框架按 COOLDOWN_TICKS 启动冷却
    }
}
```

**组件纪律清单**（写新组件时逐条对照）：
1. 不 `import` 插件主类、不 `RoleManager.getInstance()`、不 `Bukkit.getScheduler()`（`getContext` 系列方法已整体删除，见 §4.4.1）；
2. 起任务一律 `svc.timers()`（或 `svc.timers().track(...)`），**不写裸 `BukkitRunnable`**；
3. 有 `start` 必有对称 `stop`；跨组件引用用 `getComponent(X.class)` 在 `awake` 解析并缓存到字段，在 `stop` 清空；
4. `Player` **不得**存进长生命周期字段或长任务闭包（只能存 UUID 或每次 `svc.self().player()` 实时取）；
5. 数值提成类顶部命名常量；方法体里不留裸数字（几何/特效参数除外）；
6. 组件不碰热键栏：**不造 `ItemStack`、也不请求刷新**（`markDirty()` 是框架内部 API；置脏点在端口适配器与 ticker poll 里）。

---

## 13. 明确不采纳的 Unity 特性

| 特性 | 不采纳的理由 |
|---|---|
| `SendMessage` 字符串消息 | 把耦合推到运行期、无编译期检查、慢 |
| 反射扫描"组件是否定义了某方法" | Java 里虚方法/default 方法已给出 95% 的写法收益，且可内联 |
| `FixedUpdate` | 服务端只有一个 20 Hz 固定 tick，多一个阶段只会让"哪段逻辑在哪跑"更难查 |
| 把 `LateUpdate`/渲染钩子暴露给组件 | 渲染必须是框架职责（今天 6 份拷贝就是这么长出来的） |
| Script Execution Order 优先级数字 | 只有 ≤6 个组件，固定阶段顺序 + 注册顺序足够；优先级数字会变成隐式耦合 |
| 同一容器挂多个同 id 组件 | 明确禁止（id 唯一性是冷却表与协作查找的前提） |
| 组件间事件总线 / 订阅发布 | 真实协作点只有流血账本一个，**`getComponent` 直接调方法就够**；总线会让依赖关系重新变成运行期才知道 |
| `contract/` 跨组件接口包 | 作者的最终裁决：组件直接拿组件本体、调公开方法（§4.4），接口层是多余的一跳 |
| 注解/反射注入（`@Inject`） | 显式构造参数更易读，且零扫描成本 |
| `WorldPort` / `EffectPort` 等全面端口化 | 成本高、收益只是"能单测粒子"；只有 `DamageUtil` 值得端口化（跨框架边界 + PDC 副作用 + 决定流血逻辑可测性） |

---

## 14. 验收不变量（在指南 I-1..I-7 基础上新增/修订）

| # | 不变量 | 检查方式 |
|---|---|---|
| **I-7**（修订） | `roleComponent` 下不得出现聚合根类型 | `grep -rn "RoleInstance" src/main/java/**/roleComponent` → 空 |
| **I-8** | 不得再用 `instanceof *Aware` 分派生命周期/事件（能力分派除外） | `grep -rn "instanceof LifecycleAware\|instanceof UpdateAware\|instanceof SanTEChangeAware\|instanceof EnergyChangeAware"` → 空 |
| **I-9** | 端口方法集合 == §5 白名单（20 个调用的 1:1 映射） | 人工逐条比对 `ComponentServices`/端口接口与 §5 表 |
| **I-10** | 组件声明了跨组件依赖就必须在注册期可校验 | 每个 `RequiresComponents` 的 `requires()` 都在 `RoleLoader` 校验路径覆盖内（该机制可选，见 §4.4） |
| **I-13** | 组件间通信**不得**经过聚合根 | `grep -rn "getContext(\|setContext(\|hasContext(\|removeContext(" src/main/java` → 空（4 个方法连同 `context` 字段一起删除）；`roleComponent` 下只能出现 `getComponent(...)` |
| **I-11** | 分派顺序确定：同类型内部 = 注册顺序 | 三个 map 必须是 `LinkedHashMap`（代码审查 + 单测：注册 A、B，断言 `update` 被调顺序为 A→B） |
| **I-12** | 热键栏相关结构**各只有一份** | `skillCooldowns`/`mainWeaponCooldowns`/`skillMap`/`mainWeaponMap`/`SKILL_KEY`/`MAIN_WEAPON_KEY`/`createIconItem`/`getDisplayName` 在 `src/main/java` 下各只允许出现 **1 处**（`SKILL_KEY` 在阶段 4 渲染器统一前可暂存 2 处，需在报告里说明） |
| **I-14** | `onSanTEChange` **只在值真变化时**派发，且**不递归** | `pre == now` 不派发（`SanTEChangeEvent` 的对外发布不变）；派发期间的同类型写入合并为"结束后按最终值补发一次"。检查：单测覆盖 `gain(0)`、`set(当前值)`、以及在钩子内改 SanTE |
| **I-15** | `onSanTEChange` 由容器**直接派发**，不经 Bukkit 事件总线 | `grep -rn "triggerSanTEChange\|triggerEnergyChange" src/main/java/**/listener` → 空；`RoleEventListener` 的转发已删 |
| **I-16** | **掉线即销毁角色实例，零残留** | `PlayerQuitEvent` 有处理且走完整 `clearRole(uuid)`；掉线后该玩家：`playerRoleMap` 无条目、无 ticker、无 `BuffManager` updater（任务计数可验证）；**"重登无角色"只在本插件单独运行时成立**（集成环境下下游 SHDF 可能重建角色，不得据此判"裁决未生效"）；**采样前提：除被测者外至少有一名玩家在线**（集成环境下否则走 SHDF 空服早退分支，证据无效 —— 见指南 §6.4.1）；属性修饰符与药水已清除（若实测发现未持久化，则必须有 `pendingCleanup` 兜底并在报告中说明） |

---

## 15. 裁决记录（作者已定，**后续不再讨论**）

1. **角色是否跨断线重连存活 —— 已裁决：掉线即销毁（不保留）**（详见 §9.1）。
   - 现状（实测）：全仓无 `PlayerQuitEvent`/`PlayerJoinEvent`，`playerRoleMap` 为 `UUID` 键且从不移除，`RoleInstance.player` 是 `final` 字段且不刷新 → **角色关联活着、引用失效**（最坏组合），且登出后实例与 2 个 1-tick 任务永久驻留（O-25）。
   - 裁决（**最终**，取代中间的"配置化保留期/60 秒"方案）：`PlayerQuitEvent` → **立即完整 `clearRole(uuid)`**，**不挂起、不保留、不引入 `config.yml`**；语义与死亡一致。挂起/恢复/计时器/`pendingCleanup` 主路径随之作废（`pendingCleanup` 仅作实测失败时的兜底）。
   - 连带简化：`Self` 在实例存活期内**恒非空**，失效引用问题从根上消失；引导型技能"离线冻结"这一子项**自动作废**（实例已不存在）。
   - 这是**可见的玩法变化**（今天掉线重连角色还在），已登记进指南 §6.2。
2. **`heal()` → 归 `vitals` 端口**（已裁决）：组件不直接 `setHealth`，clamp 策略只保留一份实现。
3. **组件读角色模板数据 → 一个都不给**（已裁决）：`maxHP`/`baseATK` 均不可读；将来需要再加 `RoleProfile`。
4. **`EnergyChangeEvent` → 保留对外发布、删除组件级广播**（已裁决，§3.4）：`onEnergyChange` **不进基类**（零使用者）；`SanTEChangeEvent` 与其组件钩子**保留**（2 个真实使用者），只把派发方式改成容器直连（§2.1）。
5. **`RequiresComponents` 注册期校验 → 保留**（作者未特别指定，按"便宜且能提前报错"执行）：全项目只有 **1 个**真实跨组件依赖（`RedBleedPassive` 被 2 个组件消费）。删掉它不影响 `getComponent` 本身，只把"缺依赖"的发现时机从**开服注册**推回**运行期 NPE**。如需删减，须在阶段 4 的报告里显式说明。
6. **槽位冲突 / 装备回收边界 / 反馈形式 / 阶段 5.3 节流 / 阶段 6.1** → 见 `docs/最终重构指南.md` §10 的裁决记录（不在此重复，避免两套说法）。

---

## 附：本文与指南的差异（以本文为准）

| 项 | 指南 §3.3 原稿 | 本文 |
|---|---|---|
| 回调签名 | `start(ComponentContext ctx)`，上下文贯穿每次回调 | **构造期注入 `svc`**，回调只传信号（`CastSignal`/`AttackSignal`） |
| 钩子载体 | `LifecycleAware` / `UpdateAware` / `EnergyChangeAware` / `SanTEChangeAware` 四个接口 | **`RoleComponent` 基类 default 钩子**（`awake`/`start`/`stop`/`update`/`onSanTEChange`）；四个 `*Aware` 接口全部删除，`EnergyChangeAware` 的**通知能力也不再提供**（零使用者，§3.4） |
| 端口实现 | 未说明（隐含 `RoleInstance` 实现端口） | **必须是独立适配器**，禁止上转型 |
| 上下文里的 `roles()` | 存在（服务定位器后门） | **删除**；组件所有依赖来自 `svc` + 信号 |
| 协作者查找 | `ctx.collaborator(T.class)` 随时可调 | **`getComponent(T.class)` 取组件本体、直接调公开方法**；`awake()` 里解析一次并缓存，注册表冻结前调用抛异常 |
| 分派顺序 | 未规定 | 固定阶段顺序 + 注册顺序（`LinkedHashMap`），并申报顺序确定性修正 |
| 异常隔离 | 仅保留 update 的既有语义 | 明确"装配 fail-fast / 运行隔离"，并扩展事件广播隔离 |
| 新增机制 | — | `TimerPort`（协程语义，随组件销毁自动取消）、`Self`（重连安全）、`getComponent`；渲染刷新（`markDirty` + poll）是**框架内部**机制，不进 `ComponentServices`（§5） |
| 技能与主武器 | 未表态（指南 §3.1 原图画成两个并列基类） | **合并到 `ActiveComponent`，保留两个薄子类与装配 API 不变**（理由与等价清单见 §4.2） |
| 组件间通信 | 未表态（指南 §3.3 隐含"上下文 + 协作者注册表"） | **`getComponent(T.class)` 取组件本体、直接调公开方法**；聚合根上的 `context` 设施（1 个字段 + 4 个方法）整体删除（§4.4、§4.4.1） |
