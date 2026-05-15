package com.google.research.air.cosmo.presence.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.google.research.air.cosmo.presence.PresenceTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gesture modifier that makes a composable draggable with stiff physics, snapping to edge anchors
 * on release. Classifies release gesture into tap, long-press, flick-to-dismiss, or swipe-to-expand.
 *
 * Apply to the outer Box that positions the orb/tab surface. The surface reads [state.offset] for
 * its position via `Modifier.offset { state.offset.value.toIntOffset() }`.
 */
fun Modifier.edgeDockable(
    state: EdgeDockState,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onExpand: () -> Unit = {},
    onDismiss: () -> Unit = {}
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thresholds = GestureThresholds(
        tapMaxPx = with(density) { Dp(PresenceTokens.Motion.tapMaxDisplacementDp).toPx() },
        longPressMaxPx = with(density) { Dp(PresenceTokens.Motion.longPressMaxDisplacementDp).toPx() },
        flickThresholdPx = with(density) { Dp(PresenceTokens.Motion.flickVelocityThresholdDpPerSec).toPx() },
        expandThresholdPx = with(density) { Dp(PresenceTokens.Motion.expandVelocityThresholdDpPerSec).toPx() },
    )
    val callbacks = EdgeGestureCallbacks(onTap, onLongPress, onExpand, onDismiss)

    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downTime = System.currentTimeMillis()
            var totalDisplacement = Offset.Zero
            var lastPosition = down.position
            var lastTime = downTime

            var tracking = true
            while (tracking) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull()

                if (change == null) {
                    tracking = false
                } else if (!change.pressed) {
                    val releaseTime = System.currentTimeMillis()
                    val duration = releaseTime - downTime
                    val dt = (releaseTime - lastTime).coerceAtLeast(1)
                    val velocity = (change.position - lastPosition) / (dt.toFloat() / 1000f)

                    performReleaseGesture(
                        gesture = classifyReleaseGesture(state, velocity, totalDisplacement.getDistance(), duration, thresholds),
                        state = state,
                        scope = scope,
                        callbacks = callbacks,
                    )
                    tracking = false
                } else {
                    val delta = change.positionChange()
                    if (delta != Offset.Zero) {
                        totalDisplacement += delta
                        val decay = state.velocityDecayFactor(state.offset.value)
                        val dampedDelta = delta * decay
                        scope.launch { state.snapTo(state.offset.value + dampedDelta) }
                        lastPosition = change.position
                        lastTime = System.currentTimeMillis()
                        change.consume()
                    }
                }
            }
        }
    }
}

private data class GestureThresholds(
    val tapMaxPx: Float,
    val longPressMaxPx: Float,
    val flickThresholdPx: Float,
    val expandThresholdPx: Float,
)

private data class EdgeGestureCallbacks(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
    val onExpand: () -> Unit,
    val onDismiss: () -> Unit,
)

private enum class ReleaseGesture {
    TAP,
    LONG_PRESS,
    DISMISS,
    EXPAND,
    SNAP,
}

private fun classifyReleaseGesture(
    state: EdgeDockState,
    velocity: Offset,
    displacement: Float,
    duration: Long,
    thresholds: GestureThresholds,
): ReleaseGesture =
    when {
        displacement < thresholds.tapMaxPx && duration < PresenceTokens.Motion.tapMaxDurationMs -> ReleaseGesture.TAP
        displacement < thresholds.longPressMaxPx &&
            duration >= PresenceTokens.Motion.longPressMinDurationMs -> ReleaseGesture.LONG_PRESS
        abs(velocity.x) > thresholds.flickThresholdPx && isAwayFromCenter(state, velocity) -> ReleaseGesture.DISMISS
        abs(velocity.x) > thresholds.expandThresholdPx && isTowardCenter(state, velocity) -> ReleaseGesture.EXPAND
        else -> ReleaseGesture.SNAP
    }

private fun performReleaseGesture(
    gesture: ReleaseGesture,
    state: EdgeDockState,
    scope: CoroutineScope,
    callbacks: EdgeGestureCallbacks,
) {
    when (gesture) {
        ReleaseGesture.TAP -> callbacks.onTap()
        ReleaseGesture.LONG_PRESS -> callbacks.onLongPress()
        ReleaseGesture.DISMISS -> callbacks.onDismiss()
        ReleaseGesture.EXPAND -> callbacks.onExpand()
        ReleaseGesture.SNAP -> scope.launch { state.snapToNearest() }
    }
}

private fun isAwayFromCenter(state: EdgeDockState, velocity: Offset): Boolean {
    return when (state.dockedEdge) {
        ScreenEdge.RIGHT -> velocity.x > 0
        ScreenEdge.LEFT -> velocity.x < 0
        ScreenEdge.TOP -> velocity.y < 0
        ScreenEdge.BOTTOM -> velocity.y > 0
    }
}

private fun isTowardCenter(state: EdgeDockState, velocity: Offset): Boolean {
    return when (state.dockedEdge) {
        ScreenEdge.RIGHT -> velocity.x < 0
        ScreenEdge.LEFT -> velocity.x > 0
        ScreenEdge.TOP -> velocity.y > 0
        ScreenEdge.BOTTOM -> velocity.y < 0
    }
}
