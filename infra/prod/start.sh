#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/compose.yml"
ENV_FILE="$SCRIPT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Missing $ENV_FILE. Create it from .env.example." >&2
    exit 1
fi

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

echo "[build] Building Nginx and API images"
compose build nginx league-of-star-api-1

echo "[1/3] Starting Nginx"
compose up -d --no-build --wait --no-deps nginx

echo "[2/3] Starting MySQL and Redis"
compose up -d --wait --no-deps mysql redis

echo "[3/3] Starting API instances"
compose up -d --no-build --no-deps --wait league-of-star-api-1 league-of-star-api-2

compose ps
