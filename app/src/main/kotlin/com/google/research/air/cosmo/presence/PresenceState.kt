package com.google.research.air.cosmo.presence

import java.time.Instant

// region Presence state machine

sealed interface PresenceState {
    data object Hidden : PresenceState
    data class Parked(val mode: PresenceMode, val activity: OrbActivity) : PresenceState
    data class Working(val taskId: String, val phase: WorkPhase, val pillText: String? = null) : PresenceState
    data class NeedsInput(val taskId: String, val prompt: String) : PresenceState
    data class Ready(val taskId: String, val cards: List<PresenceCard>, val primaryAction: CardAction? = null) : PresenceState
    data class Dismissed(val until: Instant) : PresenceState
}

enum class PresenceMode { ORB, TAB }
enum class OrbActivity { IDLE, LISTENING, RECENTLY_DISMISSED }
enum class WorkPhase { STARTED, SYNCING, PROCESSING, FINALIZING }

// endregion

// region Card container

data class PresenceCard(
    val id: String,
    val payload: CardPayload,
    val actions: List<CardAction> = emptyList()
)

data class CardAction(
    val id: String,
    val label: String,
    val sublabel: String? = null,
    val type: CardActionType = CardActionType.DEFAULT
)

enum class CardActionType { DEFAULT, PRIMARY, DESTRUCTIVE, COLLAPSE }

// endregion

// region Card capability payloads (10 sealed subtypes)

sealed interface CardPayload {

    data class Browser(
        val query: String,
        val sources: List<Source> = emptyList(),
        val activeTab: BrowserTab = BrowserTab.SOURCE_REVIEW
    ) : CardPayload

    data class Calling(
        val contactName: String,
        val contactInitials: String,
        val status: CallStatus = CallStatus.IDLE,
        val transcript: String? = null
    ) : CardPayload

    data class Messaging(
        val body: String,
        val recipients: List<Recipient> = emptyList(),
        val channel: MessageChannel = MessageChannel.DEFAULT
    ) : CardPayload

    data class Document(
        val title: String,
        val docType: DocumentType = DocumentType.NOTE,
        val preview: String = "",
        val outline: List<String> = emptyList()
    ) : CardPayload

    data class ListTasks(
        val title: String,
        val items: List<CheckItem> = emptyList()
    ) : CardPayload

    data class CalendarTime(
        val focusMinutes: Int = 25,
        val focusSeconds: Int = 0,
        val reminders: List<Reminder> = emptyList(),
        val eventTitle: String? = null
    ) : CardPayload

    data class PeopleContext(
        val name: String,
        val initials: String,
        val detail: String,
        val role: String? = null
    ) : CardPayload

    data class MediaImage(
        val thumbnails: List<Thumbnail> = emptyList(),
        val caption: String? = null
    ) : CardPayload

    data class SourceCitation(
        val citations: List<CitationEntry> = emptyList()
    ) : CardPayload

    data class PermissionConfirmation(
        val prompt: String,
        val actionLabel: String,
        val undoable: Boolean = true
    ) : CardPayload
}

// endregion

// region Supporting data models

data class Source(
    val title: String,
    val subtitle: String,
    val confidence: Int,
    val iconLabel: String? = null
)

data class Recipient(val name: String, val address: String, val initials: String)

data class Reminder(val time: String, val label: String)

data class CheckItem(val text: String, val checked: Boolean = false)

data class Thumbnail(val label: String, val aspectRatio: Float = 1f)

data class CitationEntry(
    val title: String,
    val url: String,
    val confidence: Int,
    val reason: String? = null
)

// endregion

// region Supporting enums

enum class BrowserTab { BROWSING_AGENT, TERM_SEARCH, DEEP_RESEARCH, SOURCE_REVIEW }

enum class CallStatus { IDLE, RINGING, CONNECTED, ENDED }

enum class MessageChannel { DEFAULT, SMS, EMAIL, CLIPBOARD }

enum class DocumentType { NOTE, REPORT, OUTLINE, SUMMARY }

// endregion
