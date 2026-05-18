package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.SettingItem
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun SettingsContent(data: CardPayload.Settings) {
    Column(Modifier.testTag("settingsCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun SettingItemRow(item: SettingItem) {
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
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            item.value?.let {
                Text(
                    it,
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        TogglePill(enabled = item.enabled)
    }
}

@Composable
private fun TogglePill(enabled: Boolean) {
    Box(
        Modifier
            .width(44.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) Color(0xFF4266A9).copy(alpha = 0.74f) else Color.White.copy(alpha = 0.12f))
            .border(1.dp, PresenceTokens.Palette.BorderDim, RoundedCornerShape(999.dp))
            .padding(3.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.90f))
        )
    }
}
