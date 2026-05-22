package com.google.research.air.cosmo.gatekeeping

import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.gatekeeping.backend.AudioGatekeepingBackendSelector
import com.google.research.air.cosmo.gatekeeping.backend.CpuRecoveryBackend
import com.google.research.air.cosmo.lib.soda.SodaTranscriptionController
import com.google.research.air.cosmo.setting.CosmoSettingStore
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GatekeeperPublishDebugTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_746_000_000_000L), ZoneOffset.UTC)

    @Test
    fun publishDebugClassification_emitsToResultsFlow() =
        runTest {
            val settings = mockk<CosmoSettingStore>(relaxed = true)
            every { settings.isGatekeeperEnabled() } returns true
            val soda = mockk<SodaTranscriptionController>(relaxed = true)
            every { soda.transcriptions } returns MutableSharedFlow(extraBufferCapacity = 4)
            every { soda.directedness } returns MutableSharedFlow(extraBufferCapacity = 4)
            val gatekeeper =
                Gatekeeper(
                    ambientAudioReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    ambientAudioBytesReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    budsAudioReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    deviceAudioReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    watchAudioReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    screenReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    notificationReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    locationReader = InMemoryEphemeralBuffer(4, 60_000L, clock),
                    ambientSodaController = soda,
                    backendSelector = mockk<AudioGatekeepingBackendSelector>(relaxed = true),
                    cpuRecoveryBackend = mockk<CpuRecoveryBackend>(relaxed = true),
                    settings = settings,
                    scope = this,
                    logger = mockk(relaxed = true),
                )
            gatekeeper.start()

            val expected =
                GatekeepingResult(
                    detectedSkills = mapOf("add_timer" to 0.9f),
                    modelId = "debug_publish",
                    detectedBy = "debug",
                    triggeredFulfillment = true,
                    input = "Ambient audio: set a timer for 10 minutes",
                )

            val collected =
                async {
                    gatekeeper.results.first { it.modelId == "debug_publish" }
                }
            gatekeeper.publishDebugClassification(expected)
            advanceUntilIdle()

            val result = collected.await()
            assertEquals(expected.detectedSkills, result.detectedSkills)
            assertTrue(result.triggeredFulfillment)
            gatekeeper.close()
        }
}
