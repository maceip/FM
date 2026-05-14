package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.lib.llm.LLMRouteReason
import com.google.research.air.cosmo.lib.llm.ModelPath

enum class FulfillmentTriggerType {
    BATCH_PIPELINE,
    GATEKEEPER_TRIGGER,
    PIXEL_PHONE_DOUBLE_TAP,
    PIXEL_PHONE_PICK_UP,
    PIXEL_WATCH_DOUBLE_PINCH,
    USER_QUERY
}

sealed class FulfillmentAction(
    open val id: String,
    open val title: String,
    open val status: Status = Status.NEW
) {
    enum class Status { NEW, UPDATED, REMOVED, EXECUTED, DISMISSED }

    data class AddTask(
        override val id: String,
        override val title: String,
        val notes: String = "",
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class AddTimer(
        override val id: String,
        override val title: String,
        val durationSeconds: Int,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class AddToList(
        override val id: String,
        override val title: String,
        val items: List<String>,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class AddToMemory(
        override val id: String,
        override val title: String,
        val text: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class BrowsingAgent(
        override val id: String,
        override val title: String,
        val prompt: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class CallAgent(
        override val id: String,
        override val title: String,
        val phoneNumber: String?,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class CreateCalendarEvent(
        override val id: String,
        override val title: String,
        val startMillis: Long,
        val endMillis: Long,
        val location: String = "",
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class CreateDoc(
        override val id: String,
        override val title: String,
        val body: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class DeepResearch(
        override val id: String,
        override val title: String,
        val query: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class DefineTerm(
        override val id: String,
        override val title: String,
        val term: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class DraftEmail(
        override val id: String,
        override val title: String,
        val to: String,
        val subject: String,
        val body: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class DraftSms(
        override val id: String,
        override val title: String,
        val to: String,
        val body: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class InvokeAgentQ(
        override val id: String,
        override val title: String,
        val prompt: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class Navigation(
        override val id: String,
        override val title: String,
        val destination: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class PlayMusic(
        override val id: String,
        override val title: String,
        val query: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class ProvideEventContext(
        override val id: String,
        override val title: String,
        val answer: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class ProvideInsight(
        override val id: String,
        override val title: String,
        val answer: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class ProvidePersonContext(
        override val id: String,
        override val title: String,
        val answer: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class ProvideRecall(
        override val id: String,
        override val title: String,
        val answer: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class QueryPhotos(
        override val id: String,
        override val title: String,
        val query: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class WebSearch(
        override val id: String,
        override val title: String,
        val query: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class ActiveDataPlan(
        override val id: String,
        override val title: String,
        val plan: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)

    data class RemovedAction(
        override val id: String,
        override val title: String,
        override val status: Status = Status.REMOVED
    ) : FulfillmentAction(id, title, status)

    data class Unsupported(
        override val id: String,
        override val title: String,
        val rawType: String,
        override val status: Status = Status.NEW
    ) : FulfillmentAction(id, title, status)
}

data class FulfillmentDebugInfo(
    val fulfillmentId: String,
    val createdAt: Long,
    val llmQuery: String,
    val llmResult: String,
    val latencyMs: Long,
    val numNewActions: Int,
    val numUpdatedActions: Int,
    val numRemovedActions: Int,
    val fulfillmentTriggerType: FulfillmentTriggerType,
    val userQuery: String? = null,
    val actionIds: List<String> = emptyList(),
    val sourceBufferCounts: Map<String, Int> = emptyMap(),
    val gatekeeperModelId: String? = null,
    val gatekeeperDetectedBy: String? = null,
    val gatekeeperSkills: Map<String, Float> = emptyMap(),
    val llmModelPath: ModelPath? = null,
    val llmRouteReason: LLMRouteReason? = null,
    val errorCause: String? = null
)
