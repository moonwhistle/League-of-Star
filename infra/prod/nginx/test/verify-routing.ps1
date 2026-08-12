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

    try {
        Invoke-WebRequest -Uri "$baseUrl/actuator/prometheus" | Out-Null
        throw "Actuator endpoint was publicly accessible"
    }
    catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 404) {
            throw
        }
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

    $webSocketHeaders = curl.exe --silent --dump-header - --output NUL `
        --header "Connection: Upgrade" `
        --header "Upgrade: websocket" `
        --header "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" `
        --header "Sec-WebSocket-Version: 13" `
        --max-time 1 `
        "$baseUrl/ws/game/300"
    if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne 28) {
        throw "Failed to request the WebSocket route"
    }
    $webSocketHeadersText = $webSocketHeaders -join "`n"
    if ($webSocketHeadersText -notmatch "(?im)^HTTP/1\.1 101\s" `
            -or $webSocketHeadersText -notmatch "(?im)^Upgrade:\s*websocket\s*$" `
            -or $webSocketHeadersText -notmatch "(?im)^Connection:\s*upgrade\s*$") {
        throw "WebSocket handshake was not proxied successfully"
    }

    $sseBody = curl.exe --silent --max-time 1 `
        "$baseUrl/api/v1/notifications/match/stream"
    if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne 28) {
        throw "SSE request failed unexpectedly"
    }
    $sseBodyText = $sseBody -join "`n"
    if ($sseBodyText -notmatch "(?m)^data: first$") {
        throw "The first SSE event was buffered instead of being delivered immediately"
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
