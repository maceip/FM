package com.google.research.air.cosmo.fulfillment

import androidx.test.core.app.ApplicationProvider
import com.google.research.air.cosmo.lib.llm.SimulationLLM
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.setting.CosmoSettingStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FulfillmentRunnerTest {

    private val simulationLlm = SimulationLLM()
    private val mockSettings = mockk<CosmoSettingStore>(relaxed = true)
    private val mockClock = mockk<Clock>()
    private val mockTelemetry = mockk<CosmosTelemetry>(relaxed = true)

    private fun runner(): FulfillmentRunner {
        every { mockSettings.isGatekeeperEnabled() } returns false
        every { mockSettings.isRawAudioGatekeeperEnabled() } returns false
        every { mockSettings.isVoiceMatchEnabled() } returns false
        every { mockClock.millis() } returns 1_746_000_000_000L
        return FulfillmentRunner(
            context = ApplicationProvider.getApplicationContext(),
            audioBuffer = mockk(relaxed = true),
            screenBuffer = mockk(relaxed = true),
            notificationBuffer = mockk(relaxed = true),
            intentBuffer = mockk(relaxed = true),
            actionsRepository = ActionsRepository(),
            llm = simulationLlm,
            settings = mockSettings,
            clock = mockClock,
            telemetry = mockTelemetry
        )
    }

    @Test
    fun `fulfill with SimulationLLM produces at least one actionable response`() = runTest {
        val actions = runner().fulfill("Play some music for focus", FulfillmentTriggerType.USER_QUERY)

        assertTrue("SimulationLLM should return at least one action", actions.isNotEmpty())
        assertTrue(actions.none { it is FulfillmentAction.Unsupported })
    }

    @Test
    fun `parseResponse supports every schema action subtype plus removed`() {
        val actions =
            runner().parseResponse(
                """
                [
                  {"action_type":"ACTION_TYPE_ADD_TO_MEMORY","id":"1","title":"Memory","text":"Remember this"},
                  {"action_type":"ACTION_TYPE_ADD_TO_LIST","id":"2","title":"List","items":["eggs","milk"]},
                  {"action_type":"ACTION_TYPE_CREATE_CALENDAR_EVENT","id":"3","title":"Meet","start_millis":10,"end_millis":20},
                  {"action_type":"ACTION_TYPE_WEB_SEARCH","id":"4","title":"Search","query":"cosmo"},
                  {"action_type":"ACTION_TYPE_DEFINE_TERM","id":"5","title":"Define","term":"NPU"},
                  {"action_type":"ACTION_TYPE_QUERY_PHOTOS","id":"6","title":"Photos","query":"receipts"},
                  {"action_type":"ACTION_TYPE_CREATE_DOC","id":"7","title":"Doc","body":"Body"},
                  {"action_type":"ACTION_TYPE_PROVIDE_PERSON_CONTEXT","id":"8","title":"Person","answer":"Ada"},
                  {"action_type":"ACTION_TYPE_PROVIDE_EVENT_CONTEXT","id":"9","title":"Event","answer":"Agenda"},
                  {"action_type":"ACTION_TYPE_DEEP_RESEARCH","id":"10","title":"Research","query":"market"},
                  {"action_type":"ACTION_TYPE_PROVIDE_INSIGHT","id":"11","title":"Insight","answer":"Useful"},
                  {"action_type":"ACTION_TYPE_BROWSING_AGENT","id":"12","title":"Browse","prompt":"book"},
                  {"action_type":"ACTION_TYPE_ADD_TIMER","id":"13","title":"Timer","duration_seconds":60},
                  {"action_type":"ACTION_TYPE_CALL_AGENT","id":"14","title":"Call","phone_number":"123"},
                  {"action_type":"ACTION_TYPE_PROVIDE_RECALL","id":"15","title":"Recall","answer":"Memory"},
                  {"action_type":"ACTION_TYPE_NAVIGATION","id":"16","title":"Nav","destination":"home"},
                  {"action_type":"ACTION_TYPE_INVOKE_AGENT_Q","id":"17","title":"AgentQ","prompt":"do it"},
                  {"action_type":"ACTION_TYPE_DRAFT_EMAIL","id":"18","title":"Email","to":"a@b.com","subject":"Hi","body":"Body"},
                  {"action_type":"ACTION_TYPE_ADD_TASK","id":"19","title":"Task","notes":"Note"},
                  {"action_type":"ACTION_TYPE_DRAFT_SMS","id":"20","title":"SMS","to":"123","body":"Yo"},
                  {"action_type":"ACTION_TYPE_PLAY_MUSIC","id":"21","title":"Music","query":"lofi"},
                  {"action_type":"ACTION_TYPE_ACTIVE_DATA_PLAN","id":"22","title":"Data","plan":"mobile"},
                  {"action_type":"ACTION_TYPE_WEB_SEARCH","id":"23","title":"Remove","status":"REMOVED"}
                ]
                """.trimIndent()
            )

        assertTrue(actions.size == 23)
        assertTrue(actions.any { it is FulfillmentAction.BrowsingAgent })
        assertTrue(actions.any { it is FulfillmentAction.InvokeAgentQ })
        assertTrue(actions.any { it is FulfillmentAction.RemovedAction })
        assertTrue(actions.none { it is FulfillmentAction.Unsupported })
    }

    @Test
    fun `parseResponse drops create_calendar_event when window is invalid`() {
        val actions =
            runner().parseResponse(
                """
                [
                  {"action_type":"ACTION_TYPE_CREATE_CALENDAR_EVENT","id":"bad","title":"Bad","start_millis":0,"end_millis":0}
                ]
                """.trimIndent()
            )
        assertTrue(actions.isEmpty())
        verify {
            mockTelemetry.log(
                match {
                    it is TelemetryEvent.ActionParseRejected &&
                        it.params.getString("action_type") == "create_calendar_event" &&
                        it.params.getString("reason") == "invalid_time_window"
                }
            )
        }
    }

    @Test
    fun `parseResponse rejects missing required fields instead of defaulting`() {
        val actions =
            runner().parseResponse(
                """
                [
                  {"action_type":"ACTION_TYPE_ADD_TIMER","id":"1","title":"Timer"},
                  {"action_type":"ACTION_TYPE_WEB_SEARCH","id":"2","title":"Search"},
                  {"action_type":"ACTION_TYPE_DRAFT_EMAIL","id":"3","title":"Email","to":"a@b.com"},
                  {"action_type":"ACTION_TYPE_ADD_TO_LIST","id":"4","title":"List","items":[]}
                ]
                """.trimIndent()
            )

        assertTrue(actions.isEmpty())
        verify {
            mockTelemetry.log(match { it is TelemetryEvent.ActionParseRejected && it.params.getString("reason") == "missing_duration_seconds" })
            mockTelemetry.log(match { it is TelemetryEvent.ActionParseRejected && it.params.getString("reason") == "missing_query" })
            mockTelemetry.log(match { it is TelemetryEvent.ActionParseRejected && it.params.getString("reason") == "missing_body" })
            mockTelemetry.log(match { it is TelemetryEvent.ActionParseRejected && it.params.getString("reason") == "missing_items" })
        }
    }

    @Test
    fun `confirmation policy covers every fulfillment action subtype`() {
        val actions =
            listOf(
                FulfillmentAction.ActiveDataPlan("1", "Data", "mobile"),
                FulfillmentAction.AddTask("2", "Task"),
                FulfillmentAction.AddTimer("3", "Timer", 60),
                FulfillmentAction.AddToList("4", "List", listOf("eggs")),
                FulfillmentAction.AddToMemory("5", "Memory", "Remember"),
                FulfillmentAction.BrowsingAgent("6", "Browse", "Book"),
                FulfillmentAction.CallAgent("7", "Call", "123"),
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
                FulfillmentAction.ProvidePersonContext("19", "Person", "Answer"),
                FulfillmentAction.ProvideRecall("20", "Recall", "Answer"),
                FulfillmentAction.QueryPhotos("21", "Photos", "Query"),
                FulfillmentAction.WebSearch("22", "Search", "Query"),
                FulfillmentAction.RemovedAction("23", "Removed"),
                FulfillmentAction.Unsupported("24", "Unsupported", "raw")
            )
        val noConfirmation =
            setOf(
                "provide_event_context",
                "provide_insight",
                "provide_person_context",
                "provide_recall",
                "removed",
                "unsupported"
            )

        actions.forEach { action ->
            assertEquals(
                action.actionTypeName(),
                action.actionTypeName() !in noConfirmation,
                action.requiresExecuteConfirmation()
            )
        }
    }
}
