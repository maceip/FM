package com.google.research.air.cosmo.lib.llm

import android.content.Context
import com.google.ai.edge.aicore.ConnectionException
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.InferenceException
import com.google.ai.edge.aicore.PreparationException
import com.google.research.air.cosmo.di.BackgroundExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

interface AICoreClientWrapper {
    fun isAvailable(): Boolean

    suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit
    ): String
}

@Singleton
class EdgeAICoreClientWrapper
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    @BackgroundExecutor private val callbackExecutor: Executor
) : AICoreClientWrapper {
    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val model: GenerativeModel by lazy {
        GenerativeModel(
            GenerationConfig
                .Builder()
                .apply {
                    context = appContext
                    workerExecutor = this@EdgeAICoreClientWrapper.workerExecutor
                    callbackExecutor = this@EdgeAICoreClientWrapper.callbackExecutor
                    maxOutputTokens = MAX_OUTPUT_TOKENS
                    candidateCount = 1
                }.build()
        )
    }

    override fun isAvailable(): Boolean = AICORE_PACKAGES.any { packageName ->
        runCatching { appContext.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }

    override suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit
    ): String {
        if (!isAvailable()) {
            throw LlmUnavailableException(LlmFailureCause.AICORE_NOT_INSTALLED, "AICore not installed")
        }
        return try {
            withTimeout(AICORE_TIMEOUT_MS) {
                model.prepareInferenceEngine()
                val builder = StringBuilder()
                var previous = ""
                model.generateContentStream(prompt).collect { response ->
                    val text = response.text.orEmpty()
                    val delta = if (text.startsWith(previous)) text.removePrefix(previous) else text
                    if (delta.isNotEmpty()) {
                        builder.append(delta)
                        onToken(delta)
                    }
                    previous = text
                }
                if (builder.isEmpty()) {
                    val text = model.generateContent(prompt).text.orEmpty()
                    if (text.isNotEmpty()) {
                        builder.append(text)
                        onToken(text)
                    }
                }
                if (builder.isEmpty()) {
                    throw LlmUnavailableException(LlmFailureCause.INTERNAL, "AICore returned an empty response")
                }
                builder.toString()
            }
        } catch (e: TimeoutCancellationException) {
            throw LlmUnavailableException(LlmFailureCause.TIMEOUT, "AICore inference timed out", e)
        } catch (e: ConnectionException) {
            throw LlmUnavailableException(LlmFailureCause.AICORE_NOT_INSTALLED, "AICore service connection failed", e)
        } catch (e: PreparationException) {
            throw LlmUnavailableException(LlmFailureCause.MODEL_NOT_READY, "AICore model is not ready", e)
        } catch (e: InferenceException) {
            throw LlmUnavailableException(LlmFailureCause.INTERNAL, "AICore inference failed", e)
        } catch (e: GenerativeAIException) {
            throw LlmUnavailableException(LlmFailureCause.INTERNAL, e.message ?: "AICore failed", e)
        }
    }

    companion object {
        val AICORE_PACKAGES =
            listOf(
                "com.google.android.aicore",
                "com.google.android.apps.aicore"
            )
        private const val AICORE_TIMEOUT_MS = 30_000L
        private const val MAX_OUTPUT_TOKENS = 1024

        /**
         * Preferred AICore feature IDs (highest first).
         * As of May 2026 the public AAR does not expose a way to request a specific feature ID
         * (e.g. 108 = LLM-Nano V3). We document the intent here so when the AAR adds the API
         * we can switch without changing call sites.
         */
        val featurePreferenceOrder = listOf(108, 107, 106)
    }
}
