// ============================================================================
// t6 / 阶段 1.8 掉线路径实测工装（测试用途，非产品代码；位置 .scratch\bot\）
//
// 目的：在沙箱内取得「掉线即销毁角色实例」的可运行证据（I-16）。
//
// ⚠️ 两条**命令面事实**决定了断言怎么写（eng-验证 t7 预检 + 我的自查，勿回退）：
//   F1  `RoleCommand.java:106-110` `handleGetEnergy` **先按在线玩家解析 target**
//       （`Bukkit.getPlayer(name)`，离线即回 "Cannot find the player you provided" 并 **return**）
//       → **掉线后按名字查询拿不到任何关于 playerRoleMap 的证据**：
//         该回执与"条目是否被移除"无关，**修好也是同一句**。
//         本插件的命令面没有"按 UUID 查离线玩家"的路径 → 这一观测**不存在**，不伪装成证据。
//   F2  自己查自己走 `handleGetEnergy(player, null)`（:105 `target=sender`）：
//         有角色 → :115 "The current energy level of [<名>] is: …"
//         无角色 → :112 "<名> has no role!"      ← **不是** "You have no role yet!"
//         （"You have no role yet!" 属 `handleClear` :189/:192，**不在** energy 查询路径里）
//
// 断言口径（三态：true=测得为真 / false=测得为否 / null=未测到（仪器盲区，不得计为通过））：
//   A  在线且有角色      ：本人 `/role energy get` → 期望 "current energy level of"
//                          反证分支：若回 "has no role" → 说明角色根本没选上（false）
//   B  重登后无角色      ：重登后本人 `/role energy get` → 期望 "has no role"（RoleCommand.java:112）
//      ← **O-25 泄漏是否被修掉的核心证据**。推理必须显式写出（否则等同"代码形态倒推"）：
//         离线模式（`run\server.properties: online-mode=false`）下重登复用**同一 UUID**；
//         `RoleManager.java:80-81` 的 `hasRole(player)` 按 `player.getUniqueId()` 查 `playerRoleMap`；
//         若登出时条目泄漏，重登后 `hasRole` 仍为 true → :115 会回 "current energy level of"。
//         故：回 "has no role" ⇒ 条目已不存在 ⇒ 泄漏已修；回 "current energy level of" ⇒ 泄漏仍在。
//   C  quit 清除的持久化 ：重登后客户端 `max_health` 上 `shadowhunterrolesplugin:role_health_modifier`
//                          是否消失、value 是否 == 20（原"未知项"）
//   D  热键栏无残留      ：重登后 hotbar slot 0..8 全为 null
//   E  无残留药水        ：重登后 `bot.entity.effects` 为空（阶段 1 验收的另一半，**不得**用 C 的通过覆盖它）
//      证明力：只有"掉线前确实存在药水"时 E=true 才是强证据；脚本会先尽力制造一个效果（步骤 1b），
//      并把 `E_meaningfulness.primedBeforeQuit` 写进证据 —— 为 false 时按 vacuous 标注，不冒充强证据。
//   未覆盖：ticker 与 BuffManager updater 两个 1-tick 任务是否停止 —— 客户端无观测面。
//           eng-验证 提示的服务端路径（未取消的 1-tick 任务强引用 RoleInstance，
//           故 `jcmd <pid> GC.class_histogram` 可差分观测实例数）归 t7；本脚本不掺入。
//
// 用法：`node t6-quit-persistence.js`（要求 runServer 已把 25566 起好；脚本自己不启动服务器）
// 自检：`node t6-quit-persistence.js --selftest`（不连服务器，验证 F-1 三态可表达性 + F-2 证据强度与退出码语义）
// 输出：控制台 + `t6-quit-persistence.result.json`
// 退出码（队长 F-2 裁定）：0 = 全 true 且无 vacuous；4 = 全 true 但存在 vacuous（证据弱于宣称）；
//                          2 = 有 false（按缺陷处理）；3 = 有 null（未测到）；1 = 脚本异常。
//                          E 的定档：测得值（true/false/null）+ 强度（strong/vacuous）+ 依据（primedBeforeQuit）。
// ============================================================================

const mineflayer = require('mineflayer')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

// 自我身份：把"产出该结果的脚本版本"钉进证据本身，杜绝"旧版探针的结论被当成新版"。
const SELF_BYTES = fs.readFileSync(__filename)
const SELF_STAT = fs.statSync(__filename)
const SELF_SHA256 = crypto.createHash('sha256').update(SELF_BYTES).digest('hex').toUpperCase()

const HOST = process.env.MC_HOST || '127.0.0.1'
const PORT = parseInt(process.env.MC_PORT || '25566', 10)   // run/server.properties: server-port=25566（历史日志：Starting Minecraft server on *:25566）
const VERSION = process.env.MC_VERSION || '1.21.11'
const ROLE = process.env.ROLE_ID || 'meiqihezi'
const BOT_NAME = process.env.PROBE_NAME || 'QuitProbeBot'
const OBSERVER_NAME = process.env.OBSERVER_NAME || 'ObserverBot'

const evidence = {
  meta: {
    host: `${HOST}:${PORT}`,
    version: VERSION,
    probedAt: new Date().toISOString(),
    script: __filename,
    scriptIdentity: {
      bytes: SELF_STAT.size,
      mtime: SELF_STAT.mtime.toISOString(),
      mtimeNote: 'mtime 为 UTC（ISO8601，带 Z）；本地时间为 UTC+8，肉眼比对时勿直接与本地时刻对照',
      sha256: SELF_SHA256,
      note: '本 JSON 由该 SHA256 对应的脚本产出；引用本结果时必须同时引用此身份。'
        + ' 引用流程（与 eng-验证 约定）：读本字段的 sha256 → 对"当时磁盘上的脚本文件"再算一次 → 一致则采用，不一致判"结果与仪器版本不符"并挂 finding。'
    },
    runArgs: { MC_HOST: HOST, MC_PORT: PORT, MC_VERSION: VERSION, ROLE_ID: ROLE, PROBE_NAME: BOT_NAME, OBSERVER_NAME },
    notes: [
      'F1: RoleCommand.java:106-110 先按在线玩家解析 target，掉线后按名字查询的回执与 map 条目无关，故不作为任何断言',
      'F2: 无角色时 energy 查询回 "<名> has no role!"（:112），不是 "You have no role yet!"（那是 handleClear :189/:192）',
      'B 的推理链：离线模式重登复用同一 UUID → hasRole 按 UUID 查 map → 回 "has no role" 即证明条目已移除'
    ]
  },
  steps: [],
  chat: [],
  phases: {}
}
const log = (...a) => { const s = a.map(x => typeof x === 'string' ? x : JSON.stringify(x)).join(' '); console.log(s); evidence.steps.push(s) }
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

function makeBot (username) {
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username, version: VERSION, auth: 'offline' })
  bot.on('messagestr', (msg) => { evidence.chat.push({ who: username, msg }); console.log(`  [chat:${username}] ${msg}`) })
  bot.on('kicked', (r) => { evidence.chat.push({ who: username, kicked: String(r) }); console.log(`  [kicked:${username}] ${r}`) })
  bot.on('error', (e) => { evidence.steps.push(`[error:${username}] ${e.message}`); console.log(`  [error:${username}] ${e.message}`) })
  return bot
}

function waitFor (bot, event, timeoutMs = 90000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`timeout waiting '${event}' for ${bot.username}`)), timeoutMs)
    bot.once(event, (...args) => { clearTimeout(t); resolve(args) })
  })
}

/** 等待若干候选串中的**任意一个**出现；返回 {needle,msg} 或 null（=未测到，不当作"测得为否"） */
function waitChatAny (needles, timeoutMs = 15000) {
  return new Promise((resolve) => {
    const start = evidence.chat.length
    let poll = null; let kill = null
    const done = (v) => { if (poll) clearInterval(poll); if (kill) clearTimeout(kill); resolve(v) }
    poll = setInterval(() => {
      for (const c of evidence.chat.slice(start)) {
        if (typeof c.msg !== 'string') continue
        for (const n of needles) {
          if (c.msg.includes(n)) return done({ needle: n, msg: c.msg })
        }
      }
    }, 100)
    kill = setTimeout(() => done(null), timeoutMs)
  })
}

const ENERGY_RE = 'current energy level of'
// 收紧为"<本机器人名> has no role!"：既匹配 RoleCommand.java:112，又**不会**误配
// handleClear:192 的 "[..] has no role yet!"（多了 ' yet' → 本串不含它，故不相交）
const NOROLE_RE = `${BOT_NAME} has no role!`
const OFFLINE_RE = 'Cannot find the player you provided'

// 客户端侧读数：属性（含修饰符）、药水效果、背包
function snapshot (bot) {
  const attrs = (bot.entity && bot.entity.attributes) ? bot.entity.attributes : {}
  const maxHp = attrs['minecraft:max_health'] || attrs['generic.max_health'] || null
  // F-1（eng-验证 预检）：effects 必须能表达"未测到"（null），否则 E 的 unmeasured 分支是死代码 →
  // 读不到实体时会回落成 []，把"什么都没测到"判成"无残留药水 = 通过"（假阳性）。
  // 与 hotbar 写法保持一致：拿不到就是 null，不是 []。
  const effSrc = (bot.entity && bot.entity.effects) ? Object.values(bot.entity.effects) : null
  const hotbar = bot.inventory ? bot.inventory.slots.slice(36, 45).map(i => i ? `${i.name}x${i.count}` : null) : null
  return {
    health: bot.health,
    maxHealthAttr: maxHp ? JSON.parse(JSON.stringify(maxHp)) : null,
    // F-3（t6 实测自查发现）：mineflayer 的属性修饰符把标识放在 `uuid` 字段（实测 JSON：
    // {"uuid":"shadowhunterrolesplugin:role_health_modifier","amount":20,"operation":0}），
    // 而原判定只读 key/name → **恒为 false**，会让 C_persistence_modifierGone 无论修饰符是否残留都为真（假阳性）。
    // 现在三种字段都读。
    hasRoleHealthModifier: !!(maxHp && Array.isArray(maxHp.modifiers) &&
      maxHp.modifiers.some(m => String(m.uuid || m.key || m.name || '').includes('role_health_modifier'))),
    effects: effSrc ? effSrc.map(e => ({ id: e.id, amplifier: e.amplifier, duration: e.duration })) : null,
    hotbar,
    inventoryItems: bot.inventory ? bot.inventory.items().map(i => `${i.name}x${i.count}`) : null
  }
}

// 让 E 断言有意义：先尽力**制造**一个药水效果（本系统施加的），否则"重登后无药水"可能天然为真（vacuous）。
// 做法：拿热键栏里的角色物品逐个右键（技能图标由 RoleInstance 渲染到槽位），技能命中会给自己挂效果。
// 纯尽力而为：失败不抛错，只如实记录，绝不把"没能制造"伪装成"已清空"。
async function tryPrimeEffect (bot) {
  const info = { attempts: [], applied: false, slot: null }
  try {
    const slotWithItem = []
    for (let i = 0; i < 9; i++) {
      if (bot.inventory && bot.inventory.slots[36 + i]) slotWithItem.push(i)
    }
    const order = slotWithItem.length ? slotWithItem : [0]
    for (const s of order) {
      await bot.setQuickBarSlot(s)
      await sleep(400)
      try { bot.activateItem() } catch (e) { info.attempts.push(`slot${s}: activateError=${e.message}`); continue }
      await sleep(900)
      const eff = (bot.entity && bot.entity.effects) ? Object.values(bot.entity.effects) : []
      info.attempts.push(`slot${s}: effects=${eff.length}`)
      if (eff.length > 0) { info.applied = true; info.slot = s; break }
    }
  } catch (e) {
    info.error = e.message
  }
  return info
}

// ── 证据强度与退出码（纯函数，便于 --selftest 直接验证语义） ────────────────
// F-2（队长裁定）：E 的三态取值**只反映实际测量结果**，不要把 vacuous 混进 E 的取值；
// "证据强度"另设独立字段 E_evidenceStrength ∈ {strong, vacuous}。
function evidenceStrength (primed) { return primed ? 'strong' : 'vacuous' }

// 退出码语义（队长裁定）：0 = 全 true 且无 vacuous；4 = 全 true 但存在 vacuous（证据弱于宣称）；
// 2 = 有 false（按缺陷处理）；3 = 有 null（未测到）。优先级 false > null > vacuous。
function computeExitCode (vals, strengths) {
  if (vals.some(v => v === false)) return 2
  if (vals.some(v => v === null)) return 3
  if (strengths.some(s => s === 'vacuous')) return 4
  return 0
}

async function main () {
  log(`=== t6 掉线路径实测 === ${HOST}:${PORT} version=${VERSION} role=${ROLE}`)

  // 步骤 0：观察者先上线（全程不选角色）
  const observer = makeBot(OBSERVER_NAME)
  await waitFor(observer, 'spawn')
  await sleep(2000)
  log('步骤0: ObserverBot 已上线（不选角色）')

  // 步骤 1：被测机器人上线并选角色
  const probe = makeBot(BOT_NAME)
  await waitFor(probe, 'spawn')
  await sleep(3000)
  log('步骤1: QuitProbeBot 已上线')

  probe.chat(`/role set ${ROLE}`)
  const setAck = await waitChatAny(['role has been set'], 15000)
  log('步骤1: /role set 回执 =', setAck ? setAck.msg : '(未见回执)')
  await sleep(2500)   // 等属性/物品同步 + 至少一拍 ticker

  // 步骤 1b：先尽力制造一个本系统施加的药水效果，让断言 E 不是"天然为空"（vacuous）
  const priming = await tryPrimeEffect(probe)
  evidence.phases.effectPriming = priming
  log('步骤1b: 药水效果制造尝试 =', priming)

  evidence.phases.beforeQuit = snapshot(probe)
  log('步骤1: 掉线前客户端读数 =', evidence.phases.beforeQuit)

  // 断言 A：本人在线且有角色（期望 ENERGY_RE；若回 NOROLE_RE 则说明角色没选上）
  probe.chat('/role energy get')
  const aSelf = await waitChatAny([ENERGY_RE, NOROLE_RE], 8000)
  log('断言A: 本人查询(在线) =', aSelf ? aSelf.msg : '(未见回执)')

  // 旁证 A'：旁观者视角（非断言）
  observer.chat(`/role energy get ${BOT_NAME}`)
  const aObs = await waitChatAny([ENERGY_RE, NOROLE_RE, OFFLINE_RE], 8000)
  log("旁证A': Observer 查询 =", aObs ? aObs.msg : '(未见回执)')

  // 步骤 2：掉线（干净 disconnect → 服务端 PlayerQuitEvent）
  probe.quit()
  log('步骤2: QuitProbeBot 已断开（等价掉线）')
  await sleep(4000)

  // 步骤 3：掉线后旁观者查询 —— **只作原始记录**（F1：此回执与 map 条目无关，不作为断言）
  observer.chat(`/role energy get ${BOT_NAME}`)
  const afterQuitObs = await waitChatAny([OFFLINE_RE, ENERGY_RE, NOROLE_RE], 8000)
  evidence.phases.afterQuitObserverQuery = afterQuitObs ? afterQuitObs.msg : null
  log('步骤3: 掉线后 Observer 查询（原始记录，非断言；预期 OFFLINE_RE）=', afterQuitObs ? afterQuitObs.msg : '(未见回执)')

  // 步骤 4：同名重登（离线模式 UUID 由名字派生 → 同一 UUID）
  const probe2 = makeBot(BOT_NAME)
  await waitFor(probe2, 'spawn')
  await sleep(3000)

  // 断言 B：重登后应回 NOROLE_RE（:112）；若回 ENERGY_RE ⇒ map 条目仍存在 ⇒ 泄漏未修
  probe2.chat('/role energy get')
  const bRejoin = await waitChatAny([NOROLE_RE, ENERGY_RE], 8000)
  log('断言B: 重登后本人查询 =', bRejoin ? bRejoin.msg : '(未见回执)')

  evidence.phases.afterRejoin = snapshot(probe2)
  log('步骤4: 重登后客户端读数 =', evidence.phases.afterRejoin)

  // ── 判定：三态（true / false / null=未测到） ───────────────────────────
  const after = evidence.phases.afterRejoin
  const tri = (cond, unmeasured) => unmeasured ? null : !!cond
  // F-2（eng-验证 预检）：E 的"空转"必须进入判定，不能只写在 JSON 注释里。
  // 掉线前若没能制造出任何药水，则 E 是 vacuous（天然为空）→ 置 null，不得静默算通过。
  const ePrimed = !!(evidence.phases.beforeQuit && Array.isArray(evidence.phases.beforeQuit.effects) &&
    evidence.phases.beforeQuit.effects.length > 0)
  const verdict = {
    A_online_hadRole: tri(aSelf && aSelf.needle === ENERGY_RE, !aSelf),
    B_rejoin_noRole_mapEntryRemoved: tri(bRejoin && bRejoin.needle === NOROLE_RE, !bRejoin),
    C_persistence_modifierGone: tri(after.maxHealthAttr !== null && !after.hasRoleHealthModifier, after.maxHealthAttr === null),
    C_persistence_maxHealthIs20: tri(after.maxHealthAttr !== null && Number(after.maxHealthAttr.value) === 20, after.maxHealthAttr === null),
    D_hotbarClean: tri(Array.isArray(after.hotbar) && after.hotbar.every(x => x === null), !Array.isArray(after.hotbar)),
    // E（评审 P-1）：阶段 1 验收的另一半——"无残留药水"。不得用 C 的通过去覆盖它。
    // E 的取值只反映实际测量：读到空表=true / 读到非空=false / 读不到=null（未测到）。
    // "掉线前有没有药水"属**证据强度**，走 E_evidenceStrength，不污染 E 的取值（队长 F-2 裁定）。
    E_effectsCleared: tri(Array.isArray(after.effects) && after.effects.length === 0, !Array.isArray(after.effects))
  }
  evidence.verdict = verdict
  log('判定(三态) =', verdict)

  // 证据强度（独立于取值）：只有"掉线前确实存在药水"，E 才是 strong；否则 vacuous（天然为空）
  const eStrength = evidenceStrength(ePrimed)
  evidence.E_evidenceStrength = {
    E_effectsCleared: eStrength,
    primedBeforeQuit: ePrimed,
    primingAttempt: evidence.phases.effectPriming,
    basis: 'primedBeforeQuit=true → strong；false → vacuous（掉线前本来就没药水，E=true 不能证明"药水被清除且持久化"）',
    label: `E: 测得值=${JSON.stringify(verdict.E_effectsCleared)} / 强度=${eStrength} / 依据=primedBeforeQuit=${ePrimed}`
  }
  evidence.E_meaningfulness = {
    primedBeforeQuit: ePrimed,
    primingAttempt: evidence.phases.effectPriming,
    note: ePrimed
      ? '掉线前确有药水 → E 的判定有效（true/false 均可采信）'
      : '掉线前未能制造出药水 → E 的**证据强度为 vacuous**：E=true 只说明"重登后也没有药水"，不能证明"药水被清除且持久化"'
  }
  log('E 证据强度 =', evidence.E_evidenceStrength.label)
  if (!ePrimed) log('⚠️ 注意：E 为 vacuous（掉线前无药水）→ 已通过退出码 4 单独表达"证据弱于宣称"，不得据此宣称药水持久化已验证')

  const vals = Object.values(verdict)
  const strengths = Object.values(evidence.E_evidenceStrength).filter(v => v === 'strong' || v === 'vacuous')
  const exitCode = computeExitCode(vals, strengths)
  log(exitCode === 2 ? '结论：存在**测得为否**的断言 —— 这不是仪器问题，需按缺陷处理'
    : exitCode === 3 ? '结论：存在**未测到**（仪器盲区/超时）的断言 —— 不得计为通过，需留痕'
      : exitCode === 4 ? '结论：全部断言测得为真，但存在 **vacuous（证据弱于宣称）** 项 —— 不得据此宣称该项已验证'
        : '结论：全部断言测得为真且无 vacuous 项（含 quit 窗口持久化的运行实测通过）')

  fs.writeFileSync(path.join(__dirname, 't6-quit-persistence.result.json'), JSON.stringify(evidence, null, 2), 'utf8')
  log('原始证据已写入:', path.join(__dirname, 't6-quit-persistence.result.json'))

  try { probe2.quit() } catch (e) {}
  try { observer.quit() } catch (e) {}
  await sleep(1000)
  process.exit(exitCode)
}

// ── 自检（--selftest）：不连服务器，只验证 F-1 的三态可表达性 ────────────────
// 证据价值：证明"读不到实体时 effects = null（未测到）"而不是 []（会假阳性判通过）。
if (process.argv.includes('--selftest')) {
  const stubNoEntity = { entity: null, inventory: null, health: 20 }
  const stubEmptyEffects = { entity: { attributes: {}, effects: {} }, inventory: { slots: [], items: () => [] }, health: 20 }
  const stubWithEffects = { entity: { attributes: {}, effects: { 1: { id: 1, amplifier: 0, duration: 40 } } }, inventory: { slots: [], items: () => [] }, health: 20 }
  // F-3：修饰符标识实测在 `uuid` 字段 —— 必须能被识别，否则 C 断言恒真（假阳性）
  const stubWithModifier = { entity: { attributes: { 'minecraft:max_health': { value: 20, modifiers: [{ uuid: 'shadowhunterrolesplugin:role_health_modifier', amount: 20, operation: 0 }] } }, effects: {} }, inventory: { slots: [], items: () => [] }, health: 40 }
  const stubNoModifier = { entity: { attributes: { 'minecraft:max_health': { value: 20, modifiers: [] } }, effects: {} }, inventory: { slots: [], items: () => [] }, health: 20 }
  const s1 = snapshot(stubNoEntity)
  const s2 = snapshot(stubEmptyEffects)
  const s3 = snapshot(stubWithEffects)
  const T = true; const F = false; const N = null
  const result = {
    // F-1：三态可表达性
    noEntity_effects_is_null: s1.effects === null,
    emptyEffects_is_emptyArray: Array.isArray(s2.effects) && s2.effects.length === 0,
    withEffects_len1: Array.isArray(s3.effects) && s3.effects.length === 1,
    E_unmeasured_branch_reachable: s1.effects === null,
    // F-3：修饰符识别（uuid 字段）—— 残留可被识别、清空可被识别
    modifier_detected_when_present_uuid: snapshot(stubWithModifier).hasRoleHealthModifier === true,
    modifier_absent_when_cleared: snapshot(stubNoModifier).hasRoleHealthModifier === false,
    // F-2：证据强度与退出码语义
    strength_primed_is_strong: evidenceStrength(true) === 'strong',
    strength_unprimed_is_vacuous: evidenceStrength(false) === 'vacuous',
    exit_allTrue_strong_is_0: computeExitCode([T, T, T, T, T, T], ['strong']) === 0,
    exit_allTrue_vacuous_is_4: computeExitCode([T, T, T, T, T, T], ['vacuous']) === 4,
    exit_anyFalse_is_2: computeExitCode([T, T, F, T, T, T], ['strong']) === 2,
    exit_anyNull_is_3: computeExitCode([T, T, N, T, T, T], ['strong']) === 3,
    exit_falseBeatsVacuous_is_2: computeExitCode([F], ['vacuous']) === 2,
    exit_nullBeatsVacuous_is_3: computeExitCode([N], ['vacuous']) === 3
  }
  result.ok = Object.keys(result).every(k => result[k] === true)
  console.log(JSON.stringify({ selftest: result }, null, 2))
  process.exit(result.ok ? 0 : 1)
}

main().catch(err => {
  console.error('实测脚本异常终止:', err)
  evidence.steps.push('FATAL: ' + err.message)
  try {
    fs.writeFileSync(path.join(__dirname, 't6-quit-persistence.result.json'), JSON.stringify(evidence, null, 2), 'utf8')
  } catch (e) {}
  process.exit(1)
})

