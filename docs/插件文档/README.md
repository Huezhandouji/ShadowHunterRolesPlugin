# ShadowHunterRolesPlugin · 使用文档（使用者向）

> **写作口径（重要）**
> 1. 本文每条技术断言都给出 `路径:符号/行` 作为出处，**行号是 2026-09-17 17:42（UTC+8）时点的读数**，会随重构漂移；复核一律以「标识符 + 内容片段」为准（口径出处：`docs/重构进度与交接.md:69`）。
> 2. **身份一律现算**：本文出现的字节数 / mtime / SHA256 只是"当时的读数"，任何人要引用都必须现场重算（`Get-Item` + `Get-FileHash`），不得直接抄本文的数字。
> 3. 本文只写**已实现**的行为。计划中的东西一律标注「**待阶段 N**」并指回设计文档，绝不写成已实现。
> 4. 本文属于**插件文档类**（使用者/开发者文档）；重构过程的交付小结、验证/评审报告属于**重构证据类**，两者分开存放，索引见 `docs/README-文档索引.md`。

---

## 1. 这个插件是什么

ShadowHunterRolesPlugin 是给 Minecraft Java 版（Paper）服务端用的**角色/职业系统**插件：给玩家装配一个"角色"，角色由若干**组件**（技能 Skill、被动 Passive、主武器 MainWeapon）组成，并维护能量 Energy、理智值 SanTE、生命、阵营 Faction、冷却与热键栏图标。

- 主类：`src/main/java/com/shadowHunterRolesPlugin/ShadowHunterRolesPlugin.java:23`（`public final class ShadowHunterRolesPlugin extends JavaPlugin`）
- 入口装配全部发生在 `onEnable()`：`ShadowHunterRolesPlugin.java:33-89`
- 对外 API：`com.shadowHunterRolesPlugin.api.RoleAPI`，在 `onEnable` 里注册进 Bukkit 服务表 —— `ShadowHunterRolesPlugin.java:83-85`（`Bukkit.getServicesManager().register(RoleAPI.class, roleAPI, this, ServicePriority.Normal)`）；接口定义见 `api/RoleAPI.java:15`
- **没有配置文件**：本工程当前不引入 `config.yml`（设计裁决见 `docs/组件系统设计-Unity风格.md:557-561`），所有数值都写在 Java 里（`registry/RoleLoader.java` 的角色定义 + 各组件构造器）。

### 与下游插件的关系
同工作区的 `SHDFGamePlugin` 声明了硬依赖：`../SHDFGamePlugin/src/main/resources/plugin.yml`（`depend:\n  - ShadowHunterRolesPlugin`）。因此**服务端上必须先有本插件**，否则下游插件无法加载。

---

## 2. 环境要求（全部从工程实测读，不是记忆）

| 项目 | 实测值 | 出处 |
|---|---|---|
| 编译依赖（paper-api） | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`（`compileOnly`） | `build.gradle.kts:30` |
| Java 版本 | **21**（`JavaLanguageVersion.of(21)`；Kotlin 侧同为 `jvmToolchain(21)`） | `build.gradle.kts:34-36`、`build.gradle.kts:55-57` |
| 运行/调试用服务端版本 | `minecraftVersion("1.21.11")`，JVM 参数 `-Xms2G -Xmx2G` | `build.gradle.kts:39-45` |
| `api-version` | `'1.21.11'` | `src/main/resources/plugin.yml:6` |
| 插件名 | `ShadowHunterRolesPlugin` | `plugin.yml:1` |
| 插件版本 | `'${version}'` → 构建时由 `processResources` 展开为工程 `version` | `plugin.yml:2`、`build.gradle.kts:47-52` |
| 工程 version | `1.0.0` | `build.gradle.kts:10`（**注意** `gradle.properties:2` 里还有 `version=1.0-SNAPSHOT`，被构建脚本显式赋值覆盖；实测产物名即证） |
| 产物 jar 名 | `ShadowHunterRolesPlugin-1.0.0.jar` | 实测 `build/libs/`（2026-09-17 17:40，138 934 B） |
| main 类 | `com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin` | `plugin.yml:4` |
| paper-plugin-loader | `com.shadowHunterRolesPlugin.ShadowHunterRolesPluginLoader` | `plugin.yml:5` |
| 加载阶段 | `load: POSTWORLD` | `plugin.yml:7` |
| Gradle 侧插件 | `xyz.jpenilla.run-paper` 3.0.2、Kotlin JVM 2.4.0 | `build.gradle.kts:3`、`settings.gradle.kts:3` |
| 调试服端口 | `server-port=25566` | `run/server.properties:61`（`run/` 属运行产物，不入库） |

---

## 3. 构建、安装与部署

### 3.1 构建
工程用 Gradle Wrapper。仓库实测的构建命令（与交付小结里使用的一致）：

```powershell
cd ShadowHunterRoles
$env:GRADLE_USER_HOME = "$PWD\.gradle-work"      # 本地缓存目录（.gitignore:35 忽略）
.\gradlew build -x copyPluginJar --console=plain
```

- `build` 结束时会 `finalizedBy("copyPluginJar")`（`build.gradle.kts:66-68`），把 `build/libs/${project.name}-${project.version}.jar` 拷到 `C:/Users/ROG/Desktop/paper1.21.11/plugins`（`build.gradle.kts:59-64`）；不需要自动拷贝时用 `-x copyPluginJar` 跳过。
- **判定口径**：不要只看退出码，要看 `BUILD SUCCESSFUL` + 最新 `.class` 的 mtime 是否刷新（工程纪律见 `docs/最终重构指南.md:1090-1103`）。

### 3.2 本地起服（开发环境）
```powershell
.\gradlew runServer        # 使用 build.gradle.kts:39-45 的配置（1.21.11 / 2G 内存）
```
- 服务端目录 = `run/`（`run/server.properties`、`run/plugins/`、`run/logs/latest.log`）。
- 起服判据（工程口径，**必须写明模式**）：见 `docs/最终重构指南.md:743-751`。摘要：
  - **Mode B（稳态，本项目采用）**：不安装 `SHDFGamePlugin`。合格线 = 「**三行 + 无新增 ERROR/Exception + 第 3 行（下游依赖发现行）的替代证据**」。
  - **Mode A（备选）**：把下游 jar 拷进 `run/plugins/` 后起服，期望四行齐。
  - **禁止**把"三行"当作"四行等价"通过；「无新增错误」必须锚定明确的基线日志文件。

### 3.3 生产部署
1. 用与目标服务端 **同为 1.21.11（Paper）** 的服务端；
2. 需要 **Java 21** 运行时；
3. 把 `ShadowHunterRolesPlugin-1.0.0.jar` 放进服务端 `plugins/`；
4. 若是联调环境，再放 `SHDFGamePlugin`（它 `depend` 本插件，见 §1）；
5. 起服后在控制台看到插件启用日志即可（`ShadowHunterRolesPlugin.java:87` 的 `"ShadowHunter Character System enabled."`）。

---

## 4. 命令与权限清单（以代码为准）

### 4.1 命令注册处
- 声明：`src/main/resources/plugin.yml:9-13`
  ```yaml
  commands:
    role:
      description: "The command of ShadowHunter role system."
      usage: "/role <list|choose|info>"
      aliases: [r]
  ```
- 执行器注入：`ShadowHunterRolesPlugin.java:69-75`（`getCommand("role").setExecutor(new RoleCommand(roleManager, roleRegistry))`）；命令找不到时只打一条 warning。
- 实现：`src/main/java/com/shadowHunterRolesPlugin/command/RoleCommand.java:13`（`implements CommandExecutor`），分发 switch 在 `:37-97`。

### 4.2 实际可用语法（`RoleCommand.onCommand` 实测）

| 语法 | 行为 | 出处 |
|---|---|---|
| `/role`（无参数） | **静默返回**，没有任何提示（`sendHelp` 那行被注释掉了） | `RoleCommand.java:32-35` |
| `/role set <roleId>` | 给自己设角色 | `:38-47`、`:142-169` |
| `/role set <roleId> <playerName>` | 给指定在线玩家设角色 | 同上 |
| `/role clear` | 清除自己的角色 | `:49-58`、`:171-211` |
| `/role clear <playerName>` | 清除指定在线玩家的角色（并给对方发一条提示） | 同上 |
| `/role energy get` | 查看自己的能量 | `:60-75`、`:101-115` |
| `/role energy get <playerName>` | 查看指定玩家的能量 | 同上 |
| `/role energy set <amount>` | 设置自己的能量 | `:76-85`、`:117-140` |
| `/role energy set <amount> <playerName>` | 设置指定玩家的能量 | 同上 |
| `/role help` | 打印帮助（4 行） | `:90-92`、`:213-219` |
| 其它任意参数 | `Wrong arguments. Use /role help to learn how to use.` | `:94-96`；`energy` 参数不足时同样这句，`:61-64` |

**别名**：`/r`（`plugin.yml:12`）。

### 4.3 命令的边界行为（写文档时逐条实测出的"实际行为"，不是设计意图）

| 现象 | 事实 | 出处 |
|---|---|---|
| 控制台不能用 | 非玩家执行 → `Only players can execute this command!` 并 return true | `RoleCommand.java:27-30` |
| 帮助文本不全 | `sendHelp` 只列 `set` / `clear` 四条，**不含 energy** | `:213-219` |
| `usage` 与实现不符 | `plugin.yml` 写的是 `list|choose|info`，**代码里这三个都未实现**（落到 default 分支） | `plugin.yml:11` vs `RoleCommand.java:37-96` |
| 能量数字非法则静默 | `Integer.parseInt` 抛 `NumberFormatException` 时直接 `return`，**无任何回显** | `:131-137` |
| 目标玩家离线/找不到 | `Cannot find the player you provided: <name>` | `:106-109`、`:122-125`、`:155-158`、`:179-182` |
| 目标没有角色 | `<name> has no role!` / `You have no role yet!` | `:110-113`、`:186-194` |
| 角色 id 不存在 | `Role '<id>' not exist!`（先查注册表再找玩家） | `:143-146` |
| 设角色成功回显 | `Your role has been set: <roleId>` 或 `The role of player [ <name>] has been set: <roleId>` | `:162-165` |

### 4.4 权限清单
- **本插件没有声明任何权限节点**：`plugin.yml` 全文 13 行，没有 `permissions:` 段（`plugin.yml:1-13`）。
- **代码里也没有任何权限检查**：全仓 `src/**` 搜 `hasPermission|permission` **零命中**（复算命令见 §6）。
- 结论：**`/role` 与 `/r` 对所有能进服的玩家开放**，没有 op/权限门槛。若需要限制，应新增权限节点并在执行器里 `sender.hasPermission(...)` 判定 —— 这属于**未实现**事项，不是"已有但没写文档"。
- 对照：同工作区的 `SHDFGamePlugin` 的命令**有**权限节点（`../SHDFGamePlugin/src/main/resources/plugin.yml` 的 `permission: "shadowhunter.game.player"`）—— 两者不要混淆。

---

## 5. 快速上手（玩家视角）

1. **选角色**：`/role set meiqihezi`（或 `/role set red`）；角色 id 清单见 `docs/插件文档/角色与技能.md`。
2. **看热键栏**：设好角色后，快捷栏 0 号位是主武器，1–3 号位是三个技能（槽位由角色模板的 `slotMap` 决定，见 `core/Role.java:280` 与 `registry/RoleLoader.java:106-109`）。图标颜色/材质表示状态：
   - 绿色名字 = 就绪；灰色 = 冷却中（**技能名后带 `x.xs` 秒数，主武器不带**）；红色 `DISABLED` = 被沉默/眩晕禁用；技能还有 `ENERGY LACK`（灰名，图标 `STRUCTURE_VOID`）表示能量不够。
   - 出处：`core/Skill.java:43-110`、`core/MainWeapon.java:52-118`（渲染实现的当前形态；统一渲染器接管属阶段 4.4）。
3. **按键**：
   - 左键 / 右键 / `Q`（丢弃键）= 触发当前手持的技能或主武器（`listener/SkillListener.java:28/68/108`、`listener/MainWeaponListener.java:63/92/116`）。
   - 用主武器**攻击玩家**时走攻击路径（`MainWeaponListener.java:29-60`）。
4. **物品保护**：技能/主武器物品**不能被丢弃、不能被塞进容器**（`InventoryClickEvent` 直接取消：`SkillListener.java:145-159`、`MainWeaponListener.java:151-165`）。
5. **掉线**：掉线**立即销毁角色实例**（不保留、重连后没有角色）——`listener/PlayerListener.java:39-43` 调 `roleManager.clearRole(uuid)`。设计裁决见 `docs/组件系统设计-Unity风格.md:557-561`。
6. **死亡**：死亡即清角色，并把 9 个快捷栏里的技能/武器物品清掉（`PlayerListener.java:21-28`）；重生时也再清一次（`:30-34`）。

---

## 6. 复算命令（把本文的关键断言现场核一遍）

```powershell
cd ShadowHunterRoles

# 环境要求
Select-String -Path build.gradle.kts -Pattern 'paper-api|JavaLanguageVersion|minecraftVersion|version ='
Get-Content src/main/resources/plugin.yml

# 命令与别名 / 权限
Select-String -Path src/main/resources/plugin.yml -Pattern 'commands|role|aliases|permission'
git grep -n "hasPermission" -- src          # 期望：无输出

# 命令分支（实测可用语法）
Select-String -Path src/main/java/com/shadowHunterRolesPlugin/command/RoleCommand.java -Pattern 'case "|args.length'

# 角色 id 与槽位（应与 角色与技能.md 一致）
Select-String -Path src/main/java/com/shadowHunterRolesPlugin/registry/RoleLoader.java -Pattern 'new Definition|new Role.Builder|addSkill|addMainWeapon|addPassive|faction|maxHP|maxEnergy|maxSanTE|icon'

# 掉线即销毁
Select-String -Path src/main/java/com/shadowHunterRolesPlugin/listener/PlayerListener.java -Pattern 'onPlayerQuit|clearRole'
```

---

## 7. 已知限制与注意点（都会影响玩家体验）

| # | 事实 | 出处 |
|---|---|---|
| 1 | **掉线角色不保留**（重连后需重新 `/role set`） | `PlayerListener.java:39-43`、`docs/组件系统设计-Unity风格.md:557-561` |
| 2 | 死亡清角色时，若玩家身上还挂着本系统发放的装备被动，`stop()` 会**无条件清空**头盔/胸甲/腿甲/靴子四个槽位（连带玩家自己的装备） | `roleComponent/meiqiHezi/passive/MeiqiheziEquipmentsPassive.java:72-81`、`roleComponent/red/RedEquipmentsPassive.java:72-81`（裁决依据：`docs/最终重构指南.md` §10 裁决 3，见这两处注释） |
| 3 | 主武器**攻击玩家**时无条件进入冷却（即使武器自身逻辑没做事） | `listener/MainWeaponListener.java:50-55`（`startMainWeaponCooldown` 后才是 `onAttack`/`onLeftClick`） |
| 4 | `/role` 无参数、`/role energy set abc` 都是**静默**的，没有任何提示 | `RoleCommand.java:32-35`、`:131-137` |
| 5 | `plugin.yml` 的 `usage` 文案（`list|choose|info`）**未实现** | `plugin.yml:11` vs `RoleCommand.java:37-96` |
| 6 | 本插件当前**没有配置文件**，改数值必须重新编译 | `ShadowHunterRolesPlugin.java`（无 `saveDefaultConfig` 调用）、裁决见设计文档 §9.1 |
| 7 | 命令的能量**没有范围校验回显**，但写入会被 clamp 到 `0..maxEnergy` | `RoleCommand.java:131-133` → `core/RoleInstance.java:560-568`（`Math.clamp`） |

> 第 2、3 条是"今天就是这样的行为"，本次重构把它们当作**冻结行为**保留，不是新引入的缺陷；如果要改，属于玩法变更，需要单独裁决。

---

## 8. 相关文档

- 角色/技能/被动/主武器的逐个清单与数值：`docs/插件文档/角色与技能.md`
- 架构、端口白名单、派发管道、迁移进度：`docs/插件文档/架构与组件模型.md`
- 想加一个新角色/技能：`docs/插件文档/开发指南-新增角色或组件.md`
- 日志怎么放、证据怎么归档：`docs/插件文档/日志与证据归档规范.md`
- docs 全目录索引：`docs/README-文档索引.md`
