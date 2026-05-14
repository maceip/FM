package com.google.research.air.cosmo.lib.llm

enum class LlmBackendId(val label: String) {
    AICORE("AICore GenerativeModel"),
    ML_KIT_PROMPT("ML Kit GenAI Prompt"),
    LITERT_LM("LiteRT-LM"),
    LOCAL_CASCADE("Local LLM cascade"),
    REMOTE_INFERENCE("Remote inference"),
    NETWORK_AWARE("Network-aware LLM router"),
    SIMULATION("Simulation LLM")
}

class LlmRequest private constructor(
    val parts: List<PromptPart>
) {
    val imageCount: Int = parts.count { it is PromptPart.Image }
    val audioCount: Int = parts.count { it is PromptPart.Audio }
    val textCount: Int = parts.count { it is PromptPart.Text }

    fun requireTextPrompt(backendId: LlmBackendId): String {
        val text = parts.filterIsInstance<PromptPart.Text>().joinToString("\n") { it.text }.trim()
        if (text.isEmpty()) {
            throw LlmUnavailableException(
                LlmFailureCause.CONFIGURATION,
                "${backendId.label} requires non-empty text input"
            )
        }
        return text
    }

    companion object {
        fun text(prompt: String): LlmRequest = of(listOf(PromptPart.Text(prompt)))

        fun of(parts: List<PromptPart>): LlmRequest {
            if (parts.isEmpty()) {
                throw LlmUnavailableException(LlmFailureCause.CONFIGURATION, "LLM request must contain at least one part")
            }
            return LlmRequest(parts.toList())
        }
    }
}

data class LlmBackendCapabilities(
    val supportsText: Boolean,
    val supportsImages: Boolean,
    val supportsAudio: Boolean,
    val maxImages: Int
) {
    fun supports(request: LlmRequest): Boolean = unsupportedReasons(request).isEmpty()

    fun unsupportedReasons(request: LlmRequest): List<String> =
        buildList {
            if (!supportsText && request.textCount > 0) add("text")
            if (!supportsImages && request.imageCount > 0) add("image")
            if (supportsImages && request.imageCount > maxImages) add("image_count>${maxImages}")
            if (!supportsAudio && request.audioCount > 0) add("audio")
        }

    companion object {
        val TEXT_ONLY =
            LlmBackendCapabilities(
                supportsText = true,
                supportsImages = false,
                supportsAudio = false,
                maxImages = 0
            )
        val TEXT_AND_ONE_IMAGE =
            LlmBackendCapabilities(
                supportsText = true,
                supportsImages = true,
                supportsAudio = false,
                maxImages = 1
            )
        val TEXT_IMAGE_AUDIO_ONE_IMAGE =
            LlmBackendCapabilities(
                supportsText = true,
                supportsImages = true,
                supportsAudio = true,
                maxImages = 1
            )
        val TEXT_IMAGE_AUDIO =
            LlmBackendCapabilities(
                supportsText = true,
                supportsImages = true,
                supportsAudio = true,
                maxImages = Int.MAX_VALUE
            )
    }
}

interface LlmBackend {
    val backendId: LlmBackendId
    val capabilities: LlmBackendCapabilities

    suspend fun generate(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String
}

abstract class LlmBackendAdapter(
    final override val backendId: LlmBackendId,
    final override val capabilities: LlmBackendCapabilities
) : LLM, LlmBackend {
    final override suspend fun inference(
        prompt: String,
        onToken: (String) -> Unit
    ): String = generate(LlmRequest.text(prompt), onToken)

    final override suspend fun inference(
        parts: List<PromptPart>,
        onToken: (String) -> Unit
    ): String = generate(LlmRequest.of(parts), onToken)

    final override suspend fun generate(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String {
        request.requireSupportedBy(this)
        return generateSupported(request, onToken)
    }

    protected abstract suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String
}

fun LlmRequest.unsupportedException(backend: LlmBackend): LlmUnavailableException {
    val reasons = backend.capabilities.unsupportedReasons(this).joinToString()
    return LlmUnavailableException(
        LlmFailureCause.MODEL_NOT_READY,
        "${backend.backendId.label} does not support this request: $reasons"
    )
}

fun LlmRequest.requireSupportedBy(backend: LlmBackend) {
    if (!backend.capabilities.supports(this)) {
        throw unsupportedException(backend)
    }
}

suspend fun LLM.generateRequest(
    request: LlmRequest,
    onToken: (String) -> Unit
): String =
    if (this is LlmBackend) {
        generate(request, onToken)
    } else {
        inference(request.parts, onToken)
    }
