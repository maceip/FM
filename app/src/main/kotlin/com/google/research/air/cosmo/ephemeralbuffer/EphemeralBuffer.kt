package com.google.research.air.cosmo.ephemeralbuffer

import com.google.common.collect.ImmutableList
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AmbientAudioEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AmbientAudioTextEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AmbientAudioBytesEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BudsAudioEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BudsAudioTextEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BudsAudioTextReader

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BudsAudioBytesEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceAudioEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceAudioTextEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceAudioBytesEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WatchAudioEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WatchAudioTextEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WatchAudioTextReader

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WatchAudioBytesEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NotificationEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NotificationTextEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ScreenEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ScreenTextEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ScreenInteractionEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ImageScreenEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CameraEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CameraJPEGBytesEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceCameraEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceCameraJPEGBytesEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class GlassesCameraEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IntentEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LocationEphemeralBuffer

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LocationTextEphemeralBuffer

data class Timestamped<out T>(
    val value: T,
    val timestampMillis: Long
)

data class EvictionPolicy(
    val maxEntries: Int,
    val maxEntryAgeMillis: Long
)

enum class EphemeralBufferId {
    AMBIENT_AUDIO,
    AMBIENT_AUDIO_BYTES,
    AMBIENT_AUDIO_TEXT,
    BUDS_AUDIO,
    BUDS_AUDIO_TEXT,
    BUDS_AUDIO_BYTES,
    DEVICE_AUDIO,
    DEVICE_AUDIO_BYTES,
    DEVICE_AUDIO_TEXT,
    WATCH_AUDIO,
    WATCH_AUDIO_TEXT,
    WATCH_AUDIO_BYTES,
    NOTIFICATION,
    NOTIFICATION_TEXT,
    SCREEN_TEXT,
    SCREEN_INTERACTION,
    SCREEN_IMAGE,
    CAMERA,
    CAMERA_JPEG_BYTES,
    DEVICE_CAMERA_FRAME,
    DEVICE_CAMERA_JPEG_BYTES,
    GLASSES_CAMERA_FRAME,
    INTENT,
    LOCATION,
    LOCATION_TEXT
}

data class EphemeralBufferSnapshot<T>(
    val id: EphemeralBufferId,
    val entries: ImmutableList<Timestamped<T>>,
    val generatedAtMillis: Long
)

interface EphemeralBufferReader<T> {
    fun flow(): Flow<Timestamped<T>>

    fun snapshots(): StateFlow<ImmutableList<Timestamped<T>>>

    fun isEmpty(): Boolean

    fun size(): Int

    fun snapshot(): ImmutableList<Timestamped<T>>

    fun snapshotReversed(): ImmutableList<Timestamped<T>>

    fun <R> map(transform: (T) -> R): EphemeralBufferReader<R>
}

interface EphemeralBufferWriter<T> {
    fun append(
        value: T,
        timestampMillis: Long
    ): Boolean

    fun flush(): ImmutableList<Timestamped<T>>

    fun clear()

    fun remove(predicate: (T) -> Boolean): Boolean

    fun replace(
        predicate: (T) -> Boolean,
        value: T
    ): Boolean
}

interface EphemeralBuffer<T> : EphemeralBufferReader<T>, EphemeralBufferWriter<T>

class InMemoryEphemeralBuffer<T>(
    initialMaxEntries: Int,
    initialMaxEntryAgeMillis: Long,
    private val clock: Clock
) : EphemeralBuffer<T> {
    private val lock = Any()
    private val entries = ArrayDeque<Timestamped<T>>()
    private val updates = MutableSharedFlow<Timestamped<T>>(extraBufferCapacity = 64)
    private val _snapshots = MutableStateFlow(ImmutableList.of<Timestamped<T>>())

    @Volatile
    private var evictionPolicy = EvictionPolicy(initialMaxEntries.coerceAtLeast(1), initialMaxEntryAgeMillis.coerceAtLeast(1_000))

    override fun append(
        value: T,
        timestampMillis: Long
    ): Boolean {
        val entry = Timestamped(value, timestampMillis)
        val snapshot =
            synchronized(lock) {
                pruneLocked()
                entries.addLast(entry)
                enforceSizeLocked()
                snapshotLocked()
            }
        _snapshots.value = snapshot
        updates.tryEmit(entry)
        return true
    }

    fun add(
        value: T,
        timestampMillis: Long = clock.millis()
    ): Boolean = append(value, timestampMillis)

    override fun flush(): ImmutableList<Timestamped<T>> {
        val snapshot = snapshot()
        _snapshots.value = snapshot
        return snapshot
    }

    override fun clear() {
        val snapshot =
            synchronized(lock) {
                entries.clear()
                snapshotLocked()
            }
        _snapshots.value = snapshot
    }

    override fun remove(predicate: (T) -> Boolean): Boolean {
        val (changed, snapshot) =
            synchronized(lock) {
                val before = entries.size
                entries.removeAll { predicate(it.value) }
                val changed = before != entries.size
                changed to snapshotLocked()
            }
        if (changed) _snapshots.value = snapshot
        return changed
    }

    override fun replace(
        predicate: (T) -> Boolean,
        value: T
    ): Boolean {
        val snapshot =
            synchronized(lock) {
                pruneLocked()
                val index = entries.indexOfFirst { predicate(it.value) }
                if (index < 0) return@synchronized null
                entries[index] = Timestamped(value, clock.millis())
                snapshotLocked()
            } ?: return false
        _snapshots.value = snapshot
        return true
    }

    fun countItemsAfter(timestampMillis: Long): Int = synchronized(lock) {
        pruneLocked()
        entries.count { it.timestampMillis > timestampMillis }
    }

    fun getEvictionPolicy(): EvictionPolicy = evictionPolicy

    fun getMaxEntryAgeMillis(): Long = evictionPolicy.maxEntryAgeMillis

    fun updateMaxEntryAgeMillis(value: Long) {
        updateEvictionPolicy(evictionPolicy.copy(maxEntryAgeMillis = value.coerceAtLeast(1_000)))
    }

    fun updateEvictionPolicy(policy: EvictionPolicy) {
        evictionPolicy = policy.copy(maxEntries = policy.maxEntries.coerceAtLeast(1), maxEntryAgeMillis = policy.maxEntryAgeMillis.coerceAtLeast(1_000))
        val snapshot =
            synchronized(lock) {
                pruneLocked()
                enforceSizeLocked()
                snapshotLocked()
            }
        _snapshots.value = snapshot
    }

    override fun flow(): Flow<Timestamped<T>> = flow {
        snapshot().forEach { emit(it) }
        updates.collect { emit(it) }
    }

    override fun snapshots(): StateFlow<ImmutableList<Timestamped<T>>> = _snapshots

    override fun isEmpty(): Boolean = size() == 0

    override fun size(): Int = synchronized(lock) {
        pruneLocked()
        entries.size
    }

    override fun snapshot(): ImmutableList<Timestamped<T>> = synchronized(lock) {
        pruneLocked()
        snapshotLocked()
    }

    override fun snapshotReversed(): ImmutableList<Timestamped<T>> = synchronized(lock) {
        pruneLocked()
        ImmutableList.copyOf(entries.reversed())
    }

    override fun <R> map(transform: (T) -> R): EphemeralBufferReader<R> {
        val upstream = this
        val firstTransform = transform
        return object : EphemeralBufferReader<R> {
            override fun flow(): Flow<Timestamped<R>> = upstream.flow().map {
                Timestamped(firstTransform(it.value), it.timestampMillis)
            }

            override fun snapshots(): StateFlow<ImmutableList<Timestamped<R>>> {
                val mapped = MutableStateFlow(ImmutableList.copyOf(upstream.snapshots().value.map { Timestamped(firstTransform(it.value), it.timestampMillis) }))
                return mapped
            }

            override fun isEmpty(): Boolean = upstream.isEmpty()

            override fun size(): Int = upstream.size()

            override fun snapshot(): ImmutableList<Timestamped<R>> = ImmutableList.copyOf(upstream.snapshot().map { Timestamped(firstTransform(it.value), it.timestampMillis) })

            override fun snapshotReversed(): ImmutableList<Timestamped<R>> = ImmutableList.copyOf(upstream.snapshotReversed().map { Timestamped(firstTransform(it.value), it.timestampMillis) })

            override fun <X> map(transform: (R) -> X): EphemeralBufferReader<X> = upstream.map { value -> transform(firstTransform(value)) }
        }
    }

    private fun pruneLocked() {
        val cutoff = clock.millis() - evictionPolicy.maxEntryAgeMillis
        while (entries.firstOrNull()?.timestampMillis?.let { it < cutoff } == true) {
            entries.removeFirst()
        }
    }

    private fun enforceSizeLocked() {
        while (entries.size > evictionPolicy.maxEntries) entries.removeFirst()
    }

    private fun snapshotLocked(): ImmutableList<Timestamped<T>> = ImmutableList.copyOf(entries)
}

interface EphemeralBufferRegistry {
    fun sourceIds(): Set<EphemeralBufferId>

    fun objectSourceIds(): Set<EphemeralBufferId>

    fun writableObjectSourceIds(): Set<EphemeralBufferId>

    fun byteSourceIds(): Set<EphemeralBufferId>

    fun reader(id: EphemeralBufferId): EphemeralBufferReader<Any>

    fun writer(id: EphemeralBufferId): EphemeralBufferWriter<Any>

    fun byteReader(id: EphemeralBufferId): EphemeralBufferReader<ByteArray>

    fun byteWriter(id: EphemeralBufferId): EphemeralBufferWriter<ByteArray>

    fun ambientAudioBytesReader(): EphemeralBufferReader<ByteArray>

    fun ambientAudioBytesWriter(): EphemeralBufferWriter<ByteArray>
}

@Singleton
class DefaultEphemeralBufferRegistry
@Inject
constructor(
    @AmbientAudioEphemeralBuffer private val ambientAudio: InMemoryEphemeralBuffer<Any>,
    @AmbientAudioBytesEphemeralBuffer private val ambientAudioBytes: InMemoryEphemeralBuffer<ByteArray>,
    @AmbientAudioTextEphemeralBuffer private val ambientAudioText: InMemoryEphemeralBuffer<Any>,
    @BudsAudioEphemeralBuffer private val budsAudio: InMemoryEphemeralBuffer<Any>,
    @BudsAudioBytesEphemeralBuffer private val budsAudioBytes: InMemoryEphemeralBuffer<ByteArray>,
    @BudsAudioTextEphemeralBuffer private val budsAudioText: InMemoryEphemeralBuffer<Any>,
    @DeviceAudioEphemeralBuffer private val deviceAudio: InMemoryEphemeralBuffer<Any>,
    @DeviceAudioBytesEphemeralBuffer private val deviceAudioBytes: InMemoryEphemeralBuffer<ByteArray>,
    @DeviceAudioTextEphemeralBuffer private val deviceAudioText: InMemoryEphemeralBuffer<Any>,
    @WatchAudioEphemeralBuffer private val watchAudio: InMemoryEphemeralBuffer<Any>,
    @WatchAudioBytesEphemeralBuffer private val watchAudioBytes: InMemoryEphemeralBuffer<ByteArray>,
    @WatchAudioTextEphemeralBuffer private val watchAudioText: InMemoryEphemeralBuffer<Any>,
    @NotificationEphemeralBuffer private val notification: InMemoryEphemeralBuffer<Any>,
    @NotificationTextEphemeralBuffer private val notificationText: InMemoryEphemeralBuffer<Any>,
    @ScreenEphemeralBuffer private val screen: InMemoryEphemeralBuffer<Any>,
    @Suppress("UnusedPrivateProperty") @ScreenTextEphemeralBuffer private val screenText: InMemoryEphemeralBuffer<Any>,
    @ScreenInteractionEphemeralBuffer private val screenInteraction: InMemoryEphemeralBuffer<Any>,
    @ImageScreenEphemeralBuffer private val imageScreen: InMemoryEphemeralBuffer<Any>,
    @CameraEphemeralBuffer private val camera: InMemoryEphemeralBuffer<Any>,
    @CameraJPEGBytesEphemeralBuffer private val cameraJpegBytes: InMemoryEphemeralBuffer<ByteArray>,
    @DeviceCameraEphemeralBuffer private val deviceCamera: InMemoryEphemeralBuffer<Any>,
    @DeviceCameraJPEGBytesEphemeralBuffer private val deviceCameraJpegBytes: InMemoryEphemeralBuffer<ByteArray>,
    @GlassesCameraEphemeralBuffer private val glassesCamera: InMemoryEphemeralBuffer<Any>,
    @IntentEphemeralBuffer private val intent: InMemoryEphemeralBuffer<Any>,
    @LocationEphemeralBuffer private val location: InMemoryEphemeralBuffer<Any>,
    @LocationTextEphemeralBuffer private val locationText: InMemoryEphemeralBuffer<Any>
) : EphemeralBufferRegistry {
    private val objectBuffers: Map<EphemeralBufferId, EphemeralBuffer<Any>> =
        mapOf(
            EphemeralBufferId.AMBIENT_AUDIO to ambientAudio,
            EphemeralBufferId.AMBIENT_AUDIO_TEXT to ambientAudioText,
            EphemeralBufferId.BUDS_AUDIO to budsAudio,
            EphemeralBufferId.BUDS_AUDIO_TEXT to budsAudioText,
            EphemeralBufferId.DEVICE_AUDIO to deviceAudio,
            EphemeralBufferId.DEVICE_AUDIO_TEXT to deviceAudioText,
            EphemeralBufferId.WATCH_AUDIO to watchAudio,
            EphemeralBufferId.WATCH_AUDIO_TEXT to watchAudioText,
            EphemeralBufferId.NOTIFICATION to notification,
            EphemeralBufferId.NOTIFICATION_TEXT to notificationText,
            EphemeralBufferId.SCREEN_TEXT to screen,
            EphemeralBufferId.SCREEN_IMAGE to imageScreen,
            EphemeralBufferId.SCREEN_INTERACTION to screenInteraction,
            EphemeralBufferId.CAMERA to camera,
            EphemeralBufferId.DEVICE_CAMERA_FRAME to deviceCamera,
            EphemeralBufferId.GLASSES_CAMERA_FRAME to glassesCamera,
            EphemeralBufferId.INTENT to intent,
            EphemeralBufferId.LOCATION to location,
            EphemeralBufferId.LOCATION_TEXT to locationText
        )
    private val readerOnlyObjectBuffers: Map<EphemeralBufferId, EphemeralBufferReader<Any>> = emptyMap()
    private val byteBuffers: Map<EphemeralBufferId, EphemeralBuffer<ByteArray>> =
        mapOf(
            EphemeralBufferId.AMBIENT_AUDIO_BYTES to ambientAudioBytes,
            EphemeralBufferId.BUDS_AUDIO_BYTES to budsAudioBytes,
            EphemeralBufferId.DEVICE_AUDIO_BYTES to deviceAudioBytes,
            EphemeralBufferId.WATCH_AUDIO_BYTES to watchAudioBytes,
            EphemeralBufferId.CAMERA_JPEG_BYTES to cameraJpegBytes,
            EphemeralBufferId.DEVICE_CAMERA_JPEG_BYTES to deviceCameraJpegBytes
        )

    override fun sourceIds(): Set<EphemeralBufferId> = objectSourceIds() + byteSourceIds()

    override fun objectSourceIds(): Set<EphemeralBufferId> = objectBuffers.keys + readerOnlyObjectBuffers.keys

    override fun writableObjectSourceIds(): Set<EphemeralBufferId> = objectBuffers.keys

    override fun byteSourceIds(): Set<EphemeralBufferId> = byteBuffers.keys

    override fun reader(id: EphemeralBufferId): EphemeralBufferReader<Any> = objectBuffers[id] ?: readerOnlyObjectBuffers[id] ?: error("$id is not an object buffer")

    override fun writer(id: EphemeralBufferId): EphemeralBufferWriter<Any> = objectBuffer(id)

    override fun byteReader(id: EphemeralBufferId): EphemeralBufferReader<ByteArray> = byteBuffer(id)

    override fun byteWriter(id: EphemeralBufferId): EphemeralBufferWriter<ByteArray> = byteBuffer(id)

    override fun ambientAudioBytesReader(): EphemeralBufferReader<ByteArray> = ambientAudioBytes

    override fun ambientAudioBytesWriter(): EphemeralBufferWriter<ByteArray> = ambientAudioBytes

    private fun objectBuffer(id: EphemeralBufferId): EphemeralBuffer<Any> =
        objectBuffers[id] ?: error("$id is not an object buffer")

    private fun byteBuffer(id: EphemeralBufferId): EphemeralBuffer<ByteArray> =
        byteBuffers[id] ?: error("$id is not a byte buffer")
}

@Module
@InstallIn(SingletonComponent::class)
object EphemeralBufferModule {
    private const val MAX_ENTRIES = 256

    private fun ageMillis(store: CosmoSettingStore): Long = store.getEphemeralBufferDurationMin() * 60_000L

    private fun <T> trackedBuffer(
        store: CosmoSettingStore,
        scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<T> {
        val buffer = InMemoryEphemeralBuffer<T>(MAX_ENTRIES, ageMillis(store), clock)
        scope.launch {
            store.getCosmoSettingFlow().collectLatest {
                buffer.updateMaxEntryAgeMillis(ageMillis(store))
            }
        }
        return buffer
    }

    @Provides @Singleton
    fun registry(impl: DefaultEphemeralBufferRegistry): EphemeralBufferRegistry = impl

    @Provides @Singleton @AmbientAudioEphemeralBuffer
    fun ambient(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @AmbientAudioBytesEphemeralBuffer
    fun ambientBytes(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<ByteArray> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @AmbientAudioTextEphemeralBuffer
    fun ambientText(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @BudsAudioEphemeralBuffer
    fun buds(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @BudsAudioBytesEphemeralBuffer
    fun budsBytes(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<ByteArray> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @BudsAudioTextReader
    fun budsTextReader(
        @BudsAudioTextEphemeralBuffer buffer: InMemoryEphemeralBuffer<Any>
    ): EphemeralBufferReader<Any> = buffer

    @Provides @Singleton @BudsAudioTextEphemeralBuffer
    fun budsText(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @DeviceAudioEphemeralBuffer
    fun deviceAudio(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @DeviceAudioBytesEphemeralBuffer
    fun deviceAudioBytes(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<ByteArray> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @DeviceAudioTextEphemeralBuffer
    fun deviceAudioText(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @WatchAudioEphemeralBuffer
    fun watchAudio(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @WatchAudioBytesEphemeralBuffer
    fun watchAudioBytes(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<ByteArray> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @WatchAudioTextReader
    fun watchAudioTextReader(
        @WatchAudioTextEphemeralBuffer buffer: InMemoryEphemeralBuffer<Any>
    ): EphemeralBufferReader<Any> = buffer

    @Provides @Singleton @WatchAudioTextEphemeralBuffer
    fun watchAudioText(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @NotificationEphemeralBuffer
    fun notification(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @NotificationTextEphemeralBuffer
    fun notificationText(
        @NotificationEphemeralBuffer buffer: InMemoryEphemeralBuffer<Any>
    ): InMemoryEphemeralBuffer<Any> = buffer

    @Provides @Singleton @ScreenEphemeralBuffer
    fun screen(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @ScreenTextEphemeralBuffer
    fun screenText(
        @ScreenEphemeralBuffer buffer: InMemoryEphemeralBuffer<Any>
    ): InMemoryEphemeralBuffer<Any> = buffer

    @Provides @Singleton @ScreenInteractionEphemeralBuffer
    fun screenInteraction(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @ImageScreenEphemeralBuffer
    fun imageScreen(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @CameraEphemeralBuffer
    fun camera(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @CameraJPEGBytesEphemeralBuffer
    fun cameraJpegBytes(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<ByteArray> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @DeviceCameraEphemeralBuffer
    fun deviceCamera(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @DeviceCameraJPEGBytesEphemeralBuffer
    fun deviceCameraJpegBytes(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<ByteArray> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @GlassesCameraEphemeralBuffer
    fun glassesCamera(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @IntentEphemeralBuffer
    fun intent(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @LocationEphemeralBuffer
    fun location(
        store: CosmoSettingStore,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): InMemoryEphemeralBuffer<Any> = trackedBuffer(store, scope, clock)

    @Provides @Singleton @LocationTextEphemeralBuffer
    fun locationText(
        @LocationEphemeralBuffer buffer: InMemoryEphemeralBuffer<Any>
    ): InMemoryEphemeralBuffer<Any> = buffer
}
