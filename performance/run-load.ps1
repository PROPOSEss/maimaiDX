#requires -Version 7
param(
    [ValidateSet("charts", "assistant", "import-sync", "import-async")]
    [string]$Scenario,
    [string]$BaseUrl = "http://localhost:8080/api",
    [long]$UserId = 999,
    [string]$SongId = "mvp001",
    [int]$Vus = 5,
    [int]$DurationSeconds = 10,
    [int]$MaxRequests = 0,
    [ValidateSet(20, 50, 100)]
    [int]$ImportRecords = 20
)

$ErrorActionPreference = "Stop"

function New-ImportBody {
    param(
        [string]$RequestId,
        [int]$Count
    )
    $records = for ($i = 0; $i -lt $Count; $i++) {
        [ordered]@{
            songId = "mvp001"
            difficulty = @(2, 3, 4)[$i % 3]
            achievement = [Math]::Round(98.5 + (($i * 37) % 240) / 100, 2)
            dxScore = 950000 + (($i * 7919) % 60000)
            rate = "SSS"
            fc = ""
            fs = ""
        }
    }
    return ([ordered]@{
        requestId = $RequestId
        source = "perf_load"
        rating = 12345
        records = $records
    } | ConvertTo-Json -Depth 10 -Compress)
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    if ($Values.Count -eq 0) { return 0 }
    $sorted = $Values | Sort-Object
    $index = [Math]::Ceiling(($Percentile / 100) * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))
    return [Math]::Round($sorted[$index], 2)
}

$endAt = [DateTime]::UtcNow.AddSeconds($DurationSeconds)
$startedAt = [DateTime]::UtcNow
$workItems = if ($MaxRequests -gt 0) { 1..$MaxRequests } else { 1..$Vus }
$normalizedBaseUrl = $BaseUrl.TrimEnd("/")

$results = $workItems | ForEach-Object -Parallel {
    $scenario = $using:Scenario
    $baseUrl = $using:normalizedBaseUrl
    $userId = $using:UserId
    $songId = $using:SongId
    $endAt = $using:endAt
    $importRecords = $using:ImportRecords
    $maxRequests = $using:MaxRequests
    $localResults = New-Object System.Collections.Generic.List[object]

    do {
        if ($maxRequests -le 0 -and [DateTime]::UtcNow -gt $endAt) {
            break
        }

        $requestId = "perf-load-$scenario-$importRecords-$([Guid]::NewGuid().ToString('N'))"
        $method = "GET"
        $url = "$baseUrl/charts/$songId"
        $body = $null
        $contentType = $null
        $headers = $null

        if ($scenario -eq "assistant") {
            $method = "POST"
            $url = "$baseUrl/assistant/query"
            $body = @{ message = "查看我最近20条成绩" } | ConvertTo-Json -Compress
            $contentType = "application/json; charset=utf-8"
            $headers = @{ "X-User-Id" = "$userId" }
        } elseif ($scenario -eq "import-sync") {
            $method = "POST"
            $url = "$baseUrl/player/import?userId=$userId"
            $records = for ($i = 0; $i -lt $importRecords; $i++) {
                [ordered]@{
                    songId = "mvp001"
                    difficulty = @(2, 3, 4)[$i % 3]
                    achievement = [Math]::Round(98.5 + (($i * 37) % 240) / 100, 2)
                    dxScore = 950000 + (($i * 7919) % 60000)
                    rate = "SSS"
                    fc = ""
                    fs = ""
                }
            }
            $body = ([ordered]@{ source = "perf_load"; rating = 12345; records = $records } | ConvertTo-Json -Depth 10 -Compress)
            $contentType = "application/json; charset=utf-8"
        } elseif ($scenario -eq "import-async") {
            $method = "POST"
            $url = "$baseUrl/player/import/async?userId=$userId"
            $records = for ($i = 0; $i -lt $importRecords; $i++) {
                [ordered]@{
                    songId = "mvp001"
                    difficulty = @(2, 3, 4)[$i % 3]
                    achievement = [Math]::Round(98.5 + (($i * 37) % 240) / 100, 2)
                    dxScore = 950000 + (($i * 7919) % 60000)
                    rate = "SSS"
                    fc = ""
                    fs = ""
                }
            }
            $body = ([ordered]@{ requestId = $requestId; source = "perf_load"; rating = 12345; records = $records } | ConvertTo-Json -Depth 10 -Compress)
            $contentType = "application/json; charset=utf-8"
        }

        $sw = [Diagnostics.Stopwatch]::StartNew()
        $statusCode = 0
        $ok = $false
        $taskId = $null
        $terminalStatus = $null
        $totalMs = $null
        try {
            $params = @{ Method = $method; Uri = $url; SkipHttpErrorCheck = $true }
            if ($body -ne $null) {
                $params.Body = $body
                $params.ContentType = $contentType
            }
            if ($headers -ne $null) {
                $params.Headers = $headers
            }
            $response = Invoke-WebRequest @params
            $sw.Stop()
            $statusCode = [int]$response.StatusCode
            $ok = $statusCode -ge 200 -and $statusCode -lt 300
            if ($scenario -eq "import-async" -and $ok) {
                $json = $response.Content | ConvertFrom-Json
                $taskId = $json.data.taskId
                $pollWatch = [Diagnostics.Stopwatch]::StartNew()
                for ($poll = 0; $poll -lt 60; $poll++) {
                    Start-Sleep -Milliseconds 500
                    $taskResponse = Invoke-WebRequest -Method GET -Uri "$baseUrl/player/import/tasks/$taskId`?userId=$userId" -SkipHttpErrorCheck
                    if ($taskResponse.StatusCode -ge 200 -and $taskResponse.StatusCode -lt 300) {
                        $taskJson = $taskResponse.Content | ConvertFrom-Json
                        $terminalStatus = $taskJson.data.status
                        if ($terminalStatus -in @("SUCCESS", "FAILED", "SEND_FAILED")) {
                            break
                        }
                    }
                }
                $pollWatch.Stop()
                $totalMs = [Math]::Round($sw.Elapsed.TotalMilliseconds + $pollWatch.Elapsed.TotalMilliseconds, 2)
            }
        } catch {
            $sw.Stop()
            $ok = $false
        }

        $localResults.Add([pscustomobject]@{
            scenario = $scenario
            ok = $ok
            statusCode = $statusCode
            ms = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
            taskId = $taskId
            terminalStatus = $terminalStatus
            totalMs = $totalMs
        })
    } while ($maxRequests -le 0 -and [DateTime]::UtcNow -lt $endAt)

    $localResults
} -ThrottleLimit $Vus

$finishedAt = [DateTime]::UtcNow
$items = @($results | Where-Object { $_ -ne $null })
$latencies = @($items | ForEach-Object { [double]$_.ms })
$totalLatencies = @($items | Where-Object { $_.totalMs -ne $null } | ForEach-Object { [double]$_.totalMs })
$success = @($items | Where-Object { $_.ok })
$failed = @($items | Where-Object { -not $_.ok })
$elapsedSeconds = [Math]::Max(0.001, ($finishedAt - $startedAt).TotalSeconds)

$summary = [ordered]@{
    scenario = $Scenario
    baseUrl = $BaseUrl
    userId = $UserId
    vus = $Vus
    durationSeconds = $DurationSeconds
    maxRequests = $MaxRequests
    totalRequests = $items.Count
    success = $success.Count
    failed = $failed.Count
    failureRate = if ($items.Count -eq 0) { 0 } else { [Math]::Round($failed.Count / $items.Count, 4) }
    throughput = [Math]::Round($items.Count / $elapsedSeconds, 2)
    avgMs = if ($latencies.Count -eq 0) { 0 } else { [Math]::Round(($latencies | Measure-Object -Average).Average, 2) }
    p50Ms = Get-Percentile $latencies 50
    p95Ms = Get-Percentile $latencies 95
    p99Ms = Get-Percentile $latencies 99
}

if ($totalLatencies.Count -gt 0) {
    $summary.asyncTotalAvgMs = [Math]::Round(($totalLatencies | Measure-Object -Average).Average, 2)
    $summary.asyncTotalP95Ms = Get-Percentile $totalLatencies 95
}

$summary | ConvertTo-Json -Depth 5
