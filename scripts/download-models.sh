#!/usr/bin/env bash
# Sync models from model-manifest.json (S3). Missing or stale files are re-fetched.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="${ROOT_DIR}/app/src/main/assets/model-manifest.json"
ASSETS="${ROOT_DIR}/app/src/main/assets/models"
VERIFY_ONLY=0
BUNDLED_ONLY=0
ONLY=""
# Skip full SHA on huge artifacts (size match is enough for verify-only).
MAX_SHA_BYTES=209715200

[[ -f "${ROOT_DIR}/config/models-s3.env" ]] && source "${ROOT_DIR}/config/models-s3.env"
S3_REGION="${NEURAL_MODELS_S3_REGION:-eu-central-1}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verify-only) VERIFY_ONLY=1 ;;
    --bundled-only) BUNDLED_ONLY=1 ;;
    --only) ONLY="$2"; shift ;;
    -h | --help)
      cat >&2 <<'EOF'
usage: scripts/download-models.sh [--verify-only] [--bundled-only] [--only id]

  (default)     Download anything missing or checksum/size mismatched from S3.
  --verify-only Check on-disk artifacts; exit non-zero if a required entry is wrong.
  --bundled-only  Only APK asset paths (app/src/main/assets/...); skips artifacts/ LLM.
  --only id     Comma-separated manifest ids.
EOF
      exit 0
      ;;
    --allow-missing-binaries)
      # Deprecated: same as --bundled-only for verify (CI compat).
      BUNDLED_ONLY=1
      ;;
    *) echo "unknown arg: $1" >&2; exit 64 ;;
  esac
  shift
done

command -v python3 >/dev/null || { echo "python3 required" >&2; exit 69; }

sha256_file() { shasum -a 256 "$1" | awk '{print $1}'; }
size_file() { wc -c < "$1" | tr -d ' '; }

fetch() {
  local url="$1" dest="$2"
  mkdir -p "$(dirname "$dest")"
  local tmp="${dest}.part"
  if [[ "$url" == s3://* ]]; then
    command -v aws >/dev/null || { echo "aws CLI required for $url" >&2; exit 69; }
    aws s3 cp "$url" "$tmp" --region "$S3_REGION"
  else
    curl -fsSL --retry 3 "$url" -o "$tmp"
  fi
  mv "$tmp" "$dest"
}

check_file() {
  local path="$1" want_sha="$2" want_size="$3" label="$4"
  [[ -f "$path" ]] || return 1
  local got_size
  got_size="$(size_file "$path")"
  [[ "$got_size" == "$want_size" ]] || {
    echo "size mismatch $label: want $want_size got $got_size" >&2; return 1; }
  if [[ "$want_size" -le "$MAX_SHA_BYTES" ]]; then
    [[ "$(sha256_file "$path")" == "$want_sha" ]] || {
      echo "sha256 mismatch $label" >&2; return 1; }
  fi
}

extract_ok() {
  local dir="$1"
  [[ -d "$dir" && -n "$(find "$dir" -type f 2>/dev/null | head -1)" ]]
}

rows="$(mktemp)"
trap 'rm -f "$rows"' EXIT
python3 - "$MANIFEST" "$ONLY" "$BUNDLED_ONLY" >"$rows" <<'PY'
import json, sys
manifest = json.load(open(sys.argv[1], encoding="utf-8"))
only = {x.strip() for x in sys.argv[2].split(",") if x.strip()}
bundled_only = sys.argv[3] == "1"
asset_root = manifest.get("assetRoot", "app/src/main/assets/models")
for m in manifest["models"]:
    if only and m["id"] not in only:
        continue
    local = m.get("localPath", f"{asset_root}/{m['filename']}")
    if bundled_only and not local.startswith("app/src/main/assets/"):
        continue
    extract = m.get("extractTo") or "-"
    print("\t".join([m["id"], m["filename"], local, extract, m["url"], m["sha256"], str(m["sizeBytes"]), str(m["required"]).lower()]))
PY

failures=0
synced=0
while IFS=$'\t' read -r id filename local_path extract_to url sha size required; do
  [[ "$extract_to" == "-" ]] && extract_to=""
  [[ -z "$id" ]] && continue
  target="${ROOT_DIR}/${local_path}"
  ok=0

  if [[ -n "$extract_to" ]]; then
    dir="${ROOT_DIR}/app/src/main/assets/models/${extract_to}"
    if extract_ok "$dir"; then
      ok=1
    elif [[ "$VERIFY_ONLY" == 1 ]]; then
      ok=0
    else
      archive="${ASSETS}/${filename}"
      rm -f "$archive"
      fetch "$url" "$archive"
      check_file "$archive" "$sha" "$size" "$filename" || exit 66
      rm -rf "$dir"
      mkdir -p "$(dirname "$dir")"
      tar -xjf "$archive" -C "$(dirname "$dir")"
      extract_ok "$dir" || { echo "extract failed: $dir" >&2; exit 66; }
      ok=1
      synced=$((synced + 1))
    fi
  else
    if check_file "$target" "$sha" "$size" "$filename" 2>/dev/null; then
      ok=1
    elif [[ "$VERIFY_ONLY" == 1 ]]; then
      ok=0
    else
      rm -f "$target"
      fetch "$url" "$target"
      check_file "$target" "$sha" "$size" "$filename" || exit 66
      ok=1
      synced=$((synced + 1))
    fi
  fi

  if [[ "$ok" == 0 ]]; then
    if [[ "$required" == "true" ]]; then
      echo "missing or invalid (required): $id → $local_path" >&2
      failures=$((failures + 1))
    fi
  fi
done <"$rows"

if [[ "$failures" -gt 0 ]]; then
  exit 66
fi
if [[ "$VERIFY_ONLY" == 1 ]]; then
  echo "models verified (${MANIFEST#"${ROOT_DIR}/"})"
else
  echo "models ok (${MANIFEST#"${ROOT_DIR}/"}${synced:+, fetched $synced})"
fi
