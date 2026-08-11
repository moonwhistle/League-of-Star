#!/usr/bin/env bash

set -uo pipefail

LABEL=${1:?"usage: matching-e2e-benchmark.sh <label>"}
TOTAL_USERS=${TOTAL_USERS:-5000}
VUS=${VUS:-500}
EXPECTED_PAIRS=$((TOTAL_USERS / 2))
TOKENS_FILE=${TOKENS_FILE:-/tmp/league-of-star-leasebench-tokens.json}
API_BASE_URLS=${API_BASE_URLS:-http://localhost:8080,http://localhost:8081}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-60}
RESULT_DIR="docs/load-test/result/matching/e2e/${LABEL}"

mkdir -p "${RESULT_DIR}"

redis-cli FLUSHALL >/dev/null
redis-cli XGROUP CREATE matching:jobs matching-workers 0 MKSTREAM >/dev/null
redis-cli CONFIG RESETSTAT >/dev/null

started_ns=$(python3 -c 'import time; print(time.time_ns())')

TOTAL_USERS="${TOTAL_USERS}" \
VUS="${VUS}" \
TOKENS_FILE="${TOKENS_FILE}" \
API_BASE_URLS="${API_BASE_URLS}" \
k6 run \
  --summary-trend-stats 'avg,med,p(90),p(95),p(99),max' \
  --summary-export "${RESULT_DIR}/k6-summary.json" \
  docs/load-test/join-queue-burst.js \
  >"${RESULT_DIR}/k6-output.txt" 2>&1 &
k6_pid=$!

deadline=$((SECONDS + TIMEOUT_SECONDS))
timed_out=false

while true; do
  waiting=$(redis-cli --raw ZCARD matching:queue)
  pending=$(redis-cli --raw ZCARD match:response:timeout:pending)
  stream_pending=$(redis-cli --raw XPENDING matching:jobs matching-workers | head -1)
  stream_pending=${stream_pending:-0}

  if kill -0 "${k6_pid}" 2>/dev/null; then
    k6_running=true
  else
    k6_running=false
  fi

  if [[ "${k6_running}" == false && "${waiting}" -eq 0 && "${pending}" -eq "${EXPECTED_PAIRS}" \
        && "${stream_pending}" -eq 0 ]]; then
    break
  fi

  if [[ "${SECONDS}" -ge "${deadline}" ]]; then
    timed_out=true
    break
  fi

  sleep 0.02
done

if wait "${k6_pid}"; then
  k6_exit_code=0
else
  k6_exit_code=$?
fi

finished_ns=$(python3 -c 'import time; print(time.time_ns())')
elapsed_ms=$(python3 -c "print(round((${finished_ns} - ${started_ns}) / 1_000_000, 3))")

waiting=$(redis-cli --raw ZCARD matching:queue)
pending=$(redis-cli --raw ZCARD match:response:timeout:pending)
processing_timeout=$(redis-cli --raw ZCARD match:response:timeout:processing)
stream_pending=$(redis-cli --raw XPENDING matching:jobs matching-workers | head -1)
stream_pending=${stream_pending:-0}
stream_size=$(redis-cli --raw XLEN matching:jobs)
session_count=$(redis-cli --scan --pattern 'match:session:*' | wc -l | tr -d ' ')
status_count=$(redis-cli --scan --pattern 'match:status:*' | wc -l | tr -d ' ')

redis-cli INFO commandstats >"${RESULT_DIR}/redis-commandstats.txt"

cat >"${RESULT_DIR}/e2e-summary.txt" <<EOF
label=${LABEL}
total_users=${TOTAL_USERS}
expected_pairs=${EXPECTED_PAIRS}
vus=${VUS}
workers=2
elapsed_ms=${elapsed_ms}
k6_exit_code=${k6_exit_code}
timed_out=${timed_out}
waiting_users=${waiting}
pending_timeouts=${pending}
processing_timeouts=${processing_timeout}
stream_pending=${stream_pending}
stream_size=${stream_size}
session_count=${session_count}
status_count=${status_count}
EOF

cat "${RESULT_DIR}/e2e-summary.txt"

if [[ "${timed_out}" == true || "${waiting}" -ne 0 || "${pending}" -ne "${EXPECTED_PAIRS}" \
      || "${stream_pending}" -ne 0 || "${stream_size}" -ne 0 \
      || "${session_count}" -ne "${EXPECTED_PAIRS}" || "${status_count}" -ne "${TOTAL_USERS}" ]]; then
  exit 1
fi
