#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

fail() {
  echo "$1" >&2
  exit 66
}

scripts/download-models.sh --verify-only
scripts/verify-architecture-delta.sh
scripts/verify-relocation-safety.sh
scripts/verify-dogfood-contract-gates.sh

if grep -Fq "download-models.sh --verify-only --allow-empty" ".github/workflows/ci.yml"; then
  fail "CI still allows an empty model manifest"
fi

if ! grep -Fq "scripts/verify-dogfood-contract-gates.sh" ".github/workflows/ci.yml"; then
  fail "CI does not run the dogfood contract gate"
fi

if ! grep -Fq "scripts/download-models.sh --verify-only" ".github/workflows/ci.yml"; then
  fail "CI does not run the strict model manifest gate"
fi

if ! grep -Fq "scripts/verify-relocation-safety.sh" ".github/workflows/ci.yml"; then
  fail "CI does not run the relocation safety gate"
fi

if ! grep -Fq "## P0 Blockers" "docs/blocker-register.md" ||
  ! grep -Fq "## Recently Removed From Blocker Status" "docs/blocker-register.md"; then
  fail "docs/blocker-register.md must be the impact-sorted blocker source of truth"
fi

if ! grep -Fq 'create("releaseInternal")' "app/build.gradle.kts"; then
  fail "releaseInternal build type is missing (minified CI artifact)"
fi

if ! grep -Fq 'create("releaseProduction")' "app/build.gradle.kts"; then
  fail "releaseProduction conditional signing config is missing"
fi

if grep -Fq "reachabilityRelease" "app/build.gradle.kts"; then
  fail "legacy reachabilityRelease signing config must be removed"
fi

if ! grep -Fq "verifyCosmoReleaseModelManifest" "app/build.gradle.kts"; then
  fail "verifyCosmoReleaseModelManifest release gate task is missing"
fi

if ! grep -Fq "assembleReleaseInternal" ".github/workflows/ci.yml"; then
  fail "CI must assemble releaseInternal (not production release without secrets)"
fi

if ! grep -Fq "detekt lintDebug" ".github/workflows/ci.yml"; then
  fail "CI must run detekt and lintDebug"
fi

if ! grep -Fq "telemetryProvenance" \
  "app/src/main/kotlin/com/google/research/air/cosmo/logging/Logging.kt"; then
  fail "Telemetry provenance metadata is missing from the observability schema"
fi

if rg -n "FirebaseAnalytics|\\.logEvent\\(" app/src/main/kotlin --glob '*.kt' \
  | grep -v "app/src/main/kotlin/com/google/research/air/cosmo/logging/Logging.kt"; then
  fail "Firebase Analytics must only be used behind the logging boundary"
fi

if rg -Fq "models/gemma-4-E4B-it/model.litertlm" \
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm"; then
  fail "LiteRT-LM delivery still hardcodes an E4B runtime model path"
fi

if grep -Fq "bindAudioInterpreterFactory(impl: UnavailableAudioInterpreterFactory)" \
  "app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeepingModule.kt"; then
  fail "Raw-audio gatekeeper production binding must use the LiteRT interpreter"
fi

if ! grep -Fq "bertCpuModelReturnsFiniteNineteenFloatVector" \
  "app/src/androidTest/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeepingRuntimeSmokeTest.kt"; then
  fail "Missing androidTest CPU BERT runtime smoke"
fi

if ! grep -Fq "rememberSaveable" \
  "app/src/main/kotlin/com/google/research/air/cosmo/ui/mainscreen/MainActivity.kt"; then
  fail "Nav3 route state must be saveable"
fi

if ! grep -Fq "savedNavigationBundleSurvivesJsonRoundTrip" \
  "app/src/test/kotlin/com/google/research/air/cosmo/ui/mainscreen/MainNavigationStateTest.kt"; then
  fail "Missing Nav3 saveable route-state coverage"
fi

echo "Phase 6 release gates passed."
