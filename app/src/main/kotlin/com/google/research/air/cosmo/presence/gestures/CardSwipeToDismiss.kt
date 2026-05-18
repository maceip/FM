package com.google.research.air.cosmo.presence.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.PresenceTokens
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe-to-dismiss modifier for presence cards. Activates only when the initial touch lands in
 * the **bottom edge zone** (last [bottomZone] dp) or the **left edge zone** (first [leftZone] dp)
 * of the composable.
 *
 * - **Left swipe** from left zone → card slides left and fades out → [onDismiss]
 * - **Down swipe** from bottom zone → card slides down and fades out → [onDismiss]
 * - Release without sufficient velocity/displacement → spring back to origin
 *
 * The card's offset and alpha are driven by the gesture state. Apply this to the outer container
 * of each card (the `GlassPanel` wrapper in `CardStackSurface`).
 */
fun Modifier.cardSwipeToDismiss(
    onDismiss: () -> Unit,
    bottomZone: Dp = 56.dp,
    leftZone: Dp = 56.dp,
    dismissThresholdDp: Float = 120f,
    velocityThresholdDpPerSec: Float = 800f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val dismissPx = with(density) { dismissThresholdDp.dp.toPx() }
    val velocityPx = with(density) { velocityThresholdDpPerSec.dp.toPx() }
    val bottomZonePx = with(density) { bottomZone.toPx() }
    val leftZonePx = with(density) { leftZone.toPx() }

    this
        .graphicsLayer {
            translationX = offset.value.x
            translationY = offset.value.y
            val displacement = maxOf(abs(offset.value.x), abs(offset.value.y))
            alpha = (1f - (displacement / (dismissPx * 2f)).coerceIn(0f, 1f)).coerceAtLeast(0.2f)
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val touchX = down.position.x
                val touchY = down.position.y
                val inLeftZone = touchX < leftZonePx
                val inBottomZone = touchY > (size.height - bottomZonePx)

                if (!inLeftZone && !inBottomZone) return@awaitEachGesture

                val axis = if (inLeftZone) SwipeAxis.HORIZONTAL else SwipeAxis.VERTICAL
                var lastTime = System.currentTimeMillis()
                var lastPos = down.position

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        val dt = (System.currentTimeMillis() - lastTime).coerceAtLeast(1)
                        val velocity = (change.position - lastPos) / (dt.toFloat() / 1000f)
                        val displacement = offset.value

                        val shouldDismiss = when (axis) {
                            SwipeAxis.HORIZONTAL ->
                                displacement.x < -dismissPx || velocity.x < -velocityPx
                            SwipeAxis.VERTICAL ->
                                displacement.y > dismissPx || velocity.y > velocityPx
                        }

                        if (shouldDismiss) {
                            scope.launch {
                                val target = when (axis) {
                                    SwipeAxis.HORIZONTAL -> Offset(-size.width.toFloat() * 1.5f, displacement.y)
                                    SwipeAxis.VERTICAL -> Offset(displacement.x, size.height.toFloat() * 1.5f)
                                }
                                offset.animateTo(target, tween(200))
                                onDismiss()
                                offset.snapTo(Offset.Zero)
                            }
                        } else {
                            scope.launch {
                                offset.animateTo(
                                    Offset.Zero,
                                    spring(
                                        dampingRatio = PresenceTokens.Motion.snapDamping,
                                        stiffness = PresenceTokens.Motion.snapStiffness
                                    )
                                )
                            }
                        }
                        break
                    }

                    val delta = change.positionChange()
                    if (delta != Offset.Zero) {
                        val constrained = when (axis) {
                            SwipeAxis.HORIZONTAL -> Offset(delta.x.coerceAtMost(0f), 0f)
                            SwipeAxis.VERTICAL -> Offset(0f, delta.y.coerceAtLeast(0f))
                        }
                        scope.launch { offset.snapTo(offset.value + constrained) }
                        lastPos = change.position
                        lastTime = System.currentTimeMillis()
                        change.consume()
                    }
                }
            }
        }
}

private enum class SwipeAxis { HORIZONTAL, VERTICAL }
