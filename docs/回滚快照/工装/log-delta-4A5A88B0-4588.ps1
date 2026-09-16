# ============================================================================
# runServer log delta (TEST TOOLING, not product code)
#
# WHY: a naive case-insensitive "exception" scan is a FALSE-ALARM machine: the
# anchor logs already contain 3 oshi hardware-probe WARN lines whose TEXT says
# "COM exception ..." (HRESULT 80041003). Every server start reproduces them, so
# "3 new Exceptions" would be reported forever. Correct method (eng-verifier):
# normalise (strip timestamps) the WARN/ERROR/SEVERE lines and take a SET DIFF
# against the anchor; report only ADDED lines, with the known noise listed.
#
# USAGE (ExecutionPolicy blocks plain `& .\x.ps1` on this host):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\log-delta.ps1 `
#       -Anchor ".scratch\bot\baseline-runserver-t2-2026-09-16-2201.log" `
#       -Current "<repo>\run\logs\latest.log" -Tag "t6"
#
# READ-ONLY: reads logs, writes only the report under this script's own folder.
# ============================================================================
param(
    [Parameter(Mandatory = $true)][string]$Anchor,
    [Parameter(Mandatory = $true)][string]$Current,
    [string]$Tag = "run",
    [string]$OutDir = ""
)

$ErrorActionPreference = 'Continue'
if (-not $OutDir) { $OutDir = $PSScriptRoot }

function Get-SeverityLines([string]$path) {
    if (-not (Test-Path $path)) { return @() }
    $lines = Get-Content $path -Encoding UTF8
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($l in $lines) {
        # Paper severity marker, e.g. "[22:01:08] [ServerMain/WARN]: ..." or "[22:01:18] [Server thread/INFO]: ..."
        if ($l -match '/\s*(WARN|ERROR|SEVERE)\s*\]') {
            # normalise: drop the leading "[HH:MM:SS] " timestamp so the same event compares equal across runs
            $n = $l -replace '^\[\d{2}:\d{2}:\d{2}\]\s*', ''
            $out.Add($n)
        }
    }
    return $out
}

$a = @(Get-SeverityLines $Anchor) | Sort-Object
$c = @(Get-SeverityLines $Current) | Sort-Object

$diff = @(Compare-Object -ReferenceObject $a -DifferenceObject $c)
$added = @($diff | Where-Object { $_.SideIndicator -eq '=>' } | ForEach-Object { $_.InputObject })
$removed = @($diff | Where-Object { $_.SideIndicator -eq '<=' } | ForEach-Object { $_.InputObject })

$report = New-Object System.Collections.Generic.List[string]
$report.Add("anchor      = $Anchor")
$report.Add("current     = $Current")
# direction matters: ADDED/REMOVED only make sense in time order. Pass the PRE-change
# log as -Anchor and the POST-change log as -Current, otherwise ADDED and REMOVED swap.
$report.Add("direction   = anchor(pre) -> current(post)   [if reversed, ADDED/REMOVED swap meaning]")
$report.Add("anchor WARN/ERROR/SEVERE lines = $($a.Count)")
$report.Add("current WARN/ERROR/SEVERE lines = $($c.Count)")
$report.Add("ADDED lines   = $($added.Count)")
$report.Add("REMOVED lines = $($removed.Count)")

# Classify ADDED lines so that environment/world noise is separated from
# anything that could actually be ours. Known noise classes:
#  A) oshi hardware probe:  "[ServerMain/WARN]: [oshi.util.platform.windows.WmiQueryHandler] COM exception ..."
#  B) world-state entity noise: "Entity uuid already exists" (depends on world data, not on plugin code)
$classA = @($added | Where-Object { $_ -match 'oshi\.util\.platform\.windows\.WmiQueryHandler' })
$classB = @($added | Where-Object { $_ -match 'Entity uuid already exists' })
$classC = @($added | Where-Object { $_ -notmatch 'oshi\.util\.platform\.windows\.WmiQueryHandler' -and $_ -notmatch 'Entity uuid already exists' })

$report.Add("")
$report.Add("--- ADDED, classified ---")
$report.Add("A) oshi hardware-probe noise = $($classA.Count)  (environment; reproducible every start)")
$report.Add("B) world-state entity noise  = $($classB.Count)  ('Entity uuid already exists'; depends on world data)")
$report.Add("C) OTHER (must be explained)  = $($classC.Count)  <-- only this class can indicate a real problem")
$report.Add("")
$report.Add("--- C) OTHER, normalised ---")
foreach ($x in $classC) { $report.Add("+ $x") }
$report.Add("")
$report.Add("--- A) + B) normalised (for the record) ---")
foreach ($x in ($classA + $classB)) { $report.Add("- $x") }
$report.Add("")
$report.Add("--- REMOVED (normalised) ---")
foreach ($x in $removed) { $report.Add("- $x") }

$text = $report -join [Environment]::NewLine
Write-Output $text
$dest = Join-Path $OutDir "logdelta-$Tag.txt"
[System.IO.File]::WriteAllText($dest, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ""
Write-Output "written -> $dest"
