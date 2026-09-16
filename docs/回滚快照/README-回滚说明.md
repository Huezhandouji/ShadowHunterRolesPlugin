# 会话前基线快照（回滚用）— 使用说明

**生成时间**：2026-09-15 21:56–21:58（+08:00），由 verifier 在 t2/t6 开工**之前**采集。
**采集原因**：工作树里存在仓库作者更早的、未提交的改动（.java 文件 mtime 为 2026-09-12/09-13，本会话零 Java 改动）。`git stash list` 为空，**不存在任何会话开始快照**。一旦 t2/t6 修改这些文件，作者旧改动与本次改动的分界线将永久丢失，`git checkout <文件>` 只会把两者一起回滚，属静默数据损失。故先冻结本基线。

---

## 1. 快照内容

| 文件 | 说明 |
| --- | --- |
| `before/src/**` | 工作树中 `ShadowHunterRoles/src/` 的**逐字节副本**（48 个文件 = 47 个 .java + `main/resources/plugin.yml`），生成时已用 SHA256 与活动工作树比对，0 处不一致 |
| `session-start-baseline.patch` | `git diff HEAD --binary`（即 HEAD `dccd70c` → 当前工作树）的可反向应用补丁，50091 字节。它完整覆盖 16 个被作者修改/新增的已跟踪文件（+848 / −25 行） |
| `integrity-sha256.txt` | 48 个快照文件的 SHA256 + 原相对路径 + 与活动工作树的比对结果（全部 `OK`），外加补丁自身的 SHA256 |
| `README-回滚说明.md` | 本文件 |

**基线锚点**：HEAD = `dccd70c1a3227e56ce1447442551c3422f13a922`（2026-09-08 22:24:32 +0800，"添加了一堆新东西"）；`git stash list` 为空。
**未纳入基线**：`.gradle-work/`（构建缓存）与 `docs/`（本次工作文档）是未跟踪目录，不属于源代码基线。

采集时的 `git status --porcelain`（相对于 HEAD，**全部为作者旧改动，非本次会话产生**）：

```
 M src/main/java/com/shadowHunterRolesPlugin/api/RoleAPI.java
 M src/main/java/com/shadowHunterRolesPlugin/api/RoleAPIImpl.java
 M src/main/java/com/shadowHunterRolesPlugin/core/DamageUtil.java
 M src/main/java/com/shadowHunterRolesPlugin/core/Role.java
 M src/main/java/com/shadowHunterRolesPlugin/core/RoleInstance.java
 M src/main/java/com/shadowHunterRolesPlugin/registry/RoleRegistry.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/AutoRecoverEnergyPassive.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/AutoRecoverSanTEHealthPassive.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/SkillUtil.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/meiqiHezi/passive/MeiqiheziEquipmentsPassive.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedBleedPassive.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedDeeplySorrowSkill.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedEquipmentsPassive.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedEvilShockSkill.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedSanctifiedBladeMainWeapon.java
AM src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedSolitaryArroganceSkill.java
?? .gradle-work/
?? docs/
```

---

## 2. 警告：哪些文件**不能**用 `git checkout` 整体还原

作者旧改动与本次范围**同文件**的三个文件，最危险：

- `src/main/java/com/shadowHunterRolesPlugin/core/RoleInstance.java`（A 流 t2 必改）
- `src/main/java/com/shadowHunterRolesPlugin/api/RoleAPIImpl.java`（B 流 t6 必改）
- `src/main/java/com/shadowHunterRolesPlugin/registry/RoleRegistry.java`（B 流 t6 必改）

上表 16 个文件全部含作者未提交改动，**对其中任何一个执行 `git checkout -- <文件>` / `git restore <文件>`，都会把作者 09-12/09-13 的工作一并抹掉**。本次会话的任何回滚都必须走下面的"按文件还原"，不得使用 `git checkout`/`git restore`/`git stash`。

---

## 3. 还原步骤（按文件、按改动点）

前置：先记录当前 `git status --porcelain`，得到本次会话**新增**的文件清单（新增文件不在基线中，回滚时需删除）。

### 方式 A（推荐，逐字节精确）：从 `before/` 拷回

```powershell
# 单文件还原示例（把 <相对路径> 换成 src/main/java/... 的 POSIX 路径）
$rel = 'src/main/java/com/shadowHunterRolesPlugin/api/RoleAPIImpl.java'
Copy-Item "ShadowHunterRoles/docs/回滚快照/before/$rel" "ShadowHunterRoles/$rel" -Force
```

只需还原**本次实际改动过的文件**，不要整目录覆盖（`before/` 与活动树在未改动文件上字节相同，整目录覆盖虽等价但会连带重置未改动文件的 mtime）。

### 方式 B：反向应用补丁（回到会话前 → 再单独挑文件）

`session-start-baseline.patch` = HEAD → 会话前基线。它**不能**直接对"已被 t2/t6 改过的工作树"反向应用（补丁的 pre-image 是 HEAD，不是当前工作树）。正确用法是把补丁应用到 HEAD 干净检出上，得到会话前基线，再按需取用单文件：

```powershell
git -C ShadowHunterRoles stash list          # 必须为空，确认无他人快照
# 在一个临时目录里重建 HEAD 工作树，然后应用补丁
git -C ShadowHunterRoles archive HEAD | tar -xf - -C <临时目录>   # 需在临时目录外执行并确认 tar 存在
git -C <临时目录> init -q && git -C <临时目录> apply "C:/Users/ROG/Desktop/插件/ShadowHunterRoles/docs/回滚快照/session-start-baseline.patch"
```

### 回滚后自检（必须做）

```powershell
# 与 integrity-sha256.txt 中记录的哈希逐条比对，期望全部一致
Get-FileHash "ShadowHunterRoles/src/main/java/com/shadowHunterRolesPlugin/api/RoleAPIImpl.java" -Algorithm SHA256
```

未改动但仍需留意的文件：`integrity-sha256.txt` 覆盖全部 48 个源文件，可用于确认"回滚后源码 == 会话前基线"。

---

## 4. 本快照的效力边界（不夸大）

- 已验证：快照与活动工作树逐字节一致（0 处 SHA256 不一致）；补丁可对活动工作树 `git apply --reverse --check` 通过（exit 0），说明补丁结构有效且 post-image 就是采集时的工作树。
- **未**验证：完整"重建 HEAD 工作树 → 应用补丁 → 与 `before/` 逐文件比对"的端到端演练（受沙箱与 autocrlf 影响未执行）。该演练应在 t5 集成验收时作为"回滚方案自身可执行"的验收动作补做，并以 `integrity-sha256.txt` 为判据。
- 本快照只覆盖 `ShadowHunterRoles/src/**`；`build.gradle.kts`、`.gradle/`、`docs/` 不在其中（本次范围不改它们）。
