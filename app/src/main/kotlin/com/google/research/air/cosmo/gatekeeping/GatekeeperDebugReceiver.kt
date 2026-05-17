package com.google.research.air.cosmo.gatekeeping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.research.air.cosmo.BuildConfig
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import com.google.research.air.cosmo.gatekeeping.backend.AudioInput
import com.google.research.air.cosmo.gatekeeping.backend.CpuRecoveryBackend
import com.google.research.air.cosmo.lib.soda.JniSodaCapability
import com.google.research.air.cosmo.lib.soda.SodaPrepareResult
import com.google.research.air.cosmo.lib.soda.SodaSession
import com.google.research.air.cosmo.lib.soda.SodaStartResult
import com.google.research.air.cosmo.lib.soda.SodaTranscriptCallback
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-build only entry point that injects PCM audio into the gatekeeper / SODA pipelines for
 * adb-driven smoke testing. Two paths:
 *
 * Text → regex + BERT only (no JNI exercise):
 * ```
 * adb shell am broadcast -a com.google.research.air.cosmo.DEBUG_GATEKEEPER \
 *   --es text "Screen: define polymorphism" -p com.google.research.air.cosmo
 * ```
 *
 * Audio → exercises libtensorflowlite_jni / libdarwinndelegatejni / libsoda_dev_jni / Conformer:
 * ```
 * adb push fixture.wav /sdcard/cosmo-test/fixture.wav
 * adb shell am broadcast -a com.google.research.air.cosmo.DEBUG_INJECT_AUDIO \
 *   --es path /sdcard/cosmo-test/fixture.wav --es target both \
 *   -p com.google.research.air.cosmo
 * ```
 *
 * `target` ∈ {`ambient`, `soda`, `both`}: `ambient` feeds AmbientAudioBytesEphemeralBuffer and
 * triggers AudioGatekeepingModel (TFLite frontend + EdgeTPU backend); `soda` feeds
 * JniSodaCapability via addAudio (libsoda_dev_jni). Tail logcat with
 * `adb logcat -s COSMO-Gatekeeper Telemetry JniSodaCapability ConformerEncoder GatekeeperDebug`.
 */
@Singleton
class GatekeeperDebugReceiver
@Inject
constructor(
    private val gatekeeper: Gatekeeper,
    private val audioGatekeepingModel: AudioGatekeepingModel,
    private val cpuRecoveryBackend: CpuRecoveryBackend,
    @AmbientAudioBytesEphemeralBuffer private val ambientBuffer: InMemoryEphemeralBuffer<ByteArray>,
    @ApplicationContext private val appContext: Context,
    private val sodaCapability: JniSodaCapability,
    private val clock: Clock,
    private val settings: CosmoSettingStore
) : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun registerIfDebug(context: Context) {
        if (!BuildConfig.DEBUG) return
        val filter = IntentFilter().apply {
            addAction(ACTION_TEXT)
            addAction(ACTION_AUDIO)
            addAction(ACTION_SET_SETTING)
        }
        // RECEIVER_EXPORTED is required for `adb shell am broadcast` to reach this receiver:
        // adb runs from uid 2000 (shell), which is outside the app's uid. NOT_EXPORTED would
        // silently drop the intent. Gated by BuildConfig.DEBUG so this surface never ships.
        context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TEXT -> handleText(intent)
            ACTION_AUDIO -> handleAudio(intent)
            ACTION_SET_SETTING -> handleSetSetting(intent)
        }
    }

    private fun handleSetSetting(intent: Intent) {
        val key = intent.getStringExtra(EXTRA_SETTING_KEY)?.takeIf { it.isNotBlank() } ?: return
        val value = intent.getBooleanExtra(EXTRA_SETTING_VALUE, false)
        scope.launch {
            when (key) {
                "rawAudioGatekeeperEnabled" -> settings.setRawAudioGatekeeperEnabled(value)
                "gatekeeperEnabled" -> settings.setGatekeeperEnabled(value)
                else -> {
                    Log.w(TAG, "set setting: unknown key=$key")
                    return@launch
                }
            }
            Log.i(TAG, "set setting: $key=$value")
        }
    }

    private fun handleText(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        Log.i(TAG, "text inject: $text")
        gatekeeper.processDebugInput(text)
    }

    private fun handleAudio(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH)?.takeIf { it.isNotBlank() } ?: return
        val target = intent.getStringExtra(EXTRA_TARGET)?.lowercase() ?: TARGET_BOTH
        scope.launch { injectAudioFile(path, target) }
    }

    private suspend fun injectAudioFile(
        path: String,
        target: String
    ) {
        val file = File(path)
        val readable = withContext(Dispatchers.IO) { file.isFile && file.canRead() }
        if (!readable) {
            Log.w(TAG, "audio inject: file unreadable path=$path")
            return
        }
        val pcm = withContext(Dispatchers.IO) { readWavAsPcm16Mono16k(file) }
        if (pcm == null) {
            val size = withContext(Dispatchers.IO) { file.length() }
            Log.w(TAG, "audio inject: not a 16k mono PCM16 WAV path=$path size=$size")
            return
        }
        Log.i(TAG, "audio inject: target=$target bytes=${pcm.size} (~${pcm.size / 32}ms @ 16k mono PCM16)")
        val chunks = pcm.toList().chunked(FRAME_BYTES).map { it.toByteArray() }
        if (target == TARGET_AMBIENT || target == TARGET_BOTH) feedAmbient(chunks)
        if (target == TARGET_SODA || target == TARGET_BOTH) feedSoda(chunks)
    }

    private suspend fun feedAmbient(chunks: List<ByteArray>) {
        chunks.forEach { ambientBuffer.add(it) }
        Log.i(TAG, "audio inject ambient: pushed ${chunks.size} chunks; running AudioGatekeepingModel")
        val result = audioGatekeepingModel.predict(chunks.flattenBytes())
        Log.i(TAG, "audio inject ambient: result=$result")
    }

    private suspend fun feedSoda(chunks: List<ByteArray>) {
        if (!sodaCapability.isAvailable) {
            Log.w(TAG, "audio inject soda: capability unavailable (libsoda_dev_jni missing or load failed)")
            return
        }
        val prepared = sodaCapability.prepare()
        if (prepared !is SodaPrepareResult.Available) {
            Log.w(TAG, "audio inject soda: prepare failed: $prepared")
            return
        }
        // Dedicated debug session so we don't interfere with production per-source controllers.
        val session = SodaSession(appContext, prepared.packDir, clock)
        val started = session.start(SodaTranscriptCallback { text, isFinal ->
            Log.i(TAG, "audio inject soda transcript: isFinal=$isFinal text=$text")
            if (isFinal && text.isNotBlank()) classifyInjectedTranscript(text)
        })
        if (started !is SodaStartResult.Started) {
            Log.w(TAG, "audio inject soda: start failed: $started")
            return
        }
        val trailingSilenceChunks = List(DEBUG_SODA_TRAILING_SILENCE_CHUNKS) { ByteArray(FRAME_BYTES) }
        (chunks + trailingSilenceChunks).forEach { chunk ->
            val direct = ByteBuffer.allocateDirect(chunk.size).order(ByteOrder.nativeOrder())
            direct.put(chunk)
            direct.position(0)
            session.addAudio(direct, chunk.size)
            delay(DEBUG_SODA_CHUNK_DELAY_MS)
        }
        Log.i(
            TAG,
            "audio inject soda: pushed ${chunks.size} audio chunks + ${trailingSilenceChunks.size} silence chunks; awaiting transcripts on callback"
        )
        scope.launch {
            delay(DEBUG_SODA_SESSION_STOP_DELAY_MS)
            session.stop()
            Log.i(TAG, "audio inject soda: stopped debug session")
        }
    }

    private fun classifyInjectedTranscript(text: String) {
        scope.launch {
            val result =
                cpuRecoveryBackend.classify(
                    AudioInput.TranscribedSegment(
                        source = ContextSource.AMBIENT_AUDIO,
                        transcript = "Ambient audio: $text",
                        directednessTrigger = true
                    )
                )
            Log.i(TAG, "audio inject soda gatekeeper: result=$result")
        }
    }

    /**
     * Minimal WAV reader for the canonical fixture format (mono, 16 kHz, 16-bit PCM, little-endian).
     * Returns the raw PCM body or null if the header doesn't match.
     */
    internal fun readWavAsPcm16Mono16k(file: File): ByteArray? {
        val bytes = file.readBytes()
        if (bytes.size < 44 ||
            String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) {
            return null
        }
        val header = parseWavHeader(bytes) ?: return null
        if (header.channels != 1 || header.sampleRate != 16_000 || header.bitsPerSample != 16) {
            Log.w(TAG, "audio inject: wav has channels=${header.channels} sampleRate=${header.sampleRate} bits=${header.bitsPerSample}, want 1/16000/16")
            return null
        }
        return bytes.copyOfRange(header.dataStart, header.dataEnd)
    }

    private fun parseWavHeader(bytes: ByteArray): WavHeader? {
        var offset = 12
        var fmtFound = false
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readLeInt(bytes, offset + 4)
            val payloadStart = offset + 8
            if (chunkId == "fmt " && chunkSize >= 16) {
                channels = readLeShort(bytes, payloadStart + 2).toInt() and 0xffff
                sampleRate = readLeInt(bytes, payloadStart + 4)
                bitsPerSample = readLeShort(bytes, payloadStart + 14).toInt() and 0xffff
                fmtFound = true
            } else if (chunkId == "data" && fmtFound) {
                val dataEnd = (payloadStart + chunkSize).coerceAtMost(bytes.size)
                return WavHeader(channels, sampleRate, bitsPerSample, payloadStart, dataEnd)
            }
            offset = payloadStart + chunkSize + (chunkSize and 1)
        }
        return null
    }

    private data class WavHeader(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataStart: Int,
        val dataEnd: Int
    )

    private fun readLeInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun readLeShort(bytes: ByteArray, offset: Int): Short =
        ((bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)).toShort()

    private fun List<ByteArray>.flattenBytes(): ByteArray {
        val total = sumOf { it.size }
        val merged = ByteArray(total)
        var offset = 0
        for (chunk in this) {
            chunk.copyInto(merged, offset)
            offset += chunk.size
        }
        return merged
    }

    companion object {
        const val ACTION_TEXT = "com.google.research.air.cosmo.DEBUG_GATEKEEPER"
        const val ACTION_AUDIO = "com.google.research.air.cosmo.DEBUG_INJECT_AUDIO"
        const val ACTION_SET_SETTING = "com.google.research.air.cosmo.DEBUG_SET_SETTING"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PATH = "path"
        const val EXTRA_TARGET = "target"
        const val EXTRA_SETTING_KEY = "key"
        const val EXTRA_SETTING_VALUE = "value"
        const val TARGET_AMBIENT = "ambient"
        const val TARGET_SODA = "soda"
        const val TARGET_BOTH = "both"
        const val FRAME_BYTES = 1280 // 40 ms at 16 kHz mono PCM16
        const val DEBUG_SODA_CHUNK_DELAY_MS = 40L
        const val DEBUG_SODA_TRAILING_SILENCE_CHUNKS = 50 // 2 seconds at 40 ms/chunk.
        const val DEBUG_SODA_SESSION_STOP_DELAY_MS = 10_000L
        const val TAG = "GatekeeperDebug"
    }
}
