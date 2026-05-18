#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_NAME="kitten-mini-en-v0_1-fp16"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${MODEL_NAME}.tar.bz2"
MODEL_SHA256="a12bdf4f68a509527802c01b258d63a99e2c86550d7960726f23a7ad21bdbcb8"
MODEL_SIZE_BYTES="156630103"
ARCHIVE_DIR="${ROOT_DIR}/artifacts/voice-models"
ARCHIVE_PATH="${ARCHIVE_DIR}/${MODEL_NAME}.tar.bz2"
DEST_ROOT="${ROOT_DIR}/app/src/main/assets/models/tts"
DEST_DIR="${DEST_ROOT}/${MODEL_NAME}"
VERIFY_ONLY=0

usage() {
  cat >&2 <<'EOF'
usage: scripts/download-voice-models.sh [--verify-only]

Downloads the selected Sherpa-ONNX voice model into
app/src/main/assets/models/tts/. The model directory is intentionally gitignored;
run this script before local APK builds that should use neural on-device TTS.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verify-only)
      VERIFY_ONLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 64
      ;;
  esac
done

sha_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

size_file() {
  if size="$(stat -f '%z' "$1" 2>/dev/null)"; then
    printf '%s\n' "$size"
  else
    stat -c '%s' "$1"
  fi
}

verify_archive() {
  [[ -f "$ARCHIVE_PATH" ]] || return 1
  local actual_size actual_sha
  actual_size="$(size_file "$ARCHIVE_PATH")"
  [[ "$actual_size" == "$MODEL_SIZE_BYTES" ]] || {
    echo "voice model archive size mismatch: expected $MODEL_SIZE_BYTES, got $actual_size" >&2
    return 1
  }
  actual_sha="$(sha_file "$ARCHIVE_PATH")"
  [[ "$actual_sha" == "$MODEL_SHA256" ]] || {
    echo "voice model archive checksum mismatch: expected $MODEL_SHA256, got $actual_sha" >&2
    return 1
  }
}

verify_extracted() {
  local required=(
    "$DEST_DIR/model.fp16.onnx"
    "$DEST_DIR/voices.bin"
    "$DEST_DIR/tokens.txt"
    "$DEST_DIR/espeak-ng-data/phondata"
  )
  for path in "${required[@]}"; do
    [[ -f "$path" ]] || {
      echo "missing voice model file: ${path#$ROOT_DIR/}" >&2
      return 1
    }
  done
}

if [[ "$VERIFY_ONLY" == "1" ]]; then
  verify_extracted
  echo "voice model assets verified: ${DEST_DIR#$ROOT_DIR/}"
  exit 0
fi

mkdir -p "$ARCHIVE_DIR" "$DEST_ROOT"
if ! verify_archive >/dev/null 2>&1; then
  echo "download: $MODEL_URL"
  curl -L --fail --show-error --continue-at - -o "$ARCHIVE_PATH" "$MODEL_URL"
  verify_archive
fi

tmp_dir="${DEST_ROOT}/.${MODEL_NAME}.download"
rm -rf "$tmp_dir"
mkdir -p "$tmp_dir"
tar -xjf "$ARCHIVE_PATH" -C "$tmp_dir"
rm -rf "$DEST_DIR"
mv "$tmp_dir/$MODEL_NAME" "$DEST_DIR"
rm -rf "$tmp_dir"
verify_extracted
echo "voice model assets ready: ${DEST_DIR#$ROOT_DIR/}"
