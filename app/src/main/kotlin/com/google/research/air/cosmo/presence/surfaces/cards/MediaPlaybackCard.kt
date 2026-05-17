package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun MediaPlaybackContent(data: CardPayload.MediaPlayback) {
    Column(Modifier.testTag("mediaPlaybackCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "MEDIA", title = "PLAYBACK")
        InnerPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaybackGlyph(Modifier.size(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoLabel("REQUEST")
                    Text(
                        text = data.query,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                    Text(
                        text = data.provider ?: "DEVICE AUDIO",
                        color = PresenceTokens.Palette.OnSurfaceDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        if (data.queue.isNotEmpty()) {
            MonoLabel("QUEUE")
            data.queue.take(3).forEach { item ->
                Text(
                    text = item,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("OUTPUT")
                Text(
                    text = "READY",
                    color = PresenceTokens.Palette.CyanDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PlaybackGlyph(modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val barCount = 18
            val gap = size.width / (barCount * 2f)
            repeat(barCount) { index ->
                val normalized = index / (barCount - 1f)
                val height = size.height * (0.22f + 0.42f * kotlin.math.sin(normalized * Math.PI).toFloat())
                val x = size.width * 0.18f + index * gap
                drawRoundRect(
                    color = if (index % 3 == 0) PresenceTokens.Palette.CyanDim else PresenceTokens.Palette.OnSurfaceDim.copy(alpha = 0.46f),
                    topLeft = Offset(x, size.height / 2f - height / 2f),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), height)
                )
            }
            drawCircle(
                color = Color.Black.copy(alpha = 0.38f),
                radius = size.minDimension * 0.42f,
                center = center
            )
            drawCircle(
                color = PresenceTokens.Palette.CyanDim.copy(alpha = 0.18f),
                radius = size.minDimension * 0.40f,
                center = center
            )
        }
    }
}
