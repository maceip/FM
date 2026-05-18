package com.google.research.air.cosmo.gatekeeping

import android.content.Context
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
        nativeCreate = { modelBuffer, _, expectedInputShape, expectedOutputShape ->
            TensorFlowLiteInherentRuntimeRegistry.create(
                modelBuffer,
                expectedInputShape,
                expectedOutputShape
            )
        },
        nativeRun = { handle, input, output -> TensorFlowLiteInherentRuntimeRegistry.run(handle, input, output) },
        nativeDestroy = { handle -> TensorFlowLiteInherentRuntimeRegistry.destroy(handle) }
    )

    private var frontendHandle: Long = 0L
    private var backendHandle: Long = 0L
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
            modelId = "inherent_v0",
            detectedBy = if (detected.isEmpty()) "none" else "audio_gatekeeper",
            triggeredFulfillment = detected.isNotEmpty(),
            input = "Ambient raw audio: latest buffered PCM window",
            inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
            detector = if (detected.isEmpty()) GatekeepingDetector.NONE else GatekeepingDetector.AUDIO_GATEKEEPER
        )
    }

    override fun close() {
        val currentFrontend = frontendHandle
        val currentBackend = backendHandle
        frontendHandle = 0L
        backendHandle = 0L
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

        val output =
            ByteBuffer
                .allocateDirect(NUM_INTENTS * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        melBuffer.position(0)
        if (!nativeRun(ensureBackend(), melBuffer, output)) {
            failUnavailable("inherent_native_run_failed")
        }
        output.position(0)
        val scores = FloatArray(NUM_INTENTS) { output.get() }
        if (scores.any { !it.isFinite() }) {
            failUnavailable("inherent_invalid_output")
        }
        return scores
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
        val bytes = loadAssetBytes(GatekeeperModelIds.INHERENT_V0)
            ?: failUnavailable("inherent_asset_unavailable")
        val created =
            nativeCreate(
                bytes,
                false,
                intArrayOf(1, MAX_FRAMES, MEL_BINS),
                intArrayOf(1, NUM_INTENTS)
            )
        if (created == 0L) failUnavailable("inherent_litert_cpu_create_failed")
        backendHandle = created
        return created
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

    private fun mapScores(scores: FloatArray): Map<String, Float> =
        INDEX_TO_SKILLS.entries
            .filter { (index, _) -> index < scores.size && scores[index] >= thresholds[index] }
            .associate { (index, skill) -> skill to scores[index] }

    private fun loadThresholds(): FloatArray {
        val json = loadMetadataText()?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return DEFAULT_THRESHOLDS
        val array = json.optJSONArray("thresholds")
        if (array != null && array.length() == NUM_INTENTS) {
            return FloatArray(NUM_INTENTS) { index -> array.optDouble(index, DEFAULT_THRESHOLDS[index].toDouble()).toFloat() }
        }
        val keys = json.optJSONArray("threshold_keys_in_order")
        val thresholdsByKey = json.optJSONObject("default_thresholds")
        if (keys == null || thresholdsByKey == null || keys.length() != NUM_INTENTS) return DEFAULT_THRESHOLDS
        return FloatArray(NUM_INTENTS) { index ->
            val key = keys.optString(index)
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
        const val MEL_BINS: Int = 128
        const val MAX_FRAMES: Int = 3_000
        const val MAX_SAMPLES: Int = 960_000
        const val NUM_INTENTS: Int = 13
        private const val PCM_16BIT_SCALE: Float = 32_768.0f

        private val DEFAULT_THRESHOLDS = FloatArray(NUM_INTENTS) { 0.5f }

        private val INDEX_TO_SKILLS =
            mapOf(
                0 to "add_to_memory",
                1 to "add_to_list",
                2 to "define_term",
                3 to "query_photos",
                4 to "create_calendar_event",
                5 to "create_doc",
                6 to "provide_person_context",
                7 to "provide_event_context",
                8 to "deep_research",
                9 to "provide_insight",
                10 to "browsing_agent",
                11 to "call_agent",
                12 to "add_timer"
            )
    }
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
