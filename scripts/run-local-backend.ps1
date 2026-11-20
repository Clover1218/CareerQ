# 在独立运行目录启动本机开发后端；先执行 mvn -f backend/pom.xml package -DskipTests。
param([int]$Port = 8080)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sourceJar = Join-Path $root 'backend/shared.all/product.shared/product.service.impl/target/careerq-backend-0.1.0-SNAPSHOT.jar'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw '请将 JAVA_HOME 设置为 JDK 21。' }
if (-not (Test-Path -LiteralPath $sourceJar)) { throw '缺少后端程序包，请先完成 Maven 打包。' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($sourceJar)
try {
    if (-not $archive.GetEntry('BOOT-INF/classes/com/careerq/CareerQApplication.class')) {
        throw '程序包不完整，请重新打包成功后再启动。'
    }
} finally { $archive.Dispose() }

$client = [Net.Sockets.TcpClient]::new()
try {
    try { $client.Connect('127.0.0.1', $Port) } catch [Net.Sockets.SocketException] { }
    if ($client.Connected) { throw "端口 $Port 已被占用，请先关闭已有服务。" }
} finally { $client.Dispose() }

$runtime = Join-Path $root 'data/local-runtime'
$release = Join-Path $runtime ('releases/' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss-fff'))
$database = Join-Path $runtime 'database/careerq'
New-Item -ItemType Directory -Path $release, (Split-Path -Parent $database) -Force | Out-Null
$runtimeJar = Join-Path $release 'careerq-backend.jar'
Copy-Item -LiteralPath $sourceJar -Destination $runtimeJar
if ((Get-FileHash -LiteralPath $sourceJar).Hash -ne (Get-FileHash -LiteralPath $runtimeJar).Hash) {
    throw '运行包复制校验失败，停止启动。'
}

$variables = @('SPRING_PROFILES_ACTIVE', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD', 'SPRING_DATASOURCE_DRIVER_CLASS_NAME')
$saved = @{}
foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$previousDirectory = Get-Location
try {
    $env:SPRING_PROFILES_ACTIVE = 'dev,demo'
    $env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:' + ($database -replace '\\', '/') + ';MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE'
    $env:SPRING_DATASOURCE_USERNAME = 'sa'
    $env:SPRING_DATASOURCE_PASSWORD = ''
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
    Set-Location -LiteralPath $root
    Write-Host "启动本地开发后端：端口 $Port；数据保存在 data/local-runtime/database，按 Ctrl+C 停止。"
    & $java -jar $runtimeJar "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "后端退出，状态码 $LASTEXITCODE。" }
} finally {
    Set-Location -LiteralPath $previousDirectory.Path
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
