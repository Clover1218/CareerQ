<#
.SYNOPSIS
  一键重启 CareerQ 本地开发环境（后端 dev,demo + 前端 Vite）。
.DESCRIPTION
  - 停止占用端口的旧进程；
  - 如需要可重新打包后端；
  - 启动后端与前端，等待健康检查，打印访问地址。
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/dev-start.ps1
  powershell -ExecutionPolicy Bypass -File scripts/dev-start.ps1 -Build
#>
param(
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

Write-Host "== 清理旧进程 ==" -ForegroundColor Cyan
foreach ($port in @($BackendPort, $FrontendPort)) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $connections) {
        try {
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "  已停止端口 $port (PID $($c.OwningProcess))"
        } catch { }
    }
}

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
    $env:JAVA_HOME = $javaHome
}
$env:Path = "$javaHome\bin;$env:Path"

$jar = Get-ChildItem (Join-Path $root 'backend') -Recurse -Filter 'careerq-backend-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '\.original' } | Select-Object -First 1
if ($Build -or -not $jar) {
    Write-Host "== 打包后端 ==" -ForegroundColor Cyan
    & mvn -B -q -f (Join-Path $root 'backend\pom.xml') package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败" }
    $jar = Get-ChildItem (Join-Path $root 'backend') -Recurse -Filter 'careerq-backend-*.jar' |
        Where-Object { $_.FullName -notmatch '\.original' } | Select-Object -First 1
}

Write-Host "== 启动后端 :$BackendPort ==" -ForegroundColor Cyan
$env:SPRING_PROFILES_ACTIVE = 'dev,demo'
$backend = Start-Process -FilePath (Join-Path $javaHome 'bin\java.exe') `
    -ArgumentList @('-jar', $jar.FullName, "--server.port=$BackendPort") `
    -WorkingDirectory $root -PassThru

Write-Host "== 启动前端 :$FrontendPort ==" -ForegroundColor Cyan
$frontend = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList @('/c', "cd /d `"$root\frontend`" && npm run dev -- --host 127.0.0.1 --port $FrontendPort") `
    -PassThru

Write-Host "== 等待后端健康检查 ==" -ForegroundColor Cyan
$healthy = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:$BackendPort/api/v1/system/health" -TimeoutSec 3
        if ($r.code -eq 0) { $healthy = $true; break }
    } catch { }
}
if (-not $healthy) { throw "后端启动失败，请检查端口/日志" }

Write-Host ""
Write-Host "✅ 已启动：" -ForegroundColor Green
Write-Host "   前端: http://127.0.0.1:$FrontendPort/careerq/"
Write-Host "   登录: http://127.0.0.1:$FrontendPort/careerq/login"
Write-Host "   注册: http://127.0.0.1:$FrontendPort/careerq/register"
Write-Host "   后端: http://localhost:$BackendPort/api/v1/system/health"
Write-Host "   演示账号: 13800000001(学生) / 13800000002(HR) / 13800000003(运营)，验证码 123456"
Write-Host ""
Start-Process "http://127.0.0.1:$FrontendPort/careerq/"