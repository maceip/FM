package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun PermissionConfirmationContent(data: CardPayload.PermissionConfirmation) {
    Column(Modifier.testTag("permissionCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "CONFIRM", title = "PERMISSION")
        InnerPanel(Modifier.fillMaxWidth()) {
            Text(
                data.prompt,
                color = PresenceTokens.Palette.OnSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionAction(label = data.actionLabel, modifier = Modifier.weight(1f), primary = true)
            if (data.undoable) {
                PermissionAction(label = "UNDO", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PermissionAction(label: String, modifier: Modifier = Modifier, primary: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (primary) PresenceTokens.Palette.CyanDim else PresenceTokens.Palette.BorderDim,
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            color = if (primary) PresenceTokens.Palette.CyanDim else PresenceTokens.Palette.OnSurface,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
