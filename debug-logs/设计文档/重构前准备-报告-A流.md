# t2 实现报告（A 流）：生命周期三方法 awake/start/stop + RoleInstance 时序调整

- 执行者：`engineer`（A 流）
- 任务：`t2`，attempt 1，`attempt_id = 9d61024d-f683-4694-92a0-8c6909a6b7ed`
- 验收依据：`debug-logs/重构前准备-需求与验收.md`（冻结版，§2 = A 流需求）
- 完成时间：2026-09-15
- 改动文件集：**7 个**（A-1..A-7），与需求文档 §1 完全一致

---

## 0. 结论摘要

| # | 验收项 | 结果 |
|---|---|---|
| ① | 接口只暴露 `awake`/`start`/`stop`，全仓无 `onSet`/`onClear` 契约残留 | 通过（含注释与调试字符串，全仓 0 处残留） |
| ② | `awake` 在「全部组件创建完成之后、`start` 之前、ticker 启动之前」被调用 | 通过（`RoleInstance.java:91`，见 §4 时序论证） |
| ③ | 调试字符串同步改新方法名，且未删除调试输出 | 通过（`start() triggered!` / `stop() triggered!`，行仍在） |
| ④ | 构建通过 + jar 与 .class 真实重编译 | 通过（`BUILD SUCCESSFUL`，见 §6） |
| ⑤ | 伤害/冷却/层数/能量数值逐字一致 | 通过（126 个数值字面量多重集完全一致，见 §7） |
| ⑥ | RoleAPI 既有签名未变 | 通过（未触碰 `RoleAPI.java`/`RoleAPIImpl.java`，见 §7） |
| ⑦ | 未修改 `SHDFGamePlugin` 任何文件 | 通过（该目录 22:00 之后 0 个文件被改动） |
| ⑧ | 每处改动给出 文件:行号 + 前后对比 + 时序证据 | 通过（§3/§4/§5 + `debug-logs/测试记录/t2-diff-before-after.txt`） |

> ⚠️ **冲突区状态**：`MeiqiheziEquipmentsPassive.stop` **仍为空实现**（`:72-74`），B 流补丁尚未并入 —— 按契约由 `t8` 门禁收口，见 §8。

---

## 1. 基线与环境（改动前留证）

| 项 | 值 | 来源 |
|---|---|---|
| 工程基线 | `paper-api:1.21.11-R0.1-SNAPSHOT` / Java 21 | `build.gradle.kts:30,35` 实读 |
| before 参照 | `debug-logs/回滚快照/before/src/**`（verifier 于 21:56 冻结） | 逐文件 SHA256 |
| 改动前 jar | `build/libs/ShadowHunterRolesPlugin-1.0.0.jar` = **102873 B**，mtime `2026-09-13 15:17:26` | 与需求文档 §6.2 记载一致 |

**开工前核对**：A 流 7 个文件全部与 `debug-logs/回滚快照/before/src/**` **逐字节一致（SHA256 IDENTICAL）**，证明当时无人（含 B 流）改动过 A 流文件集：

```
IDENTICAL  LifecycleAware.java                         cur=8AE2B87BA987 snap=8AE2B87BA987
IDENTICAL  RoleInstance.java                           cur=30F97E454A1F snap=30F97E454A1F
IDENTICAL  TestSendSanTEAndEnergyChangeMsgPassive.java cur=79D9AEEBDA96 snap=79D9AEEBDA96
IDENTICAL  DefaultSanTEZeroPunishment.java             cur=69ECAA4DD953 snap=69ECAA4DD953
IDENTICAL  RedEquipmentsPassive.java                   cur=E83EE51A14AB snap=E83EE51A14AB
IDENTICAL  RedBleedPassive.java                        cur=B4E0BA208738 snap=B4E0BA208738
IDENTICAL  MeiqiheziEquipmentsPassive.java             cur=CD2C86B6D792 snap=CD2C86B6D792
```

新增一次性产物（非交付物，仅为证据）：`debug-logs/测试记录/t2-diff-before-after.txt`、`debug-logs/测试记录/t2-build.log`、`debug-logs/测试记录/t2-build-x.txt`、`debug-logs/测试记录/t2-numeric-before.txt`、`debug-logs/测试记录/t2-numeric-after.txt`。

---

## 2. 与 B 流并行的时间线（证明未越界）

按需求文档 §6.3 的判据（**mtime 切点 + 本次改动清单**，不用 `git diff` 判定"改了什么"），`src` 目录下 2026-09-15 的改动分为互不重叠的两段：

| 时间窗 | 改动者 | 文件 |
|---|---|---|
| 22:18:10 – 22:18:40 | B 流（engineer2，t6） | `RoleAPIImpl` / `RoleCommand` / `MeiqiheziJuejueMainWeapon` / `RoleRegistry` / `ShadowHunterRolesPlugin` / `Role` / `Skill` / `PassiveSkill` / `BuffManager` / `SkillListener`（10 个） |
| **22:19:35 – 22:22:43** | **A 流（本报告）** | **A-1..A-7（7 个，见 §3）** |

- A 流改动起点（22:19:35）**晚于** B 流改动终点（22:18:40），两段无交集 → 本报告未触碰任何 B 流文件。
- 未修改 `SHDFGamePlugin`：该目录内 mtime 晚于 22:00 的文件数为 **0**。

---

## 3. 逐文件改动（文件:行号 + 前后对比）

完整 unified diff（含上下文）见 `debug-logs/测试记录/t2-diff-before-after.txt`。以下为逐点摘要。

### A-1 `core/RoleComponentAware/LifecycleAware.java`（16 → 21 行，+9/−4）

```diff
-    //如果一个角色组件需要在设置角色和清理角色时执行操作，实现这个接口
-
-    void onSet(Player player, RoleInstance instance);
-
-    //在清理时，必须清理在onSet创建的所有资源
-    void onClear(Player player, RoleInstance instance);
+    //如果一个角色组件需要在角色开始生效和停止生效时执行操作，实现这个接口
+
+    //awake阶段的目的是解析跨组件依赖并缓存引用，在全部组件创建完成之后、start之前执行
+    //必须保证幂等，且不得改动任何玩家可见状态：此时stop尚未调用，重入会叠加效果
+    default void awake(Player player, RoleInstance instance) {}
+
+    //开始生效：初始化数据、发放装备、注册tick等
+    void start(Player player, RoleInstance instance);
+
+    //停止生效，与start严格对称：在stop时，必须清理在start创建的所有资源
+    void stop(Player player, RoleInstance instance);
```

- 改名后行号：`awake` `:12`（`default` 空实现）、`start` `:15`、`stop` `:18`。
- 参数保持 `(Player, RoleInstance)`，**未**新增窄上下文类型（符合 §2.2）。
- 原 `:12` 注释对 `onSet` 的引用同步改写，避免 grep 残留。

### A-2 `core/RoleInstance.java`（771 → 798 行，+39/−12）

**(a) 构造时序 `:79-102`**

```diff
         player.setHealth(getMaxHealth());
 
-        updateHotbar();
-
-
+        //生命周期时序：全部组件创建完成 -> awake全部 -> start全部 -> 启动ticker -> 渲染热键栏
+        triggerLifecycleAwake();
+        triggerLifecycleStart();
+
         updateTaskId = Bukkit.getScheduler().runTaskTimer(
                 ShadowHunterRolesPlugin.getInstance(),
                 this::triggerUpdate,
                 1L,
                 1L
         ).getTaskId();
 
-        triggerLifecycleOnSet();
+        updateHotbar();
     }
```

时序证据（**awake 在 start 之前、ticker 启动之前**）：
`initComponents()` `:75` → `new BuffManager` `:77` → 设生命 `:80-88` → **`triggerLifecycleAwake()` `:91`** → **`triggerLifecycleStart()` `:92`** → 起 ticker `:94-99` → `updateHotbar()` `:101`。

**(b) 两个触发方法 → 三个遍历方法 `:586-657`**

```diff
-    //生命周期触发
-    public void triggerLifecycleOnSet(){
+    //生命周期触发
+    //awake阶段：只解析跨组件依赖并缓存引用，必须幂等且不改动玩家可见状态
+    public void triggerLifecycleAwake(){
...
     }

-    public void triggerLifecycleOnClear(){
+    //start阶段：开始生效，顺序与awake一致（技能/被动/武器）
+    public void triggerLifecycleStart(){
...
     }
+
+    //stop阶段：停止生效，遍历顺序与start相反（武器/被动/技能），逆序拆卸
+    public void triggerLifecycleStop(){
...
     }
```

- 方法行号：`triggerLifecycleAwake()` `:588`（`awake` 调用 `:594/:600/:606`）、`triggerLifecycleStart()` `:612`（调用 `:618/:624/:630`）、`triggerLifecycleStop()` `:636`（调用 `:641/:647/:654`）。
- 三段 `instanceof` 结构**保留原样**（技能/被动/武器），未改为一次遍历分派 → 调用集合与顺序可逐行对照，符合 §2.6 的"若改结构必须保证调用集合与顺序不变"（此处选择不改结构，零风险）。
- `start` 的调用顺序与旧 `onSet` **逐字一致**：技能 → 被动 → 武器（旧 `:592/598/604`）。

**(c) `clear()` 调用点 `:778`**

```diff
     public void clear(){
-        triggerLifecycleOnClear();
+        triggerLifecycleStop();
```

### A-3 `roleComponent/TestSendSanTEAndEnergyChangeMsgPassive.java`（38 行不变，+4/−4）

```diff
-    public void onSet(Player player, RoleInstance instance) {
-        player.sendMessage(Component.text("onSet() triggered!"));
+    public void start(Player player, RoleInstance instance) {
+        player.sendMessage(Component.text("start() triggered!"));
     }
 
     @Override
-    public void onClear(Player player, RoleInstance instance) {
-        player.sendMessage(Component.text("onClear() triggered!"));
+    public void stop(Player player, RoleInstance instance) {
+        player.sendMessage(Component.text("stop() triggered!"));
     }
```

- 按需求文档 §2.7 裁决：**连字符串一起改**（不留 grep 例外）。
- **未删除调试输出**（两条 `sendMessage` 均保留），解除注册属 B-5/t6 → 本次不动。

### A-4 `roleComponent/DefaultSanTEZeroPunishment.java`（126 行不变，+2/−2）

- `:118` `onSet` → `start`（方法体仍为空，保持原样）
- `:123` `onClear` → `stop`（方法体 `Bukkit.getScheduler().cancelTask(taskId);` 未变）

> 注：该类 `taskId` 无重入保护的缺陷（架构文档 B-2）**属 B 流范围，未在本任务动**。

### A-5 `roleComponent/red/RedEquipmentsPassive.java`（76 行不变，+2/−2）

- `:22` `onSet` → `start`；`:74` `onClear` → `stop`
- 注意：`start` 内 `player.sendMessage(Component.text("xxb111"));`（`:23`）是**原有**调试输出，本次**未触碰**（不在本次清单内，按纪律不顺手清理）。

### A-6 `roleComponent/red/RedBleedPassive.java`（217 行不变，+2/−2）

- `:54` `onSet` → `start`（上下文注入逻辑未变）；`:60` `onClear` → `stop`（两个 Map 清理逻辑未变）
- 注释 `/**技能初始化时，在角色实例上下文中初始化流血记录**/` 未含旧方法名，无需改动。

### A-7 `roleComponent/meiqiHezi/passive/MeiqiheziEquipmentsPassive.java`（75 行不变，+2/−2）

- `:22` `onSet` → `start`（四件装备发放逻辑**逐字未动**）
- `:73` `onClear` → `stop`，**方法体仍为空** `:73-74` → 见 §8 冲突区声明。

---

## 4. 构造时序调整的「对当前行为无影响」论证（需求 §2.5 强制项）

### 改动前后执行序列

| 步骤 | 改动前 | 改动后 |
|---|---|---|
| 1 | `initComponents()` `:75` | `initComponents()` `:75` |
| 2 | `new BuffManager` `:77` | `new BuffManager` `:77` |
| 3 | 设生命修饰符 + `setHealth` `:80-88` | 同左 |
| 4 | `updateHotbar()` `:90` | **`triggerLifecycleAwake()` `:91`** |
| 5 | 起 1-tick ticker（首次执行 = t+1）`:93-98` | **`triggerLifecycleStart()` `:92`** |
| 6 | `triggerLifecycleOnSet()` `:100` | 起 1-tick ticker（首次执行 = t+1）`:94-99` |
| 7 | — | `updateHotbar()` `:101` |

### 论证

1. **同步性**：`RoleInstance` 的构造全程在**主线程**同步执行，无任何线程切换、无 `yield`/等待点。因此上面 7 步无论怎样重排，都发生在**同一个服务器 tick 的同一个方法调用栈内**。
2. **ticker 首次执行的绝对时刻不变**：`runTaskTimer(..., 1L, 1L)` 的首次执行时刻 = 「调度时刻所在 tick + 1」。改动前调度发生在第 4 步之后、改动后发生在第 5 步之后，但这三步都在同一 tick 内 → **首次 `triggerUpdate` 的绝对 tick 完全相同**。改动前"ticker 在回调之前启动"其实**没有**任何 tick 有机会执行：从 `runTaskTimer` 返回（`:98`）到 `triggerLifecycleOnSet()`（`:100`）之间只有一次方法调用，不存在 tick 边界。
3. **`updateHotbar()` 被前移的实际影响**：改动前它是**首个**动作（`:90`），改动后是**最后一个**动作（`:101`）。`LifecycleAware` 实现中唯一的 `updateHotbar` 间接触发者是 `updateHotbar()` 自身与 `RoleInstance` 的冷却/能量/SanTE 变更方法——本任务的 5 个实现类在 `start`/`stop` 里**均不调用**这些方法（A-3/A-4/A-5/A-6/A-7 的 diff 已证明方法体逐字未动）。因此玩家在该 tick 看到的快捷栏物品**集合与时刻均不变**；同一 tick 内 `updateHotbar` 的执行次数也不变（改动前 1 次构造期 + ≤1 次 tick 内；改动后相同）。
4. **`awake` 空实现**：`LifecycleAware.awake` 是 `default` 空方法，且**没有任何组件覆写**（全仓 `awake` 出现处仅接口声明与 `RoleInstance` 的两处遍历调用，见 §5）→ 本次 `awake` 阶段为纯空遍历，零副作用。
5. **`start` 的调用位置**：`start` 承接旧 `onSet` 的"开始生效"语义并**紧邻其后**执行，只是从「ticker 启动之后」变为「ticker 启动之前」。由于第 2 点（首次 tick 时刻不变），旧 `onSet` 里任何"注册 tick/发放装备"的效果在时间轴上与改动前**不可区分**。

> 结论：该重排是**纯结构性**的，可观察行为（玩家可见状态、任务调度时刻、方法调用次数）逐项不变。运行时行为仍由队长起服冒烟复验兜底（需求 §8.1）。

---

## 5. 验收①：命名彻底性（全仓 grep）

命令与结果（`src` 全树）：

```
grep -rn "onSet|onClear|triggerLifecycleOn" ShadowHunterRoles/src
→ No matches found（精确计数 = 0）
```

- **`onSet` 残留 = 0，`onClear` 残留 = 0，`triggerLifecycleOnSet`/`triggerLifecycleOnClear` 残留 = 0**（源码全树，含注释与字符串字面量）。
- 命名契约的完整遍历（`grep "LifecycleAware|onSet|onClear|triggerLifecycle|awake|start(Player|stop(Player"`）命中 53 处，全部为新契约点：接口声明 `LifecycleAware.java:12,15,18`；触发点 `RoleInstance.java:91,92,594,600,606,618,624,630,641,647,654,778`；5 个实现类 `TestSendSanTEAndEnergyChangeMsgPassive:20,25`、`DefaultSanTEZeroPunishment:118,123`、`RedEquipmentsPassive:22,74`、`RedBleedPassive:54,60`、`MeiqiheziEquipmentsPassive:22,73`。
- **`awake` 无任何覆写**：`awake`/`awake(` 的全部出现仅在 `LifecycleAware.java:12` 与 `RoleInstance.java:594,600,606`（接口默认实现生效）。

> 说明：`onEnergyChange` / `onSanTEChange` 属 `EnergyChangeAware` / `SanTEChangeAware`，**不在本次改名范围**（需求 §2 只规定 `LifecycleAware` 三方法），未触碰。

---

## 6. 验收④：构建

命令（需求 §6.2 规定写法）：

```powershell
cd ShadowHunterRoles; $env:GRADLE_USER_HOME="$PWD\.gradle-work"; .\gradlew build --console=plain
```

### 第 1 次（规定命令，证明真实重编译）

```
> Task :compileJava            ← 真实执行（非 UP-TO-DATE）
> Task :classes
> Task :jar
> Task :build
> Task :copyPluginJar FAILED
4 actionable tasks: 3 executed, 1 up-to-date
=== EXIT=1 ===

FAILURE: ... Execution failed for task ':copyPluginJar'
> Could not copy file '...\build\libs\ShadowHunterRolesPlugin-1.0.0.jar' to
  'C:\Users\ROG\Desktop\paper1.21.11\plugins\ShadowHunterRolesPlugin-1.0.0.jar'
  > ... (拒绝访问。)
```

**判定**（按需求 §6.2 的判定标准，**不用退出码**）：

| 判据 | 改动前 | 改动后 | 结论 |
|---|---|---|---|
| jar 存在 | 102873 B / 09-13 15:17:26 | **102878 B / 2026-09-15 22:25:22** | 已刷新（+5 B，方法名变长所致） |
| `.class` 总数 | 55 | **55** | 一致 |
| 本次重编译的 `.class` | — | **47 个** mtime = 2026-09-15 22:25:22 | 真实重编译 |
| 关键 `.class` | — | `RoleInstance.class` 26027 B / 22:25:22；`LifecycleAware.class` 579 B / 22:25:22；`MeiqiheziEquipmentsPassive.class` 3157 B / 22:25:22 | 三者均刷新 |

- **`copyPluginJar` 失败 = 预期**：目标 `C:/Users/ROG/Desktop/paper1.21.11/plugins` 在工作区外，沙箱下写入被拒（`拒绝访问。`）。**该外部拷贝未执行**，按纪律不计为缺陷。jar 的 `:jar` 任务本身**成功**（其输出已生成并刷新）。
- 日志中无任何 `error:` / `cannot find symbol` / `符号找不到` 编译错误；`注: RoleAPIImpl.java使用或覆盖了已过时的 API` 为**既有** javac deprecation 提示（B 流文件，非本次引入）。
- 退出码 1 的来源是 Gradle file-watcher 线程 `Couldn't open current thread, error = 5`（沙箱限制）**叠加** `copyPluginJar` 失败 —— 需求 §6.2 已明确"不得以退出码判定"。

### 第 2 次（排除外部拷贝任务，取得唯一成功信号）

```powershell
cd ShadowHunterRoles; $env:GRADLE_USER_HOME="$PWD\.gradle-work"; .\gradlew build -x copyPluginJar --console=plain
→ BUILD SUCCESSFUL in 8s
→ 3 actionable tasks: 3 up-to-date        （compileJava/jar 均已是最新，证明上一轮产物有效）
=== EXIT=0 ===
```

- 用 `-x copyPluginJar` 排除的是 `build.gradle.kts:66-68` 的 `finalizedBy("copyPluginJar")` 外部拷贝任务，**未修改任何构建文件、未改变编译/打包语义**。
- `UP-TO-DATE` 在此处**不构成**"没编译"：上一轮的 `:compileJava` 已执行并产出最新 `.class`（§6 表），本轮无输入变化故跳过。需求 §6.2 提醒的"UP-TO-DATE ≠ 已重新编译"针对的是**只有 UP-TO-DATE 而没有第 1 轮真实执行**的情形，本报告已同时给出第 1 轮 `> Task :compileJava` 的真实执行 + mtime 刷新作为主证据。

---

## 7. 验收⑤⑥⑦：数值、RoleAPI、SHDFGamePlugin

### ⑤ 数值逐字一致（126 个数值字面量，多重集比对）

对 A 流 7 文件提取代码区（剔除 `//` 注释与 `/* */` 行）全部数值字面量，改动前（`debug-logs/测试记录/t2-numeric-before.txt`）与改动后（`debug-logs/测试记录/t2-numeric-after.txt`）比对：

```
before_count=126  after_count=126
MULTISET-IDENTICAL: 数值逐字一致（含出现次数与顺序）
before: 0,0.1f,0.33333d,0.3d,0.5,0.5d,0d,0L,1,100,139,15,1f,1L,2,20,20f,3,30,4,40L,5000L,9
after : 0,0.1f,0.33333d,0.3d,0.5,0.5d,0d,0L,1,100,139,15,1f,1L,2,20,20f,3,30,4,40L,5000L,9
```

含 `MAX_BLEED_STACK=15`、`BLEED_DAMAGE_PER_SECOND=2d`、`BLEED_SANTE_RECOVER=4`、`BLEED_RESISTANCE_DURATION_TICKS=100`、`BLEED_SETTLE_INTERVAL_TICKS=20`、`0.3d`、`0.33333d`、`40L`、`5000L` 等全部冷却/层数/能量/伤害数值，**逐字未变**。

### ⑥ RoleAPI 既有签名未变

- 本任务**未触碰** `api/RoleAPI.java`（mtime `13:44:01`，早于 A 流改动起点）与 `api/RoleAPIImpl.java`（mtime `22:18:10`，属 B 流时间窗）。
- 当前 `RoleAPI` 公开方法共 **53 个**（已逐条列出备查，见执行记录），无任何方法被本任务增删改；两文件的 mtime 均早于 A 流改动起点 `22:19:35`。

### ⑦ SHDFGamePlugin 未被修改

- `SHDFGamePlugin` 目录内 mtime 晚于 2026-09-15 22:00 的文件数 = **0**。
- 该仓库的 `git status` 显示的改动均为**作者历史未提交改动**（mtime 远早于本次会话），与本次无关。

---

## 8. 冲突区声明（需求 §3 强制项）

`roleComponent/meiqiHezi/passive/MeiqiheziEquipmentsPassive.java`：

- 本次只做重命名：`:22` `onSet` → `start`（四件装备发放逻辑逐字未动），`:73` `onClear` → `stop`。
- **`MeiqiheziEquipmentsPassive.stop` 仍为空实现**（`:72-74`，方法体无任何语句），**待合并 B 流补丁**。
- 我**未**自行发明回收语义（不臆断回收哪四件、判空方式、是否处理死亡/复活路径），符合需求 §3 第 3 条与 t8 的"若 t6 报告无可复用补丁则回报阻塞"。
- 落地由 `t8` 门禁执行：等 `t2` + B 流验证报告齐备后，把 B 流补丁写进该文件，并保证与 `start` 的发放严格对称。

> 说明：开工时该文件 `stop` 为空且与 before 快照逐字节一致，可确认 B 流当时**尚未**写入该文件（符合"B 流不得直接改该文件"的契约）。

---

## 9. `stop` 逆序拆卸：是否采用 + 理由（需求 §2.6 要求说明）

**采用逆序**：`triggerLifecycleStop()` `:636-657` 的遍历顺序为 **武器 → 被动 → 技能**，与 `start` 的 **技能 → 被动 → 武器** 相反。

理由与影响：
1. **调用集合完全相同**：同一批 `instanceof LifecycleAware` 判定，只是外层遍历顺序相反 → 减少"先建的资源后被回收"类缺陷的可能（与 `start` 的建立顺序成镜像）。
2. **当前无可观察差异**：5 个实现类的 `stop` 之间互不依赖，且 `stop` 只涉及自身资源（取消 task、清自身 Map、发调试消息）。现状 `meiqiHezi` 角色同时包含被动与武器（`RoleRegistry`），但它们的 `stop` 均为空或仅清理自身状态 → 顺序不影响结果。
3. **与需求一致**：§2.6 "建议与 `start` 相反，并在报告中说明是否采用" → 采用，且此处说明。

---

## 10. 风险与未覆盖项（如实记录）

1. **构造时序属运行时语义，静态证据不能完全替代实跑**：§4 的论证基于"主线程同步、首次 tick 时刻不变"，但真正的兜底是队长提权起服，按需求 §6.4 合格线逐行比对（`Enabling ShadowHunterRolesPlugin v1.0.0` / `Shadow Hunter Character System enabled.` / `[SHDFGamePlugin] Succeed to find the dependency 'ShadowHunterRolesPlugin'!` / `Done (7.005s)!` 且无 ERROR/Exception）。**本任务未执行起服**（按广播归队长）。
2. **`awake` 目前无任何实现者**：本次只交付契约与调用点。`awake` 的幂等性/不可见状态约束**尚无组件可验证**，属"框架就绪、无使用者"状态；后续组件系统重构阶段才会真正使用。
3. **冲突区仍留一半**：`MeiqiheziEquipmentsPassive.stop` 空实现导致换角色/死亡后装备残留的**运行时缺陷在本次之后仍然存在**，必须由 `t8` 收口；本任务不得被判为已修复该缺陷。
4. **未做"仅 paper-api classpath"编译**：该项（需求 §4.1 B-7 强证据）属 B 流验收，本任务未涉及。
5. **数值比对为字面量级、非语义级**：只证明 A 流 7 文件内的数值字面量多重集未变，不覆盖 B 流文件（B 流由 t6/t7 负责）。

---

## 11. 交付物与证据文件

| 文件 | 说明 |
|---|---|
| `src/main/java/.../LifecycleAware.java` 等 7 个源文件 | 代码改动（见 §3） |
| `debug-logs/重构前准备-报告-A流.md` | 本报告 |
| `debug-logs/测试记录/t2-diff-before-after.txt` | 7 个文件的完整 unified diff（before 快照 vs 改动后），231 行 |
| `debug-logs/测试记录/t2-build.log` | 规定命令的完整构建日志（含 `copyPluginJar` 失败细节） |
| `debug-logs/测试记录/t2-build-x.txt` | `-x copyPluginJar` 的 `BUILD SUCCESSFUL` 日志 |
| `debug-logs/测试记录/t2-numeric-before.txt` / `debug-logs/测试记录/t2-numeric-after.txt` | 数值字面量比对输入/输出 |
