package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun MessagingContent(data: CardPayload.Messaging) {
    Column(Modifier.testTag("messagingCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "MESSAGING", title = "SEND RESULTS")
        MonoLabel("DRAFT REPLY")
        InnerPanel {
            Text(data.body, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
        }
        if (data.recipients.isNotEmpty()) {
            MonoLabel("SEND OPTIONS")
            Spacer(Modifier.height(4.dp))
            InnerPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.recipients.forEach { r -> ContactRow(name = r.name, initials = r.initials, detail = r.address) }
                }
            }
        }
    }
}
