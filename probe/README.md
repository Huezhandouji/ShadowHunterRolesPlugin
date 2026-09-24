# 探针插件（工装件 · 阶段 13 · t129）

> **一句话**：给「④ 窗口半」的三处运行级读数（W-t73 回调链/隔离条数 · W-t83 `was QUARANTINED` 反例/六步窗口 ·
> t88 装配表逐格）提供**唯一的观测手段** —— `VitalsComponent.Participant` 的**生产实现者 = 0** ⇒ 必须由探针注册一个 ✓。

## §1 机制说明（AV1）

| 项 | 机制 |
|---|---|
| **如何被加载** | `gradlew runServer`（run-paper 插件）从 **`run/plugins/`** 加载插件 ⇒ 【读数卡】把 `probe/build/libs/ShadowHunterProbe-1.0.0.jar` 复制到 `run/plugins/` 即可 ✓（与主插件并列 ✓） |
| **如何构建** | **一条命令**：`.\probe\build.ps1`（本机执行策略禁用 `.ps1` ✗ ⇒ 等价形式：`powershell -NoProfile -ExecutionPolicy Bypass -File .\probe\build.ps1` ✓，**不改系统策略** ✓；或直接两条：`.\gradlew jar` + `.\gradlew -p probe jar` ✓）⇒ ① 主工程 `gradlew jar` 出主 jar（探针要 `compileOnly` 引用它的类型面 ✓）② `gradlew -p probe jar` 出探针 jar ✓ |
| **构建隔离（硬约束①）** | `probe/` 是**独立 Gradle 构建**（自带 `settings.gradle.kts` ✓），主工程 `settings.gradle.kts` **未** include 它 ⇒ 主 `jar` 任务的输入集里**没有** `probe/` ⇒ **不可能**进发布 jar ✓（由拓扑保证 ✓，不是靠约定 ✓） |
| **可复现（硬约束②）** | 一条命令重建 ✓；产物名固定 = `ShadowHunterProbe-1.0.0.jar` ✓；无网络新依赖（paper-api 已在 Gradle 缓存 ✓） |
| **路径具名（硬约束③）** | 全部落在 inScope 内：`probe/settings.gradle.kts` · `probe/build.gradle.kts` · `probe/build.ps1` · `probe/README.md` · `probe/src/main/java/com/shadowHunterProbe/ShadowHunterProbe.java` · `probe/src/main/resources/plugin.yml` ✓ |
| **运行时依赖方向** | 探针 `depend: [ShadowHunterRolesPlugin]` ✓（单向 ✓）；探针 jar 内**不含**主插件任何类 ✓（`compileOnly` ✓） |

## §2 探针能力（AV2 · 逐条对应 F-2）

| 能力 | 落点 |
|---|---|
| ① **注册 `Participant` 实现** | `ProbeParticipant extends RoleComponent implements VitalsComponent.Participant` ✓；`/probe install <player> [id]` 把它**插进该玩家实例的容器** ✓（`ComponentRegistry.insert(index, component, Declaration.of(component))` —— `insert` 是**写口**，冻结后仍合法 ✓）；插入后 `awake()`/`start()`（与容器同序 ✓） |
| ② **可计数序列** | 静态 `AtomicInteger DAMAGED / HEALED` ✓；每次回调**自增并落一行结构化日志** ✓ ⇒ 供"隔离条数"读数逐条复算 ✓ |
| ③ **故意抛异常开关** | `/probe throw <on|off>` ✓ ⇒ `throwMode=true` 时回调**抛** `IllegalStateException` ✓ ⇒ 主插件的 `guardedCall` 记 `pendingQuarantine`、窗口 `finally` 跑四步 ⇒ 日志应出现 `was QUARANTINED` ✓（反例计数 0 → 1 ✓） |
| ④ **同 id 两份构造** | `/probe dup <player> [id]` ⇒ 向**同一实例**插入**两份同 id** 的探针组件 ✓（id 可重复 ✓，t67 起合法 ✓）⇒ 供"多份且无 `#index` ⇒ 回绝"与隔离读数 ✓ |
| ⑤ **一行式结构化日志** | `[probe] at=<ms> event=<enable|damaged|healed|install|dup|throw|status|disable> seq=<n> …` ✓（前缀 `[probe]` ✓，与主插件 `[command-access]`/`[command-debug]` 同风格 ✓） |

## §3 命令面（给【读数卡】用）

```
/probe install <player> [id]   把探针 Participant 插进该玩家实例（默认 id = probe）
/probe dup     <player> [id]   向同一实例插两份**同 id**（默认 id = probeDup）
/probe throw   <on|off>        故意抛异常开关（反例用）
/probe status                  打印计数（damaged/healed/installed/throwMode）
/probe list    <player>        列出该实例全部组件 id（**装配表读数**用）
```

## §4 已知边界（如实申报 · 工装件性质）

1. **反射**：探针经 `RoleAPI` 实现类（`internal.api.RoleAPIImpl`）的**私有 `roleManager` 字段**取 `RoleManager` ⇒ `getRoleInstance(player)` ✓
   —— 主插件**没有**公开的 manager 入口（只公开 `getRoleAPI()` ✓），而探针**必须**拿到实例容器才能插组件 ✓
   ⇒ 这是**工装件的明确让步** ✓：**产品代码不反射** ✓（设计定案 §5「禁止反射」是对产品的约束 ✓），探针**只读**该字段、不改它 ✓。
2. **不注册 `Participant` 之外的能力** ✗；不改主插件状态 ✗（除"插入探针组件"这一显式动作 ✓）。
3. **本卡不起服** ✗（只造工装件 ✓）；`run/plugins/` 的复制动作归【读数卡】✓。
