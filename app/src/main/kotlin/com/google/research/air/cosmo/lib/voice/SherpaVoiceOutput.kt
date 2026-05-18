package com.google.research.air.cosmo.lib.voice

import android.content.Context
import android.media.AudioFormat
import android.os.SystemClock
import android.util.Log
import com.google.research.air.cosmo.lib.tts.AudioRenderer
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class SherpaVoiceOutput
@Inject
constructor(
    @ApplicationContext private val context: Context
) : VoiceOutput {
    private val runtimeMutex = Mutex()

    @Volatile private var runtime: Runtime? = null
    @Volatile private var playing = false
    @Volatile private var stopped = false

    override fun isPlaying(): Boolean = playing

    override suspend fun speak(text: String): VoiceOutputResult =
        withContext(Dispatchers.IO) {
            val utterance = text.trim().take(MAX_UTTERANCE_CHARS)
            if (utterance.isBlank()) return@withContext VoiceOutputResult.Skipped("blank_text")

            val activeRuntime =
                runCatching { runtimeMutex.withLock { ensureRuntime() } }
                    .onFailure { Log.w(TAG, "Sherpa TTS init failed: ${it.message}", it) }
                    .getOrNull()
                    ?: return@withContext VoiceOutputResult.Unavailable("sherpa_tts_unavailable")

            stopped = false
            playing = true
            val startedAt = SystemClock.elapsedRealtime()
            val firstAudioAt = AtomicLong(0L)
            runCatching {
                activeRuntime.audioRenderer.reset()
                val generated =
                    activeRuntime.tts.generateWithConfigAndCallback(
                        text = utterance,
                        config =
                            GenerationConfig(
                                silenceScale = SILENCE_SCALE,
                                sid = DEFAULT_SPEAKER_ID,
                                speed = DEFAULT_SPEED
                            ),
                        callback =
                            PlaybackCallback(
                                owner = this@SherpaVoiceOutput,
                                audioRenderer = activeRuntime.audioRenderer,
                                firstAudioAt = firstAudioAt
                            )
                    )
                val completedAt = SystemClock.elapsedRealtime()
                val firstAudioMs = firstAudioAt.get().takeIf { it > 0L }?.let { it - startedAt }
                Log.i(
                    TAG,
                    "Sherpa Supertonic TTS speak completed: chars=${utterance.length} " +
                        "firstAudioMs=$firstAudioMs totalMs=${completedAt - startedAt} " +
                        "samples=${generated.samples.size}"
                )
                VoiceOutputResult.Spoken(
                    characters = utterance.length,
                    completed = !stopped && generated.samples.isNotEmpty()
                )
            }.getOrElse { error ->
                Log.w(TAG, "Sherpa TTS speak failed: ${error.message}", error)
                releaseRuntime()
                VoiceOutputResult.Unavailable("sherpa_tts_speak_failed_${error.javaClass.simpleName}")
            }.also {
                playing = false
            }
        }

    override fun stop() {
        stopped = true
        playing = false
        runtime?.audioRenderer?.reset()
    }

    private fun ensureRuntime(): Runtime {
        runtime?.let { return it }
        REQUIRED_ASSETS.forEach(::requireAsset)
        val tts =
            OfflineTts(
                assetManager = context.assets,
                config =
                    OfflineTtsConfig(
                        model =
                            OfflineTtsModelConfig(
                                supertonic =
                                    OfflineTtsSupertonicModelConfig(
                                        durationPredictor = DURATION_PREDICTOR_ASSET_PATH,
                                        textEncoder = TEXT_ENCODER_ASSET_PATH,
                                        vectorEstimator = VECTOR_ESTIMATOR_ASSET_PATH,
                                        vocoder = VOCODER_ASSET_PATH,
                                        ttsJson = TTS_JSON_ASSET_PATH,
                                        unicodeIndexer = UNICODE_INDEXER_ASSET_PATH,
                                        voiceStyle = VOICE_STYLE_ASSET_PATH
                                    ),
                                numThreads = MODEL_THREADS,
                                debug = false,
                                provider = "cpu"
                            ),
                        maxNumSentences = MAX_SENTENCES,
                        silenceScale = SILENCE_SCALE
                    )
            )
        val audioRenderer =
            AudioRenderer(
                sampleRate = tts.sampleRate(),
                channelMask = AudioFormat.CHANNEL_OUT_MONO,
                encoding = AudioFormat.ENCODING_PCM_FLOAT
            )
        return Runtime(tts = tts, audioRenderer = audioRenderer).also { runtime = it }
    }

    private fun requireAsset(path: String) {
        try {
            context.assets.open(path).use { }
        } catch (error: IOException) {
            throw MissingSherpaAssetException(path, error)
        }
    }

    private fun releaseRuntime() {
        runtime?.audioRenderer?.release()
        runtime?.tts?.release()
        runtime = null
        stopped = true
        playing = false
    }

    private data class Runtime(
        val tts: OfflineTts,
        val audioRenderer: AudioRenderer
    )

    private class PlaybackCallback(
        private val owner: SherpaVoiceOutput,
        private val audioRenderer: AudioRenderer,
        private val firstAudioAt: AtomicLong
    ) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int {
            if (owner.stopped) return STOP_GENERATION
            if (samples.isNotEmpty()) {
                firstAudioAt.compareAndSet(0L, SystemClock.elapsedRealtime())
            }
            audioRenderer.render(samples)
            return CONTINUE_GENERATION
        }
    }

    private class MissingSherpaAssetException(
        path: String,
        cause: Throwable
    ) : IOException("Missing Sherpa TTS asset: $path", cause)

    private companion object {
        const val TAG = "SherpaVoiceOutput"
        const val MODEL_DIR = "models/tts/supertonic-3"
        const val DURATION_PREDICTOR_ASSET_PATH = "$MODEL_DIR/onnx/duration_predictor.onnx"
        const val TEXT_ENCODER_ASSET_PATH = "$MODEL_DIR/onnx/text_encoder.onnx"
        const val VECTOR_ESTIMATOR_ASSET_PATH = "$MODEL_DIR/onnx/vector_estimator.onnx"
        const val VOCODER_ASSET_PATH = "$MODEL_DIR/onnx/vocoder.onnx"
        const val TTS_JSON_ASSET_PATH = "$MODEL_DIR/onnx/tts.json"
        const val UNICODE_INDEXER_ASSET_PATH = "$MODEL_DIR/onnx/unicode_indexer.bin"
        const val VOICE_STYLE_ASSET_PATH = "$MODEL_DIR/voice_styles.bin"
        val REQUIRED_ASSETS =
            listOf(
                DURATION_PREDICTOR_ASSET_PATH,
                TEXT_ENCODER_ASSET_PATH,
                VECTOR_ESTIMATOR_ASSET_PATH,
                VOCODER_ASSET_PATH,
                TTS_JSON_ASSET_PATH,
                UNICODE_INDEXER_ASSET_PATH,
                VOICE_STYLE_ASSET_PATH
            )
        const val MAX_UTTERANCE_CHARS = 500
        const val DEFAULT_SPEAKER_ID = 0
        const val DEFAULT_SPEED = 1.05f
        const val SILENCE_SCALE = 0.3f
        const val MODEL_THREADS = 4
        const val MAX_SENTENCES = 1
        const val CONTINUE_GENERATION = 1
        const val STOP_GENERATION = 0
    }
}
