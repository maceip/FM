package com.google.research.air.cosmo.presence.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.google.research.air.cosmo.presence.PresenceTokens
import kotlin.math.abs

@Stable
class EdgeDockState(
    initialOffset: Offset,
    val anchors: List<Offset>,
    val density: Density
) {
    val offset = Animatable(initialOffset, Offset.VectorConverter)

    val dockedEdge: ScreenEdge by derivedStateOf {
        val pos = offset.value
        val screenCenterX = anchors.maxOf { it.x } / 2f
        if (pos.x < screenCenterX) ScreenEdge.LEFT else ScreenEdge.RIGHT
    }

    fun nearestAnchor(position: Offset = offset.value): Offset {
        return anchors.minByOrNull { (it - position).getDistanceSquared() } ?: position
    }

    suspend fun snapToNearest() {
        val target = nearestAnchor()
        offset.animateTo(
            target,
            spring(
                dampingRatio = PresenceTokens.Motion.snapDamping,
                stiffness = PresenceTokens.Motion.snapStiffness
            )
        )
    }

    suspend fun snapTo(position: Offset) {
        offset.snapTo(position)
    }

    suspend fun animateToAnchor(anchor: Offset) {
        offset.animateTo(
            anchor,
            spring(
                dampingRatio = PresenceTokens.Motion.snapDamping,
                stiffness = PresenceTokens.Motion.snapStiffness
            )
        )
    }

    fun velocityDecayFactor(position: Offset): Float {
        val nearest = nearestAnchor(position)
        val dist = (nearest - position).getDistance()
        val proximityPx = with(density) { Dp(PresenceTokens.Motion.anchorProximityDp).toPx() }
        return if (dist < proximityPx) {
            (dist / proximityPx).coerceIn(0.1f, 1f)
        } else {
            1f
        }
    }
}

enum class ScreenEdge { LEFT, RIGHT, TOP, BOTTOM }

@Composable
fun rememberEdgeDockState(
    initialEdge: ScreenEdge = ScreenEdge.RIGHT,
    screenWidth: Float = 1080f,
    screenHeight: Float = 2340f,
    orbSize: Float = with(LocalDensity.current) { PresenceTokens.Size.orbDefault.toPx() }
): EdgeDockState {
    val density = LocalDensity.current
    return remember(initialEdge, screenWidth, screenHeight) {
        val margin = orbSize / 2f
        val anchors = listOf(
            Offset(margin, screenHeight * 0.4f),
            Offset(margin, screenHeight * 0.6f),
            Offset(screenWidth - margin, screenHeight * 0.4f),
            Offset(screenWidth - margin, screenHeight * 0.6f)
        )
        val initial = when (initialEdge) {
            ScreenEdge.RIGHT -> Offset(screenWidth - margin, screenHeight * 0.5f)
            ScreenEdge.LEFT -> Offset(margin, screenHeight * 0.5f)
            ScreenEdge.TOP -> Offset(screenWidth * 0.5f, margin)
            ScreenEdge.BOTTOM -> Offset(screenWidth * 0.5f, screenHeight - margin)
        }
        EdgeDockState(initialOffset = initial, anchors = anchors, density = density)
    }
}
