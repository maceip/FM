package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.PresenceTokens

/**
 * The Tab — an edge-docked glass sliver that spring-expands into an active pill with a
 * radar gauge and data readouts. This is the alternative to [OrbSurface] for users who prefer
 * edge-native presence.
 *
 * Two visual modes:
 * - **Idle**: 10dp wide, 180dp tall glass sliver with a central lens-flare glow point
 * - **Active**: expanded pill (~320x160dp) with a spinning radar gauge on the left and
 *   monospace data readouts on the right (Syncing / Latency / Channel / Link / Edge)
 */
@Composable
fun TabSurface(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    readouts: List<Pair<String, String>> = defaultReadouts,
    statusLabel: String = "SYNCING",
    edgeDocked: Boolean = true,
    onTap: () -> Unit = {}
) {
    AnimatedContent(
        targetState = expanded,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(200)) using
                    SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> spring(dampingRatio = 0.7f, stiffness = 400f) })
        },
        label = "tabExpand"
    ) { isExpanded ->
        if (isExpanded) {
            ExpandedPill(readouts = readouts, statusLabel = statusLabel, edgeDocked = edgeDocked, onTap = onTap)
        } else {
            IdleSliver(onTap = onTap)
        }
    }
}

@Composable
private fun IdleSliver(onTap: () -> Unit) {
    val sliverShape = RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp)
    Box(
        modifier = Modifier
            .width(PresenceTokens.Size.tabIdleWidth)
            .height(PresenceTokens.Size.tabIdleHeight)
            .shadow(
                elevation = 20.dp,
                shape = sliverShape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = PresenceTokens.Palette.CyanDim.copy(alpha = 0.35f)
            )
            .clip(sliverShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PresenceTokens.Palette.SurfaceGlass,
                        PresenceTokens.Palette.SurfaceGlassHigh,
                        PresenceTokens.Palette.SurfaceGlass
                    )
                ),
                sliverShape
            )
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.62f), Color.Transparent)
                    ),
                    topLeft = Offset.Zero,
                    size = Size(1.dp.toPx(), size.height)
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, PresenceTokens.Palette.CyanDim.copy(alpha = 0.38f), Color.Transparent)
                    ),
                    topLeft = Offset(size.width - 1.dp.toPx(), 0f),
                    size = Size(1.dp.toPx(), size.height)
                )
            }
            .border(1.dp, PresenceTokens.Palette.BorderBright, sliverShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        // lens flare core
        Box(
            Modifier
                .size(width = 2.dp, height = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(PresenceTokens.Palette.CyanDim)
                .drawBehind {
                    // horizontal lens flare
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, PresenceTokens.Palette.CyanDim.copy(alpha = 0.8f), Color.Transparent)
                        ),
                        topLeft = Offset(-30.dp.toPx(), size.height / 2 - 0.5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(60.dp.toPx(), 1.dp.toPx())
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.72f), Color.Transparent)
                        ),
                        topLeft = Offset(size.width / 2 - 0.5.dp.toPx(), -36.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(1.dp.toPx(), 84.dp.toPx())
                    )
                }
        )
    }
}

@Composable
private fun ExpandedPill(
    readouts: List<Pair<String, String>>,
    statusLabel: String,
    edgeDocked: Boolean,
    onTap: () -> Unit
) {
    val pillShape =
        if (edgeDocked) {
            RoundedCornerShape(
                topStart = PresenceTokens.Size.tabCorner,
                bottomStart = PresenceTokens.Size.tabCorner,
                topEnd = 0.dp,
                bottomEnd = 0.dp
            )
        } else {
            RoundedCornerShape(PresenceTokens.Size.tabCorner)
        }
    GlassPanel(
        modifier = Modifier
            .width(PresenceTokens.Size.tabExpandedWidth)
            .height(PresenceTokens.Size.tabExpandedHeight)
            .clickable(onClick = onTap),
        shape = pillShape,
        fill = PresenceTokens.Palette.SurfaceGlassMedium,
        showGlowEdge = edgeDocked
    ) {
        Row(
            Modifier.padding(start = 28.dp, top = 18.dp, end = 30.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadarGauge(Modifier.size(112.dp))
            Spacer(Modifier.width(24.dp))
            Column(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(start = 16.dp)
                    .drawBehind {
                        drawLine(
                            color = PresenceTokens.Palette.BorderDim,
                            start = Offset(-16.dp.toPx(), 0f),
                            end = Offset(-16.dp.toPx(), size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = statusLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                )
                Box(
                    Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(PresenceTokens.Palette.CyanDim.copy(alpha = 0.5f))
                )
                Spacer(Modifier.height(12.dp))
                readouts.forEach { (label, value) ->
                    ReadoutRow(label = label, value = value)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (edgeDocked) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .height(52.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            size = Size(size.width, size.height)
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(PresenceTokens.Palette.CyanDim.copy(alpha = 0.35f), Color.White.copy(alpha = 0.85f))
                            ),
                            topLeft = Offset(-32.dp.toPx(), 0f),
                            size = Size(36.dp.toPx(), size.height)
                        )
                    }
            )
        }
    }
}

@Composable
private fun ReadoutRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = PresenceTokens.Palette.OnSurfaceDim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f)
        )
        val isHighlight = value.equals("Online", ignoreCase = true)
        Text(
            text = value,
            color = if (isHighlight) PresenceTokens.Palette.CyanDim else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RadarGauge(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "radarSpin")
    val angle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(PresenceTokens.Ring.spinDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )
    val reverseAngle by inf.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(PresenceTokens.Ring.spinDurationSlowMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarReverseAngle"
    )

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = minOf(size.width, size.height) / 2f * 0.9f
        val middleR = outerR * 0.7f
        val innerR = outerR * 0.5f

        // outer dashed ring (spinning)
        rotate(degrees = angle, pivot = Offset(cx, cy)) {
            drawCircle(
                color = PresenceTokens.Palette.OnSurfaceDim.copy(alpha = 0.4f),
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )
            )
        }

        // middle segmented ring (reverse spinning, blue)
        rotate(degrees = reverseAngle, pivot = Offset(cx, cy)) {
            drawCircle(
                color = PresenceTokens.Palette.CyanDim.copy(alpha = 0.8f),
                radius = middleR,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
                )
            )
        }

        // dim outer tick ring
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 6f))
            )
        )

        // inner circle border
        drawCircle(
            color = PresenceTokens.Palette.OnSurfaceDim.copy(alpha = 0.3f),
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )

        // center dot
        drawCircle(
            color = PresenceTokens.Palette.CyanDim,
            radius = 3f,
            center = Offset(cx, cy)
        )

        // crosshairs
        drawLine(
            color = PresenceTokens.Palette.CyanDim.copy(alpha = 0.5f),
            start = Offset(cx, cy - innerR),
            end = Offset(cx, cy + innerR),
            strokeWidth = 1f
        )
        drawLine(
            color = PresenceTokens.Palette.CyanDim.copy(alpha = 0.5f),
            start = Offset(cx - innerR, cy),
            end = Offset(cx + innerR, cy),
            strokeWidth = 1f
        )
    }
}

private val defaultReadouts = listOf(
    "Latency" to "04ms",
    "Channel" to "Ready",
    "Link" to "Optimal",
    "Edge" to "Online"
)
