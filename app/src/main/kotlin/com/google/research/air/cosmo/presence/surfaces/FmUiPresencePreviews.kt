@file:Suppress("UnusedPrivateMember")

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
import com.google.research.air.cosmo.presence.CallStatus
import com.google.research.air.cosmo.presence.CardAction
import com.google.research.air.cosmo.presence.CardActionType
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.CheckItem
import com.google.research.air.cosmo.presence.CitationEntry
import com.google.research.air.cosmo.presence.DocumentType
import com.google.research.air.cosmo.presence.MessageChannel
import com.google.research.air.cosmo.presence.OnboardingCheckpoint
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.Recipient
import com.google.research.air.cosmo.presence.Reminder
import com.google.research.air.cosmo.presence.SettingItem
import com.google.research.air.cosmo.presence.Source
import com.google.research.air.cosmo.presence.Thumbnail

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
internal fun FmUiOrbListeningPreview() {
    FmUiCanvas(widthDp = 230, heightDp = 658, contentAlignment = Alignment.TopCenter) {
        OrbSurface(
            activity = OrbActivity.LISTENING,
            orbSize = 220.dp,
            animationPhase = 0f,
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
internal fun FmUiOrbWorkingPreview() {
    FmUiCanvas(widthDp = 230, heightDp = 658, contentAlignment = Alignment.TopCenter) {
        OrbSurface(
            activity = OrbActivity.IDLE,
            isWorking = true,
            orbSize = 220.dp,
            animationPhase = 0f,
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
internal fun FmUiOrbNeedsInputPreview() {
    FmUiCanvas(widthDp = 230, heightDp = 658, contentAlignment = Alignment.TopCenter) {
        OrbSurface(
            activity = OrbActivity.IDLE,
            needsInput = true,
            orbSize = 220.dp,
            animationPhase = 0f,
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
internal fun FmUiTabMinimizedPreview() {
    FmUiCanvas(widthDp = 30, heightDp = 300, contentAlignment = Alignment.Center) {
        TabSurface(
            expanded = false,
            animationPhase = 0.52f,
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
internal fun FmUiTabExpandedPreview() {
    FmUiCanvas(widthDp = 528, heightDp = 280, contentAlignment = Alignment.Center) {
        TabSurface(
            expanded = true,
            edgeDocked = false,
            animationPhase = 0f,
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
internal fun FmUiStackBrowserPreview() {
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
internal fun FmUiStackMessagingPreview() {
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
internal fun FmUiStackCalendarPreview() {
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
internal fun FmUiStackMobileContextPreview() {
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
internal fun FmUiStackFoldableContextPreview() {
    FmUiCanvas(widthDp = 1448, heightDp = 1086) {
        CardStackSurface(
            cards = previewStack,
            modifier = Modifier.offset(x = 670.dp, y = 96.dp)
        )
    }
}

@Preview(
    name = "fm_ui/stack/ten_card_overflow.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiStackTenCardOverflowPreview() {
    FmUiCanvas(widthDp = 1122, heightDp = 1402) {
        CardStackSurface(
            cards = previewOverflowStack,
            modifier = Modifier.offset(x = 386.dp, y = 96.dp)
        )
    }
}

@Preview(
    name = "fm_ui/cards/browser.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardBrowserPreview() {
    FmUiSingleCardPreview(browserCard)
}

@Preview(
    name = "fm_ui/cards/calling.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardCallingPreview() {
    FmUiSingleCardPreview(callingCard)
}

@Preview(
    name = "fm_ui/cards/messaging.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardMessagingPreview() {
    FmUiSingleCardPreview(messagingCard)
}

@Preview(
    name = "fm_ui/cards/document.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardDocumentPreview() {
    FmUiSingleCardPreview(documentCard)
}

@Preview(
    name = "fm_ui/cards/tasks.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardTasksPreview() {
    FmUiSingleCardPreview(tasksCard)
}

@Preview(
    name = "fm_ui/cards/calendar.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardCalendarPreview() {
    FmUiSingleCardPreview(calendarCard)
}

@Preview(
    name = "fm_ui/cards/navigation.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardNavigationPreview() {
    FmUiSingleCardPreview(navigationCard)
}

@Preview(
    name = "fm_ui/cards/media_playback.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardMediaPlaybackPreview() {
    FmUiSingleCardPreview(mediaPlaybackCard)
}

@Preview(
    name = "fm_ui/cards/people.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardPeoplePreview() {
    FmUiSingleCardPreview(peopleCard)
}

@Preview(
    name = "fm_ui/cards/media_image.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardMediaImagePreview() {
    FmUiSingleCardPreview(mediaImageCard)
}

@Preview(
    name = "fm_ui/cards/source_citation.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardSourceCitationPreview() {
    FmUiSingleCardPreview(sourceCitationCard)
}

@Preview(
    name = "fm_ui/cards/permission.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardPermissionPreview() {
    FmUiSingleCardPreview(permissionCard)
}

@Preview(
    name = "fm_ui/cards/settings.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardSettingsPreview() {
    FmUiSingleCardPreview(settingsCard)
}

@Preview(
    name = "fm_ui/cards/onboarding.png",
    widthDp = 1122,
    heightDp = 1402,
    showBackground = false
)
@Composable
internal fun FmUiCardOnboardingPreview() {
    FmUiSingleCardPreview(onboardingCard)
}

@Composable
private fun FmUiSingleCardPreview(card: PresenceCard) {
    FmUiCanvas(widthDp = 1122, heightDp = 1402) {
        CardStackSurface(
            cards = listOf(card),
            modifier = Modifier.offset(x = 386.dp, y = 132.dp)
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

private val callingCard =
    PresenceCard(
        id = "calling-target",
        payload =
            CardPayload.Calling(
                contactName = "A. Morgan",
                contactInitials = "AM",
                status = CallStatus.CONNECTED,
                transcript = "Agent: Hi Alex, I'm calling about the Q2 roadmap review.\nAlex: Great, I have a few notes to share."
            ),
        actions =
            listOf(
                CardAction("mute", "Mute", null),
                CardAction("note", "Add", "Note"),
                CardAction("confirm", "Confirm", "Action", CardActionType.PRIMARY)
            )
    )

private val documentCard =
    PresenceCard(
        id = "document-target",
        payload =
            CardPayload.Document(
                title = "Q2 Roadmap Review",
                docType = DocumentType.SUMMARY,
                preview = "Drafted an executive summary with project risks, launch blockers, and next-step owners.",
                outline = listOf("Key decisions", "Open risks", "Follow-up owners")
            ),
        actions =
            listOf(
                CardAction("open", "Open", "Doc"),
                CardAction("copy", "Copy", "Summary"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val tasksCard =
    PresenceCard(
        id = "tasks-target",
        payload =
            CardPayload.ListTasks(
                title = "ACTION ITEMS",
                items =
                    listOf(
                        CheckItem("Send summary to Alex", checked = true),
                        CheckItem("Schedule design review"),
                        CheckItem("Attach source citations")
                    )
            ),
        actions =
            listOf(
                CardAction("add", "Add", "Task"),
                CardAction("share", "Share", "List"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val navigationCard =
    PresenceCard(
        id = "navigation-target",
        payload =
            CardPayload.Navigation(
                destination = "Design Review Room",
                routeSummary = "Walk north through the studio, then take the east elevator to floor 4.",
                eta = "04 MIN"
            ),
        actions =
            listOf(
                CardAction("start", "Start", "Route", CardActionType.PRIMARY),
                CardAction("share", "Share", "ETA"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val mediaPlaybackCard =
    PresenceCard(
        id = "media-playback-target",
        payload =
            CardPayload.MediaPlayback(
                query = "Play the focus mix from yesterday",
                provider = "Spotify",
                queue = listOf("Neon Runway", "Late Night Debug", "Glass Circuit")
            ),
        actions =
            listOf(
                CardAction("play", "Play", "Now", CardActionType.PRIMARY),
                CardAction("queue", "Queue", "Next"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val peopleCard =
    PresenceCard(
        id = "people-target",
        payload =
            CardPayload.PeopleContext(
                name = "Alex Morgan",
                initials = "AM",
                detail = "alex@quantumlab.com",
                role = "Research partner • prefers concise source-backed summaries"
            ),
        actions =
            listOf(
                CardAction("message", "Message", "Alex"),
                CardAction("profile", "Open", "Profile"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val mediaImageCard =
    PresenceCard(
        id = "media-image-target",
        payload =
            CardPayload.MediaImage(
                thumbnails =
                    listOf(
                        Thumbnail("FIG 01"),
                        Thumbnail("FIG 02"),
                        Thumbnail("FIG 03")
                    ),
                caption = "Three reference images found for the quantum explainer deck."
            ),
        actions =
            listOf(
                CardAction("insert", "Insert", "Images"),
                CardAction("review", "Review", "Set"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val sourceCitationCard =
    PresenceCard(
        id = "source-citation-target",
        payload =
            CardPayload.SourceCitation(
                citations =
                    listOf(
                        CitationEntry("What is Quantum Computing? | IBM", "ibm.com", 98, "Primary definition source"),
                        CitationEntry("A Gentle Introduction to Qubits", "quantamagazine.org", 91, "Background reference")
                    )
            ),
        actions =
            listOf(
                CardAction("copy", "Copy", "Cites"),
                CardAction("open", "Open", "Sources"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val permissionCard =
    PresenceCard(
        id = "permission-target",
        payload =
            CardPayload.PermissionConfirmation(
                prompt = "Allow Cosmo to paste the drafted reply into the current app?",
                actionLabel = "Allow Paste"
            )
    )

private val settingsCard =
    PresenceCard(
        id = "settings-target",
        payload =
            CardPayload.Settings(
                items =
                    listOf(
                        SettingItem("Dark Mode", enabled = true),
                        SettingItem("Accent Color", enabled = true, value = "Blue"),
                        SettingItem("UI Animation", enabled = true),
                        SettingItem("Source Cache", enabled = false),
                        SettingItem("Location Access", enabled = false)
                    )
            ),
        actions =
            listOf(
                CardAction("search", "Search", "Settings"),
                CardAction("reset", "Reset", "Defaults"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val onboardingCard =
    PresenceCard(
        id = "onboarding-target",
        payload =
            CardPayload.Onboarding(
                body = "Get started by setting up research, connecting contacts, and creating your first action list.",
                checkpoints =
                    listOf(
                        OnboardingCheckpoint("Research", complete = true),
                        OnboardingCheckpoint("Contacts", complete = true),
                        OnboardingCheckpoint("Settings", complete = true),
                        OnboardingCheckpoint("First Task")
                    )
            ),
        actions =
            listOf(
                CardAction("open", "Open", "Browser"),
                CardAction("sources", "Better", "Sources"),
                CardAction("collapse", "Collapse", "To Edge", CardActionType.COLLAPSE)
            )
    )

private val previewStack = listOf(browserCard, messagingCard, calendarCard)

private val previewOverflowStack =
    listOf(
        browserCard,
        messagingCard,
        calendarCard,
        callingCard,
        documentCard,
        tasksCard,
        navigationCard,
        mediaPlaybackCard,
        peopleCard,
        permissionCard
    )
