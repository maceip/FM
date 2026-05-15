package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.research.air.cosmo.presence.OrbActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrbSurfaceTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `idle state renders without crash`() {
        rule.setContent {
            OrbSurface(activity = OrbActivity.IDLE)
        }
        rule.waitForIdle()
    }

    @Test
    fun `listening state renders without crash`() {
        rule.setContent {
            OrbSurface(activity = OrbActivity.LISTENING)
        }
        rule.waitForIdle()
    }

    @Test
    fun `working state renders without crash`() {
        rule.setContent {
            OrbSurface(activity = OrbActivity.IDLE, isWorking = true)
        }
        rule.waitForIdle()
    }

    @Test
    fun `needs input state shows exclamation`() {
        rule.setContent {
            OrbSurface(activity = OrbActivity.IDLE, needsInput = true)
        }
        rule.onNodeWithText("!").assertExists()
    }
}
