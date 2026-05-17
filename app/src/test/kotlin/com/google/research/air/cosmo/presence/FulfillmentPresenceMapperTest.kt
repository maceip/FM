package com.google.research.air.cosmo.presence

import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FulfillmentPresenceMapperTest {
    @Test
    fun mapsTimerActionToCalendarPresenceCardWithActionDeckReview() {
        val card = FulfillmentAction.AddTimer("timer-1", "Set timer", 610).toPresenceCard()
        val payload = card.payload as CardPayload.CalendarTime

        assertEquals("timer-1", card.id)
        assertEquals(10, payload.focusMinutes)
        assertEquals(10, payload.focusSeconds)
        assertEquals("Set timer", payload.eventTitle)
        assertEquals(CardAction("review:timer-1", "Review", "Confirm in ActionDeck", CardActionType.PRIMARY), card.actions.single())
    }

    @Test
    fun mapsAllFulfillmentActionSubtypesToPresenceCards() {
        val cards = sampleActions().map { it.toPresenceCard() }

        assertEquals(sampleActions().size, cards.size)
        assertTrue(cards.map { it.payload }.any { it is CardPayload.Browser })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.CalendarTime })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.Document })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.ListTasks })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.Messaging })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.Navigation })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.MediaPlayback })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.MediaImage })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.PeopleContext })
        assertTrue(cards.map { it.payload }.any { it is CardPayload.PermissionConfirmation })
        assertTrue(cards.filter { it.actions.isNotEmpty() }.all { it.actions.single().label == "Review" })
    }

    private fun sampleActions(): List<FulfillmentAction> =
        listOf(
            FulfillmentAction.ActiveDataPlan("1", "Data", "mobile"),
            FulfillmentAction.AddTask("2", "Task", "Note"),
            FulfillmentAction.AddTimer("3", "Timer", 60),
            FulfillmentAction.AddToList("4", "List", listOf("eggs")),
            FulfillmentAction.AddToMemory("5", "Memory", "Remember"),
            FulfillmentAction.BrowsingAgent("6", "Browse", "Book"),
            FulfillmentAction.CallAgent("7", "Call Ada", "123"),
            FulfillmentAction.CreateCalendarEvent("8", "Meet", 10, 20),
            FulfillmentAction.CreateDoc("9", "Doc", "Body"),
            FulfillmentAction.DeepResearch("10", "Research", "Query"),
            FulfillmentAction.DefineTerm("11", "Define", "NPU"),
            FulfillmentAction.DraftEmail("12", "Email", "a@b.com", "Hi", "Body"),
            FulfillmentAction.DraftSms("13", "Sms", "123", "Body"),
            FulfillmentAction.InvokeAgentQ("14", "AgentQ", "Do it"),
            FulfillmentAction.Navigation("15", "Nav", "Home"),
            FulfillmentAction.PlayMusic("16", "Music", "lofi"),
            FulfillmentAction.ProvideEventContext("17", "Event", "Answer"),
            FulfillmentAction.ProvideInsight("18", "Insight", "Answer"),
            FulfillmentAction.ProvidePersonContext("19", "Ada", "Answer"),
            FulfillmentAction.ProvideRecall("20", "Recall", "Answer"),
            FulfillmentAction.QueryPhotos("21", "Photos", "Query"),
            FulfillmentAction.WebSearch("22", "Search", "Query"),
            FulfillmentAction.RemovedAction("23", "Removed"),
            FulfillmentAction.Unsupported("24", "Unsupported", "raw")
        )
}
