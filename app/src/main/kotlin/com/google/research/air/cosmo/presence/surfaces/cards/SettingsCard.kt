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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.SettingItem
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun SettingsContent(data: CardPayload.Settings) {
    BoxWithConstraints(Modifier.testTag("settingsCard")) {
        if (maxWidth >= 560.dp) {
            WideSettingsContent(data)
        } else {
            CompactSettingsContent(data)
        }
    }
}

@Composable
private fun CompactSettingsContent(data: CardPayload.Settings) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "SETTINGS", title = data.section)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("INTERFACE")
            MonoLabel("1 / 5")
        }
        InnerPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                data.items.take(5).forEach { item ->
                    SettingItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun WideSettingsContent(data: CardPayload.Settings) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderTile("gear")
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFFB35DFF)))
                    Text(
                        "SETTINGS",
                        color = Color(0xFFB987FF),
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    "SYSTEM SETTINGS",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("1 / 5", color = Color.White.copy(alpha = 0.72f), fontSize = 25.sp, fontFamily = FontFamily.Monospace)
                SearchBox()
            }
        }
        BrightnessRail()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            SettingsGroup(
                title = "INTERFACE",
                glyph = "gear",
                items = data.items.take(3),
                modifier = Modifier.weight(1f)
            )
            SettingsGroup(
                title = "DATA & PRIVACY",
                glyph = "shield",
                items = data.items.drop(3).take(3),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeaderTile(label: String) {
    Box(
        Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, PresenceTokens.Palette.BorderBright, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        SettingsGlyph(kind = label, color = Color(0xFFB987FF), modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun SearchBox() {
    Row(
        Modifier
            .width(330.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .border(1.dp, PresenceTokens.Palette.BorderBright.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsGlyph(kind = "search", color = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Text("Search settings...", color = Color.White.copy(alpha = 0.58f), fontSize = 24.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BrightnessRail() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsGlyph(kind = "sun", color = Color.White.copy(alpha = 0.56f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier
                .weight(1f)
                .height(16.dp)
                .drawBehind {
                    val y = size.height / 2f
                    drawLine(Color.White.copy(alpha = 0.20f), Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.86f), Offset(0f, y), Offset(size.width * 0.27f, y), strokeWidth = 4.dp.toPx())
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(size.width * 0.27f, y))
                    drawCircle(Color.White.copy(alpha = 0.16f), radius = 7.dp.toPx(), center = Offset(size.width * 0.78f, y))
                }
        )
        Spacer(Modifier.width(18.dp))
        SettingsGlyph(kind = "sun", color = Color.White.copy(alpha = 0.58f), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, glyph: String, items: List<SettingItem>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, PresenceTokens.Palette.BorderBright.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 28.sp, fontFamily = FontFamily.Monospace)
            SettingsGlyph(kind = glyph, color = Color.White.copy(alpha = 0.62f), modifier = Modifier.size(38.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        items.forEach { item ->
            SettingItemRow(item, large = true)
        }
    }
}

@Composable
private fun SettingsGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.dp.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        when (kind) {
            "search" -> {
                drawCircle(color, radius = size.minDimension * 0.32f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = stroke)
                drawLine(color, Offset(size.width * 0.64f, size.height * 0.64f), Offset(size.width * 0.88f, size.height * 0.88f), strokeWidth = stroke.width)
            }
            "sun" -> {
                drawCircle(color, radius = size.minDimension * 0.16f, center = center, style = stroke)
                repeat(12) { tick ->
                    val angle = (Math.PI * 2.0 * tick / 12.0).toFloat()
                    val inner = size.minDimension * 0.30f
                    val outer = size.minDimension * 0.46f
                    drawLine(
                        color,
                        Offset(center.x + kotlin.math.cos(angle) * inner, center.y + kotlin.math.sin(angle) * inner),
                        Offset(center.x + kotlin.math.cos(angle) * outer, center.y + kotlin.math.sin(angle) * outer),
                        strokeWidth = stroke.width
                    )
                }
            }
            "shield" -> {
                val points =
                    listOf(
                        Offset(size.width * 0.50f, size.height * 0.10f),
                        Offset(size.width * 0.82f, size.height * 0.24f),
                        Offset(size.width * 0.76f, size.height * 0.66f),
                        Offset(size.width * 0.50f, size.height * 0.90f),
                        Offset(size.width * 0.24f, size.height * 0.66f),
                        Offset(size.width * 0.18f, size.height * 0.24f),
                        Offset(size.width * 0.50f, size.height * 0.10f)
                    )
                points.zipWithNext().forEach { (start, end) -> drawLine(color, start, end, strokeWidth = stroke.width) }
            }
            else -> {
                drawCircle(color, radius = size.minDimension * 0.30f, center = center, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.12f, center = center, style = stroke)
                repeat(8) { tick ->
                    val angle = (Math.PI * 2.0 * tick / 8.0).toFloat()
                    val inner = size.minDimension * 0.36f
                    val outer = size.minDimension * 0.48f
                    drawLine(
                        color,
                        Offset(center.x + kotlin.math.cos(angle) * inner, center.y + kotlin.math.sin(angle) * inner),
                        Offset(center.x + kotlin.math.cos(angle) * outer, center.y + kotlin.math.sin(angle) * outer),
                        strokeWidth = stroke.width
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingItemRow(item: SettingItem) {
    SettingItemRow(item = item, large = false)
}

@Composable
private fun SettingItemRow(item: SettingItem, large: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = if (large) 25.sp else 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            item.value?.let {
                Text(
                    it,
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = if (large) 13.sp else 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (large && item.value != null) {
            Box(
                Modifier
                    .width(74.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4D7DE3))
                    .border(2.dp, Color(0xFF86A8FF).copy(alpha = 0.82f), RoundedCornerShape(8.dp))
            )
        } else {
            TogglePill(enabled = item.enabled, large = large)
        }
    }
}

@Composable
private fun TogglePill(enabled: Boolean, large: Boolean = false) {
    Box(
        Modifier
            .width(if (large) 74.dp else 44.dp)
            .height(if (large) 40.dp else 22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) Color(0xFF4266A9).copy(alpha = 0.74f) else Color.White.copy(alpha = 0.12f))
            .border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(999.dp))
            .padding(if (large) 5.dp else 3.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(if (large) 30.dp else 16.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.90f))
        )
    }
}
