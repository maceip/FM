#!/usr/bin/env bash
# Full launch proof suite: JVM golden + device proofs (when adb is connected).
#
# Usage:
#   scripts/run-launch-device-proofs.sh
#   scripts/run-launch-device-proofs.sh --skip-release-llm   # faster; skips releaseInternal (~minutes)
#   scripts/run-launch-device-proofs.sh --skip-scorecard     # skip inherent ambient harness
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKIP_RELEASE_LLM=0
SKIP_SCORECARD=0

for arg in "$@"; do
  case "$arg" in
    --skip-release-llm) SKIP_RELEASE_LLM=1 ;;
    --skip-scorecard) SKIP_SCORECARD=1 ;;
    -h | --help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg" >&2
      exit 64
      ;;
  esac
done

echo "==> Phase 0: JVM gatekeeper fulfillment golden"
"$ROOT_DIR/scripts/run-gatekeeper-golden.sh" --unit-only

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi
device_count="$("${ADB[@]}" devices 2>/dev/null | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
if [[ "$device_count" -eq 0 ]]; then
  echo "No adb device; skipping device phases. Connect hardware and re-run." >&2
  exit 0
fi

echo "==> Phase 1: Device text gatekeeper golden (+ presence)"
"$ROOT_DIR/scripts/run-device-gatekeeper-golden-test.sh"

echo "==> Phase 2: Device audio gatekeeper golden (SODA WAV → skill → deck)"
"$ROOT_DIR/scripts/run-device-gatekeeper-audio-golden-test.sh"

if [[ "$SKIP_SCORECARD" -eq 0 ]]; then
  echo "==> Phase 3: Inherent ambient scorecard (report mode)"
  ENFORCE_INHERENT_QUALITY=0 "$ROOT_DIR/scripts/run-inherent-scorecard.sh"
else
  echo "==> Phase 3: skipped (--skip-scorecard)"
fi

if [[ "$SKIP_RELEASE_LLM" -eq 0 ]]; then
  echo "==> Phase 4: Release-internal default LLM smoke"
  DEPLOY_LITERTLM="${DEPLOY_LITERTLM:-1}" "$ROOT_DIR/scripts/run-device-release-internal-llm-smoke.sh"
else
  echo "==> Phase 4: skipped (--skip-release-llm)"
fi

echo "All launch device proofs passed."
