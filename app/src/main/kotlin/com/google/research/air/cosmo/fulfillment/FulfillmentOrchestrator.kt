package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface FulfillmentOrchestrator {
    suspend fun handleBatchRun()

    suspend fun handleDoubleTap()

    suspend fun handleUserQuery(
        query: String,
        conversationHistory: String
    )

    suspend fun handleWatchDoublePinch()
}

class FulfillmentOrchestratorImpl
@Inject
constructor(
    private val runner: FulfillmentRunner,
    private val gatekeeperOutput: GatekeeperOutputSource,
    @ApplicationScope private val scope: CoroutineScope,
    private val telemetry: CosmosTelemetry
) : FulfillmentOrchestrator {
    init {
        scope.launch {
            gatekeeperOutput.results.collectLatest { result ->
                if (result.triggeredFulfillment) {
                    fulfillAndLog(
                        prompt = "Gatekeeper trigger: ${result.detectedSkills.keys.joinToString()}",
                        trigger = FulfillmentTriggerType.GATEKEEPER_TRIGGER
                    )
                }
            }
        }
    }

    override suspend fun handleBatchRun() {
        fulfillAndLog("Batch pipeline run", FulfillmentTriggerType.BATCH_PIPELINE)
    }

    override suspend fun handleDoubleTap() {
        fulfillAndLog("Pixel phone double tap", FulfillmentTriggerType.PIXEL_PHONE_DOUBLE_TAP)
    }

    override suspend fun handleUserQuery(
        query: String,
        conversationHistory: String
    ) {
        fulfillAndLog("Reactive fulfillment. Conversation: $conversationHistory", FulfillmentTriggerType.USER_QUERY, query)
    }

    override suspend fun handleWatchDoublePinch() {
        fulfillAndLog("Pixel Watch double pinch", FulfillmentTriggerType.PIXEL_WATCH_DOUBLE_PINCH)
    }

    private suspend fun fulfillAndLog(
        prompt: String,
        trigger: FulfillmentTriggerType,
        userQuery: String? = null
    ) {
        val actions =
            try {
                runner.fulfill(prompt, trigger, userQuery)
            } catch (e: com.google.research.air.cosmo.lib.llm.LlmUnavailableException) {
                // Fail closed without killing the process. Genesis on a device with no LLM
                // provisioned still needs to exercise the upstream gatekeeper / SODA paths
                // during smoke testing; an LLM crash here would mask those signals.
                android.util.Log.w("FulfillmentOrchestrator", "LLM unavailable, skipping fulfillment: ${e.message}")
                emptyList()
            }
        telemetry.log(TelemetryEvent.FulfillmentRun(trigger.name, actions.size))
    }
}
