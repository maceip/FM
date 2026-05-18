#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_ROOT="${ROOT_DIR}/app/src/main/assets/models/tts"
ARTIFACT_ROOT="${ROOT_DIR}/artifacts/voice-models"
MODEL_NAME="supertonic-3"
VERIFY_ONLY=0

usage() {
  cat >&2 <<'EOF'
usage: scripts/download-voice-models.sh [--model supertonic-3|kitten-mini-en-v0_1-fp16] [--verify-only]

Downloads the selected Sherpa-ONNX voice model into
app/src/main/assets/models/tts/. The model directory is intentionally gitignored;
run this script before local APK builds that should use neural on-device TTS.

supertonic-3 is the launch voice path. kitten-mini-en-v0_1-fp16 is retained as a
benchmark/fallback bundle.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model)
      [[ $# -ge 2 ]] || {
        usage
        exit 64
      }
      MODEL_NAME="$2"
      shift 2
      ;;
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

download_file() {
  local url="$1"
  local path="$2"
  mkdir -p "$(dirname "$path")"
  echo "download: $url"
  curl -L --fail --show-error --retry 3 --retry-delay 2 -o "$path" "$url"
}

verify_required_files() {
  local dest_dir="$1"
  shift
  local required=("$@")
  local path
  for path in "${required[@]}"; do
    [[ -f "$dest_dir/$path" ]] || {
      echo "missing voice model file: ${dest_dir#$ROOT_DIR/}/$path" >&2
      return 1
    }
  done
}

generate_supertonic_bins() {
  local model_dir="$1"
  SUPERTONIC_MODEL_DIR="$model_dir" python3 <<'PY'
import glob
import json
import math
import os
import struct
from pathlib import Path

root = Path(os.environ["SUPERTONIC_MODEL_DIR"])

index_path = root / "onnx" / "unicode_indexer.json"
index = json.loads(index_path.read_text(encoding="utf-8"))
if not isinstance(index, list):
    raise SystemExit(f"{index_path}: expected JSON array")
for i, value in enumerate(index):
    if isinstance(value, bool) or not isinstance(value, int):
        raise SystemExit(f"{index_path}: element {i} is not int32: {value!r}")
    if value < -(2**31) or value > 2**31 - 1:
        raise SystemExit(f"{index_path}: element {i} out of int32 range: {value}")
with (root / "onnx" / "unicode_indexer.bin").open("wb") as out:
    out.write(struct.pack(f"<{len(index)}i", *index))


def flatten(values):
    if isinstance(values, list):
        for item in values:
            yield from flatten(item)
    else:
        yield values


def load_tensor(path, key):
    payload = json.loads(path.read_text(encoding="utf-8"))
    if key not in payload:
        raise SystemExit(f"{path}: missing {key}")
    tensor = payload[key]
    dims = [int(x) for x in tensor.get("dims", [])]
    if len(dims) != 3 or dims[0] != 1:
        raise SystemExit(f"{path}: {key} expected dims [1, d1, d2], got {dims}")
    data = [float(x) for x in flatten(tensor.get("data", []))]
    expected = math.prod(dims)
    if len(data) != expected:
        raise SystemExit(f"{path}: {key} has {len(data)} floats, expected {expected}")
    if not all(math.isfinite(x) for x in data):
        raise SystemExit(f"{path}: {key} contains NaN/Inf")
    return dims, data


style_paths = [Path(p) for p in sorted(glob.glob(str(root / "voice_styles" / "*.json")))]
if not style_paths:
    raise SystemExit(f"{root / 'voice_styles'}: no style JSON files found")

ttl_dims_ref = None
dp_dims_ref = None
ttl_all = []
dp_all = []
for path in style_paths:
    ttl_dims, ttl_data = load_tensor(path, "style_ttl")
    dp_dims, dp_data = load_tensor(path, "style_dp")
    if ttl_dims_ref is None:
        ttl_dims_ref = ttl_dims
        dp_dims_ref = dp_dims
    elif ttl_dims[1:] != ttl_dims_ref[1:] or dp_dims[1:] != dp_dims_ref[1:]:
        raise SystemExit(
            f"{path}: dims mismatch ttl={ttl_dims} dp={dp_dims}, "
            f"expected ttl[1:]={ttl_dims_ref[1:]} dp[1:]={dp_dims_ref[1:]}"
        )
    ttl_all.extend(ttl_data)
    dp_all.extend(dp_data)

with (root / "voice_styles.bin").open("wb") as out:
    out.write(struct.pack("<3q", len(style_paths), ttl_dims_ref[1], ttl_dims_ref[2]))
    out.write(struct.pack("<3q", len(style_paths), dp_dims_ref[1], dp_dims_ref[2]))
    out.write(struct.pack(f"<{len(ttl_all)}f", *ttl_all))
    out.write(struct.pack(f"<{len(dp_all)}f", *dp_all))
PY
}

verify_supertonic() {
  local dest_dir="${DEST_ROOT}/supertonic-3"
  verify_required_files "$dest_dir" \
    "onnx/duration_predictor.onnx" \
    "onnx/text_encoder.onnx" \
    "onnx/vector_estimator.onnx" \
    "onnx/vocoder.onnx" \
    "onnx/tts.json" \
    "onnx/unicode_indexer.bin" \
    "voice_styles.bin"
}

download_supertonic() {
  local repo_url="https://huggingface.co/Supertone/supertonic-3/resolve/main"
  local dest_dir="${DEST_ROOT}/supertonic-3"
  local tmp_dir="${DEST_ROOT}/.supertonic-3.download"
  local files=(
    "LICENSE"
    "README.md"
    "config.json"
    "onnx/duration_predictor.onnx"
    "onnx/text_encoder.onnx"
    "onnx/vector_estimator.onnx"
    "onnx/vocoder.onnx"
    "onnx/tts.json"
    "onnx/unicode_indexer.json"
    "voice_styles/F1.json"
    "voice_styles/F2.json"
    "voice_styles/F3.json"
    "voice_styles/F4.json"
    "voice_styles/F5.json"
    "voice_styles/M1.json"
    "voice_styles/M2.json"
    "voice_styles/M3.json"
    "voice_styles/M4.json"
    "voice_styles/M5.json"
  )

  rm -rf "$tmp_dir"
  mkdir -p "$tmp_dir"
  local file
  for file in "${files[@]}"; do
    download_file "$repo_url/$file" "$tmp_dir/$file"
  done
  generate_supertonic_bins "$tmp_dir"
  rm -rf "$dest_dir"
  mv "$tmp_dir" "$dest_dir"
  verify_supertonic
}

verify_kitten_archive() {
  local archive_path="$1"
  local expected_sha="a12bdf4f68a509527802c01b258d63a99e2c86550d7960726f23a7ad21bdbcb8"
  local expected_size="156630103"
  [[ -f "$archive_path" ]] || return 1
  local actual_size actual_sha
  actual_size="$(size_file "$archive_path")"
  [[ "$actual_size" == "$expected_size" ]] || {
    echo "voice model archive size mismatch: expected $expected_size, got $actual_size" >&2
    return 1
  }
  actual_sha="$(sha_file "$archive_path")"
  [[ "$actual_sha" == "$expected_sha" ]] || {
    echo "voice model archive checksum mismatch: expected $expected_sha, got $actual_sha" >&2
    return 1
  }
}

verify_kitten() {
  local dest_dir="${DEST_ROOT}/kitten-mini-en-v0_1-fp16"
  verify_required_files "$dest_dir" \
    "model.fp16.onnx" \
    "voices.bin" \
    "tokens.txt" \
    "espeak-ng-data/phondata"
}

download_kitten() {
  local model_name="kitten-mini-en-v0_1-fp16"
  local model_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${model_name}.tar.bz2"
  local archive_path="${ARTIFACT_ROOT}/${model_name}.tar.bz2"
  local dest_dir="${DEST_ROOT}/${model_name}"
  local tmp_dir="${DEST_ROOT}/.${model_name}.download"

  mkdir -p "$ARTIFACT_ROOT" "$DEST_ROOT"
  if ! verify_kitten_archive "$archive_path" >/dev/null 2>&1; then
    download_file "$model_url" "$archive_path"
    verify_kitten_archive "$archive_path"
  fi
  rm -rf "$tmp_dir"
  mkdir -p "$tmp_dir"
  tar -xjf "$archive_path" -C "$tmp_dir"
  rm -rf "$dest_dir"
  mv "$tmp_dir/$model_name" "$dest_dir"
  rm -rf "$tmp_dir"
  verify_kitten
}

case "$MODEL_NAME" in
  supertonic-3)
    if [[ "$VERIFY_ONLY" == "1" ]]; then
      verify_supertonic
    else
      download_supertonic
    fi
    ;;
  kitten-mini-en-v0_1-fp16)
    if [[ "$VERIFY_ONLY" == "1" ]]; then
      verify_kitten
    else
      download_kitten
    fi
    ;;
  *)
    usage
    exit 64
    ;;
esac

echo "voice model assets ready: app/src/main/assets/models/tts/${MODEL_NAME}"
