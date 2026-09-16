# ============================================================================
# src snapshot + per-file SHA256 manifest (TEST TOOLING, not product code)
#
# WHY: every phase's "before" must be ITS OWN pre-work snapshot (captain 6.2).
# Using docs\<rollback-snapshot>\after-baseline as the base for a later phase mixes that
# phase's predecessors' changes into the diff, so diff can never be 0 and the
# cause is misattributed. Take the snapshot as the FIRST step of your task.
#
# USAGE (ExecutionPolicy blocks plain `& .\x.ps1` on this host):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\snapshot-src.ps1 -Tag "t6-before"
#   ... do the work ...
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\snapshot-src.ps1 -Tag "t6-after"
#   compare the two manifests to get the exact change surface (expected: 1 file).
#
# Produces: .\src-snapshots\<Tag>\  (full copy of src)  +  .\src-snapshots\<Tag>.sha256.txt
# READ-ONLY w.r.t. the repo: it only reads src and writes under this folder.
# ============================================================================
param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [string]$RepoRoot = ""
)

$ErrorActionPreference = 'Continue'
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\ShadowHunterRoles')).Path }

$srcDir  = Join-Path $RepoRoot 'src'
$snapRoot = Join-Path $PSScriptRoot 'src-snapshots'
$dest    = Join-Path $snapRoot $Tag

if (-not (Test-Path $srcDir)) { Write-Output "MISSING: $srcDir"; exit 1 }
if (Test-Path $dest) { Write-Output "destination already exists (refusing to overwrite): $dest"; exit 1 }

New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item (Join-Path $srcDir '*') $dest -Recurse -Force

$files = @(Get-ChildItem $dest -Recurse -Filter *.java | Sort-Object FullName)
$manifest = New-Object System.Collections.Generic.List[string]
foreach ($f in $files) {
    $rel = $f.FullName.Substring((($dest.TrimEnd('\')) + '\').Length)
    $h = (Get-FileHash $f.FullName -Algorithm SHA256).Hash
    $manifest.Add("$h`t$rel")
}
$manPath = Join-Path $snapRoot "$Tag.sha256.txt"
[System.IO.File]::WriteAllText($manPath, ($manifest -join [Environment]::NewLine), (New-Object System.Text.UTF8Encoding($false)))

Write-Output "snapshot dir : $dest"
Write-Output "java files   : $($files.Count)"
Write-Output "manifest     : $manPath"
Write-Output "snapshotTime : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
