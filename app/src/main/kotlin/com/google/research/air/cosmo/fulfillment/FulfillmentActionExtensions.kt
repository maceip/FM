package com.google.research.air.cosmo.fulfillment

/**
 * Typed validation for LLM-produced JSON actions before they enter the repository.
 */
@Suppress("CyclomaticComplexMethod")
internal fun FulfillmentAction.isSchemaValid(): Boolean =
    when (this) {
        is FulfillmentAction.RemovedAction -> true
        is FulfillmentAction.Unsupported -> true
        is FulfillmentAction.CreateCalendarEvent -> startMillis > 0L && endMillis > startMillis
        is FulfillmentAction.AddTimer -> durationSeconds > 0
        is FulfillmentAction.AddToMemory -> text.isNotBlank()
        is FulfillmentAction.CallAgent -> !phoneNumber.isNullOrBlank()
        is FulfillmentAction.DraftSms -> to.isNotBlank()
        is FulfillmentAction.DraftEmail -> to.isNotBlank()
        is FulfillmentAction.BrowsingAgent -> prompt.isNotBlank()
        is FulfillmentAction.InvokeAgentQ -> prompt.isNotBlank()
        is FulfillmentAction.DeepResearch -> query.isNotBlank()
        is FulfillmentAction.WebSearch -> query.isNotBlank()
        is FulfillmentAction.Navigation -> destination.isNotBlank()
        is FulfillmentAction.PlayMusic -> query.isNotBlank()
        is FulfillmentAction.DefineTerm -> term.isNotBlank()
        is FulfillmentAction.CreateDoc -> body.isNotBlank()
        is FulfillmentAction.AddToList -> items.isNotEmpty()
        is FulfillmentAction.ActiveDataPlan -> plan.isNotBlank()
        is FulfillmentAction.QueryPhotos -> query.isNotBlank()
        is FulfillmentAction.AddTask -> title.isNotBlank()
        is FulfillmentAction.ProvideEventContext -> answer.isNotBlank()
        is FulfillmentAction.ProvideInsight -> answer.isNotBlank()
        is FulfillmentAction.ProvidePersonContext -> answer.isNotBlank()
        is FulfillmentAction.ProvideRecall -> answer.isNotBlank()
    }

/**
 * Actions that launch side effects or leave the app should confirm from the main Actions UI before [FulfillmentActionHandler.execute].
 */
internal fun FulfillmentAction.requiresExecuteConfirmation(): Boolean =
    when (this) {
        is FulfillmentAction.ProvideEventContext,
        is FulfillmentAction.ProvideInsight,
        is FulfillmentAction.ProvidePersonContext,
        is FulfillmentAction.ProvideRecall,
        is FulfillmentAction.RemovedAction,
        is FulfillmentAction.Unsupported -> false
        else -> true
    }

@Suppress("CyclomaticComplexMethod")
internal fun FulfillmentAction.actionTypeName(): String =
    when (this) {
        is FulfillmentAction.ActiveDataPlan -> "active_data_plan"
        is FulfillmentAction.AddTask -> "add_task"
        is FulfillmentAction.AddTimer -> "add_timer"
        is FulfillmentAction.AddToList -> "add_to_list"
        is FulfillmentAction.AddToMemory -> "add_to_memory"
        is FulfillmentAction.BrowsingAgent -> "browsing_agent"
        is FulfillmentAction.CallAgent -> "call_agent"
        is FulfillmentAction.CreateCalendarEvent -> "create_calendar_event"
        is FulfillmentAction.CreateDoc -> "create_doc"
        is FulfillmentAction.DeepResearch -> "deep_research"
        is FulfillmentAction.DefineTerm -> "define_term"
        is FulfillmentAction.DraftEmail -> "draft_email"
        is FulfillmentAction.DraftSms -> "draft_sms"
        is FulfillmentAction.InvokeAgentQ -> "invoke_agent_q"
        is FulfillmentAction.Navigation -> "navigation"
        is FulfillmentAction.PlayMusic -> "play_music"
        is FulfillmentAction.ProvideEventContext -> "provide_event_context"
        is FulfillmentAction.ProvideInsight -> "provide_insight"
        is FulfillmentAction.ProvidePersonContext -> "provide_person_context"
        is FulfillmentAction.ProvideRecall -> "provide_recall"
        is FulfillmentAction.QueryPhotos -> "query_photos"
        is FulfillmentAction.RemovedAction -> "removed"
        is FulfillmentAction.Unsupported -> "unsupported"
        is FulfillmentAction.WebSearch -> "web_search"
    }
