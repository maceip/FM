package com.google.research.air.cosmo

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.fulfillment.ActionsRepositoryContract
import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import com.google.research.air.cosmo.fulfillment.FulfillmentActionHandler
import com.google.research.air.cosmo.fulfillment.FulfillmentRunner
import com.google.research.air.cosmo.fulfillment.FulfillmentTriggerType
import com.google.research.air.cosmo.gatekeeping.Gatekeeper
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.lib.soda.DefaultMicSodaController
import com.google.research.air.cosmo.lib.soda.JniSodaCapability
import com.google.research.air.cosmo.lib.soda.SodaPrepareResult
import com.google.research.air.cosmo.lib.soda.SodaTranscriptionController
import com.google.research.air.cosmo.presence.PresenceController
import com.google.research.air.cosmo.presence.PresenceState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CoreCapabilityDeviceSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var gatekeeper: Gatekeeper

    @Inject lateinit var gatekeeperOutput: GatekeeperOutputSource

    @Inject lateinit var fulfillmentRunner: FulfillmentRunner

    @Inject lateinit var actionsRepository: ActionsRepositoryContract

    @Inject lateinit var actionHandler: FulfillmentActionHandler

    @Inject lateinit var presenceController: PresenceController

    @Inject lateinit var sodaCapability: JniSodaCapability

    @Inject
    @DefaultMicSodaController
    lateinit var micSodaController: SodaTranscriptionController

    @Test(timeout = 180_000)
    fun coreTextActionPresenceAndAudioRuntimeAreAlive() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(
                "Set -e cosmo.coreCapabilitySmoke true to run the real-device core capability smoke.",
                args.enabled(CORE_SMOKE_ARG)
            )
            hiltRule.inject()

            actionsRepository.clearAllActions()
            actionsRepository.clearAllDebugInfos()
            presenceController.start()
            gatekeeper.start()
            delay(250)

            val gatekeeperResult =
                async(start = CoroutineStart.UNDISPATCHED) {
                    gatekeeperOutput.results.first { result ->
                        result.detectedSkills.containsKey("add_timer")
                    }
                }
            gatekeeper.processDebugInput("set a timer for 10 minutes")
            val result = withTimeout(30_000) { gatekeeperResult.await() }
            assertTrue("Gatekeeper should trigger fulfillment for timer text", result.triggeredFulfillment)
            Log.i(
                TAG,
                "core_gatekeeper_smoke detectedBy=${result.detectedBy} skills=${result.detectedSkills}"
            )

            FEATURE_EXPECTATIONS.forEach { expectation ->
                actionsRepository.clearAllActions()
                val actions =
                    fulfillmentRunner.fulfill(
                        prompt = "Core capability smoke user-query run",
                        trigger = FulfillmentTriggerType.USER_QUERY,
                        userQuery = expectation.prompt
                    )
                val action = actions.first(expectation.matches)
                Log.i(
                    TAG,
                    "core_fulfillment_smoke feature=${expectation.label} action=${action.actionTypeNameForSmoke()} title=${action.title}"
                )
            }

            actionsRepository.clearAllActions()
            fulfillmentRunner.fulfill(
                prompt = "Core capability smoke presence run",
                trigger = FulfillmentTriggerType.USER_QUERY,
                userQuery = "set a timer for 10 minutes"
            )
            val timer = waitForUserFacingAction { it is FulfillmentAction.AddTimer }
            assertEquals(600, (timer as FulfillmentAction.AddTimer).durationSeconds)
            val presence = withTimeout(10_000) {
                presenceController.state.first { it is PresenceState.Ready }
            }
            assertTrue("Presence should surface user-facing actions", presence is PresenceState.Ready)
            Log.i(TAG, "core_presence_smoke state=${presence::class.simpleName}")

            val memoryAction =
                FulfillmentAction.AddToMemory(
                    id = "core-smoke-memory",
                    title = "Remember core smoke",
                    text = "Core capability smoke verified local memory action execution."
                )
            actionsRepository.maybeUpdateActionsList(listOf(memoryAction), fulfillmentId = "core-smoke")
            actionHandler.execute(memoryAction)
            val executed = withTimeout(10_000) {
                actionsRepository.executedActions.first { actions ->
                    actions.any { it.id == memoryAction.id }
                }
            }
            assertTrue(executed.any { it.id == memoryAction.id })
            Log.i(TAG, "core_action_handler_smoke executed=${memoryAction.actionTypeNameForSmoke()}")

            assertTrue("SODA native capability should be available", sodaCapability.isAvailable)
            val prepared = sodaCapability.prepare()
            assertTrue("SODA language pack should resolve, got $prepared", prepared is SodaPrepareResult.Available)
            micSodaController.startCapture()
            try {
                val silence = ByteArray(SILENCE_PCM_BYTES)
                micSodaController.addAudioBytes(silence)
                delay(750)
                val acceptedBytes = micSodaController.flush()
                assertTrue("Mic SODA controller should accept injected PCM bytes", acceptedBytes.size >= silence.size)
                Log.i(
                    TAG,
                    "core_audio_runtime_smoke sodaAvailable=true accepted_bytes=${acceptedBytes.size}"
                )
            } finally {
                micSodaController.stopCapture()
            }
        }
    }

    private suspend fun waitForUserFacingAction(matches: (FulfillmentAction) -> Boolean): FulfillmentAction =
        withTimeout(20_000) {
            actionsRepository.userFacingActions
                .first { actions -> actions.any(matches) }
                .first(matches)
        }

    private fun FulfillmentAction.actionTypeNameForSmoke(): String =
        when (this) {
            is FulfillmentAction.ActiveDataPlan -> "active_data_plan"
            is FulfillmentAction.AddTask -> "add_task"
            is FulfillmentAction.AddTimer -> "add_timer"
            is FulfillmentAction.AddToList -> "add_to_list"
            is FulfillmentAction.AddToMemory -> "add_to_memory"
            is FulfillmentAction.BrowsingAgent -> "browsing_agent"
            is FulfillmentAction.CallAgent -> "call_agent"
            is FulfillmentAction.CreateCalendarEvent -> "create_calendar_event"
            is FulfillmentAction.CreateDoc -> "create_doc"
            is FulfillmentAction.DeepResearch -> "deep_research"
            is FulfillmentAction.DefineTerm -> "define_term"
            is FulfillmentAction.DraftEmail -> "draft_email"
            is FulfillmentAction.DraftSms -> "draft_sms"
            is FulfillmentAction.InvokeAgentQ -> "invoke_agent_q"
            is FulfillmentAction.Navigation -> "navigation"
            is FulfillmentAction.PlayMusic -> "play_music"
            is FulfillmentAction.ProvideEventContext -> "provide_event_context"
            is FulfillmentAction.ProvideInsight -> "provide_insight"
            is FulfillmentAction.ProvidePersonContext -> "provide_person_context"
            is FulfillmentAction.ProvideRecall -> "provide_recall"
            is FulfillmentAction.QueryPhotos -> "query_photos"
            is FulfillmentAction.RemovedAction -> "removed"
            is FulfillmentAction.Unsupported -> "unsupported"
            is FulfillmentAction.WebSearch -> "web_search"
        }

    private companion object {
        const val TAG = "CosmoCoreDeviceSmoke"
        const val CORE_SMOKE_ARG = "cosmo.coreCapabilitySmoke"
        const val SILENCE_PCM_BYTES = 16_000 * 2

        val FEATURE_EXPECTATIONS =
            listOf(
                FeatureExpectation("timer", "set a timer for 10 minutes") {
                    it is FulfillmentAction.AddTimer && it.durationSeconds == 600
                },
                FeatureExpectation("list", "add eggs and milk to the shopping list") {
                    it is FulfillmentAction.AddToList && it.items.contains("eggs")
                },
                FeatureExpectation("doc", "create a doc about launch risks") {
                    it is FulfillmentAction.CreateDoc
                },
                FeatureExpectation("calendar", "put a meeting on my calendar tomorrow") {
                    it is FulfillmentAction.CreateCalendarEvent
                },
                FeatureExpectation("search", "search who is Ada Lovelace") {
                    it is FulfillmentAction.WebSearch
                },
                FeatureExpectation("email", "draft an email to the team") {
                    it is FulfillmentAction.DraftEmail
                }
            )
    }
}

private data class FeatureExpectation(
    val label: String,
    val prompt: String,
    val matches: (FulfillmentAction) -> Boolean
)

private fun android.os.Bundle.enabled(name: String): Boolean =
    getString(name)?.equals("true", ignoreCase = true) == true
