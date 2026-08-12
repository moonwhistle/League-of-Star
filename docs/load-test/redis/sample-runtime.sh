#!/usr/bin/env bash

set -euo pipefail

REDIS_CONTAINER="${REDIS_CONTAINER:-league-of-star-redis}"
OUTPUT_DIR="${OUTPUT_DIR:-docs/load-test/result/matching/redis}"
LABEL="${1:-manual}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-0.2}"
STOP_FILE="${STOP_FILE:-/tmp/league-of-star-redis-sampler.stop}"
OUTPUT_FILE="${OUTPUT_DIR}/${LABEL}-runtime.csv"

mkdir -p "${OUTPUT_DIR}"
rm -f "${STOP_FILE}"
echo "timestamp_ms,total_commands,total_net_input_bytes,total_net_output_bytes,instantaneous_ops_per_sec,used_cpu_sys,used_cpu_user,used_memory,used_memory_rss" > "${OUTPUT_FILE}"

value_of() {
  local payload="$1"
  local key="$2"
  printf '%s\n' "${payload}" | tr -d '\r' | awk -F: -v key="${key}" '$1 == key { print $2; exit }'
}

while [[ ! -f "${STOP_FILE}" ]]; do
  info="$(docker exec "${REDIS_CONTAINER}" redis-cli INFO)"
  timestamp_ms="$(($(date +%s) * 1000))"
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "${timestamp_ms}" \
    "$(value_of "${info}" total_commands_processed)" \
    "$(value_of "${info}" total_net_input_bytes)" \
    "$(value_of "${info}" total_net_output_bytes)" \
    "$(value_of "${info}" instantaneous_ops_per_sec)" \
    "$(value_of "${info}" used_cpu_sys)" \
    "$(value_of "${info}" used_cpu_user)" \
    "$(value_of "${info}" used_memory)" \
    "$(value_of "${info}" used_memory_rss)" \
    >> "${OUTPUT_FILE}"
  sleep "${INTERVAL_SECONDS}"
done

echo "Redis runtime samples written to ${OUTPUT_FILE}"
