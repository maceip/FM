package com.google.research.air.cosmo.fulfillment

import android.content.Context
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.IntentEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.NotificationEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.ScreenEphemeralBuffer
import com.google.research.air.cosmo.lib.llm.LLM
import com.google.research.air.cosmo.lib.llm.LLMRouteState
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class FulfillmentRunner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @AmbientAudioEphemeralBuffer private val audioBuffer: InMemoryEphemeralBuffer<Any>,
    @ScreenEphemeralBuffer private val screenBuffer: InMemoryEphemeralBuffer<Any>,
    @NotificationEphemeralBuffer private val notificationBuffer: InMemoryEphemeralBuffer<Any>,
    @IntentEphemeralBuffer private val intentBuffer: InMemoryEphemeralBuffer<Any>,
    private val actionsRepository: ActionsRepositoryContract,
    private val llm: LLM,
    private val settings: CosmoSettingStore,
    private val clock: Clock,
    private val telemetry: CosmosTelemetry
) {
    suspend fun fulfill(
        prompt: String,
        trigger: FulfillmentTriggerType,
        userQuery: String? = null
    ): List<FulfillmentAction> {
        val started = clock.millis()
        val renderedPrompt = buildPrompt(prompt, trigger, userQuery)
        val result = llm.generateText(renderedPrompt)
        val actions = parseResponse(result)
        val fulfillmentId = UUID.randomUUID().toString()
        actionsRepository.maybeUpdateActionsList(actions, fulfillmentId)
        val llmRouteState = llm as? LLMRouteState
        actionsRepository.addFulfillmentHistory(
            FulfillmentDebugInfo(
                fulfillmentId = fulfillmentId,
                createdAt = started,
                llmQuery = renderedPrompt,
                llmResult = result,
                latencyMs = clock.millis() - started,
                numNewActions = actions.count { it.status == FulfillmentAction.Status.NEW },
                numUpdatedActions = actions.count { it.status == FulfillmentAction.Status.UPDATED },
                numRemovedActions = actions.count { it.status == FulfillmentAction.Status.REMOVED },
                fulfillmentTriggerType = trigger,
                userQuery = userQuery,
                actionIds = actions.map { it.id },
                sourceBufferCounts =
                    mapOf(
                        "ambient_audio" to enabledBufferSize(settings.isAmbientAudioEnabled(), audioBuffer),
                        "screen" to enabledBufferSize(settings.isScreenContextEnabled(), screenBuffer),
                        "notification" to enabledBufferSize(settings.isNotificationCaptureEnabled(), notificationBuffer),
                        "intent" to enabledBufferSize(settings.isIntentExtractionEnabled(), intentBuffer)
                    ),
                llmModelPath = llmRouteState?.activePath?.value,
                llmRouteReason = llmRouteState?.routeDecision?.value?.reason
            )
        )
        return actions
    }

    private fun buildPrompt(
        systemPrompt: String,
        trigger: FulfillmentTriggerType,
        userQuery: String?
    ): String =
        buildString {
            appendLine(assetPromptFor(trigger))
            appendLine(systemPrompt)
            if (userQuery != null) appendLine("User query: $userQuery")
            appendLine("Allowed action schema:")
            appendLine(readAsset("filtered_skill_schema.textproto"))
            appendLine("Settings:")
            appendLine(
                "gatekeeper=${settings.isGatekeeperEnabled()}, rawAudioGatekeeper=${settings.isRawAudioGatekeeperEnabled()}, voiceMatch=${settings.isVoiceMatchEnabled()}"
            )
            appendContextSection("Audio", settings.isAmbientAudioEnabled(), audioBuffer)
            appendContextSection("Screens", settings.isScreenContextEnabled(), screenBuffer)
            appendContextSection("Notifications", settings.isNotificationCaptureEnabled(), notificationBuffer)
            appendContextSection("Intents", settings.isIntentExtractionEnabled(), intentBuffer)
        }

    private fun StringBuilder.appendContextSection(
        label: String,
        enabled: Boolean,
        buffer: InMemoryEphemeralBuffer<Any>
    ) {
        if (!enabled) return
        appendLine("$label:")
        buffer.snapshotReversed().take(8).forEach { appendLine(it.value.toString()) }
    }

    private fun enabledBufferSize(
        enabled: Boolean,
        buffer: InMemoryEphemeralBuffer<Any>
    ): Int = if (enabled) buffer.size() else 0

    private fun assetPromptFor(trigger: FulfillmentTriggerType): String =
        readAsset(
            when (trigger) {
                FulfillmentTriggerType.USER_QUERY -> "reactive_fulfillment_prompt.jinja2"
                FulfillmentTriggerType.GATEKEEPER_TRIGGER -> "gatekeeper_trigger_prompt.jinja2"
                else -> "fulfillment_prompt.jinja2"
            }
        )

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }.trim()

    fun parseResponse(response: String): List<FulfillmentAction> {
        val start = response.indexOf('[')
        val end = response.lastIndexOf(']')
        if (start < 0 || end < start) return emptyList()
        val array = JSONArray(response.substring(start, end + 1))
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { obj ->
                when (val parsed = obj.toActionParseResult()) {
                    is ActionParseResult.Accepted -> {
                        val action = parsed.action
                        if (action.isSchemaValid()) {
                            action
                        } else {
                            telemetry.log(TelemetryEvent.ActionParseRejected(action.actionTypeName(), "schema_validation"))
                            null
                        }
                    }
                    is ActionParseResult.Rejected -> {
                        telemetry.log(TelemetryEvent.ActionParseRejected(parsed.actionType, parsed.reason))
                        null
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun JSONObject.toActionParseResult(): ActionParseResult {
        val status = parseStatus(optString("status"))
        val type =
            optString("action_type", optString("type"))
                .lowercase()
                .removePrefix("action_type_")
        val id = optString("id", UUID.randomUUID().toString())
        val title = optString("title", type.ifBlank { "Action" })
        if (status == FulfillmentAction.Status.REMOVED || type == "removed" || type == "remove") {
            return accepted(FulfillmentAction.RemovedAction(id, title))
        }
        return when (type) {
            "active_data_plan" ->
                requiredString("plan")?.let { accepted(FulfillmentAction.ActiveDataPlan(id, title, it, status)) }
                    ?: rejected(type, "missing_plan")
            "add_task", "task" -> accepted(FulfillmentAction.AddTask(id, title, optString("notes"), status))
            "add_timer", "timer" ->
                requiredPositiveInt("duration_seconds")?.let { accepted(FulfillmentAction.AddTimer(id, title, it, status)) }
                    ?: rejected(type, "missing_duration_seconds")
            "add_to_list", "list" ->
                optStringList("items").takeIf { it.isNotEmpty() }?.let { accepted(FulfillmentAction.AddToList(id, title, it, status)) }
                    ?: rejected(type, "missing_items")
            "add_to_memory" ->
                requiredString("text")?.let { accepted(FulfillmentAction.AddToMemory(id, title, it, status)) }
                    ?: rejected(type, "missing_text")
            "browsing_agent" ->
                requiredString("prompt")?.let { accepted(FulfillmentAction.BrowsingAgent(id, title, it, status)) }
                    ?: rejected(type, "missing_prompt")
            "call_agent" ->
                (requiredString("phone_number") ?: requiredString("to"))
                    ?.let { accepted(FulfillmentAction.CallAgent(id, title, it, status)) }
                    ?: rejected(type, "missing_phone_number")
            "create_calendar_event" ->
                parseCalendarEvent(type, id, title, status)
            "create_doc" ->
                requiredString("body")?.let { accepted(FulfillmentAction.CreateDoc(id, title, it, status)) }
                    ?: rejected(type, "missing_body")
            "deep_research" ->
                requiredString("query")?.let { accepted(FulfillmentAction.DeepResearch(id, title, it, status)) }
                    ?: rejected(type, "missing_query")
            "define_term" ->
                requiredString("term")?.let { accepted(FulfillmentAction.DefineTerm(id, title, it, status)) }
                    ?: rejected(type, "missing_term")
            "draft_email" ->
                parseDraftEmail(type, id, title, status)
            "draft_sms" ->
                parseDraftSms(type, id, title, status)
            "invoke_agent_q" ->
                requiredString("prompt")?.let { accepted(FulfillmentAction.InvokeAgentQ(id, title, it, status)) }
                    ?: rejected(type, "missing_prompt")
            "navigation" ->
                requiredString("destination")?.let { accepted(FulfillmentAction.Navigation(id, title, it, status)) }
                    ?: rejected(type, "missing_destination")
            "play_music" ->
                requiredString("query")?.let { accepted(FulfillmentAction.PlayMusic(id, title, it, status)) }
                    ?: rejected(type, "missing_query")
            "provide_event_context" ->
                requiredString("answer")?.let { accepted(FulfillmentAction.ProvideEventContext(id, title, it, status)) }
                    ?: rejected(type, "missing_answer")
            "provide_insight" ->
                requiredString("answer")?.let { accepted(FulfillmentAction.ProvideInsight(id, title, it, status)) }
                    ?: rejected(type, "missing_answer")
            "provide_person_context" ->
                requiredString("answer")?.let { accepted(FulfillmentAction.ProvidePersonContext(id, title, it, status)) }
                    ?: rejected(type, "missing_answer")
            "provide_recall" ->
                requiredString("answer")?.let { accepted(FulfillmentAction.ProvideRecall(id, title, it, status)) }
                    ?: rejected(type, "missing_answer")
            "query_photos" ->
                requiredString("query")?.let { accepted(FulfillmentAction.QueryPhotos(id, title, it, status)) }
                    ?: rejected(type, "missing_query")
            "web_search" ->
                requiredString("query")?.let { accepted(FulfillmentAction.WebSearch(id, title, it, status)) }
                    ?: rejected(type, "missing_query")
            else -> accepted(FulfillmentAction.Unsupported(id, title, type, status))
        }
    }

    private fun JSONObject.parseCalendarEvent(
        type: String,
        id: String,
        title: String,
        status: FulfillmentAction.Status
    ): ActionParseResult {
        val start = requiredLong("start_millis") ?: return rejected(type, "missing_start_millis")
        val end = requiredLong("end_millis") ?: return rejected(type, "missing_end_millis")
        if (start <= 0L || end <= start) return rejected(type, "invalid_time_window")
        return accepted(FulfillmentAction.CreateCalendarEvent(id, title, start, end, optString("location"), status))
    }

    private fun JSONObject.parseDraftEmail(
        type: String,
        id: String,
        title: String,
        status: FulfillmentAction.Status
    ): ActionParseResult {
        val to = requiredString("to") ?: return rejected(type, "missing_to")
        val body = requiredString("body") ?: return rejected(type, "missing_body")
        return accepted(FulfillmentAction.DraftEmail(id, title, to, optString("subject", title), body, status))
    }

    private fun JSONObject.parseDraftSms(
        type: String,
        id: String,
        title: String,
        status: FulfillmentAction.Status
    ): ActionParseResult {
        val to = requiredString("to") ?: return rejected(type, "missing_to")
        val body = requiredString("body") ?: return rejected(type, "missing_body")
        return accepted(FulfillmentAction.DraftSms(id, title, to, body, status))
    }

    private fun parseStatus(raw: String): FulfillmentAction.Status =
        when (raw.lowercase()) {
            "updated" -> FulfillmentAction.Status.UPDATED
            "removed" -> FulfillmentAction.Status.REMOVED
            "executed" -> FulfillmentAction.Status.EXECUTED
            "dismissed" -> FulfillmentAction.Status.DISMISSED
            else -> FulfillmentAction.Status.NEW
        }

    private fun JSONObject.optStringList(name: String): List<String> {
        val array = optJSONArray(name)
        if (array != null) {
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }
        return optString(name)
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun JSONObject.requiredString(name: String): String? =
        if (has(name)) optString(name).trim().takeIf(String::isNotBlank) else null

    private fun JSONObject.requiredLong(name: String): Long? =
        if (has(name)) optLong(name) else null

    private fun JSONObject.requiredPositiveInt(name: String): Int? =
        if (has(name)) optInt(name).takeIf { it > 0 } else null

    private fun accepted(action: FulfillmentAction): ActionParseResult.Accepted =
        ActionParseResult.Accepted(action)

    private fun rejected(
        actionType: String,
        reason: String
    ): ActionParseResult.Rejected =
        ActionParseResult.Rejected(actionType.ifBlank { "unknown" }, reason)

    private sealed class ActionParseResult {
        data class Accepted(
            val action: FulfillmentAction
        ) : ActionParseResult()

        data class Rejected(
            val actionType: String,
            val reason: String
        ) : ActionParseResult()
    }
}
