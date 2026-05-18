package com.google.research.air.cosmo.lib.voice

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

interface VoiceOutput {
    suspend fun speak(text: String): VoiceOutputResult

    fun stop()
}

sealed interface VoiceOutputResult {
    data class Spoken(
        val characters: Int,
        val completed: Boolean
    ) : VoiceOutputResult

    data class Skipped(
        val reason: String
    ) : VoiceOutputResult

    data class Unavailable(
        val reason: String
    ) : VoiceOutputResult
}

@Singleton
class AndroidTextToSpeechVoiceOutput
@Inject
constructor(
    @ApplicationContext private val context: Context
) : VoiceOutput {
    private var tts: TextToSpeech? = null
    private var initResult: CompletableDeferred<Int>? = null
    private var configured = false

    override suspend fun speak(text: String): VoiceOutputResult =
        withContext(Dispatchers.Main.immediate) {
            val utterance = text.trim().take(MAX_UTTERANCE_CHARS)
            if (utterance.isBlank()) return@withContext VoiceOutputResult.Skipped("blank_text")

            val ready =
                withTimeoutOrNull(INIT_TIMEOUT_MS) {
                    ensureEngine().await()
                }
                    ?: return@withContext VoiceOutputResult.Unavailable("tts_init_timeout")
            if (ready != TextToSpeech.SUCCESS) {
                return@withContext VoiceOutputResult.Unavailable("tts_init_status_$ready")
            }

            val utteranceId = "cosmo-tts-${SystemClock.uptimeMillis()}"
            val spoken = CompletableDeferred<VoiceOutputResult>()
            val engine = checkNotNull(tts)
            configureEngine(engine)
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String) {
                        Log.i(TAG, "speak started id=$id chars=${utterance.length}")
                    }

                    override fun onDone(id: String) {
                        spoken.complete(
                            VoiceOutputResult.Spoken(
                                characters = utterance.length,
                                completed = true
                            )
                        )
                        Log.i(TAG, "speak completed id=$id chars=${utterance.length}")
                    }

                    @Deprecated("Deprecated in Android framework")
                    override fun onError(id: String) {
                        spoken.complete(VoiceOutputResult.Unavailable("tts_utterance_error"))
                        Log.w(TAG, "speak failed id=$id")
                    }

                    override fun onError(
                        id: String,
                        errorCode: Int
                    ) {
                        spoken.complete(VoiceOutputResult.Unavailable("tts_utterance_error_$errorCode"))
                        Log.w(TAG, "speak failed id=$id errorCode=$errorCode")
                    }
                }
            )

            val speakResult =
                engine.speak(
                    utterance,
                    TextToSpeech.QUEUE_FLUSH,
                    Bundle(),
                    utteranceId
                )
            if (speakResult == TextToSpeech.ERROR) {
                return@withContext VoiceOutputResult.Unavailable("tts_speak_rejected")
            }
            withTimeoutOrNull(SPEAK_TIMEOUT_MS) { spoken.await() }
                ?: VoiceOutputResult.Spoken(
                    characters = utterance.length,
                    completed = false
                )
        }

    override fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        initResult = null
        configured = false
    }

    private fun ensureEngine(): CompletableDeferred<Int> {
        initResult?.let { return it }
        val deferred = CompletableDeferred<Int>()
        initResult = deferred
        tts =
            TextToSpeech(context.applicationContext) { status ->
                deferred.complete(status)
                Log.i(TAG, "init status=$status")
            }
        return deferred
    }

    private fun configureEngine(engine: TextToSpeech) {
        if (configured) return
        configured = true
        runCatching {
            val languageResult = engine.setLanguage(Locale.US)
            val selected = selectBestVoice(engine.voices.orEmpty())
            if (selected != null) {
                val voiceResult = engine.setVoice(selected)
                Log.i(
                    TAG,
                    "configured voice=${selected.name} locale=${selected.locale} quality=${selected.quality} latency=${selected.latency} network=${selected.isNetworkConnectionRequired} voiceResult=$voiceResult"
                )
            } else {
                Log.w(TAG, "no suitable English TTS voice found; languageResult=$languageResult")
            }
            engine.setSpeechRate(SPEECH_RATE)
            engine.setPitch(PITCH)
        }.onFailure {
            Log.w(TAG, "unable to configure TTS engine: ${it.message}")
        }
    }

    private fun selectBestVoice(voices: Set<Voice>): Voice? =
        voices
            .filter { voice ->
                voice.locale?.language.equals(Locale.ENGLISH.language, ignoreCase = true) &&
                    !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
                compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenByDescending { it.locale?.country.equals(Locale.US.country, ignoreCase = true) }
                    .thenBy { it.name }
            )
            .firstOrNull()

    private companion object {
        const val TAG = "VoiceOutput"
        const val INIT_TIMEOUT_MS = 8_000L
        const val SPEAK_TIMEOUT_MS = 15_000L
        const val MAX_UTTERANCE_CHARS = 500
        const val SPEECH_RATE = 0.96f
        const val PITCH = 0.98f
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceOutputModule {
    @Binds
    @Singleton
    abstract fun bindVoiceOutput(impl: AndroidTextToSpeechVoiceOutput): VoiceOutput
}
