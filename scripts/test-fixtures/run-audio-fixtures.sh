#!/usr/bin/env bash
# Push audio fixtures to the connected device and broadcast injection intents
# at the cosmo debug receiver. Tails logcat for the relevant tags throughout.
#
# Usage:
#   ./run-audio-fixtures.sh            # run every fixture against target=both
#   ./run-audio-fixtures.sh ambient    # only ambient (TFLite frontend + EdgeTPU backend)
#   ./run-audio-fixtures.sh soda       # only SODA (libsoda_dev_jni)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUDIO_DIR="$SCRIPT_DIR/audio"
PACKAGE="com.google.research.air.cosmo"
TARGET="${1:-both}"
# Push to /data/local/tmp first (always shell-readable), then `run-as` the app to copy into
# its own private files dir (always app-readable, no scoped-storage games). The broadcast
# carries the app-private path so the receiver opens it under the app's own uid.
TMP_DIR="/data/local/tmp/cosmo-test"
DEVICE_DIR="/data/data/$PACKAGE/files/cosmo-test"
PER_FIXTURE_WAIT_SEC=8

case "$TARGET" in ambient|soda|both) ;; *) echo "target must be one of ambient|soda|both" >&2; exit 64 ;; esac

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not on PATH" >&2; exit 64
fi
if [[ ! -d "$AUDIO_DIR" ]] || ! ls "$AUDIO_DIR"/*.wav >/dev/null 2>&1; then
  echo "no fixtures in $AUDIO_DIR — run ./generate-audio-fixtures.sh first" >&2; exit 65
fi

device=$(adb get-serialno || true)
if [[ -z "$device" || "$device" == "unknown" ]]; then
  echo "no device connected" >&2; exit 66
fi
echo "==> device: $device"

echo "==> ensuring directories exist on device"
adb shell mkdir -p "$TMP_DIR"
adb shell run-as "$PACKAGE" mkdir -p "files/cosmo-test"

echo "==> pushing fixtures via $TMP_DIR -> $DEVICE_DIR (run-as $PACKAGE)"
for wav in "$AUDIO_DIR"/*.wav; do
  name=$(basename "$wav")
  adb push "$wav" "$TMP_DIR/$name" >/dev/null
  # `run-as ... tee` writes to the app's private dir under the app's uid, so the receiver
  # can read the file without any storage permission. Discard tee stdout for quietness.
  adb shell "cat $TMP_DIR/$name | run-as $PACKAGE sh -c 'cat > files/cosmo-test/$name'"
done
adb shell rm -rf "$TMP_DIR" 2>/dev/null || true

logcat_pid=""
trap '[[ -n "$logcat_pid" ]] && kill "$logcat_pid" 2>/dev/null || true' EXIT

artifacts="$SCRIPT_DIR/../../artifacts/audio-run/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$artifacts"
logfile="$artifacts/logcat.txt"
echo "==> tailing logcat to $logfile"
adb logcat -c
adb logcat -v time \
  GatekeeperDebug:* \
  COSMO-Gatekeeper:* \
  COSMO-RawAudioGatekeeper:* \
  COSMO-ConformerEncoder:* \
  JniSodaCapability:* \
  Telemetry:* \
  nativeloader:* \
  AndroidRuntime:E \
  *:S \
  >"$logfile" &
logcat_pid=$!

for wav in "$AUDIO_DIR"/*.wav; do
  name=$(basename "$wav")
  echo "==> inject $name target=$TARGET"
  adb shell am broadcast \
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
grep -E "GatekeeperDebug|JniSodaCapability|RawAudioGatekeeperRunResult|ConformerEncoder|UnsatisfiedLink|FATAL" "$logfile" | head -80 || true

echo
echo "logcat: $logfile"
