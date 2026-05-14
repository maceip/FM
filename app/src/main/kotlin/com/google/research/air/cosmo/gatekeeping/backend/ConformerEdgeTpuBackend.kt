package com.google.research.air.cosmo.gatekeeping.backend

import com.google.research.air.cosmo.gatekeeping.AudioGatekeepingModel
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps cosmo's recovered EdgeTPU Conformer (`audio_gatekeeper_edgetpu.tflite` +
 * `audio_frontend.tflite`) loaded via the Darwinn delegate.
 *
 * `isAvailable` is **always false today** — the delegate links against Pixel-internal system
 * libraries (`libedgetpu_client.google.so`, `libedgetpu_tachyon.google.so`) that don't ship outside
 * Pixel firmware, and the model uses Darwinn-only custom ops (`dwg_tpu_offload_async`,
 * `dwg_barrier`). Re-enabling this backend requires:
 *
 *  1. The Pixel target device to return so we can verify the Darwinn delegate loads at runtime.
 *  2. Hackathon notes confirming whether this model was Pixel-Tensor-on-die or Magnetar/Coral-attached.
 *  3. A device-class probe replacing the `false` literal in [computeIsAvailable].
 *
 * The classification body delegates to [AudioGatekeepingModel.predict] which already loads the
 * frontend + backend tflites and runs inference. The backend layer adds no extra work; its role
 * is purely routing so [AudioGatekeepingBackendSelector] can pick this path when it's enabled.
 */
@Singleton
class ConformerEdgeTpuBackend
@Inject
constructor(
    private val audioModel: Lazy<AudioGatekeepingModel>
) : AudioGatekeepingBackend {

    override val backendId = BackendId.ConformerEdgeTpu

    override val isAvailable: Boolean by lazy { computeIsAvailable() }

    private fun computeIsAvailable(): Boolean {
        // Hardware-class predicate. Today: always false. Future shape (after Pixel return +
        // notes resolve): return Build.SOC_MANUFACTURER == "google" && darwinnDelegateLoadsCleanly()
        return false
    }

    override suspend fun classify(input: AudioInput): GatekeepingResult? {
        check(isAvailable) { "ConformerEdgeTpuBackend.classify called when isAvailable=false" }
        // C1 mutex: when the EdgeTPU Conformer is the active backend, the ambient text path
        // is owned by the audio bytes path. Returning null for TranscribedSegment IS the C1
        // dispatch invariant — there's no second classifier path for ambient text under EdgeTPU.
        val pcm = (input as? AudioInput.RawPcm) ?: return null
        return audioModel.get().predict(pcm.pcm)
    }

    override fun close() {
        runCatching { audioModel.get().close() }
    }
}
