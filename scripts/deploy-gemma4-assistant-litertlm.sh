#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="${PACKAGE_NAME:-com.google.research.air.cosmo}"
MODEL_ID="${MODEL_ID:-google-gemma-4-E4B-it-litertlm}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to derive the LiteRT-LM model storage path." >&2
  exit 1
fi

MODEL_STORAGE_ID="$(python3 "$ROOT_DIR/scripts/model-storage-id.py" "$MODEL_ID")"
LOCAL_MODEL="${LOCAL_MODEL:-$ROOT_DIR/artifacts/models/$MODEL_STORAGE_ID/model.litertlm}"
DEVICE_DIR="files/models/$MODEL_STORAGE_ID"
DEVICE_MODEL="$DEVICE_DIR/model.litertlm"
ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required to deploy the LiteRT-LM model to a device." >&2
  exit 1
fi

if [[ ! -r "$LOCAL_MODEL" ]]; then
  echo "Local model is missing or unreadable: $LOCAL_MODEL" >&2
  echo "Run scripts/convert-gemma4-e4b-litertlm.sh or scripts/provision-gemma4-litertlm.sh first." >&2
  exit 1
fi

"${ADB[@]}" shell "run-as '$PACKAGE_NAME' mkdir -p '$DEVICE_DIR'"
"${ADB[@]}" shell "run-as '$PACKAGE_NAME' sh -c 'cat > \"$DEVICE_MODEL\"'" < "$LOCAL_MODEL"
"${ADB[@]}" shell "run-as '$PACKAGE_NAME' chmod 0600 '$DEVICE_MODEL'"

echo "Deployed $LOCAL_MODEL to $PACKAGE_NAME:$DEVICE_MODEL"
