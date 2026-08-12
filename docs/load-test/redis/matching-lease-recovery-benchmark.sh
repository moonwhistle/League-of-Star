#!/usr/bin/env bash

set -uo pipefail

LABEL=${1:-lease-recovery}
TOTAL_USERS=${TOTAL_USERS:-5000}
EXPECTED_PAIRS=$((TOTAL_USERS / 2))
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-90}
RESULT_DIR="docs/load-test/result/matching/recovery/${LABEL}"
PRIMARY_CONTAINER=${PRIMARY_CONTAINER:-league-of-star-api-1}
RECOVERY_CONTAINER=${RECOVERY_CONTAINER:-league-of-star-api-2}
PRIMARY_CPUS=${PRIMARY_CPUS:-0.05}
APP_COMPOSE_FILE=${APP_COMPOSE_FILE:-infra/local/docker-compose-app.yml}

mkdir -p "${RESULT_DIR}"

cleanup() {
  docker unpause "${RECOVERY_CONTAINER}" >/dev/null 2>&1 || true
  docker unpause "${PRIMARY_CONTAINER}" >/dev/null 2>&1 || true
  docker compose -f "${APP_COMPOSE_FILE}" up -d --no-deps --force-recreate \
    "${PRIMARY_CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

redis-cli FLUSHALL >/dev/null
redis-cli XGROUP CREATE matching:jobs matching-workers 0 MKSTREAM >/dev/null
redis-cli CONFIG RESETSTAT >/dev/null
recovered_claims_before=$(curl -fsS http://localhost:8081/actuator/prometheus \
  | awk '/^match_engine_recovered_claims_total / {print $2}' | tail -1)
recovered_claims_before=${recovered_claims_before:-0}
docker pause "${PRIMARY_CONTAINER}" >/dev/null
docker pause "${RECOVERY_CONTAINER}" >/dev/null

# 엔진이 멈춘 상태에서 실제 DB에 존재하는 사용자 ID를 FIFO 대기열에 준비한다.
seq 1 "${TOTAL_USERS}" | awk '{
  score = 1700000000000 + $1
  print "ZADD matching:queue " score " " $1
  print "SET match:status:" $1 " MATCHING EX 1800"
}' | redis-cli --pipe >/dev/null

docker update --cpus "${PRIMARY_CPUS}" "${PRIMARY_CONTAINER}" >/dev/null
started_ns=$(python3 -c 'import time; print(time.time_ns())')
docker unpause "${PRIMARY_CONTAINER}" >/dev/null

claim_deadline=$((SECONDS + 10))
pending_before_kill=0
while [[ "${pending_before_kill}" -eq 0 ]]; do
  pending_before_kill=$(redis-cli --raw XPENDING matching:jobs matching-workers | head -1)
  pending_before_kill=${pending_before_kill:-0}
  if [[ "${SECONDS}" -ge "${claim_deadline}" ]]; then
    echo "No pending Stream job was observed" >&2
    exit 1
  fi
  sleep 0.001
done
docker pause "${PRIMARY_CONTAINER}" >/dev/null
pending_before_kill=$(redis-cli --raw XPENDING matching:jobs matching-workers | head -1)
pending_before_kill=${pending_before_kill:-0}
if [[ "${pending_before_kill}" -eq 0 ]]; then
  echo "The Stream jobs completed before the primary worker could be paused" >&2
  exit 1
fi
waiting_before_kill=$(redis-cli --raw ZCARD matching:queue)

docker kill "${PRIMARY_CONTAINER}" >/dev/null
killed_ns=$(python3 -c 'import time; print(time.time_ns())')
docker unpause "${RECOVERY_CONTAINER}" >/dev/null

deadline=$((SECONDS + TIMEOUT_SECONDS))
timed_out=false
while true; do
  waiting=$(redis-cli --raw ZCARD matching:queue)
  pending=$(redis-cli --raw ZCARD match:response:timeout:pending)
  stream_pending=$(redis-cli --raw XPENDING matching:jobs matching-workers | head -1)
  stream_pending=${stream_pending:-0}

  if [[ "${waiting}" -eq 0 && "${pending}" -eq "${EXPECTED_PAIRS}" \
        && "${stream_pending}" -eq 0 ]]; then
    break
  fi

  if [[ "${SECONDS}" -ge "${deadline}" ]]; then
    timed_out=true
    break
  fi
  sleep 0.02
done

finished_ns=$(python3 -c 'import time; print(time.time_ns())')
elapsed_ms=$(python3 -c "print(round((${finished_ns} - ${started_ns}) / 1_000_000, 3))")
recovery_elapsed_ms=$(python3 -c "print(round((${finished_ns} - ${killed_ns}) / 1_000_000, 3))")

waiting=$(redis-cli --raw ZCARD matching:queue)
pending=$(redis-cli --raw ZCARD match:response:timeout:pending)
stream_pending=$(redis-cli --raw XPENDING matching:jobs matching-workers | head -1)
stream_pending=${stream_pending:-0}
stream_size=$(redis-cli --raw XLEN matching:jobs)
session_count=$(redis-cli --scan --pattern 'match:session:*' | wc -l | tr -d ' ')
status_count=$(redis-cli --scan --pattern 'match:status:*' | wc -l | tr -d ' ')
recovered_claims_total=$(curl -fsS http://localhost:8081/actuator/prometheus \
  | awk '/^match_engine_recovered_claims_total / {print $2}' | tail -1)
recovered_claims_total=${recovered_claims_total:-0}
recovered_claims=$(python3 -c "print(${recovered_claims_total} - ${recovered_claims_before})")

redis-cli INFO commandstats >"${RESULT_DIR}/redis-commandstats.txt"
docker logs "${RECOVERY_CONTAINER}" 2>&1 \
  | grep -E 'Recovered pending MatchJobs|ERROR|Exception' \
  | tail -500 >"${RESULT_DIR}/recovery-container.log" || true

cat >"${RESULT_DIR}/recovery-summary.txt" <<EOF
label=${LABEL}
total_users=${TOTAL_USERS}
expected_pairs=${EXPECTED_PAIRS}
pending_before_kill=${pending_before_kill}
waiting_before_kill=${waiting_before_kill}
elapsed_ms=${elapsed_ms}
recovery_elapsed_ms=${recovery_elapsed_ms}
timed_out=${timed_out}
waiting_users=${waiting}
stream_pending=${stream_pending}
stream_size=${stream_size}
pending_timeouts=${pending}
session_count=${session_count}
status_count=${status_count}
recovered_claims=${recovered_claims}
EOF

cat "${RESULT_DIR}/recovery-summary.txt"

if [[ "${timed_out}" == true || "${waiting}" -ne 0 || "${stream_pending}" -ne 0 \
      || "${stream_size}" -ne 0 \
      || "${pending}" -ne "${EXPECTED_PAIRS}" || "${session_count}" -ne "${EXPECTED_PAIRS}" \
      || "${status_count}" -ne "${TOTAL_USERS}" ]]; then
  exit 1
fi
