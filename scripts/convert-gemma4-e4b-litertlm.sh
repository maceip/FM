#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID="${MODEL_ID:-google/gemma-4-E4B-it}"
APP_MODEL_ID="${APP_MODEL_ID:-google-gemma-4-E4B-it-litertlm}"
SOURCE_DIR="${SOURCE_DIR:-$ROOT_DIR/artifacts/models/google-gemma-4-E4B-it-safetensors}"
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to derive the LiteRT-LM model storage path." >&2
  exit 1
fi
APP_MODEL_STORAGE_ID="$(python3 "$ROOT_DIR/scripts/model-storage-id.py" "$APP_MODEL_ID")"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/artifacts/models/$APP_MODEL_STORAGE_ID}"
VENV_DIR="${VENV_DIR:-$ROOT_DIR/.venv-litert-convert}"
PYTHON_BIN="$VENV_DIR/bin/python"
PREFILL_LENGTHS="${PREFILL_LENGTHS:-[256]}"
CACHE_LENGTH="${CACHE_LENGTH:-4096}"
QUANTIZATION_RECIPE="${QUANTIZATION_RECIPE:-dynamic_wi8_afp32}"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required for the LiteRT conversion environment." >&2
  exit 1
fi

mkdir -p "$SOURCE_DIR" "$OUTPUT_DIR"

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

export HF_XET_HIGH_PERFORMANCE="${HF_XET_HIGH_PERFORMANCE:-1}"

"$PYTHON_BIN" - "$MODEL_ID" "$SOURCE_DIR" <<'PY'
import hashlib
import json
import pathlib
import sys

from huggingface_hub import model_info, snapshot_download

model_id, source_dir_arg = sys.argv[1], sys.argv[2]
source_dir = pathlib.Path(source_dir_arg)
allow_patterns = [
    "README.md",
    "chat_template.jinja",
    "config.json",
    "generation_config.json",
    "model.safetensors",
    "processor_config.json",
    "tokenizer.json",
    "tokenizer_config.json",
]

info = model_info(model_id, files_metadata=True)
expected_files = {
    sibling.rfilename: sibling.size
    for sibling in info.siblings
    if sibling.rfilename in allow_patterns
}

snapshot_download(
    repo_id=model_id,
    local_dir=source_dir,
    allow_patterns=allow_patterns,
    max_workers=8,
)

missing = [name for name in allow_patterns if not source_dir.joinpath(name).exists()]
if missing:
    raise SystemExit(f"Missing downloaded model files: {missing}")

source_hashes = {}
for name, expected_size in sorted(expected_files.items()):
    path = source_dir / name
    actual_size = path.stat().st_size
    if expected_size is not None and actual_size != expected_size:
        raise SystemExit(
            f"Size mismatch for {name}: expected {expected_size}, got {actual_size}"
        )
    if name in {"model.safetensors", "tokenizer.json"}:
        sha256 = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                sha256.update(chunk)
        source_hashes[name] = {
            "bytes": actual_size,
            "sha256": sha256.hexdigest(),
        }

source_dir.joinpath("source.metadata.json").write_text(
    json.dumps(
        {
            "modelId": model_id,
            "revision": info.sha,
            "files": source_hashes,
        },
        indent=2,
        sort_keys=True,
    )
    + "\n",
    encoding="utf-8",
)

print(f"Downloaded {model_id} at revision {info.sha}")
PY

"$PYTHON_BIN" -m litert_torch.generative.export_hf \
  "$SOURCE_DIR" \
  "$OUTPUT_DIR" \
  --task=text_generation \
  --prefill_lengths="$PREFILL_LENGTHS" \
  --cache_length="$CACHE_LENGTH" \
  --quantization_recipe="$QUANTIZATION_RECIPE" \
  --externalize_embedder=True \
  --bundle_litert_lm=True \
  --use_jinja_template=True \
  --trust_remote_code=False \
  --keep_temporary_files=True

"$PYTHON_BIN" - "$MODEL_ID" "$SOURCE_DIR" "$OUTPUT_DIR" <<'PY'
import hashlib
import json
import pathlib
import struct
import sys

from ai_edge_litert.internal import litertlm_core
from ai_edge_litert.internal import litertlm_header_schema_py_generated as schema
from huggingface_hub import model_info

model_id, source_dir_arg, output_dir_arg = sys.argv[1:]
source_dir = pathlib.Path(source_dir_arg)
output_dir = pathlib.Path(output_dir_arg)
output_model = output_dir / "model.litertlm"
if not output_model.exists():
    raise SystemExit(f"LiteRT-LM export did not produce {output_model}")

def string_value(item):
    if item.ValueType() != schema.VData.StringValue:
        return None
    value = schema.StringValue()
    value.Init(item.Value().Bytes, item.Value().Pos)
    raw = value.Value()
    return raw.decode("utf-8") if raw else None

with output_model.open("rb") as stream:
    if stream.read(8) != litertlm_core.HEADER_MAGIC_BYTES:
        raise SystemExit(f"{output_model} is not a LiteRT-LM file")
    stream.seek(litertlm_core.HEADER_END_LOCATION_BYTE_OFFSET)
    header_end = struct.unpack("<Q", stream.read(8))[0]
    stream.seek(litertlm_core.HEADER_BEGIN_BYTE_OFFSET)
    header = stream.read(header_end - litertlm_core.HEADER_BEGIN_BYTE_OFFSET)

metadata = schema.LiteRTLMMetaData.GetRootAs(header, 0)
section_metadata = metadata.SectionMetadata()
model_types = set()
max_end_offset = 0
for i in range(section_metadata.ObjectsLength()):
    section = section_metadata.Objects(i)
    max_end_offset = max(max_end_offset, section.EndOffset())
    for j in range(section.ItemsLength()):
        item = section.Items(j)
        key = item.Key().decode("utf-8") if item.Key() else ""
        if key == "model_type":
            value = string_value(item)
            if value:
                model_types.add(value)

actual_size = output_model.stat().st_size
if actual_size < max_end_offset:
    raise SystemExit(
        f"{output_model} is incomplete: {actual_size} bytes, expected at least {max_end_offset}"
    )

required_model_types = {
    "tf_lite_prefill_decode",
    "tf_lite_embedder",
    "tf_lite_per_layer_embedder",
}
missing = required_model_types - model_types
if missing:
    raise SystemExit(f"{output_model} is missing LiteRT-LM sections: {sorted(missing)}")

sha256 = hashlib.sha256()
with output_model.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        sha256.update(chunk)
digest = sha256.hexdigest()
output_dir.joinpath("model.litertlm.sha256").write_text(
    f"{digest}  {output_model}\n",
    encoding="utf-8",
)

source_metadata_path = source_dir / "source.metadata.json"
source_metadata = {}
if source_metadata_path.exists():
    source_metadata = json.loads(source_metadata_path.read_text(encoding="utf-8"))
info = model_info(model_id)
metadata_path = output_dir / "model.metadata.json"
metadata_path.write_text(
    json.dumps(
        {
            "modelId": model_id,
            "source": {
                "repo": f"https://huggingface.co/{model_id}",
                "revision": source_metadata.get("revision") or info.sha,
                "files": source_metadata.get("files", {}),
            },
            "runtime": "LiteRT-LM",
            "artifact": {
                "file": "model.litertlm",
                "bytes": actual_size,
                "sha256": digest,
                "modelTypes": sorted(model_types),
            },
        },
        indent=2,
        sort_keys=True,
    )
    + "\n",
    encoding="utf-8",
)

print(f"Converted LiteRT-LM artifact: {output_model}")
print(f"SHA-256: {digest}")
PY
