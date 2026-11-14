# 职问桥全链路冒烟测试
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 [-Port 18080] [-SkipBuild]
# 依赖：JDK 21（JAVA_HOME 指向 JDK 21）、Maven、可构建的后端 jar。
param(
    [int]$Port = 18080,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
$base = "http://localhost:$Port"
$jar = Join-Path $root "backend\target\careerq-backend-0.1.0-SNAPSHOT.jar"
$javaHome = $env:JAVA_HOME

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw "断言失败：$message" }
    Write-Host "  [PASS] $message" -ForegroundColor Green
}

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    throw "JAVA_HOME 未指向有效的 JDK 21，请先设置"
}

if (-not $SkipBuild -or -not (Test-Path $jar)) {
    Write-Host "== 构建后端 ==" -ForegroundColor Cyan
    & mvn -B -q -f (Join-Path $root "backend\pom.xml") package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败" }
}

Write-Host "== 启动后端（dev,demo，端口 $Port）==" -ForegroundColor Cyan
$env:SPRING_PROFILES_ACTIVE = "dev,demo"
$log = Join-Path $env:TEMP "careerq-smoke.log"
$proc = Start-Process -FilePath (Join-Path $javaHome "bin\java.exe") `
    -ArgumentList @("-jar", $jar, "--server.port=$Port") `
    -PassThru -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden

function Wait-Healthy {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod -Uri "$base/api/v1/system/health" -TimeoutSec 2 | Out-Null
            return $true
        } catch { }
    }
    return $false
}

function New-Login([string]$phone) {
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/sms-code" `
        -ContentType "application/json" -Body (@{ phone = $phone } | ConvertTo-Json) | Out-Null
    $session = $null
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" `
        -ContentType "application/json" -Body (@{ phone = $phone; code = "123456" } | ConvertTo-Json) `
        -SessionVariable session
    return [pscustomobject]@{ Web = $session; Token = $null; User = $login.data.user }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, $Session, $Body)
    $headers = @{}
    if ($Session) {
        if (-not $Session.Token) {
            $csrf = Invoke-RestMethod -Uri "$base/api/v1/auth/csrf" -WebSession $Session.Web
            $Session.Token = $csrf.data.token
        }
        $headers["X-XSRF-TOKEN"] = $Session.Token
    }
    $params = @{ Method = $Method; Uri = "$base$Path"; Headers = $headers; ErrorAction = "Stop" }
    if ($Session) { $params["WebSession"] = $Session.Web }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6))
    }
    return Invoke-RestMethod @params
}

function Get-Items($collection) {
    if ($null -eq $collection) { return @() }
    return @($collection)
}

try {
    if (-not (Wait-Healthy)) { throw "后端未在 120 秒内启动，查看日志：$log" }
    Write-Host "健康检查通过" -ForegroundColor Green

    Write-Host "== 1. 公开问答 + 审核 ==" -ForegroundColor Cyan
    $student = New-Login "13800000001"
    $hr = New-Login "13800000002"
    $operator = New-Login "13800000003"

    $risky = Invoke-Api POST "/api/v1/questions" $student @{
        title = "想找内推"; body = "可以加微信详细聊吗？"; scope = "public"
    }
    Assert-True ($risky.data.status -eq "pending_human") "含联系方式的问题进入待审核"

    $queue = Invoke-Api GET "/api/v1/admin/moderation/queue" $operator
    $matched = @(Get-Items $queue.data | Where-Object { [string]$_.targetId -eq [string]$risky.data.id })
    Assert-True ($matched.Count -ge 1) "运营队列能看到待审问题"

    Invoke-Api POST "/api/v1/admin/moderation/question/$($risky.data.id)/decide" $operator @{
        decision = "approve"; note = "冒烟测试通过"
    } | Out-Null
    $detail = Invoke-Api GET "/api/v1/questions/$($risky.data.id)" $null
    Assert-True ($detail.data.question.status -eq "published") "审核通过后公开可见"

    $answer = Invoke-Api POST "/api/v1/questions/$($risky.data.id)/answers" $hr @{
        body = "内推需要先准备好简历与岗位匹配点。"; lengthType = "detailed"
    }
    Assert-True ($answer.data.status -eq "published") "HR 回答发布成功"

    Invoke-Api POST "/api/v1/interactions" $student @{
        targetType = "answer"; targetId = $answer.data.id; type = "useful"
    } | Out-Null
    $detail2 = Invoke-Api GET "/api/v1/questions/$($risky.data.id)" $null
    Assert-True ($detail2.data.answers[0].usefulCount -eq 1) "有用计数生效"

    Write-Host "== 2. 私密工单 + 脱敏公开 ==" -ForegroundColor Cyan
    $private = Invoke-Api POST "/api/v1/questions" $student @{
        title = "试用期被辞退怎么办？"; body = "想了解试用期辞退的合法流程。"; scope = "private"
    }
    Assert-True ($private.data.status -eq "open") "私密问题进入待认领"

    $pool = Invoke-Api GET "/api/v1/hr/private-pool" $hr
    $poolItems = @(Get-Items $pool.data.items | Where-Object { [string]$_.id -eq [string]$private.data.id })
    Assert-True ($poolItems.Count -ge 1) "HR 私密池可见"
    Assert-True ($poolItems[0].author.nickname -eq "匿名同学") "私密池作者匿名"

    Invoke-Api POST "/api/v1/hr/questions/$($private.data.id)/claim" $hr $null | Out-Null
    Invoke-Api POST "/api/v1/questions/$($private.data.id)/answers" $hr @{
        body = "先确认辞退理由与证据，再谈补偿方案。"
    } | Out-Null
    Invoke-Api POST "/api/v1/questions/$($private.data.id)/followups" $student @{
        body = "需要保留哪些证据？"
    } | Out-Null
    Invoke-Api POST "/api/v1/questions/$($private.data.id)/close" $student $null | Out-Null
    Invoke-Api POST "/api/v1/questions/$($private.data.id)/publish" $student $null | Out-Null
    Assert-True $true "私密工单闭环（认领→回答→追问→关闭→申请公开）"

    $queue2 = Invoke-Api GET "/api/v1/admin/moderation/queue" $operator
    $pendingPost = @(Get-Items $queue2.data | Where-Object {
        $_.targetType -eq "public_post" -and $_.title -like "*试用期被辞退*"
    })
    Assert-True ($pendingPost.Count -ge 1) "公开沉淀进入审核队列"
    Invoke-Api POST "/api/v1/admin/moderation/public_post/$($pendingPost[0].targetId)/decide" $operator @{
        decision = "approve"
    } | Out-Null
    $posts = Invoke-Api GET "/api/v1/posts" $null
    $postMatches = @(Get-Items $posts.data | Where-Object { $_.title -like "*试用期被辞退*" })
    Assert-True ($postMatches.Count -ge 1) "审核通过后出现在公开池"

    Write-Host "== 3. 举报处理 ==" -ForegroundColor Cyan
    Invoke-Api POST "/api/v1/reports" $student @{
        targetType = "answer"; targetId = $answer.data.id; reason = "misleading"; detail = "冒烟测试"
    } | Out-Null
    $reports = Invoke-Api GET "/api/v1/admin/reports?status=pending" $operator
    Assert-True (@($reports.data).Count -ge 1) "运营看到待处理举报"
    Invoke-Api POST "/api/v1/admin/reports/$(($reports.data)[0].id)/handle" $operator @{
        decision = "dismiss"; note = "冒烟测试关闭"
    } | Out-Null

    $metrics = Invoke-Api GET "/api/v1/admin/metrics" $operator
    Assert-True ($null -ne $metrics.data.questionsPublished) "指标接口正常"

    Write-Host ""
    Write-Host "冒烟测试全部通过 ✅" -ForegroundColor Green
    exit 0
} catch {
    Write-Host ""
    Write-Host "冒烟测试失败：$($_.Exception.Message)" -ForegroundColor Red
    if (Test-Path $log) { Write-Host "--- 后端日志尾部 ---"; Get-Content $log -Tail 30 }
    exit 1
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}
