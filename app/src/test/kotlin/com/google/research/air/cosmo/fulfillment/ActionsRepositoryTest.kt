package com.google.research.air.cosmo.fulfillment

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionsRepositoryTest {
    private fun repo() = ActionsRepository()

    @Test
    fun `addFulfillmentHistory emits and trims to last 100`() = runTest {
        val r = repo()
        repeat(120) { i ->
            r.addFulfillmentHistory(FulfillmentDebugInfo("id$i", i.toLong(), "", "", 0L, 0, 0, 0, FulfillmentTriggerType.USER_QUERY))
        }
        r.fulfillmentHistory.test {
            val list = awaitItem()
            assertEquals(100, list.size)
            assertEquals("id119", list.last().fulfillmentId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `maybeUpdateActionsList moves removed to cosmoRemoved and keeps active in current`() = runTest {
        val r = repo()
        val a1 = FulfillmentAction.AddTask("1", "Task 1")
        val a2 = FulfillmentAction.AddTimer("2", "Timer", 60)
        val removed = FulfillmentAction.RemovedAction("1", "Task 1")
        r.maybeUpdateActionsList(listOf(a1, a2, removed), "f1")
        r.currentActions.test {
            val current = awaitItem()
            assertEquals(1, current.size)
            assertEquals("2", current.first().id)
            cancelAndIgnoreRemainingEvents()
        }
        r.cosmoRemovedActions.test {
            assertTrue(awaitItem().any { it.id == "1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markActionAsExecuted moves to executedActions`() = runTest {
        val r = repo()
        val a = FulfillmentAction.AddTask("e1", "Execute me")
        r.maybeUpdateActionsList(listOf(a), "f2")
        r.markActionAsExecuted("e1")
        r.executedActions.test {
            assertTrue(awaitItem().any { it.id == "e1" && it.status == FulfillmentAction.Status.EXECUTED })
            cancelAndIgnoreRemainingEvents()
        }
        r.currentActions.test {
            assertTrue(awaitItem().none { it.id == "e1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markActionAsDismissed moves to userDismissedActions`() = runTest {
        val r = repo()
        val a = FulfillmentAction.AddTask("d1", "Dismiss me")
        r.maybeUpdateActionsList(listOf(a), "f3")
        r.markActionAsDismissed("d1")
        r.userDismissedActions.test {
            assertTrue(awaitItem().any { it.id == "d1" && it.status == FulfillmentAction.Status.DISMISSED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasNewActions emits when active actions added`() = runTest {
        val r = repo()
        r.hasNewActions.test {
            r.maybeUpdateActionsList(listOf(FulfillmentAction.AddTask("n1", "New")), "f4")
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearAllActions empties current and userFacing`() = runTest {
        val r = repo()
        r.maybeUpdateActionsList(listOf(FulfillmentAction.AddTask("c1", "Clear")), "f5")
        r.clearAllActions()
        r.currentActions.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        r.userFacingActions.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFulfillmentDebugInfo returns last by id`() {
        val r = repo()
        val info = FulfillmentDebugInfo("g1", 1L, "q", "res", 10L, 1, 0, 0, FulfillmentTriggerType.GATEKEEPER_TRIGGER)
        r.addFulfillmentHistory(info)
        assertEquals(info, r.getFulfillmentDebugInfo("g1"))
    }

    @Test
    fun `listFulfillmentHistory filters by id`() {
        val r = repo()
        r.addFulfillmentHistory(FulfillmentDebugInfo("h1", 1L, "", "", 0L, 0, 0, 0, FulfillmentTriggerType.USER_QUERY))
        r.addFulfillmentHistory(FulfillmentDebugInfo("h2", 2L, "", "", 0L, 0, 0, 0, FulfillmentTriggerType.USER_QUERY))
        assertEquals(1, r.listFulfillmentHistory("h1").size)
    }

    @Test
    fun `clearAllDebugInfos empties history`() {
        val r = repo()
        r.addFulfillmentHistory(FulfillmentDebugInfo("clr", 0L, "", "", 0L, 0, 0, 0, FulfillmentTriggerType.USER_QUERY))
        r.clearAllDebugInfos()
        assertTrue(r.fulfillmentHistory.value.isEmpty())
    }

    @Test
    fun `maybeUpdateActionsList dedupes by id on re-add`() = runTest {
        val r = repo()
        val a = FulfillmentAction.AddTask("dup", "Dup")
        r.maybeUpdateActionsList(listOf(a), "f6")
        r.maybeUpdateActionsList(listOf(a.copy(title = "Updated")), "f6")
        r.currentActions.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Updated", list.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
