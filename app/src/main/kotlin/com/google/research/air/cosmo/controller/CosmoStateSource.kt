package com.google.research.air.cosmo.controller

/**
 * Tiny probe interface mirroring cosmo's recovered shape at
 * `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/controller/CosmoStateSource.smali`.
 * Two methods, no sealed state ADT, no battery-hysteresis machinery — cosmo's `CosmoController`
 * directly implements this interface (`CosmoController.smali` declares `.implements
 * Lcom/google/research/air/cosmo/controller/CosmoStateSource;`) and other consumers depend on the
 * interface rather than the controller class.
 *
 * Genesis: [CosmoController] implements this. Inject [CosmoStateSource], not [CosmoController],
 * whenever a downstream type only needs the state probe.
 */
interface CosmoStateSource {
    fun isCosmoEnabled(): Boolean

    fun isAnyMicAudioCaptureOn(): Boolean
}
