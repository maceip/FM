#!/usr/bin/env bash
# Run ambient audio fixtures and emit Inherent/raw-audio quality scorecard.
#
# Usage:
#   scripts/run-inherent-scorecard.sh              # report only (default)
#   ENFORCE_INHERENT_QUALITY=1 scripts/run-inherent-scorecard.sh  # fail if top-1 misses
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURES_DIR="$ROOT_DIR/scripts/test-fixtures"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT_DIR/artifacts/inherent-scorecard/$STAMP}"

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  export ANDROID_SERIAL
fi

mkdir -p "$ARTIFACT_DIR"

if [[ ! -d "$FIXTURES_DIR/audio" ]] || ! ls "$FIXTURES_DIR/audio"/*.wav >/dev/null 2>&1; then
  echo "==> generating audio fixtures"
  "$FIXTURES_DIR/generate-audio-fixtures.sh"
fi

export ARTIFACT_DIR
export ENFORCE_INHERENT_QUALITY="${ENFORCE_INHERENT_QUALITY:-0}"
export ASSERT_AUDIO_FIXTURES=1

echo "==> running ambient fixture harness (ENFORCE_INHERENT_QUALITY=$ENFORCE_INHERENT_QUALITY)"
"$FIXTURES_DIR/run-audio-fixtures.sh" ambient 2>&1 | tee "$ARTIFACT_DIR/run.log"

latest_quality="$(find "$ROOT_DIR/artifacts/audio-run" -name inherent-quality.json 2>/dev/null | sort | tail -1 || true)"
if [[ -n "$latest_quality" && -f "$latest_quality" ]]; then
  cp "$latest_quality" "$ARTIFACT_DIR/inherent-quality.json"
  echo "==> scorecard: $ARTIFACT_DIR/inherent-quality.json"
  python3 - <<PY
import json
from pathlib import Path
report = json.loads(Path("$ARTIFACT_DIR/inherent-quality.json").read_text())
summary = report.get("summary", report)
print("positive_scored:", summary.get("positive_scored"), "positive_passed:", summary.get("positive_passed"))
print("positive_all_head_top1:", summary.get("positive_all_head_top1"))
print("negative_clean:", summary.get("negative_clean"), "negative_total:", summary.get("negative_total"))
PY
else
  echo "No inherent-quality.json produced; inspect $ARTIFACT_DIR/run.log" >&2
  exit 1
fi

echo "Inherent scorecard complete: $ARTIFACT_DIR"
