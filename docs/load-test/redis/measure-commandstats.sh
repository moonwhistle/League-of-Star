#!/usr/bin/env bash

set -euo pipefail

REDIS_CONTAINER="${REDIS_CONTAINER:-league-of-star-redis}"
OUTPUT_DIR="${OUTPUT_DIR:-docs/load-test/result/matching/redis}"
LABEL="${1:-manual}"
ACTION="${2:-snapshot}"

mkdir -p "${OUTPUT_DIR}"

redis_cli() {
  docker exec "${REDIS_CONTAINER}" redis-cli "$@"
}

snapshot() {
  local target="${OUTPUT_DIR}/${LABEL}.txt"
  {
    echo "label=${LABEL}"
    echo "captured_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo
    echo "[stats]"
    redis_cli INFO stats | tr -d '\r' | grep -E \
      '^(total_connections_received|total_commands_processed|instantaneous_ops_per_sec|total_net_input_bytes|total_net_output_bytes|instantaneous_input_kbps|instantaneous_output_kbps|rejected_connections|sync_full|sync_partial_ok|expired_keys|evicted_keys|keyspace_hits|keyspace_misses):'
    echo
    echo "[commandstats]"
    redis_cli INFO commandstats | tr -d '\r' | sort
    echo
    echo "[cpu]"
    redis_cli INFO cpu | tr -d '\r' | grep -E '^(used_cpu_sys|used_cpu_user|used_cpu_sys_children|used_cpu_user_children):'
    echo
    echo "[memory]"
    redis_cli INFO memory | tr -d '\r' | grep -E \
      '^(used_memory:|used_memory_peak:|used_memory_rss:|maxmemory:|mem_fragmentation_ratio:)'
  } > "${target}"
  echo "Redis metrics written to ${target}"
}

case "${ACTION}" in
  reset)
    redis_cli CONFIG RESETSTAT >/dev/null
    echo "Redis statistics reset for ${LABEL}"
    ;;
  snapshot)
    snapshot
    ;;
  *)
    echo "Usage: $0 <label> <reset|snapshot>" >&2
    exit 1
    ;;
esac
