#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_MANIFEST="${ROOT_DIR}/app/src/main/assets/model-manifest.json"
MODELS_DIR="${ROOT_DIR}/app/src/main/assets/models"

MANIFEST="${DEFAULT_MANIFEST}"
VERIFY_ONLY=0
ALLOW_EMPTY=0
ALLOW_MISSING_BINARIES=0

usage() {
  cat >&2 <<'EOF'
usage: scripts/download-models.sh [--manifest path] [--verify-only] [--allow-empty] [--allow-missing-binaries]

Reads app/src/main/assets/model-manifest.json by default. Each manifest entry must
declare id, filename, delivery, sha256, sizeBytes, mediaType, and required.
Remote entries must also declare an absolute HTTPS url. Bundled entries are
verified from app/src/main/assets/models.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
      MANIFEST="${2:-}"
      shift 2
      ;;
    --verify-only)
      VERIFY_ONLY=1
      shift
      ;;
    --allow-empty)
      ALLOW_EMPTY=1
      shift
      ;;
    --allow-missing-binaries)
      ALLOW_MISSING_BINARIES=1
      shift
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

if [[ ! -f "${MANIFEST}" ]]; then
  echo "model manifest not found: ${MANIFEST}" >&2
  exit 65
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to parse the JSON model manifest" >&2
  exit 69
fi

rows="$(mktemp)"
trap 'rm -f "${rows}"' EXIT

python3 - "${MANIFEST}" "${ALLOW_EMPTY}" > "${rows}" <<'PY'
import json
import re
import sys
from pathlib import PurePosixPath
from urllib.parse import urlparse

manifest_path = sys.argv[1]
allow_empty = sys.argv[2] == "1"

with open(manifest_path, "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

def fail(message):
    print(message, file=sys.stderr)
    raise SystemExit(65)

if not isinstance(manifest, dict):
    fail("model manifest must be a JSON object")
if manifest.get("version") != 1:
    fail("model manifest version must be 1")
if manifest.get("assetRoot") != "app/src/main/assets/models":
    fail("model manifest assetRoot must be app/src/main/assets/models")

models = manifest.get("models")
if not isinstance(models, list):
    fail("model manifest models must be an array")
if not models and not allow_empty:
    fail("model manifest does not contain any model entries")

seen_ids = set()
seen_files = set()
sha_re = re.compile(r"^[0-9a-f]{64}$")
filename_re = re.compile(r"^[A-Za-z0-9._-]+$")

for index, model in enumerate(models):
    if not isinstance(model, dict):
        fail(f"model entry {index} must be an object")
    model_id = model.get("id")
    filename = model.get("filename")
    delivery = model.get("delivery", "remote")
    url = model.get("url")
    sha256 = model.get("sha256")
    size_bytes = model.get("sizeBytes")
    media_type = model.get("mediaType")
    required = model.get("required")
    for field_name, value in (
        ("id", model_id),
        ("filename", filename),
        ("delivery", delivery),
        ("sha256", sha256),
        ("mediaType", media_type),
    ):
        if not isinstance(value, str) or not value.strip():
            fail(f"model entry {index} has invalid {field_name}")
    if not isinstance(size_bytes, int) or size_bytes <= 0:
        fail(f"model entry {model_id} has invalid sizeBytes")
    if not isinstance(required, bool):
        fail(f"model entry {model_id} has invalid required")
    if model_id in seen_ids:
        fail(f"duplicate model id: {model_id}")
    if filename in seen_files:
        fail(f"duplicate model filename: {filename}")
    if not filename_re.match(filename) or PurePosixPath(filename).name != filename or filename.startswith("."):
        fail(f"invalid model filename: {filename}")
    if not sha_re.match(sha256):
        fail(f"invalid sha256 for model {model_id}")
    if delivery not in ("bundled", "remote"):
        fail(f"model {model_id} has invalid delivery")
    if delivery == "remote":
        if not isinstance(url, str) or not url.strip():
            fail(f"model {model_id} has invalid url")
        parsed_url = urlparse(url)
        if parsed_url.scheme != "https" or not parsed_url.netloc:
            fail(f"model {model_id} url must be absolute HTTPS")
    elif url is not None:
        fail(f"bundled model {model_id} must not declare url")

    seen_ids.add(model_id)
    seen_files.add(filename)
    print("\t".join([model_id, filename, delivery, url or "-", sha256, str(size_bytes), str(required).lower()]))
PY

mkdir -p "${MODELS_DIR}"

downloaded=0
verified=0
while IFS=$'\t' read -r model_id filename delivery url sha256 size_bytes required; do
  [[ -z "${model_id}" ]] && continue

  target="${MODELS_DIR}/${filename}"

  if [[ "${VERIFY_ONLY}" -eq 1 || "${delivery}" == "bundled" ]]; then
    if [[ ! -f "${target}" ]]; then
      if [[ "${required}" == "true" ]]; then
        if [[ "${ALLOW_MISSING_BINARIES}" -eq 1 ]]; then
          echo "WARNING: required model missing but tolerated (--allow-missing-binaries): ${filename}" >&2
          continue
        fi
        echo "required model is missing: ${filename}" >&2
        exit 66
      fi
      continue
    fi
  else
    tmp="${target}.download"
    curl --proto '=https' --tlsv1.2 --fail --location --retry 3 --retry-delay 2 --output "${tmp}" "${url}"
    mv "${tmp}" "${target}"
    downloaded=$((downloaded + 1))
  fi

  actual_sha="$(shasum -a 256 "${target}" | awk '{print $1}')"
  if [[ "${actual_sha}" != "${sha256}" ]]; then
    echo "checksum mismatch for ${filename}: expected ${sha256}, got ${actual_sha}" >&2
    exit 66
  fi
  actual_size="$(wc -c < "${target}" | tr -d ' ')"
  if [[ "${actual_size}" != "${size_bytes}" ]]; then
    echo "size mismatch for ${filename}: expected ${size_bytes}, got ${actual_size}" >&2
    exit 66
  fi
  verified=$((verified + 1))
done < "${rows}"

if [[ "${VERIFY_ONLY}" -eq 0 && "${downloaded}" -eq 0 && "${verified}" -eq 0 && "${ALLOW_EMPTY}" -eq 0 ]]; then
  echo "model manifest does not contain any downloadable model entries" >&2
  exit 65
fi

if [[ "${VERIFY_ONLY}" -eq 0 && "${verified}" -gt 0 ]]; then
  checksums_tmp="$(mktemp)"
  (
    cd "${MODELS_DIR}"
    find . -maxdepth 1 -type f ! -name 'SHA256SUMS' -print0 | sort -z | xargs -0 shasum -a 256
  ) > "${checksums_tmp}"
  mv "${checksums_tmp}" "${MODELS_DIR}/SHA256SUMS"
fi
