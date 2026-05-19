package com.google.research.air.cosmo.gatekeeping.util

import com.google.research.air.cosmo.fulfillment.SkillEnablement
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.setting.CosmoSettingStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recovered cosmo `GatekeeperResultUtils.getIntents` — maps gatekeeper output to fulfillment
 * intent strings, gated by user settings.
 */
@Singleton
class GatekeeperResultUtils
@Inject
constructor(
    private val cosmoSettings: CosmoSettingStore,
    private val skillEnablement: SkillEnablement,
) {
    fun getIntents(detectedIntents: GatekeepingResult): List<String> {
        val intents = mutableListOf<String>()
        detectedIntents.detectedSkills.forEach { (skillId, _) ->
            if (!skillEnablement.isSkillEnabled(skillId)) return@forEach
            intents.addAll(skillIdToIntentNames(skillId))
        }
        return intents.distinct()
    }

    private fun skillIdToIntentNames(skillId: String): List<String> =
        when (skillId) {
            "add_to_memory" -> listOf(IntentNameEnum.IS_INTERESTING.value)
            "add_to_list" -> listOf(IntentNameEnum.ADD_TO_LIST_INTENT.value)
            "define_term" ->
                buildList {
                    if (cosmoSettings.currentSetting.defineTermsEnabled) {
                        add(IntentNameEnum.DEFINE_TERM_INTENT.value)
                    }
                    if (cosmoSettings.currentSetting.suggestGoogleSearchesEnabled) {
                        add(IntentNameEnum.TERM_SEARCH_QUERY.value)
                    }
                }
            "web_search" -> listOf(IntentNameEnum.TERM_SEARCH_QUERY.value)
            "query_photos" -> listOf(IntentNameEnum.PHOTO_QUERY.value)
            "create_calendar_event" -> listOf(IntentNameEnum.CALENDAR_EVENT.value)
            "create_doc" -> listOf(IntentNameEnum.CREATE_DOC_INTENT.value)
            "provide_person_context" -> listOf(IntentNameEnum.PERSON_CONTEXT.value)
            "provide_event_context" -> listOf(IntentNameEnum.EVENT_CONTEXT.value)
            "deep_research" -> listOf(IntentNameEnum.DEEP_RESEARCH_INTENT.value)
            "provide_insight" -> listOf(IntentNameEnum.INSIGHT_INTENT.value)
            "browsing_agent" -> listOf(IntentNameEnum.BROWSING_AGENT_INTENT.value)
            "call_agent" -> listOf(IntentNameEnum.CALLING_AGENT_INTENT.value)
            "add_timer" -> listOf(IntentNameEnum.START_TIMER_INTENT.value)
            "add_task" -> listOf(IntentNameEnum.ADD_TASK_INTENT.value)
            "draft_email" -> listOf(IntentNameEnum.DRAFT_EMAIL_INTENT.value)
            "draft_sms" -> listOf(IntentNameEnum.DRAFT_SMS_INTENT.value)
            "navigation" -> listOf(IntentNameEnum.NAVIGATION_INTENT.value)
            "play_music" -> listOf(IntentNameEnum.PLAY_MUSIC_INTENT.value)
            "provide_recall" -> listOf(IntentNameEnum.PROVIDE_RECALL_INTENT.value)
            else -> listOf(skillId.uppercase())
        }

    enum class IntentNameEnum(
        val value: String
    ) {
        IS_INTERESTING("IS_INTERESTING"),
        ADD_TO_LIST_INTENT("ADD_TO_LIST_INTENT"),
        TERM_SEARCH_QUERY("TERM_SEARCH_QUERY"),
        DEFINE_TERM_INTENT("DEFINE_TERM_INTENT"),
        PHOTO_QUERY("PHOTO_QUERY"),
        CALENDAR_EVENT("CALENDAR_EVENT"),
        CREATE_DOC_INTENT("CREATE_DOC_INTENT"),
        PERSON_CONTEXT("PERSON_CONTEXT"),
        EVENT_CONTEXT("EVENT_CONTEXT"),
        DEEP_RESEARCH_INTENT("DEEP_RESEARCH_INTENT"),
        INSIGHT_INTENT("INSIGHT_INTENT"),
        BROWSING_AGENT_INTENT("BROWSING_AGENT_INTENT"),
        CALLING_AGENT_INTENT("CALLING_AGENT_INTENT"),
        START_TIMER_INTENT("START_TIMER_INTENT"),
        PROVIDE_RECALL_INTENT("PROVIDE_RECALL_INTENT"),
        NAVIGATION_INTENT("NAVIGATION_INTENT"),
        DRAFT_EMAIL_INTENT("DRAFT_EMAIL_INTENT"),
        ADD_TASK_INTENT("ADD_TASK_INTENT"),
        DRAFT_SMS_INTENT("DRAFT_SMS_INTENT"),
        PLAY_MUSIC_INTENT("PLAY_MUSIC_INTENT"),
    }

    companion object {
        /** Map cosmo intent strings back to genesis skill ids for schema filtering. */
        fun intentNamesToSkillIds(intentNames: Collection<String>): Set<String> {
            val skills = mutableSetOf<String>()
            intentNames.forEach { intent ->
                when (intent) {
                    IntentNameEnum.IS_INTERESTING.value -> skills += "add_to_memory"
                    IntentNameEnum.ADD_TO_LIST_INTENT.value -> skills += "add_to_list"
                    IntentNameEnum.TERM_SEARCH_QUERY.value -> {
                        skills += "web_search"
                        skills += "define_term"
                    }
                    IntentNameEnum.DEFINE_TERM_INTENT.value -> skills += "define_term"
                    IntentNameEnum.PHOTO_QUERY.value -> skills += "query_photos"
                    IntentNameEnum.CALENDAR_EVENT.value -> skills += "create_calendar_event"
                    IntentNameEnum.CREATE_DOC_INTENT.value -> skills += "create_doc"
                    IntentNameEnum.PERSON_CONTEXT.value -> skills += "provide_person_context"
                    IntentNameEnum.EVENT_CONTEXT.value -> skills += "provide_event_context"
                    IntentNameEnum.DEEP_RESEARCH_INTENT.value -> skills += "deep_research"
                    IntentNameEnum.INSIGHT_INTENT.value -> skills += "provide_insight"
                    IntentNameEnum.BROWSING_AGENT_INTENT.value -> skills += "browsing_agent"
                    IntentNameEnum.CALLING_AGENT_INTENT.value -> skills += "call_agent"
                    IntentNameEnum.START_TIMER_INTENT.value -> skills += "add_timer"
                    IntentNameEnum.PROVIDE_RECALL_INTENT.value -> skills += "provide_recall"
                    IntentNameEnum.NAVIGATION_INTENT.value -> skills += "navigation"
                    IntentNameEnum.DRAFT_EMAIL_INTENT.value -> skills += "draft_email"
                    IntentNameEnum.ADD_TASK_INTENT.value -> skills += "add_task"
                    IntentNameEnum.DRAFT_SMS_INTENT.value -> skills += "draft_sms"
                    IntentNameEnum.PLAY_MUSIC_INTENT.value -> skills += "play_music"
                    else ->
                        skills += intent.lowercase()
                }
            }
            return skills
        }
    }
}
