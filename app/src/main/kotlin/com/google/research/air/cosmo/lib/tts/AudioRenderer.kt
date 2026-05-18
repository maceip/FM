package com.google.research.air.cosmo.lib.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioRenderer(
    sampleRate: Int,
    private val channelMask: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val encoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private val audioTrack: AudioTrack = createAudioTrack(sampleRate)

    fun isPlaying(): Boolean = audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING

    fun render(samples: FloatArray) {
        if (samples.isEmpty()) return
        render(samples.toPcm16())
    }

    fun render(samples: ShortArray) {
        if (samples.isEmpty()) return
        startIfNeeded()
        audioTrack.write(samples, 0, samples.size)
    }

    fun reset() {
        audioTrack.pause()
        audioTrack.flush()
    }

    fun release() {
        audioTrack.release()
    }

    private fun startIfNeeded() {
        if (!isPlaying()) {
            audioTrack.play()
        }
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        val minBufferSize =
            AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                encoding
            )
        val fallbackBufferSize = sampleRate * bytesPerSample(encoding) / MIN_BUFFER_FRACTION
        val bufferSize = minBufferSize.takeIf { it > 0 }?.coerceAtLeast(fallbackBufferSize) ?: fallbackBufferSize
        val attributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .build()
        val format =
            AudioFormat.Builder()
                .setEncoding(encoding)
                .setChannelMask(channelMask)
                .setSampleRate(sampleRate)
                .build()
        return AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also { Log.i(TAG, "audio track is initialized") }
    }

    private companion object {
        const val TAG = "AudioRenderer"
        const val MIN_BUFFER_FRACTION = 2
    }
}

private fun FloatArray.toPcm16(): ShortArray =
    ShortArray(size) { index ->
        (this[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }

private fun bytesPerSample(encoding: Int): Int =
    when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }
