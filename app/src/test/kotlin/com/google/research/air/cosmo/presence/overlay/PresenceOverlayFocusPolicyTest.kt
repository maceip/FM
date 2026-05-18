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

    @Test
    fun onlyExpandedInteractiveRoutesAcceptTouch() {
        assertFalse(PresenceOverlayFocusPolicy.acceptsTouch(PresenceOverlayRoute.HIDDEN))
        assertFalse(PresenceOverlayFocusPolicy.acceptsTouch(PresenceOverlayRoute.PARKED))
        assertFalse(PresenceOverlayFocusPolicy.acceptsTouch(PresenceOverlayRoute.PERMISSION_REQUIRED))
        assertFalse(PresenceOverlayFocusPolicy.acceptsTouch(PresenceOverlayRoute.ERROR))

        assertTrue(PresenceOverlayFocusPolicy.acceptsTouch(PresenceOverlayRoute.EXPANDED_TAB))
        assertTrue(PresenceOverlayFocusPolicy.acceptsTouch(PresenceOverlayRoute.CARD_STACK))
    }
}
