#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export TRIPOSPLAT_PORT="${TRIPOSPLAT_PORT:-7862}"

if [[ -n "${TRIPOSPLAT_PYTHON:-}" ]]; then
  PYTHON_BIN="$TRIPOSPLAT_PYTHON"
elif [[ -x ".venv-triposplat/bin/python" ]]; then
  PYTHON_BIN=".venv-triposplat/bin/python"
else
  PYTHON_BIN="python3"
fi

exec "$PYTHON_BIN" scripts/triposplat_service.py
