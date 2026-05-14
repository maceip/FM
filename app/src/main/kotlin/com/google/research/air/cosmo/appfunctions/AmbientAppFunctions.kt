package com.google.research.air.cosmo.appfunctions

import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.controller.CosmoController
import com.google.research.air.cosmo.fulfillment.FulfillmentOrchestrator
import com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorNavigationBridge
import com.google.research.air.cosmo.setting.CosmoState
import com.google.research.air.cosmo.setting.CosmoStateManager
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class AmbientStatus(
    val state: CosmoState,
    val ambientAudioOn: Boolean,
    val budsAudioOn: Boolean,
    val watchAudioOn: Boolean,
    val glassOn: Boolean,
    val lanDiscoveryOn: Boolean
)

@Singleton
class AmbientAppFunctions
@Inject
constructor(
    private val controller: CosmoController,
    private val stateManager: CosmoStateManager,
    private val contextStore: ContextStore,
    private val fulfillmentOrchestrator: FulfillmentOrchestrator,
    private val taskMonitorNavigationBridge: TaskMonitorNavigationBridge,
    private val clock: Clock
) {
    fun getAmbientStatus(): AmbientStatus =
        AmbientStatus(
            state = stateManager.getCosmoState().value,
            ambientAudioOn = controller.isAmbientAudioCaptureOn(),
            budsAudioOn = controller.isBudsAudioCaptureOn(),
            watchAudioOn = controller.isWatchAudioCaptureOn(),
            glassOn = controller.isGlassCaptureOn(),
            lanDiscoveryOn = controller.isAmbientDeviceDiscoveryOn()
        )

    fun startAmbientSession() {
        stateManager.enableCosmo()
    }

    fun stopAmbientSession() {
        stateManager.disableCosmo()
    }

    fun snoozeAmbientSession(duration: Duration) {
        stateManager.setSnoozeUntil(clock.instant().plus(duration))
    }

    suspend fun queryRecentContext(
        query: String,
        limit: Int = 10
    ): List<String> =
        contextStore
            .queryByText(query, limit)
            .mapNotNull { event -> event.text?.takeIf { it.isNotBlank() } }

    suspend fun summarizeRecentContext(query: String): String =
        queryRecentContext(query, limit = 8).joinToString(separator = "\n") { "- $it" }

    fun openTaskMonitor(
        title: String,
        prompt: String,
        agentResponse: String
    ): Boolean = taskMonitorNavigationBridge.openTaskMonitor(title, prompt, agentResponse)

    suspend fun runUserConfirmedQuery(
        query: String,
        conversationHistory: String = ""
    ) {
        fulfillmentOrchestrator.handleUserQuery(query, conversationHistory)
    }
}
