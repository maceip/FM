package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceTokens
import kotlin.math.cos
import kotlin.math.sin

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
    needsInput: Boolean = false,
    animationPhase: Float? = null
) {
    val state = when {
        needsInput -> OrbVisualState.NEEDS_INPUT
        isWorking -> OrbVisualState.WORKING
        activity == OrbActivity.LISTENING -> OrbVisualState.LISTENING
        else -> OrbVisualState.IDLE
    }

    val shape = RoundedCornerShape(PresenceTokens.Size.orbCorner)
    val alpha =
        if (animationPhase == null) {
            val animatedAlpha by animateFloatAsState(
                targetValue = if (state == OrbVisualState.IDLE) 0.4f else 1f,
                animationSpec = spring(),
                label = "orbAlpha"
            )
            animatedAlpha
        } else {
            if (state == OrbVisualState.IDLE) 0.4f else 1f
        }

    val (tickPhase, pulseAlpha) =
        if (animationPhase == null) {
            val inf = rememberInfiniteTransition(label = "orbSpin")
            val animatedPhase by inf.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(PresenceTokens.RotationSpeed.mediumFast, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "orbTickPhase"
            )
            val animatedPulseAlpha by inf.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            animatedPhase to animatedPulseAlpha
        } else {
            animationPhase to 1f
        }

    Box(
        modifier = modifier
            .size(orbSize)
            .graphicsLayer { this.alpha = alpha }
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.65f),
                spotColor = state.accent.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.055f),
                        Color(0xE00A0F18),
                        Color(0xF0040710)
                    )
                ),
                shape
            )
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)
                    ),
                    size = Size(size.width, size.height * 0.45f)
                )
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            state.accent.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.68f, size.height * 0.46f),
                        radius = size.minDimension * 0.42f
                    ),
                    size = size
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(size.width * 0.12f, 1.dp.toPx()),
                    end = Offset(size.width * 0.88f, 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, state.accent.copy(alpha = 0.72f), Color.Transparent)
                    ),
                    topLeft = Offset(size.width * 0.18f, size.height - 1.5.dp.toPx()),
                    size = Size(size.width * 0.64f, 1.5.dp.toPx())
                )
                val center = Offset(size.width / 2f, size.height / 2f)
                val side = size.minDimension
                val ringColor = PresenceTokens.Palette.OnSurfaceDim.copy(alpha = 0.18f)
                listOf(0.21f, 0.31f, 0.41f).forEach { fraction ->
                    drawCircle(
                        color = ringColor,
                        radius = side * fraction,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                repeat(48) { marker ->
                    val angle = (Math.PI * 2.0 * marker / 48.0).toFloat()
                    val innerRadius = side * if (marker % 4 == 0) 0.405f else 0.425f
                    val outerRadius = side * 0.445f
                    val alpha = if (marker % 4 == 0) 0.30f else 0.14f
                    drawLine(
                        color = PresenceTokens.Palette.OnSurfaceDim.copy(alpha = alpha),
                        start = Offset(center.x + cos(angle) * innerRadius, center.y + sin(angle) * innerRadius),
                        end = Offset(center.x + cos(angle) * outerRadius, center.y + sin(angle) * outerRadius),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                repeat(4) { marker ->
                    val angle = (Math.PI * 2.0 * marker / 4.0).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        radius = 1.dp.toPx(),
                        center = Offset(center.x + cos(angle) * side * 0.43f, center.y + sin(angle) * side * 0.43f)
                    )
                }
            }
            .border(1.dp, PresenceTokens.Palette.BorderBright, shape),
        contentAlignment = Alignment.Center
    ) {
        if (state == OrbVisualState.IDLE) {
            OrbCenterIndicator(state, pulseAlpha)
        } else {
            TacticalTickRing(
                spec = state.tickSpec,
                glyph = state.glyph,
                glyphColor = state.accent.copy(alpha = pulseAlpha),
                modifier = Modifier.size(orbSize * 0.78f),
                animationPhase = tickPhase,
                glyphSize = orbSize * 0.06f
            )
        }
    }
}

private enum class OrbVisualState { IDLE, LISTENING, WORKING, NEEDS_INPUT }

private val OrbVisualState.accent: Color
    get() =
        when (this) {
            OrbVisualState.IDLE -> PresenceTokens.Palette.CyanDim
            OrbVisualState.LISTENING -> PresenceTokens.Palette.AcidGreen
            OrbVisualState.WORKING -> PresenceTokens.Palette.Cyan
            OrbVisualState.NEEDS_INPUT -> PresenceTokens.Palette.AcidYellow
        }

private val OrbVisualState.tickSpec: TacticalTickRingSpec
    get() =
        when (this) {
            OrbVisualState.IDLE ->
                TacticalTickRingSpec(
                    tickCount = 64,
                    radiusFraction = 0.36f,
                    tickLengthFraction = 0.08f,
                    tickWidthFraction = 0.022f,
                    baseColor = PresenceTokens.Palette.OnSurfaceDim.copy(alpha = 0.22f),
                    segments = emptyList()
                )
            OrbVisualState.LISTENING ->
                TacticalTickRingSpec(
                    tickCount = 72,
                    radiusFraction = 0.38f,
                    tickLengthFraction = 0.078f,
                    tickWidthFraction = 0.019f,
                    baseColor = Color(0xFF2C3443).copy(alpha = 0.72f),
                    segments =
                        listOf(
                            TacticalTickSegment(4, 28, PresenceTokens.Palette.AcidGreen),
                            TacticalTickSegment(45, 9, PresenceTokens.Palette.CyanDim.copy(alpha = 0.62f))
                        )
                )
            OrbVisualState.WORKING ->
                TacticalTickRingSpec(
                    tickCount = 72,
                    radiusFraction = 0.38f,
                    tickLengthFraction = 0.076f,
                    tickWidthFraction = 0.019f,
                    baseColor = Color(0xFF193545).copy(alpha = 0.72f),
                    segments =
                        listOf(
                            TacticalTickSegment(0, 52, PresenceTokens.Palette.Cyan),
                            TacticalTickSegment(56, 7, PresenceTokens.Palette.CyanDim.copy(alpha = 0.50f))
                        )
                )
            OrbVisualState.NEEDS_INPUT ->
                TacticalTickRingSpec(
                    tickCount = 72,
                    radiusFraction = 0.38f,
                    tickLengthFraction = 0.078f,
                    tickWidthFraction = 0.019f,
                    baseColor = Color(0xFF2E3240).copy(alpha = 0.72f),
                    segments =
                        listOf(
                            TacticalTickSegment(0, 37, PresenceTokens.Palette.AcidYellow),
                            TacticalTickSegment(43, 9, PresenceTokens.Palette.Magenta),
                            TacticalTickSegment(58, 6, PresenceTokens.Palette.CyanDim.copy(alpha = 0.44f))
                        )
                )
        }

private val OrbVisualState.glyph: TacticalCenterGlyph
    get() =
        when (this) {
            OrbVisualState.IDLE -> TacticalCenterGlyph.Lens
            OrbVisualState.LISTENING -> TacticalCenterGlyph.Dot
            OrbVisualState.WORKING -> TacticalCenterGlyph.Square
            OrbVisualState.NEEDS_INPUT -> TacticalCenterGlyph.Exclamation
        }

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
                    .shadow(10.dp, RoundedCornerShape(50), ambientColor = PresenceTokens.Palette.AcidGreen)
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
