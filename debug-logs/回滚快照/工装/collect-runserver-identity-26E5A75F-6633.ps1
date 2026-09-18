# ============================================================================
# t6 runServer identity collector (TEST TOOLING, not product code)
#
# WHY ASCII-ONLY: Windows PowerShell 5.1 reads .ps1 as the system ANSI codepage
# when the file has no UTF-8 BOM. This workspace path contains CJK characters,
# so any CJK literal in this file would corrupt parsing. All paths are derived
# from $PSScriptRoot at runtime instead of being spelled out here.
#
# INVOCATION (plain `& .\x.ps1` is blocked by ExecutionPolicy on this host):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\collect-runserver-identity.ps1 -StartTime "<time>"
#
# Collects the four metadata items required by the captain:
#   1) jar path + SHA256 + mtime
#   2) build time + server start time
#   3) full run\plugins listing
#   4) server PID + log path
# READ-ONLY: does not touch the repo, does not write into run\.
# ============================================================================
param(
    [string]$BuildTime = "",
    [string]$StartTime = ""
)

$ErrorActionPreference = 'Continue'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\ShadowHunterRoles')).Path
$jarPath  = Join-Path $repoRoot 'build\libs\ShadowHunterRolesPlugin-1.0.0.jar'
$runDir   = Join-Path $repoRoot 'run'
$out = [ordered]@{ collectedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'); repoRoot = $repoRoot }

# 1) jar identity
if (Test-Path $jarPath) {
    $jar = Get-Item $jarPath
    $out.jar = [ordered]@{
        path   = $jar.FullName
        bytes  = $jar.Length
        mtime  = $jar.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
        sha256 = (Get-FileHash $jarPath -Algorithm SHA256).Hash
    }
} else {
    $out.jar = "MISSING: $jarPath"
}

# 2) build / start times
# eng-verifier pre-check: do NOT leave placeholder strings in the JSON, they get
# cited as data. buildTime defaults to the jar mtime (jar written = build done);
# startTime cannot be derived reliably -> null + an explicit source tag.
if ($BuildTime) {
    $out.buildTime = $BuildTime
    $out.buildTimeSource = 'passed via -BuildTime'
} elseif (Test-Path $jarPath) {
    $out.buildTime = (Get-Item $jarPath).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
    $out.buildTimeSource = 'jar-mtime (auto)'
} else {
    $out.buildTime = $null
    $out.buildTimeSource = 'unavailable (jar missing)'
}
if ($StartTime) {
    $out.startTime = $StartTime
    $out.startTimeSource = 'passed via -StartTime'
} else {
    $out.startTime = $null
    $out.startTimeSource = 'NOT PROVIDED - do not cite as data; re-run with -StartTime'
}

# freshness per appendix B (expected: 0 sources newer than jar)
if (Test-Path $jarPath) {
    $jarTime = (Get-Item $jarPath).LastWriteTime
    $javaFiles = Get-ChildItem (Join-Path $repoRoot 'src') -Recurse -Filter *.java -ErrorAction SilentlyContinue
    $out.srcNewerThanJar = @($javaFiles | Where-Object { $_.LastWriteTime -gt $jarTime }).Count
    $classesDir = Join-Path $repoRoot 'build\classes'
    if (Test-Path $classesDir) {
        # NOTE: a directory's own mtime often does NOT change when files inside are
        # rewritten, so the newest .class file time is the reliable freshness signal.
        $out.classesDirMtime = (Get-Item $classesDir).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
        $newestClass = Get-ChildItem $classesDir -Recurse -Filter *.class -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($newestClass) {
            $out.newestClassFile = [ordered]@{
                path  = $newestClass.FullName
                mtime = $newestClass.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
            }
        }
    }
}

# 3) run\plugins listing
# eng-verifier pre-check: directory entries (bStats/spark config dirs) do NOT prove
# which plugin jars were LOADED, so they are split into two fields and annotated.
$pluginsDir = Join-Path $runDir 'plugins'
if (Test-Path $pluginsDir) {
    $out.pluginsDir = $pluginsDir
    $out.pluginsDirEntries = @(Get-ChildItem $pluginsDir -Force -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name)
    $out.pluginJars = @(Get-ChildItem $pluginsDir -Force -Filter *.jar -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name)
    $out.pluginsNote = 'pluginsDirEntries = directory entries only (includes config dirs such as bStats/spark); pluginJars = *.jar actually present. NEITHER proves which plugins were LOADED - use the server log for that (e.g. grep "Enabling <Plugin>").'
}

# 4) java processes with command lines (to tell Gradle daemon from the Paper server)
$javaProcs = @(Get-Process -Name java -ErrorAction SilentlyContinue)
$cmdByPid = @{}
try {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        $cmdByPid[[int]$_.ProcessId] = $_.CommandLine
    }
} catch { }
$out.javaProcesses = @($javaProcs | ForEach-Object {
    $cmd = $cmdByPid[[int]$_.Id]
    $isServer = $false
    if ($cmd -and ($cmd -match 'paper|PaperClip|bundler|run\\')) { $isServer = $true }
    [ordered]@{
        Id           = $_.Id
        StartTime    = $_.StartTime.ToString('yyyy-MM-dd HH:mm:ss')
        WorkingSetMB = [int]($_.WorkingSet64 / 1MB)
        looksLikeServer = $isServer
        commandLineHead = if ($cmd) { $cmd.Substring(0, [Math]::Min(220, $cmd.Length)) } else { $null }
    }
})

# 4b) authoritative JVM identity via jcmd (CIM CommandLine is often unavailable here)
$jcmd = 'C:\Program Files\Java\jdk-21\bin\jcmd.exe'
if (Test-Path $jcmd) {
    $out.jcmdL = @(& $jcmd -l 2>&1 | ForEach-Object { "$_" })
} else {
    $out.jcmdL = "(jcmd not found at $jcmd)"
}

$latest = Join-Path $runDir 'logs\latest.log'
if (Test-Path $latest) {
    $li = Get-Item $latest
    $out.latestLog = [ordered]@{
        path  = $li.FullName
        bytes = $li.Length
        mtime = $li.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
    }
}

# 5) server.properties anchors (port / online-mode)
$props = Join-Path $runDir 'server.properties'
if (Test-Path $props) {
    $out.serverProperties = @(Select-String -Path $props -Pattern '^(server-port|online-mode|gamemode)=' |
        ForEach-Object { $_.Line })
}

$json = $out | ConvertTo-Json -Depth 6
Write-Output $json
$dest = Join-Path $PSScriptRoot 'runserver-identity.json'
# BOM-FREE on purpose: PowerShell 5.1's `Out-File -Encoding UTF8` writes a BOM, which
# makes strict JSON readers (e.g. Node's JSON.parse) fail. Write UTF-8 without BOM.
[System.IO.File]::WriteAllText($dest, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ""
Write-Output ("written -> " + $dest)
