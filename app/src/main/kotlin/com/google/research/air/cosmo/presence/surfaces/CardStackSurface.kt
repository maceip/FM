package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.cards.ActionButtonGrid
import com.google.research.air.cosmo.presence.surfaces.cards.BrowserContent
import com.google.research.air.cosmo.presence.surfaces.cards.CalendarTimeContent
import com.google.research.air.cosmo.presence.surfaces.cards.CallingContent
import com.google.research.air.cosmo.presence.surfaces.cards.DocumentContent
import com.google.research.air.cosmo.presence.surfaces.cards.ListTasksContent
import com.google.research.air.cosmo.presence.surfaces.cards.MediaImageContent
import com.google.research.air.cosmo.presence.surfaces.cards.MessagingContent
import com.google.research.air.cosmo.presence.surfaces.cards.PeopleContextContent
import com.google.research.air.cosmo.presence.surfaces.cards.PermissionConfirmationContent
import com.google.research.air.cosmo.presence.surfaces.cards.SourceCitationContent

@Composable
fun CardStackSurface(
    cards: List<PresenceCard>,
    modifier: Modifier = Modifier,
    onAction: (cardId: String, actionId: String) -> Unit = { _, _ -> }
) {
    Column(
        modifier = modifier.padding(horizontal = PresenceTokens.Spacing.cardPadding).testTag("cardStack"),
        verticalArrangement = Arrangement.spacedBy(PresenceTokens.Spacing.cardGap)
    ) {
        cards.forEach { card ->
            GlassPanel(
                showGlowEdge = true,
                modifier = Modifier.fillMaxWidth().testTag("card_${card.id}")
            ) {
                Column(Modifier.padding(PresenceTokens.Spacing.cardPadding)) {
                    CardContent(card.payload)
                    if (card.actions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(PresenceTokens.Palette.BorderDim))
                        Spacer(Modifier.height(12.dp))
                        ActionButtonGrid(card.id, card.actions, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContent(payload: CardPayload) {
    when (payload) {
        is CardPayload.Browser -> BrowserContent(payload)
        is CardPayload.Calling -> CallingContent(payload)
        is CardPayload.Messaging -> MessagingContent(payload)
        is CardPayload.Document -> DocumentContent(payload)
        is CardPayload.ListTasks -> ListTasksContent(payload)
        is CardPayload.CalendarTime -> CalendarTimeContent(payload)
        is CardPayload.PeopleContext -> PeopleContextContent(payload)
        is CardPayload.MediaImage -> MediaImageContent(payload)
        is CardPayload.SourceCitation -> SourceCitationContent(payload)
        is CardPayload.PermissionConfirmation -> PermissionConfirmationContent(payload)
    }
}
