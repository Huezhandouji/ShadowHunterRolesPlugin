# ============================================================================
# Literal multiset comparison: numeric + string (TEST TOOLING, not product code)
#
# Method source: the phase-0 t2 implementer (flow A), shared verbatim as a PowerShell
# snippet; this file only wraps it in an ASCII-only, parameterised script so that
# t6 / t7 can reproduce it identically.
#
# SCOPE : the WHOLE `src` tree, recursive, all *.java (47 files at baseline).
#         Do NOT use the guide's old wildcard `$src\*\*.java,$src\*\*\*.java`
#         (it misses 7 files under roleComponent/meiqiHezi/{mainWeapon,passive,skill}).
# RULE  : numeric mode strips block comments / line comments / string / char literals
#         in one pass, then extracts \b\d+(\.\d+)?([eE][-+]?\d+)?[fFdDlL]?\b ,
#         KEEPING suffixes such as 20f / 1L verbatim.
#         string mode captures double-quoted string literals RAW (as written).
# MULTISET: each output line = "<relative path><TAB><literal>"; sort with Sort-Object,
#         NEVER use -Unique; compare with Compare-Object. Expected diff rows = 0.
#
# USAGE (must be invoked this way on this host; plain `& .\x.ps1` is ExecutionPolicy-blocked):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\numeric-multiset.ps1 `
#       -BaseDir "<repo>\docs\REPO-SNAPSHOT\after-baseline" -Tag "rehearsal"
#   add -Mode numeric|string|both   (default numeric = historical behaviour, unchanged)
#
# NOTE for t6: take your OWN before-copy of `src` at task start and pass it as
# -BaseDir, otherwise earlier phases' changes leak into the diff and you cannot
# claim "0 rows".
# ============================================================================
param(
    [string]$RepoRoot = "",
    [string]$BaseDir = "",
    [string]$OutDir = "",
    [string]$Tag = "run",
    [ValidateSet('numeric', 'string', 'both')][string]$Mode = 'numeric',
    [string]$SrcRootOverride = ""   # if set, compare this src tree against BaseDir (used when
                                    # BaseDir is a bare snapshot of src, e.g. a temp copy)
)

$ErrorActionPreference = 'Continue'

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\ShadowHunterRoles')).Path }
if (-not $OutDir) { $OutDir = $PSScriptRoot }
if (-not $SrcRootOverride) { $SrcRootOverride = Join-Path $RepoRoot 'src' }

# Base may be either a snapshot root (containing src\) or a bare src copy.
$baseSrc = Join-Path $BaseDir 'src'
if (-not (Test-Path $baseSrc)) { $baseSrc = $BaseDir }

function Get-NumericLiterals([string]$text) {
    $stripped = [regex]::Replace($text,
        '(?s)/\*.*?\*/|//[^\r\n]*|"(?:\\.|[^"\\])*"|''(?:\\.|[^''\\])*''', ' ')
    [regex]::Matches($stripped, '\b\d+(?:\.\d+)?(?:[eE][-+]?\d+)?[fFdDlL]?\b') | ForEach-Object { $_.Value }
}

# string literals captured RAW (including the surrounding quotes and any escapes),
# so that "same text" means "same text" and a changed message shows up as a diff.
function Get-StringLiterals([string]$text) {
    $noBlock = [regex]::Replace($text, '(?s)/\*.*?\*/', ' ')
    $noLine = [regex]::Replace($noBlock, '//[^\r\n]*', ' ')
    [regex]::Matches($noLine, '"(?:\\.|[^"\\])*"') | ForEach-Object { $_.Value }
}

$baseFiles = @(Get-ChildItem $baseSrc -Recurse -Filter *.java -ErrorAction SilentlyContinue)
$curFiles  = @(Get-ChildItem $SrcRootOverride -Recurse -Filter *.java -ErrorAction SilentlyContinue)

Write-Output "baseSrc     = $baseSrc"
Write-Output "srcRoot     = $SrcRootOverride"
Write-Output "mode        = $Mode"
Write-Output "base files  = $($baseFiles.Count) ; current files = $($curFiles.Count)"

function Invoke-Mode([string]$kind, [scriptblock]$extract) {
    $before = New-Object System.Collections.Generic.List[string]
    $after = New-Object System.Collections.Generic.List[string]
    foreach ($f in $baseFiles) {
        $rel = $f.FullName.Substring((($baseSrc.TrimEnd('\')) + '\').Length)
        foreach ($v in (& $extract ([System.IO.File]::ReadAllText($f.FullName)))) { $before.Add("$rel`t$v") }
    }
    foreach ($f in $curFiles) {
        $rel = $f.FullName.Substring((($SrcRootOverride.TrimEnd('\')) + '\').Length)
        foreach ($v in (& $extract ([System.IO.File]::ReadAllText($f.FullName)))) { $after.Add("$rel`t$v") }
    }
    $b = $before | Sort-Object
    $a = $after | Sort-Object
    $d = @(Compare-Object -ReferenceObject $b -DifferenceObject $a)

    Write-Output "--- $kind ---"
    Write-Output "before      = $($b.Count) ; after = $($a.Count)"
    Write-Output "diff rows   = $($d.Count)"
    $d | ForEach-Object { "$($_.SideIndicator) $($_.InputObject)" }

    $b | Set-Content (Join-Path $OutDir "$kind-before-$Tag.txt") -Encoding ASCII
    $a | Set-Content (Join-Path $OutDir "$kind-after-$Tag.txt") -Encoding ASCII
    Write-Output "written -> $kind-before-$Tag.txt / $kind-after-$Tag.txt"
}

if ($Mode -eq 'numeric' -or $Mode -eq 'both') { Invoke-Mode 'numeric' { param($t) Get-NumericLiterals $t } }
if ($Mode -eq 'string' -or $Mode -eq 'both') { Invoke-Mode 'string' { param($t) Get-StringLiterals $t } }
