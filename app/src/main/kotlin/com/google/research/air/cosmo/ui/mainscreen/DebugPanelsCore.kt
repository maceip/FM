package com.google.research.air.cosmo.ui.mainscreen

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.research.air.cosmo.controller.CosmoController
import com.google.research.air.cosmo.lib.llm.LLMStatsStore
import com.google.research.air.cosmo.lib.permission.PermissionRationaleHelper
import com.google.research.air.cosmo.presence.overlay.PresenceOverlayStarter
import com.google.research.air.cosmo.setting.CosmoSettingStore
import javax.inject.Inject

class ControllerDebugPanel
@Inject
constructor(
    private val controller: CosmoController
) : BaseDebugPanel(1, "controller", "Controller jobs") {
    @Composable override fun Content() {
        DebugCard(title) {
            MetricRow("Cosmo enabled", controller.isCosmoEnabled().toString())
            MetricRow("Ambient audio job", controller.isAmbientAudioCaptureOn().toString())
            MetricRow("Screen capture job", controller.isScreenCaptureOn().toString())
            MetricRow("Interaction job", controller.isScreenInteractionCaptureOn().toString())
            MetricRow("Location job", controller.isLocationCaptureOn().toString())
        }
    }
}

class SettingsDebugPanel
@Inject
constructor(
    private val settings: CosmoSettingStore
) : BaseDebugPanel(2, "settings", "Settings state") {
    @Composable override fun Content() {
        val setting by settings.getCosmoSettingFlow().collectAsState()
        DebugCard(title) {
            MetricRow("Global Cosmo", setting.globalCosmoEnabled.toString())
            MetricRow("Gatekeeper", setting.gatekeeperEnabled.toString())
            MetricRow("Raw audio gatekeeper", setting.rawAudioGatekeeperEnabled.toString())
            MetricRow("Ambient audio", setting.ambientAudioEnabled.toString())
            MetricRow("Notifications", setting.notificationCaptureEnabled.toString())
            MetricRow("Screen context", setting.screenContextEnabled.toString())
            MetricRow("Screen interactions", setting.screenInteractionCaptureEnabled.toString())
        }
    }
}

class PermissionsDebugPanel
@Inject
constructor(
    private val permissionRationaleHelper: PermissionRationaleHelper
) : BaseDebugPanel(3, "permissions", "Permissions") {
    @Composable override fun Content() {
        val context = LocalContext.current
        val activity = context as? Activity
        var states by remember(activity) {
            mutableStateOf(emptyList<com.google.research.air.cosmo.lib.permission.RuntimePermissionState>())
        }
        LaunchedEffect(activity) {
            states = activity?.let { permissionRationaleHelper.states(it) }.orEmpty()
        }
        DebugCard(title) {
            Text("Runtime permission requests are evaluated by `PermissionRationaleHelper` from the active Activity.")
            MetricRow("Activity available", (activity != null).toString())
            states.forEach { MetricRow(it.descriptor.title, it.grantState.name) }
        }
    }
}

class LlmStatsDebugPanel
@Inject
constructor(
    private val llmStatsStore: LLMStatsStore
) : BaseDebugPanel(4, "llm_stats", "LLM stats") {
    @Composable override fun Content() {
        val stats by llmStatsStore.inferenceStats.collectAsState()
        val remoteEnabled by llmStatsStore.isRemoteInferenceEnabled.collectAsState()
        DebugCard(title) {
            MetricRow("Cache hits", stats.hit.toString())
            MetricRow("Success", stats.success.toString())
            MetricRow("Failure", stats.failure.toString())
            MetricRow("Remote inference", remoteEnabled.toString())
        }
    }
}

class PresenceOverlayDebugPanel
@Inject
constructor(
    private val starter: PresenceOverlayStarter
) : BaseDebugPanel(25, "presence_overlay", "Presence overlay") {
    @Composable override fun Content() {
        var lastResult by remember { mutableStateOf("idle") }
        DebugCard(title) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { lastResult = starter.start().debugLabel() }) { Text("Start") }
                Button(onClick = { lastResult = starter.hide().debugLabel() }) { Text("Hide") }
                Button(onClick = { lastResult = starter.requestPermission().debugLabel() }) { Text("Permission") }
            }
            MetricRow("Last result", lastResult)
        }
    }

    private fun Result<Unit>.debugLabel(): String =
        fold(
            onSuccess = { "ok" },
            onFailure = { error -> error.message ?: error::class.java.simpleName }
        )
}
