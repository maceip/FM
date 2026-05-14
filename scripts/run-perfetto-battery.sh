#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.google.research.air.cosmo}"
DURATION_SECONDS="${DURATION_SECONDS:-3600}"
OUT_DIR="${OUT_DIR:-artifacts/perfetto-battery/$(date -u +%Y%m%dT%H%M%SZ)}"
DEVICE_TRACE="/data/misc/perfetto-traces/cosmo-phase5-${DURATION_SECONDS}s.pftrace"

mkdir -p "$OUT_DIR"

adb get-state >/dev/null

echo "Collecting device metadata"
adb shell getprop ro.product.model > "$OUT_DIR/device-model.txt"
adb shell getprop ro.build.fingerprint > "$OUT_DIR/build-fingerprint.txt"
adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/package.txt"

echo "Resetting batterystats"
adb shell dumpsys batterystats --reset

echo "Starting Perfetto for ${DURATION_SECONDS}s"
adb shell perfetto \
  -o "$DEVICE_TRACE" \
  -t "${DURATION_SECONDS}s" \
  sched freq idle am wm gfx view binder_driver hal dalvik camera input power

echo "Pulling Perfetto trace"
adb pull "$DEVICE_TRACE" "$OUT_DIR/cosmo-phase5.pftrace" >/dev/null
adb shell rm -f "$DEVICE_TRACE"

echo "Collecting batterystats and bugreport"
adb shell dumpsys batterystats --charged "$PACKAGE" > "$OUT_DIR/batterystats-charged.txt"
adb shell dumpsys battery > "$OUT_DIR/battery.txt"
adb bugreport "$OUT_DIR/bugreport.zip" >/dev/null

cat > "$OUT_DIR/README.md" <<EOF
# Cosmo Phase 5 Perfetto / Battery Capture

- Package: \`$PACKAGE\`
- Duration: \`${DURATION_SECONDS}s\`
- Trace: \`cosmo-phase5.pftrace\`
- Battery Historian input: \`bugreport.zip\`
- Package batterystats: \`batterystats-charged.txt\`

For the 24-hour drain gate, run this script with \`DURATION_SECONDS=86400\` on a retail Pixel that has completed the clean-install permission flow, then import \`bugreport.zip\` into Battery Historian.
EOF

echo "Artifacts written to $OUT_DIR"
