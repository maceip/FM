package com.google.research.air.cosmo.gatekeeping

internal data class AudioGatekeepingHead(
    val index: Int,
    val modelName: String,
    val thresholdKey: String,
    val skillIds: List<String> = emptyList()
)

/**
 * Recovered Conformer contract shared by the locked EdgeTPU path and the public replacement
 * backend. The recovered model has one directedness head plus twelve intent heads; it is not a
 * flat thirteen-skill classifier.
 */
internal object AudioGatekeepingContract {
    const val SAMPLE_RATE_HZ: Int = 16_000
    const val MAX_AUDIO_LENGTH_SEC: Int = 60
    const val MAX_SAMPLES: Int = SAMPLE_RATE_HZ * MAX_AUDIO_LENGTH_SEC
    const val WINDOW_STRIDE_MS: Int = 20
    const val FRAME_STRIDE_SAMPLES: Int = SAMPLE_RATE_HZ * WINDOW_STRIDE_MS / 1_000
    const val MEL_BINS: Int = 128
    const val MAX_FRAMES: Int = MAX_SAMPLES / FRAME_STRIDE_SAMPLES
    const val NUM_HEADS: Int = 13
    const val IS_INTERESTING_INDEX: Int = 0
    const val PCM16_BYTES_PER_SAMPLE: Int = 2
    const val PCM_16BIT_SCALE: Float = 32_768.0f

    const val EDGE_TPU_MEL_BACKEND_INPUT_INDEX: Int = 0
    const val EDGE_TPU_MASK_BACKEND_INPUT_INDEX: Int = 1
    const val EDGE_TPU_INTENT_OUTPUT_INDEX: Int = 0
    const val MEL_FRONTEND_OUTPUT_INDEX: Int = 0
    const val AUDIO_FRONTEND_INPUT_INDEX: Int = 0

    const val INTERESTING_BRIDGE_SKILL_ID: String = "add_to_memory"

    val HEADS: List<AudioGatekeepingHead> =
        listOf(
            AudioGatekeepingHead(IS_INTERESTING_INDEX, "isInteresting", "is_interesting"),
            AudioGatekeepingHead(1, "hasAddToListIntent", "has_add_to_list_intent", listOf("add_to_list")),
            AudioGatekeepingHead(2, "hasTermSearchQuery", "has_term_search_query", listOf("define_term", "web_search")),
            AudioGatekeepingHead(3, "hasPhotoQuery", "has_photo_query", listOf("query_photos")),
            AudioGatekeepingHead(4, "hasCalendarEvent", "has_calendar_event", listOf("create_calendar_event")),
            AudioGatekeepingHead(5, "hasCreateDocIntent", "has_create_doc_intent", listOf("create_doc")),
            AudioGatekeepingHead(6, "hasPersonContext", "has_person_context", listOf("provide_person_context")),
            AudioGatekeepingHead(7, "hasEventContext", "has_event_context", listOf("provide_event_context")),
            AudioGatekeepingHead(8, "hasDeepResearchIntent", "has_deep_research_intent", listOf("deep_research")),
            AudioGatekeepingHead(9, "hasInsightIntent", "has_insight_intent", listOf("provide_insight")),
            AudioGatekeepingHead(10, "hasBrowsingAgentIntent", "has_browsing_agent_intent", listOf("browsing_agent")),
            AudioGatekeepingHead(11, "hasCallingAgentIntent", "has_calling_agent_intent", listOf("call_agent")),
            AudioGatekeepingHead(12, "hasStartTimerIntent", "has_start_timer_intent", listOf("add_timer"))
        )

    val THRESHOLD_KEYS: List<String> = HEADS.map { it.thresholdKey }

    val DEFAULT_THRESHOLDS: FloatArray
        get() =
            floatArrayOf(
                0.57f,
                0.65f,
                0.90f,
                0.90f,
                0.90f,
                0.90f,
                0.56f,
                0.74f,
                0.90f,
                0.90f,
                0.90f,
                0.90f,
                0.90f
            )

    fun frameCountForPcm16Bytes(pcmBytes: Int): Int {
        val samples = (pcmBytes / PCM16_BYTES_PER_SAMPLE).coerceIn(0, MAX_SAMPLES)
        return ((samples + FRAME_STRIDE_SAMPLES - 1) / FRAME_STRIDE_SAMPLES).coerceAtMost(MAX_FRAMES)
    }

    fun mapScoresToSkills(
        scores: FloatArray,
        thresholds: FloatArray = DEFAULT_THRESHOLDS,
        bridgeInterestingToMemory: Boolean = true
    ): Map<String, Float> {
        if (thresholds.size != NUM_HEADS) return emptyMap()
        val detected = linkedMapOf<String, Float>()
        HEADS.forEach { head ->
            val score = scores.getOrNull(head.index) ?: return@forEach
            if (score < thresholds[head.index]) return@forEach
            if (head.index == IS_INTERESTING_INDEX) {
                if (bridgeInterestingToMemory) detected[INTERESTING_BRIDGE_SKILL_ID] = score
            } else {
                head.skillIds.forEach { skillId -> detected[skillId] = score }
            }
        }
        return detected
    }

    fun scoreSignals(
        prefix: String,
        scores: FloatArray
    ): Map<String, Float> =
        buildMap {
            HEADS.forEach { head ->
                scores.getOrNull(head.index)?.let { score ->
                    put("${prefix}_head_${head.index}", score)
                    put("${prefix}_${head.thresholdKey}", score)
                }
            }
            scores.getOrNull(IS_INTERESTING_INDEX)?.let { put("${prefix}_is_interesting", it) }
        }
}
