package com.google.research.air.cosmo.privacy

import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.ContextStoreStats
import com.google.research.air.cosmo.context.InsertResult
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.ScoredEvent
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferId
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferReader
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferWriter
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.setting.CosmoSettingStore
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyCoordinatorTest {
    private val now = 1_746_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    @Test
    fun retentionSweepDeletesOldContextRowsAndBuffers() = runTest {
        val contextStore = RecordingContextStore()
        val registry = TestBufferRegistry(clock)
        val settings = mockk<CosmoSettingStore>()
        every { settings.getEphemeralBufferDurationMin() } returns 5
        val oldTimestamp = now - 6 * 60_000L
        val freshTimestamp = now - 4 * 60_000L
        contextStore.rows += contextEvent("old", oldTimestamp)
        contextStore.rows += contextEvent("fresh", freshTimestamp)
        registry.screen.add("old screen", oldTimestamp)
        registry.screen.add("fresh screen", freshTimestamp)

        val result = PrivacyCoordinator(contextStore, registry, settings, clock).sweepRetention()

        assertEquals(1, result.contextRowsDeleted)
        assertEquals(1, result.buffersChanged)
        assertEquals(listOf("fresh"), contextStore.rows.map { it.id })
        assertEquals(listOf("fresh screen"), registry.screen.snapshot().map { it.value })
    }

    @Test
    fun forgetWindowDeletesContextRowsAndInFlightBuffers() = runTest {
        val contextStore = RecordingContextStore()
        val registry = TestBufferRegistry(clock)
        val settings = mockk<CosmoSettingStore>(relaxed = true)
        val forgetStart = now - 2_000L
        val forgetEnd = now - 1_000L
        val forgetMiddle = now - 1_500L
        val forgottenBytes = byteArrayOf(1, 2, 3)
        contextStore.rows += contextEvent("screen-forget", forgetMiddle, Source.SCREEN)
        contextStore.rows += contextEvent("audio-forget", forgetMiddle, Source.AUDIO)
        contextStore.rows += contextEvent("screen-keep", now, Source.SCREEN)
        registry.screen.add("screen-forget", forgetMiddle)
        registry.screen.add("screen-keep", now)
        registry.audio.add("audio-forget", forgetMiddle)
        registry.audioBytes.add(forgottenBytes, forgetMiddle)

        val result = PrivacyCoordinator(contextStore, registry, settings, clock).forgetWindow(forgetStart..forgetEnd)

        assertEquals(2, result.contextRowsDeleted)
        assertEquals(3, result.buffersChanged)
        assertEquals(listOf("screen-keep"), contextStore.rows.map { it.id })
        assertEquals(listOf("screen-keep"), registry.screen.snapshot().map { it.value })
        assertTrue(registry.audio.snapshot().isEmpty())
        assertTrue(registry.audioBytes.snapshot().isEmpty())
    }

    private fun contextEvent(
        id: String,
        timestampMs: Long,
        source: Source = Source.SCREEN
    ): ContextEvent =
        ContextEvent(
            id = id,
            source = source,
            timestampMs = timestampMs,
            text = id,
            blobRef = null,
            embedding = null,
            retentionClass = RetentionClass.EPHEMERAL,
            sourceMetadata = emptyMap()
        )

    private class RecordingContextStore : ContextStore {
        val rows = mutableListOf<ContextEvent>()

        override suspend fun insert(event: ContextEvent): InsertResult {
            rows += event
            return InsertResult.Stored
        }

        override suspend fun attachEmbedding(
            id: String,
            embedding: FloatArray
        ) = Unit

        override suspend fun queryByTime(
            range: LongRange,
            sources: Set<Source>?
        ): List<ContextEvent> = rows.filter { it.timestampMs in range && sources?.contains(it.source) != false }

        override suspend fun queryByText(
            query: String,
            limit: Int
        ): List<ContextEvent> = rows.filter { it.text?.contains(query) == true }.take(limit)

        override suspend fun queryByEmbedding(
            vec: FloatArray,
            limit: Int
        ): List<ScoredEvent> = emptyList()

        override suspend fun delete(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteRange(
            range: LongRange,
            sources: Set<Source>?
        ): Int {
            val before = rows.size
            rows.removeAll { it.timestampMs in range && sources?.contains(it.source) != false }
            return before - rows.size
        }

        override suspend fun purgeSource(source: Source): Int {
            val before = rows.size
            rows.removeAll { it.source == source }
            return before - rows.size
        }

        override fun events(): Flow<ContextEvent> = emptyFlow()

        override suspend fun stats(): ContextStoreStats =
            ContextStoreStats(
                countsBySource = rows.groupingBy { it.source }.eachCount(),
                countsByRetention = rows.groupingBy { it.retentionClass }.eachCount()
            )
    }

    private class TestBufferRegistry(clock: Clock) : EphemeralBufferRegistry {
        val screen = InMemoryEphemeralBuffer<Any>(8, 24 * 60 * 60_000L, clock)
        val audio = InMemoryEphemeralBuffer<Any>(8, 24 * 60 * 60_000L, clock)
        val audioBytes = InMemoryEphemeralBuffer<ByteArray>(8, 24 * 60 * 60_000L, clock)

        override fun sourceIds(): Set<EphemeralBufferId> = objectSourceIds() + byteSourceIds()

        override fun objectSourceIds(): Set<EphemeralBufferId> =
            setOf(EphemeralBufferId.SCREEN_TEXT, EphemeralBufferId.AMBIENT_AUDIO)

        override fun writableObjectSourceIds(): Set<EphemeralBufferId> = objectSourceIds()

        override fun byteSourceIds(): Set<EphemeralBufferId> = setOf(EphemeralBufferId.AMBIENT_AUDIO_BYTES)

        override fun reader(id: EphemeralBufferId): EphemeralBufferReader<Any> =
            when (id) {
                EphemeralBufferId.SCREEN_TEXT -> screen
                EphemeralBufferId.AMBIENT_AUDIO -> audio
                else -> error("$id is not an object buffer")
            }

        override fun writer(id: EphemeralBufferId): EphemeralBufferWriter<Any> =
            when (id) {
                EphemeralBufferId.SCREEN_TEXT -> screen
                EphemeralBufferId.AMBIENT_AUDIO -> audio
                else -> error("$id is not an object buffer")
            }

        override fun byteReader(id: EphemeralBufferId): EphemeralBufferReader<ByteArray> =
            if (id == EphemeralBufferId.AMBIENT_AUDIO_BYTES) audioBytes else error("$id is not a byte buffer")

        override fun byteWriter(id: EphemeralBufferId): EphemeralBufferWriter<ByteArray> =
            if (id == EphemeralBufferId.AMBIENT_AUDIO_BYTES) audioBytes else error("$id is not a byte buffer")

        override fun ambientAudioBytesReader(): EphemeralBufferReader<ByteArray> = audioBytes

        override fun ambientAudioBytesWriter(): EphemeralBufferWriter<ByteArray> = audioBytes

        override fun removeRange(range: LongRange): Int =
            listOf(screen, audio).count { buffer -> buffer.removeEntries { it.timestampMillis in range } } +
                listOf(audioBytes).count { buffer -> buffer.removeEntries { it.timestampMillis in range } }
    }
}
