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
        val allEnabled =
            skillEnablement.enabledSkillIds(skills.list().map { it.id })
        val gatekeeperSkills =
            if (gatekeeperIntentNames.isEmpty()) {
                emptySet()
            } else {
                GatekeeperResultUtils.intentNamesToSkillIds(gatekeeperIntentNames)
            }
        val enabledSkillIds =
            when (trigger) {
                FulfillmentTriggerType.GATEKEEPER_TRIGGER -> {
                    if (gatekeeperSkills.isEmpty()) {
                        allEnabled
                    } else {
                        allEnabled.intersect(gatekeeperSkills)
                    }
                }
                else -> allEnabled
            }
        val buffers = mutableSetOf<FulfillmentBufferKind>()
        if (settings.isAmbientAudioEnabled()) buffers += FulfillmentBufferKind.AMBIENT_AUDIO
        if (settings.isMediaAudioEnabled()) buffers += FulfillmentBufferKind.DEVICE_AUDIO
        if (settings.isWatchAudioEnabled()) buffers += FulfillmentBufferKind.WATCH_AUDIO
        if (settings.isBudsAudioEnabled()) buffers += FulfillmentBufferKind.BUDS_AUDIO
        if (settings.isScreenContextEnabled()) buffers += FulfillmentBufferKind.SCREEN
        if (settings.isNotificationCaptureEnabled()) buffers += FulfillmentBufferKind.NOTIFICATION
        if (settings.isIntentExtractionEnabled()) buffers += FulfillmentBufferKind.INTENT

        val (temperature, maxMemoryQueries, memoryTopK) =
            when (trigger) {
                FulfillmentTriggerType.USER_QUERY -> Triple(0.5f, 0, 0)
                FulfillmentTriggerType.BATCH_PIPELINE -> Triple(0.5f, 0, 0)
                FulfillmentTriggerType.GATEKEEPER_TRIGGER -> Triple(1.0f, 0, 0)
                FulfillmentTriggerType.PIXEL_PHONE_DOUBLE_TAP -> Triple(1.0f, 5, 20)
                FulfillmentTriggerType.PIXEL_WATCH_DOUBLE_PINCH -> Triple(0.8f, 0, 0)
                FulfillmentTriggerType.PIXEL_PHONE_PICK_UP -> Triple(1.0f, 0, 0)
            }

        val gatekeeperBuffers =
            if (trigger == FulfillmentTriggerType.GATEKEEPER_TRIGGER) {
                setOf(
                    FulfillmentBufferKind.AMBIENT_AUDIO,
                    FulfillmentBufferKind.WATCH_AUDIO,
                    FulfillmentBufferKind.BUDS_AUDIO,
                    FulfillmentBufferKind.DEVICE_AUDIO,
                )
            } else {
                emptySet()
            }
        val enabledBuffers =
            if (gatekeeperBuffers.isEmpty()) {
                buffers
            } else {
                buffers.intersect(gatekeeperBuffers).ifEmpty { gatekeeperBuffers.intersect(buffers) }
            }

        return FulfillmentConfig(
            enabledSkillIds = enabledSkillIds,
            enabledBufferKinds = enabledBuffers,
            temperature = temperature,
            maxMemoryRetrievalQueries = maxMemoryQueries,
            memorySearchTopK = memoryTopK,
            maxRawAudioInFulfillmentSec = settings.getMaxRawAudioInFulfillmentSec(),
            useLocalLlm = useLocalLlm,
        )
    }
}
