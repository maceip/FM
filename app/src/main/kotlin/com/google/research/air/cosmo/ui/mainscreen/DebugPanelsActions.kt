package com.google.research.air.cosmo.ui.mainscreen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.research.air.cosmo.fulfillment.ActionsRepositoryContract
import javax.inject.Inject

class CurrentActionsDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(5, "current_actions", "Current actions") {
    @Composable override fun Content() {
        val current by actions.currentActions.collectAsState()
        DebugCard(title) {
            MetricRow("Count", current.size.toString())
            current.take(PREVIEW_LIMIT).forEach { Text("${it::class.simpleName}: ${it.title}") }
        }
    }
}

class UserFacingActionsDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(6, "user_actions", "User-facing actions") {
    @Composable override fun Content() {
        val current by actions.userFacingActions.collectAsState()
        DebugCard(title) {
            MetricRow("Count", current.size.toString())
            current.take(PREVIEW_LIMIT).forEach { Text("${it.status}: ${it.title}") }
        }
    }
}

class ExecutedActionsDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(7, "executed_actions", "Executed actions") {
    @Composable override fun Content() {
        val executed by actions.executedActions.collectAsState()
        DebugCard(title) {
            MetricRow("Count", executed.size.toString())
            executed.take(PREVIEW_LIMIT).forEach { Text(it.title) }
        }
    }
}

class DismissedActionsDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(8, "dismissed_actions", "Dismissed actions") {
    @Composable override fun Content() {
        val dismissed by actions.userDismissedActions.collectAsState()
        DebugCard(title) {
            MetricRow("Count", dismissed.size.toString())
            dismissed.take(PREVIEW_LIMIT).forEach { Text(it.title) }
        }
    }
}

class RemovedActionsDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(9, "removed_actions", "Cosmo-removed actions") {
    @Composable override fun Content() {
        val removed by actions.cosmoRemovedActions.collectAsState()
        DebugCard(title) {
            MetricRow("Count", removed.size.toString())
            removed.take(PREVIEW_LIMIT).forEach { Text(it.title) }
        }
    }
}

class FulfillmentHistoryDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(10, "fulfillment_history", "Fulfillment history") {
    @Composable override fun Content() {
        val history by actions.fulfillmentHistory.collectAsState()
        DebugCard(title) {
            MetricRow("Runs", history.size.toString())
            history.take(PREVIEW_LIMIT).forEach {
                Text("${it.fulfillmentTriggerType}: +${it.numNewActions} ~${it.numUpdatedActions} -${it.numRemovedActions}")
            }
        }
    }
}

class LastFulfillmentDebugPanel
@Inject
constructor(
    private val actions: ActionsRepositoryContract
) : BaseDebugPanel(11, "last_fulfillment", "Last fulfillment") {
    @Composable override fun Content() {
        val lastId by actions.lastFulfillmentId.collectAsState()
        DebugCard(title) {
            MetricRow("Last fulfillment ID", lastId ?: "none")
        }
    }
}
