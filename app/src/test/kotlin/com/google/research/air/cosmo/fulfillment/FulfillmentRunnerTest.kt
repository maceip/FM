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

    private fun runner(actionsRepository: ActionsRepository = ActionsRepository()): FulfillmentRunner {
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
            actionsRepository = actionsRepository,
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
    fun `user timer query produces ten minute add timer action`() = runTest {
        val actions =
            runner().fulfill(
                prompt = "Reactive fulfillment. Conversation:",
                trigger = FulfillmentTriggerType.USER_QUERY,
                userQuery = "set a timer for 10 minutes"
            )

        val timer = actions.single() as FulfillmentAction.AddTimer
        assertEquals("sim-timer", timer.id)
        assertEquals(600, timer.durationSeconds)
        assertEquals("Set 10 minute timer", timer.title)
    }

    @Test
    fun `parity fixtures use existing fulfillment action path`() = runTest {
        parityFixtures().forEach { fixture ->
            val repository = ActionsRepository()
            val actions =
                runner(repository).fulfill(
                    prompt = "Reactive fulfillment. Conversation:",
                    trigger = FulfillmentTriggerType.USER_QUERY,
                    userQuery = fixture.query
                )
            val action = actions.single()

            assertEquals(fixture.actionType, action.actionTypeName())
            fixture.verify(action)
            assertEquals(listOf(action), repository.userFacingActions.value)

            val history = repository.fulfillmentHistory.value.single()
            assertEquals(FulfillmentTriggerType.USER_QUERY, history.fulfillmentTriggerType)
            assertEquals(fixture.query, history.userQuery)
            assertTrue(history.llmQuery.contains("User query: ${fixture.query}"))
            assertEquals(listOf(action.id), history.actionIds)
            assertEquals(1, history.numNewActions)

            if (action.requiresExecuteConfirmation()) {
                repository.markActionAsExecuted(action.id)
                assertTrue(repository.userFacingActions.value.none { it.id == action.id })
                assertTrue(
                    repository.executedActions.value.any {
                        it.id == action.id && it.status == FulfillmentAction.Status.EXECUTED
                    }
                )
            }
        }
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

    private fun parityFixtures(): List<ParityFixture> =
        listOf(
            ParityFixture("set a timer for 10 minutes", "add_timer") { action ->
                val timer = action as FulfillmentAction.AddTimer
                assertEquals(600, timer.durationSeconds)
            },
            ParityFixture("add eggs and milk to my grocery list", "add_to_list") { action ->
                val list = action as FulfillmentAction.AddToList
                assertEquals(listOf("eggs", "milk"), list.items)
            },
            ParityFixture("put a team sync on my calendar", "create_calendar_event") { action ->
                val event = action as FulfillmentAction.CreateCalendarEvent
                assertEquals("Team sync", event.title)
                assertTrue(event.endMillis > event.startMillis)
            },
            ParityFixture("draft a document about launch risks", "create_doc") { action ->
                val doc = action as FulfillmentAction.CreateDoc
                assertEquals("Draft project brief", doc.title)
                assertTrue(doc.body.contains("Project brief"))
            },
            ParityFixture("search the web for current weather", "web_search") { action ->
                val search = action as FulfillmentAction.WebSearch
                assertEquals("current weather", search.query)
            },
            ParityFixture("define this jargon NPU", "define_term") { action ->
                val definition = action as FulfillmentAction.DefineTerm
                assertEquals("NPU", definition.term)
            },
            ParityFixture("give me an insight about the launch plan", "provide_insight") { action ->
                val insight = action as FulfillmentAction.ProvideInsight
                assertTrue(insight.answer.contains("existing action flow"))
            },
            ParityFixture("recall what I said about the timer flow", "provide_recall") { action ->
                val recall = action as FulfillmentAction.ProvideRecall
                assertTrue(recall.answer.contains("ActionDeck"))
            },
            ParityFixture("summarize the standup", "create_doc") { action ->
                val summary = action as FulfillmentAction.CreateDoc
                assertEquals("Summary", summary.title)
                assertTrue(summary.body.startsWith("Summary:"))
            }
        )

    private data class ParityFixture(
        val query: String,
        val actionType: String,
        val verify: (FulfillmentAction) -> Unit
    )
}
