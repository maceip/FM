package com.google.research.air.cosmo.presence.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceOverlayFocusPolicyTest {
    @Test
    fun onlyExpandedInteractiveRoutesAcceptBackKey() {
        assertFalse(PresenceOverlayFocusPolicy.acceptsBackKey(PresenceOverlayRoute.HIDDEN))
        assertFalse(PresenceOverlayFocusPolicy.acceptsBackKey(PresenceOverlayRoute.PARKED))
        assertFalse(PresenceOverlayFocusPolicy.acceptsBackKey(PresenceOverlayRoute.PERMISSION_REQUIRED))
        assertFalse(PresenceOverlayFocusPolicy.acceptsBackKey(PresenceOverlayRoute.ERROR))

        assertTrue(PresenceOverlayFocusPolicy.acceptsBackKey(PresenceOverlayRoute.EXPANDED_TAB))
        assertTrue(PresenceOverlayFocusPolicy.acceptsBackKey(PresenceOverlayRoute.CARD_STACK))
    }
}
