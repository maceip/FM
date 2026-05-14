#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSISTANT_MODEL_ID="${ASSISTANT_MODEL_ID:-google/gemma-4-E4B-it-assistant}"
SOURCE_DIR="${SOURCE_DIR:-$ROOT_DIR/artifacts/models/google-gemma-4-E4B-it-assistant-safetensors}"
VENV_DIR="${VENV_DIR:-$ROOT_DIR/.venv-litert-convert}"
PYTHON_BIN="$VENV_DIR/bin/python"

EXPECTED_MODEL_SHA256="12875062fc25c51e8fa9b62abd2de7ad48b7d63f8559d5d604fbd5a3d6bcff16"
EXPECTED_TOKENIZER_SHA256="75a6583c1a418e2bbd79c60d95d28e0f5bf549ad3f2990b5bdb5238c6c2bf70c"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required for the LiteRT conversion environment." >&2
  exit 1
fi

mkdir -p "$SOURCE_DIR"

if [[ ! -x "$PYTHON_BIN" ]]; then
  uv venv --python 3.12 "$VENV_DIR"
fi

install_with_retries() {
  local attempt
  for attempt in 1 2 3 4; do
    if UV_NATIVE_TLS=true UV_HTTP_TIMEOUT=600 uv pip install --python "$PYTHON_BIN" "$@"; then
      return 0
    fi
    sleep "$((attempt * 2))"
  done
  return 1
}

install_with_retries \
  torch==2.11.0 \
  ai-edge-litert==2.1.4 \
  litert-converter==0.1.0 \
  litert-torch==0.9.0 \
  transformers==5.8.0 \
  safetensors==0.7.0 \
  sentencepiece==0.2.1 \
  huggingface_hub==1.14.0

"$PYTHON_BIN" - "$ASSISTANT_MODEL_ID" "$SOURCE_DIR" <<'PY'
import sys
from huggingface_hub import snapshot_download

model_id, source_dir = sys.argv[1], sys.argv[2]
snapshot_download(
    repo_id=model_id,
    local_dir=source_dir,
    allow_patterns=[
        "README.md",
        "config.json",
        "generation_config.json",
        "model.safetensors",
        "tokenizer.json",
        "tokenizer_config.json",
    ],
)
PY

verify_sha256() {
  local path="$1"
  local expected="$2"
  local actual
  actual="$(shasum -a 256 "$path" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "SHA-256 mismatch for $path: expected $expected, got $actual" >&2
    exit 1
  fi
}

verify_sha256 "$SOURCE_DIR/model.safetensors" "$EXPECTED_MODEL_SHA256"
verify_sha256 "$SOURCE_DIR/tokenizer.json" "$EXPECTED_TOKENIZER_SHA256"

"$PYTHON_BIN" - "$SOURCE_DIR" <<'PY'
import pathlib
import sys
from transformers import AutoConfig

source_dir = pathlib.Path(sys.argv[1])
config = AutoConfig.from_pretrained(source_dir)
if config.model_type != "gemma4_assistant":
    raise SystemExit(f"Expected gemma4_assistant, got {config.model_type}")

raise SystemExit(
    "google/gemma-4-E4B-it-assistant is an MTP drafter checkpoint, not a "
    "standalone LLM. Public litert_torch 0.9.0 does not expose a Gemma 4 "
    "MTP_DRAFTER export path; direct export fails because the model requires "
    "target-model inputs_embeds and shared_kv_states. Use "
    "scripts/provision-gemma4-litertlm.sh for the production LiteRT-LM bundle "
    "that contains the Gemma 4 E4B target model and tf_lite_mtp_drafter."
)
PY
