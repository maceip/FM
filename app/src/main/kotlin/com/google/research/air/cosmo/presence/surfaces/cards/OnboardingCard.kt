package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.OnboardingCheckpoint
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun OnboardingContent(data: CardPayload.Onboarding) {
    BoxWithConstraints(Modifier.testTag("onboardingCard")) {
        if (maxWidth >= 560.dp) {
            WideOnboardingContent(data)
        } else {
            CompactOnboardingContent(data)
        }
    }
}

@Composable
private fun CompactOnboardingContent(data: CardPayload.Onboarding) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "ONBOARDING", title = data.title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("SETUP FLOW")
            MonoLabel("${data.step} / ${data.totalSteps}")
        }
        InnerPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    data.body,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
                CheckpointRail(data.checkpoints)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4266A9).copy(alpha = 0.72f))
                .border(1.dp, PresenceTokens.Palette.BorderBright, RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "NEXT: SETTINGS",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun WideOnboardingContent(data: CardPayload.Onboarding) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, PresenceTokens.Palette.BorderBright, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                OnboardingGlyph(kind = "hero", color = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(58.dp))
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFFF9B3D)))
                    Text(
                        "ONBOARDING",
                        color = Color(0xFFFFB27A),
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    data.title,
                    color = Color.White,
                    fontSize = 40.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }
            Text("${data.step} / ${data.totalSteps}", color = Color.White.copy(alpha = 0.72f), fontSize = 25.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            OnboardingDiagram(
                checkpoints = data.checkpoints,
                modifier = Modifier.weight(0.43f)
            )
            Column(Modifier.weight(0.57f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    data.body,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 29.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 35.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("SKIP INTRO", color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF4D73B9).copy(alpha = 0.86f))
                        .border(1.dp, PresenceTokens.Palette.BorderBright, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NEXT: SETTINGS",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            WideAction("OPEN IN", "BROWSER", Modifier.weight(1f))
            WideAction("BETTER", "SOURCES", Modifier.weight(1f))
            WideAction("COLLAPSE", "TO EDGE", Modifier.weight(1f))
        }
    }
}

@Composable
private fun OnboardingDiagram(checkpoints: List<OnboardingCheckpoint>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(266.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, PresenceTokens.Palette.BorderBright.copy(alpha = 0.36f), RoundedCornerShape(16.dp))
            .padding(26.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            checkpoints.take(3).forEach { checkpoint ->
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (checkpoint.complete) Color(0xFF4D73B9) else Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, PresenceTokens.Palette.BorderBright, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    OnboardingGlyph(
                        kind = if (checkpoint.complete) "check" else "empty",
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("LIST", "USER", "GEAR").forEachIndexed { index, label ->
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (index == 0) Color(0xFF4D73B9).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.035f))
                        .border(1.dp, PresenceTokens.Palette.BorderBright.copy(alpha = 0.58f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    OnboardingGlyph(kind = label.lowercase(), color = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.dp.toPx())
        when (kind) {
            "check" -> {
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.52f), Offset(size.width * 0.42f, size.height * 0.72f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.42f, size.height * 0.72f), Offset(size.width * 0.80f, size.height * 0.26f), strokeWidth = stroke.width)
            }
            "list" -> {
                repeat(3) { row ->
                    val y = size.height * (0.28f + row * 0.22f)
                    drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(size.width * 0.22f, y))
                    drawLine(color, Offset(size.width * 0.34f, y), Offset(size.width * 0.78f, y), strokeWidth = stroke.width)
                }
            }
            "user" -> {
                drawCircle(color, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.50f, size.height * 0.34f), style = stroke)
                drawArc(color, 205f, 130f, false, topLeft = Offset(size.width * 0.22f, size.height * 0.46f), size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.42f), style = stroke)
            }
            "gear" -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color, radius = size.minDimension * 0.25f, center = center, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.10f, center = center, style = stroke)
                repeat(8) { tick ->
                    val angle = (Math.PI * 2.0 * tick / 8.0).toFloat()
                    drawLine(
                        color,
                        Offset(center.x + kotlin.math.cos(angle) * size.minDimension * 0.32f, center.y + kotlin.math.sin(angle) * size.minDimension * 0.32f),
                        Offset(center.x + kotlin.math.cos(angle) * size.minDimension * 0.46f, center.y + kotlin.math.sin(angle) * size.minDimension * 0.46f),
                        strokeWidth = stroke.width
                    )
                }
            }
            "hero" -> {
                drawArc(color, 180f, 180f, false, topLeft = Offset(size.width * 0.10f, size.height * 0.24f), size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.62f), style = stroke)
                drawLine(color, Offset(size.width * 0.20f, size.height * 0.58f), Offset(size.width * 0.80f, size.height * 0.58f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.76f, size.height * 0.44f), Offset(size.width * 0.86f, size.height * 0.58f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.86f, size.height * 0.58f), Offset(size.width * 0.76f, size.height * 0.72f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.62f, size.height * 0.30f), Offset(size.width * 0.82f, size.height * 0.10f), strokeWidth = stroke.width)
            }
            else -> drawCircle(color, radius = size.minDimension * 0.26f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
        }
    }
}

@Composable
private fun WideAction(top: String, bottom: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(80.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .border(1.dp, PresenceTokens.Palette.BorderBright.copy(alpha = 0.42f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(top, color = Color.White, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
            Text(bottom, color = Color.White, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CheckpointRail(checkpoints: List<OnboardingCheckpoint>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        checkpoints.take(4).forEach { checkpoint ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (checkpoint.complete) Color(0xFF4D73B9) else Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, PresenceTokens.Palette.BorderBright, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (checkpoint.complete) "OK" else "-",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    checkpoint.label.uppercase(),
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
