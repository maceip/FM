package com.google.research.air.cosmo.fulfillment

/**
 * Cosmo-shaped fulfillment configuration (`FulfillmentConfigRegistry.getConfigFor`).
 */
data class FulfillmentConfig(
    val enabledSkillIds: Set<String>,
    val enabledBufferKinds: Set<FulfillmentBufferKind> = emptySet(),
    val temperature: Float = 1.0f,
    val maxMemoryRetrievalQueries: Int = 0,
    val memorySearchTopK: Int = 0,
    val maxRawAudioInFulfillmentSec: Int = 30,
    val useLocalLlm: Boolean = false,
)

enum class FulfillmentBufferKind {
    AMBIENT_AUDIO,
    DEVICE_AUDIO,
    WATCH_AUDIO,
    BUDS_AUDIO,
    SCREEN,
    NOTIFICATION,
    INTENT,
}
