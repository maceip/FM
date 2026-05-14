package com.google.research.air.cosmo.fulfillment.actions.mariner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.google.research.air.cosmo.fulfillment.ActionsRepositoryContract
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.ui.theme.CosmoBackground
import com.google.research.air.cosmo.ui.theme.CosmoStickerSurface
import com.google.research.air.cosmo.ui.theme.CosmoTokens
import com.google.research.air.cosmo.ui.theme.setCosmoContent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class TaskMonitorMessage(
    val role: String,
    val text: String
)

@HiltViewModel
class TaskMonitorViewModel
@Inject
constructor(
    val actionsRepository: ActionsRepositoryContract,
    private val telemetry: CosmosTelemetry
) : ViewModel() {
    fun messagesFromExtras(
        prompt: String,
        response: String
    ): List<TaskMonitorMessage> {
        telemetry.log(TelemetryEvent.TaskMonitorMessage(prompt.length, response.length))
        return buildList {
            if (prompt.isNotBlank()) add(TaskMonitorMessage("User", prompt))
            if (response.isNotBlank()) add(TaskMonitorMessage("Agent", response))
        }
    }
}

@AndroidEntryPoint
class TaskMonitorActivity : ComponentActivity() {
    private val viewModel: TaskMonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(TaskMonitorNavigationBridge.EXTRA_TITLE).orEmpty().ifBlank { "Task monitor" }
        val prompt = intent.getStringExtra(TaskMonitorNavigationBridge.EXTRA_PROMPT).orEmpty()
        val response = intent.getStringExtra(TaskMonitorNavigationBridge.EXTRA_AGENT_RESPONSE).orEmpty()
        setCosmoContent {
            val history by viewModel.actionsRepository.fulfillmentHistory.collectAsState()
            val messages = remember(prompt, response) { viewModel.messagesFromExtras(prompt, response) }
            CosmoBackground(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.padding(CosmoTokens.Spacing.screenHorizontal)) {
                    item {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                    }
                    items(messages) { message ->
                        MessageBubble(message)
                    }
                    items(history) { item ->
                        CosmoStickerSurface(
                            modifier = Modifier.padding(vertical = 8.dp),
                            containerColor = CosmoTokens.Color.SurfaceHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(item.fulfillmentTriggerType.name, style = MaterialTheme.typography.titleMedium)
                                Text(item.llmResult.take(280), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MessageBubble(message: TaskMonitorMessage) {
    val agent = message.role == "Agent"
    CosmoStickerSurface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        containerColor = if (agent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (agent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
        rotationDegrees = if (agent) 1.5f else -1.5f
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message.role, style = MaterialTheme.typography.labelMedium)
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
