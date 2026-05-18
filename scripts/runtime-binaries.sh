#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="${ROOT_DIR}/config/assets/runtime-binaries.json"
ACTION="download"
INCLUDE_OPTIONAL=0

usage() {
  cat >&2 <<'EOF'
usage: scripts/runtime-binaries.sh [download|verify|upload|verify-remote] [--include-optional] [--manifest path]

Hydrates or verifies the private S3-backed runtime binary set described by
config/assets/runtime-binaries.json. Required artifacts cover APK model assets,
SODA language packs, and recovered native libraries. Optional artifacts include
large local-only model bundles such as LiteRT-LM.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    download|verify|upload|verify-remote)
      ACTION="$1"
      shift
      ;;
    --include-optional)
      INCLUDE_OPTIONAL=1
      shift
      ;;
    --manifest)
      MANIFEST="${2:-}"
      shift 2
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

if [[ ! -f "$MANIFEST" ]]; then
  echo "runtime binary manifest not found: $MANIFEST" >&2
  exit 65
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to parse $MANIFEST" >&2
  exit 69
fi

if [[ "$ACTION" != "verify" ]] && ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required for $ACTION" >&2
  exit 69
fi

rows="$(mktemp)"
trap 'rm -f "$rows"' EXIT

python3 - "$MANIFEST" "$INCLUDE_OPTIONAL" > "$rows" <<'PY'
import json
import re
import sys
from pathlib import PurePosixPath
from urllib.parse import urlparse

manifest_path = sys.argv[1]
include_optional = sys.argv[2] == "1"
sha_re = re.compile(r"^[0-9a-f]{64}$")

def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(65)

with open(manifest_path, "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

if manifest.get("version") != 1:
    fail("runtime binary manifest version must be 1")
artifacts = manifest.get("artifacts")
if not isinstance(artifacts, list) or not artifacts:
    fail("runtime binary manifest artifacts must be a non-empty array")

seen_ids = set()
seen_paths = set()
for index, artifact in enumerate(artifacts):
    if not isinstance(artifact, dict):
        fail(f"artifact {index} must be an object")
    artifact_id = artifact.get("id")
    path = artifact.get("path")
    s3_uri = artifact.get("s3Uri")
    sha256 = artifact.get("sha256")
    size_bytes = artifact.get("sizeBytes")
    media_type = artifact.get("mediaType", "application/octet-stream")
    required = artifact.get("required")
    for field, value in (("id", artifact_id), ("path", path), ("s3Uri", s3_uri), ("sha256", sha256)):
        if not isinstance(value, str) or not value.strip():
            fail(f"artifact {index} has invalid {field}")
    if artifact_id in seen_ids:
        fail(f"duplicate artifact id: {artifact_id}")
    if path in seen_paths:
        fail(f"duplicate artifact path: {path}")
    if PurePosixPath(path).is_absolute() or ".." in PurePosixPath(path).parts:
        fail(f"artifact {artifact_id} path must be repo-relative: {path}")
    if not s3_uri.startswith("s3://"):
        fail(f"artifact {artifact_id} s3Uri must start with s3://")
    parsed = urlparse(s3_uri)
    if parsed.scheme != "s3" or not parsed.netloc or not parsed.path.strip("/"):
        fail(f"artifact {artifact_id} has invalid s3Uri: {s3_uri}")
    if not sha_re.match(sha256):
        fail(f"artifact {artifact_id} sha256 must be lowercase hex")
    if not isinstance(size_bytes, int) or size_bytes <= 0:
        fail(f"artifact {artifact_id} sizeBytes must be positive")
    if not isinstance(media_type, str) or not media_type.strip():
        fail(f"artifact {artifact_id} mediaType must be a non-empty string")
    if not isinstance(required, bool):
        fail(f"artifact {artifact_id} required must be boolean")
    seen_ids.add(artifact_id)
    seen_paths.add(path)
    if required or include_optional:
        print(
            "\t".join(
                [
                    artifact_id,
                    path,
                    s3_uri,
                    parsed.netloc,
                    parsed.path.strip("/"),
                    sha256,
                    str(size_bytes),
                    str(required).lower(),
                    media_type,
                ]
            )
        )
PY

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

verify_one() {
  local path="$1" expected_sha="$2" expected_size="$3"
  if [[ ! -f "$path" ]]; then
    return 1
  fi
  local actual_size actual_sha
  actual_size="$(size_file "$path")"
  if [[ "$actual_size" != "$expected_size" ]]; then
    echo "size mismatch for $path: expected $expected_size, got $actual_size" >&2
    return 1
  fi
  actual_sha="$(sha_file "$path")"
  if [[ "$actual_sha" != "$expected_sha" ]]; then
    echo "checksum mismatch for $path: expected $expected_sha, got $actual_sha" >&2
    return 1
  fi
}

verify_remote_one() {
  local bucket="$1" key="$2" expected_sha="$3" expected_size="$4" rel_path="$5"
  local metadata
  if ! metadata="$(aws s3api head-object \
    --bucket "$bucket" \
    --key "$key" \
    --query 'join(`\t`, [to_string(ContentLength), Metadata.sha256 || ``, ServerSideEncryption || ``])' \
    --output text)"; then
    echo "remote object missing for $rel_path: s3://$bucket/$key" >&2
    return 1
  fi

  local actual_size actual_sha encryption
  IFS=$'\t' read -r actual_size actual_sha encryption <<< "$metadata"
  if [[ "$actual_size" != "$expected_size" ]]; then
    echo "remote size mismatch for $rel_path: expected $expected_size, got $actual_size" >&2
    return 1
  fi
  if [[ "$actual_sha" != "$expected_sha" ]]; then
    echo "remote checksum metadata mismatch for $rel_path: expected $expected_sha, got ${actual_sha:-<missing>}" >&2
    return 1
  fi
  if [[ -z "${encryption:-}" || "$encryption" == "None" ]]; then
    echo "remote object is not server-side encrypted for $rel_path" >&2
    return 1
  fi
}

count=0
while IFS=$'\t' read -r artifact_id rel_path s3_uri s3_bucket s3_key sha256 size_bytes required media_type; do
  [[ -z "${artifact_id:-}" ]] && continue
  target="${ROOT_DIR}/${rel_path}"
  case "$ACTION" in
    verify)
      verify_one "$target" "$sha256" "$size_bytes"
      ;;
    verify-remote)
      verify_remote_one "$s3_bucket" "$s3_key" "$sha256" "$size_bytes" "$rel_path"
      ;;
    download)
      if verify_one "$target" "$sha256" "$size_bytes" >/dev/null 2>&1; then
        echo "ok: $rel_path"
      else
        mkdir -p "$(dirname "$target")"
        tmp="${target}.download"
        echo "download: $s3_uri -> $rel_path"
        aws s3 cp --only-show-errors "$s3_uri" "$tmp"
        mv "$tmp" "$target"
        verify_one "$target" "$sha256" "$size_bytes"
      fi
      ;;
    upload)
      verify_one "$target" "$sha256" "$size_bytes"
      echo "upload: $rel_path -> $s3_uri"
      aws s3 cp --only-show-errors \
        --content-type "$media_type" \
        --metadata "sha256=$sha256" \
        "$target" "$s3_uri"
      verify_remote_one "$s3_bucket" "$s3_key" "$sha256" "$size_bytes" "$rel_path"
      ;;
  esac
  count=$((count + 1))
done < "$rows"

echo "$ACTION complete: $count artifact(s)"
