package com.google.research.air.cosmo.presence.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.google.research.air.cosmo.presence.PresenceTokens
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gesture modifier that makes a composable draggable with stiff physics, snapping to edge anchors
 * on release. Classifies release gesture into tap, long-press, flick-to-dismiss, or swipe-to-expand.
 *
 * Apply to the outer Box that positions the orb/tab surface. The surface reads [state.offset] for
 * its position via `Modifier.offset { state.offset.value.toIntOffset() }`.
 */
@Suppress("CyclomaticComplexMethod")
fun Modifier.edgeDockable(
    state: EdgeDockState,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onExpand: () -> Unit = {},
    onDismiss: () -> Unit = {}
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val tapMaxPx = with(density) { Dp(PresenceTokens.Motion.tapMaxDisplacementDp).toPx() }
    val longPressMaxPx = with(density) { Dp(PresenceTokens.Motion.longPressMaxDisplacementDp).toPx() }
    val flickThresholdPx = with(density) { Dp(PresenceTokens.Motion.flickVelocityThresholdDpPerSec).toPx() }
    val expandThresholdPx = with(density) { Dp(PresenceTokens.Motion.expandVelocityThresholdDpPerSec).toPx() }

    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downTime = System.currentTimeMillis()
            var totalDisplacement = Offset.Zero
            var lastPosition = down.position
            var lastTime = downTime

            @Suppress("LoopWithTooManyJumpStatements")
            while (true) {
                val event = awaitPointerEvent()
                val change: PointerInputChange = event.changes.firstOrNull() ?: break

                if (!change.pressed) {
                    classifyRelease(
                        downTime, totalDisplacement, lastPosition, lastTime,
                        change, tapMaxPx, longPressMaxPx, flickThresholdPx, expandThresholdPx,
                        state, scope, onTap, onLongPress, onDismiss, onExpand
                    )
                    break
                }

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

private fun classifyRelease(
    downTime: Long,
    totalDisplacement: Offset,
    lastPosition: Offset,
    lastTime: Long,
    change: PointerInputChange,
    tapMaxPx: Float,
    longPressMaxPx: Float,
    flickThresholdPx: Float,
    expandThresholdPx: Float,
    state: EdgeDockState,
    scope: kotlinx.coroutines.CoroutineScope,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
    onExpand: () -> Unit
) {
    val upTime = System.currentTimeMillis()
    val duration = upTime - downTime
    val displacement = totalDisplacement.getDistance()
    val dt = (upTime - lastTime).coerceAtLeast(1)
    val velocity = (change.position - lastPosition) / (dt.toFloat() / 1000f)

    when {
        displacement < tapMaxPx && duration < PresenceTokens.Motion.tapMaxDurationMs -> onTap()
        displacement < longPressMaxPx && duration >= PresenceTokens.Motion.longPressMinDurationMs -> onLongPress()
        abs(velocity.x) > flickThresholdPx && isAwayFromCenter(state, velocity) -> onDismiss()
        abs(velocity.x) > expandThresholdPx && isTowardCenter(state, velocity) -> onExpand()
        else -> scope.launch { state.snapToNearest() }
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
