#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/compose.yml"
BASE_URL="http://127.0.0.1:18080"

cleanup() {
    docker compose -f "$COMPOSE_FILE" down --volumes --remove-orphans
}

get_upstream_hostname() {
    path=$1
    body=$(curl --fail --silent --show-error "$BASE_URL$path") || return 1
    printf '%s\n' "$body" | awk '/^Hostname:/ { print $2; exit }'
}

trap cleanup EXIT INT TERM

docker compose -f "$COMPOSE_FILE" up -d --build --wait

health=$(curl --fail --silent --show-error "$BASE_URL/nginx-health")
test "$health" = "ok"

rest_targets=""
request=1
while [ "$request" -le 20 ]; do
    rest_targets="$rest_targets $(get_upstream_hostname "/api/test?request=$request")"
    request=$((request + 1))
done
rest_unique_count=$(printf '%s\n' "$rest_targets" | tr ' ' '\n' | sed '/^$/d' | sort -u | wc -l | tr -d ' ')
test "$rest_unique_count" = "2"

game_targets=""
request=1
while [ "$request" -le 10 ]; do
    game_targets="$game_targets $(get_upstream_hostname "/ws/game/100?token=$request")"
    request=$((request + 1))
done
game_target=$(printf '%s\n' "$game_targets" | tr ' ' '\n' | sed '/^$/d' | sort -u)
test "$(printf '%s\n' "$game_target" | wc -l | tr -d ' ')" = "1"

custom_targets=""
request=1
while [ "$request" -le 10 ]; do
    custom_targets="$custom_targets $(get_upstream_hostname "/ws/custom-games/rooms/200?token=$request")"
    request=$((request + 1))
done
custom_target=$(printf '%s\n' "$custom_targets" | tr ' ' '\n' | sed '/^$/d' | sort -u)
test "$(printf '%s\n' "$custom_target" | wc -l | tr -d ' ')" = "1"

websocket_echo=$(curl --fail --silent --show-error \
    --header "Connection: Upgrade" \
    --header "Upgrade: websocket" \
    "$BASE_URL/ws/game/300")
printf '%s\n' "$websocket_echo" | grep -Eiq '^Upgrade:[[:space:]]*websocket[[:space:]]*$'
printf '%s\n' "$websocket_echo" | grep -Eiq '^Connection:[[:space:]]*upgrade[[:space:]]*$'

sse_headers=$(curl --fail --silent --show-error --dump-header - --output /dev/null \
    "$BASE_URL/api/v1/notifications/match/stream")
printf '%s\n' "$sse_headers" | grep -Eiq '^X-Proxy-Buffering:[[:space:]]*off[[:space:]]*$'

if [ "$game_target" = "api-1" ]; then
    selected_game_service="league-of-star-api-1"
else
    selected_game_service="league-of-star-api-2"
fi
docker compose -f "$COMPOSE_FILE" stop "$selected_game_service"

failover_target=""
attempt=1
while [ "$attempt" -le 5 ]; do
    if failover_target=$(get_upstream_hostname "/ws/game/100?token=failover-$attempt"); then
        break
    fi
    attempt=$((attempt + 1))
    sleep 1
done
test -n "$failover_target"
test "$failover_target" != "$game_target"

echo "Nginx routing verification passed"
echo "REST upstreams:$(printf '%s\n' "$rest_targets" | tr ' ' '\n' | sed '/^$/d' | sort -u | tr '\n' ' ')"
echo "Game room 100 upstream: $game_target"
echo "Custom room 200 upstream: $custom_target"
echo "Game room 100 failover upstream: $failover_target"
