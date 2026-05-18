package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun BrowserContent(data: CardPayload.Browser) {
    Column(Modifier.testTag("browserCard"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardHeader(categoryLabel = "BROWSER", title = "DEEP RESEARCH")
        BrowserModeTabs(activeTab = data.activeTab)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("TOP SOURCES")
            MonoLabel("CONFIDENCE")
        }
        data.sources.forEach { SourceRow(it) }
        InnerPanel(
            Modifier
                .fillMaxWidth()
                .clickable { }
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "WHY THIS RESULT?",
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = ">",
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun BrowserModeTabs(activeTab: BrowserTab) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BrowserTab.entries.filterNot { it == BrowserTab.DEEP_RESEARCH }.forEach { tab ->
            Column {
                Text(
                    text = tab.name.replace('_', ' '),
                    color = if (tab == activeTab) PresenceTokens.Palette.CyanDim else PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                if (tab == activeTab) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .width(48.dp)
                            .height(1.dp)
                            .background(PresenceTokens.Palette.CyanDim.copy(alpha = 0.72f))
                    )
                }
            }
        }
    }
}
