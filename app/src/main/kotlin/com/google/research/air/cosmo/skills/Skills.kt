package com.google.research.air.cosmo.skills

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import com.google.research.air.cosmo.fulfillment.FulfillmentActionHandler
import com.google.research.air.cosmo.lib.llm.LLM
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SkillDescriptor(
    val id: String,
    val displayName: String,
    val category: SkillCategory,
    val triggers: List<Trigger>,
    val inputSchema: JsonSchema,
    val outputKind: OutputKind,
    val requiresConfirmation: Boolean,
    val requiredPermissions: Set<String> = emptySet(),
    val enabledByDefault: Boolean = true
)

enum class SkillCategory { PRODUCTIVITY, CAPTURE, KNOWLEDGE, UTILITY }

enum class OutputKind { ANSWER, DRAFT, SIDE_EFFECT }

data class JsonSchema(
    val required: Set<String> = emptySet()
)

sealed class Trigger {
    data class TextPattern(
        val regex: String
    ) : Trigger()

    data class Intent(
        val name: String
    ) : Trigger()

    data class EmbeddingExemplars(
        val phrases: List<String>
    ) : Trigger()
}

data class SkillInvocation(
    val skillId: String,
    val args: JsonObject,
    val sourceRequestId: String
)

sealed class SkillResult {
    data class Answer(
        val text: String
    ) : SkillResult()

    data class Draft(
        val title: String,
        val body: String,
        val mime: String
    ) : SkillResult()

    data class SideEffectPending(
        val confirmationToken: String,
        val summary: String
    ) : SkillResult()

    data class SideEffectApplied(
        val auditId: String,
        val summary: String
    ) : SkillResult()

    data class Failed(
        val reason: String
    ) : SkillResult()
}

interface SkillRegistry {
    fun list(): List<SkillDescriptor>

    fun listByCategory(c: SkillCategory): List<SkillDescriptor>

    fun get(id: String): SkillDescriptor?

    suspend fun setEnabled(
        id: String,
        enabled: Boolean
    )

    fun observeEnabled(id: String): Flow<Boolean>

    suspend fun invoke(invocation: SkillInvocation): SkillResult

    suspend fun confirm(token: String): SkillResult

    suspend fun cancel(token: String)
}

@Singleton
class SkillsRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val fulfillmentActionHandler: FulfillmentActionHandler
) : SkillRegistry {
    private val descriptors = SkillAssetCatalog(context).loadDescriptors()
    private val enabled = descriptors.associate { it.id to MutableStateFlow(it.enabledByDefault) }
    private val pending = mutableMapOf<String, FulfillmentAction>()

    override fun list(): List<SkillDescriptor> = descriptors

    override fun listByCategory(c: SkillCategory): List<SkillDescriptor> = descriptors.filter { it.category == c }

    override fun get(id: String): SkillDescriptor? = descriptors.firstOrNull { it.id == id }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean
    ) {
        this.enabled[id]?.value = enabled
    }

    override fun observeEnabled(id: String): Flow<Boolean> = enabled[id] ?: MutableStateFlow(false)

    override suspend fun invoke(invocation: SkillInvocation): SkillResult {
        val descriptor = get(invocation.skillId) ?: return SkillResult.Failed("unknown")
        if (enabled[descriptor.id]?.value != true) return SkillResult.Failed("disabled")
        descriptor.inputSchema.required
            .firstOrNull { invocation.args[it] == null }
            ?.let { return SkillResult.Failed("schema:$it") }
        descriptor.requiredPermissions
            .firstOrNull {
                ContextCompat.checkSelfPermission(
                    context,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }?.let {
                return SkillResult.Failed("permission:$it")
            }
        val action = invocation.toAction()
        if (descriptor.outputKind == OutputKind.SIDE_EFFECT) {
            val token =
                java.util.UUID
                    .randomUUID()
                    .toString()
            pending[token] = action
            return SkillResult.SideEffectPending(token, action.title)
        }
        return when (action) {
            is FulfillmentAction.CreateDoc -> SkillResult.Draft(action.title, action.body, "text/plain")
            is FulfillmentAction.DefineTerm -> SkillResult.Answer(action.term)
            else -> SkillResult.Answer(action.title)
        }
    }

    override suspend fun confirm(token: String): SkillResult {
        val action = pending.remove(token) ?: return SkillResult.Failed("token")
        fulfillmentActionHandler.execute(action)
        return SkillResult.SideEffectApplied(
            java.util.UUID
                .randomUUID()
                .toString(),
            action.title
        )
    }

    override suspend fun cancel(token: String) {
        pending.remove(token)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun SkillInvocation.toAction(): FulfillmentAction {
        fun arg(name: String): String = args[name]?.jsonPrimitive?.content.orEmpty()
        val id =
            java.util.UUID
                .randomUUID()
                .toString()
        return when (skillId) {
            "active_data_plan" -> FulfillmentAction.ActiveDataPlan(id, "Active data plan", arg("plan"))
            "add_task" -> FulfillmentAction.AddTask(id, arg("title").ifBlank { "Task" }, arg("notes"))
            "add_timer", "timer.set" -> FulfillmentAction.AddTimer(id, "Set timer", arg("duration_seconds").toIntOrNull() ?: 300)
            "add_to_list", "list.create" ->
                FulfillmentAction.AddToList(
                    id,
                    arg("title").ifBlank { "List" },
                    arg("items").split(",").map(String::trim).filter(String::isNotBlank)
                )
            "add_to_memory" -> FulfillmentAction.AddToMemory(id, arg("title").ifBlank { "Memory" }, arg("text"))
            "browsing_agent" -> FulfillmentAction.BrowsingAgent(id, arg("title").ifBlank { "Browsing agent" }, arg("prompt"))
            "call_agent" -> FulfillmentAction.CallAgent(id, arg("title").ifBlank { "Call" }, arg("phone_number").ifBlank { null })
            "create_calendar_event", "calendar.draft" ->
                FulfillmentAction.CreateCalendarEvent(
                    id,
                    arg("title").ifBlank { "Event" },
                    arg("start_millis").toLongOrNull() ?: 0L,
                    arg("end_millis").toLongOrNull() ?: 0L,
                    arg("location")
                )
            "create_doc", "document.draft" -> FulfillmentAction.CreateDoc(id, arg("title").ifBlank { "Document" }, arg("body"))
            "deep_research" -> FulfillmentAction.DeepResearch(id, "Deep research", arg("query"))
            "define_term", "jargon.lookup" -> FulfillmentAction.DefineTerm(id, arg("term"), arg("term"))
            "draft_email" -> FulfillmentAction.DraftEmail(id, arg("title").ifBlank { "Draft email" }, arg("to"), arg("subject"), arg("body"))
            "draft_sms" -> FulfillmentAction.DraftSms(id, arg("title").ifBlank { "Draft SMS" }, arg("to"), arg("body"))
            "invoke_agent_q" -> FulfillmentAction.InvokeAgentQ(id, "AgentQ", arg("prompt"))
            "navigation" -> FulfillmentAction.Navigation(id, "Navigate", arg("destination"))
            "play_music" -> FulfillmentAction.PlayMusic(id, "Play music", arg("query"))
            "provide_event_context" -> FulfillmentAction.ProvideEventContext(id, "Event context", arg("answer"))
            "provide_insight", "insight.generate" -> FulfillmentAction.ProvideInsight(id, "Insight", arg("query").ifBlank { arg("answer") })
            "provide_person_context" -> FulfillmentAction.ProvidePersonContext(id, "Person context", arg("answer"))
            "provide_recall", "recall.query" -> FulfillmentAction.ProvideRecall(id, "Recall", arg("query"))
            "query_photos" -> FulfillmentAction.QueryPhotos(id, "Query photos", arg("query"))
            "web_search", "web.search" -> FulfillmentAction.WebSearch(id, arg("query"), arg("query"))
            else -> FulfillmentAction.ProvideInsight(id, skillId, args.toString())
        }
    }
}

class SkillAssetCatalog(
    private val context: Context
) {
    fun loadDescriptors(): List<SkillDescriptor> {
        val schema = context.assets.open("filtered_skill_schema.textproto").bufferedReader().use { it.readText() }
        return ACTION_ENUM.findAll(schema)
            .map { it.groupValues[1].lowercase() }
            .map { actionType -> descriptorFor(actionType) }
            .toList()
    }

    private fun descriptorFor(actionType: String): SkillDescriptor {
        val spec = SKILL_SPECS.getValue(actionType)
        return SkillDescriptor(
            id = actionType,
            displayName = spec.displayName,
            category = spec.category,
            triggers = listOf(Trigger.TextPattern(spec.triggerRegex)),
            inputSchema = JsonSchema(spec.required),
            outputKind = spec.outputKind,
            requiresConfirmation = spec.requiresConfirmation,
            requiredPermissions = spec.permissions
        )
    }

    private data class SkillSpec(
        val displayName: String,
        val category: SkillCategory,
        val triggerRegex: String,
        val required: Set<String>,
        val outputKind: OutputKind,
        val requiresConfirmation: Boolean,
        val permissions: Set<String> = emptySet()
    )

    companion object {
        private val ACTION_ENUM = Regex("enum:\\s*\"ACTION_TYPE_([A-Z_]+)\"")
        private val SKILL_SPECS =
            mapOf(
                "active_data_plan" to SkillSpec("Open data plan", SkillCategory.UTILITY, "data|cellular|plan", setOf("plan"), OutputKind.SIDE_EFFECT, true),
                "add_task" to SkillSpec("Add task", SkillCategory.PRODUCTIVITY, "task|todo|follow up", setOf("title"), OutputKind.SIDE_EFFECT, true),
                "add_timer" to SkillSpec("Set timer", SkillCategory.UTILITY, "timer|countdown", setOf("duration_seconds"), OutputKind.SIDE_EFFECT, true),
                "add_to_list" to SkillSpec("Add to list", SkillCategory.PRODUCTIVITY, "list|shopping|pack", setOf("items"), OutputKind.SIDE_EFFECT, true),
                "add_to_memory" to SkillSpec("Add memory", SkillCategory.CAPTURE, "remember|save this|memory", setOf("text"), OutputKind.SIDE_EFFECT, true),
                "browsing_agent" to SkillSpec("Browsing agent", SkillCategory.UTILITY, "book|order|checkout|browse|form", setOf("prompt"), OutputKind.SIDE_EFFECT, true),
                "call_agent" to SkillSpec("Call agent", SkillCategory.UTILITY, "call|phone|reservation", emptySet(), OutputKind.SIDE_EFFECT, true),
                "create_calendar_event" to SkillSpec(
                    "Create calendar event",
                    SkillCategory.PRODUCTIVITY,
                    "calendar|meeting|schedule",
                    setOf("title", "start_millis", "end_millis"),
                    OutputKind.SIDE_EFFECT,
                    true,
                    setOf(Manifest.permission.WRITE_CALENDAR)
                ),
                "create_doc" to SkillSpec("Create document", SkillCategory.PRODUCTIVITY, "doc|document|draft", setOf("title", "body"), OutputKind.DRAFT, false),
                "deep_research" to SkillSpec("Deep research", SkillCategory.KNOWLEDGE, "research|compare|report", setOf("query"), OutputKind.SIDE_EFFECT, true),
                "define_term" to SkillSpec("Define term", SkillCategory.KNOWLEDGE, "define|term|meaning", setOf("term"), OutputKind.ANSWER, false),
                "draft_email" to SkillSpec("Draft email", SkillCategory.PRODUCTIVITY, "email|mail", setOf("to", "body"), OutputKind.DRAFT, false),
                "draft_sms" to SkillSpec("Draft SMS", SkillCategory.PRODUCTIVITY, "text|sms|message", setOf("to", "body"), OutputKind.DRAFT, false),
                "invoke_agent_q" to SkillSpec("Invoke AgentQ", SkillCategory.UTILITY, "agentq|agent q|assistant", setOf("prompt"), OutputKind.SIDE_EFFECT, true),
                "navigation" to SkillSpec("Navigate", SkillCategory.UTILITY, "directions|navigate|route|map", setOf("destination"), OutputKind.SIDE_EFFECT, true),
                "play_music" to SkillSpec("Play music", SkillCategory.UTILITY, "music|song|playlist", setOf("query"), OutputKind.SIDE_EFFECT, true),
                "provide_event_context" to SkillSpec("Event context", SkillCategory.KNOWLEDGE, "event|agenda|conference", setOf("answer"), OutputKind.ANSWER, false),
                "provide_insight" to SkillSpec("Provide insight", SkillCategory.KNOWLEDGE, "explain|insight|why", setOf("answer"), OutputKind.ANSWER, false),
                "provide_person_context" to SkillSpec("Person context", SkillCategory.KNOWLEDGE, "who is|person|contact", setOf("answer"), OutputKind.ANSWER, false),
                "provide_recall" to SkillSpec("Recall memory", SkillCategory.CAPTURE, "remember|recall", setOf("query"), OutputKind.ANSWER, false),
                "query_photos" to SkillSpec("Query photos", SkillCategory.CAPTURE, "photo|picture|image", setOf("query"), OutputKind.SIDE_EFFECT, true),
                "web_search" to SkillSpec("Web search", SkillCategory.KNOWLEDGE, "search|lookup|find", setOf("query"), OutputKind.SIDE_EFFECT, true)
            )
    }
}

interface SkillDefinitionGenerator {
    suspend fun generateDefinition(capabilityRequest: String): String
}

class AssetBackedSkillDefinitionGenerator
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val llm: LLM
) : SkillDefinitionGenerator {
    override suspend fun generateDefinition(capabilityRequest: String): String {
        val prompt = context.assets.open("skill_definition_prompt.jinja2").bufferedReader().use { it.readText() }
        val schema = context.assets.open("filtered_skill_schema.textproto").bufferedReader().use { it.readText() }
        return llm.generateText("$prompt\nCapability: $capabilityRequest\nSchema:\n$schema")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SkillsRepositoryModule {
    @Binds abstract fun bindSkillsRepository(impl: SkillsRepositoryImpl): SkillRegistry

    @Binds abstract fun bindSkillDefinitionGenerator(impl: AssetBackedSkillDefinitionGenerator): SkillDefinitionGenerator
}
