package com.google.research.air.cosmo.presence

import com.google.research.air.cosmo.setting.CosmoSettingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Provides specialty [PresenceCard]s that are not derived from fulfillment actions or gatekeeper
 * results. Two categories:
 *
 * - **Onboarding**: shown once on first app launch until the user completes setup.
 * - **Settings**: shown when the app is foregrounded via a settings-related intent (QS tile long
 *   press, system app-info → "App preferences", or manual trigger from the debug panel).
 *
 * The [PresenceController] merges this source's output into its state via [cards]. Specialty cards
 * take priority over fulfillment/gatekeeper when present.
 */
@Singleton
class PresenceSpecialtyCards @Inject constructor(
    private val settingStore: CosmoSettingStore
) {
    private val settingsTrigger = MutableStateFlow<CardPayload.Settings?>(null)

    val cards: Flow<List<PresenceCard>> = combine(
        settingStore.settingState,
        settingsTrigger
    ) { setting, settingsPayload ->
        buildList {
            if (!setting.onboardingCompleted) {
                add(buildOnboardingCard(setting))
            }
            settingsPayload?.let { add(buildSettingsCard(it)) }
        }
    }

    fun showSettings(items: List<SettingItem> = emptyList()) {
        settingsTrigger.value = CardPayload.Settings(
            section = "SYSTEM SETTINGS",
            items = items.ifEmpty { defaultSettingItems() }
        )
    }

    fun dismissSettings() {
        settingsTrigger.value = null
    }

    suspend fun completeOnboarding() {
        settingStore.markOnboardingCompleted()
    }

    private fun buildOnboardingCard(
        setting: com.google.research.air.cosmo.setting.proto.CosmoSetting
    ): PresenceCard {
        val checkpoints = listOf(
            OnboardingCheckpoint("AUDIO", setting.ambientAudioEnabled),
            OnboardingCheckpoint("SCREEN", setting.screenContextEnabled),
            OnboardingCheckpoint("AGENT", setting.globalCosmoEnabled),
            OnboardingCheckpoint("NOTIFY", setting.notificationCaptureEnabled)
        )
        val completedCount = checkpoints.count { it.complete }
        return PresenceCard(
            id = "onboarding",
            payload = CardPayload.Onboarding(
                step = completedCount + 1,
                totalSteps = checkpoints.size + 1,
                title = "WELCOME & SETUP",
                body = "Enable the context sources that Cosmo needs to work.\n" +
                        "Each permission lets the ambient agent observe a different signal.",
                checkpoints = checkpoints
            ),
            actions = listOf(
                CardAction("next", "NEXT", "STEP", CardActionType.PRIMARY),
                CardAction("skip", "SKIP", "FOR NOW"),
                CardAction("collapse", "COLLAPSE", "TO EDGE", CardActionType.COLLAPSE)
            )
        )
    }

    private fun buildSettingsCard(payload: CardPayload.Settings): PresenceCard =
        PresenceCard(
            id = "settings",
            payload = payload,
            actions = listOf(
                CardAction("apply", "APPLY", type = CardActionType.PRIMARY),
                CardAction("reset", "RESET", "DEFAULTS"),
                CardAction("collapse", "COLLAPSE", "TO EDGE", CardActionType.COLLAPSE)
            )
        )

    private fun defaultSettingItems(): List<SettingItem> = listOf(
        SettingItem("Ambient Audio", settingStore.isAmbientAudioEnabled()),
        SettingItem("Screen Context", settingStore.isScreenContextEnabled()),
        SettingItem("Notification Capture", settingStore.isNotificationCaptureEnabled()),
        SettingItem("Camera Input", settingStore.isCameraEnabled()),
        SettingItem("Gatekeeper", settingStore.isGatekeeperEnabled())
    )
}
