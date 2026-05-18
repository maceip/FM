package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardAction
import com.google.research.air.cosmo.presence.CheckItem
import com.google.research.air.cosmo.presence.CitationEntry
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.Source
import com.google.research.air.cosmo.presence.Thumbnail

@Composable
internal fun CardHeader(categoryLabel: String, title: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(PresenceTokens.Size.cardIconSize).clip(CircleShape)
                .border(1.dp, PresenceTokens.Palette.BorderBright, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            HeaderIcon(categoryLabel)
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(PresenceTokens.Size.cardDotSize)
                        .clip(CircleShape)
                        .background(PresenceTokens.Palette.CyanDim)
                        .drawBehind {
                            drawCircle(
                                color = PresenceTokens.Palette.CyanDim.copy(alpha = 0.35f),
                                radius = size.minDimension
                            )
                        }
                )
            Text(categoryLabel, color = PresenceTokens.Palette.CyanDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, letterSpacing = 3.sp, modifier = Modifier.testTag("cardTitle"))
        }
    }
}

@Composable
internal fun MonoLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = modifier)
}

@Composable
internal fun ActionButtonGrid(cardId: String, actions: List<CardAction>, onAction: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PresenceTokens.Spacing.actionGridGap)) {
        actions.take(3).forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.18f))
                    .border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(8.dp))
                    .clickable { onAction(cardId, action.id) }
                    .padding(vertical = 9.dp, horizontal = 6.dp)
                    .testTag("action_${action.id}")
            ) {
                Text(action.label.uppercase(), color = PresenceTokens.Palette.OnSurfaceVariant, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                action.sublabel?.let {
                    Text(it.uppercase(), color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
internal fun SourceRow(source: Source) {
    Row(Modifier.fillMaxWidth().testTag("source_${source.title}"), verticalAlignment = Alignment.CenterVertically) {
        val iconLabel = source.iconLabel?.take(3) ?: "?"
        val iconBackground =
            when (iconLabel.uppercase()) {
                "YT" -> Color(0xFFFF2D2D)
                "Q" -> Color(0xFF05070B)
                else -> Color.White
            }
        val iconForeground =
            when (iconLabel.uppercase()) {
                "IBM" -> Color(0xFF326CE5)
                "YT" -> Color.White
                "Q" -> Color.White
                else -> Color.Black
            }
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(iconBackground).border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(iconLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = iconForeground)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
            Text(source.subtitle, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Text("${source.confidence}%", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HeaderIcon(categoryLabel: String) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.2.dp.toPx())
        val color = PresenceTokens.Palette.OnSurfaceVariant.copy(alpha = 0.9f)
        val center = Offset(size.width / 2f, size.height / 2f)
        when (categoryLabel.uppercase()) {
            "BROWSER" -> {
                drawCircle(color = color, radius = size.minDimension * 0.42f, center = center, style = stroke)
                drawLine(color, Offset(center.x, size.height * 0.14f), Offset(center.x, size.height * 0.86f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.16f, center.y), Offset(size.width * 0.84f, center.y), strokeWidth = stroke.width)
                drawCircle(color = color.copy(alpha = 0.55f), radius = size.minDimension * 0.22f, center = center, style = stroke)
            }
            "MESSAGING" -> {
                drawLine(color, Offset(size.width * 0.12f, size.height * 0.24f), Offset(size.width * 0.86f, size.height * 0.10f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.86f, size.height * 0.10f), Offset(size.width * 0.56f, size.height * 0.86f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.56f, size.height * 0.86f), Offset(size.width * 0.12f, size.height * 0.24f), strokeWidth = stroke.width)
                drawLine(color.copy(alpha = 0.7f), Offset(size.width * 0.30f, size.height * 0.34f), Offset(size.width * 0.56f, size.height * 0.86f), strokeWidth = stroke.width)
            }
            "CALENDAR" -> {
                drawRect(color = color, topLeft = Offset(size.width * 0.16f, size.height * 0.22f), size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.58f), style = stroke)
                drawLine(color, Offset(size.width * 0.16f, size.height * 0.38f), Offset(size.width * 0.84f, size.height * 0.38f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.12f), Offset(size.width * 0.34f, size.height * 0.28f), strokeWidth = stroke.width)
                drawLine(color, Offset(size.width * 0.66f, size.height * 0.12f), Offset(size.width * 0.66f, size.height * 0.28f), strokeWidth = stroke.width)
            }
            else -> drawCircle(color = color, radius = 2.dp.toPx(), center = center)
        }
    }
}

@Composable
internal fun ContactRow(name: String, initials: String, detail: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag("contact_$name"), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
            Text(detail, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
internal fun TimerDisplay(minutes: Int, seconds: Int, modifier: Modifier = Modifier) {
    Column(modifier.testTag("timer"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%02d:%02d".format(minutes, seconds), color = Color.White, fontSize = 28.sp, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MonoLabel("MIN")
            MonoLabel("SEC")
        }
    }
}

@Composable
internal fun CheckItemRow(item: CheckItem, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(8.dp))
            .clickable { onToggle(!item.checked) }
            .padding(10.dp)
            .testTag("check_${item.text}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, if (item.checked) PresenceTokens.Palette.CyanDim else PresenceTokens.Palette.OnSurfaceDim, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.checked) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(PresenceTokens.Palette.CyanDim)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            item.text,
            color = if (item.checked) PresenceTokens.Palette.OnSurfaceDim else Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun ThumbnailGrid(thumbnails: List<Thumbnail>, modifier: Modifier = Modifier) {
    val columns = if (thumbnails.size <= 2) thumbnails.size else 3
    Column(modifier.testTag("thumbnailGrid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        thumbnails.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { thumb ->
                    Box(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(8.dp))
                            .background(PresenceTokens.Palette.SurfaceContainerHigh)
                            .border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(thumb.label, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfidenceBar(confidence: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { confidence / 100f },
            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = PresenceTokens.Palette.CyanDim,
            trackColor = PresenceTokens.Palette.SurfaceContainerHigh
        )
        Text("${confidence}%", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun CitationRow(entry: CitationEntry) {
    Column(Modifier.fillMaxWidth().testTag("citation_${entry.title}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(entry.title, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
        Text(entry.url, color = PresenceTokens.Palette.CyanDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        ConfidenceBar(entry.confidence)
        entry.reason?.let {
            Text(it, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}

@Composable
internal fun ReminderRow(time: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(time, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, maxLines = 1)
    }
}
