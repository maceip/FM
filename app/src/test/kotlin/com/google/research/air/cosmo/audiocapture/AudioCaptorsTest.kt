package com.google.research.air.cosmo.audiocapture

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.ContextStoreStats
import com.google.research.air.cosmo.context.InsertResult
import com.google.research.air.cosmo.context.ScoredEvent
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.lib.buds.BudsAudioChunk
import com.google.research.air.cosmo.lib.buds.BudsAudioProvider
import com.google.research.air.cosmo.lib.buds.BudsProviderState
import com.google.research.air.cosmo.lib.buds.SbcAudioDecoder
import com.google.research.air.cosmo.lib.record.RecordServiceListener
import com.google.research.air.cosmo.lib.record.RecordServiceManager
import com.google.research.air.cosmo.lib.soda.SodaTranscriptionController
import com.google.research.air.cosmo.lib.soda.TranscriptionData
import com.google.research.air.cosmo.setting.CosmoSettingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioCaptorsTest {
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @Test
    fun budsCaptorConsumesBudsBytesAndPersistsTranscripts() = runTest {
        val fixture = CaptorFixture()
        val provider = FakeBudsAudioProvider()
        val rawBytes = byteArrayOf(9, 9)
        val decodedBytes = byteArrayOf(1, 0, 2, 0)
        val decoder =
            object : SbcAudioDecoder {
                override fun decode(bytes: ByteArray): ByteArray = decodedBytes

                override fun reset() = Unit
            }
        val context =
            mockk<Context>(relaxed = true) {
                every { getSystemService(BluetoothManager::class.java) } returns null
            }
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) } returns null
        val captor =
            SODABudsAudioCaptor(
                context = context,
                scope = this,
                sodaController = fixture.soda,
                settings = fixture.settings,
                contextStore = fixture.contextStore,
                textBuffer = fixture.textBuffer,
                audioBytesBuffer = fixture.bytesBuffer,
                budsAudioProvider = provider,
                sbcAudioDecoder = decoder,
            )
        val received = mutableListOf<TranscriptionData>()
        val collection = launch {
            captor.startCapture().take(1).collect { received += it }
        }
        advanceUntilIdle()

        provider.emit(rawBytes)
        advanceUntilIdle()

        assertTrue(fixture.soda.started)
        assertArrayEquals(decodedBytes, fixture.bytesBuffer.snapshot().single().value)
        assertArrayEquals(decodedBytes, fixture.soda.audioBytes.single())

        val transcription =
            TranscriptionData(
                text = "set a timer",
                audioData = decodedBytes,
                speakerLabel = "",
                speakerConfidenceScoreMap = emptyMap(),
                timestampMillis = clock.millis()
            )
        fixture.soda.emit(transcription)
        advanceUntilIdle()

        assertEquals(listOf(transcription), received)
        assertEquals(1, fixture.textBuffer.size())
        assertEquals("buds", fixture.contextStore.events.single().sourceMetadata["captor"])
        collection.cancel()
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun deviceCaptorConsumesDeviceBytesAndPersistsTranscripts() = runTest {
        val fixture = CaptorFixture()
        val captor =
            SODADeviceAudioCaptor(
                scope = this,
                sodaController = fixture.soda,
                callSodaController = fixture.callSoda,
                recordServiceManager = fixture.recordServiceManager,
                settings = fixture.settings,
                contextStore = fixture.contextStore,
                textBuffer = fixture.textBuffer,
                audioBytesBuffer = fixture.bytesBuffer,
                clock = clock
            )

        fixture.assertBufferBackedCapture(this, captor, "device")
    }

    @Test
    fun deviceCaptorForwardsServiceBytesOnceThroughBufferFanout() = runTest {
        val fixture = CaptorFixture()
        val listener = slot<RecordServiceListener>()
        every { fixture.recordServiceManager.startDeviceAudioRecording(capture(listener)) } returns Unit
        val captor =
            SODADeviceAudioCaptor(
                scope = this,
                sodaController = fixture.soda,
                callSodaController = fixture.callSoda,
                recordServiceManager = fixture.recordServiceManager,
                settings = fixture.settings,
                contextStore = fixture.contextStore,
                textBuffer = fixture.textBuffer,
                audioBytesBuffer = fixture.bytesBuffer,
                clock = clock
            )
        val collection = launch {
            captor.startCapture().collect()
        }
        advanceUntilIdle()

        val bytes = byteArrayOf(3, 0, 4, 0)
        listener.captured.onBytesReceived(bytes)
        advanceUntilIdle()

        assertEquals(1, fixture.soda.audioBytes.size)
        assertArrayEquals(bytes, fixture.soda.audioBytes.single())
        collection.cancel()
    }

    @Test
    fun watchCaptorConsumesWatchBytesAndPersistsTranscripts() = runTest {
        val fixture = CaptorFixture()
        val captor =
            SODAWatchAudioCaptor(
                scope = this,
                sodaController = fixture.soda,
                settings = fixture.settings,
                contextStore = fixture.contextStore,
                textBuffer = fixture.textBuffer,
                audioBytesBuffer = fixture.bytesBuffer,
                clock = clock
            )

        fixture.assertBufferBackedCapture(this, captor, "watch")
    }

    private inner class CaptorFixture {
        val soda = FakeSodaController()
        val callSoda = FakeSodaController()
        val recordServiceManager = mockk<RecordServiceManager>(relaxed = true)
        val settings =
            mockk<CosmoSettingStore> {
                every { isVoiceMatchEnabled() } returns false
            }
        val contextStore = RecordingContextStore()
        val textBuffer = InMemoryEphemeralBuffer<Any>(16, 60_000, clock)
        val bytesBuffer = InMemoryEphemeralBuffer<ByteArray>(16, 60_000, clock)

        suspend fun assertBufferBackedCapture(
            testScope: TestScope,
            captor: AudioCaptor,
            sourceName: String
        ) {
            val bytes = byteArrayOf(1, 0, 2, 0)
            bytesBuffer.add(bytes)
            val received = mutableListOf<TranscriptionData>()
            val collection = testScope.launch {
                captor.startCapture().take(1).collect { received += it }
            }
            testScope.advanceUntilIdle()

            assertTrue(soda.started)
            assertArrayEquals(bytes, soda.audioBytes.single())

            val transcription =
                TranscriptionData(
                    text = "set a timer",
                    audioData = bytes,
                    speakerLabel = "",
                    speakerConfidenceScoreMap = emptyMap(),
                    timestampMillis = clock.millis()
                )
            soda.emit(transcription)
            testScope.advanceUntilIdle()

            assertEquals(listOf(transcription), received)
            assertEquals(1, textBuffer.size())
            assertEquals(sourceName, contextStore.events.single().sourceMetadata["captor"])
            collection.cancel()
        }
    }

    private class FakeSodaController : SodaTranscriptionController {
        private val mutableTranscriptions = MutableSharedFlow<TranscriptionData>(extraBufferCapacity = 16)
        val audioBytes = mutableListOf<ByteArray>()
        var started = false

        override val transcriptions: SharedFlow<TranscriptionData> = mutableTranscriptions

        override val directedness: SharedFlow<com.google.research.air.cosmo.lib.soda.DirectednessEvent> =
            MutableSharedFlow(extraBufferCapacity = 16)

        override fun addAudioBytes(bytes: ByteArray) {
            audioBytes += bytes.copyOf()
        }

        override fun startCapture(
            voiceMatchEnabled: Boolean,
            onEvent: (TranscriptionData) -> Unit
        ) {
            started = true
        }

        override fun stopCapture() {
            started = false
        }

        override fun flush(): ByteArray {
            val out = ByteArray(audioBytes.sumOf { it.size })
            var offset = 0
            audioBytes.forEach {
                it.copyInto(out, offset)
                offset += it.size
            }
            return out
        }

        fun emit(data: TranscriptionData) {
            mutableTranscriptions.tryEmit(data)
        }
    }

    private class RecordingContextStore : ContextStore {
        val events = mutableListOf<ContextEvent>()

        override suspend fun insert(event: ContextEvent): InsertResult {
            events += event
            return InsertResult.Stored
        }

        override suspend fun attachEmbedding(
            id: String,
            embedding: FloatArray
        ) = Unit

        override suspend fun queryByTime(
            range: LongRange,
            sources: Set<Source>?
        ): List<ContextEvent> = events

        override suspend fun queryByText(
            query: String,
            limit: Int
        ): List<ContextEvent> = events

        override suspend fun queryByEmbedding(
            vec: FloatArray,
            limit: Int
        ): List<ScoredEvent> = emptyList()

        override suspend fun delete(id: String) = Unit

        override suspend fun deleteRange(
            range: LongRange,
            sources: Set<Source>?
        ): Int = 0

        override suspend fun purgeSource(source: Source): Int = 0

        override fun events(): Flow<ContextEvent> = emptyFlow()

        override suspend fun stats(): ContextStoreStats = ContextStoreStats(emptyMap(), emptyMap())
    }

    private class FakeBudsAudioProvider : BudsAudioProvider {
        private val mutableAudioData = MutableSharedFlow<BudsAudioChunk>(extraBufferCapacity = 16)
        private val mutableState = MutableStateFlow<BudsProviderState>(BudsProviderState.Idle)
        val startedDevices = mutableListOf<BluetoothDevice>()

        override val state: StateFlow<BudsProviderState> = mutableState
        override val audioData: Flow<BudsAudioChunk> = mutableAudioData

        override suspend fun start(device: BluetoothDevice) {
            startedDevices += device
            mutableState.value = BudsProviderState.Streaming
        }

        override suspend fun stop() {
            mutableState.value = BudsProviderState.Idle
        }

        override fun shutdown() {
            mutableState.value = BudsProviderState.Idle
        }

        fun emit(bytes: ByteArray) {
            mutableAudioData.tryEmit(BudsAudioChunk(bytes))
        }
    }

}
