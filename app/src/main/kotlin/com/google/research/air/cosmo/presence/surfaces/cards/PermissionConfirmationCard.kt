package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens

@Composable
internal fun PermissionConfirmationContent(data: CardPayload.PermissionConfirmation) {
    Column(Modifier.testTag("permissionCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "CONFIRM", title = "PERMISSION")
        Text(data.prompt, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(PresenceTokens.Palette.Cyan).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(data.actionLabel.uppercase(), color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            if (data.undoable) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(PresenceTokens.Palette.SurfaceContainerHigh).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("UNDO", color = PresenceTokens.Palette.OnSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
