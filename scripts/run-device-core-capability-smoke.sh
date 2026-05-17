#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_NAME="${PACKAGE_NAME:-com.google.research.air.cosmo}"
TEST_RUNNER="$PACKAGE_NAME.test/com.google.research.air.cosmo.GenesisTestRunner"
AUDIO_WAV="${1:-${AUDIO_WAV:-}}"
AUDIO_TARGET="${AUDIO_TARGET:-soda}"
AUDIO_WAIT_SEC="${AUDIO_WAIT_SEC:-14}"
APP_SETTLE_SEC="${APP_SETTLE_SEC:-6}"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT_DIR/artifacts/device-core-capability/$STAMP}"

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi

case "$AUDIO_TARGET" in
  ambient|soda|both) ;;
  *)
    echo "AUDIO_TARGET must be one of ambient, soda, both" >&2
    exit 64
    ;;
esac

mkdir -p "$ARTIFACT_DIR"

COSMO_ALLOW_MISSING_BINARIES="${COSMO_ALLOW_MISSING_BINARIES:-1}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug :app:assembleDebugAndroidTest

"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
"${ADB[@]}" install -r -t -g "$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" >/dev/null

"${ADB[@]}" shell pm grant "$PACKAGE_NAME" android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
"${ADB[@]}" shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

"${ADB[@]}" shell getprop ro.product.manufacturer > "$ARTIFACT_DIR/device-manufacturer.txt" || true
"${ADB[@]}" shell getprop ro.product.model > "$ARTIFACT_DIR/device-model.txt" || true
"${ADB[@]}" shell getprop ro.build.fingerprint > "$ARTIFACT_DIR/build-fingerprint.txt" || true

"${ADB[@]}" logcat -c || true
"${ADB[@]}" shell am instrument -w -r \
  -e cosmo.coreCapabilitySmoke true \
  -e class com.google.research.air.cosmo.CoreCapabilityDeviceSmokeTest \
  "$TEST_RUNNER" | tee "$ARTIFACT_DIR/core-capability.txt"

"${ADB[@]}" logcat -d -v time \
  | grep -E 'CosmoCoreDeviceSmoke|SodaSession|JniSodaCapability|FulfillmentOrchestrator|BertGatekeeperJni|AndroidRuntime|FATAL EXCEPTION|ANR in|Application Not Responding' \
  > "$ARTIFACT_DIR/core-logcat-filtered.txt" || true

if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS: test=initializationError' "$ARTIFACT_DIR/core-capability.txt"; then
  echo "Core capability instrumentation failed; see $ARTIFACT_DIR/core-capability.txt" >&2
  exit 1
fi
if grep -Eq 'Process crashed|INSTRUMENTATION_STATUS_CODE: -2|shortMsg=Process crashed' "$ARTIFACT_DIR/core-capability.txt"; then
  echo "Core capability instrumentation crashed; see $ARTIFACT_DIR/core-capability.txt" >&2
  exit 1
fi

if [[ -n "$AUDIO_WAV" ]]; then
  if [[ ! -f "$AUDIO_WAV" ]]; then
    echo "Audio WAV not found: $AUDIO_WAV" >&2
    exit 65
  fi

  DEVICE_TMP="/data/local/tmp/cosmo-recorded-$STAMP.wav"
  DEVICE_DIR="/data/data/$PACKAGE_NAME/files/cosmo-recorded"
  DEVICE_FILE="$DEVICE_DIR/recorded.wav"

  "${ADB[@]}" shell am start -n "$PACKAGE_NAME/.ui.mainscreen.MainActivity" >/dev/null
  sleep "$APP_SETTLE_SEC"
  "${ADB[@]}" shell am broadcast \
    -a com.google.research.air.cosmo.DEBUG_SET_SETTING \
    --es key rawAudioGatekeeperEnabled \
    --ez value false \
    -p "$PACKAGE_NAME" >/dev/null
  sleep 1
  "${ADB[@]}" shell mkdir -p /data/local/tmp
  "${ADB[@]}" shell run-as "$PACKAGE_NAME" mkdir -p files/cosmo-recorded
  "${ADB[@]}" push "$AUDIO_WAV" "$DEVICE_TMP" >/dev/null
  "${ADB[@]}" shell "cat '$DEVICE_TMP' | run-as '$PACKAGE_NAME' sh -c 'cat > files/cosmo-recorded/recorded.wav'"
  "${ADB[@]}" shell rm -f "$DEVICE_TMP" >/dev/null 2>&1 || true

  "${ADB[@]}" logcat -c || true
  "${ADB[@]}" shell am broadcast \
    -a com.google.research.air.cosmo.DEBUG_INJECT_AUDIO \
    --es path "$DEVICE_FILE" \
    --es target "$AUDIO_TARGET" \
    -p "$PACKAGE_NAME" >/dev/null
  sleep "$AUDIO_WAIT_SEC"

  "${ADB[@]}" logcat -d -v time > "$ARTIFACT_DIR/audio-logcat.txt"
  grep -E 'GatekeeperDebug|SodaSession|JniSodaCapability|ConformerEncoder|RawAudioGatekeeper|Telemetry|AndroidRuntime|FATAL EXCEPTION|ANR in|Application Not Responding' \
    "$ARTIFACT_DIR/audio-logcat.txt" > "$ARTIFACT_DIR/audio-logcat-filtered.txt" || true
fi

echo "Artifacts: $ARTIFACT_DIR"
