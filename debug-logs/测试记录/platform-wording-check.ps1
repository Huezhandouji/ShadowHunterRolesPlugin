# ============================================================================
# platform/ javadoc wording self-check  (TEST TOOLING - read-only, not product code)
#
# PURPOSE (one command, 7 criteria)
#   The "platform wording" criteria were re-checked over and over, and FOUR times
#   a bare grep of a RETIRED sentence also matched a deliberately kept history
#   trace / ruling statement, so a fixed comment looked "not fixed yet". This
#   script prints EXPECTED vs ACTUAL side by side, lists every hit verbatim and
#   states how to read it, so the check cannot produce that false positive.
#
#   Rule of thumb it makes executable:
#     to decide "is it fixed", grep for a pattern that points at the CURRENT
#     wording - never grep a retired sentence as the pattern.
#   Related trap it also documents: `git status --porcelain -- src` being EMPTY
#   means COMMITTED, not "not started" (that got confused four times too).
#
# HOW TO RUN (plain `& .\x.ps1` is blocked by ExecutionPolicy on this host)
#   Option A - copy/paste friendly (from the repository root):
#       powershell -NoProfile -ExecutionPolicy Bypass -File .\debug-logs\<records-dir>\platform-wording-check.ps1
#     where <records-dir> is this file's folder (its name is CJK; the script
#     prints its own real path at the end, so you can copy it from there).
#   Option B - change into this file's folder first, then:
#       powershell -NoProfile -ExecutionPolicy Bypass -File .\platform-wording-check.ps1
#   Optional: -RepoRoot <path>  (defaults to this file's repository)
#
# WHY THE SOURCE OF THIS FILE MUST STAY PURE ASCII
#   Windows PowerShell 5.1 reads a BOM-less .ps1 using the system ANSI codepage.
#   CJK literals in this file would therefore corrupt parsing on load. All CJK
#   patterns below are therefore built from code points at runtime, all hit
#   lines printed come from git (never from this file's own source text), and
#   even this file's own folder name is NOT written here in CJK - it is printed
#   at run time from $PSCommandPath instead.
#
# READ-ONLY: reads git + the repository only; writes nothing, changes nothing.
# ============================================================================
param([string]$RepoRoot = "")
$script:myPath = $PSCommandPath

$ErrorActionPreference = 'Continue'

# This file lives at <repo>/debug-logs/<records-dir>/  ->  repo root is two levels up.
# (the real, CJK-bearing path is printed at run time as "script = ..." below)
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
Push-Location $RepoRoot

$plat = "src/main/java/com/shadowHunterRolesPlugin/platform"

# ---- CJK patterns built from code points (keeps this file ASCII-only) -------
$cZui = [char]0x6700; $cDuo = [char]0x591A; $cXiang = [char]0x76F8; $cCha = [char]0x5DEE
$cDe = [char]0x7684
$cTong = [char]0x7EDF; $cYi = [char]0x4E00; $cBao = [char]0x5305; $cZhuang = [char]0x88C5
$patClaim = ($cZui + $cDuo + $cXiang + $cCha) + "|" + ($cZui + $cDuo + " 1 tick") + "|1 tick " + $cDe
$patOldWrap = ($cTong + $cYi + $cBao + $cZhuang) + " \{@code BukkitTask\}|Folia \{@code ScheduledTask\}"
$patNewWrap = ($cTong + $cYi + $cBao + $cZhuang)

$script:failed = 0

function Show([string]$label, [string]$pattern, [int]$expected, [string[]]$scope, [string]$reading) {
    $hits = @(git grep -n -E $pattern -- @scope 2>$null)
    $verdict = if ($hits.Count -eq $expected) { "OK" } else { "MISMATCH"; $script:failed++ }
    Write-Output ("[{0}] {1}" -f $verdict, $label)
    Write-Output ("      pattern : {0}" -f $pattern)
    Write-Output ("      scope   : {0}" -f ($scope -join ' '))
    Write-Output ("      expected: {0}   actual: {1}" -f $expected, $hits.Count)
    Write-Output ("      reading : {0}" -f $reading)
    foreach ($h in $hits) { Write-Output ("        | " + $h) }
    Write-Output ""
}

Write-Output "============ platform/ javadoc wording self-check ============"
Write-Output ("script  = " + $script:myPath)
Write-Output ("HEAD = " + (git rev-parse HEAD))
Write-Output ("src porcelain lines = " + @(git status --porcelain -- src).Count + "   (0 = COMMITTED, NOT 'not started')")
Write-Output ""

# 1
Show "no Folia-ready anywhere" `
     "Folia-ready" 0 @("src/main/java") `
     "word fully removed (expected 0)"
# 2
Show "retired phase claim (three spellings) must not match" `
     $patClaim 0 @("src/main/java") `
     "retired claim fully reclaimed; any hit = regression (expected 0)"
# 3
Show "old wrapping phrase AS A WHOLE must not match" `
     $patOldWrap 0 @("src/main/java") `
     "old sentence removed (expected 0); the bare word alone does hit the NEW wording, see #7"
# 4
Show "Folia hits inside platform/ (must all be ruling statements)" `
     "Folia" 5 @($plat) `
     "all 5 are the REQUIRED ruling text (no Folia adaptation / not Folia-specific), not a stale claim"
# 5
Show "normalisation statement must STAY (adapter :26)" `
     "1 tick" 1 @($plat) `
     "adapter :26 says initialDelay<=0 is normalised to 1 tick (Math.max(1L, ...)) - the rule itself, keep it"
# 6
Show "normalisation implementation points (2 code + 2 wording = 4)" `
     "Math.max\(1" 4 @("src/main/java") `
     "2 code (TimerPortImpl:51, adapter :56) + 2 wording (adapter, Scheduler) - all must stay"
# 7
Show "new wording phrase (exactly 1, Task.java:6)" `
     $patNewWrap 1 @($plat) `
     "Task.java:6 NEW wording; not a stale claim"

Write-Output ("==================== result: {0} of 7 criteria mismatched ====================" -f $script:failed)
Pop-Location
if ($script:failed -gt 0) { exit 1 } else { exit 0 }

# ---------------------------------------------------------------------------
# TODO (deliberately not done in this batch - keep scope to one file)
#   The seven expected values above are HARD-CODED for the current committed
#   state. To make this drift-resistant, derive them at run time instead, e.g.:
#     - criteria 1/2/3 stay literal 0 (they are invariants: retired text is gone)
#     - criteria 4/5/6/7 could be recomputed from the tree, e.g.
#         * count occurrences of the ruling sentence in $plat  -> expect == 3 lines
#           (adapter / Scheduler x2 lines / Task) using a ruling-pattern search
#         * count `Math.max(1` hits and assert == (number of normalisation call
#           sites) + (number of wording statements); both are derivable via
#           `git grep -n "Math.max(1" -- <scope>` plus a comment-line filter
#     - any change of expected value must cite the commit that changed the tree
#   Also TODO: add the same style of check for the component layer
#   (roleComponent/red/RedSolitaryArroganceSkill.java:22 mentions Folia in a
#   historical comment; out of the platform scope of this script).
# ---------------------------------------------------------------------------
