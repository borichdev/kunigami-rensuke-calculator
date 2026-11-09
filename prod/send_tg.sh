#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/kunigami-calc.apk"
CAPTION="${1:-}"
TARGET_CHAT="${2:-me}"

if [[ ! -f "$APK_PATH" ]]; then
    echo "Error: APK not found at $APK_PATH. Run ./build.sh first." >&2
    exit 1
fi

/home/borichdev/.venv/bin/python ~/.gemini/config/skills/telegram-userbot/scripts/tg_client.py \
    send-file "$TARGET_CHAT" "$APK_PATH" "$CAPTION"
