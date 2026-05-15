package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun CalendarTimeContent(data: CardPayload.CalendarTime) {
    Column(Modifier.testTag("calendarTimeCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "CALENDAR", title = data.eventTitle ?: "CALENDAR / TIMER")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InnerPanel(Modifier.weight(1f)) {
                Column {
                    MonoLabel("FOCUS SESSION")
                    Spacer(Modifier.height(8.dp))
                    TimerDisplay(minutes = data.focusMinutes, seconds = data.focusSeconds)
                }
            }
            if (data.reminders.isNotEmpty()) {
                InnerPanel(Modifier.weight(1.5f)) {
                    Column {
                        MonoLabel("REMINDERS")
                        Spacer(Modifier.height(8.dp))
                        data.reminders.forEach { ReminderRow(it.time, it.label) }
                    }
                }
            }
        }
    }
}
