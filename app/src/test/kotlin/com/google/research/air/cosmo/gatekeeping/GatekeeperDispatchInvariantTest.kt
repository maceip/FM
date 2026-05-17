package com.google.research.air.cosmo.gatekeeping

import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import com.google.research.air.cosmo.gatekeeping.backend.AudioGatekeepingBackend
import com.google.research.air.cosmo.gatekeeping.backend.AudioGatekeepingBackendSelector
import com.google.research.air.cosmo.gatekeeping.backend.AudioInput
import com.google.research.air.cosmo.gatekeeping.backend.BackendId
import com.google.research.air.cosmo.gatekeeping.backend.CpuRecoveryBackend
import com.google.research.air.cosmo.lib.soda.DirectednessEvent
import com.google.research.air.cosmo.lib.soda.SodaTranscriptionController
import com.google.research.air.cosmo.lib.soda.TranscriptionData
import com.google.research.air.cosmo.setting.CosmoSettingStore
import io.mockk.every
import io.mockk.mockk
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GatekeeperDispatchInvariantTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @Test
    fun whenRawAudioEnabledAndRealtimeOnAmbientTextDoesNotInvokeBertModel() = runTest {
        val bert = CountingTextModel()
        val harness = harness(bert = bert, rawAudioEnabled = true, realtimeEnabled = true)

        harness.gatekeeper.start()
        harness.ambientAudio.add(transcription("Set a timer"))
        advanceUntilIdle()

        assertEquals(0, bert.textCalls)
        harness.gatekeeper.close()
    }

    @Test
    fun whenRawAudioDisabledAmbientTextInvokesBertModel() = runTest {
        val bert = CountingTextModel()
        val harness = harness(bert = bert, rawAudioEnabled = false, realtimeEnabled = true)

        harness.gatekeeper.start()
        harness.ambientAudio.add(transcription("Set a timer"))
        advanceUntilIdle()

        assertEquals(1, bert.textCalls)
        harness.gatekeeper.close()
    }

    @Test
    fun whenRealtimeOffAmbientTextInvokesBertModel() = runTest {
        val bert = CountingTextModel()
        val harness = harness(bert = bert, rawAudioEnabled = true, realtimeEnabled = false)

        harness.gatekeeper.start()
        harness.ambientAudio.add(transcription("Set a timer"))
        advanceUntilIdle()

        assertEquals(1, bert.textCalls)
        harness.gatekeeper.close()
    }

    @Test
    fun whenMagicMicDirectednessFiredAmbientTextInvokesBertModel() = runTest {
        val bert = CountingTextModel()
        val harness = harness(bert = bert, rawAudioEnabled = true, realtimeEnabled = true)

        harness.gatekeeper.start()
        advanceUntilIdle()
        harness.soda.emitDirectedness(DirectednessEvent(ContextSource.AMBIENT_AUDIO, clock.instant()))
        harness.ambientAudio.add(transcription("Set a timer"))
        advanceUntilIdle()

        assertEquals(1, bert.textCalls)
        harness.gatekeeper.close()
    }

    @Test
    fun whenMagicMicDirectednessFiresAfterTranscriptWindowAmbientTextDoesNotInvokeBertModel() = runTest {
        val bert = CountingTextModel()
        val harness = harness(bert = bert, rawAudioEnabled = true, realtimeEnabled = true)

        harness.gatekeeper.start()
        advanceUntilIdle()
        harness.soda.emitDirectedness(
            DirectednessEvent(ContextSource.AMBIENT_AUDIO, clock.instant().plusMillis(1_000))
        )
        harness.ambientAudio.add(transcription("Set a timer"))
        advanceUntilIdle()

        assertEquals(0, bert.textCalls)
        harness.gatekeeper.close()
    }

    @Test
    fun whenConformerEdgeTpuBackendIsActiveAmbientTextDoesNotInvokeBertModel() = runTest {
        val bert = CountingTextModel()
        val harness =
            harness(
                bert = bert,
                rawAudioEnabled = false,
                realtimeEnabled = false,
                activeBackend = BackendId.ConformerEdgeTpu
            )

        harness.gatekeeper.start()
        harness.ambientAudio.add(transcription("Set a timer"))
        advanceUntilIdle()

        // C1 + C8/C9: when the EdgeTPU Conformer backend is active, the audio bytes path owns
        // the dispatch — the text path must skip even when raw-audio settings are off,
        // otherwise the same transcript would be classified twice (audio Conformer + text BERT).
        assertEquals(0, bert.textCalls)
        harness.gatekeeper.close()
    }

    @Test
    fun audioBytesInvokesAudioModelOnlyWhenConformerEdgeTpuBackendIsActive() = runTest {
        var audioBackendRuns = 0
        val makeModel: () -> AudioGatekeepingModel = {
            AudioGatekeepingModel(
                ConformerEncoderImpl(
                    loadAssetBuffer = fakeBufferLoader(
                        mapOf(
                            GatekeeperModelIds.AUDIO_FRONTEND to byteArrayOf(1),
                            GatekeeperModelIds.AUDIO_GATEKEEPER to byteArrayOf(2)
                        )
                    ),
                    runtimeFactory = { _, _ ->
                        object : SplitConformerRuntime {
                            override fun run(
                                audioInputTensor: Array<FloatArray>,
                                spectrogramBuffer: ByteBuffer,
                                paddingMaskTensor: Array<BooleanArray>,
                                intentOutputBuffer: Array<FloatArray>
                            ) {
                                audioBackendRuns += 1
                                intentOutputBuffer[0][0] = 0.91f
                            }

                            override fun close() = Unit
                        }
                    }
                )
            )
        }

        // CPU-recovery backend (today's default) — audio bytes path must NOT invoke audio model.
        val cpuHarness =
            harness(
                audioModel = makeModel(),
                rawAudioEnabled = true,
                realtimeEnabled = true,
                activeBackend = BackendId.CpuRecovery
            )
        cpuHarness.gatekeeper.start()
        cpuHarness.ambientAudioBytes.add(byteArrayOf(1, 0, 2, 0))
        advanceUntilIdle()
        assertEquals(0, audioBackendRuns)
        cpuHarness.gatekeeper.close()

        // EdgeTPU backend active — audio model runs once.
        audioBackendRuns = 0
        val edgeTpuHarness =
            harness(
                audioModel = makeModel(),
                rawAudioEnabled = true,
                realtimeEnabled = true,
                activeBackend = BackendId.ConformerEdgeTpu
            )
        edgeTpuHarness.gatekeeper.start()
        edgeTpuHarness.ambientAudioBytes.add(byteArrayOf(1, 0, 2, 0))
        advanceUntilIdle()
        assertEquals(1, audioBackendRuns)
        edgeTpuHarness.gatekeeper.close()
    }

    private fun TestScope.harness(
        bert: GatekeepingModel = CountingTextModel(),
        audioModel: AudioGatekeepingModel = audioModel(),
        rawAudioEnabled: Boolean,
        realtimeEnabled: Boolean,
        activeBackend: BackendId = BackendId.CpuRecovery
    ): Harness {
        val settings = mockk<CosmoSettingStore>(relaxed = true)
        every { settings.isGatekeeperEnabled() } returns true
        every { settings.isRawAudioGatekeeperEnabled() } returns rawAudioEnabled
        every { settings.isRealtimeProcessingEnabled() } returns realtimeEnabled
        val ambientAudio = anyBuffer()
        val ambientAudioBytes = byteBuffer()
        val soda = FakeSodaController()
        val cpuRecoveryBackend = CpuRecoveryBackend(bert)
        val selectedBackend: AudioGatekeepingBackend =
            when (activeBackend) {
                BackendId.CpuRecovery -> cpuRecoveryBackend
                BackendId.ConformerEdgeTpu -> testAudioBackend(BackendId.ConformerEdgeTpu, audioModel)
                BackendId.Inherent -> testAudioBackend(BackendId.Inherent, audioModel)
            }
        val backendSelector = mockk<AudioGatekeepingBackendSelector>()
        every { backendSelector.select() } returns selectedBackend
        val gatekeeper =
            Gatekeeper(
                ambientAudioReader = ambientAudio,
                ambientAudioBytesReader = ambientAudioBytes,
                budsAudioReader = anyBuffer(),
                deviceAudioReader = anyBuffer(),
                watchAudioReader = anyBuffer(),
                screenReader = anyBuffer(),
                notificationReader = anyBuffer(),
                locationReader = anyBuffer(),
                ambientSodaController = soda,
                backendSelector = backendSelector,
                cpuRecoveryBackend = cpuRecoveryBackend,
                settings = settings,
                scope = this,
                logger = object : GatekeeperLogger {
                    override fun logGatekeeperRun(result: GatekeepingResult) = Unit
                }
            )
        return Harness(gatekeeper, ambientAudio, ambientAudioBytes, soda)
    }

    private fun testAudioBackend(id: BackendId, audioModel: AudioGatekeepingModel): AudioGatekeepingBackend =
        object : AudioGatekeepingBackend {
            override val backendId = id
            override val isAvailable = true
            override suspend fun classify(input: AudioInput): GatekeepingResult? {
                // C1 mutex: audio-path backends consume RawPcm only; ambient text under
                // these backends is owned by the audio bytes dispatch and skipped here.
                val pcm = (input as? AudioInput.RawPcm) ?: return null
                return audioModel.predict(pcm.pcm)
            }
            override fun close() = Unit
        }

    private fun audioModel(): AudioGatekeepingModel =
        AudioGatekeepingModel(
            ConformerEncoderImpl(
                loadAssetBuffer = fakeBufferLoader(
                    mapOf(
                        GatekeeperModelIds.AUDIO_FRONTEND to byteArrayOf(1),
                        GatekeeperModelIds.AUDIO_GATEKEEPER to byteArrayOf(2)
                    )
                ),
                runtimeFactory = { _, _ ->
                    object : SplitConformerRuntime {
                        override fun run(
                            audioInputTensor: Array<FloatArray>,
                            spectrogramBuffer: ByteBuffer,
                            paddingMaskTensor: Array<BooleanArray>,
                            intentOutputBuffer: Array<FloatArray>
                        ) {
                            intentOutputBuffer[0][0] = 0.91f
                        }

                        override fun close() = Unit
                    }
                }
            )
        )

    private fun transcription(text: String): TranscriptionData =
        TranscriptionData(
            text = text,
            audioData = byteArrayOf(),
            speakerLabel = "",
            speakerConfidenceScoreMap = emptyMap(),
            timestampMillis = clock.millis()
        )

    private fun anyBuffer(): InMemoryEphemeralBuffer<Any> = InMemoryEphemeralBuffer(16, 60_000, clock)

    private fun byteBuffer(): InMemoryEphemeralBuffer<ByteArray> = InMemoryEphemeralBuffer(16, 60_000, clock)

    private data class Harness(
        val gatekeeper: Gatekeeper,
        val ambientAudio: InMemoryEphemeralBuffer<Any>,
        val ambientAudioBytes: InMemoryEphemeralBuffer<ByteArray>,
        val soda: FakeSodaController
    )

    private class FakeSodaController : SodaTranscriptionController {
        override val transcriptions: SharedFlow<TranscriptionData> =
            MutableSharedFlow(extraBufferCapacity = 16)
        override val directedness: MutableSharedFlow<DirectednessEvent> =
            MutableSharedFlow(replay = 1, extraBufferCapacity = 16)

        override fun addAudioBytes(bytes: ByteArray) = Unit

        override fun startCapture(
            voiceMatchEnabled: Boolean,
            onEvent: (TranscriptionData) -> Unit
        ) = Unit

        override fun stopCapture() = Unit

        override fun flush(): ByteArray = byteArrayOf()

        fun emitDirectedness(event: DirectednessEvent) {
            directedness.tryEmit(event)
        }
    }

    private class CountingTextModel : GatekeepingModel {
        var textCalls = 0

        override suspend fun predict(input: String): GatekeepingResult {
            textCalls += 1
            return GatekeepingResult(
                detectedSkills = emptyMap(),
                modelId = "fake",
                detectedBy = "none",
                triggeredFulfillment = false,
                input = input
            )
        }
    }

    private fun fakeBufferLoader(assets: Map<String, ByteArray>): (String) -> ByteBuffer? = { id ->
        assets[id]?.let { ByteBuffer.wrap(it) }
    }
}
