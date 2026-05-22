package com.google.research.air.cosmo.gatekeeping

import android.content.Context
import com.google.research.air.cosmo.BuildConfig
import com.google.research.air.cosmo.gatekeeping.models.AudioGatekeeperLiteRtImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

private fun inherentMetadataTextLoader(context: Context): () -> String? = {
    GatekeeperModelIds.filenameFor(GatekeeperModelIds.INHERENT_V0_METADATA)?.let { filename ->
        runCatching {
            context.assets.open("models/$filename").bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

@Singleton
class InherentAudioGatekeepingModel internal constructor(
    private val loadAssetBytes: (String) -> ByteArray?,
    private val loadMetadataText: () -> String?,
    private val nativeCreate: (ByteArray, Boolean, IntArray, IntArray) -> Long,
    private val nativeRun: (Long, FloatBuffer, FloatBuffer) -> Boolean,
    private val nativeDestroy: (Long) -> Unit
) : GatekeepingModel {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        loadAssetBytes = GatekeeperModelIds.byteLoaderFor(context),
        loadMetadataText = inherentMetadataTextLoader(context),
        nativeCreate = { modelBytes, useNpu, expectedInputShape, expectedOutputShape ->
            InherentRuntimeBridge.create(
                modelBytes,
                useNpu,
                expectedInputShape,
                expectedOutputShape,
                context.applicationInfo.nativeLibraryDir
            )
        },
        nativeRun = InherentRuntimeBridge::run,
        nativeDestroy = InherentRuntimeBridge::destroy
    )

    private var frontendHandle: Long = 0L
    private var backendHandle: Long = 0L
    private var backendModelId: String = MODEL_ID_CPU
    private var skipGoogleTensorBackend: Boolean = false
    private val thresholds: FloatArray by lazy { loadThresholds() }

    override suspend fun predict(input: String): GatekeepingResult =
        throw UnsupportedOperationException("Inherent audio gatekeeper does not support text prediction")

    fun isUsable(): Boolean =
        runCatching {
            ensureFrontend()
            ensureBackend()
            true
        }.getOrElse {
            close()
            false
        }

    override suspend fun predict(pcm: ByteArray): GatekeepingResult {
        val scores =
            runCatching { run(pcm) }
                .getOrElse { error ->
                    return unavailable(error.message ?: "inherent_unavailable")
                }
        val detected = mapScores(scores)
        return GatekeepingResult(
            detectedSkills = detected,
            modelId = backendModelId,
            detectedBy = if (detected.isEmpty()) "none" else "audio_gatekeeper",
            triggeredFulfillment = detected.isNotEmpty(),
            input = "Ambient raw audio: latest buffered PCM window",
            inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
            detector = if (detected.isEmpty()) GatekeepingDetector.NONE else GatekeepingDetector.AUDIO_GATEKEEPER,
            screenSignals = if (BuildConfig.DEBUG) scoreSignals(scores) else emptyMap()
        )
    }

    override fun close() {
        val currentFrontend = frontendHandle
        val currentBackend = backendHandle
        frontendHandle = 0L
        backendHandle = 0L
        backendModelId = MODEL_ID_CPU
        if (currentFrontend != 0L) runCatching { nativeDestroy(currentFrontend) }
        if (currentBackend != 0L) runCatching { nativeDestroy(currentBackend) }
    }

    private fun run(pcm: ByteArray): FloatArray {
        val frontendInput = pcm16ToFloatBuffer(pcm)
        val melBuffer =
            ByteBuffer
                .allocateDirect(MAX_FRAMES * MEL_BINS * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        if (!nativeRun(ensureFrontend(), frontendInput, melBuffer)) {
            failUnavailable("inherent_frontend_run_failed")
        }
        zeroMelTail(melBuffer, frameCountForPcmBytes(pcm.size))

        val output =
            ByteBuffer
                .allocateDirect(NUM_INTENTS * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        return runBackendWithFallback(melBuffer, output)
    }

    private fun runBackendWithFallback(
        melBuffer: FloatBuffer,
        output: FloatBuffer
    ): FloatArray {
        if (!runBackendOnce(melBuffer, output)) {
            if (!retireGoogleTensorBackend()) failUnavailable("inherent_native_run_failed")
            clearFloatBuffer(output)
            if (!runBackendOnce(melBuffer, output)) failUnavailable("inherent_native_run_failed")
        }
        var scores = readScores(output)
        if (scores.any { !it.isFinite() }) {
            if (!retireGoogleTensorBackend()) failUnavailable("inherent_invalid_output")
            clearFloatBuffer(output)
            if (!runBackendOnce(melBuffer, output)) failUnavailable("inherent_native_run_failed")
            scores = readScores(output)
            if (scores.any { !it.isFinite() }) failUnavailable("inherent_invalid_output")
        }
        return scores
    }

    private fun runBackendOnce(
        melBuffer: FloatBuffer,
        output: FloatBuffer
    ): Boolean {
        melBuffer.position(0)
        output.position(0)
        return runCatching { nativeRun(ensureBackend(), melBuffer, output) }.getOrDefault(false)
    }

    private fun readScores(output: FloatBuffer): FloatArray {
        output.position(0)
        return FloatArray(NUM_INTENTS) { output.get() }
    }

    private fun clearFloatBuffer(buffer: FloatBuffer) {
        for (index in 0 until buffer.capacity()) {
            buffer.put(index, 0f)
        }
        buffer.position(0)
    }

    private fun retireGoogleTensorBackend(): Boolean {
        if (backendModelId != MODEL_ID_GOOGLE_TENSOR_G5) return false
        val currentBackend = backendHandle
        backendHandle = 0L
        backendModelId = MODEL_ID_CPU
        skipGoogleTensorBackend = true
        if (currentBackend != 0L) runCatching { nativeDestroy(currentBackend) }
        return true
    }

    private fun ensureFrontend(): Long {
        if (frontendHandle != 0L) return frontendHandle
        val bytes = loadAssetBytes(GatekeeperModelIds.AUDIO_FRONTEND)
            ?: failUnavailable("inherent_frontend_unavailable")
        val created =
            nativeCreate(
                bytes,
                false,
                intArrayOf(1, MAX_SAMPLES),
                intArrayOf(1, MAX_FRAMES, MEL_BINS)
            )
        if (created == 0L) failUnavailable("inherent_frontend_cpu_create_failed")
        frontendHandle = created
        return created
    }

    private fun ensureBackend(): Long {
        if (backendHandle != 0L) return backendHandle
        val failures = mutableListOf<String>()
        for (candidate in backendCandidates()) {
            val bytes = loadAssetBytes(candidate.assetId)
            if (bytes == null) {
                failures += "missing:${candidate.assetId}"
                continue
            }
            val created =
                runCatching {
                    nativeCreate(
                        bytes,
                        candidate.useNpu,
                        intArrayOf(1, MAX_FRAMES, MEL_BINS),
                        intArrayOf(1, NUM_INTENTS)
                    )
                }.getOrElse { error ->
                    failures += "create_exception:${candidate.assetId}:${error.javaClass.simpleName}"
                    0L
                }
            if (created != 0L) {
                backendHandle = created
                backendModelId = candidate.modelId
                return created
            }
            failures += "create_zero:${candidate.assetId}"
        }
        failUnavailable("inherent_litert_all_backends_failed:${failures.joinToString("|")}")
    }

    private fun backendCandidates(): List<BackendCandidate> =
        if (SocClass.current() == SocClass.GOOGLE_TENSOR && !skipGoogleTensorBackend) {
            listOf(
                BackendCandidate(
                    assetId = GatekeeperModelIds.INHERENT_V0_GOOGLE_TENSOR_G5,
                    useNpu = true,
                    modelId = MODEL_ID_GOOGLE_TENSOR_G5
                ),
                BackendCandidate(
                    assetId = GatekeeperModelIds.INHERENT_V0,
                    useNpu = false,
                    modelId = MODEL_ID_CPU
                )
            )
        } else {
            listOf(
                BackendCandidate(
                    assetId = GatekeeperModelIds.INHERENT_V0,
                    useNpu = false,
                    modelId = MODEL_ID_CPU
                )
            )
        }

    private fun pcm16ToFloatBuffer(pcm: ByteArray): FloatBuffer {
        val output =
            ByteBuffer
                .allocateDirect(MAX_SAMPLES * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        var written = 0
        var index = 0
        while (index + 1 < pcm.size && written < MAX_SAMPLES) {
            val sample = ((pcm[index + 1].toInt() shl 8) or (pcm[index].toInt() and 0xff)).toShort().toInt()
            output.put(sample / PCM_16BIT_SCALE)
            written += 1
            index += 2
        }
        while (written < MAX_SAMPLES) {
            output.put(0f)
            written += 1
        }
        output.position(0)
        return output
    }

    private fun frameCountForPcmBytes(pcmBytes: Int): Int = AudioGatekeepingContract.frameCountForPcm16Bytes(pcmBytes)

    private fun zeroMelTail(
        melBuffer: FloatBuffer,
        frameCount: Int
    ) {
        val start = (frameCount.coerceIn(0, MAX_FRAMES)) * MEL_BINS
        for (index in start until melBuffer.capacity()) {
            melBuffer.put(index, 0f)
        }
        melBuffer.position(0)
    }

    private fun mapScores(scores: FloatArray): Map<String, Float> =
        AudioGatekeepingContract.mapScoresToSkills(scores, thresholds)

    private fun scoreSignals(scores: FloatArray): Map<String, Float> {
        val topIndex = scores.indices.maxByOrNull { scores[it] } ?: return emptyMap()
        return buildMap {
            putAll(AudioGatekeepingContract.scoreSignals("inherent", scores))
            put("inherent_top_index", topIndex.toFloat())
            put("inherent_top_score", scores[topIndex])
        }
    }

    private fun loadThresholds(): FloatArray {
        val json = loadMetadataText()?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return DEFAULT_THRESHOLDS
        val array = json.optJSONArray("thresholds")
        if (array != null && array.length() == NUM_INTENTS) {
            return FloatArray(NUM_INTENTS) { index -> array.optDouble(index, DEFAULT_THRESHOLDS[index].toDouble()).toFloat() }
        }
        val keys = json.optJSONArray("threshold_keys_in_order")
        val thresholdsByKey = json.optJSONObject("default_thresholds")
        if (keys == null || thresholdsByKey == null || keys.length() != NUM_INTENTS) return DEFAULT_THRESHOLDS
        val actualKeys = List(NUM_INTENTS) { index -> keys.optString(index) }
        if (actualKeys != AudioGatekeepingContract.THRESHOLD_KEYS) return DEFAULT_THRESHOLDS
        return FloatArray(NUM_INTENTS) { index ->
            val key = actualKeys[index]
            thresholdsByKey.optDouble(key, DEFAULT_THRESHOLDS[index].toDouble()).toFloat()
        }
    }

    private fun unavailable(reason: String): GatekeepingResult =
        GatekeepingResult(
            detectedSkills = emptyMap(),
            modelId = "inherent_unavailable:$reason",
            detectedBy = "none",
            triggeredFulfillment = false,
            input = "Ambient raw audio: latest buffered PCM window",
            inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
            detector = GatekeepingDetector.NONE
        )

    private fun failUnavailable(reason: String): Nothing = throw IllegalStateException(reason)

    companion object {
        const val METADATA_ASSET_PATH = "models/inherent.metadata.json"
        const val MODEL_ID_CPU = "inherent_v0"
        const val MODEL_ID_GOOGLE_TENSOR_G5 = "inherent_v0_google_tensor_g5"
        const val MEL_BINS: Int = AudioGatekeepingContract.MEL_BINS
        const val MAX_FRAMES: Int = AudioGatekeepingContract.MAX_FRAMES
        const val MAX_SAMPLES: Int = AudioGatekeepingContract.MAX_SAMPLES
        const val NUM_INTENTS: Int = AudioGatekeepingContract.NUM_HEADS
        private const val PCM_16BIT_SCALE: Float = AudioGatekeepingContract.PCM_16BIT_SCALE

        private val DEFAULT_THRESHOLDS = AudioGatekeepingContract.DEFAULT_THRESHOLDS
    }

    private data class BackendCandidate(
        val assetId: String,
        val useNpu: Boolean,
        val modelId: String
    )
}

private object TensorFlowLiteInherentRuntimeRegistry {
    private val nextHandle = AtomicLong(1L)
    private val runtimes = ConcurrentHashMap<Long, TensorFlowLiteInherentRuntime>()

    fun create(
        modelBytes: ByteArray,
        expectedInputShape: IntArray,
        expectedOutputShape: IntArray
    ): Long =
        runCatching {
            val runtime = TensorFlowLiteInherentRuntime(modelBytes, expectedInputShape, expectedOutputShape)
            val handle = nextHandle.getAndIncrement()
            runtimes[handle] = runtime
            handle
        }.getOrDefault(0L)

    fun run(
        handle: Long,
        input: FloatBuffer,
        output: FloatBuffer
    ): Boolean = runtimes[handle]?.run(input, output) == true

    fun destroy(handle: Long) {
        runtimes.remove(handle)?.close()
    }
}

private object InherentRuntimeBridge {
    private val liteRtHandles = ConcurrentHashMap.newKeySet<Long>()

    fun create(
        modelBytes: ByteArray,
        useNpu: Boolean,
        expectedInputShape: IntArray,
        expectedOutputShape: IntArray,
        dispatchLibraryDir: String? = null
    ): Long {
        if (useNpu) {
            if (!AudioGatekeeperLiteRtImpl.nativeCanInitializeGoogleTensorNpu()) {
                return 0L
            }
            val handle =
                runCatching {
                    AudioGatekeeperLiteRtImpl.nativeCreate(
                        modelBytes,
                        true,
                        expectedInputShape,
                        expectedOutputShape,
                        dispatchLibraryDir
                    )
                }.getOrDefault(0L)
            if (handle != 0L) {
                liteRtHandles += handle
                return handle
            }
        }
        return TensorFlowLiteInherentRuntimeRegistry.create(
            modelBytes,
            expectedInputShape,
            expectedOutputShape
        )
    }

    fun run(
        handle: Long,
        input: FloatBuffer,
        output: FloatBuffer
    ): Boolean =
        if (liteRtHandles.contains(handle)) {
            AudioGatekeeperLiteRtImpl.nativeRun(handle, input, output)
        } else {
            TensorFlowLiteInherentRuntimeRegistry.run(handle, input, output)
        }

    fun destroy(handle: Long) {
        if (liteRtHandles.remove(handle)) {
            runCatching { AudioGatekeeperLiteRtImpl.nativeDestroy(handle) }
        } else {
            TensorFlowLiteInherentRuntimeRegistry.destroy(handle)
        }
    }
}

private class TensorFlowLiteInherentRuntime(
    modelBytes: ByteArray,
    expectedInputShape: IntArray,
    expectedOutputShape: IntArray
) : AutoCloseable {
    private val modelBuffer =
        ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
            .also { buffer ->
                buffer.put(modelBytes)
                buffer.position(0)
            }
    private val interpreter = Interpreter(modelBuffer, Interpreter.Options())

    init {
        check(interpreter.inputTensorCount == 1) { "inherent_tflite_input_count" }
        check(interpreter.outputTensorCount == 1) { "inherent_tflite_output_count" }
        check(interpreter.getInputTensor(0).shape().contentEquals(expectedInputShape)) {
            "inherent_tflite_input_shape"
        }
        check(interpreter.getOutputTensor(0).shape().contentEquals(expectedOutputShape)) {
            "inherent_tflite_output_shape"
        }
    }

    @Synchronized
    fun run(
        input: FloatBuffer,
        output: FloatBuffer
    ): Boolean =
        runCatching {
            input.position(0)
            output.position(0)
            interpreter.run(input, output)
            output.position(0)
            true
        }.getOrDefault(false)

    override fun close() {
        interpreter.close()
    }
}
