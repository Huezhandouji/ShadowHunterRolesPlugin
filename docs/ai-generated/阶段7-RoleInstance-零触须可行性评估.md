# 阶段7 · `RoleInstance`「零触须」可行性评估（t6 评估附件 · 只评估不改码）

> **本件回答一个问题**：把「`RoleInstance` 内**不出现任何具体组件类名（含 `import`）**」当成硬判据，
> 在**现有 API 面**下是否可达？
> **答：不可达（no-go）** —— 但其中**两个维度已经达成**（构造维度 · SanTE 通知维度），
> 剩两个维度被**签名形状**与**契约归属**卡住（逐条见 §3）。本件如实记录，不造词凑判据。

---

## 0. 取数口径（可复现）

| 项 | 值 |
|---|---|
| 取数对象 | `ShadowHunterRoles/src/main/java/com/shadowHunterRolesPlugin/core/RoleInstance.java`（本卡编辑后 **1130 行**，编辑前 1134 行） |
| 读取通道 | `[System.IO.File]::ReadAllLines(<绝对路径>)` 逐行读盘（★ 不用 `Get-Content` 取行：含 CJK 且无 BOM 的 UTF-8 上它会静默打坏行数） |
| 行数口径 | **全文行数**（含空行与注释）；与"非空内容行数"是两个数 |
| 类名模式 | `\b<类名>\b`（`-cmatch` 大小写敏感）；"代码行"= 该行去掉首尾空白后**不以** `*` / `//` / `/*` 开头 |
| 字面量模式 | `\.class\b` |
| 调用点模式 | `pickApply\(` / `pickValue\(` |

---

## 1. 结论（先行）

1. **构造维度：已达成** —— `RoleInstance` 内 `new (…)Component(` 现算 **0 命中**（六件服务组件的构造与接线集中在
   `roleComponent/frameworkLevel/ServiceComponents.java` 的 `build`，见 §3.2）。
2. **SanTE 通知维度：本卡已达成** —— 不再强转、不再遍历具体组件监听器，「逐条条目」改经**框架侧通用来源面**
   （`RoleInstance.ChangeListenerSource` + `ChangeDelivery`）交回（见 §3.4 与 §5）。
3. **调用维度：不可达** —— `pickApply` / `pickValue` 的签名要求调用点给出 `Class<T>` ⇒ **14 个调用点**必然点名
   6 个具体组件类（见 §3.1）。
4. **渲染通知维度：不可达（在本卡 inScope 内）** —— 通知的接受集由「**实现了组件内嵌接口** `RenderCallback` 的组件」
   表达 ⇒ 框架要扫它们就必须点名那个内嵌类型（见 §3.3）。
★ 本题结论已被 `t7`（`3147239`）消解 ⇒ **以 §8 为准**；本节及 §3/§4 保留为当时的推理记录。

5. ⇒ 综合判定：**「含 import 全为 0」= no-go**；但在**不改公共面/不改组件基类**的前提下，
   本卡已把「框架替具体组件做它自己的事」这一类触须里的**三处**清掉（§5 给出 0 命中证据）。

---

## 2. 现算清单（工作区，本卡编辑后）

### 2.1 具体组件类名在 `RoleInstance` 的出现（代码行 / 注释行）

| 类 | 代码行（行号） | 注释行 | 在哪 |
|---|---|---|---|
| `EnergyComponent` | 3 — `26`(import) `627` `630` | 1 | 两处 `pickValue`/`pickApply` 取值口 |
| `VitalsComponent` | 2 — `33`(import) `611` | 3 | `heal` 视图 |
| `BuffComponent` | 5 — `25`(import) `220` `644` `1123` `1125` | 1 | 启动记账 / 药水入账 / 清账 |
| `TimerComponent` | 3 — `32`(import) `708` `1040` | 1 | 逐组件取消计时 |
| `HotbarRenderComponent` | 7 — `15`(import) `246` `424` `446` `844` `849` `865` | 5 | 首刷 / 两处置脏 / 帧末 flush / 取变更 / 渲染通知扇出 |
| `SanTEComponent` | **0** | 1（`635`，仅 javadoc 提及） | — |
| （非组件）`ServiceComponents` | 2 — `31`(import) `157` | 3 | 框架级清单的构造调用 |
| （非组件）`HotbarItems` | 2 — `23`(import) `1117` | 1 | 清角色时的物品清理 |
| （基类，非具体组件）`RoleComponent` | 30 | 3 | 容器与组件的通用面（**允许**） |

### 2.2 `.class` 字面量：现算 **18 行**

| 分类 | 行数 | 行号 |
|---|---|---|
| `pickApply` 实参 | **12** | `220` `246` `424` `446` `611` `630` `644` `708` `844` `1040` `1123` `1125` |
| `pickValue` 实参 | **2** | `627` `849` |
| 注释 / javadoc 提及 | 2 | `266`（javadoc `{@code EnergyComponent.class}`）· `466`（注释 `…get(HotbarRenderComponent.class)`） |
| **接口**（非具体组件类） | 2 | `865`（`HotbarRenderComponent.RenderCallback.class`）· `928`（javadoc `Participant.class`） |

★ **口径注记（防误判）**：交接文档 §4.2 与卡面记「类字面量 14 行 = 12 代码点 + 2 注释行」；本件现算 = **14 代码点**（`pickApply` 12 + `pickValue` 2）+ **2 注释行** + **2 个内嵌接口** = **18 行**。
差异根因（可复核）：旧口径的「12」只数了 `pickApply` 的实参、**未数** `pickValue` 的两处（`627` `849`）。⇒ 后续卡（t7）**必须结卡重算**，不得照抄 12/14。

---

## 3. 阻塞点（逐条：为什么在现有 API 面下不可达）

### 3.1 `pickApply` / `pickValue` 的**签名形状**（主阻塞）

```java
private <T extends RoleComponent> void pickApply(String id, Class<T> type, Consumer<T> action)            // :377
private <T extends RoleComponent, R> R pickValue(String id, Class<T> type, Function<T, R> read, R fallback) // :390
```

- 两个方法都要求调用点交出 `Class<T>` ⇒ 调用点**必然**写下 `EnergyComponent.class` 这类字面量（14 处）。
- 这不是"顺手多写了一个类型"，而是**类型安全的落点**：`type.isInstance(component)` 是"按 id 取到的东西到底是不是我要的那个组件"的唯一判据（`resolve(id)` 只保证拿到一个 `RoleComponent`）。
- 可达路径只有两条，都要动**本卡 inScope 之外**的东西：
  - **改签名**：让调用点只说 id ⇒ 需要"组件自报类型 / 按 id 的强类型句柄"这条口径（落点是组件基类 `roleComponent/RoleComponent.java`，不在本卡 inScope）；
  - **改注册面**：在装配期把"id → 类型化句柄"登记进容器 ⇒ 落点同样是组件基类与容器装配面。

### 3.2 六件服务组件的**构造点** —— **本维度已达成**

- 现算：`RoleInstance` 内 `new (.*)Component(` = **0 命中**；构造与接线在 `ServiceComponents.build` 里（该件 171 行）。
- 代价（已知并接受）：**"组件集合"的知识集中在一个文件**（框架级清单）—— 这是有意的收口，不是遗漏。
- ⇒ 「`RoleInstance` 不构造任何具体组件」这一半**可达且已达成**；不可达的是**取用**那一半（§3.1）。

### 3.3 **渲染回调**的类型登记

- 现算：`for(HotbarRenderComponent.RenderCallback callback : getAllByType(HotbarRenderComponent.RenderCallback.class))`（`:865`）。
- 为什么必须点名：**订阅者实现的是组件内嵌接口**（`RenderCallback` 定义在 `HotbarRenderComponent` 内部）⇒ 框架要"扫出谁订阅了"就离不开那个类型。框架侧**无法**用一个中性类型替换它 —— 除非把该契约**搬出组件**（新件 + 全部实现者随迁，超出本卡 inScope）。
- 另一条路（把扇出交给渲染组件自持）会**改语义**：回调不再逐个经 `deliverHook` ⇒ 「首个异常不让排在其后的组件收不到通知 / 只隔离抛异常的那一个」这条既有性质就保不住（`deliverHook` 内含 `guardedCall` + 遍历窗口，是唯一受保护入口）。⇒ 本卡**不采用**，只把方法名去组件化（`dispatchHotbarRendered` → `dispatchRenderedNotice`）。

### 3.4 **SanTE 监听器**的类型登记 —— **本卡已达成**

- 旧形态：`SanTEComponent sante = (SanTEComponent) resolve(SERVICE_ID_SANTE);` + `sante.forEachListener(entry -> …)` ⇒ 强转 + 遍历具体组件的登记类型。
- 新形态：框架只要求"提供者实现**通用来源面**"，条目由组件**自己**交回，平台侧那一条由组件自己排除：

```java
// RoleInstance（框架侧，通用）
public record ChangeDelivery(RoleComponent owner, Runnable action) { }
public interface ChangeListenerSource {
    void forEachChangeListener(int previous, int current, Consumer<ChangeDelivery> delivery);
}
// 派发边界（三条不变量仍全部在框架侧）
RoleComponent provider = resolve(SERVICE_ID_SANTE);
if (!(provider instanceof ChangeListenerSource source)) return;
withinIterationWindow(() -> source.forEachChangeListener(preSanTE, newSanTE,
        entry -> guardedCall(entry.owner(), "onSanTEChange", entry.action())));
```

- 现算：`(SanTEComponent) resolve` = **0**；`sante.forEachListener` = **0**；`SanTEComponent` 在 `RoleInstance` 的**代码行 = 0**（只剩 1 行 javadoc 提及）。
- 语义对照（**逐条未变**）：名单**快照**遍历（由组件侧 `forEachListener` 提供）· 平台侧条目排除（改由组件侧排除）· **逐条**经 `guardedCall` · 整段在**同一个**遍历窗口里 · 窗口关闭后才执行隔离四步。

### 3.5 其它同类"框架点名具体实现"（不在"具体组件类"口径内，一并申报）

- `HotbarItems.clearFrom(player)`（`:1117`）：`roleComponent/HotbarItems` 是**物品关注点的支持类**，不是组件；
- `BuffManager`（`:164` 局部变量，来自 `manager/`）：装配期局部，交接文档 §4.2.D 已判为可接受；
- `ServiceComponents`（`:157`）：**框架级清单**（它知道组件集合是它的职责）。

---

## 4. 若要真正清零：两条路线与代价（**本卡不实施**，仅登记）

| 路线 | 做法 | 落点（谁的活） | 代价 / 风险 |
|---|---|---|---|
| **A · 改取值面签名** | `pickApply`/`pickValue` 去掉 `Class<T>`，改为"组件自报类型 + 通用 `RoleComponent` 面" | `core/RoleInstance.java`（本卡可动）+ `roleComponent/RoleComponent.java`（组件基类，**不在**本卡 inScope） | 类型安全从**编译期**退到**运行期**；14 个调用点全改；需一条"自报类型"的新口径 |
| **B · 把契约搬出组件** | `RenderCallback` 这类内嵌契约搬到中立件（与"专属类放同级文件夹"的目录规则一致），实现者随迁 | 新件 + 全部实现者（`builtin/*`、`custom/**`） | 跨卡、跨目录的改动；`RoleApiSurfaceTest` 不受影响，但可见面变动需逐件复核 |

★ 两条路线**都不触碰**三条派发不变量（`1/1/10`）与唯一写点（`inv.setItem(` = 2）—— 那些是行为面，与"谁点名谁"无关。

---

## 5. 本卡**已消除**的触须（0 命中证据）

| # | 旧形态 | 现算 | 怎么改的 |
|---|---|---|---|
| 1 | `private final Runnable markHotbarDirty = this::requestHotbarRepaint` | **0** | **删字段**（全库无读取者 = 旧通道遗壳）+ 删其 javadoc |
| 2 | `private void requestHotbarRepaint()` | **0** | **删方法**（它唯一的引用就是上面那个字段的初始化式；删字段后无任何引用） |
| 3 | `dispatchHotbarRendered()` | **0** | 改名 `dispatchRenderedNotice()`（不绑定具体组件的通用名；扇出机制与逐条 `deliverHook` 不变） |
| 4 | `(SanTEComponent) resolve(SERVICE_ID_SANTE)` | **0** | 改为 `instanceof ChangeListenerSource`（框架侧通用面） |
| 5 | `sante.forEachListener(entry -> …)` | **0** | 条目由组件经 `forEachChangeListener` 逐条交回；平台侧条目在组件侧排除 |

**承接证据（删掉一对之后，框架侧置脏/重绘的活调用 = 4 处）**：`:246`（`firstFlush`）· `:424`/`:446`（`markDirty`）· `:844`（`flush`）—— 全是 `pickApply` 实参。
★ 被删方法体内的那一行（编辑前 `:214`，`HotbarRenderComponent::requestRepaint`）**不算**改后承接证据（它随方法体一起消失）。

---

## 6. 复核命令（照抄可复现本件任一条读数）

```powershell
$repo = (Resolve-Path 'ShadowHunterRoles').Path
$ri   = "$repo\src\main\java\com\shadowHunterRolesPlugin\core\RoleInstance.java"
$lines = [IO.File]::ReadAllLines($ri)                      # 权威读取（不要用 Get-Content）

# ① 三条不变量（口径 = 原始文本命中行数，注释行计入）
($lines | Select-String 'preSanTE == newSanTE').Count                          # 1
($lines | Select-String 'guardedCall\(entry\.owner\(').Count                   # 1
($lines | Select-String 'sanTEDispatching|sanTEPendingValue').Count            # 10

# ② 本卡清掉的三处（0 命中）
($lines | Select-String 'markHotbarDirty|requestHotbarRepaint|dispatchHotbarRendered').Count   # 0
($lines | Select-String '\(SanTEComponent\) resolve').Count                                   # 0
($lines | Select-String 'sante\.forEachListener').Count                                       # 0

# ③ 类名字面量清点（★ 本节数字为**本卡编辑前**的值；编辑后现值见 §8.1）
($lines | Select-String '\.class').Count                                       # 编辑前 18 / 编辑后现值 0（§8.1）

# ④ 构造维度（§1.1 已达成）
($lines | Select-String 'new \(.*\)Component\(').Count                         # 0（编辑前后同为 0）

# ⑤ 写点与平台侧通道（全库）
(Get-ChildItem "$repo\src" -Recurse -Filter *.java | ForEach-Object {
    [IO.File]::ReadAllLines($_.FullName) } | Select-String 'inv\.setItem\(').Count        # 2
(Get-ChildItem "$repo\src" -Recurse -Filter *.java | ForEach-Object {
    [IO.File]::ReadAllLines($_.FullName) } | Select-String 'notifyPlatform').Count        # 3
```

> ★ 只用 `Select-String -Path <文件>`（其 `LineNumber` 准）或 `[IO.File]::ReadAllLines()`；
> **不要**用 `Select-String -InputObject <数组>`（它把整个数组当一个对象 ⇒ 行号恒 1）。

---

## 7. 边界申报（本件**不**做的事）

1. **只评估与记录** —— 本件不改 `src/`、不改其他文档（`docs/` 下除本件之外零改动）。
2. **不实施** §4 的两条路线（它们的落点超出本卡 inScope）。
3. **不把"改名"当"去耦合"**：`dispatchRenderedNotice` 仍是框架侧的扇出，`§3.3` 的阻塞点**依旧存在**，只是名字不再绑定某个组件。
4. **不隐瞒口径差**：§2.2 的「18 行」与旧记「14 行」的差已给根因（旧口径漏数 `pickValue` 两处、且未把内嵌接口与注释分行计）—— 后续卡必须重算。

---

## 8. 更正注记（截至 `3147239`）

> ★ **本节行号取数锚点** = HEAD `c45bf9c`（取数时刻 2026-09-25 11:29）；**行号会漂，判据请以内容锚点为准** —— 同一事实在本阶段三次卡内漂了三次，照抄行号极易失效。
> **本节由 `t29` 追加**，上文 §0–§7 **一字不动**。追加理由：§1 的结论「**不可达（no-go）**」**已被后续结构卡 `t7` 消解** —— 若继续按原文执行，下一个上下文会绕过已经达成的结构去"再解一遍"。

### 8.1 原判与现状

| 项 | 原文（§1 / §3） | 现算（截至 `3147239`） |
|---|---|---|
| 总目标「`RoleInstance` 内不出现任何具体组件类名（含 import）= 0」 | **不可达** | **已达成**（`RoleInstance` 现 **995 行**） |
| §3.1 阻塞点：`pickApply` / `pickValue` 的 `Class<T>` 签名迫使调用点点名 | 主阻塞 | **已解除**：两个取用口**整体删除**，动作改由框架级清单的服务入口完成 ⇒ 调用点不再需要写 `Class<T>` |
| §3.3 阻塞点：渲染回调的内嵌契约归属 | 不可达 | **已解除**：类型扫描（`instanceof … RenderCallback`）搬进 `ServiceComponents.forEachRenderNotice`，容器侧只保留**逐个受保护投递** `deliverHook(...)` |

⇒ **`t7`（提交 `3147239`）之后，§1 的 no-go 结论、§3.1 与 §3.3 两条阻塞点、§4 的两条"若要清零"路线，全部是过期信息**：§4 的两条路线**不必再实施**（目标已达成），原文保留仅为记录当时的推理。

### 8.2 达成的**范围**（逐维给现算值）

| 维度 | 判据 | 现算 |
|---|---|---|
| **构造维度** | `new (.*)Component(` | **0**（六件服务组件的构造全在 `ServiceComponents.build`） |
| **取用维度** | `.class` 字面量 | **0 行** |
| | `SERVICE_ID_` | **0**（6 个常量声明 + 相邻两条历史注释已删） |
| | `pickApply\|pickValue` | **0**（连同 javadoc 整体删除） |
| | **对具体服务组件类**的引用（`BuffComponent` / `EnergyComponent` / `VitalsComponent` / `TimerComponent` / `HotbarRenderComponent` / `SanTEComponent`，含 import） | **0** |
| 三条派发不变量 | 真变化闸门 / 逐监听器隔离 / 重入合并 | **1 / 1 / 10**（`:604` / `:678` / 10 行） |
| 写点与平台侧通道 | `inv.setItem(` / `notifyPlatform`（全库） | **2** / **3** |

### 8.3 代价：耦合**集中在 `ServiceComponents` 一件（非消灭）**

- 组件集合的知识 —— **类 + id + 取用动作** —— 全部集中在 `roleComponent/frameworkLevel/ServiceComponents.java`（框架级清单）。
- 这正是交接文档 §5.2 认可的定位（"哪些组件、什么顺序、怎么接线"全在那个框架级清单里）⇒ **本目标是达标，不是绕过**：容器侧不再认识任何具体服务组件类，而清单侧**本来就该**知道。
- ★ **不要把这条读成"耦合被消灭了"**：它是**从容器搬进清单**；清单件从此是这类知识的**唯一落点**，改组件集合必须改它。

### 8.4 新增公共面（请独立复核）

| 新增项 | 位置 | 说明 |
|---|---|---|
| **16 个 `public static`（非 `final`）方法**（含原有的 `build`，以及 `t32` 引入的**既存入口** `renderRequestRepaint` / `scheduleRepeating`）⇒ **净新增 13 个** | `ServiceComponents.java`（共 16 处：`:122` `:181` `:188` `:195` `:202` `:213` `:226` `:231` `:238` `:245` `:252` `:259` `:266` `:273` `:284` `:299`） | 容器侧的服务取用入口（置脏 / 帧末刷新 / 首刷 / 取变化读数 / 渲染通知扫描 / 能量读与写 / 治疗 / buff 记账四项 / 取消计时 / 请求重绘 / 周期任务）；**未命中 ⇒ 无操作**，两处读口回退值与旧路径逐字相同（→ `0` / → `false`） |
| 嵌套面 `ChangeListenerSource` + `ChangeDelivery` | `core/RoleInstance.java` | `t6` 引入的「变更通知的通用来源面」（替代对具体组件的强转与直接遍历） |

- **快照 58 未破** ✓（`RoleApiSurfaceTest` 2/2 pass；`FROZEN_SIGNATURES` 逐条数 = **58**）
- **`api/` 零改动** ✓（`api/` 与 `internal/api/` 不在 `t6` / `t7` 的改动面内）

### 8.5 ★★ 标准措辞（全队唯一口径，不得改写）

> `RoleInstance` 内**对具体服务组件类**的引用 = **0**；仅剩 1 处**已裁定的静态支持类** `HotbarItems`（`:17` import / `:981` 注释 / `:982` 调用；`public final class`、不实现 `RoleComponent`、容器内无字段）—— 按用户 q8 裁定接受其位置，**不属残余**。

### 8.6 ★★ 判据边界（不得推广）

> **`.class` 字面量 = 0 是文本判据**（含注释）；**「服务组件类名 = 0」是类名判据**。两者都真，但**都不得推广成「零类名」**。

补充：`HotbarItems` 是**物品关注点的静态支持类**（`public final class` + `public static void clearFrom(Player)`，不实现 `RoleComponent`，容器内无字段声明）⇒ 它**落在两个判据之外**。

### 8.7 复核命令（逐行读盘；照抄可复现本节任一数字）

```powershell
$repo  = (Resolve-Path 'ShadowHunterRoles').Path
$ri    = "$repo\src\main\java\com\shadowHunterRolesPlugin\core\RoleInstance.java"
$lines = [IO.File]::ReadAllLines($ri)                  # 权威读取（不要用 Get-Content 数行）

# ① 取用维度的四个零（现算 0 / 0 / 0 / 0）
($lines | Select-String '\.class').Count                                  # 0
($lines | Select-String 'SERVICE_ID_').Count                              # 0
($lines | Select-String 'pickApply|pickValue').Count                      # 0
($lines | Select-String 'BuffComponent|EnergyComponent|VitalsComponent|TimerComponent|HotbarRenderComponent|SanTEComponent').Count  # 0

# ② 已裁定的例外（现算 3 处）
$lines | Select-String 'HotbarItems' | ForEach-Object { $_.LineNumber }   # 17 / 981 / 982

# ③ 构造维度（现算 0）
($lines | Select-String 'new \(.*\)Component\(').Count                    # 0

# ④ 三条不变量（现算 1 / 1 / 10）
($lines | Select-String 'preSanTE == newSanTE').Count                     # 1
($lines | Select-String 'guardedCall\(entry\.owner\(').Count              # 1
($lines | Select-String 'sanTEDispatching|sanTEPendingValue').Count       # 10

# ⑤ 新增公共面（现算 16 个 public static 非 final 方法 + 6 个原有 ID_* 常量）
$sc = [IO.File]::ReadAllLines("$repo\src\main\java\com\shadowHunterRolesPlugin\roleComponent\frameworkLevel\ServiceComponents.java")
@($sc | Where-Object { $_ -cmatch '^    public static (?!final)' }).Count # 16
@($sc | Where-Object { $_ -cmatch '^    public static final ' }).Count    # 6
```

> ★ **不得用 `git grep` 扫 `debug-logs/**`**：该目录被库内 `.gitignore:47`（`debug-logs`）+ `:85`（`debug-logs/**`）覆盖 ⇒ `git grep` 对它**恒空**（会判出假红）。本件涉及该目录的取证一律改走逐行读盘。
> ★ 行数必须写清口径：**全文行数**与"非空内容行数"是两个数；`RoleInstance` 现算 **995 行**（全文口径）。

### 8.8 与 `t13` 验证口径的对应

`t13`（V-B 独立验证）按下列三条复核本节与 `t6` / `t7` 的交付面：

1. **快照 58 未破**（`RoleApiSurfaceTest` 2/2）；
2. **`api/` 零改动**；
3. **新面的未命中语义与旧路径逐字等价** —— 逐条对照表见 `debug-logs/测试记录/阶段13/阶段13-注释沿革-B1-追加.md` §B1-3（15 个取用点；该目录 gitignored ⇒ **只能逐行读盘**）。

