# ============================================================================
# t60 criteria instrument   (TEST TOOLING; read-only w.r.t. the repository)
#
# CARD t60 = "cleanup batch (split): delete the dead import at
#   roleComponent/red/RedDeeplySorrowSkill.java:3"
#
# Prints EVERY t60 criterion with its OWN measurement moment (spec section 5
# family 8 clause 3: never splice readings taken at different moments), and it
# is safe to run BEFORE and AFTER the edit. Repository is never written to; the
# only output is a report file under this script's own folder.
#
# CRITERIA GROUPS (card wording):
#   ZERO  group : target-file \bRoleInstance\b CODE positions = 0
#                 + `git ls-files --others --exclude-standard` empty
#                 + `git status` `??` count = 0
#   DECREASE grp: repo-wide \bRoleInstance\b count must go DOWN under BOTH
#                 calibers (plain `git grep` AND `--untracked`), each decrease
#                 itemised.  An import deletion IS a real reduction -- never
#                 report it as "0 rows changed".
#   SIDE-EVIDENCE (probe criteria; NOT part of this card's change surface):
#                 outside-whitelist hits must be 0; whitelist filtering is
#                 applied ON LINES (never as a git pathspec); patterns use
#                 ANCHORS -- a bare word `sched` matches production API names
#                 (`scheduler`, `scheduler()`) and produced 22 false hits.
#
# ASCII-ONLY SOURCE (spec section 5 family 10): Windows PowerShell 5.1 reads a
# BOM-less .ps1 as ANSI, so CJK literals here would break parsing. The circled
# digits 1..5 are built from CODEPOINTS at runtime instead.
#
# USAGE:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\t60-criteria.ps1 -Phase before
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\t60-criteria.ps1 -Phase after
#   optional: -Stamp selftest   -> report file gets that suffix (default: phase)
# ============================================================================
param(
  [ValidateSet('before','after')][string]$Phase = 'before',
  [string]$RepoRoot = "",
  [string]$OutDir = "",
  [string]$Stamp = "",
  [string]$DeltaFrom = ""     # sidecar from a previous run -> computes the DECREASE group
)
$ErrorActionPreference = 'Continue'
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\ShadowHunterRoles')).Path }
if (-not $OutDir)   { $OutDir   = $PSScriptRoot }
if (-not $Stamp)    { $Stamp    = $Phase }

$log = New-Object System.Collections.Generic.List[string]
function W([string]$s) { $log.Add($s); Write-Output $s }

# family 9: Push-Location below changes what a RELATIVE path means. Resolve a
# relative -DeltaFrom against the ORIGINAL location first. (Measured 2026-09-18:
# a relative sidecar path resolved inside the repo after Push-Location and the
# family-5 input guard fired -- which is how it was caught instead of silently
# reporting a false "0 rows".)
if ($DeltaFrom -and -not [System.IO.Path]::IsPathRooted($DeltaFrom)) {
  $DeltaFrom = Join-Path (Get-Location).Path $DeltaFrom
}

Push-Location $RepoRoot

$targetRel = 'src/main/java/com/shadowHunterRolesPlugin/roleComponent/red/RedDeeplySorrowSkill.java'
$whitelist = 'command/(DebugSchedCommand|DebugCommand)\.java'
$circled   = -join (0x2460..0x2464 | ForEach-Object { [char]$_ })   # build 1..5 circles from codepoints

W ("=================== t60 criteria | phase=" + $Phase + " | stamp=" + $Stamp + " ===================")
W ("measured at = " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff'))
W ("repo        = " + $RepoRoot)

# --- instrument self-proof (family 5): tool present, output non-empty --------
$gitCmd = Get-Command git -ErrorAction SilentlyContinue
W ("[INSTRUMENT] git present = " + ($null -ne $gitCmd) + " ; plain-and-untracked calibers both used below")
$head = (& git rev-parse HEAD 2>$null)
W ("[IDENTITY]   HEAD = " + $head)

# --- A) target file identity -------------------------------------------------
$targetAbs = Join-Path $RepoRoot ($targetRel -replace '/','\')
if (Test-Path -LiteralPath $targetAbs) {
  $abs   = (Resolve-Path -LiteralPath $targetAbs).Path            # family 9: absolute path
  $item  = Get-Item -LiteralPath $abs
  $bytes = $item.Length
  $nl    = ([System.IO.File]::ReadAllLines($abs)).Count            # family 2: real line count
  $sha   = (Get-FileHash -LiteralPath $abs -Algorithm SHA256).Hash
  W ("[A] target  = " + $targetRel)
  W ("    bytes=" + $bytes + "  lines(ReadAllLines)=" + $nl + "  mtime=" + $item.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss.fff'))
  W ("    SHA256=" + $sha)
} else { W ("[A] !! TARGET MISSING: " + $targetAbs) }

# --- B) ZERO group -----------------------------------------------------------
$hits = @(& git grep -n --untracked -E '\bRoleInstance\b' -- $targetRel 2>$null)
$code = @(); $cmnt = @()
foreach ($h in $hits) {
  $txt = ($h -split ':', 3)[2]
  $t = $txt.Trim()
  if ($t.StartsWith('//') -or $t.StartsWith('*') -or $t.StartsWith('/*')) { $cmnt += $h } else { $code += $h }
}
W ("[B-ZERO] target \bRoleInstance\b : total=" + $hits.Count + "  CODE=" + $code.Count + "  COMMENT=" + $cmnt.Count)
W ("         AFTER the edit, CODE must be 0")
foreach ($x in $code) { W ("      CODE | " + $x) }
foreach ($x in $cmnt) { W ("      CMNT | " + $x) }

$untracked = @(& git ls-files --others --exclude-standard 2>$null)
W ("[B-ZERO] git ls-files --others --exclude-standard = " + $untracked.Count + " lines   (expect 0)")
foreach ($x in $untracked) { W ("      ?? " + $x) }

$porc = @(& git status --porcelain 2>$null)
$qq   = @($porc | Where-Object { $_ -match '^\?\?' })
W ("[B-ZERO] porcelain lines = " + $porc.Count + "  ;; '??' count = " + $qq.Count + "   (expect 0)")
foreach ($x in $porc) { W ("      | " + $x) }
if ($porc.Count -gt 0) {
  $numstat = @(& git diff --numstat 2>$null)
  W ("[B-ZERO] git diff --numstat lines = " + $numstat.Count)
  foreach ($x in $numstat) { W ("      ns | " + $x) }
}

# --- C) DECREASE group (both calibers) --------------------------------------
$plain = @(& git grep -n -E '\bRoleInstance\b' -- src/main/java 2>$null)
$unt   = @(& git grep -n --untracked -E '\bRoleInstance\b' -- src/main/java 2>$null)
W ("[C-DECREASE] repo-wide \bRoleInstance\b : plain=" + $plain.Count + "   --untracked=" + $unt.Count)
if ($unt.Count -ne $plain.Count) {
  W ("      NOTE: calibers differ -> untracked files contribute the difference:")
  foreach ($x in @($unt | Where-Object { $plain -notcontains $_ })) { W ("      UNTR  | " + $x) }
} else {
  W ("      both calibers agree (no untracked .java contributes hits)")
}
$sidecar = Join-Path $OutDir ("t60-roleinstance-plain-" + $Stamp + ".txt")
[System.IO.File]::WriteAllLines($sidecar, [string[]]$plain, (New-Object System.Text.UTF8Encoding($false)))
W ("      sidecar (full plain list) -> " + $sidecar)
foreach ($x in ($plain | Select-Object -First 3)) { W ("      plain | " + $x) }
if ($plain.Count -gt 3) { W ("      ... (" + ($plain.Count - 3) + " more lines in the sidecar)") }

if ($DeltaFrom) {
  if (Test-Path -LiteralPath $DeltaFrom) {
    $absDelta = (Resolve-Path -LiteralPath $DeltaFrom).Path
    $before   = @([System.IO.File]::ReadAllLines($absDelta))
    $rm = @($before | Where-Object { $plain -notcontains $_ })
    $ad = @($plain  | Where-Object { $before -notcontains $_ })
    W ("[C-DELTA] vs " + $absDelta)
    W ("      caliber 1 = LINE-KEYED (path:lineno:text)  -- line numbers are moving targets")
    W ("      before=" + $before.Count + "  after=" + $plain.Count + "  DELTA=" + ($plain.Count - $before.Count) + "   (card requires < 0, itemised)")
    W ("      REMOVED = " + $rm.Count)
    foreach ($x in $rm) { W ("        - " + $x) }
    W ("      ADDED   = " + $ad.Count)
    foreach ($x in $ad) { W ("        + " + $x) }

    # caliber 2 = TEXT-ONLY (drop "<path>:<lineno>"): separates a REAL content
    # change from a pure LINE-NUMBER SHIFT. Measured 2026-09-18: deleting 1 line
    # made caliber 1 report 2 REMOVED / 1 ADDED, because every later line in the
    # file shifted up by one -- caliber 2 collapses that noise.
    function TextOnly([string[]]$arr) { return @($arr | ForEach-Object { ($_ -split ':', 3)[2] }) }
    $bt = TextOnly $before
    $at = TextOnly $plain
    $rmT = New-Object System.Collections.Generic.List[string]
    $adT = New-Object System.Collections.Generic.List[string]
    foreach ($k in ($bt | Sort-Object -Unique)) {
      $n = @($bt | Where-Object { $_ -eq $k }).Count
      $m = @($at | Where-Object { $_ -eq $k }).Count
      for ($i = 0; $i -lt ($n - $m); $i++) { $rmT.Add($k) }
    }
    foreach ($k in ($at | Sort-Object -Unique)) {
      $m = @($at | Where-Object { $_ -eq $k }).Count
      $n = @($bt | Where-Object { $_ -eq $k }).Count
      for ($i = 0; $i -lt ($m - $n); $i++) { $adT.Add($k) }
    }
    W ("      caliber 2 = TEXT-ONLY (line numbers dropped)  -- real content change only")
    W ("      REMOVED = " + $rmT.Count)
    foreach ($x in $rmT) { W ("        - " + $x) }
    W ("      ADDED   = " + $adT.Count)
    foreach ($x in $adT) { W ("        + " + $x) }
  } else { W ("[C-DELTA] !! sidecar NOT FOUND: " + $DeltaFrom + "  (assert input non-empty - family 5)") }
}

# --- D) side-evidence criteria (probe anchors, NOT this card's surface) -----
function OutsideReport([string]$label, [string]$pattern, [string]$scope) {
  $all = @(& git grep -n --untracked -E $pattern -- $scope 2>$null)
  $ins = @($all | Where-Object { $_ -match $whitelist })
  $out = @($all | Where-Object { $_ -notmatch $whitelist })
  $v = if ($out.Count -eq 0) { 'OK' } else { 'MISMATCH' }
  W ("[" + $v + "] " + $label + " : outside-whitelist expected=0 actual=" + $out.Count + "  (authorised inside=" + $ins.Count + ")")
  foreach ($x in $out) { W ("      OUTSIDE | " + $x) }
}
OutsideReport "probe OUTPUT anchors outside the authorised subcommands" ('matrixAllPairsMatch=|portLegEquivalent=|\[sched\] [' + $circled + ']') 'src/main/java'
OutsideReport "[sched] outside the authorised subcommands" '\[sched\]' 'src/main/java'

function InfoReport([string]$label, [string]$pattern) {
  $h = @(& git grep -n --untracked -E $pattern -- src/main/java 2>$null)
  W ("[INFO] " + $label + " = " + $h.Count + "   (NOT required to reach 0)")
  foreach ($x in $h) { W ("      info | " + $x) }
}
InfoReport 'schedprobe (adapter cites the evidence FILE; a hit is legitimate)' 'schedprobe'
InfoReport 'io.papermc...threadedregions.scheduler (adapter import is REQUIRED)' 'io\.papermc\.paper\.threadedregions\.scheduler'

# --- E) red lines -----------------------------------------------------------
$api = @(& git status --porcelain -- src/main/java/com/shadowHunterRolesPlugin/api 2>$null)
W ("[F-RED] api/ porcelain = " + $api.Count + "   (expect 0: no api/** semantics change)")

$csFile = Join-Path $RepoRoot 'src\main\java\com\shadowHunterRolesPlugin\core\ports\ComponentServices.java'
if (Test-Path -LiteralPath $csFile) {
  $txt = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $csFile).Path)
  $m = [regex]::Match($txt, 'record\s+ComponentServices\s*\(([^)]*)\)', 'Singleline')
  if ($m.Success) {
    $members = @($m.Groups[1].Value -split ',' | Where-Object { $_.Trim() -ne '' })
    W ("[F-RED] ComponentServices members = " + $members.Count + "   (expect 10)")
    W ("        header = " + ($m.Groups[1].Value -replace '\s+', ' ').Trim())
  } else { W "[F-RED] !! ComponentServices record header NOT matched - verify by hand" }
} else { W "[F-RED] !! ComponentServices.java not found - verify by hand" }

$ce = @(& git grep -n --untracked 'callEvent' -- src/main/java 2>$null)
W ("[F-RED] callEvent occurrences = " + $ce.Count + "   (both event publications must stay)")
foreach ($x in $ce) { W ("      | " + $x) }

$reg = @(& git status --porcelain -- src/main/resources/plugin.yml src/main/java/com/shadowHunterRolesPlugin/ShadowHunterRolesPlugin.java 2>$null)
W ("[F-RED] plugin.yml + main class porcelain = " + $reg.Count + "   (expect 0: /role registration untouched)")

# --- F) repository activity snapshot (family 8 clause 3) --------------------
$newest = Get-ChildItem (Join-Path $RepoRoot 'src') -Recurse -Filter *.java |
          Sort-Object LastWriteTime -Descending | Select-Object -First 1
W ("[INFO] newest src mtime = " + $newest.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss.fff') + "  (" + $newest.Name + ")")
$jars = @(Get-ChildItem (Join-Path $RepoRoot 'build\libs') -Filter *.jar -ErrorAction SilentlyContinue)
foreach ($j in $jars) {
  W ("[INFO] jar = " + $j.Name + "  " + $j.Length + " B  " + $j.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss.fff') +
     "  SHA256=" + (Get-FileHash -LiteralPath $j.FullName -Algorithm SHA256).Hash)
}

Pop-Location
$text = $log -join [Environment]::NewLine
$dest = Join-Path $OutDir ("t60-criteria-" + $Stamp + ".txt")
[System.IO.File]::WriteAllText($dest, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ""
Write-Output ("written -> " + $dest)
