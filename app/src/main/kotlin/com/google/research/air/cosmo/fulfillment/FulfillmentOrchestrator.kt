package com.google.research.air.cosmo.fulfillment

import android.util.Log
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.setting.CosmoSettingStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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
    private val gatekeeperResultUtils: GatekeeperResultUtils,
    private val configRegistry: FulfillmentConfigRegistry,
    private val settings: CosmoSettingStore,
    @ApplicationScope private val scope: CoroutineScope,
    private val telemetry: CosmosTelemetry
) : FulfillmentOrchestrator {
    private val taskQueue = Channel<FulfillmentTask>(Channel.UNLIMITED)

    init {
        scope.launch { startFulfillmentConsumer() }
        scope.launch { startGatekeeperListener() }
    }

    override suspend fun handleBatchRun() {
        enqueue(
            FulfillmentTask(
                triggerType = FulfillmentTriggerType.BATCH_PIPELINE,
                userQuery = "",
            )
        )
    }

    override suspend fun handleDoubleTap() {
        enqueue(
            FulfillmentTask(
                triggerType = FulfillmentTriggerType.PIXEL_PHONE_DOUBLE_TAP,
                userQuery = "",
            )
        )
    }

    override suspend fun handleUserQuery(
        query: String,
        conversationHistory: String
    ) {
        enqueue(
            FulfillmentTask(
                triggerType = FulfillmentTriggerType.USER_QUERY,
                userQuery = query,
                conversationHistory = conversationHistory,
            )
        )
    }

    override suspend fun handleWatchDoublePinch() {
        enqueue(
            FulfillmentTask(
                triggerType = FulfillmentTriggerType.PIXEL_WATCH_DOUBLE_PINCH,
                userQuery = "",
            )
        )
    }

    private suspend fun startGatekeeperListener() {
        gatekeeperOutput.results.collect { result ->
            val intents = gatekeeperResultUtils.getIntents(result)
            if (intents.isEmpty()) {
                Log.d(TAG, "No enabled intents found for fulfillment.")
                return@collect
            }
            if (!settings.isGatekeeperEnabled()) return@collect
            enqueue(
                FulfillmentTask(
                    triggerType = FulfillmentTriggerType.GATEKEEPER_TRIGGER,
                    userQuery = "",
                    gatekeeperResultString = intents.joinToString(","),
                    conversationHistory = "",
                    gatekeepingResult = result,
                )
            )
        }
    }

    private suspend fun startFulfillmentConsumer() {
        for (task in taskQueue) {
            runCatching { doFulfillment(task) }
                .onFailure { error ->
                    Log.e(TAG, "Fulfillment failed for ${task.triggerType}", error)
                }
        }
    }

    private suspend fun enqueue(task: FulfillmentTask) {
        taskQueue.send(task)
    }

    private suspend fun doFulfillment(task: FulfillmentTask) {
        if (task.triggerType == FulfillmentTriggerType.GATEKEEPER_TRIGGER &&
            !settings.isRealtimeProcessingEnabled()
        ) {
            Log.d(TAG, "Skipping gatekeeper fulfillment: batch processing mode.")
            return
        }

        val intentNames =
            task.gatekeeperResultString
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
        val config =
            configRegistry.getConfigFor(
                trigger = task.triggerType,
                gatekeeperIntentNames = intentNames,
                useLocalLlm = false,
            )
        if (task.triggerType == FulfillmentTriggerType.GATEKEEPER_TRIGGER && config.enabledSkillIds.isEmpty()) {
            Log.d(TAG, "No enabled skills remain after gatekeeper filtering.")
            return
        }

        val actions =
            try {
                runner.fulfill(
                    config = config,
                    userQuery = task.userQuery,
                    trigger = task.triggerType,
                    conversationHistory = task.conversationHistory,
                    gatekeeperContext = task.gatekeepingResult,
                    gatekeeperIntentNames = intentNames,
                )
            } catch (e: com.google.research.air.cosmo.lib.llm.LlmUnavailableException) {
                Log.w(TAG, "LLM unavailable, skipping fulfillment: ${e.message}")
                emptyList()
            }
        telemetry.log(TelemetryEvent.FulfillmentRun(task.triggerType.name, actions.size))
    }

    private companion object {
        const val TAG = "FulfillmentOrchestrator"
    }
}
