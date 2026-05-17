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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun NavigationContent(data: CardPayload.Navigation) {
    Column(Modifier.testTag("navigationCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "NAVIGATION", title = "ROUTE ASSIST")
        InnerPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RouteGlyph(Modifier.size(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoLabel("DESTINATION")
                    Text(
                        text = data.destination,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                    data.routeSummary?.let {
                        Text(
                            text = it,
                            color = PresenceTokens.Palette.OnSurfaceDim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("ETA")
            Text(
                text = data.eta ?: "READY",
                color = PresenceTokens.Palette.CyanDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun RouteGlyph(modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val pathColor = PresenceTokens.Palette.CyanDim.copy(alpha = 0.72f)
            drawCircle(
                color = Color.Black.copy(alpha = 0.34f),
                radius = size.minDimension * 0.45f,
                center = center
            )
            drawCircle(
                color = PresenceTokens.Palette.BorderDim,
                radius = size.minDimension * 0.44f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawLine(
                color = pathColor,
                start = Offset(size.width * 0.24f, size.height * 0.72f),
                end = Offset(size.width * 0.46f, size.height * 0.44f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )
            drawLine(
                color = pathColor,
                start = Offset(size.width * 0.46f, size.height * 0.44f),
                end = Offset(size.width * 0.76f, size.height * 0.26f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )
            drawCircle(color = PresenceTokens.Palette.CyanDim, radius = 3.dp.toPx(), center = Offset(size.width * 0.24f, size.height * 0.72f))
            drawCircle(color = Color.White.copy(alpha = 0.82f), radius = 3.dp.toPx(), center = Offset(size.width * 0.76f, size.height * 0.26f))
        }
    }
}
