package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.setting.CosmoConfiguration
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.setting.proto.CosmoSetting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cosmo-shaped per-skill enablement from [CosmoSetting] suggest/record flags
 * (`GatekeeperResultUtils` + `FulfillmentConfigRegistry` consumers in recovered APK).
 */
@Singleton
class SkillEnablement
@Inject
constructor(
    private val settings: CosmoSettingStore,
    private val configuration: CosmoConfiguration,
) {
    fun isSkillEnabled(skillId: String): Boolean {
        if (!configuration.isCosmoSkillsEnabled()) return false
        val setting = settings.currentSetting
        return when (skillId) {
            "add_to_list" -> setting.updateKeepEnabled
            "define_term" -> setting.defineTermsEnabled
            "web_search" -> setting.suggestGoogleSearchesEnabled
            "query_photos" -> setting.suggestPhotosEnabled
            "create_calendar_event" -> setting.suggestCalendarEnabled
            "create_doc" -> setting.suggestDocsEnabled
            "provide_person_context" ->
                configuration.isProvidePersonContextToggleConfigured(setting.providePersonContextEnabled) &&
                    setting.providePersonContextEnabled
            "provide_event_context" ->
                configuration.isProvideEventContextToggleConfigured(setting.provideEventContextEnabled) &&
                    setting.provideEventContextEnabled
            "deep_research" -> setting.suggestDeepResearchEnabled
            "provide_insight" -> setting.cosmoInsightsEnabled
            "browsing_agent" -> setting.suggestMarinerEnabled
            "call_agent" -> setting.suggestCallAgentEnabled
            "add_timer" -> setting.suggestTimerEnabled
            "add_to_memory" ->
                configuration.isRecordMemoriesToggleEnabled() && setting.recordMemoriesEnabled
            "add_task" -> setting.suggestAddTaskEnabled
            "draft_email" -> setting.suggestMailEnabled
            "draft_sms" -> setting.draftSmsEnabled
            "navigation" -> setting.suggestNavigationEnabled
            "play_music" -> setting.playMusicEnabled
            "provide_recall" -> setting.provideRecallEnabled
            "invoke_agent_q" -> setting.invokeAgentQEnabled && settings.isInvokeAgentQEnabled()
            "active_data_plan" -> true
            else -> true
        }
    }

    fun enabledSkillIds(allSkillIds: Collection<String>): Set<String> =
        allSkillIds.filter(::isSkillEnabled).toSet()
}
