#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_MODEL_ID="${APP_MODEL_ID:-google-gemma-4-E2B-it-litertlm}"
SOURCE_MODEL="${SOURCE_MODEL:-$HOME/Downloads/gemma-4-E2B-it (1).litertlm}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to derive the LiteRT-LM model storage path." >&2
  exit 1
fi

if [[ ! -r "$SOURCE_MODEL" ]]; then
  echo "LiteRT-LM source model is missing or unreadable: $SOURCE_MODEL" >&2
  echo "Set SOURCE_MODEL=/path/to/model.litertlm if the file lives elsewhere." >&2
  exit 1
fi

APP_MODEL_STORAGE_ID="$(python3 "$ROOT_DIR/scripts/model-storage-id.py" "$APP_MODEL_ID")"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/artifacts/models/$APP_MODEL_STORAGE_ID}"
OUTPUT_MODEL="$OUTPUT_DIR/model.litertlm"

mkdir -p "$OUTPUT_DIR"
if [[ "$SOURCE_MODEL" != "$OUTPUT_MODEL" ]]; then
  cp "$SOURCE_MODEL" "$OUTPUT_MODEL"
fi

MODEL_SHA256="$(shasum -a 256 "$OUTPUT_MODEL" | awk '{print $1}')"
if MODEL_BYTES="$(stat -f '%z' "$OUTPUT_MODEL" 2>/dev/null)"; then
  :
else
  MODEL_BYTES="$(stat -c '%s' "$OUTPUT_MODEL")"
fi

cat > "$OUTPUT_DIR/model.litertlm.sha256" <<EOF
$MODEL_SHA256  $OUTPUT_MODEL
EOF

cat > "$OUTPUT_DIR/model.metadata.json" <<EOF
{
  "artifact": {
    "bytes": $MODEL_BYTES,
    "file": "model.litertlm",
    "sha256": "$MODEL_SHA256"
  },
  "modelId": "$APP_MODEL_ID",
  "runtime": "LiteRT-LM",
  "source": {
    "file": "$SOURCE_MODEL",
    "type": "local-file"
  }
}
EOF

cat > "$OUTPUT_DIR/model.manifest-entry.json" <<EOF
{
  "filename": "gemma-4-E2B-it.litertlm",
  "id": "$APP_MODEL_ID",
  "mediaType": "application/x-litertlm",
  "required": false,
  "sha256": "$MODEL_SHA256",
  "sizeBytes": $MODEL_BYTES,
  "url": ""
}
EOF

echo "Provisioned LiteRT-LM artifact: $OUTPUT_MODEL"
echo "Model id: $APP_MODEL_ID"
echo "Storage id: $APP_MODEL_STORAGE_ID"
echo "Bytes: $MODEL_BYTES"
echo "SHA-256: $MODEL_SHA256"
