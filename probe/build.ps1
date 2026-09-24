# 一条命令重建探针（工装件 · t129）
#   ① 主工程出 jar（探针 compileOnly 引用它的类型面）
#   ② 独立构建出探针 jar（probe/ 自带 settings.gradle.kts ⇒ 与主工程隔离）
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)   # ShadowHunterRoles/
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-work'
Push-Location $root
try {
    Write-Host '=== [1/2] 主工程 jar ===' -ForegroundColor Cyan
    & .\gradlew jar --console=plain
    if ($LASTEXITCODE -ne 0) { throw "main jar failed: exit $LASTEXITCODE" }

    Write-Host '=== [2/2] 探针 jar（独立构建） ===' -ForegroundColor Cyan
    & .\gradlew -p probe jar --console=plain
    if ($LASTEXITCODE -ne 0) { throw "probe jar failed: exit $LASTEXITCODE" }

    $out = Join-Path $root 'probe\build\libs\ShadowHunterProbe-1.0.0.jar'
    if (-not (Test-Path $out)) { throw "probe jar not produced: $out" }
    $i = Get-Item $out
    Write-Host ("=== OK: " + $i.FullName + " = " + $i.Length + " B / " + $i.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss.fff') + " ===") -ForegroundColor Green
}
finally {
    Pop-Location
}
