package com.google.research.air.cosmo.lib.soda

import android.content.Context
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultMicSodaController

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StreamingMicSodaController

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BudsAudioSodaController

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceAudioSodaController

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WatchAudioSodaController

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CallAudioSodaController

data class TranscriptionData(
    val text: String,
    val audioData: ByteArray,
    val speakerLabel: String,
    val speakerConfidenceScoreMap: Map<String, Float>,
    val timestampMillis: Long
) {
    val transcription: String get() = text

    fun hasContent(): Boolean = text.isNotBlank() || audioData.isNotEmpty()
}

data class DirectednessEvent(
    val source: ContextSource,
    val at: Instant
)

interface SodaTranscriptionController {
    val transcriptions: SharedFlow<TranscriptionData>

    val directedness: SharedFlow<DirectednessEvent>

    fun addAudioBytes(bytes: ByteArray)

    fun startCapture(
        voiceMatchEnabled: Boolean = false,
        onEvent: (TranscriptionData) -> Unit = {}
    )

    fun stopCapture()

    fun flush(): ByteArray
}

class AudioAccumulator
@Inject
constructor(
    private val clock: Clock
) {
    private val lock = Any()
    private val chunks = ArrayDeque<ByteArray>()
    private var totalBytes = 0

    fun addAudioBytes(bytes: ByteArray) = synchronized(lock) {
        chunks.addLast(bytes.copyOf())
        totalBytes += bytes.size
        while (totalBytes > MAX_BYTES) {
            totalBytes -= chunks.removeFirst().size
        }
    }

    fun flush(): ByteArray = synchronized(lock) {
        val out = ByteArray(totalBytes)
        var offset = 0
        chunks.forEach {
            it.copyInto(out, offset)
            offset += it.size
        }
        chunks.clear()
        totalBytes = 0
        out
    }

    fun timestampMillis(): Long = clock.millis()

    companion object {
        private const val MAX_BYTES = 32000 * 60
    }
}

/**
 * Per-source SODA recognition controller. Each subclass (mic, buds, device, watch, call,
 * streaming) constructs its own [SodaSession] — a dedicated [com.google.android.libraries.assistant.soda.Soda]
 * native session with its own callback wiring. Mirrors cosmo's `SodaController.smali`
 * (lines 619–1375): per-source instances, never one shared session. Multiple sources can run
 * concurrently with independent transcripts and directedness events.
 */
abstract class SodaController(
    private val source: ContextSource,
    private val accumulator: AudioAccumulator,
    private val scope: CoroutineScope,
    private val context: Context,
    private val sodaCapability: JniSodaCapability,
    private val clock: Clock,
) : SodaTranscriptionController {
    private val _transcriptions = MutableSharedFlow<TranscriptionData>(extraBufferCapacity = 32)
    override val transcriptions: SharedFlow<TranscriptionData> = _transcriptions
    private val _directedness = MutableSharedFlow<DirectednessEvent>(replay = 1, extraBufferCapacity = 16)
    override val directedness: SharedFlow<DirectednessEvent> = _directedness

    @Volatile
    private var session: SodaSession? = null

    @Volatile
    private var started = false

    override fun addAudioBytes(bytes: ByteArray) {
        accumulator.addAudioBytes(bytes)
        val active = session
        if (active != null && bytes.isNotEmpty()) {
            val direct = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            direct.put(bytes)
            direct.position(0)
            active.addAudio(direct, bytes.size)
        }
    }

    override fun startCapture(
        voiceMatchEnabled: Boolean,
        onEvent: (TranscriptionData) -> Unit
    ) {
        if (started) return
        started = true
        scope.launch {
            if (!sodaCapability.isAvailable) {
                started = false
                return@launch
            }
            val prepared = sodaCapability.prepare() as? SodaPrepareResult.Available
            if (prepared == null) {
                started = false
                return@launch
            }
            if (!started) return@launch
            val callback =
                object : SodaTranscriptCallback {
                    override fun onTranscript(text: String, isFinal: Boolean) {
                        if (!started) return
                        if (text.isBlank()) return
                        val data =
                            TranscriptionData(
                                text = text,
                                audioData = if (isFinal) accumulator.flush() else byteArrayOf(),
                                speakerLabel = "",
                                speakerConfidenceScoreMap = emptyMap(),
                                timestampMillis = accumulator.timestampMillis()
                            )
                        scope.launch {
                            _transcriptions.emit(data)
                            onEvent(data)
                        }
                    }

                    override fun onDirectednessTrigger(at: Instant) {
                        if (!started) return
                        scope.launch {
                            _directedness.emit(DirectednessEvent(source, at))
                        }
                    }
                }
            val newSession = SodaSession(context, prepared.packDir, clock)
            if (newSession.start(callback) is SodaStartResult.Started) {
                session = newSession
            } else {
                started = false
            }
        }
    }

    override fun stopCapture() {
        started = false
        val active = session ?: return
        session = null
        active.stop()
    }

    override fun flush(): ByteArray = accumulator.flush()
}

class MicSodaController
@Inject
constructor(
    accumulator: AudioAccumulator,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
    sodaCapability: JniSodaCapability,
    clock: Clock,
) : SodaController(ContextSource.AMBIENT_AUDIO, accumulator, scope, context, sodaCapability, clock)

class StreamingSodaController
@Inject
constructor(
    accumulator: AudioAccumulator,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
    sodaCapability: JniSodaCapability,
    clock: Clock,
) : SodaController(ContextSource.AMBIENT_AUDIO, accumulator, scope, context, sodaCapability, clock)

class BudsSodaController
@Inject
constructor(
    accumulator: AudioAccumulator,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
    sodaCapability: JniSodaCapability,
    clock: Clock,
) : SodaController(ContextSource.BUDS_AUDIO, accumulator, scope, context, sodaCapability, clock)

class DeviceSodaController
@Inject
constructor(
    accumulator: AudioAccumulator,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
    sodaCapability: JniSodaCapability,
    clock: Clock,
) : SodaController(ContextSource.DEVICE_AUDIO, accumulator, scope, context, sodaCapability, clock)

class WatchSodaController
@Inject
constructor(
    accumulator: AudioAccumulator,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
    sodaCapability: JniSodaCapability,
    clock: Clock,
) : SodaController(ContextSource.WATCH_AUDIO, accumulator, scope, context, sodaCapability, clock)

class CallSodaController
@Inject
constructor(
    accumulator: AudioAccumulator,
    @ApplicationScope scope: CoroutineScope,
    @ApplicationContext context: Context,
    sodaCapability: JniSodaCapability,
    clock: Clock,
) : SodaController(ContextSource.DEVICE_AUDIO, accumulator, scope, context, sodaCapability, clock)

// Per architecture-delta C4 row 4: `SodaCapability` interface + `UnavailableSodaCapability` +
// `SodaCapabilityModule` deleted. `JniSodaCapability` is the concrete production type, bound
// directly by its @Singleton + @Inject constructor; no @Binds shim is needed.

@Module
@InstallIn(SingletonComponent::class)
object SodaControllerModule {
    @Provides @Singleton @DefaultMicSodaController
    fun provideDefaultMic(controller: MicSodaController): SodaController = controller

    @Provides @Singleton @DefaultMicSodaController
    fun provideDefaultMicTranscriptionController(controller: MicSodaController): SodaTranscriptionController = controller

    @Provides @Singleton @StreamingMicSodaController
    fun provideStreamingMic(controller: StreamingSodaController): SodaController = controller

    @Provides @Singleton @StreamingMicSodaController
    fun provideStreamingMicTranscriptionController(controller: StreamingSodaController): SodaTranscriptionController = controller

    @Provides @Singleton @BudsAudioSodaController
    fun provideBuds(controller: BudsSodaController): SodaController = controller

    @Provides @Singleton @BudsAudioSodaController
    fun provideBudsTranscriptionController(controller: BudsSodaController): SodaTranscriptionController = controller

    @Provides @Singleton @DeviceAudioSodaController
    fun provideDevice(controller: DeviceSodaController): SodaController = controller

    @Provides @Singleton @DeviceAudioSodaController
    fun provideDeviceTranscriptionController(controller: DeviceSodaController): SodaTranscriptionController = controller

    @Provides @Singleton @WatchAudioSodaController
    fun provideWatch(controller: WatchSodaController): SodaController = controller

    @Provides @Singleton @WatchAudioSodaController
    fun provideWatchTranscriptionController(controller: WatchSodaController): SodaTranscriptionController = controller

    @Provides @Singleton @CallAudioSodaController
    fun provideCall(controller: CallSodaController): SodaController = controller

    @Provides @Singleton @CallAudioSodaController
    fun provideCallTranscriptionController(controller: CallSodaController): SodaTranscriptionController = controller
}
