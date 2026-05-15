package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun CallingContent(data: CardPayload.Calling) {
    Column(Modifier.testTag("callingCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "CALLING", title = "CALLING AGENT")
        ContactRow(name = data.contactName, initials = data.contactInitials, detail = data.status.name)
        MonoLabel("STATUS: ${data.status.name}")
        data.transcript?.let { transcript ->
            MonoLabel("TRANSCRIPT")
            Spacer(Modifier.height(4.dp))
            InnerPanel {
                Text(transcript, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            }
        }
    }
}
