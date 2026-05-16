package com.google.research.air.cosmo.logging

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface FirebaseAnalyticsWrapper {
    fun logEvent(
        name: String,
        params: Bundle
    )
}

enum class CosmoLogTag(
    val tag: String
) {
    APP("CosmoApp"),
    ACTIVITY("CosmoActivity"),
    COMPOSE("CosmoCompose"),
    NAVIGATION_UI("CosmoNavigationUi"),
    ACTIONS("CosmoActions"),
    ACTION_REPOSITORY("CosmoActionRepo"),
    ACTION_HANDLER("CosmoActionHandler"),
    FULFILLMENT("CosmoFulfillment"),
    FULFILLMENT_RUNNER("CosmoFulfillmentRunner"),
    FULFILLMENT_PROMPT("CosmoFulfillmentPrompt"),
    SKILLS("CosmoSkills"),
    SKILL_REGISTRY("CosmoSkillRegistry"),
    LLM("CosmoLlm"),
    LLM_ROUTE("CosmoLlmRoute"),
    LLM_REMOTE("CosmoLlmRemote"),
    LLM_AICORE("CosmoLlmAiCore"),
    LLM_STATS("CosmoLlmStats"),
    GATEKEEPER("CosmoGatekeeper"),
    GATEKEEPER_BERT("CosmoGatekeeperBert"),
    GATEKEEPER_REGEX("CosmoGatekeeperRegex"),
    GATEKEEPER_OUTPUT("CosmoGatekeeperOutput"),
    MODEL_LOADER("CosmoModelLoader"),
    LITERT("CosmoLiteRt"),
    AUDIO("CosmoAudio"),
    AMBIENT_AUDIO("CosmoAmbientAudio"),
    BUDS_AUDIO("CosmoBudsAudio"),
    DEVICE_AUDIO("CosmoDeviceAudio"),
    WATCH_AUDIO("CosmoWatchAudio"),
    SODA("CosmoSoda"),
    RAW_AUDIO_GATEKEEPER("CosmoRawAudioGatekeeper"),
    AUDIO_FRONTEND("CosmoAudioFrontend"),
    SCREEN_CAPTURE("CosmoScreenCapture"),
    SCREEN_INTERACTION("CosmoScreenInteraction"),
    CONTENT_CAPTURE("CosmoContentCapture"),
    NOTIFICATION_CAPTURE("CosmoNotificationCapture"),
    ACCESSIBILITY("CosmoAccessibility"),
    LOCATION("CosmoLocation"),
    CAMERA("CosmoCamera"),
    DEVICE_CAMERA("CosmoDeviceCamera"),
    GLASSES_CAMERA("CosmoGlassesCamera"),
    GLASS("CosmoGlass"),
    INTENT_CAPTURE("CosmoIntentCapture"),
    EPHEMERAL_BUFFER("CosmoEphemeralBuffer"),
    BUFFER_EVICTION("CosmoBufferEviction"),
    CONTEXT_STORE("CosmoContextStore"),
    MEMORY("CosmoMemory"),
    PERMISSION("CosmoPermission"),
    SETTINGS("CosmoSettings"),
    STATE("CosmoState"),
    TELEMETRY("CosmoTelemetry"),
    FIREBASE("CosmoFirebase"),
    FLOGGER("CosmoFlogger"),
    TASK_MONITOR("CosmoTaskMonitor"),
    MARINER("CosmoMariner"),
    AGENT_Q("CosmoAgentQ"),
    BROWSING_AGENT("CosmoBrowsingAgent"),
    CALENDAR("CosmoCalendar"),
    CONTACTS("CosmoContacts"),
    PHONE("CosmoPhone"),
    MUSIC("CosmoMusic"),
    PHOTOS("CosmoPhotos"),
    DOCUMENTS("CosmoDocuments"),
    TIMER("CosmoTimer"),
    LISTS("CosmoLists"),
    RECALL("CosmoRecall"),
    RESEARCH("CosmoResearch"),
    INSIGHT("CosmoInsight"),
    WEB_SEARCH("CosmoWebSearch"),
    SECURITY("CosmoSecurity"),
    DATABASE("CosmoDatabase"),
    DATASTORE("CosmoDataStore"),
    NETWORK("CosmoNetwork"),
    BATTERY("CosmoBattery"),
    WORK_MANAGER("CosmoWorkManager"),
    PERFORMANCE("CosmoPerformance"),
    STRICT_MODE("CosmoStrictMode"),
    STARTUP("CosmoStartup"),
    TEST_DIAGNOSTICS("CosmoTestDiagnostics")
}

enum class FloggerSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

data class FirebaseEventDefinition(
    val name: String,
    val requiredParams: Set<String>,
    val optionalParams: Set<String> = emptySet()
)

enum class TelemetryPrivacyClass {
    OPERATIONAL_METADATA,
    ACTION_METADATA,
    MODEL_DIAGNOSTIC,
    DEVICE_STATE
}

data class TelemetryEventProvenance(
    val eventName: String,
    val owner: CosmoLogTag,
    val privacyClass: TelemetryPrivacyClass,
    val sourcePath: String,
    val emittedFrom: String,
    val containsUserContent: Boolean = false
)

data class FloggerEventDefinition(
    val id: Int,
    val name: String,
    val tag: CosmoLogTag,
    val severity: FloggerSeverity,
    val firebaseEvent: FirebaseEventDefinition
)

interface ObservabilitySchema {
    val logTags: List<CosmoLogTag>
    val floggerEvents: List<FloggerEventDefinition>
    /** Distinct Firebase event names emitted via the Flogger lifecycle catalog (`cosmo_*` pattern). */
    val firebaseEvents: List<FirebaseEventDefinition>
    /**
     * Allowlisted Firebase events emitted only through [CosmosTelemetry] / [TelemetryEvent].
     * Privacy posture: events not listed here are never sent to Firebase (fail closed).
     */
    val telemetryFirebaseEvents: List<FirebaseEventDefinition>
    val telemetryProvenance: List<TelemetryEventProvenance>
}

object GenesisObservabilitySchema : ObservabilitySchema {
    private val lifecycleSignals =
        listOf(
            "start",
            "success",
            "failure",
            "state",
            "latency",
            "drop",
            "retry",
            "permission",
            "asset",
            "enqueue",
            "dequeue",
            "emit",
            "debug"
        )
    private val globalSignals =
        listOf(
            "cold_start",
            "warm_start",
            "background",
            "foreground",
            "settings_changed",
            "permission_changed",
            "model_unavailable",
            "buffer_trimmed",
            "privacy_downgrade",
            "diagnostic_snapshot"
        )

    override val logTags: List<CosmoLogTag> = CosmoLogTag.entries

    override val floggerEvents: List<FloggerEventDefinition> =
        buildList {
            var id = 1
            CosmoLogTag.entries.take(FLOGGER_DOMAIN_COUNT).forEach { tag ->
                lifecycleSignals.forEach { signal ->
                    add(tag.toEvent(id++, signal))
                }
            }
            globalSignals.forEach { signal ->
                add(CosmoLogTag.APP.toEvent(id++, signal))
            }
        }.also { events ->
            check(events.size == FLOGGER_EVENT_COUNT) {
                "Genesis observability schema must expose $FLOGGER_EVENT_COUNT Flogger events."
            }
        }

    override val firebaseEvents: List<FirebaseEventDefinition> =
        floggerEvents
            .map { it.firebaseEvent }
            .distinctBy { it.name }

    override val telemetryFirebaseEvents: List<FirebaseEventDefinition> = TELEMETRY_FIREBASE_EVENT_DEFINITIONS

    override val telemetryProvenance: List<TelemetryEventProvenance> = TELEMETRY_EVENT_PROVENANCE

    private fun CosmoLogTag.toEvent(
        id: Int,
        signal: String
    ): FloggerEventDefinition {
        val eventName = "cosmo_${name.lowercase()}_$signal".take(MAX_FIREBASE_EVENT_NAME_LENGTH)
        return FloggerEventDefinition(
            id = id,
            name = "$tag.$signal",
            tag = this,
            severity = signal.toSeverity(),
            firebaseEvent =
                FirebaseEventDefinition(
                    name = eventName,
                    requiredParams = setOf("surface", "signal", "result"),
                    optionalParams = setOf("latency_ms", "count", "reason", "source", "model_id", "action_type")
                )
        )
    }

    private fun String.toSeverity(): FloggerSeverity =
        when (this) {
            "failure", "drop" -> FloggerSeverity.WARNING
            "debug" -> FloggerSeverity.DEBUG
            else -> FloggerSeverity.INFO
        }

    private const val FLOGGER_DOMAIN_COUNT = 23
    private const val FLOGGER_EVENT_COUNT = 309
    private const val MAX_FIREBASE_EVENT_NAME_LENGTH = 40
}

/**
 * Declared Firebase contracts for [TelemetryEvent]. Each entry is sourced from a sealed
 * subclass's `companion object DEFINITION`, so adding a new `TelemetryEvent` subclass
 * without registering it here surfaces in `LoggingSchemaTest` as a sealed-vs-registry
 * count mismatch.
 *
 * Naming/param conventions: snake_case ≤ 40 chars, area prefix; coarse params only —
 * no raw user content (text, audio, transcripts, screen content, clipboards).
 */
private val TELEMETRY_FIREBASE_EVENT_DEFINITIONS: List<FirebaseEventDefinition> =
    listOf(
        TelemetryEvent.FulfillmentRun.DEFINITION,
        TelemetryEvent.ActionExecution.DEFINITION,
        TelemetryEvent.TaskMonitorMessage.DEFINITION,
        TelemetryEvent.TaskMonitorSurface.DEFINITION,
        TelemetryEvent.ActionParseRejected.DEFINITION,
        TelemetryEvent.GatekeeperRun.DEFINITION,
        TelemetryEvent.GatekeeperDirectednessGate.DEFINITION,
        TelemetryEvent.CalendarWritableFallback.DEFINITION,
        TelemetryEvent.GlassSession.DEFINITION
    ).also { defs ->
        check(defs.distinctBy { it.name }.size == defs.size) {
            "telemetry Firebase definitions must use unique event names"
        }
        defs.forEach { d ->
            check(d.name.length <= 40) {
                "telemetry Firebase event name too long: ${d.name}"
            }
        }
    }

private val TELEMETRY_EVENT_PROVENANCE: List<TelemetryEventProvenance> =
    listOf(
        TelemetryEventProvenance(
            eventName = "fulfillment_run",
            owner = CosmoLogTag.FULFILLMENT,
            privacyClass = TelemetryPrivacyClass.OPERATIONAL_METADATA,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentOrchestrator.kt",
            emittedFrom = "FulfillmentOrchestratorImpl.fulfillAndLog"
        ),
        TelemetryEventProvenance(
            eventName = "fulfillment_action_execution",
            owner = CosmoLogTag.ACTION_HANDLER,
            privacyClass = TelemetryPrivacyClass.ACTION_METADATA,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentActionHandler.kt",
            emittedFrom = "FulfillmentActionHandlerImpl.execute"
        ),
        TelemetryEventProvenance(
            eventName = "task_monitor_message",
            owner = CosmoLogTag.TASK_MONITOR,
            privacyClass = TelemetryPrivacyClass.OPERATIONAL_METADATA,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/actions/mariner/TaskMonitorActivity.kt",
            emittedFrom = "TaskMonitorViewModel.messagesFromExtras"
        ),
        TelemetryEventProvenance(
            eventName = "task_monitor_surface",
            owner = CosmoLogTag.TASK_MONITOR,
            privacyClass = TelemetryPrivacyClass.OPERATIONAL_METADATA,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/actions/mariner/TaskMonitorNavigationBridge.kt",
            emittedFrom = "TaskMonitorNavigationBridge.openTaskMonitor"
        ),
        TelemetryEventProvenance(
            eventName = "fulfillment_action_parse_rejected",
            owner = CosmoLogTag.FULFILLMENT_RUNNER,
            privacyClass = TelemetryPrivacyClass.ACTION_METADATA,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentRunner.kt",
            emittedFrom = "FulfillmentRunner.fulfill"
        ),
        TelemetryEventProvenance(
            eventName = "gatekeeper_run",
            owner = CosmoLogTag.GATEKEEPER,
            privacyClass = TelemetryPrivacyClass.MODEL_DIAGNOSTIC,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeepingPipeline.kt",
            emittedFrom = "GatekeeperLoggerImpl.logGatekeeperRun"
        ),
        TelemetryEventProvenance(
            eventName = "gatekeeper_directedness_gate",
            owner = CosmoLogTag.GATEKEEPER,
            privacyClass = TelemetryPrivacyClass.MODEL_DIAGNOSTIC,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeepingPipeline.kt",
            emittedFrom = "Gatekeeper.onAmbientAudio"
        ),
        TelemetryEventProvenance(
            eventName = "calendar_writable_fallback",
            owner = CosmoLogTag.CALENDAR,
            privacyClass = TelemetryPrivacyClass.ACTION_METADATA,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/FulfillmentActionHandler.kt",
            emittedFrom = "FulfillmentActionHandlerImpl.insertCalendarEvent"
        ),
        TelemetryEventProvenance(
            eventName = "glass_session",
            owner = CosmoLogTag.GLASS,
            privacyClass = TelemetryPrivacyClass.DEVICE_STATE,
            sourcePath = "app/src/main/kotlin/com/google/research/air/cosmo/glasses/CosmoGlass.kt",
            emittedFrom = "CosmoGlassViewModel.recordSession"
        )
    ).also { provenance ->
        check(provenance.distinctBy { it.eventName }.size == provenance.size) {
            "telemetry provenance must use unique event names"
        }
        check(provenance.none { it.containsUserContent }) {
            "telemetry provenance cannot classify Firebase events as raw user content"
        }
    }

@Suppress("DEPRECATION") // Bundle.get(String)
internal fun Bundle.filteredTelemetryCopy(allowedKeys: Set<String>): Bundle {
    val out = Bundle()
    for (key in keySet()) {
        if (key in allowedKeys) {
            when (val v = get(key)) {
                is String -> out.putString(key, v)
                is Boolean -> out.putBoolean(key, v)
                is Int -> out.putInt(key, v)
                is Long -> out.putLong(key, v)
                is Float -> out.putFloat(key, v)
                is Double -> out.putDouble(key, v)
            }
        }
    }
    return out
}

/**
 * Typed telemetry surfaced through [CosmosTelemetry]. Every subclass binds a
 * [FirebaseEventDefinition] in its constructor; this is the compile-time pairing that the
 * `TELEMETRY_FIREBASE_EVENT_DEFINITIONS` registry aggregates. Adding a new subclass without
 * a matching `DEFINITION` companion fails to compile.
 *
 * Naming/param conventions: snake_case ≤ 40 chars, area prefix (`fulfillment_*`, `glass_*`,
 * `gatekeeper_*`); ≤ 6 params per event; coarse types only (counts, booleans, short enums)—
 * never raw user text, audio, transcripts, screen content, or clipboards.
 */
sealed class TelemetryEvent(
    val definition: FirebaseEventDefinition,
    val params: Bundle,
    val logTag: CosmoLogTag = CosmoLogTag.TELEMETRY
) {
    val eventName: String get() = definition.name

    class FulfillmentRun(
        triggerType: String,
        actionCount: Int
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putString("trigger_type", triggerType)
            putInt("action_count", actionCount)
        },
        CosmoLogTag.FULFILLMENT
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "fulfillment_run",
                    requiredParams = setOf("trigger_type", "action_count"),
                    optionalParams = emptySet()
                )
        }
    }

    class ActionExecution(
        actionId: String,
        actionType: String,
        succeeded: Boolean
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putString("action_id", actionId)
            putString("action_type", actionType)
            putBoolean("succeeded", succeeded)
        },
        CosmoLogTag.ACTION_HANDLER
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "fulfillment_action_execution",
                    requiredParams = setOf("action_id", "action_type", "succeeded"),
                    optionalParams = emptySet()
                )
        }
    }

    class TaskMonitorMessage(
        promptLength: Int,
        responseLength: Int
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putInt("prompt_length", promptLength)
            putInt("response_length", responseLength)
        },
        CosmoLogTag.TASK_MONITOR
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "task_monitor_message",
                    requiredParams = setOf("prompt_length", "response_length"),
                    optionalParams = emptySet()
                )
        }
    }

    /** Provenance for Task Monitor entry (Nav3 in-app vs standalone activity). */
    class TaskMonitorSurface(
        surface: String
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putString("surface", surface)
        },
        CosmoLogTag.TASK_MONITOR
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "task_monitor_surface",
                    requiredParams = setOf("surface"),
                    optionalParams = emptySet()
                )
        }
    }

    /** LLM fulfillment JSON parsed an action record that failed typed schema validation. */
    class ActionParseRejected(
        actionType: String,
        reason: String
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putString("action_type", actionType)
            putString("reason", reason)
        },
        CosmoLogTag.FULFILLMENT_RUNNER
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "fulfillment_action_parse_rejected",
                    requiredParams = setOf("action_type", "reason"),
                    optionalParams = emptySet()
                )
        }
    }

    class GatekeeperRun(
        modelId: String,
        detectedBy: String,
        triggeredFulfillment: Boolean,
        detectedSkills: Map<String, Float>
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putString("model_id", modelId.telemetryValue())
            putString("detected_by", detectedBy.telemetryValue())
            putBoolean("triggered_fulfillment", triggeredFulfillment)
            putInt("detected_skill_count", detectedSkills.size)
            detectedSkills.maxByOrNull { it.value }?.let { (skill, score) ->
                putString("top_skill", skill.telemetryValue())
                putFloat("top_score", score)
            }
        },
        CosmoLogTag.GATEKEEPER
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "gatekeeper_run",
                    requiredParams = setOf("model_id", "detected_by", "triggered_fulfillment", "detected_skill_count"),
                    optionalParams = setOf("top_skill", "top_score")
                )
        }
    }

    /**
     * Magic Mic / IntendedSpeechDetector decision per ambient audio transcript. Emitted whenever
     * `Gatekeeper.onAmbientAudio` consults the directedness gate (i.e. when raw-audio mode is on
     * and the EdgeTPU Conformer backend is not active). Lets us measure false-negative rate on
     * real audio over time without leaking transcript content.
     */
    class GatekeeperDirectednessGate(
        gateFired: Boolean,
        rawAudioModeActive: Boolean
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putBoolean("gate_fired", gateFired)
            putBoolean("raw_audio_mode_active", rawAudioModeActive)
        },
        CosmoLogTag.GATEKEEPER
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "gatekeeper_directedness_gate",
                    requiredParams = setOf("gate_fired", "raw_audio_mode_active"),
                    optionalParams = emptySet()
                )
        }
    }

    /** No writable calendar row matched; falling back to ACTION_INSERT flow. */
    class CalendarWritableFallback : TelemetryEvent(
        DEFINITION,
        Bundle(),
        CosmoLogTag.CALENDAR
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "calendar_writable_fallback",
                    requiredParams = emptySet(),
                    optionalParams = emptySet()
                )
        }
    }

    class GlassSession(
        permissionsGranted: Boolean,
        connected: Boolean,
        bound: Boolean
    ) : TelemetryEvent(
        DEFINITION,
        Bundle().apply {
            putBoolean("permissions_granted", permissionsGranted)
            putBoolean("connected", connected)
            putBoolean("bound", bound)
        },
        CosmoLogTag.GLASS
    ) {
        companion object {
            val DEFINITION =
                FirebaseEventDefinition(
                    name = "glass_session",
                    requiredParams = setOf("permissions_granted", "connected", "bound"),
                    optionalParams = emptySet()
                )
        }
    }
}

private fun String.telemetryValue(maxLength: Int = 100): String = trim().take(maxLength)

interface CosmosTelemetry {
    fun log(event: TelemetryEvent)
}

class CosmosTelemetryImpl
@Inject
constructor(
    private val analytics: FirebaseAnalyticsWrapper,
    private val observabilitySchema: ObservabilitySchema
) : CosmosTelemetry {
    private val telemetryFirebaseByName by lazy {
        observabilitySchema.telemetryFirebaseEvents.associateBy { it.name }
    }

    override fun log(event: TelemetryEvent) {
        Log.i(event.logTag.tag, event.eventName)
        val def = telemetryFirebaseByName[event.eventName]
        if (def == null) {
            Log.e(CosmoLogTag.TELEMETRY.tag, "Blocked undocumented TelemetryEvent for Firebase: ${event.eventName}")
            return
        }
        val allowedKeys = def.requiredParams + def.optionalParams
        val sanitized = event.params.filteredTelemetryCopy(allowedKeys)
        for (required in def.requiredParams) {
            if (!sanitized.containsKey(required)) {
                Log.w(
                    CosmoLogTag.TELEMETRY.tag,
                    "Telemetry ${event.eventName} missing required param '$required'; skipping Firebase"
                )
                return
            }
        }
        analytics.logEvent(event.eventName, sanitized)
    }
}

class FirebaseAnalyticsWrapperImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val settings: CosmoSettingStore
) : FirebaseAnalyticsWrapper {
    private val firebaseAnalytics: FirebaseAnalytics? by lazy {
        if (!hasGoogleAppId()) {
            Log.w(CosmoLogTag.FIREBASE.tag, "Firebase Analytics disabled: google_app_id resource missing")
            null
        } else {
            FirebaseAnalytics.getInstance(context)
        }
    }

    override fun logEvent(
        name: String,
        params: Bundle
    ) {
        if (settings.isAnalyticsEnabled()) firebaseAnalytics?.logEvent(name, params)
    }

    private fun hasGoogleAppId(): Boolean {
        val options = FirebaseOptions.fromResource(context)
        return options?.applicationId?.isNotBlank() == true
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingBindings {
    @Binds abstract fun bindWrapper(impl: FirebaseAnalyticsWrapperImpl): FirebaseAnalyticsWrapper

    @Binds abstract fun bindTelemetry(impl: CosmosTelemetryImpl): CosmosTelemetry
}

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides @Singleton
    fun provideObservabilitySchema(): ObservabilitySchema = GenesisObservabilitySchema
}
