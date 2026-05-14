package com.google.research.air.cosmo.intent

import android.content.Context
import android.util.Log
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.IntentEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.ScreenInteractionEphemeralBuffer
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.interactioncapture.ScreenInteraction
import com.google.research.air.cosmo.lib.llm.LLM
import com.google.research.air.cosmo.lib.soda.TranscriptionData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * C7: second consumer of [GatekeeperOutputSource.results]. Cosmo's
 * `intentextraction/IntentExtractorImpl.smali` subscribes to the same gatekeeper output flow that
 * the action pipeline reads, gathers recent transcripts + screen interactions, sends them through
 * an LLM with `intent_extraction_prompt.jinja2`, parses the JSON response, and writes the
 * structured intent into a downstream ephemeral buffer.
 *
 * Genesis encodes the same dual-subscriber shape: starting [IntentExtractorImpl] launches a
 * coroutine that collects [GatekeeperOutputSource.results] alongside the action pipeline's
 * collector. They observe concurrently; neither blocks the other.
 *
 * Classification is delegated to [IntentClassifier]. Production binding is [LlmIntentClassifier].
 * Failure modes — LLM unavailable, malformed JSON, no recognizable intent — are absorbed at the
 * collect site so the second-subscriber contract is never broken.
 */
interface IntentExtractor : Closeable {
    fun start()
}

@Singleton
class IntentExtractorImpl
@Inject
constructor(
    private val gatekeeperOutputSource: GatekeeperOutputSource,
    @IntentEphemeralBuffer private val intentBuffer: InMemoryEphemeralBuffer<Any>,
    private val classifier: IntentClassifier,
    @ApplicationScope private val scope: CoroutineScope
) : IntentExtractor {
    private var job: Job? = null

    override fun start() {
        if (job != null) return
        job = scope.launch {
            gatekeeperOutputSource.results.collect { result ->
                runCatching { onGatekeeperResult(result) }
                    .onFailure { Log.w(TAG, "intent extraction failed: ${it.message}") }
            }
        }
    }

    private suspend fun onGatekeeperResult(result: GatekeepingResult) {
        val record = classifier.classify(result) ?: return
        intentBuffer.add(record)
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "IntentExtractor"
    }
}

/**
 * Pluggable intent classifier. Genesis binds [LlmIntentClassifier] in production; tests inject a
 * fake to keep the LLM out of unit-test wiring.
 */
interface IntentClassifier {
    suspend fun classify(result: GatekeepingResult): IntentRecord?
}

/**
 * Structured intent surfaced to the intent ephemeral buffer. Mirrors cosmo's recovered shape
 * (`final_answer = "App; goal"` decomposed into the `intent` summary + an `app` slot). Genesis
 * adds `input`, `modelId`, and an optional `reasoning` capture for debug surfaces.
 */
data class IntentRecord(
    val intent: String,
    val slots: Map<String, String>,
    val confidence: Float,
    val input: String,
    val modelId: String,
    val reasoning: String? = null,
)

/**
 * Cosmo-aligned LLM-backed intent classifier. Mirrors
 * `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/intentextraction/IntentExtractorImpl.smali`:
 *
 *  1. Loads `assets/intent_extraction_prompt.jinja2` once and caches it.
 *  2. On each gatekeeper trigger, builds a prompt with:
 *      - the current trigger transcript + ranked candidate skills,
 *      - up to [CONTEXT_TRANSCRIPT_LIMIT] recent ambient transcripts,
 *      - up to [CONTEXT_INTERACTION_LIMIT] recent screen interactions,
 *      - the cosmo instructions body.
 *  3. Sends to [LLM.generateText].
 *  4. Strips ` ```json` markdown fences (cosmo's `IntentExtractorImpl.smali:start$1$1$5$1` does
 *     this on the response string), parses with `org.json.JSONObject`, expects keys `reasoning`
 *     + `final_answer` per the prompt's contract.
 *  5. `final_answer` decodes as `"AppName; imperative goal"` → [IntentRecord]`(intent=goal,
 *     slots={"app": appName})`.
 */
@Singleton
class LlmIntentClassifier
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val llm: LLM,
    @AmbientAudioEphemeralBuffer
    private val ambientAudioBuffer: InMemoryEphemeralBuffer<Any>,
    @ScreenInteractionEphemeralBuffer
    private val screenInteractionBuffer: InMemoryEphemeralBuffer<Any>,
) : IntentClassifier {

    private val templateBody: String by lazy { loadTemplate() }

    override suspend fun classify(result: GatekeepingResult): IntentRecord? {
        if (result.input.isBlank()) return null
        if (!result.triggeredFulfillment && result.detectedSkills.isEmpty()) return null
        val prompt = buildPrompt(result)
        val raw = runCatching { llm.generateText(prompt) }
            .getOrElse {
                Log.w(TAG, "LLM inference failed: ${it.message}")
                return null
            }
        return parse(raw, result)
    }

    private fun loadTemplate(): String =
        runCatching {
            appContext.assets.open(PROMPT_ASSET_PATH).use { stream ->
                stream.bufferedReader().readText().trim()
            }
        }.getOrElse {
            Log.w(TAG, "Failed to load $PROMPT_ASSET_PATH; using inline fallback: ${it.message}")
            FALLBACK_INSTRUCTIONS
        }

    private fun buildPrompt(result: GatekeepingResult): String {
        val candidateSkills = result.detectedSkills
            .entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (name, score) -> "  - $name (${"%.2f".format(score)})" }
            .ifBlank { "  (none)" }
        val recentTranscripts = gatherRecentTranscripts()
        val recentInteractions = gatherRecentInteractions()
        return buildString {
            append("=== Current trigger ===\n")
            append("Transcript: ").append(result.input).append('\n')
            append("Source: ").append(result.detectedBy).append('\n')
            append("Candidate skills (from upstream gatekeeper, scored 0..1):\n")
            append(candidateSkills).append("\n\n")
            if (recentTranscripts.isNotEmpty()) {
                append("=== Recent transcripts (oldest first) ===\n")
                recentTranscripts.forEach { append("  - ").append(it).append('\n') }
                append('\n')
            }
            if (recentInteractions.isNotEmpty()) {
                append("=== Recent screen interactions (oldest first) ===\n")
                recentInteractions.forEach { append("  - ").append(it).append('\n') }
                append('\n')
            }
            append("=== Instructions ===\n")
            append(templateBody).append("\n\n")
            append("Response:")
        }
    }

    private fun gatherRecentTranscripts(): List<String> =
        ambientAudioBuffer.snapshotReversed()
            .asSequence()
            .mapNotNull { stamped ->
                val value = stamped.value
                when (value) {
                    is TranscriptionData -> value.transcription.takeIf { it.isNotBlank() }
                    else -> null
                }
            }
            .take(CONTEXT_TRANSCRIPT_LIMIT)
            .toList()
            .asReversed()

    private fun gatherRecentInteractions(): List<String> =
        screenInteractionBuffer.snapshotReversed()
            .asSequence()
            .mapNotNull { stamped ->
                val value = stamped.value
                when (value) {
                    is ScreenInteraction -> formatInteraction(value)
                    else -> null
                }
            }
            .take(CONTEXT_INTERACTION_LIMIT)
            .toList()
            .asReversed()

    private fun formatInteraction(interaction: ScreenInteraction): String {
        val text = interaction.text.take(MAX_INTERACTION_TEXT_LEN)
        return if (text.isBlank()) {
            "${interaction.type}@${interaction.packageName}"
        } else {
            "${interaction.type}@${interaction.packageName}: $text"
        }
    }

    private fun parse(raw: String, result: GatekeepingResult): IntentRecord? {
        val json = stripJsonFences(raw).trim()
        if (json.isEmpty()) return null
        val obj = try {
            JSONObject(json)
        } catch (_: JSONException) {
            Log.w(TAG, "LLM response was not valid JSON: ${raw.take(MAX_LOG_LEN)}")
            return null
        }
        val reasoning = obj.optString("reasoning", "").takeIf { it.isNotBlank() }
        val finalAnswer = obj.optString("final_answer", "").trim()
        if (finalAnswer.isEmpty() || finalAnswer.equals("unknown", ignoreCase = true)) return null
        val (app, goal) = splitFinalAnswer(finalAnswer) ?: return null
        val slots = buildMap {
            if (app.isNotBlank()) put("app", app)
            // Pass through any explicit slots if the LLM provided them; cosmo's prompt doesn't
            // formally specify slots but the response is JSON, so tolerate extras.
            obj.optJSONObject("slots")?.let { slotsObj ->
                val keys = slotsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = slotsObj.optString(key, "").takeIf { it.isNotBlank() } ?: continue
                    put(key, value)
                }
            }
        }
        val confidence = obj.optDouble("confidence", Double.NaN).toFloat()
        return IntentRecord(
            intent = goal,
            slots = slots,
            confidence = if (confidence.isNaN()) DEFAULT_CONFIDENCE else confidence.coerceIn(0f, 1f),
            input = result.input,
            modelId = "llm:${result.modelId}",
            reasoning = reasoning,
        )
    }

    /**
     * Cosmo's prompt contract: `final_answer = "AppName; imperative goal"`. Returns
     * `(app, goal)` if the semicolon is present, otherwise `("", finalAnswer)` so a malformed
     * response still yields an intent — the app slot is just missing.
     */
    private fun splitFinalAnswer(finalAnswer: String): Pair<String, String>? {
        val semicolon = finalAnswer.indexOf(';')
        if (semicolon < 0) {
            val trimmed = finalAnswer.trim()
            return if (trimmed.isEmpty()) null else "" to trimmed
        }
        val app = finalAnswer.substring(0, semicolon).trim()
        val goal = finalAnswer.substring(semicolon + 1).trim()
        if (goal.isEmpty()) return null
        return app to goal
    }

    private fun stripJsonFences(raw: String): String {
        val fenced = FENCE_REGEX.find(raw)?.groupValues?.getOrNull(1)
        return fenced ?: raw
    }

    private companion object {
        const val TAG = "LlmIntentClassifier"

        const val PROMPT_ASSET_PATH = "intent_extraction_prompt.jinja2"

        const val CONTEXT_TRANSCRIPT_LIMIT = 5
        const val CONTEXT_INTERACTION_LIMIT = 8
        const val MAX_INTERACTION_TEXT_LEN = 80
        const val MAX_LOG_LEN = 200
        const val DEFAULT_CONFIDENCE = 0.5f

        // Used only when the asset can't be loaded (test JVM without resources, packaging mishap).
        const val FALLBACK_INSTRUCTIONS =
            "Analyze the sequence of screens and interactions. Return a valid JSON object " +
                "with keys \"reasoning\" and \"final_answer\". The final answer starts with " +
                "the app name, followed by a semicolon, then an imperative summary of the " +
                "user's goal."

        // Matches ```json\n...\n``` or ```\n...\n``` fenced blocks. Cosmo strips ` ```json` per
        // IntentExtractorImpl.smali; we extend to bare fences since some LLMs omit the language tag.
        val FENCE_REGEX = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```", RegexOption.IGNORE_CASE)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class IntentExtractorModule {
    @Binds
    @Singleton
    abstract fun bindIntentExtractor(impl: IntentExtractorImpl): IntentExtractor

    @Binds
    abstract fun bindIntentClassifier(impl: LlmIntentClassifier): IntentClassifier
}
