#!/usr/bin/env bash
# Push audio fixtures to the connected device and broadcast injection intents
# at the cosmo debug receiver. Tails logcat for the relevant tags throughout.
#
# Usage:
#   ./run-audio-fixtures.sh            # run every fixture against target=both
#   ./run-audio-fixtures.sh ambient    # only ambient (selected raw-audio backend)
#   ./run-audio-fixtures.sh soda       # only SODA (libsoda_dev_jni)
#   ./run-audio-fixtures.sh conformer  # directly probe recovered EdgeTPU Conformer
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUDIO_DIR="$SCRIPT_DIR/audio"
PACKAGE="com.google.research.air.cosmo"
TARGET="${1:-both}"
ACTIVITY="$PACKAGE/.ui.mainscreen.MainActivity"
# Push to /data/local/tmp first (always shell-readable), then `run-as` the app to copy into
# its own private files dir (always app-readable, no scoped-storage games). The broadcast
# carries the app-private path so the receiver opens it under the app's own uid.
TMP_DIR="/data/local/tmp/cosmo-test"
DEVICE_DIR="/data/data/$PACKAGE/files/cosmo-test"
PER_FIXTURE_WAIT_SEC="${PER_FIXTURE_WAIT_SEC:-25}"
APP_RELAUNCH_WAIT_SEC="${APP_RELAUNCH_WAIT_SEC:-2}"

case "$TARGET" in ambient|soda|both|conformer) ;; *) echo "target must be one of ambient|soda|both|conformer" >&2; exit 64 ;; esac

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not on PATH" >&2; exit 64
fi
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi
if [[ ! -d "$AUDIO_DIR" ]] || ! ls "$AUDIO_DIR"/*.wav >/dev/null 2>&1; then
  echo "no fixtures in $AUDIO_DIR — run ./generate-audio-fixtures.sh first" >&2; exit 65
fi

device=$("${ADB[@]}" get-serialno || true)
if [[ -z "$device" || "$device" == "unknown" ]]; then
  echo "no device connected" >&2; exit 66
fi
echo "==> device: $device"

echo "==> ensuring directories exist on device"
"${ADB[@]}" shell mkdir -p "$TMP_DIR"
"${ADB[@]}" shell run-as "$PACKAGE" mkdir -p "files/cosmo-test"

echo "==> pushing fixtures via $TMP_DIR -> $DEVICE_DIR (run-as $PACKAGE)"
for wav in "$AUDIO_DIR"/*.wav; do
  name=$(basename "$wav")
  "${ADB[@]}" push "$wav" "$TMP_DIR/$name" >/dev/null
  # `run-as ... tee` writes to the app's private dir under the app's uid, so the receiver
  # can read the file without any storage permission. Discard tee stdout for quietness.
  "${ADB[@]}" shell "cat $TMP_DIR/$name | run-as $PACKAGE sh -c 'cat > files/cosmo-test/$name'"
done
"${ADB[@]}" shell rm -rf "$TMP_DIR" 2>/dev/null || true

echo "==> starting app so the debug receiver is registered"
"${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null
sleep 4
if [[ "$TARGET" == "ambient" || "$TARGET" == "both" ]]; then
  echo "==> enabling raw-audio backend selection for ambient injection"
  "${ADB[@]}" shell am broadcast \
    -a com.google.research.air.cosmo.DEBUG_SET_SETTING \
    --es key rawAudioGatekeeperEnabled \
    --ez value true \
    -p "$PACKAGE" >/dev/null
  sleep 1
fi

logcat_pid=""
trap '[[ -n "$logcat_pid" ]] && kill "$logcat_pid" 2>/dev/null || true' EXIT

artifacts="$SCRIPT_DIR/../../artifacts/audio-run/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$artifacts"
logfile="$artifacts/logcat.txt"
echo "==> tailing logcat to $logfile"
"${ADB[@]}" logcat -c
"${ADB[@]}" logcat -v time \
  GatekeeperDebug:* \
  COSMO-Gatekeeper:* \
  COSMO-RawAudioGatekeeper:* \
  COSMO-ConformerEncoder:* \
  JniSodaCapability:* \
  SodaSession:* \
  SodaSession.Callback:* \
  Telemetry:* \
  nativeloader:* \
  AndroidRuntime:E \
  *:S \
  >"$logfile" &
logcat_pid=$!

for wav in "$AUDIO_DIR"/*.wav; do
  name=$(basename "$wav")
  echo "==> ensuring app receiver is registered for $name"
  "${ADB[@]}" shell am start -n "$ACTIVITY" >/dev/null
  sleep "$APP_RELAUNCH_WAIT_SEC"
  echo "==> inject $name target=$TARGET"
  "${ADB[@]}" shell am broadcast \
    -a com.google.research.air.cosmo.DEBUG_INJECT_AUDIO \
    --es path "$DEVICE_DIR/$name" \
    --es target "$TARGET" \
    -p "$PACKAGE" >/dev/null
  sleep "$PER_FIXTURE_WAIT_SEC"
done

echo "==> waiting 4s for trailing transcripts"
sleep 4
kill "$logcat_pid" 2>/dev/null || true
logcat_pid=""

echo
echo "==> summary"
grep -E "GatekeeperDebug|SodaSession|JniSodaCapability|RawAudioGatekeeperRunResult|ConformerEncoder|UnsatisfiedLink|FATAL" "$logfile" | head -120 || true

echo
echo "logcat: $logfile"

if [[ "${ASSERT_AUDIO_FIXTURES:-1}" != "0" && "$TARGET" != "conformer" ]]; then
  echo "==> validating fixture output"
  enforce_quality_args=""
  if [[ "${ENFORCE_INHERENT_QUALITY:-0}" == "1" ]]; then
    enforce_quality_args="--enforce-ambient-quality"
  fi
  # shellcheck disable=SC2086
  python3 "$SCRIPT_DIR/assert-audio-fixture-run.py" "$logfile" \
    --index "$SCRIPT_DIR/gatekeeper-utterances.json" \
    --target "$TARGET" \
    --ambient-quality-report "$artifacts/inherent-quality.json" \
    $enforce_quality_args
  echo "inherent quality report: $artifacts/inherent-quality.json"
elif [[ "$TARGET" == "conformer" ]]; then
  echo "==> skipping fixture validator for direct Conformer probe; inspect raw_audio result in logcat"
fi
