package com.google.research.air.cosmo.presence.overlay

import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.PresenceState
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceOverlayWindowControllerTest {
    @Test
    fun deniedPermissionNeverCallsShow() {
        val host = FakeHost()
        val controller = controller(host = host, granted = false)

        val state = controller.renderPresence(readyState())

        assertEquals(PresenceOverlayRoute.PERMISSION_REQUIRED, state.route)
        assertEquals(0, host.showCount)
        assertEquals(1, host.hideCount)
    }

    @Test
    fun hiddenPresenceRemovesVisibleHost() {
        val host = FakeHost()
        val controller = controller(host = host)

        controller.renderPresence(readyState())
        controller.renderPresence(PresenceState.Hidden)

        assertEquals(1, host.showCount)
        assertEquals(1, host.hideCount)
    }

    @Test
    fun windowFailureDoesNotThrowAndMovesToErrorState() {
        val host = FakeHost(showResult = Result.failure(SecurityException("overlay denied")))
        val controller = controller(host = host)

        val state = controller.renderPresence(readyState())

        assertEquals(PresenceOverlayRoute.ERROR, state.route)
        assertEquals("overlay denied", state.lastError)
        assertEquals(1, host.hideCount)
    }

    @Test
    fun backEventUpdatesVisibleHostLayout() {
        val host = FakeHost()
        val controller = controller(host = host)
        controller.renderPresence(readyState())

        val state = controller.dispatch(PresenceOverlayEvent.Back)

        assertEquals(PresenceOverlayRoute.EXPANDED_TAB, state.route)
        assertEquals(1, host.showCount)
        assertEquals(1, host.updateCount)
    }

    @Test
    fun destroyAlwaysAttemptsHide() {
        val host = FakeHost()
        val controller = controller(host = host)
        controller.renderPresence(readyState())

        controller.destroy()

        assertEquals(1, host.hideCount)
    }

    private fun controller(
        host: FakeHost,
        granted: Boolean = true
    ): PresenceOverlayWindowController =
        PresenceOverlayWindowController(
            backStack = PresenceOverlayBackStack(),
            permissionChecker = FakePermissionChecker(granted),
            host = host
        )

    private fun readyState(): PresenceState.Ready =
        PresenceState.Ready(
            taskId = "task",
            cards = listOf(PresenceCard("card", CardPayload.Browser(query = "test")))
        )

    private class FakePermissionChecker(private val granted: Boolean) : PresenceOverlayPermissionChecker {
        override fun canDrawOverlays(): Boolean = granted
    }

    private class FakeHost(
        private val showResult: Result<Unit> = Result.success(Unit),
        private val updateResult: Result<Unit> = Result.success(Unit),
        private val hideResult: Result<Unit> = Result.success(Unit)
    ) : PresenceOverlayWindowHost {
        var showCount = 0
        var updateCount = 0
        var hideCount = 0

        override fun show(state: PresenceOverlayUiState): Result<Unit> {
            showCount += 1
            return showResult
        }

        override fun update(state: PresenceOverlayUiState): Result<Unit> {
            updateCount += 1
            return updateResult
        }

        override fun hide(): Result<Unit> {
            hideCount += 1
            return hideResult
        }
    }
}
