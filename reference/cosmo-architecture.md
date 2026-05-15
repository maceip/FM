# Architecture

Synthesis of the nine substrate maps. Every claim is an aggregation
of substrate citations; none is speculation. Citation grammar:
`[smali]`, `[elf]`, `[asset]`, `[aapt2]`, `[doc §X.Y]`.

---

The process boots from a manifest-declared `Application` whose Hilt
parent extends `android.app.Application` and builds the
`SingletonComponent` inside `super.onCreate()` `[runtime §A][dagger §A]`.
A `PureProcess.isPure(Context)` check short-circuits the
`:primes_lifeboat` sub-process before any subsystem starts
`[runtime §A, §C]`. `androidx.startup.InitializationProvider` plus an
explicit `ProcessInitializerRunner.init()` complete startup wiring.

Hilt projects an 11-component tree — `SingletonComponent`,
`ServiceComponent`, `ActivityRetainedComponent` and its descendants
including `ViewModelComponent`, down to `ViewWithFragmentComponent`
`[dagger §F]`. Forty-four user-written `@Module` classes plus 14
`HiltWrapper_*` aliases declare 73 `@Provides`/`@Binds` edges
`[dagger §B, §C]`. Thirty-three `@Qualifier` annotations disambiguate
otherwise-identical types — most importantly the per-source
`InMemoryEphemeralBuffer` and per-source `SodaController` slots
`[dagger §D]`. ViewModels register through paired `*_HiltModules$BindsModule`
+ `*_HiltModules$KeyModule` `[dagger §J]`.

The `AndroidManifest.xml` declares 29 `<uses-permission>`s, one
custom signature `<permission>`, eight activities, twenty-four
services, eight receivers, two providers, and a `<queries>` block
covering seven companion packages plus the projected-XR bind/engagement
intents `[runtime §B, §B.5, §G]`. Foreground-service types are
encoded as bitmask: `microphone=0x80`, `camera=0x40`, `dataSync=0x01`,
`specialUse=0x800` `[runtime §F]`. The launcher activity carries
`MAIN`+`LAUNCHER`; the double-tap activity uses `Theme.NoDisplay` and
exits as soon as it broadcasts `Intent("show_session", trigger_type=DOUBLE_TAP)`
`[ui §D]`.

Configuration is a 60-field protobuf in
`androidx.datastore.core.DataStore` keyed by `cosmo_setting.pb`,
exposed as `StateFlow<CosmoSetting>` through
`getCosmoSettingFlow()` `[settings §A, §B, §D]`. Per-field setters
serialize through DataStore's executor. A separate
`CosmoConfiguration` exposes 27 build-flavor visibility flags fixed
at construction `[settings §C]`. `CosmoStateManager` schedules
start/end-of-day transitions through `AlarmManager` against the
`COSMO_STATE_UPDATE_ACTION` receiver and snoozes Cosmo when battery
falls below `BATTERY_LOW_THRESHOLD = 10.0f` `[settings §E]`.

Captors share the shape `startCapture(): Flow<T>` plus `flush()`. A
foreground microphone service (`foregroundServiceType=microphone`)
wraps `AudioRecord` and feeds four parallel SODA-based captors —
ambient, buds, device, watch — that emit `TranscriptionData`
`[audio §B, §D, §E.1, §F]`. Notification capture extends
`NotificationListenerService`; screen-text and screen-interaction
captors implement an `AccessibilityEventListener` interface receiving
events from a `ConnectedAccessibilityServiceProvider` bound to the
application's own `AccessibilityService` `[text §A.1, §B.2, §C.2]`.
CameraX captures device-camera frames through an `ImageAnalysis$Analyzer`
throttled to one frame per 10,000 ms (`0x2710` read directly from
the analyzer body) `[image §A.3]`. A second camera path binds the
projected-glass capture service `[image §B]`.

Twenty-three per-source `InMemoryEphemeralBuffer<T>` instances sit
between captors and consumers, each disambiguated by a
runtime-retained `@Qualifier` annotation `[dagger §C.5][text §D.3]`.
Entries timestamp on insert and drop on access past
`maxEntryAgeMillis`, which tracks the user setting
`getEphemeralBufferDurationMin()` `[text §D.2, settings §G.1]`.

The gatekeeper is a two-backbone classifier — regex matcher plus
BERT — fused by `CombinedBertRegexGatekeepingModel` `[dagger §C.8]`.
TFLite assets ship CPU and Pixel-9/Pixel-10 EdgeTPU model variants;
a separate audio-frontend model preprocesses raw audio for the
audio-side gatekeeper `[audio §H, §H.1]`. BERT trains on 14 skill
labels; six source-prefix tags (`"Ambient audio: "`, `"Buds audio: "`,
`"Device audio: "`, `"Watch audio: "`, `"Notification: "`,
`"Screen: "`) prepend the input. Output is a
`MutableSharedFlow<GatekeepingResult>` Hilt singleton consumed by
both `IntentExtractorImpl` and `FulfillmentOrchestratorImpl`
`[dagger §C.7][llm §E.2][text §E.2]`.

The `LLM` interface takes either `String` or `List<PromptPart>`
(`Text`/`Audio`/`Image`) and returns a result or streams tokens.
Local: `AICoreLLM` binds the AICore service via
`com.google.android.apps.aicore.client.api`, requesting
`AiFeature 108 (LLM-Nano V3)` `[llm §A.3]`. Remote:
`PrivateInferenceArateaLLM` builds a `Blob`/`Content`/`Candidate`
proto and calls `PrivateArateaClient.performInference` over an Oak
session (`libpi_client_session_config_jni.so`,
`liboak_session_config_builder_jni.so`) `[llm §A.9, §B]`.
`NetworkAwareLLM` selects between two delegates via
`NetworkUtilsKt.isNetworkAvailable` →
`ConnectivityManager.getActiveNetwork`/`getNetworkCapabilities`
`[llm §A.7]`. `LLMStatsRepository` accumulates per-call hit/success/
failure StateFlows.

Skills are LLM-generated. `SkillGenerator` runs
`skill_definition_prompt.jinja2` against `filtered_skill_schema.textproto`
(23 `ACTION_TYPE_*` enums); `GatekeepingGenerator` runs
`gatekeeper_trigger_prompt.jinja2` against
`gatekeeper_trigger_info_schema.textproto` (a two-field
`trigger_descriptions` + `trigger_strings` shape) `[llm §C, §D]`.
Six `FulfillmentTriggerType` values — `BATCH_PIPELINE`,
`GATEKEEPER_TRIGGER`, `PIXEL_PHONE_DOUBLE_TAP`,
`PIXEL_PHONE_PICK_UP`, `PIXEL_WATCH_DOUBLE_PINCH`, `USER_QUERY` —
enter `FulfillmentOrchestratorImpl`, which dispatches to a
`FulfillmentRunner` whose 16-arg constructor holds nine
`EphemeralBufferReader`s, an `ActionsRepository`, a `MemoryStorage`,
two `LLM` slots, and a prompt builder `[llm §E]`. The runner renders
`fulfillment_prompt.jinja2` (proactive) or
`reactive_fulfillment_prompt.jinja2` (user query), parses the JSON
`NEW`/`UPDATED`/`REMOVED` array, and constructs `FulfillmentAction`
sealed-class subtypes — 24 of them, verified by per-file `.super`
grep `[llm §F.1]`.

`FulfillmentActionHandlerImpl.execute<Subtype>` dispatches each
action: most build an `Intent` and `launchIntent`; the calendar
executor writes through `ContentResolver.insert` against the Calendar
Provider; `executeInvokeAgentQ` opens an `AgentQSessionManager` that
binds an external `a2a.AgentClient`; the browsing-agent executor
opens `TaskMonitorActivity` `[llm §F]`. `ActionsRepository` is the
state machine — `currentActions`, `userFacingActions`,
`executedActions`, `userDismissedActions`, `cosmoRemovedActions`,
plus `getFulfillmentHistory(): StateFlow<List<FulfillmentDebugInfo>>`
`[llm §F.3]`.

UI is Compose. `MainActivity.onCreate` calls
`ComponentActivityKt.setContent$default` and renders a `NavHost`
graph populated by 24 `*ViewComposable`s, each Hilt-injected, plus
12 debug-panel composables `[ui §A, §E, §G]`. `CosmoGlassActivity`
renders Compose only on `requiredDisplayCategory="…XR_PROJECTED"`;
`TaskMonitorActivity` renders the Mariner browsing-agent's
per-message bubble list. Two `@HiltViewModel`s back `CosmoGlassActivity`
and `TaskMonitorActivity`. Resources total 9,696 entries; the
application contributes ~10 original strings and one classic XML
layout (`floaty_ui`) `[ui §H]`.

Observability instruments three surfaces. Firebase Analytics emits
`fulfillment_run`, `gatekeeper_run`, and `bert_invocation` with
stable parameter schemas (`fulfillment_run_*` for action counts and
latency; `gatekeeper_model_id` ∈ `{bert, regex, both, none}`,
`detected_<skillId>`, `bert_confidence_<skillId>`,
`triggered_fulfillment`, six `screen_type_*` flags) `[telemetry §A]`.
Flogger declares 309 per-class `GoogleLogger` instances accessed
through synthetic `access$getLogger$cp()` companions `[telemetry §B]`.
Seventy-eight `COSMO-<Component>` log-tag strings span every
subsystem `[telemetry §C]`. `FulfillmentDebugInfo` is a 25-field
proto held in memory per fulfillment run, exposed as a `StateFlow`
list to the in-app debug panel `[telemetry §D]`.

---

## Reachability under DMCA §1201(f)

Reachable from a debug-keystore APK on a retail Pixel: microphone,
buds, foreground camera, notification capture, screen capture, screen
interaction, AICore inference (when installed), private remote
inference (when network plus credentials), every `executeXxx` whose
Intent the user can grant runtime perms for.

Not reachable: device-audio (`CAPTURE_AUDIO_OUTPUT` +
`MODIFY_AUDIO_ROUTING` are signature), call-audio
(`CAPTURE_VOICE_COMMUNICATION_OUTPUT` is signature), AOC nanoapp
(`ACCESS_CONTEXT_HUB` plus firmware verification), vendor-only Glass
projected capture and projected-XR engagement.

Four substitution points let an interop implementation swap
components without disturbing the architecture: the runner's two-LLM
constructor slots, `NetworkAwareLLM`'s two-delegate routing, the
gatekeeper's two-backbone fusion, and the action handler's
per-subtype dispatch table. Sealed-class action polymorphism admits
partial implementation — any subset of the 24 subtypes implemented,
the rest mapped to `Unsupported`.

---

Provenance: the nine substrate documents. Aggregation: yes.
Speculation: no.
