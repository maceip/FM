package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.PresenceTokens
import kotlin.math.roundToInt

internal data class TacticalTickSegment(
    val startTick: Int,
    val tickCount: Int,
    val color: Color
)

internal data class TacticalTickRingSpec(
    val tickCount: Int,
    val radiusFraction: Float,
    val tickLengthFraction: Float,
    val tickWidthFraction: Float,
    val baseColor: Color,
    val segments: List<TacticalTickSegment>,
    val startAngleDegrees: Float = -90f,
    val innerRingFractions: List<Float> = listOf(0.38f, 0.58f, 0.78f),
    val markerColor: Color = PresenceTokens.Palette.OnSurfaceDim.copy(alpha = 0.38f)
)

internal enum class TacticalCenterGlyph {
    Dot,
    Square,
    Exclamation,
    Lens
}

@Composable
internal fun TacticalTickRing(
    spec: TacticalTickRingSpec,
    glyph: TacticalCenterGlyph,
    glyphColor: Color,
    modifier: Modifier = Modifier,
    animationPhase: Float = 0f,
    glyphSize: Dp = 14.dp
) {
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawTacticalTickRing(spec = spec, animationPhase = animationPhase)
            drawCenterGlyphCanvas(glyph = glyph, color = glyphColor, glyphSizePx = glyphSize.toPx())
        }
        if (glyph == TacticalCenterGlyph.Exclamation) {
            Text(
                text = "!",
                color = glyphColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
internal fun TacticalTickRing(
    spec: TacticalTickRingSpec,
    glyph: TacticalCenterGlyph,
    glyphColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    animationPhase: Float = 0f
) {
    TacticalTickRing(
        spec = spec,
        glyph = glyph,
        glyphColor = glyphColor,
        modifier = modifier.size(size),
        animationPhase = animationPhase
    )
}

private fun DrawScope.drawTacticalTickRing(spec: TacticalTickRingSpec, animationPhase: Float) {
    val side = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = side * spec.radiusFraction
    val tickLength = side * spec.tickLengthFraction
    val tickWidth = side * spec.tickWidthFraction
    val tickCorner = CornerRadius(tickWidth * 0.45f, tickWidth * 0.45f)
    val phaseTickOffset = (animationPhase.coerceIn(0f, 1f) * spec.tickCount).roundToInt()

    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = radius + tickLength * 0.68f,
        center = center
    )

    spec.innerRingFractions.forEach { fraction ->
        drawCircle(
            color = spec.markerColor.copy(alpha = 0.26f),
            radius = side * fraction,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }

    repeat(spec.tickCount) { tick ->
        val phasedTick = floorMod(tick - phaseTickOffset, spec.tickCount)
        val color = spec.colorForTick(phasedTick)
        val angle = spec.startAngleDegrees + tick * (360f / spec.tickCount)
        rotate(degrees = angle, pivot = center) {
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = center.x - tickWidth / 2f,
                    y = center.y - radius - tickLength / 2f
                ),
                size = Size(tickWidth, tickLength),
                cornerRadius = tickCorner
            )
        }
    }

    repeat(4) { marker ->
        val angle = marker * 90f
        rotate(degrees = angle, pivot = center) {
            drawCircle(
                color = spec.markerColor,
                radius = 1.25.dp.toPx(),
                center = Offset(center.x, center.y - radius - tickLength * 1.25f)
            )
        }
    }
}

private fun DrawScope.drawCenterGlyphCanvas(
    glyph: TacticalCenterGlyph,
    color: Color,
    glyphSizePx: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(
        color = Color.Black.copy(alpha = 0.42f),
        radius = glyphSizePx * 1.4f,
        center = center
    )
    drawCircle(
        color = color.copy(alpha = 0.24f),
        radius = glyphSizePx * 1.8f,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )

    when (glyph) {
        TacticalCenterGlyph.Dot,
        TacticalCenterGlyph.Lens -> {
            drawCircle(color = color.copy(alpha = 0.28f), radius = glyphSizePx * 0.72f, center = center)
            drawCircle(color = color, radius = glyphSizePx * 0.28f, center = center)
        }
        TacticalCenterGlyph.Square -> {
            val square = glyphSizePx * 0.82f
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - square / 2f, center.y - square / 2f),
                size = Size(square, square),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            val inset = 2.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.84f),
                topLeft = Offset(center.x - square / 2f + inset, center.y - square / 2f + inset),
                size = Size(square - inset * 2f, square - inset * 2f),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        TacticalCenterGlyph.Exclamation -> Unit
    }
}

private fun TacticalTickRingSpec.colorForTick(tick: Int): Color {
    segments.forEach { segment ->
        if (floorMod(tick - segment.startTick, tickCount) < segment.tickCount) {
            return segment.color
        }
    }
    return baseColor
}

private fun floorMod(value: Int, modulus: Int): Int {
    val result = value % modulus
    return if (result < 0) result + modulus else result
}
