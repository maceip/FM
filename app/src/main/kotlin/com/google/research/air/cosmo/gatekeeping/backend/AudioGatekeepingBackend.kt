package com.google.research.air.cosmo.gatekeeping.backend

import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import java.io.Closeable

/**
 * Routing surface for the dispatch in `Gatekeeper`. Three concrete backends:
 *
 *  - `ConformerEdgeTpuBackend` — cosmo's recovered EdgeTPU Conformer (Pixel + Darwinn delegate);
 *    `isAvailable = false` until the Pixel target returns and the delegate-init probe lands.
 *  - `CpuRecoveryBackend` — always-available CPU baseline: SODA Magic Mic gates → BERT-on-text
 *    per-skill classification. The directedness decision arrives via `AudioInput.TranscribedSegment.directednessTrigger`;
 *    backend returns null when the gate suppressed the segment.
 *  - `InherentBackend` — from-scratch CPU model loaded from `inherent.tflite`; opt-in via raw
 *    audio realtime settings plus asset presence. Quality and device proof are still pending.
 *
 * Selection is performed once per session by [AudioGatekeepingBackendSelector]; the result is
 * cached for the lifetime of the `Gatekeeper`. Each backend produces a [GatekeepingResult] directly
 * (the same type the rest of the Gatekeeper pipeline consumes) so the routing layer doesn't have
 * to translate between contract shapes.
 */
interface AudioGatekeepingBackend : Closeable {
    val backendId: BackendId

    /** Checked once per session before activation; impls cache hardware/asset probe results. */
    val isAvailable: Boolean

    /**
     * Classify one ambient audio segment. Returns null when the backend's gate suppressed the
     * segment (e.g. Magic Mic decided it wasn't directed) — caller treats null as
     * "drop, do not emit." Non-null means the backend ran classification and the
     * [GatekeepingResult] is ready for downstream consumers.
     */
    suspend fun classify(input: AudioInput): GatekeepingResult?
}

enum class BackendId(val displayName: String) {
    ConformerEdgeTpu("conformer-edgetpu"),
    CpuRecovery("cpu-recovery"),
    Inherent("inherent"),
}

/**
 * Classification input. Two shapes; the right one is supplied by the captor + SODA pipeline:
 *
 *  - [RawPcm]: a 16 kHz mono PCM window. Used by `ConformerEdgeTpuBackend` and `InherentBackend`,
 *    both of which run a frontend mel transform internally (or via a shared `audio_frontend.tflite`).
 *
 *  - [TranscribedSegment]: the SODA transcript text plus the directedness trigger SODA emitted.
 *    Used by `CpuRecoveryBackend` — the directedness decision is already made (Magic Mic), so the
 *    backend only needs to score per-skill heads on the transcript.
 *
 * Either shape may be enriched as the project evolves; pin the union here and let backends pick.
 */
sealed interface AudioInput {
    val source: ContextSource

    data class RawPcm(
        override val source: ContextSource,
        val pcm: ByteArray,
        val sampleRateHz: Int = 16_000,
    ) : AudioInput

    data class TranscribedSegment(
        override val source: ContextSource,
        val transcript: String,
        val pcm: ByteArray? = null,
        val directednessTrigger: Boolean,
    ) : AudioInput
}
