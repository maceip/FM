# Cosmo Settings & Config — Static Analysis Map

Static-analysis map of the runtime-configuration surface that gates
every captor and routing decision in the recovered Cosmo APK
(`com.google.research.air.cosmo`, antisplit v1.1):

- the `CosmoSetting` proto field set,
- the `CosmoSettingStore` interface and impl (read- and write-side),
- the `CosmoConfiguration` shape (build-time visibility flags),
- the `CosmoSettingModule` Hilt provider for the DataStore,
- the `CosmoStateManager` (start/end-of-day scheduler + battery-snooze),
- the `CosmoStateUpdateReceiver`,
- defaults / persistence backing,
- the consumer matrix: every captor / runner / handler that reads a
  setting, indexed by the setting it reads.

## Discipline

Every claim is backed by `[smali …]`, `[asset …]`, `[aapt2 …]`, or
`[doc §X.Y]`. Aggregations carry the union of their tags.

---

## Section A — `CosmoSetting` proto fields

`com.google.research.air.cosmo.setting.CosmoSetting` is a protobuf
message; full field set verified by grep of
`^\.field public static final.*FIELD_NUMBER` and
`^\.field private .*_:` on `[smali setting/CosmoSetting.smali]`.

60 user-facing fields, ordered by field number:

```
SCREEN_CONTEXT_ENABLED                = 0x01
AMBIENT_AUDIO_ENABLED                 = 0x02
UPDATE_KEEP_ENABLED                   = 0x03
SUGGEST_CALENDAR_ENABLED              = 0x04
SUGGEST_MARINER_ENABLED               = 0x05
SUGGEST_CALL_AGENT_ENABLED            = 0x06
SUGGEST_PHOTOS_ENABLED                = 0x07
SUGGEST_DEEP_RESEARCH_ENABLED         = 0x08
COSMO_INSIGHTS_ENABLED                = 0x09
SUGGEST_DOCS_ENABLED                  = 0x0a
RECORD_MEMORIES_ENABLED               = 0x0b
DEFINE_TERMS_ENABLED                  = 0x0c
PROVIDE_PERSON_CONTEXT_ENABLED        = 0x0d
PROVIDE_EVENT_CONTEXT_ENABLED         = 0x0e
EPHEMERAL_BUFFER_DURATION_MIN         = 0x0f
GLOBAL_COSMO_ENABLED                  = 0x10
SUGGEST_GOOGLE_SEARCHES_ENABLED       = 0x11
BLOCKED_PACKAGES                      = 0x12
NOTIFICATION_CAPTURE_ENABLED          = 0x13
VOICE_MATCH_ENABLED                   = 0x14
SUGGEST_TIMER_ENABLED                 = 0x15
MEDIA_AUDIO_ENABLED                   = 0x16
CONNECT_TO_GLASS_ENABLED              = 0x17
LOCATION_CAPTURE_ENABLED              = 0x18
VOICE_MATCH_ENROLLED                  = 0x19
MAX_RAW_AUDIO_IN_FULFILLMENT_SEC      = 0x1a
GLASSES_CAMERA_ENABLED                = 0x1b
ANALYTICS_ENABLED                     = 0x1c
PROVIDE_RECALL_ENABLED                = 0x1d
WATCH_AUDIO_ENABLED                   = 0x1e
BATCH_MODE_ENABLED                    = 0x1f
CAMERA_ENABLED                        = 0x20
BUDS_AUDIO_ENABLED                    = 0x21
COSMO_START_DAY_TIME                  = 0x22
COSMO_END_DAY_TIME                    = 0x23
SNOOZE_UNTIL_TIME                     = 0x24
SCREEN_INTERACTION_CAPTURE_ENABLED    = 0x25
DEVELOPER_MODE_ENABLED                = 0x26
DEVELOPER_MODE_UNLOCKED               = 0x27
COSMO_TRANSPARENCY_LEVEL              = 0x28
SNOOZE_UNTIL_TIMESTAMP_MS             = 0x29
ON_DEVICE_TRANSCRIPTION_ENABLED       = 0x2a
STREAM_TO_WATCH_ENABLED               = 0x2b
AUTO_SNOOZE_ENABLED                   = 0x2c
INTENT_EXTRACTION_ENABLED             = 0x2d
USE_NANOAPP_AUDIO_ENABLED             = 0x2e
PROCESSING_MODE                       = 0x2f
FULFILLMENT_MODEL                     = 0x30
DEVICE_CONTROL_ENABLED                = 0x31
RAW_AUDIO_GATEKEEPER_ENABLED          = 0x32
SUGGEST_NAVIGATION_ENABLED            = 0x33
GATEKEEPER_ENABLED                    = 0x34
SUGGEST_ADD_TASK_ENABLED              = 0x35
SUGGEST_MAIL_ENABLED                  = 0x36
INVOKE_AGENT_Q_ENABLED                = 0x37
BATCH_PROCESSING_INTERVAL_MINS        = 0x38
ALTAIR_SYNC_ENABLED                   = 0x39
DRAFT_SMS_ENABLED                     = 0x3a
PLAY_MUSIC_ENABLED                    = 0x3b
```

Field types (from `^\.field private` declarations):

- All `*_ENABLED` fields are `Z` (boolean).
- `EPHEMERAL_BUFFER_DURATION_MIN`, `MAX_RAW_AUDIO_IN_FULFILLMENT_SEC`,
  `COSMO_START_DAY_TIME`, `COSMO_END_DAY_TIME`,
  `BATCH_PROCESSING_INTERVAL_MINS` → `I` (int).
- `SNOOZE_UNTIL_TIMESTAMP_MS` → `J` (long).
- `COSMO_TRANSPARENCY_LEVEL` → `F` (float).
- `SNOOZE_UNTIL_TIME` → `Lcom.google.protobuf.Timestamp;`.
- `BLOCKED_PACKAGES` → `Lcom.google.protobuf.Internal$ProtobufList;` of strings.
- `PROCESSING_MODE` → `I` (enum proxied; enum class
  `com.google.research.air.cosmo.setting.ProcessingMode`).
- `FULFILLMENT_MODEL` → `I` (enum proxied; enum class
  `com.google.research.air.cosmo.setting.FulfillmentModel`).

Two `bitField` ints (`bitField0_`, `bitField1_`) carry presence
information for all 60 fields.

---

## Section B — `CosmoSettingStore`

`com.google.research.air.cosmo.setting.CosmoSettingStore`
(`.super Ljava/lang/Object;`).

### B.1 Constructor

```
public <init>(
    androidx.datastore.core.DataStore,                                       // DataStore<CosmoSetting>
    kotlinx.coroutines.CoroutineScope,
    com.google.research.air.cosmo.lib.screenstate.ScreenStateMonitor
)
```

### B.2 Read-side methods

73 read-side accessors enumerated from `^\.method public final`
on `[smali setting/CosmoSettingStore.smali]`:

```
getCosmoSettingFlow(): kotlinx.coroutines.flow.StateFlow
getCurrentSetting(): CosmoSetting
getCurrentValue(): CosmoSetting
getAndroidBuildType(): String
getBatchProcessingIntervalMin(): int
getBlockedPackages(): java.util.List
getCosmoEndTime(): int
getCosmoStartTime(): int
getCosmoTransparencyLevel(): float
getEphemeralBufferDurationMin(): int
getFulfillmentModel(): FulfillmentModel
getMaxEphemeralBufferDurationMinutes(): float
getMaxRawAudioInFulfillmentSec(): int
getPhoneModel(): String
getProcessingMode(): ProcessingMode
getSnoozeUntil$java_…(): j$.time.Instant
hasProvideEventContextToggleEnabled(): boolean
hasProvidePersonContextToggleEnabled(): boolean
hasProvideRecallToggleEnabled(): boolean
isAltairSyncEnabled(): boolean
isAmbientAudioEnabled(): boolean
isAnalyticsEnabled(): boolean
isAutoSnoozeEnabled(): boolean
isBudsAudioEnabled(): boolean
isCameraEnabled(): boolean
isConnectToGlassEnabled(): boolean
isCosmoInsightsEnabled(): boolean
isDefineTermsEnabled(): boolean
isDeveloperModeEnabled(): boolean
isDeveloperModeUnlocked(): boolean
isDeviceControlEnabled(): boolean
isDraftSmsEnabled(): boolean
isGatekeeperEnabled(): boolean
isGlassesCameraEnabled(): boolean
isIntentExtractionEnabled(): boolean
isInvokeAgentQEnabled(): boolean
isLocationCaptureEnabled(): boolean
isMediaAudioEnabled(): boolean
isNotificationCaptureEnabled(): boolean
isProvideEventContextToggleEnabled(): boolean
isProvidePersonContextToggleEnabled(): boolean
isProvideRecallToggleEnabled(): boolean
isRawAudioGatekeeperEnabled(): boolean
isRealtimeProcessingEnabled(): kotlinx.coroutines.flow.StateFlow
isRecordMemoriesEnabled(): boolean
isScreenContextEnabled(): boolean
isScreenInteractionCaptureEnabled(): boolean
isScreenOn(): kotlinx.coroutines.flow.StateFlow
isSnoozed(): boolean
isStreamToWatchEnabled(): boolean
isSuggestAddTaskEnabled(): boolean
isSuggestCalendarEnabled(): boolean
isSuggestCallAgentEnabled(): boolean
isSuggestDeepResearchEnabled(): boolean
isSuggestDocsEnabled(): boolean
isSuggestGoogleSearchesEnabled(): boolean
isSuggestMailEnabled(): boolean
isSuggestMarinerEnabled(): boolean
isSuggestNavigationEnabled(): boolean
isSuggestPhotosEnabled(): boolean
isSuggestTimerEnabled(): boolean
isUpdateKeepEnabled(): boolean
isUseNanoappAudioEnabled(): boolean
isVoiceMatchEnabled(): boolean
isVoiceMatchEnrolled(): boolean
isWatchAudioEnabled(): boolean
```

`getCosmoSettingFlow()` returns a `StateFlow<CosmoSetting>`; the
boolean accessors above are convenience readers that snapshot the
current value.

### B.3 Write-side methods

50 write-side setters:

```
setAltairSyncEnabled(boolean)
setAmbientAudioEnabled(boolean)
setAnalyticsEnabled(boolean)
setAutoSnoozeEnabled(boolean)
setBatchProcessingIntervalMins(int)
setBlockedPackages(java.util.Collection)
setBudsAudioEnabled(boolean)
setCameraEnabled(boolean)
setConnectToGlassEnabled(boolean)
setCosmoEndTime$java_…(int)
setCosmoInsightsEnabled(boolean)
setCosmoStartTime$java_…(int)
setCosmoTransparencyLevel(float)
setDefineTermsEnabled(boolean)
setDeveloperModeEnabled(boolean)
setDeveloperModeUnlocked(boolean)
setDeviceControlEnabled(boolean)
setDraftSmsEnabled(boolean)
setEphemeralBufferDurationMin(int)
setFulfillmentModel(FulfillmentModel)
setGatekeeperEnabled(boolean)
setGlassesCameraEnabled(boolean)
setGlobalCosmoEnabled$java_…(boolean)
setIntentExtractionEnabled(boolean)
setInvokeAgentQEnabled(boolean)
setIsVoiceMatchEnrolled(boolean)
setLocationCaptureEnabled(boolean)
setMaxRawAudioInFulfillmentSec(int)
setMediaAudioEnabled(boolean)
setNotificationCaptureEnabled(boolean)
setProcessingMode(ProcessingMode)
setProvideEventContextEnabled(boolean)
setProvidePersonContextEnabled(boolean)
setProvideRecallEnabled(boolean)
setRawAudioGatekeeperEnabled(boolean)
setRecordMemoriesEnabled(boolean)
setScreenContextEnabled(boolean)
setScreenInteractionCaptureEnabled(boolean)
setSnoozeUntil$java_…(j$.time.Instant)
setStreamToWatchEnabled(boolean)
setSuggestAddTaskEnabled(boolean)
setSuggestCalendarEnabled(boolean)
setSuggestCallAgentEnabled(boolean)
setSuggestDeepResearchEnabled(boolean)
setSuggestDocsEnabled(boolean)
setSuggestGoogleSearchesEnabled(boolean)
setSuggestMailEnabled(boolean)
setSuggestMarinerEnabled(boolean)
setSuggestNavigationEnabled(boolean)
setSuggestPhotosEnabled(boolean)
setSuggestTimerEnabled(boolean)
setUpdateKeepEnabled(boolean)
setUseNanoappAudioEnabled(boolean)
setVoiceMatchEnabled(boolean)
setWatchAudioEnabled(boolean)
clearSnooze$java_…(): void
```

### B.4 Held state

```
private final dataStore: androidx.datastore.core.DataStore
private final _cosmoSettingState: kotlinx.coroutines.flow.MutableStateFlow
```

(Plus accessors `access$getDataStore$p`, `access$get_cosmoSettingState$p`,
`access$getInitialDisabledPackagesList$cp`, `access$getLogger$cp`.)

A `Flogger` `GoogleLogger` is held as a static via
`access$getLogger$cp()` (cross-ref `[telemetry §B]`). Tag:
`"COSMO-CosmoSettingStore"` `[telemetry §C]`.

---

## Section C — `CosmoConfiguration`

`com.google.research.air.cosmo.setting.CosmoConfiguration`
(`.super Ljava/lang/Object;`).

Constructor:

```
public <init>(CosmoSettingModule, com.google.research.air.cosmo.skills.CosmoSkillsFeature)
```

Fields (all `final`):

```
private final cosmoSettingModule: CosmoSettingModule
private final cosmoSkillsFeature: CosmoSkillsFeature
private final isExternal: boolean
private final isTeamfood: boolean
private final logger: GoogleLogger
```

Public methods (visibility: build-time feature flags; computed once at
construction):

```
final getLogger(): GoogleLogger
final isAddTaskSkillVisible(): boolean
final isAgentSkillVisible(): boolean
final isAltairSyncVisible(): boolean
final isAnalyticsSettingsVisible(): boolean
final isBudsAudioToggleVisible(): boolean
final isBufferDurationSettingsVisible(): boolean
final isCallAgentSkillVisible(): boolean
final isCosmoSkillsEnabled(): boolean
final isDisableXHoursEnabled(): boolean
final isDraftSmsToggleEnabled(): boolean
final isExternal(): boolean
final isGlobalCosmoSnoozeVisible(): boolean
final isInvokeAgentQVisible(): boolean
final isLocationCaptureToggleEnabled(): boolean
final isLocationSettingsVisible(): boolean
final isNavigationSkillVisible(): boolean
final isProcessingModeToggleVisible(): boolean
final isProvideEventContextToggleConfiguredForBuild(boolean): boolean
final isProvidePersonContextToggleConfiguredForBuild(boolean): boolean
final isProvideRecallContextToggleConfiguredForBuild(boolean): boolean
final isRawAudioGatekeeperToggleVisible(): boolean
final isRawAudioSettingsVisible(): boolean
final isRecordMemoriesToggleEnabled(): boolean
final isSuggestCallAgentToggleEnabled(): boolean
final isSuggestMailVisible(): boolean
final isTeamfood(): boolean
final isTransparencyLevelAdjustmentVisible(): boolean
```

`isExternal` and `isTeamfood` are populated from `CosmoSettingModule`'s
two abstract methods (verified `[smali setting/CosmoSettingModule.smali]`):

```
abstract isExternal(): boolean
abstract isTeamfood(): boolean
```

This is where the build-flavor split lives: `CosmoSettingModule` is
abstract; the concrete impl is `CosmoSettingTeamfoodModuleImpl`
(`[smali setting/CosmoSettingTeamfoodModuleImpl_Factory.smali]`),
which fixes the two flags at construction.

---

## Section D — `CosmoSettingModule` (Hilt provider)

`com.google.research.air.cosmo.setting.CosmoSettingModule.Companion`
provides the `DataStore<CosmoSetting>` via
`CosmoSettingModule_Companion_ProvideCosmoSettingDataStoreFactory`
(`[dagger §C.18]`).

The DataStore filename, verified by `^const-string` grep on
`[smali setting/CosmoSettingModule$Companion.smali]`:

```
"cosmo_setting.pb"
```

…so the DataStore is a single proto-DataStore at
`/data/data/com.google.research.air.cosmo/files/datastore/cosmo_setting.pb`
(per the AndroidX `DataStoreFactory.create(file)` convention applied to
the const-string in the factory body).

`CosmoSettingTeamfoodModule` and its `CosmoSettingTeamfoodModuleImpl`
provide the build-flavor flags. These are the two `@Binds` targets the
`CosmoSettingModule` exposes.

---

## Section E — `CosmoStateManager`

`com.google.research.air.cosmo.setting.CosmoStateManager`
(`.super Ljava/lang/Object;`).

Constructor:

```
public <init>(
    CosmoSettingStore,
    android.content.Context,
    kotlinx.coroutines.CoroutineScope,                    // backgroundScope
    kotlinx.coroutines.CoroutineScope,                    // uiScope
    CosmoConfiguration
)
```

Static state:

```
public static final BATTERY_LOW_THRESHOLD: float = 10.0f
public static final COSMO_STATE_UPDATE_ACTION: String =
        "com.google.research.air.cosmo.setting.STATE_UPDATE"
public static final COSMO_STATE_UPDATE_REQUEST_CODE: int = 0x3e9
private static final LOW_BATTERY_SNOOZE_DURATION: j$.time.Duration
```

Held fields:

```
private final alarmManager: AlarmManager
private final backgroundScope: CoroutineScope
private final context: Context
private final cosmoConfiguration: CosmoConfiguration
private final cosmoSettingStore: CosmoSettingStore
private final cosmoState: kotlinx.coroutines.flow.StateFlow
private _cosmoState: kotlinx.coroutines.flow.MutableStateFlow
private final uiScope: CoroutineScope
```

Public methods:

```
final clearSnooze(): void
final disableCosmo(): void
final enableCosmo(): void
final getCosmoState(): StateFlow
final handleBatteryLevelChange(Context, float): void
final setCosmoEndTime(int): void
final setCosmoStartTime(int): void
final setSnoozeUntil(j$.time.Instant): void
final updateAndScheduleCosmoState$java_…(): void
```

The receiver `CosmoStateUpdateReceiver` (`[runtime §B.3]`) handles
`COSMO_STATE_UPDATE_ACTION`; `setCosmoStartTime`/`setCosmoEndTime`
write through `CosmoSettingStore` and re-schedule the alarm, and
`handleBatteryLevelChange` snoozes Cosmo for `LOW_BATTERY_SNOOZE_DURATION`
when the battery level falls below `BATTERY_LOW_THRESHOLD = 10.0f`.

`CosmoApp.onCreate` registers a `BatteryStateReceiver` for
`android.intent.action.BATTERY_CHANGED` (`[runtime §A]`); the
receiver's `getBatteryLevelFlow()` `StateFlow` feeds
`CosmoStateManager.handleBatteryLevelChange`.

---

## Section F — Defaults

The `CosmoSetting.DEFAULT_INSTANCE` singleton is the proto's zero
value (booleans default to `false`, ints to `0`, strings to `""`,
lists empty). No `set<Field>()` invokes appear in the
`CosmoSettingModule.Companion`'s data-store factory body that would
override this at construction (the body uses
`PreferenceDataStoreFactory.create(file)` against `cosmo_setting.pb`
and relies on the proto's natural defaults).

`access$getInitialDisabledPackagesList$cp()` on `CosmoSettingStore`
returns the initial value for `BLOCKED_PACKAGES`. The list literal is
held as a static field.

---

## Section G — Setting → consumer matrix

For every captor, runner, or handler invoked by name on
`CosmoSettingStore` (`grep -rln 'CosmoSettingStore;->\|CosmoSetting;->'`),
the consumer-of-record is documented.

### G.1 Captors

| Setting accessor | Consumed by | Doc ref |
|---|---|---|
| `getCosmoSettingFlow()` | `CosmoController`, `SODAAmbientAudioCaptor`, `SODABudsAudioCaptor`, `SODADeviceAudioCaptor`, `SODAWatchAudioCaptor`, `CosmoGlassManager`, `LocationCaptor`, `NotificationCaptureManager`, `PeriodicScreenCaptorImpl`, `ScreenInteractionCaptorImpl`, `IntentExtractorImpl` | `[audio §D, §E.1, §F.1, §F.2, §G]`, `[text §A.2, §B.2, §C.2, §E.2, §F.1]`, `[image §B.1]` |
| `isUseNanoappAudioEnabled()` | `SODAAmbientAudioCaptor.startWork` (selects AOC vs mic) | `[audio §D]` |
| `isRawAudioGatekeeperEnabled()` | `SODAAmbientAudioCaptor` | `[audio §D]` |
| `isRealtimeProcessingEnabled()` | `SODAAmbientAudioCaptor` (StateFlow) | `[audio §D]` |
| `isAmbientAudioEnabled()` | `CosmoController.isAmbientAudioCaptureOn` resolution; `CosmoGlassManager` | `[text §F.1]`, `[audio §G]` |
| `isBudsAudioEnabled()` | `SODABudsAudioCaptor` | `[audio §E.1]` |
| `isWatchAudioEnabled()` | `SODAWatchAudioCaptor`, `WearableAudioStreamService` | `[audio §F.2, §F.3]` |
| `isMediaAudioEnabled()` | `SODADeviceAudioCaptor` | `[audio §F.1]` |
| `isCameraEnabled()` | `CameraViewComposable` | `[image §A.1]` |
| `isGlassesCameraEnabled()` | `CosmoGlassManager.setupGlassCameraCapture` | `[image §B.1]` |
| `isConnectToGlassEnabled()` | `CosmoGlassManager.bindGlassCaptureService` | `[audio §G]` |
| `isLocationCaptureEnabled()` | `LocationCaptor` | `[runtime §A]` |
| `isNotificationCaptureEnabled()` | `NotificationCaptureManager` | `[text §A.2]` |
| `isScreenContextEnabled()` | `PeriodicScreenCaptorImpl` | `[text §B.2]` |
| `isScreenInteractionCaptureEnabled()` | `ScreenInteractionCaptorImpl` | `[text §C.2]` |
| `isIntentExtractionEnabled()` | `IntentExtractorImpl.start` | `[text §E.2]` |
| `getEphemeralBufferDurationMin()` / `getMaxEphemeralBufferDurationMinutes()` | `InMemoryEphemeralBuffer.maxEntryAgeMillis` updaters | `[text §D.2]` |
| `getMaxRawAudioInFulfillmentSec()` | `FulfillmentRunner.getRecentRawAudioContent` | `[llm §E.4]` |

### G.2 Gatekeeper

| Setting accessor | Consumed by | Doc ref |
|---|---|---|
| `isGatekeeperEnabled()` | `Gatekeeper.processInput` | `[smali gatekeeping/Gatekeeper.smali]` |
| `isRawAudioGatekeeperEnabled()` | `Gatekeeper.processRawAudioInput` | `[smali gatekeeping/Gatekeeper$processRawAudioInput$1.smali]` |

### G.3 Fulfillment

| Setting accessor | Consumed by | Doc ref |
|---|---|---|
| `getProcessingMode()` | `FulfillmentOrchestratorImpl.handleBatchRun` | `[llm §E.2]` |
| `getFulfillmentModel()` | `FulfillmentRunner.fulfill-yxL6bBk` (selects LLM slot) | `[llm §E.4]` |
| `isInvokeAgentQEnabled()` | `FulfillmentActionHandlerImpl.executeInvokeAgentQ` | `[llm §F.2]` |
| `isDraftSmsEnabled()` | `FulfillmentActionHandlerImpl.executeDraftSms` | `[llm §F.2]` |
| `isStreamToWatchEnabled()` | `WearableActionSynchronizer` | `[smali lib/wearable/WearableActionSynchronizer.smali]` |
| `isAltairSyncEnabled()` | `AltairActionSynchronizer` | `[smali altair/AltairActionSynchronizer.smali]` |
| `isVoiceMatchEnabled()` | `FulfillmentRunner.fulfill-yxL6bBk` (passed as `voice_match_enabled` to `fulfillment_prompt.jinja2`) | `[llm §D.3]` |
| `isAnalyticsEnabled()` | `FirebaseAnalyticsWrapperImpl.logEvent` (gates emit) | `[telemetry §A]` |

### G.4 Suggestion gates (per-action skill toggles)

```
isSuggestCalendarEnabled         → Skill: create_calendar_event
isSuggestPhotosEnabled           → Skill: query_photos
isSuggestDocsEnabled             → Skill: create_doc
isSuggestTimerEnabled            → Skill: add_timer
isSuggestMarinerEnabled          → Skill: browsing_agent
isSuggestCallAgentEnabled        → Skill: call_agent
isSuggestDeepResearchEnabled     → Skill: deep_research
isSuggestGoogleSearchesEnabled   → Skill: web_search
isSuggestNavigationEnabled       → Skill: navigation
isSuggestAddTaskEnabled          → Skill: add_task
isSuggestMailEnabled             → Skill: draft_email
isProvidePersonContextEnabled    → Skill: provide_person_context
isProvideEventContextEnabled     → Skill: provide_event_context
isProvideRecallEnabled           → Skill: provide_recall
isCosmoInsightsEnabled           → Skill: provide_insight
isDefineTermsEnabled             → Skill: define_term
isRecordMemoriesEnabled          → Skill: add_to_memory
isUpdateKeepEnabled              → Keep notes integration
isDeviceControlEnabled           → Active-data-plan device control
isPlayMusicEnabled (set via setter)→ Skill: play_music
```

(The mapping is established by the const-string grep on
`[smali skills/generation/PromptBuilder.smali]` `[llm §C.3]` showing
the verbatim per-skill descriptions, paired with the matching
`is*Enabled` setting accessor.)

### G.5 Cosmo state

| Setting accessor | Consumed by | Doc ref |
|---|---|---|
| `getCosmoStartTime()` / `getCosmoEndTime()` | `CosmoStateManager.updateAndScheduleCosmoState` | §E |
| `isAutoSnoozeEnabled()` | `CosmoStateManager.handleBatteryLevelChange` | §E |
| `getSnoozeUntil$java_…` / `setSnoozeUntil$java_…` / `clearSnooze$java_…` | `CosmoStateManager` | §E |
| `getCosmoTransparencyLevel()` | `LockscreenView` (overlay opacity) | `[ui §B]` |
| `getBatchProcessingIntervalMin()` | `BatchCoordinator` | `[smali batch/BatchCoordinator.smali]` |
| `isBatchModeEnabled` (proto field 0x1f) | `BatchCoordinator` | `[smali batch/]` |
| `isOnDeviceTranscriptionEnabled` (proto field 0x2a) | `MainRecognitionService` chain | `[smali voiceassistant/]` |

### G.6 Voice-match

| Setting accessor | Consumed by |
|---|---|
| `isVoiceMatchEnrolled()` / `setIsVoiceMatchEnrolled(boolean)` | `VoiceMatchViewComposable`, `SodaController.enrollVoiceMatch` `[audio §A.1]` |

---

## Section H — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/setting/`:
  `CosmoSetting.smali`, `CosmoSettingStore.smali`,
  `CosmoConfiguration.smali`, `CosmoSettingModule.smali`,
  `CosmoSettingModule$Companion.smali`,
  `CosmoSettingTeamfoodModule.smali`,
  `CosmoSettingTeamfoodModuleImpl.smali`, `CosmoStateManager.smali`,
  `CosmoStateUpdateReceiver.smali`, `Hilt_CosmoStateUpdateReceiver.smali`,
  `ProcessingMode.smali`, `FulfillmentModel.smali`.
- `[asset assets/cosmo_setting.pb]` — the on-device DataStore filename.
- Cross-references: `[audio §D, §E.1, §F.1, §F.2, §G]`,
  `[image §A.1, §B.1]`, `[text §A.2, §B.2, §C.2, §E.2, §F.1]`,
  `[llm §C.3, §D.3, §E.2, §E.4, §F.2, §H.4]`,
  `[runtime §A, §B.3]`, `[dagger §C.18, §C.16]`,
  `[telemetry §A, §C]`.

This document is static analysis only.
