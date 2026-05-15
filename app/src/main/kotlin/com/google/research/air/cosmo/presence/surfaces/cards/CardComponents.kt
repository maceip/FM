package com.google.research.air.cosmo.presence.surfaces.cards

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(PresenceTokens.Size.cardDotSize).clip(CircleShape).background(PresenceTokens.Palette.CyanDim))
                Text(categoryLabel, color = PresenceTokens.Palette.CyanDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(title, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = Modifier.testTag("cardTitle"))
        }
    }
}

@Composable
internal fun MonoLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = modifier)
}

@Composable
internal fun ActionButtonGrid(cardId: String, actions: List<CardAction>, onAction: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.take(3).forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onAction(cardId, action.id) }.padding(vertical = 4.dp).testTag("action_${action.id}")
            ) {
                Text(action.label.uppercase(), color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                action.sublabel?.let {
                    Text(it.uppercase(), color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
internal fun SourceRow(source: Source) {
    Row(Modifier.fillMaxWidth().testTag("source_${source.title}"), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(source.iconLabel?.take(3) ?: "?", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
    Row(modifier.fillMaxWidth().testTag("check_${item.text}"), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = PresenceTokens.Palette.CyanDim, uncheckedColor = PresenceTokens.Palette.OnSurfaceDim)
        )
        Text(item.text, color = if (item.checked) PresenceTokens.Palette.OnSurfaceDim else Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
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
