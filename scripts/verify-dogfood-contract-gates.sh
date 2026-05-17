#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

fail() {
  echo "$1" >&2
  exit 66
}

require() {
  local pattern="$1"
  local file="$2"
  local description="$3"
  if ! grep -Fq "$pattern" "$file"; then
    fail "Missing ${description}: expected '${pattern}' in ${file}"
  fi
}

forbid() {
  local pattern="$1"
  local file="$2"
  local description="$3"
  if grep -Fq "$pattern" "$file"; then
    fail "Forbidden ${description}: found '${pattern}' in ${file}"
  fi
}

soda_builder="app/src/main/kotlin/com/google/research/air/cosmo/lib/soda/SodaConfigBuilder.kt"
soda_test="app/src/test/kotlin/com/google/research/air/cosmo/lib/soda/SodaConfigBuilderTest.kt"
pipeline="app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeepingPipeline.kt"
pipeline_test="app/src/test/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeeperDispatchInvariantTest.kt"
selector="app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/backend/AudioGatekeepingBackendSelector.kt"
nanoapp="app/src/main/kotlin/com/google/research/air/cosmo/lib/record/NanoAppClient.kt"
captors="app/src/main/kotlin/com/google/research/air/cosmo/audiocapture/AudioCaptors.kt"
fulfillment_handler="app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentActionHandler.kt"
fulfillment_module="app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentModule.kt"
fulfillment_executors="app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentActionExecutors.kt"
fulfillment_test="app/src/test/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentActionHandlerImplTest.kt"
logging="app/src/main/kotlin/com/google/research/air/cosmo/logging/Logging.kt"
inherent_installer="scripts/install-inherent-litert-artifact.py"
inherent_backend_doc="app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/backend/AudioGatekeepingBackend.kt"

require "AMBIENT_ONESHOT" "${soda_builder}" "SODA oneshot app domain"
require "setAttachHotwordEvent(true)" "${soda_builder}" "SODA hotword-event attachment"
require "setIntendedSpeechConfig" "${soda_builder}" "SODA intended speech config"
require "INTENT_DETECTION_MODE_OPEN_MIC" "${soda_builder}" "open-mic intended speech mode"
require "setAttachOpenMicResult(true)" "${soda_builder}" "open-mic result attachment"
forbid "ApplicationDomain.AMBIENT_CONTINUOUS" "${soda_builder}" "ambient continuous SODA launch path"
require "coreConfigEnablesOneshotIntendedSpeech" "${soda_test}" "SODA config regression test"

require "AudioInput.TranscribedSegment" "${pipeline}" "ambient transcript backend input"
require "directednessTrigger" "${pipeline}" "directedness gate propagation"
require "backendSelector.select().classify(input)" "${pipeline}" "selector-routed gatekeeper dispatch"
require "gatekeeper_directedness_gate" "${logging}" "directedness telemetry"
require "whenConformerEdgeTpuBackendIsActiveAmbientTextDoesNotInvokeBertModel" "${pipeline_test}" "C1 dispatch invariant test"
require "Selected CpuRecoveryBackend (Magic Mic + BERT)" "${selector}" "CPU recovery selector label"

require "isUseNanoappAudioEnabled() && recordServiceManager.startAocRecording(listener)" "${captors}" "settings-gated AOC routing"
require "ContextHubClientCallback is a class" "${nanoapp}" "AOC public-SDK callback limitation"
require "return null" "${nanoapp}" "AOC fail-closed public build"

require "interface FulfillmentActionExecutor" "${fulfillment_handler}" "fulfillment executor contract"
require "Set<@JvmSuppressWildcards FulfillmentActionExecutor>" "${fulfillment_handler}" "executor registry injection"
require "IntentFulfillmentActionExecutor" "${fulfillment_executors}" "intent executor split"
require "MemoryFulfillmentActionExecutor" "${fulfillment_executors}" "memory executor split"
require "TaskMonitorFulfillmentActionExecutor" "${fulfillment_executors}" "task monitor executor split"
require "PassiveFulfillmentActionExecutor" "${fulfillment_executors}" "passive executor split"
require "@IntoSet abstract fun bindIntentActionExecutor" "${fulfillment_module}" "executor Hilt multibinding"
require "executeDelegatesToMatchingExecutorAndMarksActionExecuted" "${fulfillment_test}" "executor dispatch unit test"

require "IntendedSpeech directedness" "README.md" "README SODA status"
require "CPU recovery directedness gate is wired" "README.md" "README Magic Mic status"
require "subtype executors split" "README.md" "README fulfillment executor status"
forbid "Not used as launch gate" "README.md" "stale Magic Mic launch status"
forbid "single action handler awaiting" "README.md" "stale fulfillment split status"
require "asap-static3000-int8" "${inherent_installer}" "validated inherent installer default"
forbid "Today: stub" "${inherent_backend_doc}" "stale Inherent backend stub label"

echo "Dogfood contract gates passed."
