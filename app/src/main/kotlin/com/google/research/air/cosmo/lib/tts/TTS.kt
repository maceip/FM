package com.google.research.air.cosmo.lib.tts

import android.util.Log
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.lib.voice.AndroidTextToSpeechVoiceOutput
import com.google.research.air.cosmo.lib.voice.VoiceOutput
import com.google.research.air.cosmo.lib.voice.VoiceOutputResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface TTS {
    fun isPlaying(): Boolean

    fun speak(text: String)

    fun stop()
}

/**
 * Launch-compatible replacement for Cosmo's recovered VoiceLMTTS slot.
 *
 * The recovered implementation submitted text into Vogo/VoiceLM and streamed
 * PCM through AudioRenderer. This class preserves the same synchronous TTS API
 * while delegating generation to the selected VoiceOutput backend.
 */
@Singleton
class VoiceLMTTS
@Inject
constructor(
    private val voiceOutput: VoiceOutput,
    @ApplicationScope private val scope: CoroutineScope
) : TTS {
    @Volatile private var activeJob: Job? = null

    override fun isPlaying(): Boolean = activeJob?.isActive == true || voiceOutput.isPlaying()

    override fun speak(text: String) {
        activeJob?.cancel()
        activeJob =
            scope.launch {
                when (val result = voiceOutput.speak(text)) {
                    is VoiceOutputResult.Unavailable ->
                        Log.w(TAG, "VoiceLM replacement unavailable: ${result.reason}")

                    else -> Unit
                }
            }
    }

    override fun stop() {
        activeJob?.cancel()
        activeJob = null
        voiceOutput.stop()
    }

    private companion object {
        const val TAG = "VoiceLMTTS"
    }
}

@Singleton
class LocalTTS
@Inject
constructor(
    private val androidVoiceOutput: AndroidTextToSpeechVoiceOutput,
    @ApplicationScope private val scope: CoroutineScope
) : TTS {
    @Volatile private var activeJob: Job? = null

    override fun isPlaying(): Boolean = activeJob?.isActive == true || androidVoiceOutput.isPlaying()

    override fun speak(text: String) {
        activeJob?.cancel()
        activeJob =
            scope.launch {
                when (val result = androidVoiceOutput.speak(text)) {
                    is VoiceOutputResult.Unavailable ->
                        Log.w(TAG, "Local TTS unavailable: ${result.reason}")

                    else -> Unit
                }
            }
    }

    override fun stop() {
        activeJob?.cancel()
        activeJob = null
        androidVoiceOutput.stop()
    }

    private companion object {
        const val TAG = "LocalTTS"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TTSModule {
    @Binds @Singleton abstract fun bindTTS(impl: VoiceLMTTS): TTS
}
