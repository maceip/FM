package com.google.research.air.cosmo.lib.llm

import javax.inject.Inject
import javax.inject.Singleton

/**
 * SimulationLLM – deterministic fake LLM for development, testing, and demo on non-Pixel devices.
 *
 * Allows the full intelligence loop (gatekeeper → orchestrator → action dispatch) to be exercised
 * without AICore or the real remote inference service.
 *
 * Used when:
 * - Running on emulator
 * - No Pixel hardware available
 * - Running unit / instrumentation tests
 * - Demo mode is enabled in CosmoBuildConfig or settings
 */
@Singleton
class SimulationLLM @Inject constructor() : LlmBackendAdapter(
    backendId = LlmBackendId.SIMULATION,
    capabilities = LlmBackendCapabilities.TEXT_ONLY
) {

    override suspend fun generateSupported(request: LlmRequest, onToken: (String) -> Unit): String {
        val prompt = request.requireTextPrompt(backendId)
        val response = generateSimulatedResponse(prompt)
        onToken(response)
        return response
    }

    private fun generateSimulatedResponse(prompt: String): String {
        val query = prompt.userQueryOrFullPrompt()
        val lower = query.lowercase()

        return staticResponse(lower)
            ?: when {
                "timer" in lower || "remind me in" in lower -> {
                    val durationSeconds = timerDurationSeconds(lower)
                    val title = timerTitle(durationSeconds)
                    """
                    [
                      {"action_type": "add_timer", "id": "sim-timer", "title": "$title", "duration_seconds": $durationSeconds}
                    ]
                    """.trimIndent()
                }

                else -> """
                    [
                      {"action_type": "provide_insight", "id": "sim-999", "title": "Insight", "answer": "I simulated a helpful response based on the context you provided."}
                    ]
                """.trimIndent()
            }
    }

    private fun staticResponse(query: String): String? =
        STATIC_RESPONSES.firstOrNull { it.matches(query) }?.response

    private fun String.userQueryOrFullPrompt(): String =
        lineSequence()
            .lastOrNull { it.startsWith("User query:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: this

    private fun timerDurationSeconds(query: String): Int {
        val minutes = TIMER_MINUTES.find(query)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (minutes != null) return minutes * SECONDS_PER_MINUTE
        val seconds = TIMER_SECONDS.find(query)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return seconds ?: DEFAULT_TIMER_SECONDS
    }

    private fun timerTitle(durationSeconds: Int): String =
        if (durationSeconds % SECONDS_PER_MINUTE == 0) {
            "Set ${durationSeconds / SECONDS_PER_MINUTE} minute timer"
        } else {
            "Set $durationSeconds second timer"
        }

    private companion object {
        const val DEFAULT_TIMER_SECONDS = 300
        const val SECONDS_PER_MINUTE = 60
        val TIMER_MINUTES = Regex("""\b(\d+)\s*(?:minutes?|mins?)\b""")
        val TIMER_SECONDS = Regex("""\b(\d+)\s*(?:seconds?|secs?)\b""")
        val STATIC_RESPONSES =
            listOf(
                SimulatedResponse(Regex("""\b(?:summarize|summary)\b""")) {
                    """
                    [
                      {"action_type": "create_doc", "id": "sim-summary", "title": "Summary", "body": "Summary: the standup covered launch risks, timer flow status, and privacy validation."}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:document|doc|brief)\b""")) {
                    """
                    [
                      {"action_type": "create_doc", "id": "sim-doc", "title": "Draft project brief", "body": "Project brief draft with goals, risks, and next steps."}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:list|groceries|shopping)\b""")) {
                    """
                    [
                      {"action_type": "add_to_list", "id": "sim-list", "title": "Add grocery list items", "items": ["eggs", "milk"]}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:define|jargon|what does)\b""")) {
                    """
                    [
                      {"action_type": "define_term", "id": "sim-define", "title": "Define NPU", "term": "NPU"}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:recall|remember)\b""")) {
                    """
                    [
                      {"action_type": "provide_recall", "id": "sim-recall", "title": "Recall launch note", "answer": "You noted that timer flow must stay on the existing ActionDeck path."}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:insight|why)\b""")) {
                    """
                    [
                      {"action_type": "provide_insight", "id": "sim-insight", "title": "Insight", "answer": "The fastest path is to harden the existing action flow before adding new executors."}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:play music|song)\b""")) {
                    """
                    [
                      {"action_type": "play_music", "id": "sim-001", "title": "Play lofi beats", "query": "lofi hip hop radio"}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:calendar|meeting)\b""")) {
                    """
                    [
                      {"action_type": "create_calendar_event", "id": "sim-003", "title": "Team sync", "start_millis": 1746504000000, "end_millis": 1746507600000}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:search|who is)\b""")) {
                    """
                    [
                      {"action_type": "web_search", "id": "sim-004", "title": "Web search", "query": "current weather"}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:navigate|directions)\b""")) {
                    """
                    [
                      {"action_type": "navigation", "id": "sim-005", "title": "Navigate home", "destination": "home"}
                    ]
                    """.trimIndent()
                },
                SimulatedResponse(Regex("""\b(?:email|draft)\b""")) {
                    """
                    [
                      {"action_type": "draft_email", "id": "sim-006", "title": "Draft reply", "to": "team@company.com", "subject": "Update", "body": "Here is the latest status..."}
                    ]
                    """.trimIndent()
                }
            )
    }
}

private data class SimulatedResponse(
    val pattern: Regex,
    val responseFactory: () -> String
) {
    val response: String
        get() = responseFactory()

    fun matches(query: String): Boolean = pattern.containsMatchIn(query)
}
