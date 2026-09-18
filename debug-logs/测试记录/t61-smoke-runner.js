// ============================================================================
// t61 smoke runner  (TEST TOOLING - mineflayer bot, drives /role debug ...)
//
// WHY: t59/t61 moved the probe into a formal subcommand and added console
// output for the debug commands. The smoke must therefore drive the NEW surface:
//     /role debug cooldown status | end <slot|componentId> | restart <slot|componentId>
//     /role debug sched [all]        (scheduler probe; output anchors unchanged)
//
// ANCHOR DISCIPLINE (learned the hard way tonight):
//   assert on NARROW anchors, never a bare word - `sched` also matches
//   `scheduler` / `scheduler()` (plain API names) and produced 22 false hits.
//   Anchors used here:  [sched] , matrixAllPairsMatch= , portLegEquivalent= ,
//   CONCLUSION= , sameThread=
//
// TWO MODES
//   1) live  : node t61-smoke-runner.js --out <transcript.txt>
//              (requires the server on 25566 and the bot name to be op)
//   2) dry-run (NO server, NO network): parse an existing transcript/log and
//              evaluate the assertions -> this is the parser self-test.
//              node t61-smoke-runner.js --dry-run <file> [<file>...]
//
// ENV OVERRIDES (same names as the t6 harness): MC_HOST MC_PORT MC_VERSION
// USAGE NOTE: run from .scratch/bot (mineflayer is installed there).
// ============================================================================
'use strict'
const fs = require('fs')
const path = require('path')

const HOST = process.env.MC_HOST || '127.0.0.1'
const PORT = parseInt(process.env.MC_PORT || '25566', 10)
const VERSION = process.env.MC_VERSION || '1.21.11'
const BOT_NAME = process.env.SMOKE_BOT || 'HueZhandouji'   // must be op (run/ops.json)
const OPGATE_BOT = process.env.SMOKE_BOT_OPGATE || ('smoke_probe_' + Math.floor(Math.random() * 1e6))
const COOLDOWN_SLOT = process.env.COOLDOWN_SLOT || '1'

// The smoke steps. Each step: { name, cmd, expect: [regex,...] , optional }
function buildSteps () {
  return [
    { group: 'cooldown', name: 'cooldown/status', cmd: '/role debug cooldown status',
      expect: [/cooldown/i] },
    { group: 'cooldown', name: 'cooldown/end', cmd: `/role debug cooldown end ${COOLDOWN_SLOT}`,
      expect: [/cooldown|end/i] },
    { group: 'cooldown', name: 'cooldown/restart', cmd: `/role debug cooldown restart ${COOLDOWN_SLOT}`,
      expect: [/cooldown|restart/i] },
    { group: 'sched', name: 'debug/sched (probe)', cmd: '/role debug sched all',
      expect: [/\[sched\]/, /CONCLUSION=/, /\[sched\] ⑤/, /sameThread=/] },
    { group: 'sched', name: 'debug/sched anchors (matrix+warnings)', cmd: '/role debug sched all',
      expect: [/matrixAllPairsMatch=/, /portLegEquivalent=/], optional: true }
  ]
}

// --------------------------------------------------------------------------
// assertion engine (shared by both modes)
//   onlyFilter: when set, steps of OTHER groups are skipped entirely (used by
//   --dry-run on a file that only contains one kind of output). Without it the
//   dry-run would report false mismatches for the missing group.
// --------------------------------------------------------------------------
function evaluate (text, label, onlyFilter) {
  const steps = buildSteps().filter((s) => !onlyFilter || s.group === onlyFilter)
  const results = []
  for (const s of steps) {
    const hitRe = s.expect.filter((re) => re.test(text))
    const missing = s.expect.filter((re) => !re.test(text))
    results.push({
      step: s.name,
      cmd: s.cmd,
      optional: !!s.optional,
      ok: s.optional ? (hitRe.length > 0) : (missing.length === 0),
      hit: hitRe.map(String),
      missing: missing.map(String)
    })
  }
  // diagnostic counters (narrow anchors only)
  const counts = {
    '[sched]': (text.match(/\[sched\]/g) || []).length,
    'matrixAllPairsMatch=': (text.match(/matrixAllPairsMatch=/g) || []).length,
    'portLegEquivalent=': (text.match(/portLegEquivalent=/g) || []).length,
    'CONCLUSION=': (text.match(/CONCLUSION=/g) || []).length,
    'sameThread=': (text.match(/sameThread=/g) || []).length
  }
  return { label, results, counts }
}

function report (evaluation) {
  const { label, results, counts } = evaluation
  console.log(`---- assertions for: ${label} ----`)
  let failed = 0
  for (const r of results) {
    const tag = r.ok ? (r.optional ? 'OK(optional)' : 'OK') : (r.optional ? 'SKIP(optional)' : 'MISMATCH')
    if (!r.ok && !r.optional) failed++
    console.log(`  [${tag}] ${r.step}  <- ${r.cmd}`)
    if (r.missing.length) console.log(`           missing: ${r.missing.join(' , ')}`)
  }
  console.log(`  anchor counts: ${JSON.stringify(counts)}`)
  console.log(`  failed (non-optional) = ${failed}`)
  return failed
}

// --------------------------------------------------------------------------
// dry-run mode: parse existing files, no network
// --------------------------------------------------------------------------
function dryRun (files, onlyFilter) {
  let totalFailed = 0
  for (const f of files) {
    if (!fs.existsSync(f)) { console.log(`  MISSING FILE: ${f}`); totalFailed++; continue }
    const text = fs.readFileSync(f, 'utf8')
    console.log(`\n==== dry-run parse self-test: ${f} (${text.length} chars, only=${onlyFilter || 'all'}) ====`)
    // empty-input guard: check the TRIMMED text (a lone BOM / newline is still empty)
    if (text.trim().length === 0) {
      console.log('  EMPTY INPUT (after trim) -> refusing to judge (trap family 5)')
      totalFailed++
      continue
    }
    totalFailed += report(evaluate(text, path.basename(f), onlyFilter))
  }
  console.log(`\n==== dry-run total failed = ${totalFailed} ====`)
  process.exit(totalFailed === 0 ? 0 : 1)
}

// --------------------------------------------------------------------------
// live mode
// --------------------------------------------------------------------------
async function live (outFile, onlyFilter) {
  const mineflayer = require('mineflayer')
  const transcript = []
  const log = (...a) => { const s = a.join(' '); transcript.push(s); console.log(s) }

  function makeBot (username) {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, version: VERSION, auth: 'offline' })
    bot.on('messagestr', (msg) => { transcript.push(`CHAT ${msg}`); console.log(`  [chat] ${msg}`) })
    bot.on('kicked', (r) => transcript.push(`KICKED ${r}`))
    bot.on('error', (e) => transcript.push(`ERROR ${e.message}`))
    return bot
  }
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

  log(`=== t61 smoke === ${HOST}:${PORT} version=${VERSION} bot=${BOT_NAME} slot=${COOLDOWN_SLOT}`)
  const bot = makeBot(BOT_NAME)
  await new Promise((res, rej) => {
    bot.once('spawn', res); bot.once('error', rej)
    setTimeout(() => rej(new Error('timeout waiting spawn')), 30000)
  })
  log('spawn ok')

  for (const s of buildSteps()) {
    log(`>>> ${s.cmd}`)
    bot.chat(s.cmd)
    await sleep(2500)
  }
  if (process.env.SMOKE_OPGATE === '1') {
    log(`>>> [opgate] connecting as non-op bot ${OPGATE_BOT}`)
    const nb = makeBot(OPGATE_BOT)
    await new Promise((res, rej) => { nb.once('spawn', res); nb.once('error', rej); setTimeout(() => rej(new Error('opgate spawn timeout')), 30000) })
    nb.chat('/role debug sched all')
    await sleep(2500)
    nb.quit()
  }
  bot.quit()

  const text = transcript.join('\n')
  if (outFile) { fs.writeFileSync(outFile, text, 'utf8'); console.log(`transcript -> ${outFile} (${text.length} chars)`) }
  const failed = report(evaluate(text, outFile || '(live transcript)', onlyFilter))
  console.log(`==== live total failed = ${failed} ====`)
  process.exit(failed === 0 ? 0 : 1)
}

// --------------------------------------------------------------------------
const argv = process.argv.slice(2)
const onlyIdx = argv.indexOf('--only')
const onlyFilter = onlyIdx >= 0 ? argv[onlyIdx + 1] : null
if (argv[0] === '--dry-run') {
  dryRun(argv.slice(1).filter((a, i, arr) => a !== '--only' && arr[i - 1] !== '--only'), onlyFilter)
} else {
  const i = argv.indexOf('--out')
  live(i >= 0 ? argv[i + 1] : null, onlyFilter).catch((e) => { console.error('live run failed:', e.message); process.exit(2) })
}
