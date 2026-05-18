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
    animationPhase: Float? = null,
    onTap: () -> Unit = {}
) {
    val phase =
        if (animationPhase == null) {
            val inf = rememberInfiniteTransition(label = "tabMotion")
            val animatedPhase by inf.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(PresenceTokens.RotationSpeed.mediumFast, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "tabTickPhase"
            )
            animatedPhase
        } else {
            animationPhase
        }

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
            ExpandedPill(readouts = readouts, statusLabel = statusLabel, edgeDocked = edgeDocked, animationPhase = phase, onTap = onTap)
        } else {
            IdleSliver(animationPhase = phase, onTap = onTap)
        }
    }
}

@Composable
private fun IdleSliver(animationPhase: Float, onTap: () -> Unit) {
    val sliverShape = RoundedCornerShape(999.dp)
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
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.50f), Color.Transparent)
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
                val glintY = size.height * (0.18f + 0.64f * animationPhase)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.86f),
                            PresenceTokens.Palette.CyanDim.copy(alpha = 0.42f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, glintY),
                        radius = 28.dp.toPx()
                    ),
                    radius = 28.dp.toPx(),
                    center = Offset(size.width / 2f, glintY)
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.95f), PresenceTokens.Palette.CyanDim.copy(alpha = 0.92f), Color.Transparent)
                    ),
                    topLeft = Offset(-32.dp.toPx(), glintY - 0.5.dp.toPx()),
                    size = Size(76.dp.toPx(), 1.dp.toPx())
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.88f), Color.Transparent)
                    ),
                    topLeft = Offset(size.width / 2f - 0.5.dp.toPx(), glintY - 56.dp.toPx()),
                    size = Size(1.dp.toPx(), 112.dp.toPx())
                )
                drawRect(
                    color = PresenceTokens.Palette.Magenta.copy(alpha = 0.75f),
                    topLeft = Offset(0f, size.height - 7.dp.toPx()),
                    size = Size(1.5.dp.toPx(), 5.dp.toPx())
                )
            }
            .border(1.dp, PresenceTokens.Palette.BorderBright, sliverShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        // lens flare core
        Box(
            Modifier
                .size(width = 2.dp, height = 18.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.82f))
                .drawBehind {
                    // horizontal lens flare
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.90f), PresenceTokens.Palette.CyanDim.copy(alpha = 0.72f), Color.Transparent)
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
    animationPhase: Float,
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
        borderColor = PresenceTokens.Palette.BorderBright,
        showGlowEdge = true
    ) {
        Row(
            Modifier.padding(start = 28.dp, top = 16.dp, end = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadarGauge(modifier = Modifier.size(112.dp), animationPhase = animationPhase)
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
                Spacer(Modifier.height(8.dp))
                readouts.forEach { (label, value) ->
                    ReadoutRow(label = label, value = value)
                    Spacer(Modifier.height(7.dp))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(10.dp)
                .height(78.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                PresenceTokens.Palette.CyanDim.copy(alpha = 0.38f),
                                Color.White.copy(alpha = 0.96f)
                            )
                        ),
                        topLeft = Offset(-28.dp.toPx(), 0f),
                        size = Size(38.dp.toPx(), size.height)
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = Offset(size.width - 1.5.dp.toPx(), 0f),
                        size = Size(1.5.dp.toPx(), size.height)
                    )
                }
        )
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
private fun RadarGauge(modifier: Modifier = Modifier, animationPhase: Float) {
    TacticalTickRing(
        spec = tabGaugeSpec,
        glyph = TacticalCenterGlyph.Lens,
        glyphColor = Color(0xFFB9D7FF),
        modifier = modifier,
        animationPhase = animationPhase,
        glyphSize = 13.dp
    )
}

private val tabGaugeSpec =
    TacticalTickRingSpec(
        tickCount = 72,
        radiusFraction = 0.38f,
        tickLengthFraction = 0.09f,
        tickWidthFraction = 0.018f,
        baseColor = Color(0xFF323947).copy(alpha = 0.78f),
        segments =
            listOf(
                TacticalTickSegment(2, 34, Color(0xFFCFEAFF)),
                TacticalTickSegment(38, 8, Color(0xFF7BA7E6).copy(alpha = 0.68f))
            ),
        innerRingFractions = listOf(0.24f, 0.38f, 0.55f)
    )

private val defaultReadouts = listOf(
    "Latency" to "04ms",
    "Channel" to "Ready",
    "Link" to "Optimal",
    "Edge" to "Online"
)
