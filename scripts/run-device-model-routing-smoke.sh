#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LANE="${1:-${DEVICE_LANE:-fallback}}"
PACKAGE_NAME="${PACKAGE_NAME:-com.google.research.air.cosmo}"
LITERTLM_MODEL_ID="${LITERTLM_MODEL_ID:-${MODEL_ID:-google-gemma-4-E2B-it-litertlm}}"
TEST_RUNNER="$PACKAGE_NAME.test/com.google.research.air.cosmo.GenesisTestRunner"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT_DIR/artifacts/device-model-routing/$STAMP-$LANE}"

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi

case "$LANE" in
  pixel10|fallback) ;;
  *)
    echo "usage: $0 [pixel10|fallback]" >&2
    echo "  pixel10  = require AICore visibility, then run production hybrid route smoke" >&2
    echo "  fallback = do not require AICore; prove ML Kit, LiteRT-LM, or remote route" >&2
    exit 64
    ;;
esac

mkdir -p "$ARTIFACT_DIR"

COSMO_ALLOW_MISSING_BINARIES="${COSMO_ALLOW_MISSING_BINARIES:-1}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
  -Pcosmo.localLlm.litertLmModelId="$LITERTLM_MODEL_ID" \
  :app:assembleDebug :app:assembleDebugAndroidTest

"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null

if [[ "${DEPLOY_LITERTLM:-0}" == "1" || "${DEPLOY_LITERTLM:-}" == "true" ]]; then
  MODEL_ID="$LITERTLM_MODEL_ID" "$ROOT_DIR/scripts/deploy-gemma4-litertlm.sh"
fi

"${ADB[@]}" logcat -c || true

run_test() {
  local class_name="$1"
  shift
  "${ADB[@]}" shell am instrument -w -r "$@" -e class "$class_name" "$TEST_RUNNER"
}

check_instrumentation_output() {
  local path="$1"
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS: test=initializationError' "$path"; then
    echo "Instrumentation failed; see $path" >&2
    return 1
  fi
  if grep -Eq 'Process crashed|INSTRUMENTATION_STATUS_CODE: -2|shortMsg=Process crashed' "$path"; then
    echo "Instrumentation crashed; see $path" >&2
    return 1
  fi
}

if [[ "$LANE" == "pixel10" ]]; then
  run_test \
    com.google.research.air.cosmo.lib.llm.AICoreDeviceSmokeTest \
    -e cosmo.requireAicore true | tee "$ARTIFACT_DIR/aicore.txt"
else
  run_test \
    com.google.research.air.cosmo.lib.llm.AICoreDeviceSmokeTest | tee "$ARTIFACT_DIR/aicore.txt"
fi

run_test \
  com.google.research.air.cosmo.lib.llm.DeviceModelRoutingSmokeTest \
  -e cosmo.productionLlmSmoke true | tee "$ARTIFACT_DIR/model-routing.txt"

"${ADB[@]}" logcat -d -v time \
  | grep -E 'CosmoDeviceSmoke|AICore|LiteRT|RemoteInference|AndroidRuntime|FATAL EXCEPTION|ANR' \
  > "$ARTIFACT_DIR/logcat-filtered.txt" || true

check_instrumentation_output "$ARTIFACT_DIR/aicore.txt"
check_instrumentation_output "$ARTIFACT_DIR/model-routing.txt"

echo "Artifacts: $ARTIFACT_DIR"
