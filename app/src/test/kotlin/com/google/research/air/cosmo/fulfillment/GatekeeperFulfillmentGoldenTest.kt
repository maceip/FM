package com.google.research.air.cosmo.fulfillment

import androidx.test.core.app.ApplicationProvider
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.fulfillment.FulfillmentAction.AddTimer
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingDetector
import com.google.research.air.cosmo.gatekeeping.GatekeepingInputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils
import com.google.research.air.cosmo.lib.llm.SimulationLLM
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.setting.CosmoConfiguration
import com.google.research.air.cosmo.setting.CosmoSettingSerializer
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.setting.proto.ProcessingMode
import com.google.research.air.cosmo.skills.SkillsRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Golden path: gatekeeper head/skill → settings-gated intents → queued fulfillment → action card.
 *
 * Mirrors recovered cosmo: `GatekeeperResultUtils.getIntents` → `FulfillmentTask` →
 * `FulfillmentConfigRegistry` → `FulfillmentRunner` → `ActionsRepository`.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GatekeeperFulfillmentGoldenTest {
    @Test
    fun gatekeeperTimerIntentProducesAddTimerActionOnActionDeck() = runTest {
        val gatekeeperOutput = FakeGatekeeperOutputSource()
        val actionsRepository = ActionsRepository()
        val settings = enabledRealtimeSettings()
        val skillEnablement = SkillEnablement(settings, CosmoConfiguration())
        val gatekeeperResultUtils = GatekeeperResultUtils(settings, skillEnablement)
        val skills =
            SkillsRepositoryImpl(
                context = ApplicationProvider.getApplicationContext(),
                fulfillmentActionHandler = mockk(relaxed = true),
            )
        val configRegistry = FulfillmentConfigRegistry(skills, settings, skillEnablement)
        val telemetry = mockk<CosmosTelemetry>(relaxed = true)
        val clock = Clock.fixed(Instant.parse("2026-05-19T12:00:00Z"), ZoneOffset.UTC)
        val runner =
            FulfillmentRunner(
                context = ApplicationProvider.getApplicationContext(),
                audioBuffer = InMemoryEphemeralBuffer(8, 60_000L, clock),
                screenBuffer = InMemoryEphemeralBuffer(8, 60_000L, clock),
                notificationBuffer = InMemoryEphemeralBuffer(8, 60_000L, clock),
                intentBuffer = InMemoryEphemeralBuffer(8, 60_000L, clock),
                actionsRepository = actionsRepository,
                llm = SimulationLLM(),
                settings = settings,
                clock = clock,
                telemetry = telemetry,
            )
        val orchestratorScope = CoroutineScope(coroutineContext + SupervisorJob())
        val orchestrator =
            FulfillmentOrchestratorImpl(
                runner = runner,
                gatekeeperOutput = gatekeeperOutput,
                gatekeeperResultUtils = gatekeeperResultUtils,
                configRegistry = configRegistry,
                settings = settings,
                scope = orchestratorScope,
                telemetry = telemetry,
            )

        val gatekeeperResult = goldenGatekeeperResult()
        val intents = gatekeeperResultUtils.getIntents(gatekeeperResult)
        assertTrue("Fixture must map to timer intent", intents.contains("START_TIMER_INTENT"))

        // Start queue consumer + gatekeeper collector before emitting (SharedFlow has no replay).
        advanceUntilIdle()
        gatekeeperOutput.emit(gatekeeperResult)
        advanceUntilIdle()

        val userFacing = actionsRepository.userFacingActions.value
        assertEquals(1, userFacing.size)
        val timer = userFacing.single() as AddTimer
        assertEquals("sim-timer", timer.id)
        assertEquals(600, timer.durationSeconds)
        assertEquals("Set 10 minute timer", timer.title)

        val history = actionsRepository.fulfillmentHistory.value.single()
        assertEquals(FulfillmentTriggerType.GATEKEEPER_TRIGGER, history.fulfillmentTriggerType)
        assertTrue(history.llmQuery.contains("Gatekeeper intents:"))
        assertTrue(history.llmQuery.contains("START_TIMER_INTENT"))
        assertTrue(history.llmQuery.contains(GOLDEN_GATEKEEPER_INPUT))
        assertTrue(history.llmQuery.contains("ACTION_TYPE_ADD_TIMER"))
        assertEquals(listOf(timer.id), history.actionIds)

        verify { telemetry.log(match { it is TelemetryEvent.FulfillmentRun }) }
        checkNotNull(orchestrator)
    }

    private fun goldenGatekeeperResult(): GatekeepingResult =
        GatekeepingResult(
            detectedSkills = mapOf("add_timer" to 0.91f),
            modelId = "golden_test",
            detectedBy = "audio_gatekeeper",
            triggeredFulfillment = true,
            input = GOLDEN_GATEKEEPER_INPUT,
            timestampMillis = 1_746_000_000_000L,
            inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
            detector = GatekeepingDetector.AUDIO_GATEKEEPER,
        )

    private fun enabledRealtimeSettings(): CosmoSettingStore {
        val setting =
            CosmoSettingSerializer.defaultValue
                .toBuilder()
                .setGatekeeperEnabled(true)
                .setSuggestTimerEnabled(true)
                .setProcessingMode(ProcessingMode.PROCESSING_MODE_REALTIME)
                .build()
        val store = mockk<CosmoSettingStore>(relaxed = true)
        every { store.currentSetting } returns setting
        every { store.isGatekeeperEnabled() } returns true
        every { store.isRealtimeProcessingEnabled() } returns true
        every { store.isAmbientAudioEnabled() } returns true
        every { store.isScreenContextEnabled() } returns false
        every { store.isNotificationCaptureEnabled() } returns false
        every { store.isIntentExtractionEnabled() } returns false
        every { store.isRawAudioGatekeeperEnabled() } returns true
        every { store.isVoiceMatchEnabled() } returns false
        every { store.getMaxRawAudioInFulfillmentSec() } returns 30
        every { store.isInvokeAgentQEnabled() } returns false
        return store
    }

    private class FakeGatekeeperOutputSource : GatekeeperOutputSource {
        private val mutable =
            MutableSharedFlow<GatekeepingResult>(
                replay = 1,
                extraBufferCapacity = 16,
            )
        override val results: SharedFlow<GatekeepingResult> = mutable

        suspend fun emit(result: GatekeepingResult) {
            mutable.emit(result)
        }
    }

    private companion object {
        const val GOLDEN_GATEKEEPER_INPUT = "Ambient audio: set a timer for 10 minutes"
    }
}
