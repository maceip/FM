package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.BrowserTab
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens

@Composable
internal fun BrowserContent(data: CardPayload.Browser) {
    Column(Modifier.testTag("browserCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "BROWSER", title = "DEEP RESEARCH")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BrowserTab.entries.forEach { tab ->
                val active = tab == data.activeTab
                Column {
                    Text(
                        text = tab.displayLabel,
                        color = if (active) PresenceTokens.Palette.CyanDim else PresenceTokens.Palette.OnSurfaceDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        Modifier
                            .width(if (active) 56.dp else 1.dp)
                            .height(1.dp)
                            .background(if (active) PresenceTokens.Palette.CyanDim else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
        if (data.query.isNotBlank()) {
            MonoLabel("ANSWER SNAPSHOT")
            Text(
                text = data.query,
                color = PresenceTokens.Palette.OnSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("TOP SOURCES")
            MonoLabel("CONFIDENCE")
        }
        data.sources.forEach { SourceRow(it) }
    }
}

private val BrowserTab.displayLabel: String
    get() =
        when (this) {
            BrowserTab.BROWSING_AGENT -> "BROWSING\nAGENT"
            BrowserTab.TERM_SEARCH -> "TERM\nSEARCH"
            BrowserTab.DEEP_RESEARCH -> "PROVENANCE"
            BrowserTab.SOURCE_REVIEW -> "SOURCE\nREVIEW"
        }
