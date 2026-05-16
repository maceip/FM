package com.google.research.air.cosmo.presence

import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import com.google.research.air.cosmo.fulfillment.requiresExecuteConfirmation

@Suppress("CyclomaticComplexMethod")
internal fun FulfillmentAction.toPresenceCard(): PresenceCard =
    PresenceCard(
        id = id,
        payload =
            when (this) {
                is FulfillmentAction.ActiveDataPlan ->
                    CardPayload.Document(title = title, preview = plan)
                is FulfillmentAction.AddTask ->
                    CardPayload.ListTasks(title = title, items = listOf(CheckItem(notes.ifBlank { title })))
                is FulfillmentAction.AddTimer ->
                    CardPayload.CalendarTime(
                        focusMinutes = durationSeconds / 60,
                        focusSeconds = durationSeconds % 60,
                        reminders = listOf(Reminder(formatDuration(durationSeconds), title)),
                        eventTitle = title
                    )
                is FulfillmentAction.AddToList ->
                    CardPayload.ListTasks(title = title, items = items.map(::CheckItem))
                is FulfillmentAction.AddToMemory ->
                    CardPayload.Document(title = title, preview = text)
                is FulfillmentAction.BrowsingAgent ->
                    CardPayload.Browser(query = prompt, activeTab = BrowserTab.BROWSING_AGENT)
                is FulfillmentAction.CallAgent ->
                    CardPayload.Calling(contactName = title, contactInitials = title.initials())
                is FulfillmentAction.CreateCalendarEvent ->
                    CardPayload.CalendarTime(
                        reminders = listOf(Reminder("${startMillis}-${endMillis}", location.ifBlank { title })),
                        eventTitle = title
                    )
                is FulfillmentAction.CreateDoc ->
                    CardPayload.Document(title = title, preview = body)
                is FulfillmentAction.DeepResearch ->
                    CardPayload.Browser(query = query, activeTab = BrowserTab.DEEP_RESEARCH)
                is FulfillmentAction.DefineTerm ->
                    CardPayload.Browser(query = term, activeTab = BrowserTab.TERM_SEARCH)
                is FulfillmentAction.DraftEmail ->
                    CardPayload.Messaging(
                        body = body,
                        recipients = listOf(Recipient(to, to, to.initials())),
                        channel = MessageChannel.EMAIL
                    )
                is FulfillmentAction.DraftSms ->
                    CardPayload.Messaging(
                        body = body,
                        recipients = listOf(Recipient(to, to, to.initials())),
                        channel = MessageChannel.SMS
                    )
                is FulfillmentAction.InvokeAgentQ ->
                    CardPayload.Browser(query = prompt, activeTab = BrowserTab.BROWSING_AGENT)
                is FulfillmentAction.Navigation ->
                    CardPayload.Browser(query = "Directions to $destination")
                is FulfillmentAction.PlayMusic ->
                    CardPayload.MediaImage(caption = query)
                is FulfillmentAction.ProvideEventContext ->
                    CardPayload.Document(title = title, docType = DocumentType.SUMMARY, preview = answer)
                is FulfillmentAction.ProvideInsight ->
                    CardPayload.Document(title = title, docType = DocumentType.REPORT, preview = answer)
                is FulfillmentAction.ProvidePersonContext ->
                    CardPayload.PeopleContext(name = title, initials = title.initials(), detail = answer)
                is FulfillmentAction.ProvideRecall ->
                    CardPayload.Document(title = title, docType = DocumentType.SUMMARY, preview = answer)
                is FulfillmentAction.QueryPhotos ->
                    CardPayload.MediaImage(caption = query)
                is FulfillmentAction.RemovedAction ->
                    CardPayload.PermissionConfirmation(prompt = title, actionLabel = "Removed", undoable = false)
                is FulfillmentAction.Unsupported ->
                    CardPayload.Document(title = title, preview = rawType)
                is FulfillmentAction.WebSearch ->
                    CardPayload.Browser(query = query, activeTab = BrowserTab.SOURCE_REVIEW)
            },
        actions = presenceActions()
    )

private fun FulfillmentAction.presenceActions(): List<CardAction> =
    if (requiresExecuteConfirmation()) {
        listOf(
            CardAction(
                id = "review:$id",
                label = "Review",
                sublabel = "Confirm in ActionDeck",
                type = CardActionType.PRIMARY
            )
        )
    } else {
        emptyList()
    }

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (remainingSeconds == 0) "${minutes}m" else "${minutes}m ${remainingSeconds}s"
}

private fun String.initials(): String =
    trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }
