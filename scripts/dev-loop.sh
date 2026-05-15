#!/usr/bin/env bash
# scripts/dev-loop.sh — dumb-simple local edit→build→install→log→launch loop.
#
# Usage:
#   scripts/dev-loop.sh                   # build, install, launch, stream logs
#   scripts/dev-loop.sh --reinstall       # adb install -r (preserves data)
#   scripts/dev-loop.sh --clean-install   # uninstall first, then fresh install
#   scripts/dev-loop.sh --no-launch       # build + install, but don't auto-launch
#   scripts/dev-loop.sh --no-logs         # skip logcat stream
#   scripts/dev-loop.sh --mirror          # also start scrcpy (must be on PATH)
#   scripts/dev-loop.sh --apk-only        # just build the APK, print path, exit
#
# Reads ANDROID_SERIAL if set (for adb -s targeting). Otherwise picks first
# connected device. Fails fast with actionable messages if adb / device / build
# aren't ready.
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.google.research.air.cosmo"
ACTIVITY="${PACKAGE}/.ui.mainscreen.MainActivity"

# Genesis-specific logcat tags. Add new tags here as subsystems get their own.
LOG_TAGS=(
  "COSMO-Gatekeeper:V"
  "Gatekeeper:V"
  "JniSodaCapability:V"
  "SodaSession:V"
  "SodaController:V"
  "ConformerEncoder:V"
  "GatekeeperDebug:V"
  "IntentExtractor:V"
  "LlmIntentClassifier:V"
  "AudioGatekeepingBackendSelector:V"
  "NanoAppClient:V"
  "ContextHubClientUtil:V"
  "CosmoController:V"
  "CosmoNotificationManager:V"
  "CosmoApp:V"
  "AndroidRuntime:E"   # crashes
  "*:S"                # silence everything else
)

REINSTALL=false
CLEAN_INSTALL=false
LAUNCH=true
LOGS=true
MIRROR=false
APK_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reinstall)     REINSTALL=true; shift ;;
    --clean-install) CLEAN_INSTALL=true; shift ;;
    --no-launch)     LAUNCH=false; shift ;;
    --no-logs)       LOGS=false; shift ;;
    --mirror)        MIRROR=true; shift ;;
    --apk-only)      APK_ONLY=true; LAUNCH=false; LOGS=false; shift ;;
    -h|--help)
      sed -n '2,16p' "$0" | sed 's/^# //'
      exit 0
      ;;
    *)
      echo "Unknown flag: $1" >&2
      echo "Run with --help for usage." >&2
      exit 64
      ;;
  esac
done

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "✗ Required tool '$1' not on PATH." >&2
    case "$1" in
      adb)    echo "  Install: 'brew install android-platform-tools' (macOS)" >&2 ;;
      scrcpy) echo "  Install: 'brew install scrcpy' (macOS)" >&2 ;;
    esac
    exit 127
  }
}

require adb
$MIRROR && require scrcpy

ensure_device() {
  local devices
  devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  if [[ -z "$devices" ]]; then
    echo "✗ No device connected." >&2
    echo "  Plug in a phone (USB debugging enabled) OR start an emulator." >&2
    echo "  Quick AVD list:  emulator -list-avds" >&2
    echo "  Boot an AVD:     emulator -avd <name> &" >&2
    exit 2
  fi
  local count
  count="$(echo "$devices" | wc -l | tr -d ' ')"
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    echo "✓ Targeting ANDROID_SERIAL=${ANDROID_SERIAL}"
  elif [[ "$count" -gt 1 ]]; then
    echo "⚠ Multiple devices connected; using the first. Set ANDROID_SERIAL to pick:"
    echo "$devices" | sed 's/^/    /'
    export ANDROID_SERIAL="$(echo "$devices" | head -1)"
  else
    export ANDROID_SERIAL="$(echo "$devices" | head -1)"
    echo "✓ Targeting $ANDROID_SERIAL"
  fi
}

ensure_device

echo "==> Building :app:assembleDebug …"
START_BUILD=$(date +%s)
./gradlew --quiet :app:assembleDebug
END_BUILD=$(date +%s)
echo "    built in $((END_BUILD - START_BUILD))s"

if [[ ! -f "$APK_PATH" ]]; then
  echo "✗ Expected APK not found at $APK_PATH" >&2
  exit 1
fi

APK_SIZE_MB=$(( $(stat -f%z "$APK_PATH" 2>/dev/null || stat -c%s "$APK_PATH") / 1024 / 1024 ))
echo "    APK: $APK_PATH ($APK_SIZE_MB MB)"

if $APK_ONLY; then
  echo "✓ APK-only mode; exiting."
  exit 0
fi

if $CLEAN_INSTALL; then
  echo "==> Uninstalling previous package …"
  adb uninstall "$PACKAGE" 2>/dev/null | sed 's/^/    /'
fi

echo "==> Installing …"
START_INSTALL=$(date +%s)
adb install ${REINSTALL:+-r} -t -d "$APK_PATH" | sed 's/^/    /'
END_INSTALL=$(date +%s)
echo "    installed in $((END_INSTALL - START_INSTALL))s"

if $LAUNCH; then
  echo "==> Launching $ACTIVITY …"
  adb shell am start -n "$ACTIVITY" -W | sed 's/^/    /'
fi

if $MIRROR; then
  echo "==> Starting scrcpy (background) …"
  scrcpy --window-title="FM ($ANDROID_SERIAL)" >/dev/null 2>&1 &
  SCRCPY_PID=$!
  echo "    scrcpy pid=$SCRCPY_PID"
  trap "kill $SCRCPY_PID 2>/dev/null || true" EXIT
fi

if $LOGS; then
  echo "==> Streaming logcat (Ctrl-C to stop) …"
  echo "    filter: ${LOG_TAGS[*]}"
  echo
  adb logcat -c   # clear backlog so we only see this session's logs
  exec adb logcat "${LOG_TAGS[@]}"
fi

echo "✓ Done."
