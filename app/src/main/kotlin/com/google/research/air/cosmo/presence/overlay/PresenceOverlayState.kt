package com.google.research.air.cosmo.presence.overlay

import com.google.research.air.cosmo.presence.PresenceState

enum class PresenceOverlayRoute {
    HIDDEN,
    PARKED,
    EXPANDED_TAB,
    CARD_STACK,
    PERMISSION_REQUIRED,
    ERROR
}

data class PresenceOverlayUiState(
    val route: PresenceOverlayRoute = PresenceOverlayRoute.HIDDEN,
    val presenceState: PresenceState = PresenceState.Hidden,
    val backStack: List<PresenceOverlayRoute> = emptyList(),
    val canDrawOverlays: Boolean = true,
    val lastError: String? = null,
    val backProgress: Float = 0f
) {
    val isVisible: Boolean
        get() = route != PresenceOverlayRoute.HIDDEN
}

sealed interface PresenceOverlayEvent {
    data object Expand : PresenceOverlayEvent
    data object ShowCards : PresenceOverlayEvent
    data object Collapse : PresenceOverlayEvent
    data object Dismiss : PresenceOverlayEvent
    data object Back : PresenceOverlayEvent
    data object BackStarted : PresenceOverlayEvent
    data object BackCancelled : PresenceOverlayEvent
    data class BackProgressed(val progress: Float) : PresenceOverlayEvent
    data class PresenceChanged(val state: PresenceState) : PresenceOverlayEvent
    data class PermissionChanged(val granted: Boolean) : PresenceOverlayEvent
    data class WindowError(val message: String) : PresenceOverlayEvent
}
