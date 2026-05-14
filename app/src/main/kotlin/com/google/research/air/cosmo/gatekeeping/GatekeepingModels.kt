package com.google.research.air.cosmo.gatekeeping

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

enum class GatekeepingInputSource {
    UNKNOWN,
    AMBIENT_AUDIO,
    BUDS_AUDIO,
    DEVICE_AUDIO,
    WATCH_AUDIO,
    NOTIFICATION,
    SCREEN,
    SCREEN_INTERACTION,
    DEVICE_CAMERA,
    GLASSES_CAMERA,
    INTENT,
    LOCATION,
    USER_QUERY;

    companion object {
        fun fromInput(input: String): GatekeepingInputSource =
            when {
                input.startsWith("Ambient audio: ") -> AMBIENT_AUDIO
                input.startsWith("Ambient raw audio: ") -> AMBIENT_AUDIO
                input.startsWith("Buds audio: ") -> BUDS_AUDIO
                input.startsWith("Device audio: ") -> DEVICE_AUDIO
                input.startsWith("Watch audio: ") -> WATCH_AUDIO
                input.startsWith("Notification: ") -> NOTIFICATION
                input.startsWith("Screen: ") -> SCREEN
                input.startsWith("Screen interaction: ") -> SCREEN_INTERACTION
                input.startsWith("Device camera: ") -> DEVICE_CAMERA
                input.startsWith("Glasses camera: ") -> GLASSES_CAMERA
                input.startsWith("Intent: ") -> INTENT
                input.startsWith("Location: ") -> LOCATION
                input.startsWith("User query: ") -> USER_QUERY
                else -> UNKNOWN
            }
    }
}

enum class GatekeepingDetector {
    REGEX,
    BERT,
    AUDIO_GATEKEEPER,
    FUSED,
    NONE;

    companion object {
        fun fromDetectedBy(detectedBy: String): GatekeepingDetector =
            when (detectedBy.lowercase()) {
                "regex" -> REGEX
                "bert" -> BERT
                "audio_gatekeeper" -> AUDIO_GATEKEEPER
                "both" -> FUSED
                else -> NONE
            }
    }
}

data class GatekeepingSkillScore(
    val skillId: String,
    val confidence: Float,
    val detector: GatekeepingDetector
)

data class GatekeepingResult(
    val detectedSkills: Map<String, Float>,
    val modelId: String,
    val detectedBy: String,
    val triggeredFulfillment: Boolean,
    val input: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val inputSource: GatekeepingInputSource = GatekeepingInputSource.UNKNOWN,
    val detector: GatekeepingDetector = GatekeepingDetector.fromDetectedBy(detectedBy),
    val skillScores: List<GatekeepingSkillScore> =
        detectedSkills.map { (skillId, confidence) ->
            GatekeepingSkillScore(skillId, confidence, detector)
        },
    val screenSignals: Map<String, Float> = emptyMap()
)

interface GatekeepingModel : AutoCloseable {
    suspend fun predict(input: String): GatekeepingResult {
        throw UnsupportedOperationException("Text prediction is not supported by this gatekeeping model")
    }

    suspend fun predict(pcm: ByteArray): GatekeepingResult {
        throw UnsupportedOperationException("Audio prediction is not supported by this gatekeeping model")
    }

    override fun close() = Unit
}

/**
 * Compound intent rule with positive / anti / cleanup regexes, mirroring the recovered cosmo
 * `RegexGatekeepingModelV0.IntentRule` shape. A rule fires when the positive regex matches and
 * the anti regex does not.
 */
data class IntentRule(
    val skillId: String,
    val positive: Regex,
    val anti: Regex? = null,
    val cleanup: Regex? = null
) {
    fun matches(input: String): Boolean = positive.containsMatchIn(input) && anti?.containsMatchIn(input) != true
}

class RegexGatekeepingModelV0
@Inject
constructor() : GatekeepingModel {
    override suspend fun predict(input: String): GatekeepingResult {
        val scores =
            INTENT_RULES
                .filter { rule -> rule.matches(input) }
                .associate { rule -> rule.skillId to 1.0f }
        return GatekeepingResult(
            scores,
            "regex",
            "regex",
            scores.isNotEmpty(),
            input,
            inputSource = GatekeepingInputSource.fromInput(input),
            detector = GatekeepingDetector.REGEX
        )
    }

    companion object {
        private fun re(pattern: String): Regex = Regex(pattern, RegexOption.IGNORE_CASE)

        internal val INTENT_RULES: List<IntentRule> =
            listOf(
                IntentRule(
                    skillId = "add_to_memory",
                    positive = re(
                        "feedback on|opinion is|my take|the takeaway|the main point|planning to|trip to|" +
                            "honeymoon|vacation|my goal is|the decision is|loves|hates|is my favorite|is allergic to|" +
                            "gift idea for|he mentioned|she said|recommended|suggested that|" +
                            "worth (?:remembering|noting|revisiting)|memorable|secret (?:is|to)|pivotal|" +
                            "foundational|key insight|(?:should|need to|must) (?:remember|note|keep in mind)|" +
                            "don'?t forget that|(?:important|crucial|key|essential|fantastic|amazing|legendary|" +
                            "remarkable|fascinating) (?:fact|point|detail|piece|step|news)|landmark|tradition|" +
                            "is known for|historically|mesmerizing|masterful|standout|essential listen|" +
                            "must-read|unbelievable|HAVE to (?:try|see|check)|really something|" +
                            "I (?:never|didn'?t) know that"
                    )
                ),
                IntentRule(
                    skillId = "add_to_list",
                    positive = re(
                        "buy\\s|need to get|add\\s.*\\sto\\s(?:list|shopping)|I should get|remember to buy|" +
                            "start a list|list|recommendations|" +
                            "(?:buy|get|need|pick up|add) .*\\s(?:list|shopping)|remember to (?:buy|get)"
                    ),
                    anti = re("shopping list|grocery")
                ),
                IntentRule(
                    skillId = "define_term",
                    positive = re(
                        "what is\\s.*\\?|define\\s.*|what'?s the meaning of|what exactly are|let'?s first check|" +
                            "what does that|what does that even mean|that even mean|mean"
                    )
                ),
                IntentRule(
                    skillId = "query_photos",
                    positive = re(
                        "(?:find|search|look for|show|view|get|review|watch|tap to|recorded|send|see) " +
                            ".*\\s(?:photos?|pics?|pictures?|images?|screenshots?|albums?|footage|meme|logo|feed)|" +
                            "(?:photos?|pics?|pictures?|images?|screenshots?|albums?|footage|meme|logo) " +
                            "(?:of|from|with|in)|panoramic shot|shared (?:an?|new)? album|shared (?:an?|new)? photo"
                    ),
                    anti = re("(?:photo|image|snapshot|picture) (?:contest|competition|prize|award)")
                ),
                IntentRule(
                    skillId = "create_calendar_event",
                    positive = re(
                        "calendar invite|schedule a meeting|mark your calendar|add to (?:my )?calendar|" +
                            "reservation for|booking|reschedule|(?:flight|train|bus) [A-Z]{1,}\\d+|" +
                            "due (?:today|tomorrow|by|on)\\b|deadline(?: is)? (?:today|tomorrow|on)|" +
                            "time:\\s?\\d{1,2}(?::\\d{2})?|loc(?:ation)?:\\s?\\w+|rsvp to|gate [a-z]\\d+|" +
                            "invite from|available to meet|free to meet|" +
                            "(?:meet|sync|call|standup|1:1|dinner|party|concert|appointment|show|celebration|" +
                            "flight|train|trip|itinerary) (?:at|on|tomorrow|next|this|@|for)\\s?\\d?|" +
                            "(?:next|this|last|every) (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday) " +
                            "(?:morning|afternoon|evening|night|at|@)?|the \\d{1,2}(?:st|nd|rd|th)\\b|" +
                            "the (?:twenty|thirty) \\w+"
                    ),
                    anti = re(
                        "save the date|itinerary|confirmation|RSVP by|" +
                            "depart(?:ing|s)? at \\d|arrive(?:s|d)? at \\d"
                    )
                ),
                IntentRule(
                    skillId = "create_doc",
                    positive = re(
                        "create a doc|write a one pager|new document|draft a doc|write a letter|paper trail|" +
                            "write a doc|put in a doc|google doc|email|e-mail|draft an|draft a|" +
                            "(?:create|write|draft|new|put in|formalize|summarize|document|share|send) " +
                            "(?:an?|the)?\\s?(?:doc|document|pager|file|email|letter|summary|report|white paper|" +
                            "spec|brief|post-mortem|lessons learned|proposal|cheat sheet|deck|briefing)|" +
                            "(?:one|two)-pager|paper trail|google doc|design spec|" +
                            "write (?:it|this|that) (?:up|down)|put (?:it|this|that) (?:on|in) a\\b"
                    )
                ),
                IntentRule(
                    skillId = "provide_person_context",
                    positive = re(
                        "(?!(?:app|reminder|notification|it|that|there|where|here|how|which|today|yesterday|" +
                            "tomorrow|model|system|researcher|theorist|analyst|study|data|approach|firm|company|" +
                            "theory|algorithm|concept)'s)" +
                            "\\b(?:sister|mom|dad|doctor|vet|boss|client|sitter|husband|wife|friend|manager|" +
                            "colleague|partner|teacher|prof)'s\\s(?:favorite|birthday|allergic|likes|hates|" +
                            "feedback|input|comment|roadmap|wish\\s?list|checkup|progress|insight)|" +
                            "(?:he|she|they) (?:said|mentioned|told|thinks|likes|wants|hates|needs|is)|" +
                            "(?:his|her|their|my|our) (?:favorite|birthday|allergic|likes|hates|feedback|input|" +
                            "comment|roadmap|wish\\s?list)|" +
                            "(?:email|text|ping|call|message|contact|with|reach out to|appointment with) " +
                            "(?:him|her|them|the client|the team|mr\\.|mrs\\.|ms\\.|miss|prof\\.|dr\\.)|" +
                            "remind me that"
                    )
                ),
                IntentRule(
                    skillId = "provide_event_context",
                    positive = re(
                        "the meeting about|invitation for|that event on|" +
                            "(?:project \\w+|q[1-4]) (?:meeting|sync|call|review|session|launch|roadmap|agenda|" +
                            "status|kickoff)|" +
                            "(?:upcoming|next|that|this) (?:meeting|sync|trip|party|call|event|ceremony|reunion|" +
                            "standup|1:1)|" +
                            "(?:meeting|sync|session|call|follow-up|summit|trip|status report|presentation review) " +
                            "(?:about|for|with|at|to|sync with|call with)"
                    )
                ),
                IntentRule(
                    skillId = "deep_research",
                    positive = re(
                        "(?:research|study|analyze|breakdown|examine|look into|data on|mechanics of) " +
                            "(?:the|this|data|numbers|risk|impact|market|trends)|" +
                            "what actually works|is it effective|need a breakdown|best practices for|" +
                            "optimal (?:architecture|network|grid|strategy)|" +
                            "long-term (?:effect|impact|implication|viability)|proven framework|" +
                            "someone must have (?:studied|looked into|researched)|deep dive|" +
                            "underlying (?:issue|reason|cause)"
                    )
                ),
                IntentRule(
                    skillId = "provide_insight",
                    positive = re(
                        "pros and cons|trade-off|middle ground|alternatives|worth the risk|worth the investment|" +
                            "is there a pattern|optimal strategy|how (?:do|to) (?:we|to) approach|" +
                            "questions should I ask|what actually works|how (?:do|to) we decide|" +
                            "weighing (?:the|my|our) options|smart move|value vs\\.? volume|" +
                            "long-term (?:satisfaction|trajectory|implications)|unique selling proposition"
                    )
                ),
                IntentRule(
                    skillId = "browsing_agent",
                    positive = re(
                        "return them|process a return|make a spreadsheet|fill a spreadsheet|pay this bill|" +
                            "finish that spreadsheet|book\\s.*\\sonline|" +
                            "pay (?:the|my|this)?\\s?(?:water|electric|gas|utility|phone|internet|cable|power|" +
                            "credit card)?\\s?bill|" +
                            "account #\\d+|amount due\\b|bill for \\$|view your full bill|" +
                            "return (?:the|my|this|an?|these|it|them|item|order|package|label|product|jeans|" +
                            "sweater|shoes|books?)|" +
                            "send (?:it|them) back|shipping label|RMA|tracking (?:number|info|#)|" +
                            "(?:initiate|process|start|finish|go online to) (?:a|the)?\\s?return|" +
                            "checkout\\b.*\\bonline|booking portal|support (?:page|portal|site)|" +
                            "order (?:history|status|details|confirmation|new)|" +
                            "(?:update|finalize|send|fill|open|budget|edit) (?:a|the|my)?\\s?spreadsheet"
                    )
                ),
                IntentRule(
                    skillId = "call_agent",
                    positive = re(
                        "(?:call|phone|reach out to|contact) (?:the|them|an?|my|your|support|service|doctor|" +
                            "dentist|office|clinic|insurance|bank|agent|company|repair|school)|" +
                            "(?:book|schedule|make|arrange) (?:an? )?(?:appointment|inspection|visit|reservation|" +
                            "consultation|technician)|" +
                            "under warranty|insurance claim|dentist appointment|get a quote"
                    )
                ),
                IntentRule(
                    skillId = "add_timer",
                    positive = re("timer for|set a timer|start a timer|remind me in|countdown|let me know in"),
                    anti = re(
                        "(?:leave|cook|bake|rest|boil|simmer|broil|roast|fry|steep|soak|grill|chill|set) " +
                            "(?:in|for|until)"
                    )
                ),
                IntentRule(
                    skillId = "provide_recall",
                    positive = re(
                        "(?:what|where) (?:was|were|is|did) (?:that|those|the|it)(?: \\w+){0,2} " +
                            "(?:code|directions?|number|password|pin|address|name)(?: again)?\\b|" +
                            "(?:what|where) (?:was|were|is|did) (?:that|those|it|I|we) again\\b|" +
                            "(?:do you know if )?did (?:I|you|we) (?:already )?\\w+|" +
                            "(?:not (?:going to |gonna )?|don'?t |do not )remember(?: (?:that|what|all of that))?\\b|" +
                            "what did (?:they|he|she|you) (?:just )?say\\b|" +
                            "can you (?:repeat|remind me)\\b|say that again\\b"
                    )
                ),
                IntentRule(
                    skillId = "navigation",
                    positive = re(
                        "\\b(?:navigate to|directions to|how do i get to|take me to|show me the way to|" +
                            "find directions to) .+|" +
                            "\\b(?:where is|find) .+\\b(?:alley|avenue|boulevard|cafe|court|drive|highway|lane|" +
                            "park|piazza|place|plaza|road|square|street|terrace|trail|way|restaurant|bar|hotel|" +
                            "airport|station|address|location)\\b"
                    )
                ),
                IntentRule(
                    skillId = "draft_email",
                    positive = re("\\b(?:send|draft|write)(?: an?| the)? email\\b|\\b(?:email to)")
                ),
                IntentRule(
                    skillId = "add_task",
                    positive = re(
                        "\\b(?:add task|add a task|new task)\\s|" +
                            "\\b(?:remind me to|i need to|i have to|remember to|don'?t forget to|make sure to)\\s"
                    )
                ),
                IntentRule(
                    skillId = "draft_sms",
                    positive = re(
                        "(?:(?:text|SMS)\\s+(?:(?:an?|the)\\s+)?" +
                            "(?:contact|person|mom|dad|doctor|vet|boss|client|sitter|husband|wife|friend|manager|" +
                            "colleague|partner|teacher|prof)\\b)|" +
                            "(?:send\\s+(?:(?:an?|the)\\s+)?" +
                            "(?:contact|person|mom|dad|doctor|vet|boss|client|sitter|husband|wife|friend|manager|" +
                            "colleague|partner|teacher|prof)\\s+(?:(?:an?|the)\\s+)?(?:message|text|SMS)\\b)|" +
                            "(?:(?:send|text|SMS)\\s+(?:(?:an?|the)\\s+)?(?:message|text|SMS)\\s+(?:to|from)\\s+" +
                            "(?:(?:an?|the)\\s+)?" +
                            "(?:contact|person|mom|dad|doctor|vet|boss|client|sitter|husband|wife|friend|manager|" +
                            "colleague|partner|teacher|prof)\\b)"
                    )
                ),
                IntentRule(
                    skillId = "play_music",
                    positive = re(
                        "(?:play|listen to|put on|stream|queue up) (?:(?:some|the|a|an) )?" +
                            "(?:music|songs?|tunes?|tracks?|albums?|playlists?|artists?|bands?|beats?|soundtrack|" +
                            "jazz|rock|pop|classical|hip-?hop|rap|metal|country|blues|reggae|folk|k-pop)|" +
                            "(?:(?:favorite|best) (?:song|album|artist|band))|" +
                            "needs? (?:some )?(?:music|tunes?|beats?)"
                    )
                )
            )
    }
}

/**
 * Bundled-asset identifiers for the gatekeeper. Filenames are fixed at build time and verified
 * against `model-manifest.json` via `scripts/download-models.sh --verify-only` in CI; the
 * runtime no longer parses the manifest. Per architecture-delta C4 row 5: the previous
 * `GatekeepingModelLoader` + `ManifestGatekeepingModelLoader` + `GatekeepingModelLoadResult` +
 * `GatekeepingModelAsset` indirection was deleted in favor of direct asset reads at the
 * construction site.
 */
object GatekeeperModelIds {
    const val BERT_VOCAB = "gatekeeper_bert_vocab"
    const val BERT_CPU = "gatekeeper_bert_v0_cpu"
    const val BERT_EDGETPU_P24 = "gatekeeper_bert_v0_p24"
    const val BERT_EDGETPU_P25 = "gatekeeper_bert_v0_p25"
    const val BERT_NPU_MT6989 = "gatekeeper_bert_v0_mt6989"
    const val AUDIO_FRONTEND = "gatekeeper_audio_frontend"
    const val AUDIO_GATEKEEPER = "gatekeeper_audio_gatekeeper"
    const val INHERENT_V0 = "gatekeeper_inherent_v0"

    private val FILENAMES =
        mapOf(
            BERT_VOCAB to "bert_vocab.txt",
            BERT_CPU to "bert_v0_cpu.tflite",
            BERT_EDGETPU_P24 to "bert_v0_p24.tflite",
            BERT_EDGETPU_P25 to "bert_v0_p25.tflite",
            BERT_NPU_MT6989 to "bert_v0_mt6989.tflite",
            AUDIO_FRONTEND to "audio_frontend.tflite",
            AUDIO_GATEKEEPER to "audio_gatekeeper_edgetpu.tflite",
            INHERENT_V0 to "inherent.tflite"
        )

    fun filenameFor(id: String): String? = FILENAMES[id]

    /**
     * Production asset-bytes loader. Builds the `(id) -> ByteArray?` seam that
     * [BertClassifierGatekeepingModel] and [ConformerEncoderImpl] consume. Null means the asset
     * isn't packaged at the expected path; the consumer translates that into its own
     * unavailability reason.
     */
    fun byteLoaderFor(context: Context): (String) -> ByteArray? = { id ->
        val filename = filenameFor(id)
        if (filename == null) {
            null
        } else {
            runCatching { context.assets.open("models/$filename").use { it.readBytes() } }.getOrNull()
        }
    }
}
