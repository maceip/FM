package com.google.research.air.cosmo.presence.overlay

import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.PresenceMode
import com.google.research.air.cosmo.presence.PresenceState
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceOverlayBackStackTest {
    @Test
    fun readyPresenceCreatesCardStackBackStack() {
        val stack = PresenceOverlayBackStack()
        val state = stack.dispatch(PresenceOverlayEvent.PresenceChanged(readyState()))

        assertEquals(PresenceOverlayRoute.CARD_STACK, state.route)
        assertEquals(
            listOf(PresenceOverlayRoute.PARKED, PresenceOverlayRoute.EXPANDED_TAB, PresenceOverlayRoute.CARD_STACK),
            state.backStack
        )
    }

    @Test
    fun backNavigatesCardsToTabToParkedAndKeepsParkedVisible() {
        val stack = PresenceOverlayBackStack()
        stack.dispatch(PresenceOverlayEvent.PresenceChanged(readyState()))

        assertEquals(PresenceOverlayRoute.EXPANDED_TAB, stack.dispatch(PresenceOverlayEvent.Back).route)
        assertEquals(PresenceOverlayRoute.PARKED, stack.dispatch(PresenceOverlayEvent.Back).route)
        assertEquals(PresenceOverlayRoute.PARKED, stack.dispatch(PresenceOverlayEvent.Back).route)
    }

    @Test
    fun predictiveBackProgressResetsOnCancelAndCommit() {
        val stack = PresenceOverlayBackStack()
        stack.dispatch(PresenceOverlayEvent.PresenceChanged(readyState()))

        assertEquals(0.02f, stack.dispatch(PresenceOverlayEvent.BackStarted).backProgress, 0.001f)
        assertEquals(0.7f, stack.dispatch(PresenceOverlayEvent.BackProgressed(0.7f)).backProgress, 0.001f)
        assertEquals(0f, stack.dispatch(PresenceOverlayEvent.BackCancelled).backProgress, 0.001f)

        stack.dispatch(PresenceOverlayEvent.BackProgressed(0.8f))
        val committed = stack.dispatch(PresenceOverlayEvent.Back)
        assertEquals(PresenceOverlayRoute.EXPANDED_TAB, committed.route)
        assertEquals(0f, committed.backProgress, 0.001f)
    }

    @Test
    fun permissionDeniedRoutesToPermissionRequiredInsteadOfVisibleOverlay() {
        val stack = PresenceOverlayBackStack()
        stack.dispatch(PresenceOverlayEvent.PermissionChanged(false))
        val state = stack.dispatch(PresenceOverlayEvent.PresenceChanged(readyState()))

        assertEquals(PresenceOverlayRoute.PERMISSION_REQUIRED, state.route)
        assertEquals(false, state.canDrawOverlays)
    }

    @Test
    fun permissionGrantReplaysCurrentPresenceState() {
        val stack = PresenceOverlayBackStack()
        stack.dispatch(PresenceOverlayEvent.PermissionChanged(false))
        stack.dispatch(PresenceOverlayEvent.PresenceChanged(readyState()))
        val state = stack.dispatch(PresenceOverlayEvent.PermissionChanged(true))

        assertEquals(PresenceOverlayRoute.CARD_STACK, state.route)
        assertEquals(true, state.canDrawOverlays)
    }

    @Test
    fun parkedPresenceDoesNotForceFocusableExpandedState() {
        val stack = PresenceOverlayBackStack()
        val state =
            stack.dispatch(
                PresenceOverlayEvent.PresenceChanged(
                    PresenceState.Parked(PresenceMode.ORB, OrbActivity.LISTENING)
                )
            )

        assertEquals(PresenceOverlayRoute.PARKED, state.route)
        assertEquals(listOf(PresenceOverlayRoute.PARKED), state.backStack)
    }

    private fun readyState(): PresenceState.Ready =
        PresenceState.Ready(
            taskId = "task",
            cards = listOf(PresenceCard("card", CardPayload.Browser(query = "test")))
        )
}
