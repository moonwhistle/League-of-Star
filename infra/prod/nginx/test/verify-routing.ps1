$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "compose.yml"
$baseUrl = "http://127.0.0.1:18080"

function Get-UpstreamHostname {
    param([string]$Path)

    $body = Invoke-RestMethod -Uri "$baseUrl$Path"
    $match = [regex]::Match($body, "Hostname:\s*(\S+)")
    if (-not $match.Success) {
        throw "Could not find upstream hostname in response for $Path"
    }
    return $match.Groups[1].Value
}

try {
    docker compose -f $composeFile up -d --build --wait
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start Nginx routing test containers"
    }

    $health = Invoke-RestMethod -Uri "$baseUrl/nginx-health"
    if ($health.Trim() -ne "ok") {
        throw "Nginx health endpoint returned an unexpected response"
    }

    $restTargets = 1..20 | ForEach-Object { Get-UpstreamHostname "/api/test?request=$_" }
    if (($restTargets | Sort-Object -Unique).Count -ne 2) {
        throw "REST requests were not distributed across both API instances"
    }

    $gameTargets = 1..10 | ForEach-Object { Get-UpstreamHostname "/ws/game/100?token=$_" }
    if (($gameTargets | Sort-Object -Unique).Count -ne 1) {
        throw "Requests for the same gameRoomId reached multiple API instances"
    }

    $customRoomTargets = 1..10 | ForEach-Object {
        Get-UpstreamHostname "/ws/custom-games/rooms/200?token=$_"
    }
    if (($customRoomTargets | Sort-Object -Unique).Count -ne 1) {
        throw "Requests for the same custom roomId reached multiple API instances"
    }

    $webSocketEcho = curl.exe --silent --show-error `
        --header "Connection: Upgrade" `
        --header "Upgrade: websocket" `
        "$baseUrl/ws/game/300"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to request the WebSocket route"
    }
    $webSocketEchoText = $webSocketEcho -join "`n"
    if ($webSocketEchoText -notmatch "(?im)^Upgrade:\s*websocket\s*$" `
            -or $webSocketEchoText -notmatch "(?im)^Connection:\s*upgrade\s*$") {
        throw "WebSocket upgrade headers were not forwarded to the API instance"
    }

    $sseHeaders = curl.exe --silent --show-error --dump-header - --output NUL `
        "$baseUrl/api/v1/notifications/match/stream"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to request the SSE endpoint"
    }
    $sseHeadersText = $sseHeaders -join "`n"
    if ($sseHeadersText -notmatch "(?im)^X-Proxy-Buffering:\s*off\s*$") {
        throw "SSE buffering header was not disabled"
    }

    $selectedGameTarget = $gameTargets[0]
    $selectedGameService = if ($selectedGameTarget -eq "api-1") {
        "league-of-star-api-1"
    } else {
        "league-of-star-api-2"
    }
    docker compose -f $composeFile stop $selectedGameService
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to stop the selected game WebSocket upstream"
    }

    $failoverTarget = $null
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            $failoverTarget = Get-UpstreamHostname "/ws/game/100?token=failover-$attempt"
            break
        }
        catch {
            if ($attempt -eq 5) {
                throw
            }
            Start-Sleep -Seconds 1
        }
    }
    if ($failoverTarget -eq $selectedGameTarget) {
        throw "WebSocket reconnect did not fail over after the selected API instance stopped"
    }

    Write-Output "Nginx routing verification passed"
    Write-Output "REST upstreams: $($restTargets | Sort-Object -Unique)"
    Write-Output "Game room 100 upstream: $($gameTargets[0])"
    Write-Output "Custom room 200 upstream: $($customRoomTargets[0])"
    Write-Output "Game room 100 failover upstream: $failoverTarget"
}
finally {
    docker compose -f $composeFile down --volumes --remove-orphans
}
