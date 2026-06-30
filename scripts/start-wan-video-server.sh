#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

: "${WAN_REPO_DIR:=${ROOT_DIR}/Wan2.1}"
: "${WAN_CKPT_DIR:=${WAN_REPO_DIR}/Wan2.1-T2V-1.3B}"
: "${WAN_OUTPUT_DIR:=${ROOT_DIR}/images/wan-video}"
: "${WAN_VIDEO_HOST:=127.0.0.1}"
: "${WAN_VIDEO_PORT:=7861}"

export WAN_REPO_DIR
export WAN_CKPT_DIR
export WAN_OUTPUT_DIR
export WAN_VIDEO_HOST
export WAN_VIDEO_PORT

python3 "${ROOT_DIR}/scripts/wan_video_server.py"
