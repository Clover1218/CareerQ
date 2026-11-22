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
$jar = (Get-ChildItem (Join-Path $root "backend") -Recurse -Filter "careerq-backend-0.1.0-SNAPSHOT.jar" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "\\classes\\" } | Select-Object -First 1).FullName
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
    $session = [pscustomobject]@{ Web = (New-Object Microsoft.PowerShell.Commands.WebRequestSession); Token = $null; User = $null }
    Invoke-Api -Method Post -Path "/api/v1/auth/sms-code" -Session $session -Body @{ phone = $phone } | Out-Null
    $login = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Session $session -Body @{ phone = $phone; code = "123456" }
    $session.User = $login.data.user
    $session.Token = $null
    return $session
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

function Assert-ApiStatus([string]$method, [string]$path, $session, $body, [int]$expectedStatus, [string]$message) {
    try {
        Invoke-Api -Method $method -Path $path -Session $session -Body $body | Out-Null
    } catch {
        if ($_.Exception.Response) {
            Assert-True ([int]$_.Exception.Response.StatusCode -eq $expectedStatus) $message
            return
        }
        throw
    }
    throw "断言失败：$message（接口意外成功）"
}

try {
    if (-not (Wait-Healthy)) { throw "后端未在 120 秒内启动，查看日志：$log" }
    Write-Host "健康检查通过" -ForegroundColor Green

    $newStudent = [pscustomobject]@{ Web = (New-Object Microsoft.PowerShell.Commands.WebRequestSession); Token = $null }
    Invoke-Api POST "/api/v1/auth/sms-code" $newStudent @{ phone = "13800000009" } | Out-Null
    $registration = Invoke-Api POST "/api/v1/auth/register" $newStudent @{ phone = "13800000009"; code = "123456" }
    $me = Invoke-Api GET "/api/v1/me" $newStudent
    Assert-True ($me.data.id -eq $registration.data.user.id -and $me.data.role -eq "student") "注册后可使用会话访问当前用户"
    Invoke-Api POST "/api/v1/auth/logout" $newStudent | Out-Null
    $signedIn = New-Login "13800000009"
    Assert-True ($signedIn.User.id -eq $registration.data.user.id) "注册账号可退出并重新登录"

    Write-Host "== 1. 公开问答 + 审核 ==" -ForegroundColor Cyan
    $student = New-Login "13800000001"
    $hr = New-Login "13800000002"
    $operator = New-Login "13800000003"

    Assert-ApiStatus POST "/api/v1/questions" $student @{
        title = "非法范围验证"; body = "只允许公开问题"; scope = "private"
    } 400 "非公开范围的提问被拒绝"
    foreach ($legacy in @(
        @{ Method = "GET"; Path = "/api/v1/hr/private-pool" },
        @{ Method = "POST"; Path = "/api/v1/hr/questions/1/claim" },
        @{ Method = "POST"; Path = "/api/v1/questions/1/close" },
        @{ Method = "POST"; Path = "/api/v1/questions/1/publish" },
        @{ Method = "GET"; Path = "/api/v1/posts" },
        @{ Method = "GET"; Path = "/api/v1/feed/highlights" }
    )) {
        Assert-ApiStatus $legacy.Method $legacy.Path $hr $null 404 "旧接口已移除：$($legacy.Path)"
    }

    $risky = Invoke-Api POST "/api/v1/questions" $student @{
        title = "想找内推"; body = "可以加微信详细聊吗？"; scope = "public"
    }
    Assert-True ($risky.data.displayStatus -eq "REVIEWING") "含联系方式的问题进入待审核"

    $queue = Invoke-Api GET "/api/v1/admin/moderation/queue" $operator
    $matched = @(Get-Items $queue.data.items | Where-Object { [string]$_.targetId -eq [string]$risky.data.id })
    Assert-True ($matched.Count -ge 1) "运营队列能看到待审问题"

    Invoke-Api POST "/api/v1/admin/moderation/question/$($risky.data.id)/decide" $operator @{
        decision = "approve"; note = "冒烟测试通过"
    } | Out-Null
    $detail = Invoke-Api GET "/api/v1/questions/$($risky.data.id)" $null
    Assert-True ($detail.data.question.displayStatus -eq "PUBLISHED") "审核通过后公开可见"

    $answer = Invoke-Api POST "/api/v1/questions/$($risky.data.id)/answers" $hr @{
        body = "内推需要先准备好简历与岗位匹配点。"; lengthType = "detailed"
    }
    if ($answer.data.displayStatus -ne "PUBLISHED") {
        Invoke-Api POST "/api/v1/admin/moderation/answer/$($answer.data.id)/decide" $operator @{
            decision = "approve"; note = "冒烟测试通过"
        } | Out-Null
    }
    $detailAfterAnswer = Invoke-Api GET "/api/v1/questions/$($risky.data.id)" $null
    Assert-True ($detailAfterAnswer.data.answers[0].id -eq $answer.data.id) "HR 回答发布成功"

    Invoke-Api POST "/api/v1/interactions" $student @{
        targetType = "answer"; targetId = $answer.data.id; type = "useful"
    } | Out-Null
    $detail2 = Invoke-Api GET "/api/v1/questions/$($risky.data.id)" $null
    Assert-True ($detail2.data.answers[0].usefulCount -eq 1) "有用计数生效"

    Write-Host "== 1.1 撤销、审核次数与仲裁 ==" -ForegroundColor Cyan
    $arbiter = New-Login "13800000007"
    $operatorB = New-Login "13800000008"
    $undoTarget = Invoke-Api POST "/api/v1/questions" $student @{
        title = "撤销与审核次数验证"; body = "撤销用例，请加微信 smoke-undo-001 联系"; scope = "public"
    }
    Assert-True ($undoTarget.data.displayStatus -eq "REVIEWING") "新问题进入待审核"

    Invoke-Api POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/decide" $operator @{
        decision = "approve"; note = "撤销前先通过"
    } | Out-Null
    $reviewed = Invoke-Api GET "/api/v1/admin/moderation/reviewed?page=1&size=200" $operator
    $reviewedHit = @(Get-Items $reviewed.data.items | Where-Object {
        $_.targetType -eq "question" -and [string]$_.targetId -eq [string]$undoTarget.data.id
    })
    Assert-True ($reviewedHit.Count -eq 1 -and $reviewedHit[0].canUndo) "本人审核记录可撤销"

    Invoke-Api POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/undo" $operator @{
        note = "冒烟测试撤销"
    } | Out-Null
    $queueAfterUndo = Invoke-Api GET "/api/v1/admin/moderation/queue?page=1&size=200" $operator
    $requeued = @(Get-Items $queueAfterUndo.data.items | Where-Object {
        $_.targetType -eq "question" -and [string]$_.targetId -eq [string]$undoTarget.data.id
    })
    Assert-True ($requeued.Count -eq 1 -and $requeued[0].reviewAttempts -eq 1) "撤销后回到待审且保留 1 次审核"

    $reviewedAfterUndo = Invoke-Api GET "/api/v1/admin/moderation/reviewed?page=1&size=200" $operator
    $stillReviewed = @(Get-Items $reviewedAfterUndo.data.items | Where-Object {
        $_.targetType -eq "question" -and [string]$_.targetId -eq [string]$undoTarget.data.id
    })
    Assert-True ($stillReviewed.Count -eq 0) "撤销后不再出现在“我审核的”"
    Assert-ApiStatus POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/decide" $operator @{
        decision = "reject"; note = "撤销人再审"
    } 403 "撤销人不能再审核同一内容"

    Invoke-Api POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/decide" $operatorB @{
        decision = "reject"; note = "第二次人工审核"
    } | Out-Null
    Invoke-Api POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/undo" $operatorB @{
        note = "第二次审核后撤销，进入待仲裁"
    } | Out-Null

    $arbiterQueue = Invoke-Api GET "/api/v1/admin/moderation/queue?page=1&size=200" $arbiter
    $arbitrationHit = @(Get-Items $arbiterQueue.data.items | Where-Object {
        $_.targetType -eq "question" -and [string]$_.targetId -eq [string]$undoTarget.data.id
    })
    Assert-True ($arbitrationHit.Count -eq 1 -and
        $arbitrationHit[0].arbitrationRequired -and
        $arbitrationHit[0].reviewAttempts -eq 2 -and
        $arbitrationHit[0].displayStatus -eq "ARBITRATING") "两次撤销后进入待仲裁，仲裁员可见"
    Assert-ApiStatus POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/decide" $operatorB @{
        decision = "approve"; note = "重复审核"
    } 403 "已审过的人不能再审核同一内容"

    $operatorQueue = Invoke-Api GET "/api/v1/admin/moderation/queue?page=1&size=200" $operator
    $hiddenForOperator = @(Get-Items $operatorQueue.data.items | Where-Object {
        $_.targetType -eq "question" -and [string]$_.targetId -eq [string]$undoTarget.data.id
    })
    Assert-True ($hiddenForOperator.Count -eq 0) "非仲裁员看不到待仲裁内容"

    $metrics = Invoke-Api GET "/api/v1/admin/metrics" $operator
    Assert-True ($metrics.data.arbitrationPending -ge 1) "指标返回待仲裁数量"

    Invoke-Api POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/decide" $arbiter @{
        decision = "approve"; note = "仲裁通过"
    } | Out-Null
    $finalDetail = Invoke-Api GET "/api/v1/admin/moderation/question/$($undoTarget.data.id)" $arbiter
    $arbitrateRecord = @($finalDetail.data.records | Where-Object {
        $_.stage -eq "ARBITRATE" -and $_.decision -eq "approve"
    })
    Assert-True ($finalDetail.data.item.displayStatus -eq "PUBLISHED" -and
        $finalDetail.data.item.reviewAttempts -eq 3 -and
        $arbitrateRecord.Count -eq 1) "仲裁裁定写 ARBITRATE 记录并终局"

    $arbiterReviewed = Invoke-Api GET "/api/v1/admin/moderation/reviewed?page=1&size=200" $arbiter
    $arbiterReviewedHit = @(Get-Items $arbiterReviewed.data.items | Where-Object {
        $_.targetType -eq "question" -and [string]$_.targetId -eq [string]$undoTarget.data.id
    })
    Assert-True ($arbiterReviewedHit.Count -eq 1 -and -not $arbiterReviewedHit[0].canUndo) "仲裁裁定不提供撤销"
    Assert-ApiStatus POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/undo" $arbiter @{
        note = "仲裁后撤销"
    } 403 "仲裁裁定不可撤销"
    Assert-ApiStatus POST "/api/v1/admin/moderation/question/$($undoTarget.data.id)/undo" $operatorB @{
        note = "覆盖仲裁"
    } 403 "仲裁终局后原审核人也不能撤销"

    $earlyTarget = Invoke-Api POST "/api/v1/questions" $student @{
        title = "仲裁首审即终局验证"; body = "首审仲裁用例，请加微信 smoke-undo-002 联系"; scope = "public"
    }
    Invoke-Api POST "/api/v1/admin/moderation/question/$($earlyTarget.data.id)/decide" $arbiter @{
        decision = "reject"; note = "仲裁员首审"
    } | Out-Null
    $earlyDetail = Invoke-Api GET "/api/v1/admin/moderation/question/$($earlyTarget.data.id)" $arbiter
    $earlyArbitrate = @($earlyDetail.data.records | Where-Object { $_.stage -eq "ARBITRATE" })
    Assert-True ($earlyDetail.data.item.reviewAttempts -eq 1 -and
        $earlyArbitrate.Count -eq 1) "仲裁运营首审也按仲裁记录并终局"
    Assert-ApiStatus POST "/api/v1/admin/moderation/question/$($earlyTarget.data.id)/undo" $arbiter @{
        note = "首审仲裁后撤销"
    } 403 "首审仲裁裁定不可撤销"
    Assert-ApiStatus POST "/api/v1/admin/moderation/question/$($earlyTarget.data.id)/decide" $operator @{
        decision = "approve"; note = "终局后改判"
    } 409 "仲裁终局后不可改判"

    Write-Host "== 2. HR 工作台 + 回复学生追问 ==" -ForegroundColor Cyan
    $studentFollowup = Invoke-Api POST "/api/v1/questions/$($risky.data.id)/followups" $student @{
        hrId = $hr.User.id; body = "准备简历时，应该怎样突出岗位匹配度？"
    }
    if ($studentFollowup.data.displayStatus -ne "PUBLISHED") {
        Invoke-Api POST "/api/v1/admin/moderation/followup/$($studentFollowup.data.id)/decide" $operator @{
            decision = "approve"; note = "冒烟测试通过"
        } | Out-Null
    }
    $followupAnswer = Invoke-Api POST "/api/v1/questions/$($risky.data.id)/followups/$($studentFollowup.data.id)/answers" $hr @{
        body = "按岗位要求逐条对应经历，并用结果说明影响。"; lengthType = "detailed"
    }
    if ($followupAnswer.data.displayStatus -ne "PUBLISHED") {
        Invoke-Api POST "/api/v1/admin/moderation/answer/$($followupAnswer.data.id)/decide" $operator @{
            decision = "approve"; note = "冒烟测试通过"
        } | Out-Null
    }
    Assert-True ($followupAnswer.data.followupId -eq $studentFollowup.data.id) "HR 回复指定学生追问"

    $todo = Invoke-Api GET "/api/v1/hr/question-pool?limit=50" $hr
    $todoMatch = @(Get-Items $todo.data.items | Where-Object { [string]$_.id -eq [string]$risky.data.id })
    Assert-True ($todoMatch.Count -eq 0) "任一 HR 回答后问题从待回答列表移出"
    $allAnswered = Invoke-Api GET "/api/v1/hr/answered-public-questions?limit=50" $hr
    $answeredMatch = @(Get-Items $allAnswered.data.items | Where-Object {
        [string]$_.question.id -eq [string]$risky.data.id
    })
    Assert-True ($answeredMatch.Count -eq 1) "已回答公开问题列表包含该问题"
    $myAnswered = Invoke-Api GET "/api/v1/hr/answered-questions?limit=50" $hr
    $myMatch = @(Get-Items $myAnswered.data.items | Where-Object {
        [string]$_.question.id -eq [string]$risky.data.id
    })
    Assert-True ($myMatch.Count -eq 1 -and $myMatch[0].myAnswer.followupId -eq $studentFollowup.data.id) "我回答过的列表包含追问回复"
    $thread = Invoke-Api GET "/api/v1/questions/$($risky.data.id)/thread" $student
    $replyMatch = @(Get-Items $thread.data.items | Where-Object {
        $_.kind -eq "followup_answer" -and $_.followupId -eq $studentFollowup.data.id
    })
    Assert-True ($replyMatch.Count -eq 1) "公开时间线展示追问回复"

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
