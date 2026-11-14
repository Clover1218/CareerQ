param(
    [int]$Port = 18081,
    [string]$Out = "docs/openapi.json"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "backend\target\careerq-backend-0.1.0-SNAPSHOT.jar"
$javaHome = $env:JAVA_HOME

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    throw "JAVA_HOME 未指向有效的 JDK 21"
}
if (-not (Test-Path $jar)) {
    throw "未找到 $jar，请先执行 mvn -f backend/pom.xml package -DskipTests"
}

$outPath = Join-Path $root $Out
$log = Join-Path $env:TEMP "careerq-openapi.log"
$proc = Start-Process -FilePath (Join-Path $javaHome "bin\java.exe") `
    -ArgumentList @("-jar", $jar, "--server.port=$Port") `
    -PassThru -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden

try {
    $up = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod "http://localhost:$Port/api/v1/system/health" -TimeoutSec 2 | Out-Null
            $up = $true
            break
        } catch { }
    }
    if (-not $up) { throw "后端未启动，日志：$log" }

    $json = Invoke-RestMethod "http://localhost:$Port/v3/api-docs" -TimeoutSec 30
    $json | ConvertTo-Json -Depth 100 | Set-Content -Path $outPath -Encoding UTF8
    Write-Host "已导出 OpenAPI 到 $outPath"
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}
