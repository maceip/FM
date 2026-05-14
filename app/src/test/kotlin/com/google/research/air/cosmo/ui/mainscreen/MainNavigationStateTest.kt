package com.google.research.air.cosmo.ui.mainscreen

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainNavigationStateTest {
    @Test
    fun taskMonitorRouteRoundTripsThroughSaveableToken() {
        val route = CosmoRoute.TaskMonitor("Trip", "Find a flight", "Done")
        val token = cosmoRouteToToken(route)

        assertEquals(route, cosmoRouteFromToken(token))
    }

    @Test
    fun durableRoutesRestoreSelectedTabStackAndDropAdaptiveSupportPane() {
        val saved =
            defaultSavedNavigation().copy(
                selectedTabOrdinal = MainTab.Actions.ordinal,
                stacks =
                    mapOf(
                        MainTab.Actions.name to
                            listOf(
                                cosmoRouteToToken(CosmoRoute.Actions),
                                cosmoRouteToToken(CosmoRoute.ActionDetail("a1")),
                                cosmoRouteToToken(CosmoRoute.DebugSupport)
                            )
                    )
            )

        assertEquals(
            listOf(CosmoRoute.Actions, CosmoRoute.ActionDetail("a1")),
            durableRoutesFromSaved(saved)
        )
    }

    @Test
    fun invalidSavedStackFallsBackToSelectedTabRoot() {
        val saved =
            SavedNavigationBundle(
                selectedTabOrdinal = MainTab.Settings.ordinal,
                stacks = mapOf(MainTab.Settings.name to listOf("not-a-route")),
                historyOrdinals = emptyList()
            )

        assertEquals(listOf(CosmoRoute.Settings), durableRoutesFromSaved(saved))
    }

    @Test
    fun adaptiveSupportPaneIsAddedOnlyForNonDebugRoots() {
        val actionsStack: List<NavKey> = listOf(CosmoRoute.Actions, CosmoRoute.ActionDetail("a1"))
        val debugStack: List<NavKey> = listOf(CosmoRoute.Debug)

        assertEquals(
            listOf(CosmoRoute.Actions, CosmoRoute.ActionDetail("a1"), CosmoRoute.DebugSupport),
            actionsStack.withAdaptiveSupport(supportingPaneMode = true)
        )
        assertEquals(
            listOf(CosmoRoute.Debug),
            debugStack.withAdaptiveSupport(supportingPaneMode = true)
        )
        assertEquals(
            actionsStack,
            actionsStack.withAdaptiveSupport(supportingPaneMode = false)
        )
    }

    @Test
    fun corruptTaskMonitorTokenDoesNotRestoreRoute() {
        assertNull(cosmoRouteFromToken("tm:{not-json"))
    }

    @Test
    fun savedNavigationBundleSurvivesJsonRoundTrip() {
        val saved =
            defaultSavedNavigation().copy(
                selectedTabOrdinal = MainTab.Capture.ordinal,
                historyOrdinals = listOf(MainTab.Actions.ordinal, MainTab.Settings.ordinal)
            )

        assertEquals(saved, MainNavJson.decodeFromString<SavedNavigationBundle>(MainNavJson.encodeToString(saved)))
    }
}
