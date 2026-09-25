# ai-generated/ · 本项目的文档（写给 AI 读）

> **这份文档集面向 AI**：每条规则给【做 / 不做】+ 反例 + **可复跑命令**；不写协作过程、不含易变身份量。
> **基线**：本目录三份文档的现算读数（口径 = 顶层目录**含直属 `.java`** 才算「包」）= 顶层 **9 包** · 未过滤的**顶层目录共 10 个**（差额 = `internal/`：它**没有直属 `.java`**、只有 `internal/api/` 子包；★ **按「顶层目录」数会得 10**，两个数都对、只是口径不同）· 任意层级**末级包共 23 个**（该口径的取数命令见下方「基线取数命令」块①）· `core/` **13 件** · `roleComponent/` **38 件** · `core/ports/` **4 件**（端口接口 3 + 服务集记录 1）· `src/main` **89 件** · `src/test` **17 件** / `@Test` **117**。每条口径与取数见三件文档各自的命令块。**与代码不一致处以代码为准**。

## 阅读顺序（也是信任顺序）

1. **`架构总览.md`** —— 一条链路（模板 → 实例 → 组件）+ 三件必须先知道的设施（受保护派发入口 / 唯一物品写点 / 两阶段生命周期）+ 9 个包的职责。
2. **`组件模型.md`** —— 三层模型（`Role` → `RoleInstance` → `RoleComponent`）+ **判据集** + 端口面现状 + 包结构 + **6 份 ADR 的决策结论**。
3. **本文件** —— 索引与使用说明。

## 每份写什么

| 文件 | 内容 | 何时读 |
|---|---|---|
| `架构总览.md` | 运行时链路、关键设施、分层方向、目录职责 | 第一次接触仓库 / 要改动跨层行为时 |
| `组件模型.md` | 组件契约、判据集（含反例）、端口现状、包结构、决策史要点 | 写或改**组件**、动**端口面**、判断"该不该合并接口"时 |
| `../../README.md` | 索引、阅读顺序、口径说明（含**未入库项申报**） | 进入本目录的第一步 |
| `使用者手册.md` | **面向玩家 / 管理员**（**不是** AI 向）：插件做什么、指令表、角色表、机制分节（能量 / SanTE 归零惩罚 / 阵营与敌对 / 冷却 / 快捷栏）、权限节点表、常见问题（含可自检步骤） | 要给人讲清"这插件怎么用"时 |

## 本目录里唯一的非 AI 向文件

- **`使用者手册.md` 面向玩家与管理员**，**不受**上面那条"面向 AI"的口径约束：它写**游戏内现象 + 可自检步骤**，不铺架构细节。
- **改它时不要把它改成 AI 向** ✗：架构与判据放 `架构总览.md` / `组件模型.md`；反过来，**不要把本目录的架构文档搬给玩家** ✗。

## 口径（改文档时同样适用）

- **做**：每条规则都配一条**现算命令**；路径一律**仓库相对**（POSIX）；与代码冲突时**以代码为准**并就地修正文档。
- **不做**：不写协作痕迹（谁做的、评审结论、任务编号）；不写易变身份量（提交哈希、制品哈希、时间戳）；**不编造符号或编号**——拿不到原文就如实标注"未入库"。

## 未入库项（如实申报，勿当作已核对）

- **`R-1…R-8` 这套判据编号在本仓库确有使用**：**稳定分母 = `docs/**` 现算 20 行**（`skills/create-role/SKILL.md` 11 行 · `交接-下一上下文.md` 5 行 · `README.md`/`组件模型.md`/`如何新增一个角色.md`/`阶段7-文档语义漂移核报告.md` 各 1 行）—— 这一半是**已跟踪件** ⇒ 读数可复核、可复跑对拍。因此编号**是**本仓库的既有词汇，只是**编号原文（`R-N` 的完整判据条文）未入库** ⇒ `组件模型.md` 的判据集**不按编号**罗列，而是逐条给出**可从 ADR / 代码现算的判据 + 反例 + 命令**；`组件模型.md` / `SKILL.md` 现用的编号是**按代码与 ADR 复原**的，与原文的逐条对应关系**未经核对**。
  - ★ **取数必须逐行读盘**：`debug-logs/` 被库内 `.gitignore:47`（`debug-logs`）+ `:85`（`debug-logs/**`）覆盖 ⇒ `git grep` **对该半边恒空**（只看已跟踪件），拿它得"0 命中"是**真空断言**、不可复核。两个半边都要用下面的命令。
  - ★ **`debug-logs/**` 是持续追加的归档区**（还有卡在往里写件）⇒ 它那半边的读数**只作"取数时刻"口径，不当稳定分母**（实测：取数时刻 **332 行 / 26 件**，同一会话稍早为 331 行 / 25 件，差额 = 新追加的 `阶段13-注释沿革-C2-追加.md`）。所以结论的分母请用 `docs/**` 的 **20 行**；`debug-logs/**` 的读数只作旁证，复跑以命令当场输出为准。
  ```powershell
  $repo = (Resolve-Path 'ShadowHunterRoles').Path
  Get-ChildItem "$repo\docs" -Recurse -File -Include *.md | ForEach-Object {
    $n = [IO.File]::ReadAllLines($_.FullName)
    for ($i = 0; $i -lt $n.Count; $i++) { if ($n[$i] -cmatch 'R-[1-8]') { "$($_.FullName.Substring($repo.Length+1)):$($i+1)" } } }
  # 期望 20 行：SKILL.md 11 · 交接-下一上下文.md 5 · README/组件模型/如何新增一个角色/阶段7漂移报告 各 1
  Get-ChildItem "$repo\debug-logs" -Recurse -File -Include *.md,*.txt | ForEach-Object {
    $n = [IO.File]::ReadAllLines($_.FullName)
    for ($i = 0; $i -lt $n.Count; $i++) { if ($n[$i] -cmatch 'R-[1-8]') { "$($_.FullName.Substring($repo.Length+1)):$($i+1)" } } }
  # 取数时刻实测 332 行 / 26 件 —— ★ 这是持续追加的归档区，**不是稳定分母**（★ 不得用 git grep 取这半边：被 .gitignore 覆盖 ⇒ 恒空）
  ```
- 若将来拿到编号原文，请**并入** `组件模型.md` 的判据表（保留现表，不要用编号替换掉可复算的判据行）。

**基线取数命令（三件 AI 向文档共用）**

```powershell
$repo = (Resolve-Path 'ShadowHunterRoles').Path
$S    = "$repo\src\main\java\com\shadowHunterRolesPlugin"

#① 顶层包数（口径：顶层目录**含直属 .java** 才算「包」）
"顶层目录 = $((Get-ChildItem $S -Directory).Count)"                                                   # 10
"顶层包   = $((Get-ChildItem $S -Directory | Where-Object { (Get-ChildItem $_.FullName -File -Filter *.java).Count -gt 0 }).Count)"   # 9（internal/ 只有 internal/api/ 子包）
"末级包     = $((Get-ChildItem $S -Recurse -Directory | Where-Object { (Get-ChildItem $_.FullName -File -Filter *.java).Count -gt 0 }).Count + 1)"            # 23（任意层级末级包，含仓库根包名）
#② 件数
"core/         = $((Get-ChildItem "$S\core" -Recurse -File -Filter *.java).Count)"                    # 13
"roleComponent = $((Get-ChildItem "$S\roleComponent" -Recurse -File -Filter *.java).Count)"           # 38
"src/main      = $((Get-ChildItem "$repo\src\main" -Recurse -File -Filter *.java).Count)"             # 89
#③ roleComponent/ 分项（= 38）：根 8 + base 3 + builtin 4 + custom 11 + frameworkLevel 12（直属 9 + hotbar 3）
"根 $((Get-ChildItem "$S\roleComponent" -File -Filter *.java).Count) + base $((Get-ChildItem "$S\roleComponent\base" -Recurse -File -Filter *.java).Count) + builtin $((Get-ChildItem "$S\roleComponent\builtin" -Recurse -File -Filter *.java).Count) + custom $((Get-ChildItem "$S\roleComponent\custom" -Recurse -File -Filter *.java).Count) + frameworkLevel $((Get-ChildItem "$S\roleComponent\frameworkLevel" -Recurse -File -Filter *.java).Count)"   # 8 + 3 + 4 + 11 + 12 = 38 ✓
#④ 测试基线（@Test 计数；件数 = 测试文件数）
$t = 0
Get-ChildItem "$repo\src\test" -Recurse -File -Filter *.java | ForEach-Object {
  $n = [IO.File]::ReadAllLines($_.FullName)
  for ($i = 0; $i -lt $n.Count; $i++) { if ($n[$i] -cmatch '@Test\b') { $t++ } } }
"@Test = $t / 测试件数 = $((Get-ChildItem "$repo\src\test" -Recurse -File -Filter *.java).Count)"    # 117 / 17
#⑤ 跑闸门后，逐份测试套件加起来（与 @Test 同数）
$dir = "$repo\build\test-results\test"; $t=0; $f=0; $e=0
Get-ChildItem $dir -Filter *.xml | ForEach-Object { $s = ([xml](Get-Content -Raw -Encoding UTF8 $_.FullName)).testsuite; $t += [int]$s.tests; $f += [int]$s.failures; $e += [int]$s.errors }
"suites = $((Get-ChildItem $dir -Filter *.xml).Count) / tests = $t / failures = $f / errors = $e"     # 17 / 117 / 0 / 0
```

（★ 逐行读盘一律用 `[IO.File]::ReadAllLines()`，**不要**用 `Get-Content` 取行/数行 —— 无 BOM 的多字节 CJK 文件上它会静默少算。★ 行数口径 = **全文行数**（含空行）。）

## 历史文档（**已删除** —— 取代指向）

旧 `docs/架构/**` 的 **8 件已全部删除**（用 `git rm` 保留历史）；其**决策结论已并入**本目录的 `组件模型.md`（第 5 节「决策史要点」）。若两者曾有冲突，**以本目录 + 代码为准**。

| 被删的旧路径 | 结论去向（新位置） |
|---|---|
| `docs/架构/架构与组件模型.md` | `组件模型.md`（三层模型 / 判据集 / 端口面现状 / 包结构 / 决策史要点） |
| `docs/架构/adr/0001-允许组件覆写识别键与状态文案.md` | `组件模型.md` §5「识别键与状态文案」 |
| `docs/架构/adr/0002-默认画法落在组件基类.md` | `组件模型.md` §5「默认画法」 |
| `docs/架构/adr/0003-能力接口的合并条件.md` | `组件模型.md` §2 判据集「能力接口的合并条件」+ §5「能力接口合并」 |
| `docs/架构/adr/0004-扩展性审查的第三个实例测试.md` | `组件模型.md` §2 判据集「第三个实例测试」+ §5「扩展性审查」 |
| `docs/架构/adr/0005-单一冷却命名空间.md` | `组件模型.md` §2 判据集「单一冷却命名空间」+ §5「冷却」 |
| `docs/架构/adr/0006-角色信息归聚合根不组件化.md` | `组件模型.md` §2 判据集「角色信息归聚合根」+ §5「角色信息」 |
| `docs/架构/adr/README.md` | 本条（无独立结论；它只是上表的索引） |

```powershell
git -C ShadowHunterRoles ls-files docs/架构        # 期望：0 件
git -C ShadowHunterRoles grep -rn '架构/adr' -- docs README.md   # 期望：仅本文件的说明行
```
