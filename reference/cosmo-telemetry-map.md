# Cosmo Telemetry & Logging — Static Analysis Map

Static-analysis map of the observability surface of the recovered
Cosmo APK (`com.google.research.air.cosmo`, antisplit v1.1):

- the `FirebaseAnalytics` + `FirebaseAnalyticsWrapper` event surface,
- every `GoogleLogger` (Flogger) site,
- every `COSMO-*` log tag,
- the `FulfillmentDebugInfo` proto + history flow,
- the dev/debug surface (debug panel, addTestActions),
- exception types,
- `LoggingParamsStore`,
- `CosmoStateManager` flow exposure (battery state, schedule).

## Discipline

Every claim is backed by `[smali …]`, `[asset …]`, `[aapt2 …]`, or
`[doc §X.Y]`. Aggregations carry the union of their tags.

---

## Section A — Firebase Analytics

### A.1 `FirebaseAnalyticsWrapper` interface

`com.google.research.air.cosmo.logging.FirebaseAnalyticsWrapper`
(`.super Ljava/lang/Object;`):

```
public abstract logEvent(java.lang.String, android.os.Bundle): void
```

### A.2 `FirebaseAnalyticsWrapperImpl`

`.super Ljava/lang/Object;`. Constructor:

```
public <init>(com.google.firebase.analytics.FirebaseAnalytics)
```

Method:

```
public logEvent(String, Bundle): void
```

Cosmo accesses this via `FirebaseAnalyticsWrapperImpl_Factory`
`[dagger §G]`. The wrapped `FirebaseAnalytics` instance is provided by
`LoggingModule_ProvideFirebaseAnalyticsFactory` and the wrapper itself
by `LoggingModule_ProvideFirebaseAnalyticsWrapperFactory`
`[dagger §C.16]`.

### A.3 Logged events

Two event names are emitted via `FirebaseAnalyticsWrapper.logEvent` in
the recovered DEX (`grep -rln 'FirebaseAnalyticsWrapper;->logEvent'`):

| Caller | Event name | Source |
|---|---|---|
| `FulfillmentOrchestratorImpl.logFulfillmentRun` | `"fulfillment_run"` | `[smali fulfillment/FulfillmentOrchestratorImpl.smali]` |
| `GatekeeperLoggerImpl.logGatekeeperRun` | `"gatekeeper_run"` | `[smali gatekeeping/GatekeeperLoggerImpl.smali]` |
| `GatekeeperLoggerImpl.logBertInvocation` | `"bert_invocation"` | `[smali gatekeeping/GatekeeperLoggerImpl.smali]` |

### A.4 Event params

`fulfillment_run` Bundle params (verified `^const-string` near the
`logEvent` call in `FulfillmentOrchestratorImpl.smali`):

```
fulfillment_run_prompt_char_length         (int)
fulfillment_run_new_actions_count          (int)
fulfillment_run_updated_actions_count      (int)
fulfillment_run_removed_actions_count      (int)
fulfillment_run_latency_ms                 (long)
```

`gatekeeper_run` Bundle params (from `GatekeeperLoggerImpl.smali`
const-strings near the same call site):

```
gatekeeper_model_id           (string: "bert" | "regex" | "both" | "none" | "unknown")
detected_by                   (string)
detected_<skillId>            (boolean per skill)
bert_confidence_<skillId>     (float per skill)
triggered_fulfillment         (boolean)
screen_type_checkout_page     (boolean)
screen_type_product_details   (boolean)
screen_type_search_results    (boolean)
screen_type_shopping_cart     (boolean)
screen_type_shopping_category (boolean)
screen_type_travel_category   (boolean)
```

`bert_invocation` is emitted from inside the BERT-invocation path
(`bert` is the model id; `bert_confidence_*` per-skill confidences are
the same dynamic param shape as in `gatekeeper_run`).

The `analyticsEnabled` proto field (`CosmoSetting.ANALYTICS_ENABLED`
`[settings §A]`) gates emission.

### A.5 Companion analytics services

Manifest declares `[runtime §B.2]`:

```
com.google.android.gms.measurement.AppMeasurementReceiver
com.google.android.gms.measurement.AppMeasurementService
com.google.android.gms.measurement.AppMeasurementJobService
com.google.firebase.components.ComponentDiscoveryService
```

`ComponentDiscoveryService` registers
`com.google.firebase.analytics.connector.internal.AnalyticsConnectorRegistrar`
and `com.google.firebase.installations.FirebaseInstallationsRegistrar`
via meta-data `[aapt2 xmltree@249, @252]`.

---

## Section B — `GoogleLogger` (Flogger) sites

Cosmo uses `com.google.common.flogger.GoogleLogger` extensively. Every
class that declares a Flogger logger exposes it via the synthetic
companion accessor `access$getLogger$cp(): GoogleLogger`.

309 smali files reference `Lcom/google/common/flogger/GoogleLogger;`
(`grep -rl` count). Each Flogger logger is instantiated via
`GoogleLogger.forInjectedClassName(java.lang.String)` with the smali
class name; the logger's de-facto tag is therefore that class name
unless overridden by a `withInjectedLogSite(...)` call carrying an
explicit string.

Cosmo's loggers fall into four groups: those that carry an explicit
`COSMO-*` tag string passed to `Log.w/Log.i/Log.e` or to the Flogger
`with(...)` chain (§C below), those that do not pass an explicit tag
(the default Flogger output uses the smali class name), the
`GatekeeperLoggerImpl` (§A above), and `FirebaseAnalyticsWrapperImpl`
(§A above).

The complete enumeration of class-name-tagged loggers is the
`access$getLogger$cp` symbol set; it spans:

```
controller/                  (CosmoController and inner classes)
fulfillment/                  (Orchestrator, Runner, Handler, ActionsRepository,
                               AgentQSessionManager, DoubleTapActivity,
                               ActionChipsFactory + creators, mariner/TaskMonitor*)
gatekeeping/                  (Gatekeeper + 7 inner-flow classes,
                               GatekeeperLoggerImpl + inner)
gatekeeping/impl/             (BertClassifier..., AudioGatekeeping...,
                               CombinedBertRegex..., RegexGatekeepingV0)
gatekeeping/models/           (BertEncoderImpl, ConformerEncoderImpl,
                               DarwinnDelegateFactory)
intentextraction/             (IntentExtractorImpl)
contentcapture/               (PeriodicScreenCaptorImpl + 3 inner,
                               ScreenToTextKt)
locationcapture/              (LocationCaptorImpl)
notificationcapture/          (NotificationCaptureManager,
                               NotificationCaptureService)
interactioncapture/           (ScreenInteractionCaptorImpl + inner)
audiocapture/                 (SODA*Captor x4, RecordServiceManager)
ephemeralbuffer/              (InMemoryEphemeralBuffer + Companion)
memory/                       (NoOpMemoryImpl)
glasses/                      (CosmoGlassManager + inner; CosmoGlassActivity)
ui/mainscreen/                (MainActivity + Companion)
voiceassistant/               (MainInteractionService, MainInteractionSession,
                               LockscreenView, ChatView*, BrowsingAgentVM,
                               CreateSkillVM, ScreenContextVM, SkillLibraryVM,
                               + every debugpanel/*ViewComposable)
gboard/                       (GBoardSuggestionsService + Controller,
                               PromptGenerator)
lib/llm/                      (AICoreLLM, NetworkAwareLLM,
                               PrivateInferenceArateaLLM,
                               PrivateInferenceArateaBatchLLM, LTCLLM,
                               LLMStatsRepository)
lib/soda/                     (SodaController + concrete subclasses,
                               LanguagePackManager, AudioAccumulator)
lib/record/                   (RecordService, NanoAppClient)
lib/buds/                     (BudsAudioProviderImpl + inner)
lib/wearable/                 (WearableService, WearableAudioStreamService,
                               WearableActionSynchronizer + Companion,
                               WearableStatusSynchronizer, WatchStatusState,
                               WearableClientImpl)
lib/tts/                      (LocalTTS, VoiceLMTTS, AudioRenderer)
lib/google/auth/              (AuthManager)
lib/google/googledocs/        (GoogleDocsManager)
lib/screenstate/              (ScreenStateMonitorImpl)
lib/battery/                  (BatteryStateReceiver)
lib/permission/               (PermissionHelperImpl)
altair/                       (AltairActionSynchronizer + inner + Companion)
batch/                        (BatchCoordinator, BatchWorker, BatchRunState)
batch/pipelines/              (FulfillmentPipeline, RetranscriptionPipeline)
skills/                       (SkillsRepositoryImpl)
skills/generation/            (SkillGenerator, GatekeepingGenerator)
setting/                      (CosmoSettingStore, CosmoStateManager)
logging/                      (LoggingParamsStore, FirebaseAnalyticsWrapperImpl)
```

(The list is structural — each entry is a directory with at least one
`access$getLogger$cp`-declaring class. To enumerate every class:
`grep -rl 'access$getLogger$cp' cosmo_baksmali/classes*/com/google/research/air/cosmo/`.)

---

## Section C — `COSMO-*` log tags

Verbatim list of every `"COSMO-…"` const-string in the smali tree
(78 unique strings, deduped via grep on
`'"COSMO-[^"]*"'`):

```
"COSMO-ActionsRepository"
"COSMO-AgentQSessionManager"
"COSMO-AICoreLLM"
"COSMO-AltairSynchronizer"
"COSMO-AudioAccumulator"
"COSMO-AudioCompressor"
"COSMO-AudioGatekeepingModel"
"COSMO-AuthManager"
"COSMO-BatchCoordinator"
"COSMO-BatchWorker"
"COSMO-Battery"
"COSMO-BertClassifierGatekeepingModel"
"COSMO-BertEncoder"
"COSMO-BudsAudioProvider"
"COSMO-CameraViewComposable"
"COSMO-ChatView"
"COSMO-ChatViewModel"
"COSMO-CombinedBertRegexGatekeepingModel"
"COSMO-CommonComponentComposable"
"COSMO-ConformerEncoder"
"COSMO-ConversationController"
"COSMO-CosmoAccessibilityService"
"COSMO-CosmoController"
"COSMO-CosmoSettingStore"
"COSMO-CosmoState"
"COSMO-DebugUtils"
"COSMO-FloatyNavView: chatTrigger: %s"
"COSMO-FloatyNavView"
"COSMO-Fulfillment"
"COSMO-FulfillmentActionHandler"
"COSMO-Gatekeeper"
"COSMO-GatekeepingGenerator"
"COSMO-GBoardSuggestionsController"
"COSMO-GoogleDocsManager"
"COSMO-InMemoryEphemeralBuffer"
"COSMO-IntentExtractor"
"COSMO-LocalTTS"
"COSMO-LocationCaptorImpl"
"COSMO-LoggingParamsStore"
"COSMO-LTCLLM"
"COSMO-MainInteractionService: chatTrigger: "
"COSMO-MainInteractionService"
"COSMO-MainInteractionSession onCreate"
"COSMO-MainInteractionSession onCreateContentView"
"COSMO-MainInteractionSession onHide"
"COSMO-MainInteractionSession onShow"
"COSMO-MainInteractionSession: Making LPP window focusable"
"COSMO-MainInteractionSession: Making LPP window non-focusable"
"COSMO-MainRecognitionService"
"COSMO-MemoryView"
"COSMO-NanoAppClient"
"COSMO-NotificationManager"
"COSMO-NotificationService"
"COSMO-PeriodicScreenCaptorImpl"
"COSMO-PrivateInferenceArateaBatchLLM"
"COSMO-PrivateInferenceArateaLLM"
"COSMO-RecordService"
"COSMO-RegexGatekeepingModelV0"
"COSMO-ReplayView"
"COSMO-RetranscriptionPipeline"
"COSMO-ScreenInteractionCaptor"
"COSMO-ScreenStateMonitor"
"COSMO-SkillGenerator"
"COSMO-SkillsRepositoryImpl"
"COSMO-SODAAmbientAudioCaptor"
"COSMO-SODABudsAudioCaptor"
"COSMO-SodaController"
"COSMO-SODADeviceAudioCaptor"
"COSMO-SODAWatchAudioCaptor"
"COSMO-WatchStatusState"
"COSMO-WearableActionSynchronizer - proactiveSync: %s"
"COSMO-WearableActionSynchronizer - userFacingActions updated: %s"
"COSMO-WearableActionSynchronizer started"
"COSMO-WearableActionSynchronizer"
"COSMO-WearableAudioStream"
"COSMO-WearableService"
"COSMO-WearableStatusSynchronizer"
```

The first identifier after `COSMO-` is the de-facto component tag; the
trailing colon-separated suffixes (e.g. `: chatTrigger: %s`,
`onCreate`, etc.) are partial log-line templates emitted directly via
`Log.{i,w,e,d}` rather than through Flogger.

---

## Section D — `FulfillmentDebugInfo` proto

`com.google.research.air.cosmo.fulfillment.FulfillmentDebugInfo`
(verified by `^\.field public static final.*FIELD_NUMBER` grep):

```
FULFILLMENT_ID_FIELD_NUMBER                        = 0x01
CREATED_AT_FIELD_NUMBER                             = 0x02
EPHEMERAL_AUDIO_BUFFER_FIELD_NUMBER                 = 0x03
EPHEMERAL_SCREEN_BUFFER_FIELD_NUMBER                = 0x04
EPHEMERAL_NOTIFICATION_BUFFER_FIELD_NUMBER          = 0x05
GATEKEEPER_RESULT_FIELD_NUMBER                      = 0x06
MEMORY_RETRIEVAL_QUERIES_FIELD_NUMBER               = 0x07
MEMORY_RETRIEVAL_RESULTS_FIELD_NUMBER               = 0x08
LLM_QUERY_FIELD_NUMBER                              = 0x09
LLM_RESULT_FIELD_NUMBER                             = 0x0a
PREVIOUS_ACTIONS_FIELD_NUMBER                       = 0x0b
EXECUTED_ACTIONS_FIELD_NUMBER                       = 0x0c
DISMISSED_ACTIONS_FIELD_NUMBER                      = 0x0d
ACTIONS_DESCRIPTION_FIELD_NUMBER                    = 0x0e
EPHEMERAL_DEVICE_AUDIO_BUFFER_FIELD_NUMBER          = 0x0f
RAW_AUDIO_CONTENT_FIELD_NUMBER                      = 0x10
LATENCY_MS_FIELD_NUMBER                             = 0x11
LLM_REQUEST_SIZE_BYTES_FIELD_NUMBER                 = 0x12
NUM_NEW_ACTIONS_FIELD_NUMBER                        = 0x13
NUM_UPDATED_ACTIONS_FIELD_NUMBER                    = 0x14
NUM_REMOVED_ACTIONS_FIELD_NUMBER                    = 0x15
CONTEXT_SOURCE_FIELD_NUMBER                         = 0x16
FULFILLMENT_TRIGGER_TYPE_FIELD_NUMBER               = 0x17
DEVICE_CAMERA_FRAMES_FIELD_NUMBER                   = 0x18
EPHEMERAL_INTENT_BUFFER_FIELD_NUMBER                = 0x19
```

This is the per-fulfillment-run debug record. Stored via
`ActionsRepository.addFulfillmentHistory(FulfillmentDebugInfo)`
`[llm §F.3]`; the in-memory history flow is exposed as
`getFulfillmentHistory(): StateFlow<List<FulfillmentDebugInfo>>`.
Lookup by id: `getFulfillmentDebugInfo(String)`. Per-id list:
`listFulfillmentHistory(String)`. Bulk clear: `clearAllDebugInfos()`.

`COSMO-Fulfillment` + `COSMO-FulfillmentActionHandler` are the two
log tags emitting against this surface (§C above).

---

## Section E — Debug / dev surface

### E.1 Debug-panel view composables

`com/google/research/air/cosmo/voiceassistant/debugpanel/`:

```
AboutViewComposable
ActionListViewComposable
BufferViewComposable
DebugPanelViewComposable
FulfillmentViewComposable
GatekeepingViewComposable
HealthStatusViewComposable
LogsViewComposable
MemoryViewComposable
ReplayViewComposable
StatsViewComposable
UxViewComposable
```

Each has a matching `*_Factory` `[dagger §G]`. The debug-panel
composables are reachable from `MainActivity` (`[ui §D]`) — none are
declared as standalone activities in the manifest, so the surface is
behind in-app navigation rather than launchable from a launcher.

### E.2 Test-actions trigger

`ActionsRepository.addTestActions(): void` `[llm §F.3]` is invoked
from exactly one site
(`grep -rn 'addTestActions\b'`):

```
[smali voiceassistant/debugpanel/UxViewComposable.smali:104]
    invoke-virtual {v0}, ActionsRepository;->addTestActions()V
```

…so the dev/test action seeding path is gated behind the
`UxViewComposable` button inside the debug panel.

### E.3 `DebugUtils`

`voiceassistant/debugpanel/DebugUtils.smali` — log tag
`"COSMO-DebugUtils"`. Helper class for the debug panel.

---

## Section F — Exception types

Two user-defined exceptions under
`com/google/research/air/cosmo/` (`find … -name '*Exception.smali'`):

| Class | super | Doc ref |
|---|---|---|
| `memory.MemoryStorageException` | `Ljava/lang/Exception;` | `[text §D.5]` |
| `skills.generation.InvalidResponseException` | `Ljava/lang/Exception;` | (raised by `SkillGenerator.parseSkillGenerationResponse` and `GatekeepingGenerator.parseGatekeeperTriggerInfoResponse` `[llm §C.1, §C.2]`) |

---

## Section G — `LoggingParamsStore`

`com.google.research.air.cosmo.logging.LoggingParamsStore`
(`.super Ljava/lang/Object;`).

Constructor:

```
public <init>(androidx.datastore.core.DataStore, kotlinx.coroutines.CoroutineScope)
```

Methods:

```
final getLoggingParamsFlow(): kotlinx.coroutines.flow.StateFlow
final getUserId(Continuation): java.lang.Object
```

Plus synthetic accessors `access$generateUserId`,
`access$getCompanion$p`, `access$getDataStore$p`,
`access$get_loggingParamsState$p`, `access$getLogger$cp`.

`LoggingModule_ProvideLoggingParamsDataStoreFactory` `[dagger §C.16]`
provides the backing `DataStore<…>`. The DataStore filename and
proto-payload type are not enumerated here without reading the factory
body.

Log tag: `"COSMO-LoggingParamsStore"` (§C above).

---

## Section H — `CosmoStateManager` flows (telemetry-side)

Cross-ref `[settings §E]`. `CosmoStateManager` exposes a
`StateFlow<CosmoState>` via `getCosmoState()`; its updates are tagged
`"COSMO-CosmoState"` (§C above). The `BatteryStateReceiver`
(`COSMO-Battery`) feeds battery levels; the `ScreenStateMonitorImpl`
(`COSMO-ScreenStateMonitor`) feeds screen on/off into
`CosmoSettingStore.isScreenOn(): StateFlow`.

These three `StateFlow`s — `getCosmoState`, `getBatteryLevelFlow`,
`isScreenOn` — are the device-level signals available to the rest of
the app for state-driven decisions.

---

## Section I — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/logging/`:
  `FirebaseAnalyticsWrapper`, `FirebaseAnalyticsWrapperImpl`,
  `LoggingModule`, `LoggingParamsStore` and their factories.
- `cosmo_baksmali/classes*/com/google/research/air/cosmo/gatekeeping/`:
  `GatekeeperLoggerImpl`, `GatekeeperLogger`.
- `cosmo_baksmali/classes*/com/google/research/air/cosmo/fulfillment/`:
  `FulfillmentDebugInfo`, `ActionsRepository`,
  `FulfillmentOrchestratorImpl`.
- `grep -rEho '"COSMO-[^"]*"' cosmo_baksmali/classes*/com/google/research/air/cosmo/`
  for the `COSMO-*` tag inventory.
- `grep -rl 'Lcom/google/common/flogger/GoogleLogger;'
  cosmo_baksmali/classes*/com/google/research/air/cosmo/` for the 309
  Flogger sites.
- Cross-references: `[audio §H.3, §I]`, `[text §B.2, §D.5, §E.2]`,
  `[image §A.1, §B.1]`, `[llm §A.3, §C.1, §C.2, §E.2, §F.3]`,
  `[runtime §A, §B.2, §B.3, §G]`, `[dagger §C.16, §G]`,
  `[settings §A, §E, §G]`, `[ui §D]`.

This document is static analysis only.
