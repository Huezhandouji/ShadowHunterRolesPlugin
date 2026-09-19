# debug-logs/ 根目录整理 · 旧路径 → 新路径 映射表

> **用途**：2026-09-19 把 `debug-logs/` 根目录的 **36 个散件**按类移入 3 个子目录（`调试日志/` 17 件 · `交付小结/` 9 件 · `设计文档/` 10 件）。
> **为什么非要这张表**：既有报告与冻结证据件里大量引用这些文件的**旧路径**，而它们是**「当时路径」、按纪律不予改写** ⇒ 旧路径引用**一律由本表解析**到新路径。
> **只移动、不改内容**：每件的 `bytes` + `SHA256` 移动前后**逐字相同**（36/36；判据、命令与原始输出见 `debug-logs/测试记录/阶段5-文档目录整理-说明.txt`）。表中身份 = **移动后现算**值（与移动前基线同值）；引用前请**重算**。
> **列序**：`旧路径 ｜ 新路径 ｜ bytes ｜ SHA256`（三节分别为 `调试日志` / `交付小结` / `设计文档`；每节行数 = 该目录件数）。
> **留在根目录不动的 2 件**：`README.md`（`.gitignore:85` 的 `!` 例外 —— 移走会被忽略规则吞掉）· `README-文档索引.md`（文档地图，仍是入口）。

### 调试日志（17 件）
### 调试日志（17 件）
| `debug-logs/baseline-runserver-2026-09-13-1519.log` | `debug-logs/调试日志/baseline-runserver-2026-09-13-1519.log` | 13325 | `FC5903DCE08FAC3BE27DDC2ED77275F9B383138726850A1D3CDC0E16C6CA72EA` |
| `debug-logs/baseline-runserver-B0a-2026-09-17-0040.log` | `debug-logs/调试日志/baseline-runserver-B0a-2026-09-17-0040.log` | 6138 | `CDF90A2C850B02501907964C2908051B120F7B2EB471FE47B54B841D29E32DAB` |
| `debug-logs/baseline-runserver-B0b1-2026-09-17-0046.log` | `debug-logs/调试日志/baseline-runserver-B0b1-2026-09-17-0046.log` | 6140 | `97A40B330780F70E8F46EB01AC87144BB8A383AA53526A57D020C436D1FD182D` |
| `debug-logs/baseline-runserver-B0b2-2026-09-17-0055.log` | `debug-logs/调试日志/baseline-runserver-B0b2-2026-09-17-0055.log` | 6077 | `67F35AF44C01C9605A884734AB4C4D945EA748BF3952E7C4C7CFB282B4B313D3` |
| `debug-logs/baseline-runserver-B0b3-2026-09-17-1732.log` | `debug-logs/调试日志/baseline-runserver-B0b3-2026-09-17-1732.log` | 6077 | `2A006417C806C9EFECEA03953606B4D724678B019F782E9E2A80B7889B4AB24F` |
| `debug-logs/baseline-runserver-t14-2026-09-17-0027.log` | `debug-logs/调试日志/baseline-runserver-t14-2026-09-17-0027.log` | 6077 | `E4CC1C1E0C97965632297EE3426D9F358CEC407293AC639145ED42365549F524` |
| `debug-logs/baseline-runserver-t2-2026-09-16-2201.log` | `debug-logs/调试日志/baseline-runserver-t2-2026-09-16-2201.log` | 6199 | `2A4BD077852D213901506205E15AC64C2F5D6A27C965D59F922D31FEE5ADB524` |
| `debug-logs/baseline-runserver-t5-2026-09-16-2222.log` | `debug-logs/调试日志/baseline-runserver-t5-2026-09-16-2222.log` | 6076 | `A3EB7CB65081824039D0EB592CF98A4B6F22E648B946491D5C74B65E484CF63C` |
| `debug-logs/baseline-runserver-t9-2026-09-16-2359.log` | `debug-logs/调试日志/baseline-runserver-t9-2026-09-16-2359.log` | 6137 | `B3667A1C4BFE98764A8A33440526E2300ECC32BEC428C56AD2E12018F537ED7E` |
| `debug-logs/t13-build.log` | `debug-logs/调试日志/t13-build.log` | 1585 | `DA09DCA52961DD263034C5410B18ACA43ECD1DBBBD249B006663EF8AEECA0391` |
| `debug-logs/t13-runserver2.log` | `debug-logs/调试日志/t13-runserver2.log` | 6615 | `3E53B0D85623E08745E499238A6F4D0B15390AB78ED6E3A48C0752499B42A1E0` |
| `debug-logs/t13-runserver-gradle.log` | `debug-logs/调试日志/t13-runserver-gradle.log` | 6613 | `170C58AF1F377B370DF51036AE9AD74DAD98FE422EE12C5935AB87C33C72CB5D` |
| `debug-logs/t6-build.log` | `debug-logs/调试日志/t6-build.log` | 1585 | `AC6B2B21897E84A4DF80A9B67FA15CC4DA1CE4FBA529251BE90869D76A1D31A8` |
| `debug-logs/t6-runserver-gradle.log` | `debug-logs/调试日志/t6-runserver-gradle.log` | 9602 | `DEEE34DB2F73F603CCBEFF54668B34836DF61646B0BBAFA10EC327A448C8D4CE` |
| `debug-logs/t6-runserver-postchange-20260916-225507.log` | `debug-logs/调试日志/t6-runserver-postchange-20260916-225507.log` | 9786 | `0D0C5F3B3FE39B8798697CF50710E08E8DA297DD96BE246518B51D778CA976A2` |
| `debug-logs/t7-runserver-20260916-231457.log` | `debug-logs/调试日志/t7-runserver-20260916-231457.log` | 7467 | `88A7B7D5707CCB34FD85003CB5B48CB3E9292044AF89B3624860CE2CB7D0B515` |
| `debug-logs/阶段3-起服日志-t13ModeB.log` | `debug-logs/调试日志/阶段3-起服日志-t13ModeB.log` | 6079 | `9FB3FE40139A98C3A81676865051F01929310BEFACDB457DAE50D0AA1CB912DC` |

### 交付小结（9 件）
| `debug-logs/阶段0-交付小结.md` | `debug-logs/交付小结/阶段0-交付小结.md` | 33255 | `E448E4C5470688BE74860C3CD96AF4B5DE511EFF86E9D4F7427F5575DA73CC76` |
| `debug-logs/阶段0-前置-基线冻结记录.md` | `debug-logs/交付小结/阶段0-前置-基线冻结记录.md` | 17491 | `4750422EBBFECDB66A6F43A2860C3C56CE14EC4C78DB568A117817FBCE7A27CD` |
| `debug-logs/阶段1-交付小结.md` | `debug-logs/交付小结/阶段1-交付小结.md` | 38111 | `36A2246A517C4066BE244FEAEEE9EA263A64BFC9D2F2430A4570EAAFCE1539C7` |
| `debug-logs/阶段1-收口记录.md` | `debug-logs/交付小结/阶段1-收口记录.md` | 18029 | `F216D5A9D0E02492BBD3ED0B465843E7F7E808AB71CE681AB9EA591A5ECE6DE4` |
| `debug-logs/阶段1.8-交付小结.md` | `debug-logs/交付小结/阶段1.8-交付小结.md` | 20678 | `6B5C4CA85229609BFE296B714C186DC89452975EA355A75B71E22CD7DF416F04` |
| `debug-logs/阶段2-交付小结.md` | `debug-logs/交付小结/阶段2-交付小结.md` | 32752 | `A9A95930F4FE72B141341B4736946EEE190DCD0964DC461112E27875115EBFB2` |
| `debug-logs/阶段3-交付小结.md` | `debug-logs/交付小结/阶段3-交付小结.md` | 20827 | `781EABCE669C511C2DD34C23FDF9ED9F13A54F1857FC17CCBE03A615A3818585` |
| `debug-logs/阶段4-交付小结.md` | `debug-logs/交付小结/阶段4-交付小结.md` | 98197 | `4A6B67DF566CDEA5E02B44CC27B33F7EAD275DD63B72B2CFCAC5154B08EB7336` |
| `debug-logs/阶段5-交付小结.md` | `debug-logs/交付小结/阶段5-交付小结.md` | 17710 | `7AA96164079A2D9A0F78EDA679125970A02604AD7E27CACC7F971D22A06D8D18` |

### 设计文档（10 件）
| `debug-logs/最终重构指南.md` | `debug-logs/设计文档/最终重构指南.md` | 180261 | `3C98651AC4B7362A3AB25241904153BE90DE193F3C210294936B495D6F752F8F` |
| `debug-logs/组件系统设计-Unity风格.md` | `debug-logs/设计文档/组件系统设计-Unity风格.md` | 68925 | `43E676060DBE8895160525BA73940F517712CC67273861A7ECFF6CA1259EDEE1` |
| `debug-logs/架构重构意见.md` | `debug-logs/设计文档/架构重构意见.md` | 59276 | `F8553FC9C658FB46F92BA18C62C5A794FC7E114A12D6FEB21A90FA03A72ACD11` |
| `debug-logs/重构前准备-需求与验收.md` | `debug-logs/设计文档/重构前准备-需求与验收.md` | 18907 | `0C9FF15F6FA4654050F35C0500DD13AC062C7B32C88DE3C4DD0D49BFD75253A3` |
| `debug-logs/重构前准备-报告-A流.md` | `debug-logs/设计文档/重构前准备-报告-A流.md` | 22685 | `508EDABFD59B0A777CEB43FA77F13B15957BEC1A289AAA13CFA449D447773ED0` |
| `debug-logs/调度器迁移方案.md` | `debug-logs/设计文档/调度器迁移方案.md` | 14747 | `BECEFE51B6EF5D3AD90F98212816697147DC9BB8BEBF428F60741821C0C26AFE` |
| `debug-logs/冷却自管理架构规划.md` | `debug-logs/设计文档/冷却自管理架构规划.md` | 12465 | `6C943AFBDC79A08B0A1EA7FBFFBF1B51A2D0D27C05DC1A26B62BC5FB0583DC4A` |
| `debug-logs/阶段4-4.4渲染器接管预案.md` | `debug-logs/设计文档/阶段4-4.4渲染器接管预案.md` | 22332 | `6360BC341EF12EBFEA2C0B55C5497B0C503DE632D5CD1FABC10C64295E2295EA` |
| `debug-logs/阶段4-T块⑦前置清单.md` | `debug-logs/设计文档/阶段4-T块⑦前置清单.md` | 5713 | `4AC4B23BE47E28BBE6C41DBF3D44D025836B9FF44D2C0E4F953F379D0BF35D7B` |
| `debug-logs/阶段4-迁移清单.md` | `debug-logs/设计文档/阶段4-迁移清单.md` | 131326 | `46894E46136A7103AA9FC5368485D53D65122E839BA1A06BD39A07F4B87E058C` |

## 口径与未覆盖
- **本表只覆盖 `debug-logs/` 根目录的这 36 件**；`debug-logs/测试记录/**`（证据与报告）与 `debug-logs/回滚快照/**`（只读归档）**未移动** ⇒ **不在本表内**。
- **旧路径在冻结件内仍然存在**：既有报告 / 冻结证据件里的旧路径属『**当时路径**』，按纪律**不改写** ⇒ 见到旧路径请**查本表**解析，**不得**据此判定"文件丢了"。
- **引用列的判定口径**：本卡对 `debug-logs/**` + `docs/**` + 仓库根 `*.md` 做**一次性字面横扫**（扫描发生在本表落盘**之前**，故本表自身不在扫描集内）⇒ 只改写了 `README-文档索引.md` 与 `README.md` 两个**活文档**里的路径引用。