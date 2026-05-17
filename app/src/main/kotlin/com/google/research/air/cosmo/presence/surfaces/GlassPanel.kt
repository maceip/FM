package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.PresenceTokens

/**
 * Frosted-glass container matching the Tactical Glass Interface spec. Semi-transparent dark fill,
 * 1px laser-cut border (top/left brighter), optional right-edge cyan glow strip, inner top-edge
 * gradient highlight.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PresenceTokens.Size.cardCorner),
    fill: Color = PresenceTokens.Palette.SurfaceGlass,
    borderColor: Color = PresenceTokens.Palette.BorderDefault,
    showGlowEdge: Boolean = false,
    glowColor: Color = PresenceTokens.Palette.CyanDim,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = glowColor.copy(alpha = if (showGlowEdge) 0.22f else 0.08f)
            )
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, borderColor, shape)
            .drawBehind {
                // Top and bottom edge highlights create the machined glass outline from the comps.
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.48f), Color.Transparent)
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, 1.dp.toPx())
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)
                    ),
                    topLeft = Offset.Zero,
                    size = Size(1.dp.toPx(), size.height)
                )
                // bottom-edge subtle line
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, PresenceTokens.Palette.BorderDim, Color.Transparent)
                    ),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx())
                )
            }
    ) {
        content()
        if (showGlowEdge) {
            GlowEdgeStrip(
                color = glowColor,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * Inner recessed panel for nested content (source lists, draft replies, timer areas).
 */
@Composable
fun InnerPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(PresenceTokens.Size.cardInnerCorner))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(PresenceTokens.Size.cardInnerCorner))
            .padding(PresenceTokens.Spacing.sectionGap),
        content = content
    )
}

@Composable
private fun GlowEdgeStrip(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(PresenceTokens.Glow.cardEdgeGlowWidth)
            .height(60.dp) // ~60% of card height
            .drawBehind {
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(4f),
                    size = Size(size.width, size.height)
                )
                // glow halo
                drawRoundRect(
                    color = color.copy(alpha = 0.3f),
                    topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                    size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                    cornerRadius = CornerRadius(8f)
                )
            }
    )
}
