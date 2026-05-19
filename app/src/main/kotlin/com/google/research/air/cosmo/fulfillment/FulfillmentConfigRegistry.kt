package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.skills.SkillRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FulfillmentConfigRegistry
@Inject
constructor(
    private val skills: SkillRegistry,
    private val settings: CosmoSettingStore,
    private val skillEnablement: SkillEnablement,
) {
    fun getConfigFor(
        trigger: FulfillmentTriggerType,
        gatekeeperIntentNames: List<String> = emptyList(),
        useLocalLlm: Boolean = false,
    ): FulfillmentConfig {
        val params = triggerParams.getValue(trigger)

        return FulfillmentConfig(
            enabledSkillIds = enabledSkillIds(trigger, gatekeeperIntentNames),
            enabledBufferKinds = enabledBufferKinds(trigger),
            temperature = params.temperature,
            maxMemoryRetrievalQueries = params.maxMemoryQueries,
            memorySearchTopK = params.memoryTopK,
            maxRawAudioInFulfillmentSec = settings.getMaxRawAudioInFulfillmentSec(),
            useLocalLlm = useLocalLlm,
        )
    }

    private fun enabledSkillIds(
        trigger: FulfillmentTriggerType,
        gatekeeperIntentNames: List<String>,
    ): Set<String> {
        val allEnabled = skillEnablement.enabledSkillIds(skills.list().map { it.id })
        if (trigger != FulfillmentTriggerType.GATEKEEPER_TRIGGER) return allEnabled

        val gatekeeperSkills = GatekeeperResultUtils.intentNamesToSkillIds(gatekeeperIntentNames)
        return if (gatekeeperSkills.isEmpty()) allEnabled else allEnabled.intersect(gatekeeperSkills)
    }

    private fun enabledBufferKinds(trigger: FulfillmentTriggerType): Set<FulfillmentBufferKind> {
        val buffers = enabledBufferSwitches.mapNotNull { it.enabledKind(settings) }.toSet()
        if (trigger != FulfillmentTriggerType.GATEKEEPER_TRIGGER) return buffers
        return buffers.intersect(gatekeeperBufferKinds)
    }

    private data class TriggerParams(
        val temperature: Float,
        val maxMemoryQueries: Int,
        val memoryTopK: Int,
    )

    private data class BufferSwitch(
        val kind: FulfillmentBufferKind,
        val isEnabled: CosmoSettingStore.() -> Boolean,
    ) {
        fun enabledKind(settings: CosmoSettingStore): FulfillmentBufferKind? =
            kind.takeIf { settings.isEnabled() }
    }

    private companion object {
        val triggerParams =
            mapOf(
                FulfillmentTriggerType.USER_QUERY to TriggerParams(0.5f, 0, 0),
                FulfillmentTriggerType.BATCH_PIPELINE to TriggerParams(0.5f, 0, 0),
                FulfillmentTriggerType.GATEKEEPER_TRIGGER to TriggerParams(1.0f, 0, 0),
                FulfillmentTriggerType.PIXEL_PHONE_DOUBLE_TAP to TriggerParams(1.0f, 5, 20),
                FulfillmentTriggerType.PIXEL_WATCH_DOUBLE_PINCH to TriggerParams(0.8f, 0, 0),
                FulfillmentTriggerType.PIXEL_PHONE_PICK_UP to TriggerParams(1.0f, 0, 0),
            )

        val enabledBufferSwitches =
            listOf(
                BufferSwitch(FulfillmentBufferKind.AMBIENT_AUDIO, CosmoSettingStore::isAmbientAudioEnabled),
                BufferSwitch(FulfillmentBufferKind.DEVICE_AUDIO, CosmoSettingStore::isMediaAudioEnabled),
                BufferSwitch(FulfillmentBufferKind.WATCH_AUDIO, CosmoSettingStore::isWatchAudioEnabled),
                BufferSwitch(FulfillmentBufferKind.BUDS_AUDIO, CosmoSettingStore::isBudsAudioEnabled),
                BufferSwitch(FulfillmentBufferKind.SCREEN, CosmoSettingStore::isScreenContextEnabled),
                BufferSwitch(FulfillmentBufferKind.NOTIFICATION, CosmoSettingStore::isNotificationCaptureEnabled),
                BufferSwitch(FulfillmentBufferKind.INTENT, CosmoSettingStore::isIntentExtractionEnabled),
            )

        val gatekeeperBufferKinds =
            setOf(
                FulfillmentBufferKind.AMBIENT_AUDIO,
                FulfillmentBufferKind.WATCH_AUDIO,
                FulfillmentBufferKind.BUDS_AUDIO,
                FulfillmentBufferKind.DEVICE_AUDIO,
            )
    }
}
