# ============================================================================
# Freeze instrument versions so any referenced version stays recoverable
# (TEST TOOLING, not product code)
#
# WHY (captain, new clause): .scratch\ is NOT under version control - an
# overwrite is permanent loss. Any instrument version that has been referenced
# in a report must stay recoverable. This copies each tool to a content-addressed
# name <name>-<sha8>-<bytes>.<ext> and appends a manifest row.
#
# USAGE (ExecutionPolicy blocks plain `& .\x.ps1` on this host):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\freeze-instruments.ps1
#
# Idempotent: an already-stored identical copy is skipped. Never overwrites.
# ============================================================================
param(
    [string[]]$Tools = @(
        't6-quit-persistence.js',
        'collect-runserver-identity.ps1',
        'numeric-multiset.ps1',
        'log-delta.ps1',
        'snapshot-src.ps1',
        'check-instruments.ps1',
        'freeze-instruments.ps1'
    ),
    [string]$StoreDir = ''
)

$ErrorActionPreference = 'Continue'
if (-not $StoreDir) { $StoreDir = Join-Path $PSScriptRoot 'instrument-versions' }
if (-not (Test-Path $StoreDir)) { New-Item -ItemType Directory -Force -Path $StoreDir | Out-Null }

$manifestPath = Join-Path $StoreDir 'MANIFEST.txt'
$rows = New-Object System.Collections.Generic.List[string]
if (Test-Path $manifestPath) {
    foreach ($l in (Get-Content $manifestPath -Encoding UTF8)) { if ($l.Trim()) { $rows.Add($l) } }
}

foreach ($t in $Tools) {
    $src = Join-Path $PSScriptRoot $t
    if (-not (Test-Path $src)) { Write-Output "SKIP (missing): $t"; continue }
    $fi = Get-Item $src
    $hash = (Get-FileHash $src -Algorithm SHA256).Hash
    $sha8 = $hash.Substring(0, 8)
    $ext = [System.IO.Path]::GetExtension($t)
    $base = [System.IO.Path]::GetFileNameWithoutExtension($t)
    $storeName = "$base-$sha8-$($fi.Length)$ext"
    $dest = Join-Path $StoreDir $storeName
    $isNew = $false
    if (-not (Test-Path $dest)) {
        Copy-Item $src $dest -Force
        $isNew = $true
    }
    $row = "$hash`t$($fi.Length)`t$($fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))`t$t`t$storeName"
    if (-not ($rows | Where-Object { $_ -like "$hash`t*" })) { $rows.Add($row) }
    Write-Output ("{0} {1} ({2} B) sha8={3}" -f ($(if ($isNew) { 'STORED ' } else { 'PRESENT' }), $t, $fi.Length, $sha8))
}

[System.IO.File]::WriteAllText($manifestPath, ($rows -join [Environment]::NewLine), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ""
Write-Output "manifest -> $manifestPath  ($($rows.Count) rows)"
Write-Output "NOTE: rows are 'sha256<TAB>bytes<TAB>mtime<TAB>source-name<TAB>stored-name'"
