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
        return skillChecks[skillId]?.invoke(this, setting) ?: true
    }

    fun enabledSkillIds(allSkillIds: Collection<String>): Set<String> =
        allSkillIds.filter(::isSkillEnabled).toSet()

    private companion object {
        val skillChecks: Map<String, SkillEnablement.(CosmoSetting) -> Boolean> =
            mapOf(
                "add_to_list" to { it.updateKeepEnabled },
                "define_term" to { it.defineTermsEnabled },
                "web_search" to { it.suggestGoogleSearchesEnabled },
                "query_photos" to { it.suggestPhotosEnabled },
                "create_calendar_event" to { it.suggestCalendarEnabled },
                "create_doc" to { it.suggestDocsEnabled },
                "provide_person_context" to {
                    configuration.isProvidePersonContextToggleConfigured(it.providePersonContextEnabled) &&
                        it.providePersonContextEnabled
                },
                "provide_event_context" to {
                    configuration.isProvideEventContextToggleConfigured(it.provideEventContextEnabled) &&
                        it.provideEventContextEnabled
                },
                "deep_research" to { it.suggestDeepResearchEnabled },
                "provide_insight" to { it.cosmoInsightsEnabled },
                "browsing_agent" to { it.suggestMarinerEnabled },
                "call_agent" to { it.suggestCallAgentEnabled },
                "add_timer" to { it.suggestTimerEnabled },
                "add_to_memory" to { configuration.isRecordMemoriesToggleEnabled() && it.recordMemoriesEnabled },
                "add_task" to { it.suggestAddTaskEnabled },
                "draft_email" to { it.suggestMailEnabled },
                "draft_sms" to { it.draftSmsEnabled },
                "navigation" to { it.suggestNavigationEnabled },
                "play_music" to { it.playMusicEnabled },
                "provide_recall" to { it.provideRecallEnabled },
                "invoke_agent_q" to { it.invokeAgentQEnabled && settings.isInvokeAgentQEnabled() },
                "active_data_plan" to { true },
            )
    }
}
