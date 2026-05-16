package com.google.research.air.cosmo.presence

import com.google.research.air.cosmo.fulfillment.ActionsRepository
import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingDetector
import com.google.research.air.cosmo.gatekeeping.GatekeepingInputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresenceControllerTest {
    @Test
    fun actionsRepositoryDrivesReadyPresenceState() = runTest {
        val repository = ActionsRepository()
        val controller = PresenceController(repository, FakeGatekeeperOutputSource(), testScope(testScheduler))
        controller.start()

        repository.maybeUpdateActionsList(
            actions = listOf(FulfillmentAction.AddTimer("timer-1", "Set timer", 600)),
            fulfillmentId = "fulfillment-1"
        )
        advanceUntilIdle()

        val state = controller.state.value as PresenceState.Ready
        assertEquals("fulfillment-1", state.taskId)
        assertEquals("timer-1", state.cards.single().id)
        assertTrue(state.cards.single().payload is CardPayload.CalendarTime)
        assertEquals("review:timer-1", state.primaryAction?.id)
    }

    @Test
    fun retiringActionsClearsReadyPresenceState() = runTest {
        val repository = ActionsRepository()
        val controller = PresenceController(repository, FakeGatekeeperOutputSource(), testScope(testScheduler))
        controller.start()
        repository.maybeUpdateActionsList(
            actions = listOf(FulfillmentAction.AddTimer("timer-1", "Set timer", 600)),
            fulfillmentId = "fulfillment-1"
        )
        advanceUntilIdle()

        repository.markActionAsExecuted("timer-1")
        advanceUntilIdle()

        assertTrue(controller.state.value is PresenceState.Hidden)
    }

    @Test
    fun triggeredGatekeeperOutputDrivesWorkingPresenceState() = runTest {
        val gatekeeper = FakeGatekeeperOutputSource()
        val controller = PresenceController(ActionsRepository(), gatekeeper, testScope(testScheduler))
        controller.start()
        advanceUntilIdle()

        gatekeeper.emit(gatekeeperResult(triggeredFulfillment = true))
        advanceUntilIdle()

        val state = controller.state.value as PresenceState.Working
        assertEquals("gatekeeper-1234", state.taskId)
        assertEquals(WorkPhase.PROCESSING, state.phase)
        assertEquals("add_timer 90%", state.pillText)
    }

    @Test
    fun actionablePresenceOverridesGatekeeperWorkingState() = runTest {
        val repository = ActionsRepository()
        val gatekeeper = FakeGatekeeperOutputSource()
        val controller = PresenceController(repository, gatekeeper, testScope(testScheduler))
        controller.start()
        advanceUntilIdle()
        gatekeeper.emit(gatekeeperResult(triggeredFulfillment = true))
        advanceUntilIdle()

        repository.maybeUpdateActionsList(
            actions = listOf(FulfillmentAction.WebSearch("search-1", "Search", "weather")),
            fulfillmentId = "fulfillment-2"
        )
        advanceUntilIdle()

        val state = controller.state.value as PresenceState.Ready
        assertEquals("fulfillment-2", state.taskId)
        assertTrue(state.cards.single().payload is CardPayload.Browser)
    }

    private fun gatekeeperResult(triggeredFulfillment: Boolean): GatekeepingResult =
        GatekeepingResult(
            detectedSkills = mapOf("add_timer" to 0.9f),
            modelId = "regex",
            detectedBy = "regex",
            triggeredFulfillment = triggeredFulfillment,
            input = "User query: set a timer",
            timestampMillis = 1234L,
            inputSource = GatekeepingInputSource.USER_QUERY,
            detector = GatekeepingDetector.REGEX
        )

    private fun testScope(scheduler: TestCoroutineScheduler): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(scheduler))

    private class FakeGatekeeperOutputSource : GatekeeperOutputSource {
        private val mutable = MutableSharedFlow<GatekeepingResult>(extraBufferCapacity = 16)
        override val results: SharedFlow<GatekeepingResult> = mutable

        suspend fun emit(result: GatekeepingResult) {
            mutable.emit(result)
        }
    }
}
