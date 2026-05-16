package com.google.research.air.cosmo.lib.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.research.air.cosmo.runtime.CosmoBuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
@OptIn(ExperimentalApi::class)
class LiteRtLmLLM
@Inject
constructor(
    @ApplicationContext private val appContext: Context
) : LlmBackendAdapter(
    backendId = LlmBackendId.LITERT_LM,
    capabilities = LlmBackendCapabilities.TEXT_IMAGE_AUDIO_ONE_IMAGE
) {
    private val engineMutex = Mutex()
    private val inferenceMutex = Mutex()
    private var initializedEngine: InitializedEngine? = null

    override suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String {
        val contents = request.parts.toContents()
        val engine = engine()
        return try {
            withTimeout(INFERENCE_TIMEOUT_MS) {
                inferenceMutex.withLock {
                    engine.engine.createConversation(conversationConfig()).use { conversation ->
                        val response = StringBuilder()
                        conversation.sendMessageAsync(contents).collect { message ->
                            val text = message.text()
                            if (text.isNotEmpty()) {
                                response.append(text)
                                onToken(text)
                            }
                        }
                        if (response.isEmpty()) {
                            val text = conversation.sendMessage(contents).text()
                            response.append(text)
                            if (text.isNotEmpty()) onToken(text)
                        }
                        if (response.isEmpty()) {
                            throw LlmUnavailableException(
                                LlmFailureCause.INTERNAL,
                                "LiteRT-LM returned an empty response from ${engine.backendName}"
                            )
                        }
                        response.toString()
                    }
                }
            }
        } catch (e: LlmUnavailableException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            throw LlmUnavailableException(LlmFailureCause.TIMEOUT, "LiteRT-LM inference timed out", e)
        } catch (e: LiteRtLmJniException) {
            throw LlmUnavailableException(LlmFailureCause.INTERNAL, "LiteRT-LM inference failed", e)
        } catch (e: IllegalStateException) {
            throw LlmUnavailableException(LlmFailureCause.INTERNAL, "LiteRT-LM engine is not ready", e)
        }
    }

    private suspend fun engine(): InitializedEngine =
        engineMutex.withLock {
            initializedEngine?.let { return@withLock it }
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            val modelFile = resolveModelFile()
            ExperimentalFlags.enableSpeculativeDecoding = modelSupportsSpeculativeDecoding(modelFile)
            val failures = mutableListOf<Pair<String, Throwable>>()
            backendCandidates().forEach { candidate ->
                val engine =
                    try {
                        withContext(Dispatchers.IO) {
                            Engine(
                                EngineConfig(
                                    modelPath = modelFile.absolutePath,
                                    backend = candidate.backend,
                                    visionBackend = candidate.backend,
                                    audioBackend = candidate.backend,
                                    maxNumTokens = CosmoBuildConfig.localLiteRtLmMaxTokens,
                                    maxNumImages = MAX_NUM_IMAGES,
                                    cacheDir = cacheDir().absolutePath
                                )
                            ).also { it.initialize() }
                        }
                    } catch (e: RuntimeException) {
                        failures += candidate.name to e
                        return@forEach
                    }
                return@withLock InitializedEngine(candidate.name, engine).also { initializedEngine = it }
            }
            throw liteRtUnavailable(failures, modelFile)
        }

    private fun backendCandidates(): List<BackendCandidate> {
        val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir.orEmpty()
        val npu = BackendCandidate("NPU", Backend.NPU(nativeLibraryDir))
        val gpu = BackendCandidate("GPU", Backend.GPU())
        val cpuThreads = Runtime.getRuntime().availableProcessors().coerceIn(MIN_CPU_THREADS, MAX_CPU_THREADS)
        val cpu = BackendCandidate("CPU", Backend.CPU(cpuThreads))
        return when (CosmoBuildConfig.localLiteRtLmBackend.trim().uppercase(Locale.US)) {
            "NPU" -> listOf(npu, gpu, cpu)
            "GPU" -> listOf(gpu, cpu)
            "CPU" -> listOf(cpu)
            else -> listOf(npu, gpu, cpu)
        }
    }

    private fun List<PromptPart>.toContents(): Contents {
        if (isEmpty()) {
            throw LlmUnavailableException(LlmFailureCause.CONFIGURATION, "LiteRT-LM prompt is empty")
        }
        val content =
            map { part ->
                when (part) {
                    is PromptPart.Text -> Content.Text(part.text)
                    is PromptPart.Image -> Content.ImageBytes(part.imageBytes)
                    is PromptPart.Audio -> Content.AudioBytes(part.audioBytes)
                }
            }
        if (content.filterIsInstance<Content.Text>().all { it.text.isBlank() } && content.size == 1) {
            throw LlmUnavailableException(LlmFailureCause.CONFIGURATION, "LiteRT-LM text prompt is empty")
        }
        return Contents.of(content)
    }

    private fun modelSupportsSpeculativeDecoding(modelFile: File): Boolean {
        val capabilities =
            try {
                Capabilities(modelFile.absolutePath)
            } catch (_: RuntimeException) {
                return false
            }
        return try {
            capabilities.hasSpeculativeDecodingSupport()
        } catch (_: RuntimeException) {
            false
        } finally {
            capabilities.close()
        }
    }

    private fun conversationConfig(): ConversationConfig =
        ConversationConfig(systemInstruction = Contents.of(SYSTEM_INSTRUCTION))

    private fun Message.text(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }

    private fun cacheDir(): File =
        File(appContext.cacheDir, "litertlm").also { dir ->
            if (!dir.exists()) dir.mkdirs()
        }

    /**
     * Resolves an app-private LiteRT-LM file deployed by `scripts/deploy-gemma4-litertlm.sh`
     * before falling back to the bundled asset. Replaces the 4-mode `LocalLlmModelDelivery`
     * state machine deleted per architecture-delta C4 row 6. If runtime download is ever
     * needed again, re-introduce it here at the call site rather than as a separate layer.
     *
     * Paths are keyed by `cosmo.localLlm.litertLmModelId` via [storageSlug], which mirrors
     * `scripts/model-storage-id.py` so deployment scripts and runtime lookup agree.
     */
    private fun resolveModelFile(): File {
        val appPrivate = appPrivateModelFile()
        if (appPrivate.exists() && appPrivate.length() > 0) return appPrivate
        return stageBundledModel()
    }

    private fun stageBundledModel(): File {
        val target = stagedFile()
        if (target.exists() && target.length() > 0) return target
        val parent = target.parentFile
            ?: stageFailure(LlmFailureCause.MODEL_NOT_READY, "no writable parent at ${target.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) {
            stageFailure(LlmFailureCause.MODEL_NOT_READY, "parent cannot be created: ${parent.absolutePath}")
        }
        val temp = File(parent, "${target.name}.part")
        if (temp.exists()) temp.delete()
        val copyError =
            runCatching {
                appContext.assets.open(bundledAssetPath()).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            }.exceptionOrNull()
        if (copyError != null) {
            if (temp.exists()) temp.delete()
            throw LlmUnavailableException(
                LlmFailureCause.LOCAL_MODEL_UNAVAILABLE,
                "LiteRT-LM bundled asset missing at ${bundledAssetPath()}: ${copyError.message}",
                copyError
            )
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            stageFailure(LlmFailureCause.MODEL_NOT_READY, "rename failed: ${target.absolutePath}")
        }
        return target
    }

    private fun stageFailure(cause: LlmFailureCause, message: String): Nothing =
        throw LlmUnavailableException(cause, "LiteRT-LM bundled model $message")

    private fun bundledAssetPath(): String = "models/${storageSlug()}/bundled.litertlm"

    private fun stagedFile(): File = File(appContext.filesDir, "models/${storageSlug()}/bundled.litertlm")

    private fun appPrivateModelFile(): File = File(appContext.filesDir, "models/${storageSlug()}/model.litertlm")

    private fun storageSlug(): String {
        val raw = CosmoBuildConfig.localLiteRtLmModelId.trim()
        return liteRtLmModelStorageSlug(raw)
    }

    private fun liteRtUnavailable(
        failures: List<Pair<String, Throwable>>,
        modelFile: File
    ): LlmUnavailableException {
        val message =
            failures.joinToString(
                prefix = "LiteRT-LM could not initialize ${modelFile.absolutePath}: "
            ) { (backend, failure) ->
                "$backend=${failure.javaClass.simpleName}"
            }
        return LlmUnavailableException(LlmFailureCause.MODEL_NOT_READY, message).also { aggregate ->
            failures.forEach { (_, failure) -> aggregate.addSuppressed(failure) }
        }
    }

    private data class BackendCandidate(
        val name: String,
        val backend: Backend
    )

    private data class InitializedEngine(
        val backendName: String,
        val engine: Engine
    )

    private companion object {
        private const val INFERENCE_TIMEOUT_MS = 45_000L
        private const val MAX_NUM_IMAGES = 1
        private const val MIN_CPU_THREADS = 2
        private const val MAX_CPU_THREADS = 6
        private const val SYSTEM_INSTRUCTION = "You are Cosmo's on-device assistant. Answer concisely and follow the user request."
    }
}

private const val DEFAULT_MODEL_STORAGE_ID = "default-litertlm"
private const val MODEL_ID_HASH_CHARS = 16
private const val MAX_MODEL_STORAGE_ID_CHARS = 96
private val UNSAFE_MODEL_ID_CHARS = Regex("[^A-Za-z0-9._-]+")

internal fun liteRtLmModelStorageSlug(modelId: String): String {
    val raw = modelId.trim()
    val normalized =
        UNSAFE_MODEL_ID_CHARS
            .replace(raw, "-")
            .trim('-', '_', '.')
            .ifEmpty { DEFAULT_MODEL_STORAGE_ID }
    val hashSuffix = raw.sha256Hex().take(MODEL_ID_HASH_CHARS)
    val maxSlugChars = MAX_MODEL_STORAGE_ID_CHARS - MODEL_ID_HASH_CHARS - 1
    val slug = normalized.take(maxSlugChars).trim('-', '_', '.').ifEmpty { DEFAULT_MODEL_STORAGE_ID }
    return "$slug-$hashSuffix"
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
