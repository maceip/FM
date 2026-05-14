package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top and/or bottom edges of a scrollable composable using `BlendMode.DstIn`. Adapted
 * from Kvaesitso's `FadingEdges.kt`. The receiver must render its own content (Lazy/Column etc.);
 * this modifier only masks the alpha at the edges.
 *
 * @param top fade height for the top edge (default 0.dp = no top fade)
 * @param bottom fade height for the bottom edge (default 24.dp)
 */
fun Modifier.verticalFadingEdges(
    top: Dp = 0.dp,
    bottom: Dp = 24.dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        if (topPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topPx
                ),
                size = Size(size.width, topPx),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomPx,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - bottomPx),
                size = Size(size.width, bottomPx),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Horizontal counterpart for row-scrolling surfaces. */
fun Modifier.horizontalFadingEdges(
    start: Dp = 24.dp,
    end: Dp = 24.dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val startPx = start.toPx()
        val endPx = end.toPx()
        if (startPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = startPx
                ),
                size = Size(startPx, size.height),
                blendMode = BlendMode.DstIn
            )
        }
        if (endPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - endPx,
                    endX = size.width
                ),
                topLeft = Offset(size.width - endPx, 0f),
                size = Size(endPx, size.height),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Convenience scope-aware wrapper that applies vertical fading edges to its contents. */
@Composable
fun FadingEdgeBox(
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
    bottom: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.verticalFadingEdges(top = top, bottom = bottom), content = content)
}
