package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FulfillmentActionHandlerImplTest {
    @Test
    fun executeDelegatesToMatchingExecutorAndMarksActionExecuted() = runTest {
        val action = FulfillmentAction.AddTask("task-1", "Call Sam")
        val repository = ActionsRepository().apply { maybeUpdateActionsList(listOf(action), "fulfillment-1") }
        val telemetry = mockk<CosmosTelemetry>(relaxed = true)
        val executor = RecordingExecutor(result = true)
        val handler =
            FulfillmentActionHandlerImpl(
                actionsRepository = repository,
                telemetry = telemetry,
                executors = setOf(executor)
            )

        handler.execute(action)

        assertEquals(listOf(action), executor.actions)
        assertTrue(repository.executedActions.value.any { it.id == action.id })
        assertTrue(repository.currentActions.value.none { it.id == action.id })
        verify {
            telemetry.log(
                match {
                    it is TelemetryEvent.ActionExecution &&
                        it.params.getString("action_type") == "add_task" &&
                        it.params.getBoolean("succeeded")
                }
            )
        }
    }

    @Test
    fun executeMarksActionDismissedWhenNoExecutorSupportsIt() = runTest {
        val action = FulfillmentAction.Unsupported("unsupported-1", "Unsupported", "unknown_type")
        val repository = ActionsRepository().apply { maybeUpdateActionsList(listOf(action), "fulfillment-1") }
        val telemetry = mockk<CosmosTelemetry>(relaxed = true)
        val handler =
            FulfillmentActionHandlerImpl(
                actionsRepository = repository,
                telemetry = telemetry,
                executors = emptySet()
            )

        handler.execute(action)

        assertTrue(repository.userDismissedActions.value.any { it.id == action.id })
        assertTrue(repository.currentActions.value.none { it.id == action.id })
        verify {
            telemetry.log(
                match {
                    it is TelemetryEvent.ActionExecution &&
                        it.params.getString("action_type") == "unsupported" &&
                        !it.params.getBoolean("succeeded")
                }
            )
        }
    }

    private class RecordingExecutor(
        private val result: Boolean
    ) : FulfillmentActionExecutor {
        val actions = mutableListOf<FulfillmentAction>()

        override fun supports(action: FulfillmentAction): Boolean = action is FulfillmentAction.AddTask

        override suspend fun execute(action: FulfillmentAction): Boolean {
            actions += action
            return result
        }
    }
}
