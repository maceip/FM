package com.google.research.air.cosmo.lib.llm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CascadingLocalLLM
@Inject
constructor(
    private val aicore: AICoreLLM,
    private val mlKitPrompt: MlKitPromptLLM,
    private val liteRtLm: LiteRtLmLLM
) : LlmBackendAdapter(
    backendId = LlmBackendId.LOCAL_CASCADE,
    capabilities = LlmBackendCapabilities.TEXT_IMAGE_AUDIO_ONE_IMAGE
) {
    private val providers: List<LlmBackend>
        get() =
            listOf<LlmBackend>(
                aicore,
                mlKitPrompt,
                liteRtLm
            )

    override suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String = runProviderCascade(request, onToken)

    private suspend fun runProviderCascade(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String {
        val failures = mutableListOf<Pair<String, LlmUnavailableException>>()
        providers.forEach { provider ->
            if (!provider.capabilities.supports(request)) {
                failures += provider.backendId.label to request.unsupportedException(provider)
                return@forEach
            }
            var emitted = false
            return try {
                provider.generate(request) { token ->
                    emitted = true
                    onToken(token)
                }
            } catch (e: LlmUnavailableException) {
                if (emitted) throw e
                failures += provider.backendId.label to e
                return@forEach
            }
        }
        throw unavailable(failures)
    }

    private fun unavailable(failures: List<Pair<String, LlmUnavailableException>>): LlmUnavailableException {
        val message =
            failures.joinToString(prefix = "No local LLM provider is available: ") { (name, failure) ->
                "$name=${failure.reason}"
            }
        return LlmUnavailableException(LlmFailureCause.LOCAL_MODEL_UNAVAILABLE, message).also { aggregate ->
            failures.forEach { (_, failure) -> aggregate.addSuppressed(failure) }
        }
    }
}
