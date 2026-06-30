#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${STABLE_DIFFUSION_COMPOSE_FILE:-${PROJECT_DIR}/stable-diffusion-docker-compose.yml}"

if [ ! -f "${COMPOSE_FILE}" ]; then
  echo "Stable Diffusion compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  docker compose -f "${COMPOSE_FILE}" up -d
  exit 0
fi

if command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "${COMPOSE_FILE}" up -d
  exit 0
fi

echo "Docker Compose command not found. Install docker compose or docker-compose first." >&2
exit 1
