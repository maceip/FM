package com.google.research.air.cosmo.presence.overlay

object PresenceOverlayFocusPolicy {
    fun acceptsBackKey(route: PresenceOverlayRoute): Boolean =
        route == PresenceOverlayRoute.EXPANDED_TAB || route == PresenceOverlayRoute.CARD_STACK

    fun acceptsTouch(route: PresenceOverlayRoute): Boolean =
        route == PresenceOverlayRoute.EXPANDED_TAB || route == PresenceOverlayRoute.CARD_STACK
}
