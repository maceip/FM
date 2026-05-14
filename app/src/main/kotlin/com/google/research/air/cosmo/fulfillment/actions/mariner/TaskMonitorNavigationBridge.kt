package com.google.research.air.cosmo.fulfillment.actions.mariner

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes Task Monitor UX through the main app's Nav3 graph when [MainActivity] registers an in-app
 * navigator; otherwise falls back to [TaskMonitorActivity] for standalone/deep-link style entry.
 */
@Singleton
class TaskMonitorNavigationBridge
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val telemetry: CosmosTelemetry
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var inAppNavigator: ((TaskMonitorNavPayload) -> Unit)? = null

    fun registerInAppNavigator(navigator: ((TaskMonitorNavPayload) -> Unit)?) {
        inAppNavigator = navigator
    }

    fun openTaskMonitor(
        title: String,
        prompt: String,
        agentResponse: String
    ): Boolean {
        val nav = inAppNavigator
        return if (nav != null) {
            telemetry.log(TelemetryEvent.TaskMonitorSurface("nav3_in_app"))
            val payload = TaskMonitorNavPayload(title = title, prompt = prompt, agentResponse = agentResponse)
            mainHandler.post { nav(payload) }
            true
        } else {
            telemetry.log(TelemetryEvent.TaskMonitorSurface("standalone_activity"))
            launchStandaloneActivity(title, prompt, agentResponse)
        }
    }

    fun createHandoffIntent(payload: TaskMonitorNavPayload): Intent =
        Intent(context, TaskMonitorActivity::class.java)
            .putExtra(EXTRA_TITLE, payload.title)
            .putExtra(EXTRA_PROMPT, payload.prompt)
            .putExtra(EXTRA_AGENT_RESPONSE, payload.agentResponse)
            .putExtra(EXTRA_HANDOFF_SOURCE, "task_monitor")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openTaskMonitorFromHandoff(intent: Intent): Boolean =
        openTaskMonitor(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty(),
            agentResponse = intent.getStringExtra(EXTRA_AGENT_RESPONSE).orEmpty()
        )

    private fun launchStandaloneActivity(
        title: String,
        prompt: String,
        agentResponse: String
    ): Boolean =
        try {
            val intent =
                Intent(context, TaskMonitorActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_PROMPT, prompt)
                    .putExtra(EXTRA_AGENT_RESPONSE, agentResponse)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_AGENT_RESPONSE = "agent_response"
        const val EXTRA_HANDOFF_SOURCE = "handoff_source"
    }
}

data class TaskMonitorNavPayload(
    val title: String,
    val prompt: String,
    val agentResponse: String
)
