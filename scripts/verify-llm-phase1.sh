#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

required_paths=(
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/AICoreClientWrapper.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/AICoreExperiment.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/LLM.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/LocalLlmModelDelivery.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RemoteCredentialStore.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RemoteInferenceClient.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RemoteInferenceConfigProvider.kt"
  "app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RequestSigner.kt"
  "app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/AICoreExperimentToggleTest.kt"
  "app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/AICoreLLMTest.kt"
  "app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/LocalLlmModelDeliveryTest.kt"
  "app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/NetworkAwareLLMTest.kt"
  "app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/RemoteInferenceClientTest.kt"
  "app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/RemoteInferenceConfigProviderTest.kt"
  "docs/aicore-api-gap.md"
  "docs/remote-inference-contract.md"
)

for path in "${required_paths[@]}"; do
  if [[ ! -f "${path}" ]]; then
    echo "missing Phase 1 LLM path: ${path}" >&2
    exit 65
  fi
done

if rg -n "Class\\.forName|ReflectiveOperationException|java\\.lang\\.reflect" app/src/main/kotlin/com/google/research/air/cosmo/lib/llm; then
  echo "production LLM code must not use reflection" >&2
  exit 66
fi

if rg -n --glob '!scripts/verify-llm-phase1.sh' "REMOTE_INFERENCE_PRIVATE_TOKEN|cosmo\\.remoteInference\\.privateToken|ConfiguredPrivateToken|privateToken\\s*=" app/src/main app/build.gradle.kts gradle docs scripts; then
  echo "remote inference must acquire credentials at runtime, not from Gradle/private-token config" >&2
  exit 66
fi

if rg -n --glob '!scripts/verify-llm-phase1.sh' "Aratea" app/src/main app/src/test docs scripts; then
  echo "Genesis remote inference path must use the owned remote contract naming" >&2
  exit 66
fi

if ! rg -q "RefreshingAnonymousCredentialProvider" app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/RemoteInferenceClientTest.kt; then
  echo "Remote credential refresh path is not covered by tests" >&2
  exit 66
fi

if ! rg -q "LLMRouteReason.REMOTE_UNAVAILABLE" app/src/test/kotlin/com/google/research/air/cosmo/lib/llm/NetworkAwareLLMTest.kt; then
  echo "Network-aware remote downgrade path is not covered by tests" >&2
  exit 66
fi
