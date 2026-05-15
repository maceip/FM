package com.google.research.air.cosmo.presence.gestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EdgeDockableTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `tap callback fires on short press`() {
        val tapped = AtomicBoolean(false)
        rule.setContent {
            val state = rememberEdgeDockState(initialEdge = ScreenEdge.RIGHT)
            Box(
                Modifier
                    .size(48.dp)
                    .offset { IntOffset(state.offset.value.x.toInt(), state.offset.value.y.toInt()) }
                    .edgeDockable(state, onTap = { tapped.set(true) })
                    .testTag("dockable")
            )
        }
        rule.onNodeWithTag("dockable").performClick()
        rule.waitForIdle()
        assertTrue("Tap callback should fire", tapped.get())
    }

    @Test
    fun `state initializes to right edge`() {
        var docked: ScreenEdge? = null
        rule.setContent {
            val state = rememberEdgeDockState(initialEdge = ScreenEdge.RIGHT)
            docked = state.dockedEdge
            Box(Modifier.size(48.dp))
        }
        rule.waitForIdle()
        assertTrue("Should dock to RIGHT", docked == ScreenEdge.RIGHT)
    }

    @Test
    fun `state initializes to left edge`() {
        var docked: ScreenEdge? = null
        rule.setContent {
            val state = rememberEdgeDockState(initialEdge = ScreenEdge.LEFT)
            docked = state.dockedEdge
            Box(Modifier.size(48.dp))
        }
        rule.waitForIdle()
        assertTrue("Should dock to LEFT", docked == ScreenEdge.LEFT)
    }
}
