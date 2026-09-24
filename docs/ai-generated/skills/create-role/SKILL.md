---
name: create-role
description: 在 ShadowHunterRolesPlugin 里**只做角色、不碰框架**地新增一个角色（角色模板 + 技能/被动组件 + 注册行 + 两条闸门）。当用户要求"新增角色 / 加一个职业 / 加一个技能或被动 / 给角色加物品与冷却"时使用。**不要**用于改框架（core/api/command/listener/manager/platform/registry 的非注册点部分）。
---

# Skill：新增一个角色（不碰框架）

> **本 skill 的判据全部现算自代码**（核对时间见仓库 `HEAD`）；**以代码为准** ✗ —— 若本文与代码冲突，信代码。
> 读完后你应该能在**不读全仓**的前提下完成一个角色：**3 处改动**（组件文件 + 注册行 + 常量）✓。

## 0. 前置假设（先确认这三条，否则停手问人 ✗）

1. 仓库根 = `ShadowHunterRoles/`；构建用 `gradlew`（Gradle wrapper）✓。
2. 每个命令**必须先设** `GRADLE_USER_HOME`（否则会去下载第二份缓存 ✗）：
   ```powershell
   cd C:\Users\ROG\Desktop\插件\ShadowHunterRoles; $env:GRADLE_USER_HOME="$PWD\.gradle-work"
   ```
3. **用例基线 = 91**（`src/test` 共 15 个测试文件）✓ —— 你**不得**删/停用任何测试 ✗；改了框架才会动它，而你**不该**改框架 ✓。

## 1. 硬边界表（★ 本 skill 的核心 ✗）

### 绝对不要改（改了就是碰框架 ✗）
| 路径 | 为什么 ✗ |
|---|---|
| `src/main/java/com/shadowHunterRolesPlugin/core/` | 领域核心：`Role`（聚合根）/`RoleInstance`（每实例容器）/`RoleInfoImpl`（只读服务面）—— 你**读**它们、**不写**它们 ✓ |
| `…/api/` · `…/internal/api/` | 公开 API 面 + 实现；`RoleApiSurfaceTest` 冻结 **58** 条签名 ✗（改一处即真红） |
| `…/command/` | 指令面（`/role …`）；加角色**不需要**新指令 ✓（`/role set <id>` 自动可用 ✓） |
| `…/listener/` | 平台事件入口（施放/攻击/伤害钩子投递）—— 由框架统一接线 ✓ |
| `…/manager/` · `…/platform/` · `…/config/` · `…/event/` | 运行期管理 / 平台适配 / 配置 / 对外事件 —— 角色与它们无关 ✓ |
| `…/registry/`（**除 §3 的注册点** ✗） | 模板装载与注册表；**唯一允许**碰的是 `RoleLoader` 里的**一行注册 + 一个 builder 方法** ✓ |
| `…/roleComponent/frameworkLevel/` | 框架级服务组件（`EnergyComponent`/`SanTEComponent`/`VitalsComponent`/`BuffComponent`/`TimerComponent`/`HotbarRenderComponent`）—— 你**取用**它们 ✓、**不新增/不改**它们 ✗ |
| `…/roleComponent/base/` | 组件基类（`Skill`/`MainWeapon`/`PassiveSkill`）—— 你 **extends** 它们 ✓ |
| `…/roleComponent/`（根，除 §3 允许的读取 ✗） | `RoleComponent`/`ActiveComponent`/`OperationProvider`/`ComponentFactory` 等基座 —— 只读 ✓ |
| `build.gradle.kts` · `settings.gradle.kts` · `src/main/resources/plugin.yml` · `config.yml` | 构建与插件清单；加角色**不需要**动它们 ✓（你**不新增依赖、不新增指令、不新增权限节点** ✓） |
| `src/test/`（**除 §6 自检** ✓） | 基线 91 不许降 ✗；给角色加测试**可选**（会改基线，需在结卡里申报 ✓） |

### 允许新增 / 修改（**只有这些** ✓）
| 路径 | 说明 |
|---|---|
| `src/main/java/com/shadowHunterRolesPlugin/roleComponent/custom/<角色名>/…` | **你的全部新代码**（建议子包：`skill/` · `passive/` · `mainWeapon/` ✓ —— 照 `custom/red/` 与 `custom/meiqiHezi/` 的既有布局 ✓） |
| `src/main/java/com/shadowHunterRolesPlugin/registry/RoleLoader.java` | **唯一允许碰的框架文件** ✓，且**只允许**：① 加一行 `private static final String ID_… = "…";` ② 加一行 `new Definition("<id>", RoleLoader::<id>Builder)` ③ 加一个 `private static Role.Builder <id>Builder()` ✓ |

## 2. 三层模型（30 秒版）

```
Role（模板：声明值 + 组件清单，全实例共享，只读）
  └─ RoleInstance（每玩家一份：容器 + 生命周期）
        └─ RoleComponent（组件：状态 + 行为；技能/被动/主武器都是组件）
```
- **组件 = 状态 + 生命周期 + 按实例持有** ✓；**能力接口**只在"表达的东西**不可能成为组件**"时才允许 ✓（见 §5 R-1/R-8）。
- 组件的**服务**只经**三个端口**取 ✓：`svc().self()` · `svc().components()` · `svc().roleInfo()` ✓（§5 R-6）。

## 3. 步骤（照做 ✓）

### 步骤 1：建角色目录
```
src/main/java/com/shadowHunterRolesPlugin/roleComponent/custom/<角色名>/skill/<Xxx>Skill.java
src/main/java/com/shadowHunterRolesPlugin/roleComponent/custom/<角色名>/passive/<Xxx>Passive.java
```
（只做技能/只做被动也行 ✓；主武器放 `mainWeapon/` ✓）

### 步骤 2：写组件（**两个真实骨架** ✓ —— 全部符号现算自 `custom/red/`）
**技能**（`extends Skill` ✓，照 `RedDeeplySorrowSkill`）：
```java
public class MySkill extends Skill {                       // ✓ core→roleComponent/base 已迁移：Skill 在 roleComponent.base
    public MySkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);                // ✓ 三参构造是基类要求
    }
    @Override public void start() {                        // ★ R-4：取组件**只在 start()** ✓（不在 awake() ✗）
        // 例：energy = svc().components().get(EnergyComponent.class);
    }
    @Override public void onCast(CastSignal signal) { }    // ✓ 施放回调（CastSignal 由框架投递）
    @Override public void update() { }                     // ✓ 每刻（需要时覆写）
    @Override protected boolean gateOpen() { return true; }      // ✓ 门控（不满足 ⇒ 不施放、不启冷却）
    @Override protected int currentEnergy() { return 0; }        // ✓ 施放前能量读口（与 EnergyComponent 一致）
    @Override public void stop() { }                       // ✓ 清理（幂等）

    public static final class Specification extends Skill.Specification {   // ★ 声明值写在这里 ✓
        public Specification() {
            super(net.kyori.adventure.text.Component.text("我的技能"),   // 显示名（★ Adventure ✗ 不用 ChatColor）
                  java.util.List.of(net.kyori.adventure.text.Component.text("说明")),
                  org.bukkit.Material.REDSTONE,                        // 图标
                  100,                                                 // 冷却 tick（0 = 无冷却）
                  0);                                                  // 耗能（0 = 不耗能）
        }
        @Override public MySkill create(String id, ComponentServices services) { return new MySkill(id, services, this); }
    }
}
```
**被动**（`extends PassiveSkill` ✓，照 `RedBleedPassive`/`AutoRecoverEnergyPassive`）：
```java
public class MyPassive extends PassiveSkill {
    public MyPassive(String id, ComponentServices services, Specification specification) { super(id, services, specification); }
    @Override public void update() { }                     // 每刻；或覆写 start()/stop()
    public static final class Specification extends PassiveSkill.Specification { /* 同型：显示名 + 说明 + 图标 */ }
}
```
**★ 冷却与耗能** ✓（R-7）：冷却由**组件自持** —— 在施放成功处调 `startCooldown()` ✓、需要停时 `stopCooldown()` ✓（**不要**去框架里找冷却表 ✗）。耗能写进描述符（第 5 参 ✓），施放前用 `currentEnergy()` 声明读口 ✓。

### 步骤 3：注册（**唯一允许碰的框架文件** ✓ · 三处小改）
`src/main/java/com/shadowHunterRolesPlugin/registry/RoleLoader.java`：
```java
// ① 常量区（照 ID_RED_DEEPLY_SORROW 等既有写法）
private static final String ID_MY_SKILL = "myrole_skill_my";

// ② defaultDefinitions() 里加一行（照 new Definition("red", RoleLoader::redBuilder)）
new Definition("myrole", RoleLoader::myroleBuilder),

// ③ 加 builder 方法（照 redBuilder() 的既有写法；这些 API 全部现算自 Role.Builder ✓）
private static Role.Builder myroleBuilder() {
    return new Role.Builder("myrole")
            .displayName(Component.text("我的角色"))
            .description(List.of(Component.text("说明")))
            .maxHP(40).baseATK(10).maxEnergy(100).maxSanTE(100)
            .faction(Faction.SHADOW)                                        // 可选
            .addComponent(ID_MY_SKILL, new MySkill.Specification().setSlot(3))   // ★ 栏位 0..8 ✓
            .addPassive(ID_MY_PASSIVE, MyPassive::new);                      // 被动不占栏位 ✓
}
```
**`Role.Builder` 的可用方法**（现算自 `core/Role.java:369-560` ✓）：`displayName(Component)` · `description(List<Component>)` · `addLineOfDescription(Component)` · `maxHP(double)` · `baseATK(double)` · `maxEnergy(int)` · `maxSanTE(int)` · `faction(Faction)` · `icon(Material)` · `addComponent(String, RoleComponent.Specification<?>)` · `addPassive(String, ComponentFactory<PassiveSkill>)` · `build()` ✓
★ **`addComponent` 的 spec 必须带栏位** ✗（`setSlot(0..8)` ✓，否则装配期抛异常 ✓）；`addPassive` **没有**栏位 ✓。

### 步骤 4：跑两条闸门（**真执行态** ✓）
```powershell
cd C:\Users\ROG\Desktop\插件\ShadowHunterRoles; $env:GRADLE_USER_HOME="$PWD\.gradle-work"
.\gradlew compileJava --rerun --no-build-cache --console=plain    # 期望：> Task :compileJava 执行态 + BUILD SUCCESSFUL
.\gradlew test      --rerun --no-build-cache --console=plain      # 期望：> Task :test 执行态 + 91 tests / 0 failures
```
★ **不要**只跑 `build` ✗（全 up-to-date 的"真空绿"不算读数 ✗）。

## 4. 反例（这些**都会**出问题 ✗）
| 反例 | 后果 |
|---|---|
| 在 `awake()` 里 `svc().components().get(...)` | ★ R-4 违反 ✗：装配序未定 ⇒ 可能取到 `null`（在 `start()` 里取 ✓） |
| 给技能**新造**一个"能上热键栏"的能力接口 | ★ R-1/R-8 违反 ✗：热键栏**已经是组件**（`HotbarRenderComponent`）⇒ 不要新接口 ✓ |
| 用 `ChatColor` / 裸字符串做显示名 | 违反"外观一律 Adventure" ✗（用 `Component.text(...)` ✓） |
| 自己去 `core/` 加冷却表 / 改 `RoleInstance` | 碰框架 ✗；冷却归组件自持（`startCooldown()` ✓） |
| 在 `plugin.yml` 加指令/权限 | 不需要 ✗（`/role set <id>` 自动可用 ✓） |
| 删/停用测试以让闸门变绿 | 违反 91 基线 ✗（真红就是真红 ✓） |

## 5. 必须遵守的既有判据（**编号原文未入库**，下列为按代码与 ADR 复原的可复算判据 ✓）

| 判据 | 内容 | 反例 |
|---|---|---|
| **R-1 / R-8** | 不得为"**已经是组件**"的关注点新造能力接口 ✗；能力接口**仅当**它表达的东西**不可能成为组件**时才成立 ✓ | 新造 `HotbarItemProviding` 式接口 ✗ |
| **R-4** | 组件依赖**只在 `start()`** 里取 ✓ | 在 `awake()`/构造器里取 ✗ |
| **R-6** | 只许**三端口**（`self`/`components`/`roleInfo`）+ **直接取组件** ✓ | 自造查找通道 / 反射 ✗ |
| **R-7** | 冷却**归组件自持** ✓（`startCooldown()` / `stopCooldown()`） | 去框架加冷却字段 ✗ |
| **归属原则** | 能力**各归其家**：能量→`EnergyComponent`、SanTE→`SanTEComponent`、生命→`VitalsComponent`、Buff→`BuffComponent`、计时→`TimerComponent`、热键栏→`HotbarRenderComponent` ✓ | 把能量状态写进自己的组件 ✗ |
| **`OperationProvider`** | **可选加入**：`String onOperationCommand(String payload)` ✓ —— **单方法冻结** ✗（不得加方法/默认实现 ✓）；payload 首 token 必为动词 ✓，grammar 写进你的 javadoc ✓ | 给接口加第二个方法 ✗ |
| **Adventure** | 一切玩家可见文本用 `net.kyori.adventure.text.Component` ✓ | `ChatColor` ✗ |
| **基线 91** | `src/test` 15 件 / 91 用例 ✓ —— 不得降 ✗ | 删测试 ✗ |
| **两条闸门** | `compileJava` + `test`，**真执行态**（`--rerun --no-build-cache` ✓） | 只跑 `build` ✗ |

## 6. 自检清单（结卡前逐条 ✓）
- [ ] 新文件**只在** `roleComponent/custom/<角色名>/…` ✓
- [ ] 框架改动**只有** `RoleLoader.java` 的三处（常量 + Definition 行 + builder）✓
- [ ] 组件依赖在 `start()` 取（R-4 ✓）；只用三端口（R-6 ✓）；冷却用 `startCooldown()`（R-7 ✓）
- [ ] 显示名/描述全 Adventure（无 `ChatColor` ✓）
- [ ] `addComponent` 的 spec 都 `setSlot(0..8)` ✓；`addPassive` 不带栏位 ✓
- [ ] 两条闸门**真执行态**：`> Task :compileJava` / `> Task :test` 都出现 ✓，XML = **91 / 0 / 0** ✓
- [ ] 未新增接口 ✗（除"确实不能成为组件"的关注点 ✓）、未改签名/注解 ✓、未动 `plugin.yml` ✓
- [ ] 在结卡里申报：新组件路径 + `RoleLoader` 改动行数 + 两条闸门读数 + 未覆盖项 ✓

## 7. 出错怎么办（**停手 + 申报** ✗）
- 若你发现**必须**改 `core/`/`api/`/`command/`/`listener/`/`frameworkLevel/` 才能完成 ⇒ **停手** ✗，把"要改什么 + 为什么 + 影响面"写进结卡，交人裁定 ✓（不要顺手改 ✗）。
- 若闸门**真红**且原因在框架 ⇒ 同上 ✓；若原因在你的组件 ⇒ 改你的组件 ✓。
