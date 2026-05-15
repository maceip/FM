package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceTokens

/**
 * The Orb — a 36-48dp squircle that shows the agent's current state via spinning rings and
 * a color-coded center indicator. This is the "parked" presence affordance.
 *
 * States:
 * - IDLE: matte frosted glass, no rings, faint center bar
 * - LISTENING: acid green dashed ring + green center dot
 * - WORKING: electric cyan dotted ring + cyan center square
 * - NEEDS_INPUT: acid yellow segmented ring with magenta accent + "!" center
 */
@Composable
fun OrbSurface(
    activity: OrbActivity,
    modifier: Modifier = Modifier,
    orbSize: Dp = PresenceTokens.Size.orbDefault,
    isWorking: Boolean = false,
    needsInput: Boolean = false
) {
    val state = when {
        needsInput -> OrbVisualState.NEEDS_INPUT
        isWorking -> OrbVisualState.WORKING
        activity == OrbActivity.LISTENING -> OrbVisualState.LISTENING
        else -> OrbVisualState.IDLE
    }

    val shape = RoundedCornerShape(PresenceTokens.Size.orbCorner)
    val alpha by animateFloatAsState(
        targetValue = if (state == OrbVisualState.IDLE) 0.4f else 1f,
        animationSpec = spring(),
        label = "orbAlpha"
    )

    val inf = rememberInfiniteTransition(label = "orbSpin")
    val spinAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(PresenceTokens.Ring.spinDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    val pulseAlpha by inf.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(orbSize)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(PresenceTokens.Palette.SurfaceGlass, shape)
            .border(1.dp, PresenceTokens.Palette.BorderDefault, shape),
        contentAlignment = Alignment.Center
    ) {
        if (state != OrbVisualState.IDLE) {
            Canvas(Modifier.size(orbSize)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawStateRings(state, cx, cy, spinAngle, pulseAlpha, size)
            }
        }
        OrbCenterIndicator(state, pulseAlpha)
    }
}

private enum class OrbVisualState { IDLE, LISTENING, WORKING, NEEDS_INPUT }

@Composable
private fun OrbCenterIndicator(state: OrbVisualState, pulseAlpha: Float) {
    when (state) {
        OrbVisualState.IDLE -> {
            Box(
                Modifier
                    .size(width = 20.dp, height = 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
        OrbVisualState.LISTENING -> {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PresenceTokens.Palette.AcidGreen.copy(alpha = pulseAlpha))
            )
        }
        OrbVisualState.WORKING -> {
            Box(
                Modifier
                    .size(8.dp)
                    .border(2.dp, PresenceTokens.Palette.Cyan, RoundedCornerShape(1.dp))
            )
        }
        OrbVisualState.NEEDS_INPUT -> {
            Text(
                text = "!",
                color = PresenceTokens.Palette.AcidYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun DrawScope.drawStateRings(
    state: OrbVisualState,
    cx: Float,
    cy: Float,
    angle: Float,
    pulseAlpha: Float,
    canvasSize: Size
) {
    val outerRadius = minOf(canvasSize.width, canvasSize.height) * 0.38f
    val innerRadius = outerRadius * 0.6f

    val (color, dashEffect) = when (state) {
        OrbVisualState.LISTENING -> PresenceTokens.Palette.AcidGreen to
                PathEffect.dashPathEffect(floatArrayOf(PresenceTokens.Ring.dashOn, PresenceTokens.Ring.dashOff))
        OrbVisualState.WORKING -> PresenceTokens.Palette.Cyan to
                PathEffect.dashPathEffect(floatArrayOf(PresenceTokens.Ring.dotOn, PresenceTokens.Ring.dotOff))
        OrbVisualState.NEEDS_INPUT -> PresenceTokens.Palette.AcidYellow to
                PathEffect.dashPathEffect(floatArrayOf(PresenceTokens.Ring.dashOn * 2, PresenceTokens.Ring.dashOff))
        else -> return
    }

    // inner solid ring
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = innerRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1f)
    )

    // outer dashed/dotted ring (spinning)
    rotate(degrees = angle, pivot = Offset(cx, cy)) {
        drawCircle(
            color = color.copy(alpha = 0.8f * pulseAlpha),
            radius = outerRadius,
            center = Offset(cx, cy),
            style = Stroke(width = PresenceTokens.Ring.strokeWidth, pathEffect = dashEffect)
        )
    }

    // needs-input magenta highlight segment
    if (state == OrbVisualState.NEEDS_INPUT) {
        rotate(degrees = angle + 120f, pivot = Offset(cx, cy)) {
            val arcSize = outerRadius * 2
            drawArc(
                color = PresenceTokens.Palette.Magenta.copy(alpha = 0.8f),
                startAngle = 0f,
                sweepAngle = 30f,
                useCenter = false,
                topLeft = Offset(cx - outerRadius, cy - outerRadius),
                size = Size(arcSize, arcSize),
                style = Stroke(width = PresenceTokens.Ring.strokeWidthHeavy)
            )
        }
    }
}
