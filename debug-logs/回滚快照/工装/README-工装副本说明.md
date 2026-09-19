# 工装副本区（仪器可复原，§6.6:798-799）

> **性质**：**测试工装，非产品代码**，不参与插件构建与交付逻辑；放在仓库内只为让"被引用的仪器版本随交付一起走、可复原、可引用"。
> **依据**：`docs\最终重构指南.md` §6.6:798-799 ——「被引用的仪器版本必须可复原：`.scratch\bot\` 不在版本控制下，被覆盖即永久丢失……凡要作为证据引用的仪器版本，引用前先复制出带哈希的副本（放入仓库内或其它受管位置）」。起因：被公告为"最终冻结版"的 `15 621 B / 29B421B9…` 已不可复原，导致"只对该版复检一次"的指令无法执行。
> **与工装工作区的关系**：可执行工作副本仍在工作区根的 `.scratch\bot\`（§6.6:1042）；**本目录是"被引用版本"的内容寻址副本**，两份都必须存在（"身份可核"≠"产物可复原"，两者都要）。
> **命名**：`<名字>-<sha8>-<字节数>.<ext>`；`MANIFEST.txt` 每行 = `sha256 <TAB> bytes <TAB> mtime <TAB> 源文件名 <TAB> 存档名`。
> **引用纪律**：报告引用某脚本时给 **内容片段 + 字节数/mtime/SHA256**，且该 sha256 必须能在本目录找到同名副本（否则先补副本再引用）。

## 当前副本

| 源文件 | 字节数 | SHA256 | 本目录存档名 |
|---|---|---|---|
| `t6-quit-persistence.js` | 20 981 | `5E7CABEDFC338890372203DCD0726FF37492B098154C8F3DB92DC3A7FDCF867D` | `t6-quit-persistence-5E7CABED-20981.js` |
| `collect-runserver-identity.ps1` | 6 633 | `26E5A75FCA6DF998418F1D5C8DB11876872C19F21326E32C0D06AE0F5FB11DFF` | `collect-runserver-identity-26E5A75F-6633.ps1` |
| `numeric-multiset.ps1` | 3 997 | `06462664FE45F33E00DE63CE51C8D7DE86AE3171612D7F5F2EADA60CAA281E7C` | `numeric-multiset-06462664-3997.ps1` |
| `log-delta.ps1` | 4 588 | `4A5A88B0879DE9DFB21551674E8D4ED76B4BBA8DD83A966829FB8C115332082C` | `log-delta-4A5A88B0-4588.ps1` |
| `snapshot-src.ps1` | 2 438 | `21A19061D1EB48D7D5D405BB6C47B38828CF7F3980AFA14BA63974D7633164D3` | `snapshot-src-21A19061-2438.ps1` |
| `check-instruments.ps1` | 3 881 | `E7EC4636D11B0FAABAD57B910B25A3D72A28BE3E9FE6C30587AC4404A47C7639` | `check-instruments-E7EC4636-3881.ps1` |
| `freeze-instruments.ps1` | 2 737 | `2ABBD4BDF67E44CC7BDC8419D3CD1AAEB154083E804F5190E2A966743CCA4FCC` | `freeze-instruments-2ABBD4BD-2737.ps1` |

- `.ps1` 全部 **ASCII-only**（`check-instruments.ps1` 的准入自检 `failures = 0`）；探针 `--selftest` **exit 0 / 12 项全 true**。
- **t6 实测跑完后**：若探针脚本再有任何改动，**必须**把"产出 `result.json` 的那一版"补入本目录，并在交付小结里同时给出 ① `result.json` 内的 `meta.scriptIdentity` ② 本目录副本的现算 SHA256（两者一致才可采用）。
