package com.google.research.air.cosmo.contracts

import com.google.research.air.cosmo.ephemeralbuffer.DefaultEphemeralBufferRegistry
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferId
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import com.google.research.air.cosmo.ephemeralbuffer.EvictionPolicy
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.fulfillment.ActionsRepository
import com.google.research.air.cosmo.fulfillment.ActionsRepositoryContract
import com.google.research.air.cosmo.gatekeeping.GatekeepingDetector
import com.google.research.air.cosmo.gatekeeping.GatekeepingInputSource
import com.google.research.air.cosmo.gatekeeping.RegexGatekeepingModelV0
import com.google.research.air.cosmo.lib.llm.LLM
import com.google.research.air.cosmo.lib.llm.LLMRouteReason
import com.google.research.air.cosmo.lib.llm.LLMRouteState
import com.google.research.air.cosmo.lib.llm.ModelPath
import com.google.research.air.cosmo.lib.llm.NetworkAwareLLM
import com.google.research.air.cosmo.lib.llm.NetworkPolicy
import com.google.research.air.cosmo.lib.llm.NetworkPolicyProvider
import com.google.research.air.cosmo.lib.llm.PromptPart
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContractsTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_746_000_000_000L), ZoneOffset.UTC)

    @Test
    fun inMemoryBufferSatisfiesEphemeralBufferContract() {
        val buffer: EphemeralBuffer<String> = InMemoryEphemeralBuffer(8, 60_000L, clock)

        assertTrue(buffer.append("screen text", clock.millis()))
        assertEquals("screen text", buffer.snapshot().single().value)
    }

    @Test
    fun registryExposesTheSameProductionBufferBySourceId() {
        val screen = objectBuffer()
        val registry: EphemeralBufferRegistry =
            DefaultEphemeralBufferRegistry(
                ambientAudio = objectBuffer(),
                ambientAudioBytes = InMemoryEphemeralBuffer(8, 60_000L, clock),
                ambientAudioText = objectBuffer(),
                budsAudio = objectBuffer(),
                budsAudioBytes = InMemoryEphemeralBuffer(8, 60_000L, clock),
                budsAudioText = objectBuffer(),
                deviceAudio = objectBuffer(),
                deviceAudioBytes = InMemoryEphemeralBuffer(8, 60_000L, clock),
                deviceAudioText = objectBuffer(),
                watchAudio = objectBuffer(),
                watchAudioBytes = InMemoryEphemeralBuffer(8, 60_000L, clock),
                watchAudioText = objectBuffer(),
                notification = objectBuffer(),
                notificationText = objectBuffer(),
                screen = screen,
                screenText = screen,
                screenInteraction = objectBuffer(),
                imageScreen = objectBuffer(),
                camera = objectBuffer(),
                cameraJpegBytes = InMemoryEphemeralBuffer(8, 60_000L, clock),
                deviceCamera = objectBuffer(),
                deviceCameraJpegBytes = InMemoryEphemeralBuffer(8, 60_000L, clock),
                glassesCamera = objectBuffer(),
                intent = objectBuffer(),
                location = objectBuffer(),
                locationText = objectBuffer()
            )

        registry.writer(EphemeralBufferId.SCREEN_TEXT).append("visible text", clock.millis())

        assertEquals("visible text", registry.reader(EphemeralBufferId.SCREEN_TEXT).snapshot().single().value)
        assertEquals(screen.snapshot(), registry.reader(EphemeralBufferId.SCREEN_TEXT).snapshot())
        assertTrue(registry.sourceIds().containsAll(EphemeralBufferId.entries))
    }

    @Test
    fun bufferFlushPublishesEvictedSnapshot() {
        val buffer = InMemoryEphemeralBuffer<String>(10, 60_000L, clock)
        buffer.updateEvictionPolicy(EvictionPolicy(maxEntries = 2, maxEntryAgeMillis = 60_000L))

        buffer.append("one", clock.millis())
        buffer.append("two", clock.millis())
        buffer.append("three", clock.millis())

        val flushed = buffer.flush()

        assertEquals(listOf("two", "three"), flushed.map { it.value })
        assertEquals(flushed, buffer.snapshots().value)
    }

    @Test
    fun actionsRepositorySatisfiesRepositoryContract() {
        val repository: ActionsRepositoryContract = ActionsRepository()

        assertTrue(repository.currentActions.value.isEmpty())
        assertTrue(repository.fulfillmentHistory.value.isEmpty())
    }

    @Test
    fun gatekeeperResultFreezesDetectorSkillScoresAndInputSource() = runTest {
        val result = RegexGatekeepingModelV0().predict("Screen: define polymorphism")

        assertEquals(GatekeepingInputSource.SCREEN, result.inputSource)
        assertEquals(GatekeepingDetector.REGEX, result.detector)
        assertEquals(GatekeepingDetector.REGEX, result.skillScores.single().detector)
        assertEquals("define_term", result.skillScores.single().skillId)
    }

    @Test
    fun networkAwareLlmSatisfiesRouteStateContract() = runTest {
        val llm =
            NetworkAwareLLM(
                local = FixedLLM("local"),
                remote = FixedLLM("remote"),
                networkPolicyProvider =
                    FixedNetworkPolicyProvider(
                        NetworkPolicy(
                            available = true,
                            validated = true,
                            unmetered = true,
                            vpnActive = false,
                            downstreamKbps = 2_000,
                            upstreamKbps = 512
                        )
                    )
            )
        val routeState: LLMRouteState = llm

        assertEquals("local", llm.generateText("hello"))
        assertEquals(ModelPath.LOCAL_LLM, routeState.activePath.value)
        assertEquals(LLMRouteReason.LOCAL_ELIGIBLE, routeState.routeDecision.value.reason)
    }

    private fun objectBuffer(): InMemoryEphemeralBuffer<Any> = InMemoryEphemeralBuffer(8, 60_000L, clock)

    private class FixedNetworkPolicyProvider(
        private val policy: NetworkPolicy
    ) : NetworkPolicyProvider {
        override fun currentNetworkPolicy(): NetworkPolicy = policy
    }

    private class FixedLLM(
        private val response: String
    ) : LLM {
        override suspend fun inference(
            prompt: String,
            onToken: (String) -> Unit
        ): String {
            onToken(response)
            return response
        }

        override suspend fun inference(
            parts: List<PromptPart>,
            onToken: (String) -> Unit
        ): String {
            onToken(response)
            return response
        }
    }
}
