package com.google.research.air.cosmo.presence.overlay

import com.google.research.air.cosmo.presence.PresenceState

class PresenceOverlayBackStack(
    initialState: PresenceOverlayUiState = PresenceOverlayUiState()
) {
    var state: PresenceOverlayUiState = initialState
        private set

    fun dispatch(event: PresenceOverlayEvent): PresenceOverlayUiState {
        state =
            when (event) {
                PresenceOverlayEvent.Back -> onBack()
                PresenceOverlayEvent.BackStarted -> state.copy(backProgress = 0.02f)
                PresenceOverlayEvent.BackCancelled -> state.copy(backProgress = 0f)
                is PresenceOverlayEvent.BackProgressed -> state.copy(backProgress = event.progress.coerceIn(0f, 1f))
                PresenceOverlayEvent.Collapse -> collapse()
                PresenceOverlayEvent.Dismiss -> hidden(state.presenceState, state.canDrawOverlays)
                PresenceOverlayEvent.Expand -> push(PresenceOverlayRoute.EXPANDED_TAB)
                PresenceOverlayEvent.ShowCards -> showCards()
                is PresenceOverlayEvent.PermissionChanged -> onPermissionChanged(event.granted)
                is PresenceOverlayEvent.PresenceChanged -> onPresenceChanged(event.state)
                is PresenceOverlayEvent.WindowError ->
                    state.copy(
                        route = PresenceOverlayRoute.ERROR,
                        backStack = listOf(PresenceOverlayRoute.ERROR),
                        lastError = event.message,
                        backProgress = 0f
                    )
            }
        return state
    }

    private fun onPresenceChanged(next: PresenceState): PresenceOverlayUiState {
        if (!state.canDrawOverlays && next != PresenceState.Hidden) {
            return state.copy(
                route = PresenceOverlayRoute.PERMISSION_REQUIRED,
                presenceState = next,
                backStack = listOf(PresenceOverlayRoute.PERMISSION_REQUIRED),
                    lastError = null,
                    backProgress = 0f
            )
        }

        return when (next) {
            PresenceState.Hidden -> hidden(next, state.canDrawOverlays)
            is PresenceState.Dismissed -> hidden(next, state.canDrawOverlays)
            is PresenceState.Parked ->
                state.copy(
                    route = PresenceOverlayRoute.PARKED,
                    presenceState = next,
                    backStack = listOf(PresenceOverlayRoute.PARKED),
                    lastError = null,
                    backProgress = 0f
                )
            is PresenceState.Working,
            is PresenceState.NeedsInput ->
                state.copy(
                    route = PresenceOverlayRoute.EXPANDED_TAB,
                    presenceState = next,
                    backStack = listOf(PresenceOverlayRoute.PARKED, PresenceOverlayRoute.EXPANDED_TAB),
                    lastError = null,
                    backProgress = 0f
                )
            is PresenceState.Ready ->
                state.copy(
                    route = PresenceOverlayRoute.CARD_STACK,
                    presenceState = next,
                    backStack = listOf(PresenceOverlayRoute.PARKED, PresenceOverlayRoute.EXPANDED_TAB, PresenceOverlayRoute.CARD_STACK),
                    lastError = null,
                    backProgress = 0f
                )
        }
    }

    private fun onPermissionChanged(granted: Boolean): PresenceOverlayUiState {
        if (!granted) {
            return state.copy(
                canDrawOverlays = false,
                route = if (state.presenceState == PresenceState.Hidden) PresenceOverlayRoute.HIDDEN else PresenceOverlayRoute.PERMISSION_REQUIRED,
                backStack = if (state.presenceState == PresenceState.Hidden) emptyList() else listOf(PresenceOverlayRoute.PERMISSION_REQUIRED)
            )
        }
        val previousPresence = state.presenceState
        state = state.copy(canDrawOverlays = true)
        return onPresenceChanged(previousPresence)
    }

    private fun onBack(): PresenceOverlayUiState {
        if (state.route == PresenceOverlayRoute.PARKED) {
            return state.copy(backProgress = 0f, lastError = null)
        }
        if (state.backStack.size <= 1) {
            return collapse()
        }
        val nextStack = state.backStack.dropLast(1)
        return state.copy(route = nextStack.last(), backStack = nextStack, lastError = null, backProgress = 0f)
    }

    private fun collapse(): PresenceOverlayUiState {
        if (state.route == PresenceOverlayRoute.HIDDEN) return state
        return state.copy(
            route = PresenceOverlayRoute.PARKED,
            backStack = listOf(PresenceOverlayRoute.PARKED),
            lastError = null,
            backProgress = 0f
        )
    }

    private fun showCards(): PresenceOverlayUiState =
        when (state.presenceState) {
            is PresenceState.Ready ->
                state.copy(
                    route = PresenceOverlayRoute.CARD_STACK,
                    backStack = listOf(PresenceOverlayRoute.PARKED, PresenceOverlayRoute.EXPANDED_TAB, PresenceOverlayRoute.CARD_STACK),
                    lastError = null,
                    backProgress = 0f
                )
            else -> push(PresenceOverlayRoute.EXPANDED_TAB)
        }

    private fun push(route: PresenceOverlayRoute): PresenceOverlayUiState {
        if (state.route == PresenceOverlayRoute.HIDDEN || state.route == PresenceOverlayRoute.PERMISSION_REQUIRED) return state
        val stack = if (route in state.backStack) state.backStack.takeWhile { it != route } + route else state.backStack + route
        return state.copy(route = route, backStack = stack, lastError = null, backProgress = 0f)
    }

    private fun hidden(presenceState: PresenceState, canDrawOverlays: Boolean): PresenceOverlayUiState =
        PresenceOverlayUiState(
            route = PresenceOverlayRoute.HIDDEN,
            presenceState = presenceState,
            canDrawOverlays = canDrawOverlays
        )
}
