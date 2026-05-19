package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.CardPayload

@Composable
internal fun ListTasksContent(data: CardPayload.ListTasks, onToggle: (Int, Boolean) -> Unit = { _, _ -> }) {
    Column(Modifier.testTag("listTasksCard"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CardHeader(categoryLabel = "LISTING", title = data.title)
        data.items.forEachIndexed { index, item ->
            CheckItemRow(item = item, onToggle = { checked -> onToggle(index, checked) })
        }
    }
}
