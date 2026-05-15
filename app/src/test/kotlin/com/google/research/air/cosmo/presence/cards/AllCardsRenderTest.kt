package com.google.research.air.cosmo.presence.cards

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.google.research.air.cosmo.presence.CardAction
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.CheckItem
import com.google.research.air.cosmo.presence.CitationEntry
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.Recipient
import com.google.research.air.cosmo.presence.Reminder
import com.google.research.air.cosmo.presence.Source
import com.google.research.air.cosmo.presence.Thumbnail
import com.google.research.air.cosmo.presence.surfaces.CardStackSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllCardsRenderTest {

    @get:Rule val rule = createComposeRule()

    private val samplePayloads: List<Pair<String, CardPayload>> = listOf(
        "browser" to CardPayload.Browser(
            query = "quantum",
            sources = listOf(Source("IBM", "ibm.com", 98, "IBM")),
        ),
        "calling" to CardPayload.Calling(contactName = "Alice", contactInitials = "AL"),
        "messaging" to CardPayload.Messaging(
            body = "Draft text",
            recipients = listOf(Recipient("Bob", "bob@test.com", "BB"))
        ),
        "document" to CardPayload.Document(title = "Notes", preview = "Some content"),
        "listTasks" to CardPayload.ListTasks(
            title = "Groceries",
            items = listOf(CheckItem("Milk"), CheckItem("Eggs", checked = true))
        ),
        "calendarTime" to CardPayload.CalendarTime(
            reminders = listOf(Reminder("09:00", "Stand Up"))
        ),
        "peopleContext" to CardPayload.PeopleContext(name = "Carol", initials = "CC", detail = "PM"),
        "mediaImage" to CardPayload.MediaImage(
            thumbnails = listOf(Thumbnail("img1"), Thumbnail("img2")),
            caption = "Results"
        ),
        "sourceCitation" to CardPayload.SourceCitation(
            citations = listOf(CitationEntry("Paper A", "https://a.com", 92, "Relevant"))
        ),
        "permission" to CardPayload.PermissionConfirmation(prompt = "Send?", actionLabel = "Send")
    )

    @Test
    fun `all 10 card types render without crash`() {
        val cards = samplePayloads.mapIndexed { i, (id, payload) ->
            PresenceCard(id = id, payload = payload, actions = listOf(CardAction("act", "Action")))
        }
        rule.setContent {
            CardStackSurface(cards = cards)
        }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodesWithTag("cardStack").fetchSemanticsNodes().isNotEmpty()
        }
        rule.mainClock.advanceTimeBy(3000)
        rule.waitForIdle()
        rule.onNodeWithTag("cardStack").assertExists()
        samplePayloads.forEach { (id, _) ->
            rule.onNodeWithTag("card_$id").assertExists()
        }
    }

    @Test
    fun `browser card shows source row`() {
        val card = PresenceCard(
            id = "b1",
            payload = CardPayload.Browser(
                query = "test",
                sources = listOf(Source("IBM Paper", "ibm.com", 98, "IBM"))
            )
        )
        rule.setContent { CardStackSurface(cards = listOf(card)) }
        rule.onNodeWithTag("browserCard").assertExists()
        rule.onNodeWithTag("source_IBM Paper").assertExists()
    }

    @Test
    fun `calling card shows contact`() {
        val card = PresenceCard(
            id = "c1",
            payload = CardPayload.Calling(contactName = "Alice", contactInitials = "AL")
        )
        rule.setContent { CardStackSurface(cards = listOf(card)) }
        rule.onNodeWithTag("callingCard").assertExists()
        rule.onNodeWithTag("contact_Alice").assertExists()
    }

    @Test
    fun `list tasks card shows check items`() {
        val card = PresenceCard(
            id = "l1",
            payload = CardPayload.ListTasks(title = "TODO", items = listOf(CheckItem("Item A"), CheckItem("Item B", true)))
        )
        rule.setContent { CardStackSurface(cards = listOf(card)) }
        rule.onNodeWithTag("listTasksCard").assertExists()
        rule.onNodeWithTag("check_Item A").assertExists()
        rule.onNodeWithTag("check_Item B").assertExists()
    }

    @Test
    fun `permission card shows confirm and undo`() {
        val card = PresenceCard(
            id = "p1",
            payload = CardPayload.PermissionConfirmation(prompt = "Delete file?", actionLabel = "Delete", undoable = true)
        )
        rule.setContent { CardStackSurface(cards = listOf(card)) }
        rule.onNodeWithTag("permissionCard").assertExists()
    }
}
