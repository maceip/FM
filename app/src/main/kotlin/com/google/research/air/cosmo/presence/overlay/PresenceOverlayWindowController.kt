package com.google.research.air.cosmo.presence.overlay

import com.google.research.air.cosmo.presence.PresenceState

interface PresenceOverlayWindowHost {
    fun show(state: PresenceOverlayUiState): Result<Unit>
    fun update(state: PresenceOverlayUiState): Result<Unit>
    fun hide(): Result<Unit>
}

class PresenceOverlayWindowController(
    private val backStack: PresenceOverlayBackStack,
    private val permissionChecker: PresenceOverlayPermissionChecker,
    private val host: PresenceOverlayWindowHost
) {
    private var hostVisible: Boolean = false

    val state: PresenceOverlayUiState
        get() = backStack.state

    fun renderPresence(presenceState: PresenceState): PresenceOverlayUiState {
        backStack.dispatch(PresenceOverlayEvent.PermissionChanged(permissionChecker.canDrawOverlays()))
        val next = backStack.dispatch(PresenceOverlayEvent.PresenceChanged(presenceState))
        syncHost(next)
        return backStack.state
    }

    fun dispatch(event: PresenceOverlayEvent): PresenceOverlayUiState {
        val next = backStack.dispatch(event)
        syncHost(next)
        return backStack.state
    }

    fun destroy() {
        host.hide()
        hostVisible = false
    }

    private fun syncHost(state: PresenceOverlayUiState) {
        if (!state.canDrawOverlays || state.route == PresenceOverlayRoute.PERMISSION_REQUIRED || state.route == PresenceOverlayRoute.HIDDEN) {
            host.hide()
            hostVisible = false
            return
        }

        val result =
            if (hostVisible) {
                host.update(state)
            } else {
                host.show(state).onSuccess { hostVisible = true }
            }

        result.onFailure { error ->
            hostVisible = false
            backStack.dispatch(PresenceOverlayEvent.WindowError(error.message ?: error::class.java.simpleName))
            host.hide()
        }
    }
}
