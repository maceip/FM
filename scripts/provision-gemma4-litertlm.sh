#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID="${MODEL_ID:-litert-community/gemma-4-E4B-it-litert-lm}"
APP_MODEL_ID="${APP_MODEL_ID:-google-gemma-4-E4B-it-litertlm}"
MODEL_FILENAME="${MODEL_FILENAME:-gemma-4-E4B-it.litertlm}"
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to derive the LiteRT-LM model storage path." >&2
  exit 1
fi
APP_MODEL_STORAGE_ID="$(python3 "$ROOT_DIR/scripts/model-storage-id.py" "$APP_MODEL_ID")"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/artifacts/models/$APP_MODEL_STORAGE_ID}"
OUTPUT_MODEL="$OUTPUT_DIR/model.litertlm"
VENV_DIR="${VENV_DIR:-$ROOT_DIR/.venv-litert-convert}"
PYTHON_BIN="$VENV_DIR/bin/python"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required for the LiteRT provisioning environment." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

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
  ai-edge-litert==2.1.4 \
  huggingface_hub==1.14.0

MANIFEST_ENTRY_ID="${MANIFEST_ENTRY_ID:-$APP_MODEL_ID}"
MANIFEST_ENTRY_FILENAME="${MANIFEST_ENTRY_FILENAME:-gemma-4-E4B-it.litertlm}"
MANIFEST_ENTRY_URL="${MANIFEST_ENTRY_URL:-https://huggingface.co/$MODEL_ID/resolve/main/$MODEL_FILENAME}"

"$PYTHON_BIN" - "$MODEL_ID" "$MODEL_FILENAME" "$OUTPUT_DIR" "$OUTPUT_MODEL" \
  "$MANIFEST_ENTRY_ID" "$MANIFEST_ENTRY_FILENAME" "$MANIFEST_ENTRY_URL" <<'PY'
import json
import os
import pathlib
import shutil
import struct
import sys

from ai_edge_litert.internal import litertlm_core
from ai_edge_litert.internal import litertlm_header_schema_py_generated as schema
from huggingface_hub import hf_hub_download, model_info

(
    model_id,
    model_filename,
    output_dir,
    output_model,
    manifest_entry_id,
    manifest_entry_filename,
    manifest_entry_url,
) = sys.argv[1:]
output_dir = pathlib.Path(output_dir)
output_model = pathlib.Path(output_model)

downloaded = pathlib.Path(
    hf_hub_download(
        repo_id=model_id,
        filename=model_filename,
        local_dir=output_dir,
    )
)
if downloaded != output_model:
    shutil.copyfile(downloaded, output_model)

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
    "tf_lite_mtp_drafter",
}
missing = required_model_types - model_types
if missing:
    raise SystemExit(f"{output_model} is missing LiteRT-LM sections: {sorted(missing)}")

sha256 = __import__("hashlib").sha256()
with output_model.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        sha256.update(chunk)
digest = sha256.hexdigest()
output_dir.joinpath("model.litertlm.sha256").write_text(
    f"{digest}  {output_model}\n",
    encoding="utf-8",
)

info = model_info(model_id)
metadata_path = output_dir / "model.metadata.json"
metadata_path.write_text(
    json.dumps(
        {
            "modelId": model_id,
            "source": {
                "repo": f"https://huggingface.co/{model_id}",
                "revision": info.sha,
                "file": model_filename,
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

manifest_entry = {
    "id": manifest_entry_id,
    "filename": manifest_entry_filename,
    "url": manifest_entry_url,
    "sha256": digest,
    "sizeBytes": actual_size,
    "mediaType": "application/x-litertlm",
    "required": True,
}
output_dir.joinpath("model.manifest-entry.json").write_text(
    json.dumps(manifest_entry, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)

print(f"Provisioned LiteRT-LM artifact: {output_model}")
print(f"SHA-256: {digest}")
print(f"Manifest entry: {output_dir / 'model.manifest-entry.json'}")
PY
