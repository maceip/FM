package com.google.research.air.cosmo.devicediscovery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferId
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class AmbientLanPeerBridge
@Inject
constructor(
    private val discovery: AmbientDeviceDiscovery,
    private val client: AmbientLanPeerClient,
    private val buffers: EphemeralBufferRegistry,
    private val contextStore: ContextStore,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val localDeviceId = UUID.randomUUID().toString()
    private val lastSentByBuffer = mutableMapOf<EphemeralBufferId, Long>()
    private val lastFetchedByPeer = mutableMapOf<String, Long>()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        discovery.start()
        job =
            scope.launch {
                while (isActive) {
                    syncOnce()
                    delay(SYNC_INTERVAL_MILLIS)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        discovery.stop()
    }

    suspend fun syncOnce() {
        val peers =
            discovery
                .devices
                .value
                .filter { it.isCosmoPeer && it.host != null && it.port > 0 }
        if (peers.isEmpty()) return
        val frames = localFrames()
        peers.forEach { peer ->
            if (frames.isNotEmpty()) {
                client.sendFrames(peer, AmbientLanFrameBatch(deviceId = localDeviceId, frames = frames))
            }
            val key = peer.serviceName.ifBlank { peer.host?.hostAddress.orEmpty() }
            val since = lastFetchedByPeer[key] ?: 0L
            client.fetchFrames(peer, since)
                .onSuccess { batch ->
                    batch.frames.forEach(::ingestFrame)
                    lastFetchedByPeer[key] = batch.frames.maxOfOrNull { it.timestampMillis } ?: since
                }
        }
    }

    fun localCapabilities(): AmbientLanPeerCapabilities =
        AmbientLanPeerCapabilities(
            deviceId = localDeviceId,
            supportedBuffers = (OUTBOUND_OBJECT_BUFFERS + OUTBOUND_BYTE_BUFFERS + INBOUND_OBJECT_BUFFERS + INBOUND_BYTE_BUFFERS).toSet()
        )

    private fun localFrames(): List<AmbientLanFrame> =
        buildList {
            OUTBOUND_BYTE_BUFFERS.forEach { id ->
                val after = lastSentByBuffer[id] ?: 0L
                buffers
                    .byteReader(id)
                    .snapshot()
                    .filter { it.timestampMillis > after }
                    .forEach { entry ->
                        add(
                            AmbientLanFrame(
                                id = "${localDeviceId}:${id.name}:${entry.timestampMillis}",
                                bufferId = id,
                                kind = AmbientLanPayloadKind.BYTES,
                                timestampMillis = entry.timestampMillis,
                                payload = entry.value,
                                metadata = mapOf("origin" to "local-android")
                            )
                        )
                    }
                lastSentByBuffer[id] = buffers.byteReader(id).snapshot().maxOfOrNull { it.timestampMillis } ?: after
            }
            OUTBOUND_OBJECT_BUFFERS.forEach { id ->
                val after = lastSentByBuffer[id] ?: 0L
                buffers
                    .reader(id)
                    .snapshot()
                    .filter { it.timestampMillis > after }
                    .forEach { entry ->
                        val frame = entry.value.toLanFrame(localDeviceId, id, entry.timestampMillis)
                        if (frame != null) add(frame)
                    }
                lastSentByBuffer[id] = buffers.reader(id).snapshot().maxOfOrNull { it.timestampMillis } ?: after
            }
        }

    private fun ingestFrame(frame: AmbientLanFrame) {
        if (frame.metadata["origin"] == "local-android") return
        when {
            frame.bufferId in INBOUND_BYTE_BUFFERS -> {
                buffers.byteWriter(frame.bufferId).append(frame.payload, frame.timestampMillis)
                persistIfTextual(frame, null)
            }
            frame.bufferId in INBOUND_OBJECT_BUFFERS -> {
                val value = frame.toObjectValue() ?: return
                buffers.writer(frame.bufferId).append(value, frame.timestampMillis)
                persistIfTextual(frame, value.toString())
            }
        }
    }

    private fun persistIfTextual(
        frame: AmbientLanFrame,
        text: String?
    ) {
        val source = frame.bufferId.toContextSource() ?: return
        val content = text ?: frame.metadata["text"] ?: return
        scope.launch {
            contextStore.insert(
                ContextEvent(
                    id = frame.id,
                    source = source,
                    timestampMs = frame.timestampMillis,
                    text = content,
                    blobRef = null,
                    embedding = null,
                    retentionClass = RetentionClass.EPHEMERAL,
                    sourceMetadata =
                        frame.metadata +
                            mapOf(
                                "transport" to "local_network",
                                "buffer_id" to frame.bufferId.name
                            )
                )
            )
        }
    }

    private fun Any.toLanFrame(
        deviceId: String,
        id: EphemeralBufferId,
        timestampMillis: Long
    ): AmbientLanFrame? =
        when (this) {
            is ByteArray ->
                AmbientLanFrame(
                    id = "$deviceId:${id.name}:$timestampMillis",
                    bufferId = id,
                    kind = AmbientLanPayloadKind.BYTES,
                    timestampMillis = timestampMillis,
                    payload = this,
                    metadata = mapOf("origin" to "local-android")
                )
            is Bitmap ->
                AmbientLanFrame(
                    id = "$deviceId:${id.name}:$timestampMillis",
                    bufferId = id,
                    kind = AmbientLanPayloadKind.BYTES,
                    timestampMillis = timestampMillis,
                    payload = toJpegBytes(),
                    metadata = mapOf("origin" to "local-android", "content_type" to "image/jpeg")
                )
            else ->
                AmbientLanFrame(
                    id = "$deviceId:${id.name}:$timestampMillis",
                    bufferId = id,
                    kind = AmbientLanPayloadKind.TEXT,
                    timestampMillis = timestampMillis,
                    payload = toString().encodeToByteArray(),
                    metadata = mapOf("origin" to "local-android", "text" to toString())
                )
        }

    private fun AmbientLanFrame.toObjectValue(): Any? =
        when {
            metadata["content_type"] == "image/jpeg" -> BitmapFactory.decodeByteArray(payload, 0, payload.size)
            kind == AmbientLanPayloadKind.TEXT -> payload.decodeToString()
            else -> payload
        }

    private fun Bitmap.toJpegBytes(): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }

    private fun EphemeralBufferId.toContextSource(): Source? =
        when (this) {
            EphemeralBufferId.AMBIENT_AUDIO_TEXT,
            EphemeralBufferId.BUDS_AUDIO_TEXT,
            EphemeralBufferId.DEVICE_AUDIO_TEXT,
            EphemeralBufferId.WATCH_AUDIO_TEXT -> Source.AUDIO
            EphemeralBufferId.NOTIFICATION_TEXT -> Source.NOTIFICATION
            EphemeralBufferId.SCREEN_TEXT -> Source.SCREEN
            EphemeralBufferId.SCREEN_INTERACTION -> Source.INTERACTION
            EphemeralBufferId.CAMERA,
            EphemeralBufferId.DEVICE_CAMERA_FRAME,
            EphemeralBufferId.GLASSES_CAMERA_FRAME,
            EphemeralBufferId.CAMERA_JPEG_BYTES,
            EphemeralBufferId.DEVICE_CAMERA_JPEG_BYTES -> Source.CAMERA
            EphemeralBufferId.INTENT -> Source.INTENT
            EphemeralBufferId.LOCATION,
            EphemeralBufferId.LOCATION_TEXT -> Source.LOCATION
            else -> null
        }

    companion object {
        private const val SYNC_INTERVAL_MILLIS = 5_000L
        private const val JPEG_QUALITY = 85
        private val OUTBOUND_BYTE_BUFFERS =
            setOf(
                EphemeralBufferId.AMBIENT_AUDIO_BYTES,
                EphemeralBufferId.BUDS_AUDIO_BYTES,
                EphemeralBufferId.DEVICE_AUDIO_BYTES,
                EphemeralBufferId.WATCH_AUDIO_BYTES,
                EphemeralBufferId.CAMERA_JPEG_BYTES,
                EphemeralBufferId.DEVICE_CAMERA_JPEG_BYTES
            )
        private val INBOUND_BYTE_BUFFERS = OUTBOUND_BYTE_BUFFERS
        private val OUTBOUND_OBJECT_BUFFERS =
            setOf(
                EphemeralBufferId.AMBIENT_AUDIO_TEXT,
                EphemeralBufferId.BUDS_AUDIO_TEXT,
                EphemeralBufferId.DEVICE_AUDIO_TEXT,
                EphemeralBufferId.WATCH_AUDIO_TEXT,
                EphemeralBufferId.NOTIFICATION_TEXT,
                EphemeralBufferId.SCREEN_TEXT,
                EphemeralBufferId.SCREEN_INTERACTION,
                EphemeralBufferId.DEVICE_CAMERA_FRAME,
                EphemeralBufferId.GLASSES_CAMERA_FRAME,
                EphemeralBufferId.INTENT,
                EphemeralBufferId.LOCATION_TEXT
            )
        private val INBOUND_OBJECT_BUFFERS = OUTBOUND_OBJECT_BUFFERS
    }
}
