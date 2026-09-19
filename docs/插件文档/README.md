# ShadowHunterRolesPlugin 使用手册

这份文档写给**服主和玩家**。读完它你能做到三件事：把插件装到自己的 Paper 服务端上；知道玩家进服后能用哪些命令、怎么装配和切换角色；看懂热键栏里每个图标的含义与按键方式。

想了解内部结构、或者准备自己动手加角色与组件的开发者，请接着看同目录的 `架构与组件模型.md` 与 `开发指南-新增角色或组件.md`。

---

## 这是什么

ShadowHunterRolesPlugin 是 Minecraft Java 版（**Paper**）服务端上的**角色 / 职业系统**：玩家用一条命令给自己装配一个"角色"，角色由若干**组件**拼成。

| 组件类型 | 玩家能感觉到的东西 | 本插件现有的例子 |
|---|---|---|
| 技能 Skill | 占热键栏 1–3 号位；右键、左键或 `Q` 触发；有冷却与能量消耗 | 「漫不经心」「圆弧斩」「煞气震赫」 |
| 被动 Passive | 后台持续生效，不占按键 | 「自动恢复能量」「流血」「穿戴装备」 |
| 主武器 MainWeapon | 占热键栏 0 号位；攻击玩家或左键时触发 | 「Jue Jue」「至洁之刃」 |

角色自己维护一组数值：**能量 Energy**、**理智 SanTE**、生命上限、**阵营 Faction**，以及每个技能的冷却状态。这些数值与状态最终都反映到热键栏图标上（见下文「热键栏图标怎么读」）。

插件里另外有几件玩家一定会遇到的事：

- **死亡即清角色**：死亡时角色被清除、热键栏里的技能与武器物品被收回；重生时再清一次。
- **掉线不保留角色**：玩家退出服务器时角色实例被销毁，重新进服需要重新 `/role set`。
- **技能与武器物品受保护**：这些物品不能被丢到地上，也不能放进箱子 / 背包容器。

代码位置：主类 `com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin`（`onEnable` 中完成全部装配），对外接口 `com.shadowHunterRolesPlugin.api.RoleAPI`。

### 与其它插件的关系

本插件对外提供 `RoleAPI`：启用时由 `ShadowHunterRolesPlugin.onEnable` 注册进 Bukkit 服务表（`Bukkit.getServicesManager().register(...)`，`ServicePriority.Normal`），其它插件可以用 `ServicesManager` 取到它来读写玩家的角色、能量、SanTE、生命、阵营与技能冷却。

同工作区里的 `SHDFGamePlugin` 在它的 `plugin.yml` 里声明了 `depend: ShadowHunterRolesPlugin`。也就是说：**同时使用这两个插件时，ShadowHunterRolesPlugin 必须先装**，否则 `SHDFGamePlugin` 无法加载。

---

## 环境要求

| 项目 | 要求 | 出处 |
|---|---|---|
| 服务端 | **Paper**（本插件按 Paper 编写；未做 Folia 适配，也没有 Folia 分支） | `build.gradle.kts`、`platform/Scheduler`、`platform/BukkitSchedulerAdapter` |
| 服务端版本 | `api-version: '1.21.11'`，调试起服用 `minecraftVersion("1.21.11")` | `src/main/resources/plugin.yml`、`build.gradle.kts` |
| Java | **21**（`JavaLanguageVersion.of(21)`、`jvmToolchain(21)`） | `build.gradle.kts` |
| 编译依赖 | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`（仅编译期） | `build.gradle.kts` |
| 构建工具 | Gradle Wrapper（`gradle-9.6.1-bin`）+ `xyz.jpenilla.run-paper` 3.0.2 + Kotlin 2.4.0 | `gradle/wrapper/gradle-wrapper.properties`、`build.gradle.kts`、`settings.gradle.kts` |
| 插件名 / 主类 | `ShadowHunterRolesPlugin` / `com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin` | `plugin.yml` |
| 插件加载器 | `com.shadowHunterRolesPlugin.ShadowHunterRolesPluginLoader`（`paper-plugin-loader`） | `plugin.yml` |
| 加载阶段 | `load: POSTWORLD` | `plugin.yml` |
| 产物 | `build/libs/ShadowHunterRolesPlugin-1.0.0.jar`（工程 version = `1.0.0`） | `build.gradle.kts` |
| 运行内存（调试起服） | `-Xms2G -Xmx2G` | `build.gradle.kts` 的 `runServer` |
| 配置文件 | **没有** `config.yml`，也没有任何 `saveDefaultConfig` 调用 | `src/main/resources/`（只有 `plugin.yml`） |

> 工程 version 是 `build.gradle.kts` 里的 `version = "1.0.0"`；`gradle.properties` 里的 `version` 会被它覆盖。`plugin.yml` 的 `version: '${version}'` 在构建时由 `processResources` 展开。

---

## 构建、安装与部署

### 构建

仓库自带 Gradle Wrapper，在仓库根目录执行：

```powershell
cd ShadowHunterRoles
$env:GRADLE_USER_HOME = "$PWD\.gradle-work"   # 可选：把 Gradle 缓存放在仓库内，避免写用户目录
.\gradlew build -x copyPluginJar --console=plain
```

- `-x copyPluginJar` 用来跳过"自动拷贝到服务端目录"这一步（不跳过也可以，见下条）。
- 构建成功会看到 `BUILD SUCCESSFUL`；产物在 `build/libs/ShadowHunterRolesPlugin-1.0.0.jar`。
- 自动拷贝：`build.gradle.kts` 注册了 `copyPluginJar`，并在 `build` 结束时调用它。它**只在 `gradle.properties` 里配置了 `pluginCopyPath` 时**才真的复制 —— 把该属性指向目标服务端的 `plugins` 目录（例如 `pluginCopyPath=C:/你的服务端/plugins`），以后每次 `build` 就会顺带把 jar 拷过去。该文件不入版本库，属于本机配置。

### 本地起服调试

```powershell
.\gradlew runServer
```

- 由 `run-paper` 插件拉起 Paper 1.21.11，服务端目录是仓库内的 `run/`（`run/server.properties`、`run/plugins/`、`run/logs/latest.log`）。
- 起服成功时控制台会打印 `ShadowHunter Character System enabled.`（`ShadowHunterRolesPlugin.onEnable` 的收尾日志）。
- 如果看到 `No role templates were registered; /role and SHDF role selection will be unavailable.`，说明角色模板一条都没装配成功，需要查上面的 `SEVERE` 日志。

### 部署到正式服务端

1. 准备 **Paper 1.21.11** 服务端，运行环境为 **Java 21**；
2. 把 `ShadowHunterRolesPlugin-1.0.0.jar` 放进服务端 `plugins/`；
3. 如果还要用 `SHDFGamePlugin`，把它一并放进 `plugins/`（它依赖本插件，先装本插件即可，加载顺序由依赖声明保证）；
4. 重启服务端，控制台出现 `ShadowHunter Character System enabled.` 即加载完成。

---

## 命令与权限

### 命令一览

主命令是 `/role`，**别名 `/r`**（`plugin.yml` 的 `commands: role:` 段声明，主类 `ShadowHunterRolesPlugin.onEnable` 里通过 `getCommand("role").setExecutor(new RoleCommand(...))` 注入执行器）。

| 命令 | 作用 |
|---|---|
| `/role set <角色id>` | 给自己装配角色 |
| `/role set <角色id> <玩家名>` | 给指定在线玩家装配角色 |
| `/role clear` | 清除自己的角色 |
| `/role clear <玩家名>` | 清除指定在线玩家的角色（对方会收到一条提示） |
| `/role energy get` | 查看自己的能量 |
| `/role energy get <玩家名>` | 查看指定玩家的能量 |
| `/role energy set <数值>` | 设置自己的能量 |
| `/role energy set <数值> <玩家名>` | 设置指定玩家的能量 |
| `/role help` | 打印帮助文案 |
| `/role debug cooldown <status\|end\|restart> <槽位\|组件id>` | 仅 op：查看 / 结束 / 重启某个技能或主武器的冷却 |
| `/role debug sched [all]` | 仅 op：打印一组调度器实测数据 |

- 子命令名不区分大小写（`/role SET` 与 `/role set` 等价）。
- **命令执行者必须是玩家**：控制台或命令方块执行会收到 `Only players can execute this command!`。
- `/role` **不带任何参数时不输出任何内容**（静默返回）。
- 第一个参数不认识时输出 `Wrong arguments. Use /role help to learn how to use.`。
- **`/role` 的 Tab 补全会补全命令本身**：第一层补全 `set` / `clear` / `energy` / `help` / `debug`；第二层交给对应子命令 —— `set` 补全角色 id，`energy` 补全 `get` / `set`，`debug` 补全 `cooldown` / `sched`（非 op 玩家补全为空）。补全来自 `RoleCommand implements TabCompleter`，不需要在 `plugin.yml` 里额外声明。

### 权限

**本插件没有声明任何权限节点**：`plugin.yml` 里没有 `permissions:` 段，代码里也没有任何 `hasPermission` 检查。也就是说 `/role` 与 `/r` 对**所有能进服的玩家**开放。

唯一的例外是调试子命令：`/role debug ...` 只对 **op** 玩家有效（`DebugCommand` 在进入调试树之前判断 `player.isOp()`）。非 op 玩家无论后面跟什么参数，都会收到同一句 `You do not have permission to use this command.`，也不会看到调试话题的补全候选。

如果希望把 `/role` 做成有权限门槛的命令，需要自行新增权限节点并在命令执行处判定 —— 本插件当前不提供这一能力。

### 调试子命令的输出

`/role debug ...` 是给管理员排查问题用的，输出同时发给玩家自己，关键行还会以 `[command-debug] ` 前缀写进服务端日志（`logs/latest.log`）。

| 命令 | 输出要点 |
|---|---|
| `/role debug cooldown status <槽位\|组件id>` | `[cooldown] <组件id> \| cooling=<true/false> \| remainingTicks=<剩余刻> \| remainingSeconds=<剩余秒> \| declaredTicks=<声明冷却>` |
| `/role debug cooldown end <槽位\|组件id>` | 结束冷却：输出 `end(<组件id>) returned=<true/false>`；处于冷却中时返回 `true` 并触发组件的冷却结束回调，未在冷却中则显示 `no-op (was not cooling)` |
| `/role debug cooldown restart <槽位\|组件id>` | 以声明值重新开始冷却：输出 `restart(<组件id>) ... \| newRemainingTicks=<剩余刻>`；旧冷却段未走完时会显示 `RESTARTED dispatched (old segment dropped)` |
| `/role debug sched [all]` | 打印调度器探针：命令线程名、`GlobalRegionScheduler` 的 `execute` / `run` / `runDelayed` / `runAtFixedRate` 实际触发刻、与 `Bukkit.getScheduler()` 的对照、取消语义、以及一行结论 |

参数说明与报错：

- `<槽位|组件id>` 里**纯数字按热键栏槽位解析**（读取该角色模板的槽位表），其它写法按组件 id 解析；两种都找不到时输出 `No component found for: <输入>`。
- 目标不是技能 / 主武器（例如指向一个被动）时输出 `Not an active component (skill/main weapon): <组件id>`。
- 参数不足时输出用法提示 `Usage: /role debug cooldown <status|end|restart> <slot|componentId>` / `Usage: /role debug sched [all]`。
- 自己还没有角色时输出 `You have no role yet!`。

### 命令的边界行为（都是当前的实际行为，不是设计意图）

| 现象 | 实际行为 | 出处 |
|---|---|---|
| `/role set` 参数个数不对（0 个或 3 个以上） | **静默返回**，没有任何提示 | `command/SetRoleCommand.execute` |
| `/role clear` 参数个数多于 1 个 | **静默返回** | `command/ClearRoleCommand.execute` |
| `/role energy` 不带动作 | 输出 `Wrong arguments. Use /role help to learn how to use.` | `command/EnergyCommand.execute` |
| `/role energy <未知动作>`，或 `get` / `set` 参数个数不对 | **回退打印帮助文案**（不报错） | `command/EnergyCommand.execute` |
| `/role energy set` 的数值不是整数 | **静默返回**（`Integer.parseInt` 失败直接返回，无回显） | `command/EnergyCommand.handleSetEnergy` |
| `/role energy set` 的数值超出范围 | 不报错，写入值会被夹到 `0..maxEnergy` | `command/EnergyCommand.handleSetEnergy` → `core/RoleInstance.setCurrentEnergy` 的 `Math.clamp` |
| 目标玩家不在线 / 名字打错 | `Cannot find the player you provided: <名字>` | 各子命令的目标解析分支 |
| 目标玩家没有角色 | 能量查询输出 `<玩家名> has no role!`；清除自己的角色输出 `You have no role yet!`，清除别人的输出 `[<玩家名>] has no role yet!` | `command/EnergyCommand`、`command/ClearRoleCommand` |
| 角色 id 不存在 | `Role '<id>' not exist!` | `command/SetRoleCommand.handleSet` |
| 装配成功 / 失败的提示 | `Your role has been set: <角色id>`（帮别人设为 `The role of player [ <名字>] has been set: <角色id>`）；失败为 `Role set operation failed.` | `command/SetRoleCommand.handleSet` |
| `/role help` 的文案 | 打印 5 行（标题 + `set` 两条 + `clear` 两条），**不包含 `energy` 与 `debug`** | `command/HelpCommand.sendHelp` |

---

## 玩家快速上手

### 装配角色

```
/role set meiqihezi      （或 /role set red）
/r                       （别名）
```

角色 id 与各自技能、数值见 `角色与技能.md`。装配瞬间热键栏就会被布置好：0 号位是主武器，1–3 号位是三个技能（槽位来自各角色的槽位表）。

### 怎么触发

| 操作 | 触发的东西 |
|---|---|
| **右键** 手持技能物品 | 该技能 |
| **左键** 手持技能物品 | 该技能（左键分支） |
| **`Q`（丢弃键）** 手持技能物品 | 该技能（丢弃分支），同时**物品不会真的掉出去** |
| **左键** 手持主武器 | 主武器的左键分支（现有两个武器里只有「Jue Jue」实现了它：能量 ≥ 20 时打出范围伤害） |
| **右键** 手持主武器 | 主武器的右键分支（现有两个武器都没有实现它，按下不产生效果） |
| **`Q`** 手持主武器 | 主武器的丢弃分支（现有两个武器都没有实现它），物品不会掉出去 |
| **攻击玩家**（手持主武器） | 主武器的攻击分支；原版伤害被取消，伤害与效果由本插件计算 |

技能与主武器的冷却没走完时，触发不会生效（`Q` 仍会被拦下，物品不会丢出）。另外并不是每个组件都实现了全部按键：现有两个主武器都没有实现右键与 `Q` 分支；技能侧也有只响应右键的例子（见 `角色与技能.md`）。

### 热键栏图标怎么读

图标是实时刷新的：技能在冷却时会显示剩余秒数，冷却结束、能量恢复、状态被解除都会自动恢复。

| 状态 | 图标材质 | 名字 | 附加说明 |
|---|---|---|---|
| 就绪 | 组件自己声明的图标（如「圆弧斩」是金锭、「Jue Jue」是钻石锄） | **绿色**、加粗 | 显示该组件的描述文字 |
| 冷却中 | `STRUCTURE_VOID`（灰色虚空方块） | **灰色**、加粗 | **技能**在名字后追加剩余秒数（形如 ` x.xs`）；**主武器不追加秒数** |
| 被禁用（沉默 / 眩晕） | `BARRIER`（屏障） | **红色**、加粗、后缀 ` DISABLED` | 表示此刻不能施放 |
| 能量不足 | `STRUCTURE_VOID` | **灰色**、加粗、后缀 ` ENERGY LACK` | 只有**技能**会出现这个状态；主武器没有能量消耗，永远不会显示 |

每种状态的物品下方还有固定的描述行（例如冷却中显示 `Skill is on cooldown.`，普通状态显示 `Skill is ready.`），随后一行分隔线与该组件自己的描述文字。**状态判定顺序是固定的：冷却 → 被禁用 → 能量不足 → 就绪**；同一条判定链路上先满足的状态优先显示。

### 物品保护

技能物品与主武器物品都带有隐藏的持久化标记（`skill_id` / `main_weapon_id`）：

- 把它们**丢出去**（`Q`）会被拦下，转而触发技能 / 武器（见前文「怎么触发」）；
- 在**背包或容器界面里点击**它们会被拦下（`InventoryClickEvent` 直接取消），既拿不到光标，也放不进去；
- 角色被清除（`/role clear`、死亡、掉线）时，热键栏 0–8 号位里所有带上述标记的物品会被一并清掉。

### 角色什么时候会消失

| 事件 | 结果 |
|---|---|
| `/role clear` / 被别人 `/role clear <你>` | 角色被清除，热键栏物品收回，属性与效果回收 |
| 死亡 | 角色被清除，热键栏物品收回；重生时再清一次热键栏 |
| 掉线 | 角色实例被销毁，重新进服需要重新 `/role set` |
| 重新 `/role set` 另一个角色 | 先清除旧角色，再装配新角色 |
| 服务端关闭 / 插件卸载 | 所有在线玩家的角色被逐个清除释放 |

---

## 已知问题与当前限制

以下都是**现在就是这样**的行为，列出来是为了让你在开服前有预期：

1. **掉线不保留角色**：玩家退出即销毁实例，重连后需要重新装配。这是刻意选择的实现方式。
2. **没有配置文件**：角色数值、冷却、能量消耗、伤害都写死在 Java 代码里，改数值必须重新编译并重启服务端。数据驱动的角色配置目前没有实现。
3. **清角色会清掉玩家身上的四个装备槽**：「穿戴装备」类被动在生效时会替换头盔 / 胸甲 / 腿甲 / 靴子，停止生效时**无条件清空这四个槽位**（`roleComponent/meiqihezi/passive/MeiqiheziEquipmentsPassive.stop`、`roleComponent/red/RedEquipmentsPassive.stop`）。也就是说玩家自己原本穿的装备在被清角色时也会一起消失。
4. **攻击玩家会强制进入武器冷却**：只要手持主武器攻击到玩家，攻击分支就会按声明值启动冷却（`roleComponent/meiqihezi/mainWeapon/MeiqiheziJuejueMainWeapon.onAttack`、`roleComponent/red/RedSanctifiedBladeMainWeapon.onAttack`），即使本次攻击只是普通挥击。
5. **`plugin.yml` 的 `usage` 文案与实际命令不一致**：文件里写的是 `/role <list|choose|info>`，而这三个子命令并不存在（实际可用的是 `set` / `clear` / `energy` / `help` / `debug`）。这条文案只影响命令列表里的提示，修正它需要改 `plugin.yml`。
6. **部分输入是静默的**：`/role` 不带参数、`/role set` 参数个数不对、`/role energy set abc` 都不会给出任何提示（见前文「命令的边界行为」）。
7. **能量只能被夹取，不会报错**：`/role energy set` 写入超出范围的数值不会提示，实际写入值被夹到 `0..当前角色 maxEnergy`。
8. **帮助文案不全**：`/role help` 只列出 `set` 与 `clear`，没有 `energy` 与 `debug` 的用法。
9. **只有 op 能用调试子命令**：`/role debug ...` 对普通玩家一律拒绝；插件没有提供更细的权限节点。

---

## 文档地图

| 文件 | 写给谁 | 内容 |
|---|---|---|
| `docs/插件文档/README.md` | 服主、玩家 | 本文件：安装部署、命令与权限、玩法速查、已知限制 |
| `docs/插件文档/角色与技能.md` | 玩家、服主 | 两个角色的逐个清单：技能、被动、主武器、数值与效果，以及描述文字与实现数值对不上的地方 |
| `docs/插件文档/架构与组件模型.md` | 开发者 | 各层职责、端口白名单、组件生命周期、施放与冷却契约、热键栏渲染契约、平台层与对外接口 |
| `docs/插件文档/开发指南-新增角色或组件.md` | 开发者 | 新增技能 / 被动 / 主武器 / 角色 的具体步骤与可照抄的骨架，命令编写规范，常见限制 |
