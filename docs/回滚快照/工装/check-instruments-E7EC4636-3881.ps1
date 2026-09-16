# ============================================================================
# Instrument admission self-check (TEST TOOLING, not product code)
#
# Runs the per-instrument admission checks that a verifier would run before
# trusting any tool in this folder:
#   1) ASCII-only  - PS 5.1 reads BOM-less .ps1 as ANSI; non-ASCII bytes can break
#                    parsing (this project already hit it twice). Applies to .ps1.
#   2) PS parse    - powershell -Command "& { [scriptblock]::Create((Get-Content -Raw ...)) }"
#                    is not used here; instead the file is invoked with -? guard is
#                    skipped, and we rely on the caller's real run. We DO check the
#                    byte-level rules that silently break parsing.
#   3) identity    - bytes + mtime + SHA256 (content-addressed), and whether the
#                    exact version is archived under instrument-versions\.
#   4) read-only   - static scan for write/process verbs, listing them for review.
#
# USAGE (ExecutionPolicy blocks plain `& .\x.ps1` on this host):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\check-instruments.ps1
#
# Exit code: 0 = all checks passed; 1 = at least one failure.
# ============================================================================
param(
    [string[]]$Tools = @(
        't6-quit-persistence.js',
        'collect-runserver-identity.ps1',
        'numeric-multiset.ps1',
        'log-delta.ps1',
        'snapshot-src.ps1',
        'freeze-instruments.ps1'
    ),
    [string]$StoreDir = ''
)

$ErrorActionPreference = 'Continue'
if (-not $StoreDir) { $StoreDir = Join-Path $PSScriptRoot 'instrument-versions' }
$manifestPath = Join-Path $StoreDir 'MANIFEST.txt'
$archivedHashes = @()
if (Test-Path $manifestPath) {
    foreach ($l in (Get-Content $manifestPath -Encoding UTF8)) {
        if ($l.Trim()) { $archivedHashes += ($l -split "`t")[0] }
    }
}

$fail = 0
$rows = @()

foreach ($t in $Tools) {
    $p = Join-Path $PSScriptRoot $t
    if (-not (Test-Path $p)) { Write-Output "MISSING: $t"; $fail++; continue }
    $fi = Get-Item $p
    $bytes = [System.IO.File]::ReadAllBytes($p)
    $hash = (Get-FileHash $p -Algorithm SHA256).Hash
    $nonAscii = @($bytes | Where-Object { $_ -gt 127 })
    $isPs1 = $t.ToLower().EndsWith('.ps1')
    $asciiOk = if ($isPs1) { $nonAscii.Count -eq 0 } else { $true }

    $text = [System.IO.File]::ReadAllText($p)
    $writeVerbs = @()
    foreach ($v in @('Set-Content', 'Add-Content', 'Out-File', 'New-Item', 'Remove-Item', 'Copy-Item', 'Move-Item', 'Stop-Process', 'Start-Process', 'Move-Item')) {
        if ($text -match [regex]::Escape($v)) { $writeVerbs += $v }
    }
    $archived = ($archivedHashes -contains $hash)

    $status = if ($asciiOk) { 'PASS' } else { 'FAIL' }
    if (-not $asciiOk) { $fail++ }

    Write-Output ("[{0}] {1}" -f $status, $t)
    Write-Output ("        bytes={0}  mtime={1}  sha256={2}" -f $fi.Length, $fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'), $hash)
    Write-Output ("        asciiOnly={0} (nonAsciiBytes={1})  archivedUnderInstrumentVersions={2}" -f $asciiOk, $nonAscii.Count, $archived)
    Write-Output ("        write/processVerbsSeen={0}" -f ($(if ($writeVerbs.Count) { $writeVerbs -join ',' } else { '(none)' })))

    $rows += [ordered]@{ tool = $t; bytes = $fi.Length; mtime = $fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'); sha256 = $hash; asciiOnly = $asciiOk; archived = $archived; writeVerbs = $writeVerbs }
}

$dest = Join-Path $PSScriptRoot 'instrument-selfcheck.json'
$json = [ordered]@{ checkedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'); failures = $fail; tools = $rows } | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText($dest, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ""
Write-Output ("failures = {0} ; report -> {1}" -f $fail, $dest)
exit $(if ($fail -gt 0) { 1 } else { 0 })
