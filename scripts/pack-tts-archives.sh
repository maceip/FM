#!/usr/bin/env bash
# Pack gitignored TTS trees into versioned tar.bz2 bundles for S3 upload.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TTS_ROOT="${ROOT_DIR}/app/src/main/assets/models/tts"
OUT_DIR="${ROOT_DIR}/artifacts/tts-bundles"

usage() {
  cat >&2 <<'EOF'
usage: scripts/pack-tts-archives.sh [--model supertonic-3|kitten-mini-en-v0_1-fp16|all]

Creates artifacts/tts-bundles/tts-<model>.tar.bz2 from hydrated app assets.
Run scripts/download-voice-models.sh once from upstream HF/GitHub if trees are absent.
EOF
}

MODEL="all"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --model)
      MODEL="${2:-}"
      shift 2
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 64
      ;;
  esac
done

pack_one() {
  local name="$1"
  local src="${TTS_ROOT}/${name}"
  local archive="${OUT_DIR}/tts-${name}.tar.bz2"
  [[ -d "${src}" ]] || {
    echo "missing TTS tree: ${src#"${ROOT_DIR}/"}" >&2
    exit 66
  }
  mkdir -p "${OUT_DIR}"
  rm -f "${archive}"
  tar -cjf "${archive}" -C "${TTS_ROOT}" "${name}"
  echo "packed ${archive} ($(wc -c < "${archive}" | tr -d ' ') bytes, sha256=$(shasum -a 256 "${archive}" | awk '{print $1}'))"
}

case "${MODEL}" in
  supertonic-3) pack_one supertonic-3 ;;
  kitten-mini-en-v0_1-fp16) pack_one kitten-mini-en-v0_1-fp16 ;;
  all)
    pack_one supertonic-3
    pack_one kitten-mini-en-v0_1-fp16
    ;;
  *)
    usage
    exit 64
    ;;
esac
