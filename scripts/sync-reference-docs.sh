#!/usr/bin/env bash
# scripts/sync-reference-docs.sh — refresh ~/neural/reference/ from canonical
# cosmo-dev + inherent doc locations.
#
# Usage:
#   scripts/sync-reference-docs.sh           # overwrite local copies
#   scripts/sync-reference-docs.sh --diff    # show what would change (dry run)
#
# Reads SOURCES below — pairs of (source path, destination basename under
# reference/). Modify SOURCES when adding new mirrored docs.
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"
REF_DIR="${REPO_ROOT}/reference"

DIFF_MODE=false
[[ "${1:-}" == "--diff" ]] && DIFF_MODE=true

mkdir -p "$REF_DIR/contracts"

# (source_path|destination_basename) pairs.
declare -a SOURCES=(
  "/Users/mac/cosmo-dev/repo/docs/architecture.md|cosmo-architecture.md"
  "/Users/mac/cosmo-dev/repo/docs/audio_stack_map.md|cosmo-audio-stack-map.md"
  "/Users/mac/cosmo-dev/repo/docs/text_stack_map.md|cosmo-text-stack-map.md"
  "/Users/mac/cosmo-dev/repo/docs/image_stack_map.md|cosmo-image-stack-map.md"
  "/Users/mac/cosmo-dev/repo/docs/llm_stack_map.md|cosmo-llm-stack-map.md"
  "/Users/mac/cosmo-dev/repo/docs/ui_surface_map.md|cosmo-ui-surface-map.md"
  "/Users/mac/cosmo-dev/repo/docs/dagger_graph_map.md|cosmo-dagger-graph-map.md"
  "/Users/mac/cosmo-dev/repo/docs/settings_and_config_map.md|cosmo-settings-and-config-map.md"
  "/Users/mac/cosmo-dev/repo/docs/telemetry_map.md|cosmo-telemetry-map.md"
  "/Users/mac/cosmo-dev/repo/docs/reachability-matrix.md|cosmo-reachability-matrix.md"
  "/Users/mac/cosmo-dev/repo/docs/runtime_topology_map.md|cosmo-runtime-topology-map.md"
  "/Users/mac/cosmo-dev/repo/docs/bert_gatekeeper_litert_jni_replacement.md|cosmo-bert-mt6989-aot-pipeline.md"
  "/Users/mac/cosmo-dev/repo/docs/contracts/00_README.md|contracts/00_README.md"
  "/Users/mac/cosmo-dev/repo/docs/contracts/01_context_store_api.md|contracts/01_context_store_api.md"
  "/Users/mac/cosmo-dev/repo/docs/contracts/02_fulfillment_api.md|contracts/02_fulfillment_api.md"
  "/Users/mac/cosmo-dev/repo/docs/contracts/03_skill_registry_api.md|contracts/03_skill_registry_api.md"
  "/Users/mac/inherent/contracts/runtime_contract.md|inherent-runtime-contract.md"
  "/Users/mac/inherent/AUDIO_COLLECTION.md|inherent-audio-collection.md"
)

CHANGED=0
MISSING=0

for pair in "${SOURCES[@]}"; do
  IFS='|' read -r src basename <<<"$pair"
  dst="$REF_DIR/$basename"

  if [[ ! -f "$src" ]]; then
    echo "  ⚠ source missing: $src"
    MISSING=$((MISSING + 1))
    continue
  fi

  if [[ ! -f "$dst" ]] || ! cmp -s "$src" "$dst"; then
    if $DIFF_MODE; then
      echo "  ↻ would update: $basename"
      diff -u "$dst" "$src" 2>/dev/null | head -20 || echo "      (file does not exist locally)"
    else
      cp "$src" "$dst"
      echo "  ✓ updated: $basename"
    fi
    CHANGED=$((CHANGED + 1))
  fi
done

echo
if $DIFF_MODE; then
  echo "Diff mode: $CHANGED file(s) would change, $MISSING source(s) missing."
else
  echo "Sync complete: $CHANGED file(s) updated, $MISSING source(s) missing."
fi

if [[ "$MISSING" -gt 0 ]]; then
  echo
  echo "Missing sources mean either:"
  echo "  - The upstream doc has moved or been deleted"
  echo "  - You're running this on a machine without ~/cosmo-dev or ~/inherent"
  exit 1
fi
