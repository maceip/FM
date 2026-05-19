#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="${PACKAGE_NAME:-com.google.research.air.cosmo}"
TEST_RUNNER="$PACKAGE_NAME.test/com.google.research.air.cosmo.GenesisTestRunner"
TEST_CLASS="com.google.research.air.cosmo.fulfillment.GatekeeperFulfillmentGoldenDeviceTest"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT_DIR/artifacts/device-gatekeeper-golden/$STAMP}"

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi

mkdir -p "$ARTIFACT_DIR"

device_count="$("${ADB[@]}" devices | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
if [[ "$device_count" -eq 0 ]]; then
  echo "No adb device visible. Plug in USB or run: adb connect <ip>:<port>" >&2
  exit 1
fi

"${ADB[@]}" devices -l | tee "$ARTIFACT_DIR/adb-devices.txt"
"${ADB[@]}" shell getprop ro.product.model > "$ARTIFACT_DIR/device-model.txt" || true
"${ADB[@]}" shell getprop ro.build.fingerprint > "$ARTIFACT_DIR/build-fingerprint.txt" || true

COSMO_ALLOW_MISSING_BINARIES="${COSMO_ALLOW_MISSING_BINARIES:-1}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug :app:assembleDebugAndroidTest

"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null

"${ADB[@]}" shell pm grant "$PACKAGE_NAME" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
"${ADB[@]}" shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

"${ADB[@]}" logcat -c || true
"${ADB[@]}" shell am instrument -w -r \
  -e cosmo.gatekeeperGolden true \
  -e class "$TEST_CLASS" \
  "$TEST_RUNNER" | tee "$ARTIFACT_DIR/gatekeeper-golden.txt"

"${ADB[@]}" logcat -d -v time \
  | grep -E 'GatekeeperGoldenDevice|FulfillmentOrchestrator|GatekeeperDebug|CosmoCoreDeviceSmoke|AndroidRuntime|FATAL EXCEPTION|INSTRUMENTATION' \
  > "$ARTIFACT_DIR/logcat-filtered.txt" || true

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS: test=initializationError' "$ARTIFACT_DIR/gatekeeper-golden.txt"; then
  echo "Gatekeeper golden device test failed; see $ARTIFACT_DIR/gatekeeper-golden.txt" >&2
  exit 1
fi
if grep -Eq 'Process crashed|INSTRUMENTATION_STATUS_CODE: -2|shortMsg=Process crashed' "$ARTIFACT_DIR/gatekeeper-golden.txt"; then
  echo "Gatekeeper golden device test crashed; see $ARTIFACT_DIR/gatekeeper-golden.txt" >&2
  exit 1
fi

echo "Gatekeeper golden test passed on device."
echo "Artifacts: $ARTIFACT_DIR"
