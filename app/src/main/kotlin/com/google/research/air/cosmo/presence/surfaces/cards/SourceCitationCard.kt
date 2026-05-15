package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.CardPayload

@Composable
internal fun SourceCitationContent(data: CardPayload.SourceCitation) {
    Column(Modifier.testTag("sourceCitationCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "SOURCES", title = "CITATIONS")
        MonoLabel("PROVENANCE")
        data.citations.forEach { CitationRow(it) }
    }
}
