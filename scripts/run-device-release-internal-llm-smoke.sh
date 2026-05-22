#!/usr/bin/env bash
# Release-internal APK: prove default LLM binding is production route (not SimulationLLM).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="${PACKAGE_NAME:-com.google.research.air.cosmo}"
LITERTLM_MODEL_ID="${LITERTLM_MODEL_ID:-${MODEL_ID:-google-gemma-4-E2B-it-litertlm}}"
TEST_RUNNER="$PACKAGE_NAME.test/com.google.research.air.cosmo.GenesisTestRunner"
TEST_CLASS="com.google.research.air.cosmo.lib.llm.ReleaseInternalLlmDeviceSmokeTest"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT_DIR/artifacts/device-release-internal-llm/$STAMP}"

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi

device_count="$("${ADB[@]}" devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
if [[ "$device_count" -eq 0 ]]; then
  echo "No adb device visible." >&2
  exit 1
fi

mkdir -p "$ARTIFACT_DIR"

COSMO_ALLOW_MISSING_BINARIES="${COSMO_ALLOW_MISSING_BINARIES:-1}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
  -Pcosmo.localLlm.litertLmModelId="$LITERTLM_MODEL_ID" \
  :app:assembleReleaseInternal :app:assembleDebugAndroidTest

APP_APK="$ROOT_DIR/app/build/outputs/apk/releaseInternal/app-releaseInternal.apk"
TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [[ "${DEPLOY_LITERTLM:-1}" == "1" || "${DEPLOY_LITERTLM:-}" == "true" ]]; then
  MODEL_ID="$LITERTLM_MODEL_ID" "$ROOT_DIR/scripts/deploy-gemma4-litertlm.sh"
fi

"${ADB[@]}" install -r -t -g "$APP_APK" >/dev/null
"${ADB[@]}" install -r -t -g "$TEST_APK" >/dev/null

"${ADB[@]}" logcat -c || true
"${ADB[@]}" shell am instrument -w -r \
  -e cosmo.releaseInternalLlmSmoke true \
  -e class "$TEST_CLASS" \
  "$TEST_RUNNER" | tee "$ARTIFACT_DIR/release-internal-llm.txt"

"${ADB[@]}" logcat -d -v time \
  | grep -E 'ReleaseInternalLlm|LiteRT|AICore|RemoteInference|AndroidRuntime|FATAL' \
  > "$ARTIFACT_DIR/logcat-filtered.txt" || true

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS: test=initializationError' "$ARTIFACT_DIR/release-internal-llm.txt"; then
  echo "Release-internal LLM smoke failed; see $ARTIFACT_DIR/release-internal-llm.txt" >&2
  exit 1
fi

echo "Release-internal LLM smoke passed."
echo "Artifacts: $ARTIFACT_DIR"
