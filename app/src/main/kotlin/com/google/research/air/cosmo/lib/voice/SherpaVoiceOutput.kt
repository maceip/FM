package com.google.research.air.cosmo.lib.voice

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
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
                activeRuntime.audioTrack.pause()
                activeRuntime.audioTrack.flush()
                activeRuntime.audioTrack.play()
                val generated =
                    activeRuntime.tts.generateWithConfigAndCallback(
                        text = utterance,
                        config =
                            GenerationConfig(
                                sid = DEFAULT_SPEAKER_ID,
                                speed = DEFAULT_SPEED
                            ),
                        callback =
                            PlaybackCallback(
                                owner = this@SherpaVoiceOutput,
                                audioTrack = activeRuntime.audioTrack,
                                firstAudioAt = firstAudioAt
                            )
                    )
                val completedAt = SystemClock.elapsedRealtime()
                val firstAudioMs = firstAudioAt.get().takeIf { it > 0L }?.let { it - startedAt }
                Log.i(
                    TAG,
                    "Sherpa TTS speak completed: chars=${utterance.length} " +
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
        runtime?.audioTrack?.pause()
        runtime?.audioTrack?.flush()
    }

    private fun ensureRuntime(): Runtime {
        runtime?.let { return it }
        requireAsset(MODEL_ASSET_PATH)
        requireAsset(VOICES_ASSET_PATH)
        requireAsset(TOKENS_ASSET_PATH)
        val dataDir = copyAssetDirectoryToFiles(ESPEAK_ASSET_DIR)
        val tts =
            OfflineTts(
                assetManager = context.assets,
                config =
                    OfflineTtsConfig(
                        model =
                            OfflineTtsModelConfig(
                                kitten =
                                    OfflineTtsKittenModelConfig(
                                        model = MODEL_ASSET_PATH,
                                        voices = VOICES_ASSET_PATH,
                                        tokens = TOKENS_ASSET_PATH,
                                        dataDir = dataDir.absolutePath,
                                        lengthScale = LENGTH_SCALE
                                    ),
                                numThreads = MODEL_THREADS,
                                debug = false,
                                provider = "cpu"
                            ),
                        maxNumSentences = MAX_SENTENCES,
                        silenceScale = SILENCE_SCALE
                    )
            )
        val audioTrack = createAudioTrack(tts.sampleRate())
        return Runtime(tts = tts, audioTrack = audioTrack).also { runtime = it }
    }

    private fun requireAsset(path: String) {
        try {
            context.assets.open(path).use { }
        } catch (error: IOException) {
            throw MissingSherpaAssetException(path, error)
        }
    }

    private fun copyAssetDirectoryToFiles(assetDir: String): File {
        val targetDir = File(context.filesDir, "voice/sherpa/$assetDir")
        if (targetDir.isDirectory && File(targetDir, COPY_COMPLETE_MARKER).isFile) {
            return targetDir
        }
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        copyAssetTree(context.assets, assetDir, targetDir)
        File(targetDir, COPY_COMPLETE_MARKER).writeText("ok")
        return targetDir
    }

    private fun copyAssetTree(
        assets: AssetManager,
        assetPath: String,
        targetPath: File
    ) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            targetPath.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(targetPath).use { output -> input.copyTo(output) }
            }
            return
        }

        targetPath.mkdirs()
        children.forEach { child ->
            copyAssetTree(
                assets = assets,
                assetPath = "$assetPath/$child",
                targetPath = File(targetPath, child)
            )
        }
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        val minBufferSize =
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 2)
        val attributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .build()
        val format =
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setSampleRate(sampleRate)
                .build()
        return AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun releaseRuntime() {
        runtime?.audioTrack?.release()
        runtime?.tts?.release()
        runtime = null
        stopped = true
        playing = false
    }

    private data class Runtime(
        val tts: OfflineTts,
        val audioTrack: AudioTrack
    )

    private class PlaybackCallback(
        private val owner: SherpaVoiceOutput,
        private val audioTrack: AudioTrack,
        private val firstAudioAt: AtomicLong
    ) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int {
            if (owner.stopped) return STOP_GENERATION
            if (samples.isNotEmpty()) {
                firstAudioAt.compareAndSet(0L, SystemClock.elapsedRealtime())
            }
            audioTrack.write(
                samples,
                0,
                samples.size,
                AudioTrack.WRITE_BLOCKING
            )
            return CONTINUE_GENERATION
        }
    }

    private class MissingSherpaAssetException(
        path: String,
        cause: Throwable
    ) : IOException("Missing Sherpa TTS asset: $path", cause)

    private companion object {
        const val TAG = "SherpaVoiceOutput"
        const val MODEL_DIR = "models/tts/kitten-mini-en-v0_1-fp16"
        const val MODEL_ASSET_PATH = "$MODEL_DIR/model.fp16.onnx"
        const val VOICES_ASSET_PATH = "$MODEL_DIR/voices.bin"
        const val TOKENS_ASSET_PATH = "$MODEL_DIR/tokens.txt"
        const val ESPEAK_ASSET_DIR = "$MODEL_DIR/espeak-ng-data"
        const val COPY_COMPLETE_MARKER = ".complete"
        const val MAX_UTTERANCE_CHARS = 500
        const val DEFAULT_SPEAKER_ID = 0
        const val DEFAULT_SPEED = 1.0f
        const val LENGTH_SCALE = 1.0f
        const val SILENCE_SCALE = 0.2f
        const val MODEL_THREADS = 4
        const val MAX_SENTENCES = 1
        const val CONTINUE_GENERATION = 1
        const val STOP_GENERATION = 0
    }
}
