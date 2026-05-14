#!/usr/bin/env bash
set -euo pipefail

# Lightweight CI gate: JSON manifest must declare version 1, assetRoot, and at least one model entry.
# Does not verify bundled bytes on disk (use scripts/download-models.sh --verify-only for that).

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="${ROOT_DIR}/app/src/main/assets/model-manifest.json"

if [[ ! -f "${MANIFEST}" ]]; then
  echo "model manifest not found: ${MANIFEST}" >&2
  exit 65
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 69
fi

python3 - "${MANIFEST}" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

if not isinstance(manifest, dict):
    print("model manifest must be a JSON object", file=sys.stderr)
    sys.exit(65)
if manifest.get("version") != 1:
    print("model manifest version must be 1", file=sys.stderr)
    sys.exit(65)
if manifest.get("assetRoot") != "app/src/main/assets/models":
    print('model manifest assetRoot must be "app/src/main/assets/models"', file=sys.stderr)
    sys.exit(65)

models = manifest.get("models")
if not isinstance(models, list) or len(models) == 0:
    print("model manifest models must be a non-empty array", file=sys.stderr)
    sys.exit(65)
PY
