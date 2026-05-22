#!/usr/bin/env bash
# Push timer WAV fixture and run the audio gatekeeper golden instrumentation test.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURES_DIR="$ROOT_DIR/scripts/test-fixtures"
AUDIO_DIR="$FIXTURES_DIR/audio"
PACKAGE_NAME="${PACKAGE_NAME:-com.google.research.air.cosmo}"
TEST_RUNNER="$PACKAGE_NAME.test/com.google.research.air.cosmo.GenesisTestRunner"
TEST_CLASS="com.google.research.air.cosmo.fulfillment.GatekeeperAudioGoldenDeviceTest"
FIXTURE_NAME="${FIXTURE_NAME:-timer_ten_minutes.wav}"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT_DIR/artifacts/device-gatekeeper-audio-golden/$STAMP}"

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

if [[ ! -f "$AUDIO_DIR/$FIXTURE_NAME" ]]; then
  echo "==> generating audio fixtures (macOS say + afconvert)"
  "$FIXTURES_DIR/generate-audio-fixtures.sh"
fi

COSMO_ALLOW_MISSING_BINARIES="${COSMO_ALLOW_MISSING_BINARIES:-1}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug :app:assembleDebugAndroidTest

"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null

"${ADB[@]}" shell pm grant "$PACKAGE_NAME" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true

DEVICE_DIR="/data/data/$PACKAGE_NAME/files/cosmo-test"
TMP="/data/local/tmp/cosmo-golden-$FIXTURE_NAME"
"${ADB[@]}" shell mkdir -p /data/local/tmp
"${ADB[@]}" shell run-as "$PACKAGE_NAME" mkdir -p files/cosmo-test
"${ADB[@]}" push "$AUDIO_DIR/$FIXTURE_NAME" "$TMP" >/dev/null
"${ADB[@]}" shell "cat '$TMP' | run-as '$PACKAGE_NAME' sh -c 'cat > files/cosmo-test/$FIXTURE_NAME'"
"${ADB[@]}" shell rm -f "$TMP" >/dev/null 2>&1 || true

FIXTURE_PATH="$DEVICE_DIR/$FIXTURE_NAME"

"${ADB[@]}" logcat -c || true
"${ADB[@]}" shell am instrument -w -r \
  -e cosmo.gatekeeperAudioGolden true \
  -e cosmo.fixturePath "$FIXTURE_PATH" \
  -e class "$TEST_CLASS" \
  "$TEST_RUNNER" | tee "$ARTIFACT_DIR/audio-golden.txt"

"${ADB[@]}" logcat -d -v time \
  | grep -E 'GatekeeperAudioGolden|GatekeeperDebug|SodaSession|FulfillmentOrchestrator|AndroidRuntime|FATAL' \
  > "$ARTIFACT_DIR/logcat-filtered.txt" || true

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS: test=initializationError' "$ARTIFACT_DIR/audio-golden.txt"; then
  echo "Audio gatekeeper golden failed; see $ARTIFACT_DIR/audio-golden.txt" >&2
  exit 1
fi

echo "Audio gatekeeper golden passed."
echo "Artifacts: $ARTIFACT_DIR"
