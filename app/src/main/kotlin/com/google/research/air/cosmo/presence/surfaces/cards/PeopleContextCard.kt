package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceTokens

@Composable
internal fun PeopleContextContent(data: CardPayload.PeopleContext) {
    Column(Modifier.testTag("peopleContextCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "PEOPLE", title = "CONTACT")
        ContactRow(name = data.name, initials = data.initials, detail = data.detail)
        data.role?.let {
            MonoLabel("ROLE")
            Text(it, color = PresenceTokens.Palette.OnSurfaceVariant, fontSize = 11.sp)
        }
    }
}
