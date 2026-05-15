package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabSurfaceTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `idle tab renders without crash`() {
        rule.setContent {
            TabSurface(expanded = false)
        }
        rule.waitForIdle()
    }

    @Test
    fun `expanded tab shows SYNCING label`() {
        rule.setContent {
            TabSurface(expanded = true, statusLabel = "SYNCING")
        }
        rule.onNodeWithText("SYNCING").assertExists()
    }

    @Test
    fun `expanded tab shows readout values`() {
        rule.setContent {
            TabSurface(
                expanded = true,
                readouts = listOf("Latency" to "04ms", "Channel" to "Ready")
            )
        }
        rule.onNodeWithText("04ms").assertExists()
        rule.onNodeWithText("Ready").assertExists()
    }
}
