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
import com.google.research.air.cosmo.presence.DocumentType
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun DocumentContent(data: CardPayload.Document) {
    Column(Modifier.testTag("documentCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val title = if (data.docType == DocumentType.REPORT) "DOCUMENT OVERVIEW" else data.docType.name
        CardHeader(categoryLabel = if (data.docType == DocumentType.REPORT) "REVIEWING" else "DOCUMENT", title = title)
        MonoLabel(data.title)
        if (data.preview.isNotBlank()) {
            InnerPanel {
                Text(data.preview, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp, maxLines = 6)
            }
        }
        if (data.outline.isNotEmpty()) {
            MonoLabel("OUTLINE")
            Spacer(Modifier.height(4.dp))
            data.outline.forEachIndexed { i, section ->
                Text("${i + 1}. $section", color = PresenceTokens.Palette.OnSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
