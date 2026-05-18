package com.google.research.air.cosmo.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardPayloadModelTest {

    @Test
    fun `all card payload subtypes are instantiable with defaults`() {
        val payloads: List<CardPayload> = listOf(
            CardPayload.Browser(query = "quantum computing"),
            CardPayload.Calling(contactName = "Alice", contactInitials = "AL"),
            CardPayload.Messaging(body = "Hello"),
            CardPayload.Document(title = "Notes"),
            CardPayload.ListTasks(title = "Groceries"),
            CardPayload.CalendarTime(),
            CardPayload.Navigation(destination = "Home"),
            CardPayload.MediaPlayback(query = "lofi"),
            CardPayload.PeopleContext(name = "Bob", initials = "BB", detail = "Engineer"),
            CardPayload.MediaImage(),
            CardPayload.SourceCitation(),
            CardPayload.PermissionConfirmation(prompt = "Send?", actionLabel = "Send"),
            CardPayload.Settings(),
            CardPayload.Onboarding(body = "Welcome")
        )
        assertEquals(14, payloads.size)
        payloads.forEach { assertNotNull(it) }
    }

    @Test
    fun `exhaustive when covers all subtypes`() {
        val payloads = listOf(
            CardPayload.Browser(query = "q"),
            CardPayload.Calling(contactName = "A", contactInitials = "A"),
            CardPayload.Messaging(body = "b"),
            CardPayload.Document(title = "t"),
            CardPayload.ListTasks(title = "l"),
            CardPayload.CalendarTime(),
            CardPayload.Navigation(destination = "d"),
            CardPayload.MediaPlayback(query = "q"),
            CardPayload.PeopleContext(name = "n", initials = "n", detail = "d"),
            CardPayload.MediaImage(),
            CardPayload.SourceCitation(),
            CardPayload.PermissionConfirmation(prompt = "p", actionLabel = "a"),
            CardPayload.Settings(),
            CardPayload.Onboarding(body = "b")
        )
        payloads.forEach { payload ->
            val label = when (payload) {
                is CardPayload.Browser -> "browser"
                is CardPayload.Calling -> "calling"
                is CardPayload.Messaging -> "messaging"
                is CardPayload.Document -> "document"
                is CardPayload.ListTasks -> "list"
                is CardPayload.CalendarTime -> "calendar"
                is CardPayload.Navigation -> "navigation"
                is CardPayload.MediaPlayback -> "mediaPlayback"
                is CardPayload.PeopleContext -> "people"
                is CardPayload.MediaImage -> "media"
                is CardPayload.SourceCitation -> "citation"
                is CardPayload.PermissionConfirmation -> "permission"
                is CardPayload.Settings -> "settings"
                is CardPayload.Onboarding -> "onboarding"
            }
            assertNotNull(label)
        }
    }

    @Test
    fun `Browser defaults to SOURCE_REVIEW tab`() {
        val browser = CardPayload.Browser(query = "test")
        assertEquals(BrowserTab.SOURCE_REVIEW, browser.activeTab)
    }

    @Test
    fun `CalendarTime defaults to 25min 0sec`() {
        val cal = CardPayload.CalendarTime()
        assertEquals(25, cal.focusMinutes)
        assertEquals(0, cal.focusSeconds)
    }

    @Test
    fun `PermissionConfirmation defaults to undoable`() {
        val perm = CardPayload.PermissionConfirmation(prompt = "Delete?", actionLabel = "Delete")
        assertEquals(true, perm.undoable)
    }

    @Test
    fun `PresenceCard wraps payload with actions`() {
        val card = PresenceCard(
            id = "c1",
            payload = CardPayload.Browser(query = "test"),
            actions = listOf(CardAction(id = "open", label = "Open"))
        )
        assertEquals("c1", card.id)
        assertEquals(1, card.actions.size)
    }
}
