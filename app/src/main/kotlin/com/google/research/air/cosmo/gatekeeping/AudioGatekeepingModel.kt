package com.google.research.air.cosmo.gatekeeping

import android.content.Context
import com.google.android.apps.hardware.protostar.components.delegate.DarwinnDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter

private fun mappedModelBufferLoader(context: Context): (String) -> MappedByteBuffer? = { id ->
    val filename = GatekeeperModelIds.filenameFor(id)
    if (filename == null) {
        null
    } else {
        runCatching {
            context.assets.openFd("models/$filename").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
        }.getOrNull()
    }
}

internal interface SplitConformerRuntime : AutoCloseable {
    fun run(
        audioInputTensor: Array<FloatArray>,
        spectrogramBuffer: ByteBuffer,
        paddingMaskTensor: Array<BooleanArray>,
        intentOutputBuffer: Array<FloatArray>
    )
}

@Singleton
class ConformerEncoderImpl internal constructor(
    private val loadAssetBuffer: (String) -> ByteBuffer?,
    private val runtimeFactory: (ByteBuffer, ByteBuffer) -> SplitConformerRuntime
) : AutoCloseable {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        loadAssetBuffer = mappedModelBufferLoader(context),
        runtimeFactory = { frontendModel, backendModel ->
            TensorFlowLiteSplitConformerRuntime(context, frontendModel, backendModel)
        }
    )

    private val audioInputBuffer = FloatArray(MAX_SAMPLES)
    private val audioInputTensor = arrayOf(audioInputBuffer)
    private val paddingMaskBuffer = BooleanArray(MAX_FRAMES)
    private val paddingMaskTensor = arrayOf(paddingMaskBuffer)
    private val spectrogramBuffer =
        ByteBuffer
            .allocateDirect(MAX_FRAMES * MEL_BINS * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
    private val intentOutputBuffer = arrayOf(FloatArray(NUM_INTENTS))
    private var runtime: SplitConformerRuntime? = null

    fun run(pcm: ByteArray): FloatArray {
        preprocess(pcm)
        ensureRuntime().run(audioInputTensor, spectrogramBuffer, paddingMaskTensor, intentOutputBuffer)
        val scores = intentOutputBuffer[0].copyOf()
        if (scores.any { !it.isFinite() }) {
            failUnavailable("audio_backend_invalid_output")
        }
        return scores
    }

    override fun close() {
        val current = runtime
        runtime = null
        if (current != null) runCatching { current.close() }
    }

    private fun ensureRuntime(): SplitConformerRuntime {
        runtime?.let { return it }
        val frontendModel = loadAssetBuffer(GatekeeperModelIds.AUDIO_FRONTEND)
            ?: failUnavailable("audio_frontend_unavailable")
        val backendModel = loadAssetBuffer(GatekeeperModelIds.AUDIO_GATEKEEPER)
            ?: failUnavailable("audio_backend_unavailable")
        val created =
            runCatching { runtimeFactory(frontendModel, backendModel) }
                .getOrElse { error ->
                    failUnavailable(error.message ?: "audio_tflite_split_create_failed")
                }
        runtime = created
        return created
    }

    private fun preprocess(pcm: ByteArray) {
        val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val validSamples = minOf(samples.remaining(), MAX_SAMPLES)
        var written = 0
        while (written < validSamples) {
            audioInputBuffer[written] = samples.get(written) / PCM_16BIT_SCALE
            written += 1
        }
        audioInputBuffer.fill(0f, validSamples, MAX_SAMPLES)

        val validFrames = minOf((validSamples + FRAME_STRIDE_SAMPLES - 1) / FRAME_STRIDE_SAMPLES, MAX_FRAMES)
        paddingMaskBuffer.fill(true, 0, validFrames)
        paddingMaskBuffer.fill(false, validFrames, MAX_FRAMES)
    }

    private fun failUnavailable(reason: String): Nothing = throw IllegalStateException(reason)

    companion object {
        const val SAMPLE_RATE: Int = 16_000
        const val MEL_BINS: Int = 128
        const val MAX_FRAMES: Int = 3_000
        const val MAX_SAMPLES: Int = 960_000
        const val NUM_INTENTS: Int = 13
        private const val FRAME_STRIDE_SAMPLES: Int = 320
        private const val PCM_16BIT_SCALE: Float = 32_768.0f
    }
}

private class TensorFlowLiteSplitConformerRuntime(
    context: Context,
    frontendModel: ByteBuffer,
    backendModel: ByteBuffer
) : SplitConformerRuntime {
    private val frontendInterpreter: Interpreter
    private val darwinnDelegate: DarwinnDelegate
    private val backendInterpreter: Interpreter

    init {
        var frontend: Interpreter? = null
        var delegate: DarwinnDelegate? = null
        try {
            frontend = Interpreter(frontendModel, Interpreter.Options())
            delegate = DarwinnDelegate(context, enableTachyonBackend = false)
            val backendOptions = Interpreter.Options().addDelegate(delegate)
            frontendInterpreter = frontend
            darwinnDelegate = delegate
            backendInterpreter = Interpreter(backendModel, backendOptions)
        } catch (error: Throwable) {
            runCatching { frontend?.close() }
            runCatching { delegate?.close() }
            throw IllegalStateException("audio_tflite_split_create_failed", error)
        }
    }

    override fun run(
        audioInputTensor: Array<FloatArray>,
        spectrogramBuffer: ByteBuffer,
        paddingMaskTensor: Array<BooleanArray>,
        intentOutputBuffer: Array<FloatArray>
    ) {
        runFrontendStage(audioInputTensor, spectrogramBuffer)
        runBackendStage(spectrogramBuffer, paddingMaskTensor, intentOutputBuffer)
    }

    override fun close() {
        runCatching { frontendInterpreter.close() }
        runCatching { backendInterpreter.close() }
        runCatching { darwinnDelegate.close() }
    }

    private fun runFrontendStage(
        audioInputTensor: Array<FloatArray>,
        spectrogramBuffer: ByteBuffer
    ) {
        val inputs = arrayOfNulls<Any>(frontendInterpreter.inputTensorCount)
        inputs[AUDIO_INPUT_INDEX] = audioInputTensor
        val outputs = linkedMapOf<Int, Any>(MEL_FRONTEND_OUTPUT_INDEX to spectrogramBuffer)
        spectrogramBuffer.clear()
        frontendInterpreter.runForMultipleInputsOutputs(inputs, outputs)
    }

    private fun runBackendStage(
        spectrogramBuffer: ByteBuffer,
        paddingMaskTensor: Array<BooleanArray>,
        intentOutputBuffer: Array<FloatArray>
    ) {
        val inputs = arrayOfNulls<Any>(backendInterpreter.inputTensorCount)
        inputs[MEL_BACKEND_INPUT_INDEX] = spectrogramBuffer
        inputs[MASK_BACKEND_INPUT_INDEX] = paddingMaskTensor
        val outputs = linkedMapOf<Int, Any>(INTENT_OUTPUT_INDEX to intentOutputBuffer)
        spectrogramBuffer.clear()
        backendInterpreter.runForMultipleInputsOutputs(inputs, outputs)
    }

    private companion object {
        const val AUDIO_INPUT_INDEX = 0
        const val MEL_BACKEND_INPUT_INDEX = 0
        const val MASK_BACKEND_INPUT_INDEX = 1
        const val MEL_FRONTEND_OUTPUT_INDEX = 0
        const val INTENT_OUTPUT_INDEX = 0
    }
}

@Singleton
class AudioGatekeepingModel
@Inject
constructor(
    private val encoder: ConformerEncoderImpl
) : GatekeepingModel {
    override suspend fun predict(input: String): GatekeepingResult =
        throw UnsupportedOperationException("Audio gatekeeper does not support text prediction")

    override suspend fun predict(pcm: ByteArray): GatekeepingResult {
        val scores =
            runCatching { encoder.run(pcm) }
                .getOrElse { error ->
                    return unavailable(error.message ?: "audio_gatekeeper_unavailable")
                }
        val detected = mapScores(scores)
        return GatekeepingResult(
            detectedSkills = detected,
            modelId = "raw_audio",
            detectedBy = if (detected.isEmpty()) "none" else "audio_gatekeeper",
            triggeredFulfillment = detected.isNotEmpty(),
            input = "Ambient raw audio: latest buffered PCM window",
            inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
            detector = if (detected.isEmpty()) GatekeepingDetector.NONE else GatekeepingDetector.AUDIO_GATEKEEPER
        )
    }

    override fun close() {
        encoder.close()
    }

    private fun unavailable(reason: String): GatekeepingResult =
        GatekeepingResult(
            detectedSkills = emptyMap(),
            modelId = "raw_audio_unavailable:$reason",
            detectedBy = "none",
            triggeredFulfillment = false,
            input = "Ambient raw audio: latest buffered PCM window",
            inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
            detector = GatekeepingDetector.NONE
        )

    private fun mapScores(scores: FloatArray): Map<String, Float> =
        AUDIO_INDEX_TO_SKILLS.entries
            .filter { (index, _) -> index < scores.size && scores[index] >= AUDIO_THRESHOLDS[index] }
            .flatMap { (index, skills) -> skills.map { it to scores[index] } }
            .toMap()

    private companion object {
        val AUDIO_THRESHOLDS =
            floatArrayOf(
                0.57f, 0.65f, 0.90f, 0.90f, 0.90f, 0.90f, 0.56f, 0.74f, 0.90f, 0.90f, 0.90f, 0.90f, 0.90f
            )

        val AUDIO_INDEX_TO_SKILLS =
            mapOf(
                0 to listOf("add_to_memory"),
                1 to listOf("add_to_list"),
                2 to listOf("define_term", "web_search"),
                3 to listOf("query_photos"),
                4 to listOf("create_calendar_event"),
                5 to listOf("create_doc"),
                6 to listOf("provide_person_context"),
                7 to listOf("provide_event_context"),
                8 to listOf("deep_research"),
                9 to listOf("provide_insight"),
                10 to listOf("browsing_agent"),
                11 to listOf("call_agent"),
                12 to listOf("add_timer")
            )
    }
}
