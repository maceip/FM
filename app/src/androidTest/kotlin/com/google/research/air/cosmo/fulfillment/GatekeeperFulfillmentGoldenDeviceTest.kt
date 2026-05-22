package com.google.research.air.cosmo.fulfillment

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.gatekeeping.Gatekeeper
import com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceController
import com.google.research.air.cosmo.presence.PresenceState
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
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

/**
 * On-device golden path: real gatekeeper classification → orchestrator queue → fulfillment → action deck.
 *
 * Run via [scripts/run-device-gatekeeper-golden-test.sh] or:
 * ```
 * adb shell am instrument -w -r \
 *   -e cosmo.gatekeeperGolden true \
 *   -e class com.google.research.air.cosmo.fulfillment.GatekeeperFulfillmentGoldenDeviceTest \
 *   com.google.research.air.cosmo.test/com.google.research.air.cosmo.GenesisTestRunner
 * ```
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GatekeeperFulfillmentGoldenDeviceTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var fulfillmentOrchestrator: FulfillmentOrchestrator

    @Inject lateinit var gatekeeper: Gatekeeper

    @Inject lateinit var gatekeeperResultUtils: GatekeeperResultUtils

    @Inject lateinit var actionsRepository: ActionsRepositoryContract

    @Inject lateinit var settings: CosmoSettingStore

    @Inject lateinit var presenceController: PresenceController

    @Test(timeout = 120_000)
    fun gatekeeperTimerIntentProducesAddTimerActionOnActionDeck() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(
                "Set -e cosmo.gatekeeperGolden true to run the gatekeeper fulfillment golden test.",
                args.enabled(GOLDEN_ARG),
            )
            hiltRule.inject()

            actionsRepository.clearAllActions()
            actionsRepository.clearAllDebugInfos()

            // Eager-init orchestrator listeners (CosmoApp does not inject orchestrator until UI/work).
            checkNotNull(fulfillmentOrchestrator)
            gatekeeper.start()
            presenceController.start()
            delay(500)

            assumeTrue("Gatekeeper must be enabled", settings.isGatekeeperEnabled())
            assumeTrue("Timer suggestions must be enabled", settings.isSuggestTimerEnabled())
            assumeTrue("Realtime/hybrid processing required", settings.isRealtimeProcessingEnabled())

            val debugInput = GOLDEN_DEBUG_INPUT
            gatekeeper.processDebugInput(debugInput)

            val gatekeeperResult =
                withTimeout(45_000) {
                    gatekeeper.results.first { result ->
                        result.detectedSkills.containsKey("add_timer") && result.triggeredFulfillment
                    }
                }
            val intents = gatekeeperResultUtils.getIntents(gatekeeperResult)
            assertTrue("Expected START_TIMER_INTENT, got $intents", intents.contains("START_TIMER_INTENT"))
            Log.i(
                TAG,
                "gatekeeper_golden detectedBy=${gatekeeperResult.detectedBy} skills=${gatekeeperResult.detectedSkills} intents=$intents",
            )

            val timer =
                withTimeout(45_000) {
                    actionsRepository.userFacingActions
                        .first { actions -> actions.any { it is FulfillmentAction.AddTimer } }
                        .filterIsInstance<FulfillmentAction.AddTimer>()
                        .first()
                }
            assertEquals(600, timer.durationSeconds)
            assertTrue(
                "Timer title should reference 10 minutes: ${timer.title}",
                timer.title.contains("10", ignoreCase = true) || timer.title.contains("minute", ignoreCase = true),
            )
            Log.i(TAG, "gatekeeper_golden action id=${timer.id} title=${timer.title} duration=${timer.durationSeconds}")

            val gatekeeperHistory =
                withTimeout(10_000) {
                    actionsRepository.fulfillmentHistory.first { history ->
                        history.any { it.fulfillmentTriggerType == FulfillmentTriggerType.GATEKEEPER_TRIGGER }
                    }
                }.last { it.fulfillmentTriggerType == FulfillmentTriggerType.GATEKEEPER_TRIGGER }

            assertTrue(
                "Prompt should include gatekeeper intents",
                gatekeeperHistory.llmQuery.contains("START_TIMER_INTENT"),
            )
            assertTrue(
                "Prompt should include gatekeeper transcript context",
                gatekeeperHistory.llmQuery.contains("Gatekeeper input:") ||
                    gatekeeperHistory.llmQuery.contains(debugInput, ignoreCase = true),
            )
            assertTrue(
                "Schema should be filtered to timer skill",
                gatekeeperHistory.llmQuery.contains("ACTION_TYPE_ADD_TIMER"),
            )
            assertEquals(listOf(timer.id), gatekeeperHistory.actionIds)
            Log.i(TAG, "gatekeeper_golden fulfillmentId=${gatekeeperHistory.fulfillmentId} trigger=GATEKEEPER_TRIGGER")

            val presence =
                withTimeout(15_000) {
                    presenceController.state.first { it is PresenceState.Ready }
                } as PresenceState.Ready
            assertEquals(timer.id, presence.cards.single().id)
            assertTrue(
                "Presence should surface timer card payload",
                presence.cards.single().payload is CardPayload.CalendarTime,
            )
            assertEquals("review:${timer.id}", presence.primaryAction?.id)
            Log.i(TAG, "gatekeeper_golden presence taskId=${presence.taskId} card=${presence.cards.single().id}")
        }
    }

    private companion object {
        const val TAG = "GatekeeperGoldenDevice"
        const val GOLDEN_ARG = "cosmo.gatekeeperGolden"
        const val GOLDEN_DEBUG_INPUT = "set a timer for 10 minutes"
    }
}

private fun android.os.Bundle.enabled(name: String): Boolean =
    getString(name)?.equals("true", ignoreCase = true) == true
