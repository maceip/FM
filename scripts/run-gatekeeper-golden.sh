#!/usr/bin/env bash
# Gatekeeper → fulfillment golden path: fast JVM test, then on-device if adb is up.
#
# Usage:
#   scripts/run-gatekeeper-golden.sh              # unit + device (when connected)
#   scripts/run-gatekeeper-golden.sh --unit-only  # JVM only (~10s)
#   ANDROID_SERIAL=ip:port scripts/run-gatekeeper-golden.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "${1:-}" == "--all-proofs" ]]; then
  shift
  exec "$ROOT_DIR/scripts/run-launch-device-proofs.sh" "$@"
fi

UNIT_TEST="com.google.research.air.cosmo.fulfillment.GatekeeperFulfillmentGoldenTest"
RUN_DEVICE=1

for arg in "$@"; do
  case "$arg" in
    --unit-only) RUN_DEVICE=0 ;;
    -h | --help)
      sed -n '2,10p' "$0"
      echo "  --all-proofs   Run full launch proof suite (delegates to run-launch-device-proofs.sh)"
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg (try --unit-only)" >&2
      exit 64
      ;;
  esac
done

echo "==> JVM golden: $UNIT_TEST"
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:testDebugUnitTest --tests "$UNIT_TEST"

if [[ "$RUN_DEVICE" -eq 0 ]]; then
  echo "Skipping device golden (--unit-only)."
  exit 0
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
else
  ADB=(adb)
fi

device_count="$("${ADB[@]}" devices 2>/dev/null | awk 'NR>1 && $2=="device" {c++} END {print c+0}')"
if [[ "$device_count" -eq 0 ]]; then
  echo "No adb device; skipping device golden. Connect USB/WiFi or set ANDROID_SERIAL." >&2
  echo "  adb connect <ip>:<port>" >&2
  exit 0
fi

echo "==> Device golden (attached hardware)"
"$ROOT_DIR/scripts/run-device-gatekeeper-golden-test.sh"
