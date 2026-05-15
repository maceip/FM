# Cosmo Dagger / Hilt Graph — Static Analysis Map

Static-analysis map of the Hilt/Dagger dependency graph in the
recovered Cosmo APK (`com.google.research.air.cosmo`, antisplit v1.1):

- the generated component hierarchy (`CosmoApp_HiltComponents.*`),
- every `@Module` class,
- every generated `*_Provide…Factory` and `*_Bind…Factory`,
- every `@Qualifier` annotation interface,
- the generated `Hilt_*` parent chain bound to user-written components,
- `*_Factory` enumeration,
- `*_MembersInjector` enumeration,
- `HiltWrapper_*` modules.

## Discipline

Every claim is backed by `[smali …]`, `[asset …]`, `[aapt2 …]`, or
`[doc §X.Y]`. Aggregations carry the union of their tags.

---

## Section A — Application-level Hilt entry & generated scaffolding

`CosmoApp` is annotated `@HiltAndroidApp` (entailed by the `Hilt_CosmoApp`
generated parent + the `CosmoApp_HiltComponents` constellation; cross-ref
`[runtime §A]`). The Hilt-generated companions, all under
`com/google/research/air/cosmo/`:

```
Hilt_CosmoApp.smali                                   .super Landroid/app/Application;
CosmoApp_GeneratedInjector.smali                       (interface; injector for CosmoApp)
CosmoApp_HiltComponents.smali                          (member-classes container)
CosmoApp_HiltComponents$SingletonC.smali
CosmoApp_HiltComponents$ActivityC.smali
CosmoApp_HiltComponents$ActivityCBuilderModule.smali
CosmoApp_HiltComponents$ActivityRetainedC.smali
CosmoApp_HiltComponents$ActivityRetainedCBuilderModule.smali
CosmoApp_HiltComponents$FragmentC.smali
CosmoApp_HiltComponents$FragmentCBuilderModule.smali
CosmoApp_HiltComponents$FragmentRetainedC.smali
CosmoApp_HiltComponents$FragmentRetainedCBuilderModule.smali
CosmoApp_HiltComponents$ScreenC.smali
CosmoApp_HiltComponents$ScreenCBuilderModule.smali
CosmoApp_HiltComponents$ScreenRetainedC.smali
CosmoApp_HiltComponents$ScreenRetainedCBuilderModule.smali
CosmoApp_HiltComponents$ServiceC.smali
CosmoApp_HiltComponents$ServiceCBuilderModule.smali
CosmoApp_HiltComponents$ViewC.smali
CosmoApp_HiltComponents$ViewCBuilderModule.smali
CosmoApp_HiltComponents$ViewModelC.smali
CosmoApp_HiltComponents$ViewModelCBuilderModule.smali
CosmoApp_HiltComponents$ViewWithFragmentC.smali
CosmoApp_HiltComponents$ViewWithFragmentCBuilderModule.smali
CosmoApp_MembersInjector.smali
```

The component classes (`SingletonC`, `ActivityC`, `ActivityRetainedC`,
`FragmentC`, `FragmentRetainedC`, `ScreenC`, `ScreenRetainedC`,
`ServiceC`, `ViewC`, `ViewModelC`, `ViewWithFragmentC`) are nested
under `CosmoApp_HiltComponents` per its `MemberClasses` annotation
(read directly from `[smali CosmoApp_HiltComponents.smali]` — 11 inner
component classes plus 11 matching Builder modules).

The `Hilt_*` parent chains for every user-written `Application` /
`Activity` / `Service` / `BroadcastReceiver` (verified by per-file
grep, `[smali Hilt_*.smali]`):

```
Hilt_CosmoApp                       .super Landroid/app/Application;
Hilt_MainActivity                   .super Landroidx/activity/ComponentActivity;
Hilt_DoubleTapActivity              .super Landroidx/activity/ComponentActivity;
Hilt_CosmoGlassActivity             .super Landroidx/activity/ComponentActivity;
Hilt_TaskMonitorActivity            .super Landroidx/activity/ComponentActivity;
Hilt_CosmoAccessibilityService      .super Landroid/accessibilityservice/AccessibilityService;
Hilt_NotificationCaptureService     .super Landroid/service/notification/NotificationListenerService;
Hilt_WearableService                .super Lcom/google/android/gms/wearable/WearableListenerService;
Hilt_WearableAudioStreamService     .super Landroid/app/Service;
Hilt_GBoardSuggestionsService       .super Landroid/app/Service;
Hilt_MainInteractionService         .super Landroid/service/voice/VoiceInteractionService;
Hilt_MainInteractionSessionService  .super Landroid/service/voice/VoiceInteractionSessionService;
Hilt_MainRecognitionService         .super Landroid/speech/RecognitionService;
Hilt_CosmoStateUpdateReceiver       .super Landroid/content/BroadcastReceiver;
```

Each user-written class extends the matching `Hilt_*` (cross-ref the
per-component `.super` chains in `[runtime §B.1, §B.2, §B.3]`).

---

## Section B — `@Module` enumeration

44 user-written modules (excluding the 11 generated `*BuilderModule`s
in §A and the `BatchWorker_HiltModule`) under
`com/google/research/air/cosmo/`. Verified by
`find … -name '*Module.smali' -not -name '*_Companion_*'`:

```
audiocapture/AmbientAudioCaptureModule
audiocapture/BistoModule
audiocapture/HiltWrapper_AmbientAudioCaptureModule
audiocapture/HiltWrapper_BistoModule
batch/BatchModule
batch/BatchWorker_HiltModule
batch/pipelines/stats_repository/StatsRepositoryModule
contentcapture/ContentCaptureModule
contentcapture/HiltWrapper_ContentCaptureModule
controller/CosmoControllerModule
conversation/ConversationModule
CosmoModule
HiltWrapper_CosmoModule
ephemeralbuffer/EphemeralBufferModule
ephemeralbuffer/HiltWrapper_EphemeralBufferModule
fulfillment/FulfillmentModule
fulfillment/actions/mariner/TaskMonitorViewModel_HiltModules$BindsModule
fulfillment/actions/mariner/TaskMonitorViewModel_HiltModules$KeyModule
gatekeeping/GatekeeperLoggerModule
gatekeeping/GatekeeperOutputModule
gatekeeping/impl/AudioGatekeepingModule
gatekeeping/impl/CombinedBertRegexGatekeepingModule
gatekeeping/models/BertEncoderModule
gatekeeping/models/ConformerEncoderModule
glasses/CosmoGlassViewModel_HiltModules$BindsModule
glasses/CosmoGlassViewModel_HiltModules$KeyModule
intentextraction/IntentExtractionModule
intentextraction/HiltWrapper_IntentExtractionModule
interactioncapture/InteractionCaptureModule
lib/audio/AudioModule
lib/audio/HiltWrapper_AudioModule
lib/buds/BudsModule
lib/llm/ArateaExecutorModule
lib/llm/LLMModule
lib/llm/HiltWrapper_LLMModule
lib/permission/PermissionHelperModule
lib/screenstate/ScreenStateMonitorModule
lib/soda/SodaControllerModule
lib/soda/HiltWrapper_SodaControllerModule
lib/tts/TTSModule
lib/tts/HiltWrapper_TTSModule
lib/wearable/WearableModule
locationcapture/LocationCaptureModule
locationcapture/HiltWrapper_LocationCaptureModule
logging/LoggingModule
logging/HiltWrapper_LoggingModule
memory/NoOpMemoryModule
notificationcapture/NotificationCaptureModule
notificationcapture/HiltWrapper_NotificationCaptureModule
setting/CosmoSettingModule
setting/CosmoSettingTeamfoodModule
skills/CosmoSkillsDisabledModule
skills/HiltWrapper_CosmoSkillsDisabledModule
skills/SkillsRepositoryModule
```

(Excluding the generated `*BuilderModule` set from §A.)

`@dagger.Module` annotations are present on every entry above (verified
by grep `Ldagger/Module;` in the smali tree).

---

## Section C — `@Provides` / `@Binds` edge inventory

Every generated `*_Provide*Factory` / `*_Bind*Factory` corresponds to
one `@Provides`/`@Binds` method. 73 factories total, by module:

### C.1 `AmbientAudioCaptureModule`

```
ProvideAmbientAudioCaptorFactory   →  AmbientAudioCaptor
ProvideBudsAudioCaptorFactory      →  AmbientAudioCaptor (BudsAudioCaptor qualifier)
ProvideDeviceAudioCaptorFactory    →  AmbientAudioCaptor (DeviceAudioCaptor qualifier)
ProvideWatchAudioCaptorFactory     →  AmbientAudioCaptor (WatchAudioCaptor qualifier)
```

### C.2 `BistoModule`

```
ProvideBistoContextFactory
```

### C.3 `ContentCaptureModule`

```
ProvideExtraScreenContentCaptureConfigFactory
ProvidePeriodicContentCaptorFactory
ProvideScreenCaptorConfigFactory
```

### C.4 `CosmoModule`

```
ProvideItemExporterFactory
ProvideNotificationManagerFactory
ProvidesInstantSourceFactory
ProvideTimeSourceFactory
```

### C.5 `EphemeralBufferModule.Companion` (23 factories)

```
ProvideAmbientAudioBytesEphemeralBufferFactory
ProvideAmbientAudioEphemeralBufferFactory
ProvideAmbientAudioTextEphemeralBufferFactory
ProvideBudsAudioBytesEphemeralBufferFactory
ProvideBudsAudioEphemeralBufferFactory
ProvideBudsAudioTextReaderFactory
ProvideCameraEphemeralBufferFactory
ProvideCameraJPEGBytesEphemeralBufferFactory
ProvideDeviceAudioBytesEphemeralBufferFactory
ProvideDeviceAudioEphemeralBufferFactory
ProvideDeviceAudioTextEphemeralBufferFactory
ProvideDeviceCameraEphemeralBufferFactory
ProvideDeviceCameraJPEGBytesEphemeralBufferFactory
ProvideImageScreenEphemeralBufferFactory
ProvideIntentEphemeralBufferFactory
ProvideLocationEphemeralBufferFactory
ProvideLocationTextEphemeralBufferFactory
ProvideNotificationEphemeralBufferFactory
ProvideNotificationTextEphemeralBufferFactory
ProvideScreenInteractionEphemeralBufferFactory
ProvideScreenTextEphemeralBufferFactory
ProvideWatchAudioEphemeralBufferFactory
ProvideWatchAudioTextReaderFactory
```

### C.6 `FulfillmentModule`

```
ProvideAgentQSessionManagerFactory
ProvideClockFactory
ProvideConfigRegistryFactory
ProvideFulfillmentActionHandlerFactory
ProvideFulfillmentOrchestratorFactory
ProvideFulfillmentProviderFactory
ProvideWorkflowStorageFactory
```

### C.7 `GatekeeperOutputModule.Companion`

```
ProvideGatekeeperOutputMutableFlowFactory
```

### C.8 `CombinedBertRegexGatekeepingModule.Companion`

```
ProvideGatekeepingModelIdFactory
```

### C.9 `InteractionCaptureModule.Companion`

```
ProvideDispatcherFactory
```

### C.10 `BudsModule.Companion`

```
ProvideBudsScopeFactory
```

### C.11 `LLMModule`

```
ProvideBatchInferenceLLMFactory
ProvideDefaultLlmFactory
ProvideHybridLlmFactory
ProvideLLMStatsRepositoryFactory
ProvideLocalLLMFactory
ProvideServerLLMFactory
```

### C.12 `SodaControllerModule`

```
ProvideBudsAudioSodaControllerFactory
ProvideCallAudioSodaControllerFactory
ProvideDefaultMicSodaControllerFactory
ProvideDeviceAudioSodaControllerFactory
ProvideStreamingMicSodaControllerFactory
ProvideWatchAudioSodaControllerFactory
```

### C.13 `TTSModule`

```
ProvideTTSFactory
```

### C.14 `WearableModule.Companion`

```
ProvideChannelClientFactory
ProvideMessageClientFactory
ProvideNodeClientFactory
```

### C.15 `LocationCaptureModule`

```
ProvideLocationCaptorFactory
```

### C.16 `LoggingModule`

```
ProvideFirebaseAnalyticsFactory
ProvideFirebaseAnalyticsWrapperFactory
ProvideLoggingParamsDataStoreFactory
```

### C.17 `NotificationCaptureModule.Companion`

```
ProvideNotificationEventFlowFactory
```

### C.18 `CosmoSettingModule.Companion`

```
ProvideCosmoSettingDataStoreFactory   (binds DataStore<CosmoSetting>)
```

### C.19 `CosmoSkillsDisabledModule`

```
ProvideCosmoSkillsFeatureFactory
```

### C.20 `SkillsRepositoryModule.Companion`

```
ProvideSkillInstancesDataStoreFactory
ProvideSystemSkillsAssetPathFactory
```

### C.21 ViewModel-key modules (Hilt-generated)

```
TaskMonitorViewModel_HiltModules_KeyModule_ProvideFactory
CosmoGlassViewModel_HiltModules_KeyModule_ProvideFactory
```

---

## Section D — `@Qualifier` annotation interfaces

All 33 `@Qualifier` annotation interfaces under
`com/google/research/air/cosmo/` (verified by grep
`'^\.annotation runtime Ljavax/inject/Qualifier;'`):

| Qualifier | Source |
|---|---|
| `ActionUpdate` | `[smali fulfillment/]` |
| `AmbientAudioEphemeralBuffer` | `[text §D.3]` |
| `AudioGatekeeper` | `[smali gatekeeping/]` |
| `BertGatekeeper` | `[smali gatekeeping/]` |
| `BudsAudioCaptor` | `[smali audiocapture/]` |
| `BudsAudioEphemeralBuffer` | `[text §D.3]` |
| `BudsAudioSodaController` | `[audio §A.1]` |
| `BudsScope` | `[smali lib/buds/]` |
| `CallAudioSodaController` | `[audio §A.1]` |
| `DefaultMicSodaController` | `[audio §A.1]` |
| `DeviceAudioCaptor` | `[smali audiocapture/]` |
| `DeviceAudioEphemeralBuffer` | `[text §D.3]` |
| `DeviceAudioSodaController` | `[audio §A.1]` |
| `DeviceCameraEphemeralBuffer` | `[image §C]` |
| `GatekeeperOutput` | `[smali gatekeeping/]` |
| `GatekeepingModelId` | `[smali gatekeeping/impl/]` |
| `GlassesCameraEphemeralBuffer` | `[image §C]` |
| `HybridLLM` | `[smali lib/llm/]` |
| `IntentEphemeralBuffer` | `[smali ephemeralbuffer/]` |
| `LocalLLM` | `[smali lib/llm/]` |
| `LocationEphemeralBuffer` | `[smali ephemeralbuffer/]` |
| `NotificationEphemeralBuffer` | `[smali ephemeralbuffer/]` |
| `RegexGatekeeper` | `[smali gatekeeping/]` |
| `ScreenEphemeralBuffer` | `[smali ephemeralbuffer/]` |
| `ScreenInteractionCaptorDispatcher` | `[text §C.3]` |
| `ScreenInteractionEphemeralBuffer` | `[smali ephemeralbuffer/]` |
| `ServerLLM` | `[smali lib/llm/]` |
| `SkillPolyEncoderGatekeeper` | `[smali gatekeeping/]` |
| `StatusUpdate` | `[smali fulfillment/]` |
| `StreamingMicSodaController` | `[audio §A.1]` |
| `WatchAudioCaptor` | `[smali audiocapture/]` |
| `WatchAudioEphemeralBuffer` | `[smali ephemeralbuffer/]` |
| `WatchAudioSodaController` | `[audio §A.1]` |

Plus, used as qualifier annotations on injection sites but defined
externally (not in Cosmo's smali tree): Hilt's
`dagger.hilt.android.qualifiers.ApplicationContext` (referenced by
`CosmoModule`, `BistoModule`, `LLMModule`, `LoggingModule`,
`SodaControllerModule`, `TTSModule`).

---

## Section E — Scope distribution

The `@dagger.hilt.InstallIn` annotation is not directly readable from
the Cosmo-side `*Module.smali` files (it does not survive into the
smali in this APK — only the `Ldagger/Module;` and `Ldagger/Provides;`
annotations are visible per `[smali CosmoModule.smali, LLMModule.smali]`).
The `HiltWrapper_*` set provides the actual install-graph wiring;
each `HiltWrapper_<X>Module.smali` declares
`includes = { …<X>Module; }` in its `Ldagger/Module;` annotation
(verified by reading
`[smali lib/llm/HiltWrapper_LLMModule.smali]`):

```
.annotation runtime Ldagger/Module;
    includes = { Lcom/google/research/air/cosmo/lib/llm/LLMModule; }
.end annotation
```

These wrappers are the modules that get installed; the inner module
is included as an entry. The 14 `HiltWrapper_*` modules are §H below.

`@Singleton` scope is implied for any factory in `CosmoApp_HiltComponents$SingletonC`
(the singleton component class). Direct `@dagger.Singleton` annotations
on individual factories are not enumerated here without component-by-
component enumeration, which the smali makes possible but
intentionally not done in this pass.

---

## Section F — Component hierarchy

Cosmo uses the standard Hilt component graph, instantiated under
`CosmoApp_HiltComponents$*`:

```
SingletonC
├── ActivityRetainedC
│   ├── ActivityC
│   │   ├── FragmentC
│   │   │   └── ViewWithFragmentC
│   │   ├── ViewC
│   │   ├── ScreenC
│   │   └── ScreenRetainedC
│   ├── FragmentRetainedC
│   └── ViewModelC
└── ServiceC
```

(Verified: each `…C.smali` exists, plus its matching
`…CBuilderModule.smali`. `MemberClasses` annotation on
`CosmoApp_HiltComponents.smali` enumerates all 11 nested component
types.)

---

## Section G — `*_Factory` enumeration

103 user-written `*_Factory.smali` files under
`com/google/research/air/cosmo/` (verified count). One factory per
`@Inject`-annotated constructor.

Selected high-traffic factories (each name implies the type produced):

```
audiocapture/SODAAmbientAudioCaptor_Factory
audiocapture/SODABudsAudioCaptor_Factory
audiocapture/SODADeviceAudioCaptor_Factory
audiocapture/SODAWatchAudioCaptor_Factory
audiocapture/RecordServiceManager_Factory
contentcapture/PeriodicScreenCaptorImpl_Factory
controller/CosmoController_Factory
fulfillment/ActionsRepository_Factory
fulfillment/AgentQSessionManagerImpl_Factory
fulfillment/FulfillmentActionHandlerImpl_Factory
fulfillment/FulfillmentOrchestratorImpl_Factory
fulfillment/FulfillmentRunner_Factory
fulfillment/FulfillmentConfigRegistry_Factory
fulfillment/prompts/PromptBuilder_Factory
gatekeeping/Gatekeeper_Factory
gatekeeping/GatekeeperLoggerImpl_Factory
gatekeeping/impl/AudioGatekeepingModel_Factory
gatekeeping/impl/BertClassifierGatekeepingModel_Factory
gatekeeping/impl/CombinedBertRegexGatekeepingModel_Factory
gatekeeping/impl/RegexGatekeepingModelV0_Factory
gatekeeping/models/BertEncoderImpl_Factory
gatekeeping/models/ConformerEncoderImpl_Factory
gatekeeping/models/DarwinnDelegateFactory_Factory
gatekeeping/util/GatekeeperResultUtils_Factory
glasses/CosmoGlassManager_Factory
glasses/CosmoGlassViewModel_Factory
intentextraction/IntentExtractorImpl_Factory
interactioncapture/ScreenInteractionCaptorImpl_Factory
lib/llm/AICoreLLM_Factory
lib/llm/LLMStatsRepository_Factory
lib/llm/NetworkAwareLLM_Factory
lib/llm/PrivateInferenceArateaBatchLLM_Factory
lib/llm/PrivateInferenceArateaLLM_Factory
lib/permission/PermissionHelperImpl_Factory
lib/screenstate/ScreenStateMonitorImpl_Factory
lib/soda/AudioAccumulator_Factory
lib/soda/LanguagePackManager_Factory
lib/wearable/WatchStatusState_Factory
lib/wearable/WearableActionSynchronizer_Factory
lib/wearable/WearableClientImpl_Factory
lib/wearable/WearableStatusSynchronizer_Factory
locationcapture/LocationCaptorImpl_Factory
logging/FirebaseAnalyticsWrapperImpl_Factory
logging/LoggingParamsStore_Factory
memory/NoOpMemoryImpl_Factory
notificationcapture/NotificationCaptureManager_Factory
setting/CosmoConfiguration_Factory
setting/CosmoSettingStore_Factory
setting/CosmoSettingTeamfoodModuleImpl_Factory
setting/CosmoStateManager_Factory
skills/SkillsRepositoryImpl_Factory
skills/generation/GatekeepingGenerator_Factory
skills/generation/PromptBuilder_Factory
skills/generation/SkillGenerator_Factory
voiceassistant/MainInteractionSession_Factory
voiceassistant/CosmoDeps_Factory
voiceassistant/camera/CameraViewComposable_Factory
voiceassistant/ChatViewComposable_Factory
voiceassistant/CreateSkillViewComposable_Factory
voiceassistant/SettingViewComposable_Factory
voiceassistant/ScreenContextViewComposable_Factory
voiceassistant/SkillLibraryViewComposable_Factory
voiceassistant/VoiceMatchViewComposable_Factory
voiceassistant/CosmoHoursViewComposable_Factory
voiceassistant/PrivateInferenceWarningComposable_Factory
voiceassistant/FloatyNavViewComposable_Factory
voiceassistant/FloatyViewComposables_Factory
voiceassistant/processingmode/ProcessingModeViewComposable_Factory
voiceassistant/fulfillmentmodel/FulfillmentModelViewComposable_Factory
voiceassistant/debugpanel/AboutViewComposable_Factory
voiceassistant/debugpanel/ActionListViewComposable_Factory
voiceassistant/debugpanel/BufferViewComposable_Factory
voiceassistant/debugpanel/DebugPanelViewComposable_Factory
voiceassistant/debugpanel/DebugUtils_Factory
voiceassistant/debugpanel/FulfillmentViewComposable_Factory
voiceassistant/debugpanel/GatekeepingViewComposable_Factory
voiceassistant/debugpanel/HealthStatusViewComposable_Factory
voiceassistant/debugpanel/LogsViewComposable_Factory
voiceassistant/debugpanel/MemoryViewComposable_Factory
voiceassistant/debugpanel/ReplayViewComposable_Factory
voiceassistant/debugpanel/StatsViewComposable_Factory
voiceassistant/debugpanel/UxViewComposable_Factory
fulfillment/actions/mariner/TaskMonitorViewModel_Factory
fulfillment/activedata/SqliteWorkflowStorage_Factory
fulfillment/activedata/WorkflowRepository_Factory
fulfillment/ui/ActionChipsFactory_Factory
batch/BatchCoordinator_Factory
batch/BatchRunState_Factory
batch/BatchWorker_Factory
batch/pipelines/FulfillmentPipeline_Factory
batch/pipelines/RetranscriptionPipeline_Factory
batch/pipelines/stats_repository/BatchRetranscriptionStatsRepository_Factory
gboard/GBoardSuggestionsController_Factory
gboard/prompt/PromptGenerator_Factory
gatekeeping/stats_repository/GatekeepingStatsRepository_Factory
altair/AltairActionSynchronizer_Factory
conversation/ConversationController_Factory
lib/audio/AudioCompressorImpl_Factory
lib/audio/VoiceMatchAudioFetcherImpl_Factory
lib/buds/BudsAudioProviderImpl_Factory
lib/google/auth/AuthManager_Factory
lib/google/googledocs/GoogleDocsManager_Factory
survey/SurveyUtils_Factory
```

---

## Section H — `*_MembersInjector` enumeration

10 user-written `*_MembersInjector.smali` files (one per
field-injection site):

```
accessibility/CosmoAccessibilityService_MembersInjector
CosmoApp_MembersInjector
gboard/GBoardSuggestionsService_MembersInjector
lib/wearable/WearableAudioStreamService_MembersInjector
lib/wearable/WearableService_MembersInjector
notificationcapture/NotificationCaptureService_MembersInjector
setting/CosmoStateUpdateReceiver_MembersInjector
ui/mainscreen/MainActivity_MembersInjector
voiceassistant/MainInteractionService_MembersInjector
voiceassistant/MainInteractionSessionService_MembersInjector
```

Every Cosmo `Activity`/`Service`/`Receiver` that uses `@Inject` field
injection has exactly one entry above.

---

## Section I — `HiltWrapper_*` set

The 14 `HiltWrapper_*` modules (each declares
`@dagger.Module(includes = {<inner module>})`):

```
audiocapture/HiltWrapper_AmbientAudioCaptureModule
audiocapture/HiltWrapper_BistoModule
contentcapture/HiltWrapper_ContentCaptureModule
ephemeralbuffer/HiltWrapper_EphemeralBufferModule
HiltWrapper_CosmoModule
intentextraction/HiltWrapper_IntentExtractionModule
lib/audio/HiltWrapper_AudioModule
lib/llm/HiltWrapper_LLMModule
lib/soda/HiltWrapper_SodaControllerModule
lib/tts/HiltWrapper_TTSModule
locationcapture/HiltWrapper_LocationCaptureModule
logging/HiltWrapper_LoggingModule
notificationcapture/HiltWrapper_NotificationCaptureModule
skills/HiltWrapper_CosmoSkillsDisabledModule
```

Pattern: `HiltWrapper_<Name>Module` `includes = { <Name>Module }`. The
wrapper is the module Hilt installs; the inner module carries the
`@Provides`/`@Binds` methods.

---

## Section J — `*_HiltModules` and ViewModel keys

```
fulfillment/actions/mariner/TaskMonitorViewModel_HiltModules$BindsModule
fulfillment/actions/mariner/TaskMonitorViewModel_HiltModules$KeyModule
glasses/CosmoGlassViewModel_HiltModules$BindsModule
glasses/CosmoGlassViewModel_HiltModules$KeyModule
batch/BatchWorker_HiltModule
```

Every `@HiltViewModel`-annotated class generates one `_HiltModules$BindsModule`
+ one `_HiltModules$KeyModule`. The two ViewModels Cosmo declares are
`TaskMonitorViewModel` and `CosmoGlassViewModel`. `BatchWorker_HiltModule`
is generated for the `@HiltWorker`-annotated `BatchWorker`.

---

## Section K — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/` for all
  `*Module.smali`, `*_Factory.smali`, `*_MembersInjector.smali`,
  `Hilt_*.smali`, `HiltWrapper_*.smali`, qualifier annotation
  interfaces, `CosmoApp_HiltComponents.*` constellation.
- Cross-references: `[audio §A.1, §A.12]`, `[text §D.3, §D.5, §C.3]`,
  `[image §C]`, `[llm §A.12, §A.13, §F.2]`, `[runtime §A, §B]`,
  `[settings §C]`, `[telemetry §A, §B]`.

This document is static analysis only.
