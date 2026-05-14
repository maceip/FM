package com.google.research.air.cosmo.gatekeeping

import android.content.Context
import com.google.research.air.cosmo.gatekeeping.models.AudioGatekeeperLiteRtImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConformerEncoderImpl internal constructor(
    // Test seam: see [BertClassifierGatekeepingModel] for the rationale. Production binds to
    // `GatekeeperModelIds.byteLoaderFor(context)`; tests pass `{ id -> fakeAssets[id] }`.
    private val loadAssetBytes: (String) -> ByteArray?,
    private val nativeCreate: (ByteArray, Boolean) -> Long,
    private val nativeRun: (Long, FloatBuffer, FloatBuffer) -> Boolean,
    private val nativeDestroy: (Long) -> Unit
) : AutoCloseable {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        loadAssetBytes = GatekeeperModelIds.byteLoaderFor(context),
        nativeCreate = { modelBuffer, useNpu -> AudioGatekeeperLiteRtImpl.nativeCreate(modelBuffer, useNpu) },
        nativeRun = { handle, input, output -> AudioGatekeeperLiteRtImpl.nativeRun(handle, input, output) },
        nativeDestroy = { handle -> AudioGatekeeperLiteRtImpl.nativeDestroy(handle) }
    )

    private var frontendHandle: Long = 0L
    private var backendHandle: Long = 0L

    fun run(pcm: ByteArray): FloatArray {
        val frontendInput = pcm16ToFloatBuffer(pcm)
        val melBuffer =
            ByteBuffer
                .allocateDirect(MAX_FRAMES * MEL_BINS * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        if (!nativeRun(ensureFrontend(), frontendInput, melBuffer)) {
            failUnavailable("audio_frontend_run_failed")
        }

        val intentBuffer =
            ByteBuffer
                .allocateDirect(NUM_INTENTS * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        melBuffer.position(0)
        if (!nativeRun(ensureBackend(), melBuffer, intentBuffer)) {
            failUnavailable("audio_backend_run_failed")
        }
        intentBuffer.position(0)
        val scores = FloatArray(NUM_INTENTS) { intentBuffer.get() }
        if (scores.any { !it.isFinite() }) {
            failUnavailable("audio_backend_invalid_output")
        }
        return scores
    }

    override fun close() {
        val currentFrontend = frontendHandle
        val currentBackend = backendHandle
        frontendHandle = 0L
        backendHandle = 0L
        if (currentFrontend != 0L) runCatching { nativeDestroy(currentFrontend) }
        if (currentBackend != 0L) runCatching { nativeDestroy(currentBackend) }
    }

    private fun ensureFrontend(): Long {
        if (frontendHandle != 0L) return frontendHandle
        val bytes = loadAssetBytes(GatekeeperModelIds.AUDIO_FRONTEND)
            ?: failUnavailable("audio_frontend_unavailable")
        val created = nativeCreate(bytes, false)
        if (created == 0L) failUnavailable("audio_litert_cpu_create_failed")
        frontendHandle = created
        return created
    }

    private fun ensureBackend(): Long {
        if (backendHandle != 0L) return backendHandle
        val bytes = loadAssetBytes(GatekeeperModelIds.AUDIO_GATEKEEPER)
            ?: failUnavailable("audio_backend_unavailable")
        val created = nativeCreate(bytes, true)
        if (created == 0L) failUnavailable("audio_litert_npu_create_failed")
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

    private fun failUnavailable(reason: String): Nothing = throw IllegalStateException(reason)

    companion object {
        const val SAMPLE_RATE: Int = 16_000
        const val MEL_BINS: Int = 128
        const val MAX_FRAMES: Int = 3_000
        const val MAX_SAMPLES: Int = 960_000
        const val NUM_INTENTS: Int = 13
        private const val PCM_16BIT_SCALE: Float = 32_768.0f
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
