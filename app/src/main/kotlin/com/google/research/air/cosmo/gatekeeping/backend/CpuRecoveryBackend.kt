package com.google.research.air.cosmo.gatekeeping.backend

import com.google.research.air.cosmo.gatekeeping.GatekeepingModel
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The CPU-recoverable baseline. Runs on any device the SODA native lib loads on (which is
 * everything genesis currently targets). Always available.
 *
 * Architecture (per architecture-delta C8 + PR0):
 *
 *  1. **Optional directedness gate** — callers can provide an upstream directedness decision
 *     through [AudioInput.TranscribedSegment.directednessTrigger]. If false (and the source is
 *     the ambient mic), [classify] returns null so the text classifier isn't invoked. Launch
 *     SODA is transcript-only, matching recovered cosmo; Conformer/Inherent own directedness.
 *  2. **Per-skill classification** — when the gate passes, [classify] runs the bound
 *     [GatekeepingModel] (today: `CombinedBertRegexGatekeepingModel`, which fuses BERT
 *     per-skill scores with the regex fast-path) on the transcript and returns its
 *     [GatekeepingResult].
 *
 * Buds / device-audio / watch-audio sources are treated as trusted (the user explicitly
 * connected the source) — the gate is bypassed for them. Mirrors cosmo's per-source behavior
 * per architecture-delta G7.
 */
@Singleton
class CpuRecoveryBackend
@Inject
constructor(
    private val textModel: GatekeepingModel
) : AudioGatekeepingBackend {

    override val backendId = BackendId.CpuRecovery

    override val isAvailable: Boolean = true

    override suspend fun classify(input: AudioInput): GatekeepingResult? {
        // C1 mutex: CPU recovery owns the text path. RawPcm under CPU recovery means raw bytes
        // arrived without a transcript — there's no Conformer to consume them, so we drop.
        val segment = (input as? AudioInput.TranscribedSegment) ?: return null
        if (segment.source == ContextSource.AMBIENT_AUDIO && !segment.directednessTrigger) {
            return null
        }
        return textModel.predict(segment.transcript)
    }

    override fun close() {
        textModel.close()
    }
}
