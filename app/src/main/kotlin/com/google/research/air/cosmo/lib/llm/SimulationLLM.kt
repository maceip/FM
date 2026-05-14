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
        val lower = prompt.lowercase()

        return when {
            "play music" in lower || "song" in lower -> """
                [
                  {"action_type": "play_music", "id": "sim-001", "title": "Play lofi beats", "query": "lofi hip hop radio"}
                ]
            """.trimIndent()

            "timer" in lower || "remind me in" in lower -> """
                [
                  {"action_type": "add_timer", "id": "sim-002", "title": "Timer", "duration_seconds": 300}
                ]
            """.trimIndent()

            "calendar" in lower || "meeting" in lower -> """
                [
                  {"action_type": "create_calendar_event", "id": "sim-003", "title": "Team sync", "start_millis": 1746504000000, "end_millis": 1746507600000}
                ]
            """.trimIndent()

            "search" in lower || "who is" in lower -> """
                [
                  {"action_type": "web_search", "id": "sim-004", "title": "Web search", "query": "current weather"}
                ]
            """.trimIndent()

            "navigate" in lower || "directions" in lower -> """
                [
                  {"action_type": "navigation", "id": "sim-005", "title": "Navigate home", "destination": "home"}
                ]
            """.trimIndent()

            "email" in lower || "draft" in lower -> """
                [
                  {"action_type": "draft_email", "id": "sim-006", "title": "Draft reply", "to": "team@company.com", "subject": "Update", "body": "Here is the latest status..."}
                ]
            """.trimIndent()

            else -> """
                [
                  {"action_type": "provide_insight", "id": "sim-999", "title": "Insight", "answer": "I simulated a helpful response based on the context you provided."}
                ]
            """.trimIndent()
        }
    }
}
