# ShadowHunterRolesPlugin

Paper 服务端的**角色与技能系统**插件：给玩家装配一个"角色"，角色由若干**组件**（技能 / 被动 / 主武器）组成，并维护能量、SanTE、生命、阵营、冷却与热键栏图标。

## 运行环境与依赖

| 项目 | 值 | 出处 |
|---|---|---|
| 服务端 | Paper（`api-version: 1.21.11`） | `src/main/resources/plugin.yml` |
| Java | 21 | `build.gradle.kts`（Gradle toolchain） |
| 编译依赖 | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`（`compileOnly`） | `build.gradle.kts` |
| 调试服务端版本 | `1.21.11` | `build.gradle.kts`（`runServer.minecraftVersion`） |
| 插件名 / 主类 | `ShadowHunterRolesPlugin` / `com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin` | `src/main/resources/plugin.yml` |
| 加载阶段 | `POSTWORLD` | `src/main/resources/plugin.yml` |

## 构建

```powershell
cd ShadowHunterRoles
$env:GRADLE_USER_HOME = "$PWD\.gradle-work"        # 本地 Gradle 缓存（不入库）

.\gradlew build -x copyPluginJar --console=plain
```

- `-x copyPluginJar` 用于**跳过**构建后自动把 jar 拷贝到调试服插件目录这一步（`build.gradle.kts` 里的 `copyPluginJar`；目标目录见 `gradle.properties` 的 `pluginCopyPath`）。需要自动拷贝时去掉该参数。
- 产物：`build/libs/ShadowHunterRolesPlugin-<version>.jar`（版本号取自 `build.gradle.kts` 的 `version`）。
- 判定"构建是否真的发生"：看 `BUILD SUCCESSFUL` **且** `build/classes` 下最新 `.class` 的修改时间已刷新，不要只看退出码。

## 运行（本地调试）

```powershell
.\gradlew runServer
```

- 服务端工作目录为 `run/`；端口等参数见 `run/server.properties`（当前 `server-port=25566`）。
- 需要 Java 21 运行时；`runServer` 会按 `build.gradle.kts` 的配置启动 Paper 1.21.11。

## 部署

把 `build/libs/ShadowHunterRolesPlugin-<version>.jar` 放进服务端 `plugins/` 后启动。本插件当前**没有配置文件**，所有数值都写在 Java 里（角色定义与各组件构造器）。

## 文档

- **面向 AI 的项目文档：`docs/ai-generated/`**
  - `README.md` —— 索引、阅读顺序、口径说明（含未入库项申报）
  - `架构总览.md` —— 运行时链路、关键设施、分层方向、11 个包职责
  - `组件模型.md` —— 三层模型、判据集（含反例与可复跑命令）、端口面现状、包结构、6 份 ADR 的决策结论
  - `如何新增一个角色.md` —— 新增角色 / 技能 / 被动 / 主武器的步骤
  - `skills/create-role/SKILL.md` —— 「新增一个角色」的可执行技能清单
- **测试记录、运行日志与过程文档：`debug-logs/`**（内含 `测试记录/` 与 `回滚快照/`）。
  该目录下**新增**文件默认不入库（被忽略规则覆盖），需要随仓库发布时用 `git add -f` 显式纳入。
