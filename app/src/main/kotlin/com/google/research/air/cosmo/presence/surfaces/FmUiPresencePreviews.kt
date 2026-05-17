package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.BrowserTab
import com.google.research.air.cosmo.presence.CardAction
import com.google.research.air.cosmo.presence.CardActionType
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.MessageChannel
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.Recipient
import com.google.research.air.cosmo.presence.Reminder
import com.google.research.air.cosmo.presence.Source

@Composable
private fun FmUiCanvas(
    widthDp: Int,
    heightDp: Int,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(widthDp.dp, heightDp.dp)
            .background(Color.Black),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Preview(
    name = "fm_ui/orb/variant1.png",
    widthDp = 230,
    heightDp = 658,
    showBackground = false
)
@Composable
private fun FmUiOrbListeningPreview() {
    FmUiCanvas(widthDp = 230, heightDp = 658, contentAlignment = Alignment.TopCenter) {
        OrbSurface(
            activity = OrbActivity.LISTENING,
            orbSize = 220.dp,
            modifier = Modifier.offset(y = 8.dp)
        )
    }
}

@Preview(
    name = "fm_ui/orb/variant2.png",
    widthDp = 230,
    heightDp = 658,
    showBackground = false
)
@Composable
private fun FmUiOrbWorkingPreview() {
    FmUiCanvas(widthDp = 230, heightDp = 658, contentAlignment = Alignment.TopCenter) {
        OrbSurface(
            activity = OrbActivity.IDLE,
            isWorking = true,
            orbSize = 220.dp,
            modifier = Modifier.offset(y = 230.dp)
        )
    }
}

@Preview(
    name = "fm_ui/orb/variant3.png",
    widthDp = 230,
    heightDp = 658,
    showBackground = false
)
@Composable
private fun FmUiOrbNeedsInputPreview() {
    FmUiCanvas(widthDp = 230, heightDp = 658, contentAlignment = Alignment.TopCenter) {
        OrbSurface(
            activity = OrbActivity.IDLE,
            needsInput = true,
            orbSize = 220.dp,
            modifier = Modifier.offset(y = 430.dp)
        )
    }
}

@Preview(
    name = "fm_ui/tab/minimized.png",
    widthDp = 30,
    heightDp = 300,
    showBackground = false
)
@Composable
private fun FmUiTabMinimizedPreview() {
    FmUiCanvas(widthDp = 30, heightDp = 300, contentAlignment = Alignment.Center) {
        TabSurface(
            expanded = false,
            modifier = Modifier.scale(scaleX = 1.65f, scaleY = 1.55f)
        )
    }
}

@Preview(
    name = "fm_ui/tab/expanded.png",
    widthDp = 528,
    heightDp = 280,
    showBackground = false
)
@Composable
private fun FmUiTabExpandedPreview() {
    FmUiCanvas(widthDp = 528, heightDp = 280, contentAlignment = Alignment.Center) {
        TabSurface(
            expanded = true,
            edgeDocked = false,
            modifier = Modifier.scale(scaleX = 1.5f, scaleY = 1.55f)
        )
    }
}

@Preview(
    name = "fm_ui/stack/variant1.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
private fun FmUiStackBrowserPreview() {
    FmUiCanvas(widthDp = 1122, heightDp = 1402) {
        CardStackSurface(
            cards = listOf(browserCard),
            modifier = Modifier.offset(x = 386.dp, y = 132.dp)
        )
    }
}

@Preview(
    name = "fm_ui/stack/variant2.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
private fun FmUiStackMessagingPreview() {
    FmUiCanvas(widthDp = 1122, heightDp = 1402) {
        CardStackSurface(
            cards = listOf(messagingCard),
            modifier = Modifier.offset(x = 386.dp, y = 520.dp)
        )
    }
}

@Preview(
    name = "fm_ui/stack/variant3.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
private fun FmUiStackCalendarPreview() {
    FmUiCanvas(widthDp = 1122, heightDp = 1402) {
        CardStackSurface(
            cards = listOf(calendarCard),
            modifier = Modifier.offset(x = 386.dp, y = 830.dp)
        )
    }
}

@Preview(
    name = "fm_ui/stack/mobile.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
private fun FmUiStackMobileContextPreview() {
    FmUiCanvas(widthDp = 1122, heightDp = 1402) {
        CardStackSurface(
            cards = previewStack,
            modifier = Modifier.offset(x = 386.dp, y = 150.dp)
        )
    }
}

@Preview(
    name = "fm_ui/stack/foldable.png",
    widthDp = 1448,
    heightDp = 1086,
    showBackground = false
)
@Composable
private fun FmUiStackFoldableContextPreview() {
    FmUiCanvas(widthDp = 1448, heightDp = 1086) {
        CardStackSurface(
            cards = previewStack,
            modifier = Modifier.offset(x = 670.dp, y = 96.dp)
        )
    }
}

private val browserCard =
    PresenceCard(
        id = "browser-target",
        payload =
            CardPayload.Browser(
                query = "Quantum computing uses qubits and superposition to solve certain problems exponentially faster than classical systems.",
                activeTab = BrowserTab.SOURCE_REVIEW,
                sources =
                    listOf(
                        Source("What is Quantum Computing? | IBM", "ibm.com", 98, "IBM"),
                        Source("A Gentle Introduction to Qubits", "quantamagazine.org", 91, "Q"),
                        Source("Quantum Computing Explained Simply", "youtube.com", 88, "YT")
                    )
            ),
        actions =
            listOf(
                CardAction("open", "Open", "In Browser"),
                CardAction("review", "Review", "Sources"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val messagingCard =
    PresenceCard(
        id = "messaging-target",
        payload =
            CardPayload.Messaging(
                body = "Here are the key findings from my research on quantum computing.\nLet me know your thoughts.",
                recipients = listOf(Recipient("Alex Morgan", "alex@quantumlab.com", "AM")),
                channel = MessageChannel.EMAIL
            ),
        actions =
            listOf(
                CardAction("send", "Send", "Now", CardActionType.PRIMARY),
                CardAction("choose", "Choose", "App"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val calendarCard =
    PresenceCard(
        id = "calendar-target",
        payload =
            CardPayload.CalendarTime(
                focusMinutes = 25,
                focusSeconds = 0,
                reminders =
                    listOf(
                        Reminder("09:00", "Stand Up"),
                        Reminder("11:00", "Design Review"),
                        Reminder("15:30", "Client Sync")
                    )
            ),
        actions =
            listOf(
                CardAction("start", "Start", "Timer", CardActionType.PRIMARY),
                CardAction("reminder", "Add", "Reminder"),
                CardAction("event", "Create", "Event")
            )
    )

private val previewStack = listOf(browserCard, messagingCard, calendarCard)
