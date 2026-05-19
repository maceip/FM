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
        if (skillId == "define_term") {
            defineTermIntentNames()
        } else {
            skillIntentNames[skillId] ?: listOf(skillId.uppercase())
        }

    private fun defineTermIntentNames(): List<String> =
        buildList {
            if (cosmoSettings.currentSetting.defineTermsEnabled) {
                add(IntentNameEnum.DEFINE_TERM_INTENT.value)
            }
            if (cosmoSettings.currentSetting.suggestGoogleSearchesEnabled) {
                add(IntentNameEnum.TERM_SEARCH_QUERY.value)
            }
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
        fun intentNamesToSkillIds(intentNames: Collection<String>): Set<String> =
            intentNames
                .flatMap { intentToSkillIds[it] ?: listOf(it.lowercase()) }
                .toSet()

        private val skillIntentNames =
            mapOf(
                "add_to_memory" to listOf(IntentNameEnum.IS_INTERESTING.value),
                "add_to_list" to listOf(IntentNameEnum.ADD_TO_LIST_INTENT.value),
                "web_search" to listOf(IntentNameEnum.TERM_SEARCH_QUERY.value),
                "query_photos" to listOf(IntentNameEnum.PHOTO_QUERY.value),
                "create_calendar_event" to listOf(IntentNameEnum.CALENDAR_EVENT.value),
                "create_doc" to listOf(IntentNameEnum.CREATE_DOC_INTENT.value),
                "provide_person_context" to listOf(IntentNameEnum.PERSON_CONTEXT.value),
                "provide_event_context" to listOf(IntentNameEnum.EVENT_CONTEXT.value),
                "deep_research" to listOf(IntentNameEnum.DEEP_RESEARCH_INTENT.value),
                "provide_insight" to listOf(IntentNameEnum.INSIGHT_INTENT.value),
                "browsing_agent" to listOf(IntentNameEnum.BROWSING_AGENT_INTENT.value),
                "call_agent" to listOf(IntentNameEnum.CALLING_AGENT_INTENT.value),
                "add_timer" to listOf(IntentNameEnum.START_TIMER_INTENT.value),
                "add_task" to listOf(IntentNameEnum.ADD_TASK_INTENT.value),
                "draft_email" to listOf(IntentNameEnum.DRAFT_EMAIL_INTENT.value),
                "draft_sms" to listOf(IntentNameEnum.DRAFT_SMS_INTENT.value),
                "navigation" to listOf(IntentNameEnum.NAVIGATION_INTENT.value),
                "play_music" to listOf(IntentNameEnum.PLAY_MUSIC_INTENT.value),
                "provide_recall" to listOf(IntentNameEnum.PROVIDE_RECALL_INTENT.value),
            )

        private val intentToSkillIds =
            mapOf(
                IntentNameEnum.IS_INTERESTING.value to listOf("add_to_memory"),
                IntentNameEnum.ADD_TO_LIST_INTENT.value to listOf("add_to_list"),
                IntentNameEnum.TERM_SEARCH_QUERY.value to listOf("web_search", "define_term"),
                IntentNameEnum.DEFINE_TERM_INTENT.value to listOf("define_term"),
                IntentNameEnum.PHOTO_QUERY.value to listOf("query_photos"),
                IntentNameEnum.CALENDAR_EVENT.value to listOf("create_calendar_event"),
                IntentNameEnum.CREATE_DOC_INTENT.value to listOf("create_doc"),
                IntentNameEnum.PERSON_CONTEXT.value to listOf("provide_person_context"),
                IntentNameEnum.EVENT_CONTEXT.value to listOf("provide_event_context"),
                IntentNameEnum.DEEP_RESEARCH_INTENT.value to listOf("deep_research"),
                IntentNameEnum.INSIGHT_INTENT.value to listOf("provide_insight"),
                IntentNameEnum.BROWSING_AGENT_INTENT.value to listOf("browsing_agent"),
                IntentNameEnum.CALLING_AGENT_INTENT.value to listOf("call_agent"),
                IntentNameEnum.START_TIMER_INTENT.value to listOf("add_timer"),
                IntentNameEnum.PROVIDE_RECALL_INTENT.value to listOf("provide_recall"),
                IntentNameEnum.NAVIGATION_INTENT.value to listOf("navigation"),
                IntentNameEnum.DRAFT_EMAIL_INTENT.value to listOf("draft_email"),
                IntentNameEnum.ADD_TASK_INTENT.value to listOf("add_task"),
                IntentNameEnum.DRAFT_SMS_INTENT.value to listOf("draft_sms"),
                IntentNameEnum.PLAY_MUSIC_INTENT.value to listOf("play_music"),
            )
    }
}
