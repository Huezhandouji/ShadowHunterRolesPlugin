# t60 执行清单（收到队长【编辑停止】直投后按序执行）

> 卡面：清理批（拆分版）—— 删 `RedDeeplySorrowSkill.java:3` 的死 import，**只动 1 个 java 文件**
> 本清单**只预排**：不碰 src、不取基线、不起服（队长 21:39 授权范围）。
> 工装落在 `.scratch/bot/`（git 忽略区，**不进 repo**）⇒ `changedPaths` 零污染。

---

## 0. 闸门（未满足 ⇒ 零写盘）

- 触发条件 = 队长**直投** `to=eng-实现B` 的【编辑停止】（= `t61` 交付并被队长提交之后）。
- **不得**用 `t59cmd-before` 作基线（已失效：`RoleCommand.java` 688 → 84 行）。
- 收到信号后**第一动作是取基线**，不是编辑（顺序不可颠倒）。

## 1. 当刻取基线（在写 repo 之前）

```powershell
# 1.1 快照 + 逐文件 SHA256 自证  -> .scratch/bot/src-snapshots/t60-before/
powershell -NoProfile -ExecutionPolicy Bypass -File .scratch\bot\t59-baseline-and-scoped-criteria.ps1 -Name t60-before
# 1.2 判据 + 减少组 sidecar     -> .scratch/bot/t60-criteria-before.txt
#                                 .scratch/bot/t60-roleinstance-plain-before.txt
powershell -NoProfile -ExecutionPolicy Bypass -File .scratch\bot\t60-criteria.ps1 -Phase before -Stamp before
```

断言（任一不满足 ⇒ **停手回报**，不得"带病开工"）：

- [ ] 目标文件 = **3941 B / 81 行 / `76EDDD9EA8A2842E4F3B557D85B200185F882C24024BC958CF657F497B990CA3`**（变了 ⇒ 有人动过它 ⇒ 重取或回报）
- [ ] 归零组 before：目标文件 `\bRoleInstance\b` **CODE = 1**（`:3`）、COMMENT = 1（`:66`）
- [ ] 归零组 before：`git ls-files --others --exclude-standard` = **0 行**、`??` 计数 = **0**
- [ ] 减少组 before：双口径计数（自测读数 139 / 139；**执行时现算**）已写入 sidecar
- [ ] 窗口 free（三路，见 §4）＋ **无人在写**：`porcelain` 里除我之外**不得有在途 src 改动**（有 ⇒ 停手回报，闸门其实没关）

## 2. 编辑（唯一改动面，1 行）

- [ ] 删 `roleComponent/red/RedDeeplySorrowSkill.java:3` 的 `import com.shadowHunterRolesPlugin.core.RoleInstance;`
- [ ] `:66` 的注释行**保留**（它是注释位，不属于"代码位归零"的对象）
- [ ] 复算归零组：CODE = **0** ✓、COMMENT = 1、untracked = **0** ✓、`??` = **0** ✓
      （我改的是 **tracked** 文件 ⇒ 表现为 ` M <该 java>`；**M ≠ ??** ⇒ `??` 仍须为 0）
- [ ] 减少组：`-DeltaFrom` 复算

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .scratch\bot\t60-criteria.ps1 -Phase after -Stamp after `
    -DeltaFrom .scratch\bot\t60-roleinstance-plain-before.txt
```
  期望：`DELTA = -1`、`REMOVED = 1`（**就是被删的 `:3` import 行**）、`ADDED = 0`
  ⇒ **这一条就是 (d) 的全部内容**；**不得**把减少组写成"0 行"（那是"什么都没做"的读法）

## 3. 构建（本卡真删代码 ⇒ 必改 class ⇒ jar 换代）

```powershell
cd ShadowHunterRoles; $env:GRADLE_USER_HOME="$PWD\.gradle-work"; .\gradlew build -x copyPluginJar
```

- [ ] exit = 0 / `BUILD SUCCESSFUL`
- [ ] 最新 `.class` mtime **<** jar mtime；`src 新于 jar = 0`
- [ ] 记录 jar 新身份（bytes / mtime / **SHA256**）——本代身份进证据链

## 4. 起服前广播 + 窗口三路互证（规范 §1.1/§1.2）

- [ ] 广播（`to=eng-验证`、`to=eng-平台`、`to=captain`）：预计占用时长、证据**新文件名** `阶段5-起服日志-清理批.log`、"你若正在取证请回我一句，我等你"
- [ ] ① `TcpClient.Connect('127.0.0.1',25566)` **失败**
- [ ] ② `netstat -ano | Select-String ":25566"` **无 LISTENING**（仅 `TIME_WAIT` 残影可接受）
- [ ] ③ `Get-Process java` 只剩 19:01 的 daemon（看不出"最近起过服"）
- [ ] **禁用** `Get-NetTCPConnection -State Listen` 作唯一判据（族 1：它在真监听时也可能给 0）
- [ ] 不得把"端口空闲"当放行 ⇒ 等停机方"**已释放（物证已落）**"（§1.3）

## 5. 起服 Mode B

- [ ] `.\gradlew runServer`（后台 job）
- [ ] Mode B 三行：`Enabling` / `enabled` / `Done (…)`
- [ ] 正序集合差：`log-delta.ps1 -Anchor <阶段4-起服日志-T2b.log 权威路径> -Current run\logs\latest.log -Tag t60` ⇒ **只看 C = 0**（A/B 亦应 0）
- [ ] ERROR/SEVERE 扫描：**锚定级别字段** `\] \[[^]]*/(ERROR|SEVERE)\]`；**不得用裸 `ERROR`**（oshi WMI 的 WARN 正文含 "Error was -2147217405" ⇒ 3 条假阳性，实测过）

## 6. 停服前先冻结（§2.1 —— 顺序不可颠倒）

- [ ] **在服务器仍在线时**：`Copy-Item run\logs\latest.log debug-logs\测试记录\阶段5-起服日志-清理批.log`
- [ ] **当场**算身份：bytes / mtime / **SHA256**，写进物证
- [ ] 写一句**可核事实**："在服务器仍在线时冻结，随后才 `job_kill`"
- [ ] 补冻结（尽力而为）：停机后再试一次；若被后继起服覆盖 ⇒ 如实标"补冻结失败"，**绝不**覆盖主冻结

## 7. 停机（§2.2/§2.3）

- [ ] **只** `job_kill`（不对 java 进程动手）
- [ ] 三路互证：TCP connect **失败** + `latest.log` **独占打开成功** + `netstat` 中 `25566` **无任何条目**（连 TIME_WAIT 都清空）+ java 只剩 daemon
      ⚠ 独占打开是"互斥探针"⇒ **与他人的只读探测串行做**；结论以**最后一次完整三路**为准
- [ ] 广播"**已释放（物证已落）**"（§2.4 —— 这一句才是别人的放行信号）

## 8. 制品归档 + MANIFEST（一行一值，族 6）

- [ ] 归档：`copy build\libs\ShadowHunterRolesPlugin-1.0.0.jar -> debug-logs\回滚快照\jars\ShadowHunterRolesPlugin-1.0.0-verified-<yyyyMMdd-HHmmss>-<SHA8>.jar`
- [ ] 断言：归档件与 `build/libs` **逐字节相同**（SHA256 相等）
- [ ] `debug-logs/回滚快照/jars/MANIFEST.txt` **追加一行**：`<jar 文件名> = <bytes> B / <mtime> / <SHA256>`
- [ ] **F1 链**自证：`jar mtime < log mtime < 物证 mtime`
- ⚠ 卡面 inScope **未列**"jar 归档"与"MANIFEST"两条路径 ⇒ 按 `t39`/`t50`/`t59` 先例：**照做 + 在 output 逐条申报**；`changedPaths` 只列 inScope 内的（校验器强制 ⊆ inScope）
- ⚠ **绝不为"重建后逐字节相同"去删 jar + 重建**（已冻结代次会断 F1 链）⇒ 要验就在 `build/` 副本上做（§3.4）

## 9. 三份证据文件（卡面 inScope）

- [ ] `debug-logs/测试记录/阶段5-清理批-说明.txt`
      判据表（**每个数字带自己的测量时刻**，族 8 ③）＋ 双口径含 (d) 逐条 ＋ 逐文件 SHA256 ＋ 红线核对 ＋ 命令与原始输出
- [ ] `debug-logs/测试记录/阶段5-起服日志-清理批.log`
      冻结件（**落盘后不得再改**）＋ 文首/物证里写身份
- [ ] `debug-logs/测试记录/阶段5-清理批-停服物证.txt`
      三路互证读数 ＋ F1 链 ＋ **制品自绑定**（本代 jar SHA + bytes + mtime）＋ **源码状态绑定**（commit 号 ＋ 工作树状态清单）

## 10. 双口径（含 (d)）

```powershell
# 口径 A：path-aware（路径 + 字面量）
powershell -NoProfile -ExecutionPolicy Bypass -File .scratch\bot\numeric-multiset.ps1 -BaseDir .scratch\bot\src-snapshots\t60-before -Mode both -Tag t60-before
# 口径 B：value-only（只比字面量值多重集）
powershell -NoProfile -ExecutionPolicy Bypass -File .scratch\bot\t59-literal-net.ps1 -Before .scratch\bot\src-snapshots\t60-before -After ShadowHunterRoles\src -Tag t60
```

- [ ] 期望：本卡只删 **1 行 import**（非字面量）⇒ 两条口径 **差异行 = 0**；若不为 0 ⇒ 逐条申报（不得含糊）
- [ ] 减少组（`\bRoleInstance\b` 双口径）：`DELTA = -1`，逐条 = 被删的 `:3` import ⇒ **(d) 的内容就是这一条**
- [ ] 双口径**两条都**要打印（口径 A 的"减少"与口径 B 的"净变化"不得混写）

## 11. 交付

- [ ] `changedPaths` ⊆ inScope（1 个 java + 3 个证据文件）
- [ ] 超范围但被判据要求的 2 条路径（jar 归档、MANIFEST）在 output 逐条申报
- [ ] `acceptanceResults` 8 条按卡面**原序**；`commandsRun` 3 条按卡面**原序**
- [ ] 红线核对：`api/**` porcelain = 0 · `ComponentServices` 成员 = 10 · `callEvent` = 2 · `plugin.yml`/主类 porcelain = 0 · 冷却/调度语义一字不改 · **不做 git 写**

---

## 陷阱速查（本卡专属）

| 族 | 陷阱 | 纪律 |
|---|---|---|
| 族 2 | `Measure-Object -Line` 给 **60**，真值 **81** | 行数用 `ReadAllLines` / 换行符计数 |
| 族 5 | 工具缺失 ⇒ 两侧空串 ⇒ "逐字相同" **假 True** | 先断言工具可用 + 输出非空，并打印两侧行数 |
| 族 7 | 对**原生进程**管道用 `Select-Object -First N` ⇒ 伪非零退出/伪 stderr | 先落变量再筛，或用 `-Last N` |
| 族 8 | 普通 `git grep` **不搜未跟踪** ⇒ 假归零 / 假进展 | 一律 `--untracked`；归零判据与未跟踪计数**联合读**；清理类另要求 `??` 归零 |
| 族 9 | .NET 文件 API 的相对路径按**进程 CWD** 解析 | 一律绝对路径 / `-LiteralPath` |
| 族 10 | 断言串自身写错；CJK 在命令行走廊被转码 | ASCII 锚或**码点构造**；先在"已知为真"的样本上验证断言会通过 |
| — | 裸词 `sched` 命中 `scheduler`/`scheduler()` 生产 API（实测**假 22 条**） | 用锚串 `matrixAllPairsMatch=\|portLegEquivalent=\|\[sched\] [①-⑤]` |
| — | 白名单当 **pathspec** 传（git pathspec 不支持 `()` 分组）⇒ 无意义值 | 白名单过滤作用在**【行】**上 |
| §3.2 | jar SHA **不是**"同一份代码"的稳定指纹 | 判同码用反汇编/源码非注释；判身份才用 jar SHA + mtime |
| §7.1 | 引"条数/行号/族数"是移动靶 | 引 **§章节 + 条目标题**，或"§5 @时刻" |

---

## 已就绪工装（本轮 self-test 通过，**未动 src、未取基线、未起服**）

| 工装 | 用途 | 自证 |
|---|---|---|
| `.scratch/bot/t60-criteria.ps1` | 归零组 / 减少组 / 旁证 / 红线 一次算全 | 已在**已知为真**与**合成差异**两种样本上双向验证：identical ⇒ `0/0`；少 1 行 ⇒ 精确点名该行 |
| `.scratch/bot/t59-baseline-and-scoped-criteria.ps1 -Name t60-before` | 快照 + 逐文件 SHA256 自证 | 团队既有（t59 用过） |
| `.scratch/bot/numeric-multiset.ps1` | 口径 A（path-aware） | 团队既有 |
| `.scratch/bot/t59-literal-net.ps1` | 口径 B（value-only） | 团队既有 |
| `.scratch/bot/log-delta.ps1` | 起服日志正序集合差（锚定） | 团队既有 |

**self-test 附带的时点事实**（@21:40:49）：`porcelain` = 5 M（`DebugCommand` 16+/0 · `DebugCooldownCommand` 22+/13 · `DebugSchedCommand` 38+/24 ＋ 2 份文档）⇒ **`t61` 的 console 那半正在写**，闸门确实未开。
