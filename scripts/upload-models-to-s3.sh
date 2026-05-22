#!/usr/bin/env bash
# Upload local files listed in model-manifest.json to s3://$NEURAL_MODELS_S3_BUCKET/v$VERSION/
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/config/models-s3.env"

MANIFEST="${ROOT_DIR}/app/src/main/assets/model-manifest.json"
PREFIX="v${NEURAL_MODELS_VERSION}"

command -v aws >/dev/null || { echo "aws CLI required" >&2; exit 69; }

python3 - "$MANIFEST" "$ROOT_DIR" "$NEURAL_MODELS_S3_BUCKET" "$PREFIX" "$NEURAL_MODELS_S3_REGION" <<'PY'
import json, subprocess, sys
from pathlib import Path

manifest, root, bucket, prefix, region = sys.argv[1:6]
root = Path(root)
data = json.load(open(manifest, encoding="utf-8"))
n = 0
SKIP_IDS = frozenset({"llm_gemma_4_e2b_litertlm"})  # publish via HTTPS→S3 stream, not laptop upload
for m in data["models"]:
    if m["id"] in SKIP_IDS:
        print(f"skip upload (direct-fetch only): {m['id']}", file=sys.stderr)
        continue
    if m.get("extractTo"):
        path = root / "artifacts/tts-bundles" / m["filename"]
    else:
        path = root / m.get("localPath", f"app/src/main/assets/models/{m['filename']}")
    if not path.is_file():
        print(f"skip (no file): {m['id']} {path}", file=sys.stderr)
        continue
    dest = f"s3://{bucket}/{prefix}/{m['filename']}"
    subprocess.run(["aws", "s3", "cp", str(path), dest, "--region", region], check=True)
    print(dest)
    n += 1
print(f"uploaded {n} file(s) to s3://{bucket}/{prefix}/", file=sys.stderr)
PY
