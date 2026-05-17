package com.google.research.air.cosmo.gatekeeping.backend

import android.content.Context
import com.google.research.air.cosmo.gatekeeping.GatekeeperModelIds
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.gatekeeping.InherentAudioGatekeepingModel
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the from-scratch joint audio→intent model produced by the `~/inherent` project. See
 * `~/inherent/contracts/runtime_contract.md` for the input/output tensor shapes and head order
 * this backend assumes.
 *
 * Activation is double-gated:
 *  1. The `inherent.tflite` asset (and `inherent.metadata.json` sidecar) must be present, either
 *     bundled in `assets/models/` or provisioned to the app-private files dir at runtime.
 *  2. The user setting `isInherentBackendEnabled` must be true. Off by default — this is opt-in
 *     until the model has shipped at least one validated release.
 *
 * Default selection priority (see [AudioGatekeepingBackendSelector]):
 *   ConformerEdgeTpu (Pixel only, off today) → Inherent (if available + enabled) → CpuRecovery (always)
 */
@Singleton
class InherentBackend
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val model: Lazy<InherentAudioGatekeepingModel>
) : AudioGatekeepingBackend {

    override val backendId = BackendId.Inherent

    override val isAvailable: Boolean by lazy { computeIsAvailable() }

    private fun computeIsAvailable(): Boolean {
        return assetExists(checkNotNull(GatekeeperModelIds.filenameFor(GatekeeperModelIds.INHERENT_V0))) &&
            assetExists(checkNotNull(GatekeeperModelIds.filenameFor(GatekeeperModelIds.INHERENT_V0_METADATA))) &&
            model.get().isUsable()
    }

    override suspend fun classify(input: AudioInput): GatekeepingResult? {
        check(isAvailable) { "InherentBackend.classify called when isAvailable=false" }
        // C1 mutex: Inherent runs the joint audio->intent path; ambient text under Inherent is
        // owned by the audio bytes path. Returning null for TranscribedSegment IS the dispatch
        // invariant: there is no second classifier path for ambient text under Inherent.
        val pcm = (input as? AudioInput.RawPcm) ?: return null
        return model.get().predict(pcm.pcm)
    }

    override fun close() {
        runCatching { model.get().close() }
    }

    private fun assetExists(filename: String): Boolean =
        runCatching { context.assets.open("models/$filename").use { true } }.getOrDefault(false)
}
