package com.google.research.air.cosmo.gatekeeping

import android.util.Log
import com.google.research.air.cosmo.contentcapture.ScreenObservation
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import com.google.research.air.cosmo.gatekeeping.backend.AudioGatekeepingBackendSelector
import com.google.research.air.cosmo.gatekeeping.backend.AudioInput
import com.google.research.air.cosmo.gatekeeping.backend.CpuRecoveryBackend
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.BudsAudioTextEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.DeviceAudioTextEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.LocationTextEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.NotificationEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.ScreenEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.WatchAudioTextEphemeralBuffer
import com.google.research.air.cosmo.lib.soda.DefaultMicSodaController
import com.google.research.air.cosmo.lib.soda.DirectednessEvent
import com.google.research.air.cosmo.lib.soda.SodaTranscriptionController
import com.google.research.air.cosmo.lib.soda.TranscriptionData
import com.google.research.air.cosmo.locationcapture.LocationObservation
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.notificationcapture.NotificationEvent
import com.google.research.air.cosmo.setting.CosmoSettingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

interface GatekeeperOutputSource {
    val results: SharedFlow<GatekeepingResult>
}

@Singleton
class Gatekeeper
@Inject
constructor(
    @AmbientAudioEphemeralBuffer private val ambientAudioReader: InMemoryEphemeralBuffer<Any>,
    @AmbientAudioBytesEphemeralBuffer private val ambientAudioBytesReader: InMemoryEphemeralBuffer<ByteArray>,
    @BudsAudioTextEphemeralBuffer private val budsAudioReader: InMemoryEphemeralBuffer<Any>,
    @DeviceAudioTextEphemeralBuffer private val deviceAudioReader: InMemoryEphemeralBuffer<Any>,
    @WatchAudioTextEphemeralBuffer private val watchAudioReader: InMemoryEphemeralBuffer<Any>,
    @ScreenEphemeralBuffer private val screenReader: InMemoryEphemeralBuffer<Any>,
    @NotificationEphemeralBuffer private val notificationReader: InMemoryEphemeralBuffer<Any>,
    @LocationTextEphemeralBuffer private val locationReader: InMemoryEphemeralBuffer<Any>,
    @DefaultMicSodaController private val ambientSodaController: SodaTranscriptionController,
    private val backendSelector: AudioGatekeepingBackendSelector,
    private val cpuRecoveryBackend: CpuRecoveryBackend,
    private val settings: CosmoSettingStore,
    @ApplicationScope private val scope: CoroutineScope,
    private val logger: GatekeeperLogger
) : AutoCloseable, GatekeeperOutputSource {
    private val mutableResults = MutableSharedFlow<GatekeepingResult>(extraBufferCapacity = 64)
    private val jobs = mutableListOf<Job>()
    private var started = false

    override val results: SharedFlow<GatekeepingResult> = mutableResults

    fun start() {
        if (started) return
        started = true
        jobs += scope.launch { ambientAudioReader.flow().collect { onAmbientAudio(it.value) } }
        jobs += scope.launch { ambientAudioBytesReader.flow().collect { onAmbientAudioBytes() } }
        jobs += scope.launch { budsAudioReader.flow().collect { onText("Buds audio", ContextSource.BUDS_AUDIO, it.value) } }
        jobs += scope.launch { deviceAudioReader.flow().collect { onText("Device audio", ContextSource.DEVICE_AUDIO, it.value) } }
        jobs += scope.launch { watchAudioReader.flow().collect { onText("Watch audio", ContextSource.WATCH_AUDIO, it.value) } }
        jobs += scope.launch { screenReader.flow().collect { onScreen(it.value) } }
        jobs += scope.launch { notificationReader.flow().collect { onNotification(it.value) } }
        jobs += scope.launch { locationReader.flow().collect { onText("Location", ContextSource.LOCATION, it.value) } }
        jobs += scope.launch { ambientSodaController.directedness.collect { onDirectedness(it) } }
    }

    fun processDebugInput(input: String) {
        if (!settings.isGatekeeperEnabled() || input.isBlank()) return
        scope.launch { runTextClassifier(input, ContextSource.USER_QUERY) }
    }

    override fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        started = false
    }

    private suspend fun onAmbientAudio(value: Any) {
        val data = value as? TranscriptionData ?: return
        if (!data.hasContent()) return
        if (!settings.isGatekeeperEnabled()) return
        // C1: mutually-exclusive dispatch. Cosmo Gatekeeper$2$1.smali:135-277 enforces that
        // exactly one classifier runs per ambient transcript. We route through the selector;
        // when EdgeTPU is the active backend it returns null for TranscribedSegment (the audio
        // bytes path owns it). When CpuRecovery is active, raw-audio mode gates on the Magic
        // Mic directedness trigger before BERT runs.
        val rawAudioMode = settings.isRawAudioGatekeeperEnabled() && settings.isRealtimeProcessingEnabled()
        val directednessTrigger =
            if (rawAudioMode) {
                val fired = directednessGateFired(data.timestampMillis)
                logger.logDirectednessGate(gateFired = fired, rawAudioModeActive = true)
                fired
            } else {
                true
            }
        val input =
            AudioInput.TranscribedSegment(
                source = ContextSource.AMBIENT_AUDIO,
                transcript = "Ambient audio: ${data.transcription}",
                directednessTrigger = directednessTrigger
            )
        emitResult(backendSelector.select().classify(input))
    }

    private suspend fun onAmbientAudioBytes() {
        if (!settings.isGatekeeperEnabled() || !settings.isRawAudioGatekeeperEnabled() || !settings.isRealtimeProcessingEnabled()) return
        val pcm = ambientAudioBytesReader.snapshot().map { it.value }.filter { it.isNotEmpty() }.flattenBytes()
        if (pcm.isEmpty()) return
        // C9: the selector decides which backend consumes raw PCM. EdgeTPU and Inherent both
        // run the audio path here; CpuRecovery returns null (it owns the text path instead).
        val input = AudioInput.RawPcm(source = ContextSource.AMBIENT_AUDIO, pcm = pcm)
        val result = backendSelector.select().classify(input) ?: return
        if (result.modelId.startsWith("raw_audio_unavailable:")) {
            Log.w(TAG, "Raw audio gatekeeper unavailable: ${result.modelId}")
        }
        logger.logGatekeeperRun(result)
        mutableResults.emit(result)
    }

    private suspend fun onText(
        prefix: String,
        source: ContextSource,
        value: Any
    ) {
        val text =
            when (value) {
                is TranscriptionData -> value.transcription
                is LocationObservation -> value.toContextText()
                else -> value.toString()
            }.takeIf { it.isNotBlank() } ?: return
        runTextClassifier("$prefix: $text", source)
    }

    private suspend fun onScreen(value: Any) {
        val text =
            when (value) {
                is ScreenObservation -> value.text
                else -> value.toString()
            }.takeIf { it.isNotBlank() } ?: return
        runTextClassifier("Screen: $text", ContextSource.SCREEN)
    }

    private suspend fun onNotification(value: Any) {
        val event = value as? NotificationEvent
        val text =
            event?.notification?.let { notification ->
                listOf(notification.title, notification.text, notification.bigText)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            } ?: value.toString()
        if (text.isBlank()) return
        runTextClassifier("Notification: $text", ContextSource.NOTIFICATION)
    }

    private suspend fun runTextClassifier(input: String, source: ContextSource) {
        if (!settings.isGatekeeperEnabled() || input.isBlank()) return
        // Non-ambient text paths always route through CpuRecoveryBackend (the BERT classifier).
        // Mirrors cosmo: only the ambient mic dispatch picks between audio and text classifiers;
        // every other source feeds text directly into BertClassifierGatekeepingModel.
        val segment =
            AudioInput.TranscribedSegment(
                source = source,
                transcript = input,
                directednessTrigger = true
            )
        emitResult(cpuRecoveryBackend.classify(segment))
    }

    private suspend fun emitResult(result: GatekeepingResult?) {
        if (result == null) return
        logger.logGatekeeperRun(result)
        mutableResults.emit(result)
    }

    private fun onDirectedness(event: DirectednessEvent) {
        lastAmbientDirectednessMillis = event.at.toEpochMilli()
    }

    private fun directednessGateFired(transcriptTimestampMillis: Long): Boolean {
        val firedAtMillis =
            ambientSodaController.directedness.replayCache.lastOrNull()?.at?.toEpochMilli()
                ?: lastAmbientDirectednessMillis
                ?: return false
        val delta = transcriptTimestampMillis - firedAtMillis
        return delta in 0..DIRECTEDNESS_WINDOW_MILLIS
    }

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

    @Volatile
    private var lastAmbientDirectednessMillis: Long? = null

    private companion object {
        const val TAG = "Gatekeeper"
        const val DIRECTEDNESS_WINDOW_MILLIS = 10_000L
    }
}

interface GatekeeperLogger {
    fun logGatekeeperRun(result: GatekeepingResult)

    fun logDirectednessGate(gateFired: Boolean, rawAudioModeActive: Boolean) = Unit
}

class GatekeeperLoggerImpl
@Inject
constructor(
    private val telemetry: CosmosTelemetry
) : GatekeeperLogger {
    override fun logGatekeeperRun(result: GatekeepingResult) {
        telemetry.log(
            TelemetryEvent.GatekeeperRun(
                modelId = result.modelId,
                detectedBy = result.detectedBy,
                triggeredFulfillment = result.triggeredFulfillment,
                detectedSkills = result.detectedSkills
            )
        )
    }

    override fun logDirectednessGate(gateFired: Boolean, rawAudioModeActive: Boolean) {
        telemetry.log(
            TelemetryEvent.GatekeeperDirectednessGate(
                gateFired = gateFired,
                rawAudioModeActive = rawAudioModeActive
            )
        )
    }
}
