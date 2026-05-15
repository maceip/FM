package com.google.research.air.cosmo.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PresenceStateTransitionTest {

    @Test
    fun `Hidden is a singleton`() {
        val a: PresenceState = PresenceState.Hidden
        val b: PresenceState = PresenceState.Hidden
        assertTrue(a === b)
    }

    @Test
    fun `Parked holds mode and activity`() {
        val state = PresenceState.Parked(PresenceMode.ORB, OrbActivity.LISTENING)
        assertEquals(PresenceMode.ORB, state.mode)
        assertEquals(OrbActivity.LISTENING, state.activity)
    }

    @Test
    fun `Working holds task and phase`() {
        val state = PresenceState.Working("task-1", WorkPhase.SYNCING, "Finding restaurants...")
        assertEquals("task-1", state.taskId)
        assertEquals(WorkPhase.SYNCING, state.phase)
        assertEquals("Finding restaurants...", state.pillText)
    }

    @Test
    fun `NeedsInput holds prompt`() {
        val state = PresenceState.NeedsInput("task-2", "Which restaurant?")
        assertEquals("Which restaurant?", state.prompt)
    }

    @Test
    fun `Ready holds cards list`() {
        val cards = listOf(
            PresenceCard("c1", CardPayload.Browser(query = "thai food"), listOf(CardAction("send", "Send")))
        )
        val state = PresenceState.Ready("task-3", cards, primaryAction = CardAction("send", "Send"))
        assertEquals(1, state.cards.size)
        assertEquals("send", state.primaryAction?.id)
    }

    @Test
    fun `Dismissed holds expiry instant`() {
        val until = Instant.now().plusSeconds(3600)
        val state = PresenceState.Dismissed(until)
        assertTrue(state.until.isAfter(Instant.now()))
    }

    @Test
    fun `state transition sequence types correctly`() {
        val sequence: List<PresenceState> = listOf(
            PresenceState.Hidden,
            PresenceState.Parked(PresenceMode.TAB, OrbActivity.IDLE),
            PresenceState.Working("t1", WorkPhase.STARTED),
            PresenceState.NeedsInput("t1", "Confirm?"),
            PresenceState.Ready("t1", emptyList()),
            PresenceState.Dismissed(Instant.now())
        )
        assertEquals(6, sequence.size)
        assertTrue(sequence[0] is PresenceState.Hidden)
        assertTrue(sequence[1] is PresenceState.Parked)
        assertTrue(sequence[2] is PresenceState.Working)
        assertTrue(sequence[3] is PresenceState.NeedsInput)
        assertTrue(sequence[4] is PresenceState.Ready)
        assertTrue(sequence[5] is PresenceState.Dismissed)
    }
}
