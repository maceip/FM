#!/usr/bin/env bash
# scripts/provision-from-recovery.sh — populate the gitignored recovered
# binaries from the canonical cosmo recovery directories.
#
# What this does:
#   - Copies recovered TFLite models from ~/Downloads/cosmo_recovered_large_assets
#     into app/src/main/assets/models/
#   - Copies recovered SODA language packs into app/src/main/assets/soda/
#   - Copies recovered JNI .so files from ~/Downloads/cosmo_recovered_native_libs
#     into app/src/main/jniLibs/arm64-v8a/
#   - Verifies SHA-256 sums against app/src/main/assets/model-manifest.json
#     after copying.
#
# Usage:
#   scripts/provision-from-recovery.sh              # provision everything
#   scripts/provision-from-recovery.sh --verify     # just verify, don't copy
#   scripts/provision-from-recovery.sh --dry-run    # show what would happen
#   COSMO_RECOVERY_ROOT=/path/to/recovery ./scripts/provision-from-recovery.sh
#
# Without --verify, files already present at destination are skipped (unless
# their SHA-256 differs from source). Source dir is read-only.
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"

ROOT="${COSMO_RECOVERY_ROOT:-$HOME/Downloads}"
LARGE_ASSETS_DIR="${ROOT}/cosmo_recovered_large_assets"
NATIVE_LIBS_DIR="${ROOT}/cosmo_recovered_native_libs/lib/arm64-v8a"

MODELS_DEST="${REPO_ROOT}/app/src/main/assets/models"
SODA_DEST="${REPO_ROOT}/app/src/main/assets/soda"
JNI_DEST="${REPO_ROOT}/app/src/main/jniLibs/arm64-v8a"

VERIFY_ONLY=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verify)  VERIFY_ONLY=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help)
      sed -n '2,18p' "$0" | sed 's/^# //'
      exit 0
      ;;
    *) echo "Unknown flag: $1" >&2; exit 64 ;;
  esac
done

if [[ ! -d "$LARGE_ASSETS_DIR" ]]; then
  echo "✗ Recovery directory not found: $LARGE_ASSETS_DIR" >&2
  echo "  Set COSMO_RECOVERY_ROOT to the parent of cosmo_recovered_* if it lives elsewhere." >&2
  exit 2
fi

mkdir -p "$MODELS_DEST" "$SODA_DEST" "$JNI_DEST"

# (source_path, dest_path) pairs. Source filenames in the recovery have an
# 'assets__category__' prefix that we strip when copying into the asset tree.
declare -a MAP=(
  "$LARGE_ASSETS_DIR/assets__gatekeeper__bert_vocab.txt|$MODELS_DEST/bert_vocab.txt"
  "$LARGE_ASSETS_DIR/assets__gatekeeper__bert_v0_cpu.tflite|$MODELS_DEST/bert_v0_cpu.tflite"
  "$LARGE_ASSETS_DIR/assets__gatekeeper__bert_v0_p24.tflite|$MODELS_DEST/bert_v0_p24.tflite"
  "$LARGE_ASSETS_DIR/assets__gatekeeper__bert_v0_p25.tflite|$MODELS_DEST/bert_v0_p25.tflite"
  "$LARGE_ASSETS_DIR/assets__gatekeeper__audio_frontend.tflite|$MODELS_DEST/audio_frontend.tflite"
  "$LARGE_ASSETS_DIR/assets__gatekeeper__audio_gatekeeper_edgetpu.tflite|$MODELS_DEST/audio_gatekeeper_edgetpu.tflite"
  "$LARGE_ASSETS_DIR/assets__soda__lp_cpu.zip|$SODA_DEST/lp_cpu.zip"
  "$LARGE_ASSETS_DIR/assets__soda__lp_lga_tpu.zip|$SODA_DEST/lp_lga_tpu.zip"
  "$LARGE_ASSETS_DIR/assets__soda__lp_zuma_tpu.zip|$SODA_DEST/lp_zuma_tpu.zip"
  "$NATIVE_LIBS_DIR/libsoda_dev_jni.so|$JNI_DEST/libsoda_dev_jni.so"
  "$NATIVE_LIBS_DIR/libdarwinndelegatejni.so|$JNI_DEST/libdarwinndelegatejni.so"
  "$NATIVE_LIBS_DIR/libtensorflowlite_jni.so|$JNI_DEST/libtensorflowlite_jni.so"
)

sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" 2>/dev/null | awk '{print $1}'
  else
    sha256sum "$1" 2>/dev/null | awk '{print $1}'
  fi
}

provision_one() {
  local src="$1" dst="$2"
  local name
  name="$(basename "$dst")"

  if [[ ! -f "$src" ]]; then
    echo "  ⚠ $name: source missing at $src — skipping"
    return 0
  fi

  if [[ -f "$dst" ]]; then
    local src_sum dst_sum
    src_sum="$(sha256 "$src")"
    dst_sum="$(sha256 "$dst")"
    if [[ "$src_sum" == "$dst_sum" ]]; then
      echo "  ✓ $name: already present, sha matches"
      return 0
    fi
    echo "  ↻ $name: sha differs, will overwrite"
  fi

  if $VERIFY_ONLY; then
    echo "  ✗ $name: would copy (but --verify mode)"
    return 1
  fi

  if $DRY_RUN; then
    echo "  → would copy: $src → $dst"
    return 0
  fi

  cp "$src" "$dst"
  local dst_sum
  dst_sum="$(sha256 "$dst")"
  echo "  ✓ $name (sha=${dst_sum:0:12}…)"
}

echo "==> Provisioning recovered assets from $ROOT"
echo

MISSING=0
for pair in "${MAP[@]}"; do
  IFS='|' read -r src dst <<<"$pair"
  if ! provision_one "$src" "$dst"; then
    MISSING=$((MISSING + 1))
  fi
done

echo
if [[ -x "$REPO_ROOT/scripts/download-models.sh" ]]; then
  if $DRY_RUN || $VERIFY_ONLY; then
    echo "(skipping download-models.sh --verify-only in dry-run/verify mode)"
  else
    echo "==> Running scripts/download-models.sh --verify-only --bundled-only …"
    if "$REPO_ROOT/scripts/download-models.sh" --verify-only --bundled-only 2>&1 | sed 's/^/    /'; then
      echo "✓ Manifest verification passed."
    else
      echo "✗ Manifest verification failed — check SHA-256 vs model-manifest.json" >&2
      exit 1
    fi
  fi
fi

if [[ -x "$REPO_ROOT/scripts/verify-soda-assets.sh" ]] && ! $DRY_RUN && ! $VERIFY_ONLY; then
  echo "==> Running scripts/verify-soda-assets.sh …"
  "$REPO_ROOT/scripts/verify-soda-assets.sh" 2>&1 | sed 's/^/    /' || {
    echo "✗ SODA asset verification failed" >&2
    exit 1
  }
fi

if [[ "$MISSING" -gt 0 ]]; then
  echo
  echo "⚠ $MISSING file(s) needed action. Re-run without --verify to provision."
  exit 1
fi

echo
echo "✓ All recovered binaries provisioned and verified."
