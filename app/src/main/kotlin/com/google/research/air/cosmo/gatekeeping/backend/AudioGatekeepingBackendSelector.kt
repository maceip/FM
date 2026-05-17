package com.google.research.air.cosmo.gatekeeping.backend

import android.util.Log
import com.google.research.air.cosmo.setting.CosmoSettingStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses which backend the unified `Gatekeeper` (post-PR1) routes ambient audio through.
 *
 * Default priority:
 *   1. [ConformerEdgeTpuBackend] — Pixel + Darwinn delegate active. Today: never selected because
 *      `isAvailable = false` until the Pixel return + hackathon-notes work resolves the device-class
 *      probe.
 *   2. [InherentBackend] — opt-in CPU joint model. Selected when both the `inherent.tflite` asset
 *      is present and the user has enabled the setting.
 *   3. [CpuRecoveryBackend] — always-available baseline. Catches everything that the first two
 *      decline.
 *
 * Override: a debug setting (`forcedBackend`) can pin selection to one specific backend regardless
 * of availability — used for A/B comparison and regression-testing the EdgeTPU path on a Pixel.
 */
@Singleton
class AudioGatekeepingBackendSelector @Inject constructor(
    private val edgeTpu: ConformerEdgeTpuBackend,
    private val inherent: InherentBackend,
    private val cpuRecovery: CpuRecoveryBackend,
    private val settings: CosmoSettingStore,
) {
    /** Returns the backend that should classify this captor's audio right now. Stable per session. */
    fun select(): AudioGatekeepingBackend {
        val forced = forcedBackendOrNull()
        if (forced != null) {
            Log.i(TAG, "Backend forced via debug setting: ${forced.backendId.displayName}")
            return forced
        }
        return when {
            edgeTpu.isAvailable -> {
                Log.i(TAG, "Selected ConformerEdgeTpuBackend (Pixel/Darwinn path active)")
                edgeTpu
            }
            rawAudioRealtimeEnabled() && inherent.isAvailable -> {
                Log.i(TAG, "Selected InherentBackend (asset present + setting enabled)")
                inherent
            }
            else -> {
                Log.i(TAG, "Selected CpuRecoveryBackend (Magic Mic + BERT)")
                cpuRecovery
            }
        }
    }

    /** Returns every backend whose `isAvailable` is true; used by debug UI for transparency. */
    fun listAvailable(): List<AudioGatekeepingBackend> =
        listOf(edgeTpu, inherent, cpuRecovery).filter { it.isAvailable }

    private fun forcedBackendOrNull(): AudioGatekeepingBackend? {
        // Future workstream: read settingsRepository.debug.forcedBackend. Returns the named
        // backend even if isAvailable=false; the impl is responsible for
        // failing loudly when called in that state, which surfaces config errors fast.
        return null
    }

    private fun rawAudioRealtimeEnabled(): Boolean =
        settings.isRawAudioGatekeeperEnabled() && settings.isRealtimeProcessingEnabled()

    private companion object {
        const val TAG = "GatekeepingBackendSelector"
    }
}
