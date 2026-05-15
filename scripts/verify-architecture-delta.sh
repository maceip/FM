#!/usr/bin/env bash
# verify-architecture-delta.sh — confirms the architecture-delta doc + the
# files it references exist on disk, and that the required Phase headers
# are present.
#
# Originally also enforced specific CI-workflow steps; those checks were
# dropped in 2026-05-15 when CI was overhauled to a lean compile+lint+test
# gate with emu validation gated on workflow_dispatch (the CI runner does
# not have the recovered binaries needed for the strict gates).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

delta="docs/architecture-delta.md"
hook="docs/architecture-update-hook.md"

if [[ ! -f "${delta}" ]]; then
  echo "missing ${delta}" >&2
  exit 65
fi

if [[ ! -f "${hook}" ]]; then
  echo "missing ${hook}" >&2
  exit 65
fi

required_sections=(
  "## Phase 0"
  "## Phase 1"
  "## Phase 2"
  "## Phase 3"
  "## Phase 4"
  "## Phase 5"
  "## Phase 6"
)

required_paths=(
  ".github/workflows/ci.yml"
  "app/build.gradle.kts"
  "app/src/main/assets/model-manifest.json"
  "baselineprofile/build.gradle.kts"
  "config/assets/model-manifest.schema.json"
  "config/signing/README.md"
  "docs/aicore-api-gap.md"
  "docs/blocker-register.md"
  "docs/reference-repository.md"
  "docs/release-signing-policy.md"
  "docs/remote-inference-contract.md"
  "gradle.properties"
  "scripts/download-models.sh"
  "scripts/generate-baseline-profile.sh"
  "scripts/verify-phase6-release-gates.sh"
  "scripts/verify-relocation-safety.sh"
  "scripts/verify-llm-phase1.sh"
)

for section in "${required_sections[@]}"; do
  if ! grep -Fq "${section}" "${delta}"; then
    echo "missing architecture delta section: ${section}" >&2
    exit 66
  fi
done

for path in "${required_paths[@]}"; do
  if [[ ! -e "${path}" ]]; then
    echo "architecture delta references missing path: ${path}" >&2
    exit 66
  fi
  if ! grep -Fq "${path}" "${delta}"; then
    echo "architecture delta does not mention ${path}" >&2
    exit 66
  fi
done
