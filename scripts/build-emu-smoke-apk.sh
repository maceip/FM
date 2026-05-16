#!/usr/bin/env bash
# Builds an emulator smoke APK small enough for emu.devkeys.net uploads.
#
# The normal emuDebug APK intentionally bundles recovered SODA/model assets and is hundreds of
# megabytes. The timer-flow emulator proof does not need those assets, so this script strips them
# from the already-built APK, zipaligns it, and re-signs it with the repo debug key.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_IN="$ROOT_DIR/app/build/outputs/apk/emuDebug/app-emuDebug.apk"
OUT="${1:-$ROOT_DIR/artifacts/emu-apk/app-emuDebug-smoke.apk}"
WORK_DIR="$(dirname "$OUT")/.work"
WORK_APK="$WORK_DIR/app-emuDebug-smoke-work.apk"
ALIGNED_APK="$WORK_DIR/app-emuDebug-smoke-aligned.apk"
DEBUG_KEYSTORE="$ROOT_DIR/config/signing/genesis-debug.keystore"

find_build_tool() {
  local tool="$1"
  local root
  for root in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "$HOME/Library/Android/sdk" \
    "/opt/homebrew/share/android-commandlinetools"; do
    if [[ -n "$root" && -d "$root/build-tools" ]]; then
      local candidate
      candidate="$(find "$root/build-tools" -name "$tool" -type f | sort | tail -n 1)"
      if [[ -n "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    fi
  done
  return 1
}

command -v zip >/dev/null || {
  echo "ERROR: zip is required to strip smoke-only assets" >&2
  exit 1
}

ZIPALIGN="$(find_build_tool zipalign)" || {
  echo "ERROR: zipalign not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
}
APKSIGNER="$(find_build_tool apksigner)" || {
  echo "ERROR: apksigner not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
}

mkdir -p "$WORK_DIR" "$(dirname "$OUT")"

COSMO_ALLOW_MISSING_BINARIES="${COSMO_ALLOW_MISSING_BINARIES:-1}" \
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleEmuDebug

cp "$APK_IN" "$WORK_APK"
zip -q -d "$WORK_APK" \
  'assets/soda/*' \
  'assets/models/*' \
  'assets/model-manifest.json' \
  >/dev/null || true

"$ZIPALIGN" -p -f 4 "$WORK_APK" "$ALIGNED_APK"
"$APKSIGNER" sign \
  --ks "$DEBUG_KEYSTORE" \
  --ks-pass pass:android \
  --ks-key-alias androiddebugkey \
  --key-pass pass:android \
  --out "$OUT" \
  "$ALIGNED_APK"
"$APKSIGNER" verify --verbose "$OUT"
ls -lh "$OUT"
