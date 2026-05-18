package com.google.research.air.cosmo.lib.llm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

@Singleton
class MlKitPromptLLM
@Inject
constructor() : LlmBackendAdapter(
    backendId = LlmBackendId.ML_KIT_PROMPT,
    capabilities = LlmBackendCapabilities.TEXT_AND_ONE_IMAGE
) {
    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val modelClients: List<ModelClient> by lazy {
        MODEL_PROFILES.map { profile ->
            val config =
                generationConfig {
                    workerExecutor = this@MlKitPromptLLM.workerExecutor
                    modelConfig =
                        modelConfig {
                            preference = profile.preference
                            releaseStage = ModelReleaseStage.STABLE
                        }
                }
            ModelClient(profile, Generation.getClient(config))
        }
    }

    override suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String = runGeneration(request = request.parts.toRequest(), onToken = onToken)

    private suspend fun runGeneration(
        request: MlKitPromptRequest,
        onToken: (String) -> Unit
    ): String {
        val model = readyModel()
        return try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                val response = StringBuilder()
                model.generateContentStream(request.toGenerateContentRequest()).collect { chunk ->
                    val text = chunk.text()
                    if (text.isNotEmpty()) {
                        response.append(text)
                        onToken(text)
                    }
                }
                if (response.isEmpty()) {
                    val text = model.generateContent(request.toGenerateContentRequest()).text()
                    response.append(text)
                    if (text.isNotEmpty()) onToken(text)
                }
                if (response.isEmpty()) {
                    fail(LlmFailureCause.INTERNAL, "ML Kit GenAI returned an empty response")
                }
                response.toString()
            }
        } catch (e: LlmUnavailableException) {
            fail(e)
        } catch (e: TimeoutCancellationException) {
            fail(LlmFailureCause.TIMEOUT, "ML Kit GenAI inference timed out", e)
        } catch (e: GenAiException) {
            fail(e.toLlmUnavailableException("ML Kit GenAI inference failed"))
        } catch (e: IllegalArgumentException) {
            fail(LlmFailureCause.CONFIGURATION, "ML Kit GenAI rejected the prompt request", e)
        }
    }

    private suspend fun readyModel(): GenerativeModel {
        val failures = mutableListOf<Pair<String, LlmUnavailableException>>()
        modelClients.forEach { client ->
            try {
                client.model.ensureAvailable(client.profile.name)
                return client.model
            } catch (e: LlmUnavailableException) {
                failures += client.profile.name to e
            }
        }
        val message =
            failures.joinToString(prefix = "ML Kit GenAI Prompt is not available: ") { (name, failure) ->
                "$name=${failure.reason}"
            }
        throw LlmUnavailableException(LlmFailureCause.MODEL_NOT_READY, message).also { aggregate ->
            failures.forEach { (_, failure) -> aggregate.addSuppressed(failure) }
        }
    }

    private suspend fun GenerativeModel.ensureAvailable(profileName: String) {
        try {
            when (val status = checkStatus()) {
                FeatureStatus.AVAILABLE -> return
                FeatureStatus.DOWNLOADABLE,
                FeatureStatus.DOWNLOADING -> downloadAndWait(profileName)
                FeatureStatus.UNAVAILABLE ->
                    fail(
                        LlmFailureCause.MODEL_NOT_READY,
                        "ML Kit GenAI model $profileName is unavailable on this device"
                    )
                else ->
                    fail(
                        LlmFailureCause.MODEL_NOT_READY,
                        "ML Kit GenAI model $profileName returned unknown status $status"
                    )
            }
        } catch (e: LlmUnavailableException) {
            fail(e)
        } catch (e: GenAiException) {
            fail(e.toLlmUnavailableException("ML Kit GenAI status check failed for $profileName"))
        }
    }

    private suspend fun GenerativeModel.downloadAndWait(profileName: String) {
        try {
            var completed = false
            withTimeout(DOWNLOAD_TIMEOUT_MS) {
                download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadCompleted -> completed = true
                        is DownloadStatus.DownloadFailed ->
                            fail(status.e.toLlmUnavailableException("ML Kit GenAI model $profileName download failed"))
                        else -> Unit
                    }
                }
            }
            if (!completed && checkStatus() != FeatureStatus.AVAILABLE) {
                fail(
                    LlmFailureCause.MODEL_NOT_READY,
                    "ML Kit GenAI model $profileName download did not complete"
                )
            }
        } catch (e: LlmUnavailableException) {
            fail(e)
        } catch (e: TimeoutCancellationException) {
            fail(LlmFailureCause.TIMEOUT, "ML Kit GenAI model $profileName download timed out", e)
        } catch (e: GenAiException) {
            fail(e.toLlmUnavailableException("ML Kit GenAI model $profileName download failed"))
        }
    }

    private fun List<PromptPart>.toRequest(): MlKitPromptRequest {
        val audioCount = count { it is PromptPart.Audio }
        if (audioCount > 0) {
            fail(LlmFailureCause.MODEL_NOT_READY, "ML Kit GenAI Prompt does not support audio input")
        }
        val images = filterIsInstance<PromptPart.Image>()
        if (images.size > 1) {
            fail(LlmFailureCause.MODEL_NOT_READY, "ML Kit GenAI Prompt supports one image per request")
        }
        val text = filterIsInstance<PromptPart.Text>().joinToString("\n") { it.text }.trim()
        if (text.isEmpty()) {
            fail(LlmFailureCause.CONFIGURATION, "ML Kit GenAI Prompt requires text input")
        }
        val image =
            images.firstOrNull()?.let { part ->
                try {
                    ImagePart(part.imageBytes)
                } catch (e: IllegalArgumentException) {
                    fail(LlmFailureCause.CONFIGURATION, "ML Kit GenAI image input is invalid", e)
                }
            }
        return MlKitPromptRequest(text = text, image = image)
    }

    private fun MlKitPromptRequest.toGenerateContentRequest() =
        image?.let { imagePart ->
            generateContentRequest(imagePart, TextPart(text)) { applyGenerationDefaults() }
        } ?: generateContentRequest(TextPart(text)) { applyGenerationDefaults() }

    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.applyGenerationDefaults() {
        candidateCount = 1
        maxOutputTokens = MAX_OUTPUT_TOKENS
        temperature = TEMPERATURE
        topK = TOP_K
    }

    private fun com.google.mlkit.genai.prompt.GenerateContentResponse.text(): String =
        candidates.firstOrNull()?.text.orEmpty()

    private fun GenAiException.toLlmUnavailableException(message: String): LlmUnavailableException =
        LlmUnavailableException(
            reason =
                when (errorCode) {
                    GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> LlmFailureCause.QUOTA
                    GenAiException.ErrorCode.BUSY -> LlmFailureCause.MODEL_NOT_READY
                    GenAiException.ErrorCode.NOT_AVAILABLE,
                    GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                    GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> LlmFailureCause.MODEL_NOT_READY
                    GenAiException.ErrorCode.REQUEST_TOO_LARGE,
                    GenAiException.ErrorCode.REQUEST_TOO_SMALL,
                    GenAiException.ErrorCode.INVALID_INPUT_IMAGE -> LlmFailureCause.CONFIGURATION
                    else -> LlmFailureCause.INTERNAL
                },
            message = message,
            cause = this
        )

    private fun fail(exception: LlmUnavailableException): Nothing = throw exception

    private fun fail(
        reason: LlmFailureCause,
        message: String,
        cause: Throwable? = null
    ): Nothing = throw LlmUnavailableException(reason, message, cause)

    private data class ModelProfile(
        val name: String,
        val preference: Int
    )

    private data class ModelClient(
        val profile: ModelProfile,
        val model: GenerativeModel
    )

    private data class MlKitPromptRequest(
        val text: String,
        val image: ImagePart?
    )

    private companion object {
        private const val DOWNLOAD_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 30_000L
        private const val MAX_OUTPUT_TOKENS = 256
        private const val TEMPERATURE = 0.2f
        private const val TOP_K = 16
        private val MODEL_PROFILES =
            listOf(
                ModelProfile("stable-full", ModelPreference.FULL),
                ModelProfile("stable-fast", ModelPreference.FAST)
            )
    }
}
