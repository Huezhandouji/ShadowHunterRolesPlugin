// ============================================================================
// t63 smoke runner  (TEST TOOLING - mineflayer bot, drives /role debug ...)
//
// PURPOSE (t63 = the evidence + bot-smoke round AFTER t61 landed):
//   t61 added a SECOND output path for the debug commands: the same text that
//   goes to the player (Adventure chat) is ALSO written to the server console
//   via DebugCommand.log() with the prefix [command-debug].
//   => the smoke must capture BOTH paths verbatim, and must prove the op gate
//      on BOTH sides (chat output AND tab completion), because a second path is
//      a second place where a gate can leak.
//
// ANCHOR DISCIPLINE (spec 协同规范-起服停机取证.txt §5 family 8/10):
//   * narrow ASCII anchors only: [command-debug] [sched] [cooldown]
//     matrixAllPairsMatch= portLegEquivalent= CONCLUSION=
//     NEVER a bare word: `sched` also matches `scheduler`/`scheduler()` and
//     produced 22 false hits (found by eng-验证).
//   * every "empty result" conclusion is paired with a POSITIVE CONTROL on the
//     same instrument in the same run (trap family 5: a missing tool reports
//     empty and every comparison "succeeds").
//
// MODES
//   1) live     : node t63-smoke-runner.js --live [--out <transcript.txt>]
//                 (requires the server listening on 25566)
//   2) selftest : node t63-smoke-runner.js --selftest
//                 (NO server, NO network: runs the assertion engine against a
//                  synthetic known-true sample AND an empty sample; the empty
//                  sample MUST be refused, the synthetic MUST pass)
//
// ENV: MC_HOST MC_PORT MC_VERSION SMOKE_BOT SMOKE_BOT_NOOP COOLDOWN_SLOT
//      SMOKE_LOG (absolute path to run/logs/latest.log)
// USAGE NOTE: run from .scratch/bot (mineflayer is installed there).
// ============================================================================
'use strict'
const fs = require('fs')
const path = require('path')

const HOST = process.env.MC_HOST || '127.0.0.1'
const PORT = parseInt(process.env.MC_PORT || '25566', 10)
const VERSION = process.env.MC_VERSION || '1.21.11'
const OP_BOT = process.env.SMOKE_BOT || 'HueZhandouji'          // must be op (run/ops.json)
const NOOP_BOT = process.env.SMOKE_BOT_NOOP || ('smoke_probe_' + Math.floor(Math.random() * 1e6))
const ROLE_CANDIDATES = (process.env.SMOKE_ROLES || 'meiqihezi,red').split(',')
const LOG_PATH = process.env.SMOKE_LOG ||
  path.resolve(__dirname, '..', '..', 'ShadowHunterRoles', 'run', 'logs', 'latest.log')

// ---- narrow anchors (ASCII only) -------------------------------------------
const A = {
  CONSOLE: '[command-debug]',
  SCHED: /\[sched\]/,
  COOLDOWN: /\[cooldown\]/,
  MATRIX: /matrixAllPairsMatch=/,
  PORTLEG: /portLegEquivalent=/,
  CONCLUSION: /CONCLUSION=/,
  NO_PERM: 'You do not have permission to use this command.',
  USAGE_CD: 'Usage: /role debug cooldown <status|end|restart> <slot|componentId>',
  MODEB: ['Enabling ShadowHunterRolesPlugin', 'ShadowHunter Character System enabled.', 'Done (']
}

// ---- assertion engine (pure; used by both live and selftest) ---------------
// text  : the verbatim transcript of ONE path (chat transcript OR console slice)
// kind  : 'chat' | 'console'  -> which anchors that path is REQUIRED to carry
function evaluate (text, kind, label) {
  const checks = []
  const add = (name, ok, detail) => checks.push({ name, ok, detail })

  if (!text || text.trim().length === 0) {
    // trap family 5: refuse to judge empty input instead of "passing" vacuously
    add('non-empty input', false, 'EMPTY INPUT (after trim) -> refusing to judge')
    return { label, kind, checks, failed: 1, counts: {} }
  }

  const counts = {
    '[command-debug]': (text.match(/\[command-debug\]/g) || []).length,
    '[sched]': (text.match(/\[sched\]/g) || []).length,
    '[cooldown]': (text.match(/\[cooldown\]/g) || []).length,
    'matrixAllPairsMatch=': (text.match(/matrixAllPairsMatch=/g) || []).length,
    'portLegEquivalent=': (text.match(/portLegEquivalent=/g) || []).length,
    'CONCLUSION=': (text.match(/CONCLUSION=/g) || []).length
  }

  if (kind === 'chat') {
    add('chat: cooldown payload present', A.COOLDOWN.test(text), `[cooldown] count=${counts['[cooldown]']}`)
    add('chat: cooldown cooling= field present', /cooling=/.test(text), 'cooling= field')
    add('chat: sched probe anchors present',
      A.SCHED.test(text) && A.MATRIX.test(text) && A.PORTLEG.test(text) && A.CONCLUSION.test(text),
      `[sched]=${counts['[sched]']} matrix=${counts['matrixAllPairsMatch=']} portLeg=${counts['portLegEquivalent=']} conclusion=${counts['CONCLUSION=']}`)
  } else {
    add('console: [command-debug] path populated', counts['[command-debug]'] > 0,
      `[command-debug] count=${counts['[command-debug]']}`)
    add('console: cooldown payload echoed to console',
      counts['[command-debug]'] > 0 && /\[command-debug\] .*\[cooldown\]/.test(text),
      'narrow anchor: "[command-debug] ... [cooldown]"')
    add('console: sched probe anchors echoed to console',
      A.SCHED.test(text) && A.MATRIX.test(text) && A.PORTLEG.test(text) && A.CONCLUSION.test(text),
      `[sched]=${counts['[sched]']} matrix=${counts['matrixAllPairsMatch=']} portLeg=${counts['portLegEquivalent=']} conclusion=${counts['CONCLUSION=']}`)
  }
  return { label, kind, checks, failed: checks.filter((c) => !c.ok).length, counts }
}

// ---- op-gate assertions ----------------------------------------------------
function evaluateGate (g) {
  const checks = []
  const add = (name, ok, detail) => checks.push({ name, ok, detail })

  // positive control FIRST: the instrument itself must work for op
  add('op tab /role -> contains "debug" (instrument positive control)',
    Array.isArray(g.opRoot) && g.opRoot.includes('debug'), JSON.stringify(g.opRoot))
  add('op tab /role debug -> exactly [cooldown, sched]',
    Array.isArray(g.opDebug) && g.opDebug.length === 2 &&
    g.opDebug.includes('cooldown') && g.opDebug.includes('sched'), JSON.stringify(g.opDebug))
  add('op tab /role debug cooldown -> [status, end, restart]',
    Array.isArray(g.opCooldown) && ['status', 'end', 'restart'].every((x) => g.opCooldown.includes(x)),
    JSON.stringify(g.opCooldown))
  add('op tab /role debug sched -> [all]',
    Array.isArray(g.opSched) && g.opSched.includes('all'), JSON.stringify(g.opSched))

  // gate side 1: tab completion
  add('GATE tab: non-op /role debug -> EMPTY', Array.isArray(g.noDebug) && g.noDebug.length === 0, JSON.stringify(g.noDebug))
  add('GATE tab: non-op /role debug cooldown -> EMPTY', Array.isArray(g.noCooldown) && g.noCooldown.length === 0, JSON.stringify(g.noCooldown))
  add('non-op tab /role -> contains "debug" (same instrument, gate is topic-level)',
    Array.isArray(g.noRoot) && g.noRoot.includes('debug'), JSON.stringify(g.noRoot))

  // gate side 2: command output
  add('GATE chat: non-op got the permission line verbatim', (g.noChat || '').includes(A.NO_PERM), A.NO_PERM)
  add('GATE chat: non-op saw NO [sched] output', !A.SCHED.test(g.noChat || ''), 'narrow anchor [sched]')
  add('GATE chat: non-op saw NO [cooldown] payload', !/\[cooldown\] .*cooling=/.test(g.noChat || ''), 'narrow anchor "[cooldown] ... cooling="')
  add('GATE console: non-op window carried ZERO [command-debug] lines',
    (g.noConsole || '').split('[command-debug]').length - 1 === 0,
    `[command-debug] count=${(g.noConsole || '').split('[command-debug]').length - 1}`)

  return { checks, failed: checks.filter((c) => !c.ok).length }
}

function reportBlock (title, checks) {
  console.log(`\n---- ${title} ----`)
  let failed = 0
  for (const c of checks) {
    if (!c.ok) failed++
    console.log(`  [${c.ok ? 'OK' : 'MISMATCH'}] ${c.name}`)
    if (c.detail) console.log(`           ${c.detail}`)
  }
  return failed
}

// ---- selftest (no server) --------------------------------------------------
function selftest () {
  // Accounting: a NEGATIVE sample is expected to fail, and that is a SUCCESS of
  // the instrument. Only "expected pass but failed" / "expected fail but passed"
  // count against the selftest (trap families 5 + 10).
  let ok = 0; let bad = 0
  const expectPass = (title, checks) => {
    const f = reportBlock(title, checks)
    if (f === 0) { ok++ } else { bad++; console.log(`  [SELFTEST-FAIL] ${title}: expected 0 failed, got ${f}`) }
  }
  const expectFail = (title, checks) => {
    const f = reportBlock(title, checks)
    if (f > 0) { ok++ } else { bad++; console.log(`  [SELFTEST-FAIL] ${title}: expected >0 failed, got 0`) }
  }

  const synthetic = [
    '[12:00:01] [Server thread/INFO]: [ShadowHunterRolesPlugin] [command-debug] [cooldown] red_sanctified_blade | cooling=false | remainingTicks=0 | remainingSeconds=0.00 | declaredTicks=200',
    '[12:00:02] [Server thread/INFO]: [ShadowHunterRolesPlugin] [command-debug] [sched] ① probe start',
    'CHAT [sched] ⑤ verdict matrixAllPairsMatch=true portLegEquivalent=true',
    'CHAT CONCLUSION=adapter normalisation verified'
  ].join('\n')
  const chat = 'CHAT [cooldown] red_sanctified_blade | cooling=true | remainingTicks=100 | remainingSeconds=5.00 | declaredTicks=200\n' +
    'CHAT [sched] ⑤ verdict matrixAllPairsMatch=true portLegEquivalent=true\nCHAT CONCLUSION=ok'

  console.log('==== selftest: synthetic KNOWN-TRUE samples (MUST pass) ====')
  expectPass('console synthetic known-true', evaluate(synthetic, 'console', 'synthetic').checks)
  expectPass('chat synthetic known-true', evaluate(chat, 'chat', 'synthetic').checks)

  console.log('\n==== selftest: EMPTY sample MUST be refused (trap family 5) ====')
  expectFail('empty sample must be refused', evaluate('   \r\n  ', 'console', 'empty').checks)

  console.log('\n==== selftest: GARBAGE sample MUST fail every anchor ====')
  const garbage = evaluate('random noise without any anchor', 'console', 'garbage')
  expectFail('garbage sample must fail', garbage.checks)
  console.log(`  [expectation] garbage failed-checks = ${garbage.failed} (all 3 anchors must miss)`)
  if (garbage.failed === 3) { ok++ } else { bad++; console.log('  [SELFTEST-FAIL] garbage sample did not miss exactly 3 anchors') }

  console.log('\n==== selftest: gate table ====')
  const goodGate = {
    opRoot: ['debug', 'help'], opDebug: ['cooldown', 'sched'], opCooldown: ['status', 'end', 'restart'], opSched: ['all'],
    noRoot: ['debug', 'help'], noDebug: [], noCooldown: [],
    noChat: A.NO_PERM, noConsole: '[12:00:00] [Server thread/INFO]: nothing debug here'
  }
  const leakedTab = { ...goodGate, noDebug: ['cooldown', 'sched'] }        // simulated tab-completion leak
  const leakedChat = { ...goodGate, noChat: 'You do not have permission to use this command.\n[sched] ① oops' }
  expectPass('gate/clean (expect all OK)', evaluateGate(goodGate).checks)
  expectFail('gate/tab-leak (MUST be caught)', evaluateGate(leakedTab).checks)
  expectFail('gate/chat-leak (MUST be caught)', evaluateGate(leakedChat).checks)

  console.log(`\n==== selftest total: ok=${ok} bad=${bad} ====`)
  console.log('EXPECTED: ok=8 bad=0  (2 known-true + 1 empty-refusal + 1 garbage +')
  console.log('          1 leaked tab + 1 leaked chat caught + 1 clean gate + 1 garbage-anchor-count)')
  process.exit(bad === 0 ? 0 : 1)
}

// ---- live ------------------------------------------------------------------
async function live (outFile) {
  const mineflayer = require('mineflayer')
  const lines = []
  const log = (...a) => { const s = a.join(' '); lines.push(s); console.log(s) }
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
  const readLog = () => (fs.existsSync(LOG_PATH) ? fs.readFileSync(LOG_PATH, 'utf8') : '')
  const sliceFrom = (whole, off) => whole.length > off ? whole.slice(off) : ''
  const logLen = () => (fs.existsSync(LOG_PATH) ? fs.statSync(LOG_PATH).size : 0)

  const chat = []
  const stepResults = []

  function makeBot (username) {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, version: VERSION, auth: 'offline' })
    bot.on('messagestr', (msg) => { chat.push(`[${username}] ${msg}`); console.log(`  [chat:${username}] ${msg}`) })
    bot.on('kicked', (r) => chat.push(`[${username}] KICKED ${JSON.stringify(r)}`))
    bot.on('error', (e) => chat.push(`[${username}] ERROR ${e.message}`))
    return bot
  }

  log(`=== t63 smoke === ${new Date().toISOString()} host=${HOST}:${PORT} version=${VERSION} op=${OP_BOT} noop=${NOOP_BOT}`)
  log(`server log = ${LOG_PATH}`)
  log(`HEAD = ${process.env.SMOKE_HEAD || '(caller to fill: git rev-parse HEAD)'}`)

  if (!fs.existsSync(LOG_PATH)) { log(`FATAL: server log not found at ${LOG_PATH} (trap family 9: absolute path)`); process.exit(2) }

  const off0 = logLen()
  log(`console offset0 = ${off0} (op window starts here)`)

  // ---------- op phase ----------
  const bot = makeBot(OP_BOT)
  await new Promise((res, rej) => { bot.once('spawn', res); bot.once('error', rej); setTimeout(() => rej(new Error('spawn timeout')), 60000) })
  log('op spawn ok')
  await sleep(1500)

  async function step (cmd, waitMs) {
    log(`>>> ${cmd}`)
    bot.chat(cmd)
    await sleep(waitMs || 1600)
  }
  async function tab (text) {
    try {
      const m = await bot.tabComplete(text, true, false, 5000)
      log(`>>> TAB "${text}" -> ${JSON.stringify(m)}`)
      return m
    } catch (e) {
      log(`>>> TAB "${text}" -> ERROR ${e.message}`)
      return null
    }
  }

  const opRoot = await tab('/role ')
  const opDebug = await tab('/role debug ')
  const opCooldown = await tab('/role debug cooldown ')
  const opSched = await tab('/role debug sched ')

  // role setup (needed for a real cooldown payload instead of "You have no role yet!")
  let roleSet = null
  for (const r of ROLE_CANDIDATES) {
    await step(`/role set ${r}`, 1600)
    if (chat.join('\n').includes(`Your role has been set: ${r}`)) { roleSet = r; break }
  }
  log(`role set -> ${roleSet === null ? 'NONE (all candidates failed)' : roleSet}`)

  // find a slot that resolves to an ActiveComponent (payload instead of "No component found for:")
  let slot = null
  const preferred = process.env.COOLDOWN_SLOT || '1'
  const order = [preferred, '0', '2', '3', '4', '5', '6', '7', '8']
  for (const s of order) {
    const before = chat.length
    await step(`/role debug cooldown status ${s}`, 1500)
    const got = chat.slice(before).join('\n')
    if (/\[cooldown\] .*cooling=/.test(got)) { slot = s; break }
    if (/You have no role yet!/.test(got)) break
  }
  log(`cooldown slot resolved -> ${slot === null ? 'NONE' : slot}`)

  if (slot !== null) {
    await step(`/role debug cooldown status ${slot}`, 1500)          // state A: not cooling
    await step(`/role debug cooldown restart ${slot}`, 1800)         // restart -> cooling
    await step(`/role debug cooldown status ${slot}`, 1500)          // state B: cooling=true (proves restart)
    const m = chat.join('\n').match(/\[cooldown\] (\S+) \| cooling=/)
    const compId = m ? m[1] : null
    log(`componentId resolved from payload -> ${compId}`)
    if (compId) await step(`/role debug cooldown status ${compId}`, 1500)   // id form
    await step(`/role debug cooldown end ${slot}`, 1800)             // end -> not cooling
    await step(`/role debug cooldown status ${slot}`, 1500)          // state C: back to false
  }
  await step('/role debug cooldown', 1500)                            // state D: usage (arity)
  await step('/role debug cooldown bogus 0', 1500)                    // default branch -> usage
  await step('/role debug sched', 1500)                               // usage of sched
  await step('/role debug sched all', 14000)                          // the probe (needs ticks)

  const off1 = logLen()
  log(`console offset1 = ${off1} (op window ends here)`)
  await sleep(600)

  // ---------- non-op phase (op gate) ----------
  const nb = makeBot(NOOP_BOT)
  await new Promise((res, rej) => { nb.once('spawn', res); nb.once('error', rej); setTimeout(() => rej(new Error('noop spawn timeout')), 60000) })
  log('non-op spawn ok')
  await sleep(1500)
  async function ntab (text) {
    try { const m = await nb.tabComplete(text, true, false, 5000); log(`>>> NOOP TAB "${text}" -> ${JSON.stringify(m)}`); return m } catch (e) { log(`>>> NOOP TAB "${text}" -> ERROR ${e.message}`); return null }
  }
  const noRoot = await ntab('/role ')
  const noDebug = await ntab('/role debug ')
  const noCooldown = await ntab('/role debug cooldown ')
  const noChatStart = chat.length
  nb.chat('/role debug sched all'); await sleep(4000)
  nb.chat('/role debug cooldown status 0'); await sleep(2000)
  nb.chat('/role debug cooldown end 0'); await sleep(2000)
  const noChat = chat.slice(noChatStart).join('\n')
  const off2 = logLen()
  log(`console offset2 = ${off2} (non-op window ends here)`)
  await sleep(600)

  // cleanup: leave server state as found
  await step('/role clear', 1500)
  try { nb.quit() } catch (e) {}
  try { bot.quit() } catch (e) {}
  await sleep(1200)

  // ---------- console slices (verbatim windows, offset-bounded) ----------
  // Trap family: report the offsets AND the resulting lengths so a truncated /
  // rotated log cannot silently produce an empty comparison.
  const whole = readLog()
  const off0c = Math.min(off0, whole.length)
  const off1c = Math.min(off1, whole.length)
  const off2c = Math.min(off2, whole.length)
  const opSliceText = whole.slice(off0c, off1c)     // op window (console path must be populated here)
  const noOpSliceText = whole.slice(off1c, off2c)   // non-op window (must carry ZERO [command-debug])
  log(`console file now = ${whole.length} chars; offsets ${off0}/${off1}/${off2} -> clamped ${off0c}/${off1c}/${off2c}`)
  log(`console slice lengths: op=${opSliceText.length} nonop=${noOpSliceText.length}`)
  if (whole.length < off1) log('WARN: log shorter than an offset -> the log was rotated/truncated mid-run; slices are clamped and may be incomplete')
  if (opSliceText.length === 0) log('WARN: op window slice is EMPTY -> the console path cannot be judged (will be reported as MISMATCH, not as pass)')

  // ---------- assertions ----------
  const chatText = chat.join('\n')
  let failed = 0
  const eChat = evaluate(chatText, 'chat', 'bot chat transcript')
  failed += reportBlock('chat path', eChat.checks)
  const eConsole = evaluate(opSliceText, 'console', 'server log, op window (verbatim)')
  failed += reportBlock('console path ([command-debug])', eConsole.checks)

  const gate = evaluateGate({
    opRoot, opDebug, opCooldown, opSched, noRoot, noDebug, noCooldown, noChat, noConsole: noOpSliceText
  })
  failed += reportBlock('op gate (tab completion + command output + console window)', gate.checks)

  const modeb = A.MODEB.map((s) => ({ name: `Mode B line present: ${s}`, ok: whole.includes(s) }))
  failed += reportBlock('Mode B qualification lines (whole log)', modeb)

  // ---------- verbatim transcript ----------
  log('\n===== VERBATIM: chat transcript =====')
  log(chatText)
  log('\n===== VERBATIM: server log, op window =====')
  log(opSliceText)
  log('\n===== VERBATIM: server log, non-op window =====')
  log(noOpSliceText)
  log(`\n==== live total failed = ${failed} ====`)
  if (outFile) { fs.writeFileSync(outFile, lines.join('\n') + '\n', 'utf8'); console.log(`transcript -> ${outFile}`) }
  process.exit(failed === 0 ? 0 : 1)
}

// ---- entry -----------------------------------------------------------------
const argv = process.argv.slice(2)
if (argv.includes('--selftest')) {
  selftest()
} else {
  const i = argv.indexOf('--out')
  live(i >= 0 ? argv[i + 1] : path.resolve(__dirname, 't63-smoke-transcript.txt'))
    .catch((e) => { console.error('live run failed:', e && e.stack ? e.stack : e); process.exit(2) })
}
