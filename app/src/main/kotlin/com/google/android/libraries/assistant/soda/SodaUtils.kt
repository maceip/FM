package com.google.android.libraries.assistant.soda

import com.google.speech.soda.AudioProto.RawAudioFormat
import com.google.speech.soda.SodaEventProto.SodaEvent
import com.google.speech.soda.client.SodaClientConfigProto.SodaClientConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Replacement for the recovered com.google.android.libraries.assistant.soda.SodaUtils.
// Only the methods called by the rest of the recovered SODA Java surface are
// implemented; the original VoiceSearchConfiguration / language-pack-validation
// helpers (which pulled the GWS/MDI proto graph) are intentionally absent —
// callers of those live outside our extracted closure.
object SodaUtils {

    @JvmStatic
    fun numBytesPerFrame(format: RawAudioFormat): Int {
        // 20 ms worth of samples at the format's rate. SODA's recovered
        // C++ side uses this only to size the Java-owned direct buffer it
        // pulls audio from; oversize is fine, undersize starves the engine.
        val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 16_000
        val bytesPerSample = 2
        val channels = format.channelCount.takeIf { it > 0 } ?: 1
        return sampleRate * bytesPerSample * channels / 50
    }

    @JvmStatic
    fun stopReasonFromInt(value: Int): SodaStopReason {
        // Recovered enum declared as ERROR, HOTWORD_TIMEOUT, MIC_END_OF_DATA,
        // STOP_CALLED, UNKNOWN. Original Java declaration order is unknown;
        // values()[] gives the safe fallback.
        val all = SodaStopReason.values()
        return all.getOrElse(value) { SodaStopReason.UNKNOWN }
    }

    @JvmStatic
    fun createDefaultConfig(): SodaClientConfig.Builder = SodaClientConfig.newBuilder()

    @JvmStatic
    fun convertHotqueryToQuickPhraseEvent(builder: SodaEvent.Builder): SodaEvent =
        builder.build()

    @JvmStatic
    fun convertHotwordToQuickPhraseEvent(builder: SodaEvent.Builder): SodaEvent =
        builder.build()

    class DirectByteBufferMaker {
        fun createOrReuse(size: Int): ByteBuffer =
            ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

        fun createOrReuse(size: Int, order: ByteOrder): ByteBuffer =
            ByteBuffer.allocateDirect(size).order(order)
    }
}
