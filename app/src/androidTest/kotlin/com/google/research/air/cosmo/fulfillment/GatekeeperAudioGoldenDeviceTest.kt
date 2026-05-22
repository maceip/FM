package com.google.research.air.cosmo.fulfillment

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.gatekeeping.Gatekeeper
import com.google.research.air.cosmo.gatekeeping.GatekeeperDebugReceiver
import com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceController
import com.google.research.air.cosmo.presence.PresenceState
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
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
 * On-device audio golden: WAV → SODA → gatekeeper skill → orchestrator → action deck + presence.
 *
 * Prepare the fixture on device first (see [scripts/run-device-gatekeeper-audio-golden-test.sh]).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GatekeeperAudioGoldenDeviceTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var fulfillmentOrchestrator: FulfillmentOrchestrator

    @Inject lateinit var gatekeeper: Gatekeeper

    @Inject lateinit var gatekeeperResultUtils: GatekeeperResultUtils

    @Inject lateinit var actionsRepository: ActionsRepositoryContract

    @Inject lateinit var settings: CosmoSettingStore

    @Inject lateinit var presenceController: PresenceController

    @Test(timeout = 180_000)
    fun injectedTimerWavProducesAddTimerThroughGatekeeperPipeline() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(
                "Set -e cosmo.gatekeeperAudioGolden true to run the audio gatekeeper golden test.",
                args.enabled(AUDIO_GOLDEN_ARG),
            )
            hiltRule.inject()

            val fixturePath =
                args.getString(FIXTURE_PATH_ARG)?.takeIf { it.isNotBlank() }
                    ?: defaultFixturePath()
            assumeTrue(
                "Fixture WAV must exist at $fixturePath (run scripts/run-device-gatekeeper-audio-golden-test.sh)",
                File(fixturePath).isFile,
            )

            actionsRepository.clearAllActions()
            actionsRepository.clearAllDebugInfos()

            checkNotNull(fulfillmentOrchestrator)
            gatekeeper.start()
            presenceController.start()
            delay(500)

            assumeTrue("Gatekeeper must be enabled", settings.isGatekeeperEnabled())
            assumeTrue("Timer suggestions must be enabled", settings.isSuggestTimerEnabled())
            assumeTrue("Realtime/hybrid processing required", settings.isRealtimeProcessingEnabled())

            injectAudioFixture(fixturePath, target = "soda")

            val gatekeeperResult =
                withTimeout(90_000) {
                    gatekeeper.results.first { result ->
                        result.detectedSkills.containsKey(EXPECTED_SKILL) && result.triggeredFulfillment
                    }
                }
            val intents = gatekeeperResultUtils.getIntents(gatekeeperResult)
            assertTrue("Expected START_TIMER_INTENT, got $intents", intents.contains("START_TIMER_INTENT"))
            Log.i(
                TAG,
                "audio_golden detectedBy=${gatekeeperResult.detectedBy} skills=${gatekeeperResult.detectedSkills}",
            )

            val timer =
                withTimeout(45_000) {
                    actionsRepository.userFacingActions
                        .first { actions -> actions.any { it is FulfillmentAction.AddTimer } }
                        .filterIsInstance<FulfillmentAction.AddTimer>()
                        .first()
                }
            assertEquals(600, timer.durationSeconds)

            val history =
                actionsRepository.fulfillmentHistory.first { h ->
                    h.any { it.fulfillmentTriggerType == FulfillmentTriggerType.GATEKEEPER_TRIGGER }
                }.last { it.fulfillmentTriggerType == FulfillmentTriggerType.GATEKEEPER_TRIGGER }
            assertTrue(history.llmQuery.contains("START_TIMER_INTENT"))

            val presence =
                withTimeout(15_000) {
                    presenceController.state.first { it is PresenceState.Ready }
                } as PresenceState.Ready
            assertTrue(presence.cards.single().payload is CardPayload.CalendarTime)
            Log.i(TAG, "audio_golden passed fixture=$fixturePath action=${timer.id}")
        }
    }

    private fun defaultFixturePath(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.filesDir, "cosmo-test/$FIXTURE_NAME").absolutePath
    }

    private fun injectAudioFixture(
        path: String,
        target: String,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(GatekeeperDebugReceiver.ACTION_AUDIO).apply {
                setPackage(context.packageName)
                putExtra(GatekeeperDebugReceiver.EXTRA_PATH, path)
                putExtra(GatekeeperDebugReceiver.EXTRA_TARGET, target)
            }
        context.sendBroadcast(intent)
        Log.i(TAG, "audio_golden broadcast path=$path target=$target")
    }

    private companion object {
        const val TAG = "GatekeeperAudioGolden"
        const val AUDIO_GOLDEN_ARG = "cosmo.gatekeeperAudioGolden"
        const val FIXTURE_PATH_ARG = "cosmo.fixturePath"
        const val FIXTURE_NAME = "timer_ten_minutes.wav"
        const val EXPECTED_SKILL = "add_timer"
    }
}

private fun android.os.Bundle.enabled(name: String): Boolean =
    getString(name)?.equals("true", ignoreCase = true) == true
