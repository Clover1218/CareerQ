<#
.SYNOPSIS
  在真实 MySQL 上验证职问桥：Flyway 迁移 + 全链路冒烟。

.DESCRIPTION
  需要先设置以下环境变量（不要把密码写进脚本或仓库）：
    SPRING_DATASOURCE_URL      例：jdbc:mysql://host:3306/careerq?serverTimezone=GMT%2B8&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true
    SPRING_DATASOURCE_USERNAME
    SPRING_DATASOURCE_PASSWORD

.EXAMPLE
  $env:SPRING_DATASOURCE_URL='jdbc:mysql://.../careerq?...'
  $env:SPRING_DATASOURCE_USERNAME='careerq'
  $env:SPRING_DATASOURCE_PASSWORD='<密码>'
  powershell -ExecutionPolicy Bypass -File scripts/verify-mysql.ps1
#>
param(
    [int]$Port = 18080,
    [switch]$SkipBuild
)

$required = @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD')
foreach ($name in $required) {
    if (-not [Environment]::GetEnvironmentVariable($name)) {
        Write-Host "缺少环境变量 $name（不要把密码写进脚本/仓库）" -ForegroundColor Red
        exit 1
    }
}

$dbUrl = $env:SPRING_DATASOURCE_URL
$masked = $dbUrl -replace 'password=[^&]*', 'password=***'
Write-Host "目标数据库：$masked" -ForegroundColor Cyan
Write-Host "确认这是独立库（不要用带有其他项目表的库，Flyway 会拒绝非空且无迁移历史的库）" -ForegroundColor Yellow

$smoke = Join-Path $PSScriptRoot 'smoke-test.ps1'
$argsList = @('-Port', $Port)
if ($SkipBuild) { $argsList += '-SkipBuild' }

& $smoke @argsList
exit $LASTEXITCODE