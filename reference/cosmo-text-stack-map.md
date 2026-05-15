# Cosmo Text Stack — Static Analysis Map

Static-analysis map of every text-input surface in the recovered Cosmo APK
(`com.google.research.air.cosmo`, antisplit v1.1).

Same artifact-only discipline as `audio_stack_map.md`. Every claim is
backed by a smali grep, a const-string, an ELF symbol, an `aapt2` line, or
the bytes of an extracted asset file. Where a body is stripped past
recovery, the row reads `(body not recovered)`. No behavior is asserted
that hasn't been read from artifact.

Permissions specific to this doc:

| Tag | Permission | Source of protection-level claim |
|---|---|---|
| `[P:NL]` | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | declared by `NotificationListenerService` in public AOSP `frameworks/base` source; user-grantable via Settings |
| `[P:A11Y]` | `android.permission.BIND_ACCESSIBILITY_SERVICE` | declared by `AccessibilityService` in AOSP; user-grantable via Settings |

The other permission tags (RECORD_AUDIO, etc.) are defined in
`audio_stack_map.md`.

---

## Section A — Notification capture

### A.1 `com.google.research.air.cosmo.notificationcapture.NotificationCaptureService`

Methods (`^\.method` grep, non-synthetic):

```
public <init>()
public onCreate(): void
public onListenerConnected(): void
public onListenerDisconnected(): void
public onNotificationPosted(android.service.notification.StatusBarNotification): void
public onNotificationRemoved(android.service.notification.StatusBarNotification): void
public final getBackgroundScope(): kotlinx.coroutines.CoroutineScope
public final setBackgroundScope(CoroutineScope): void
public final getNotificationEventFlow(): kotlinx.coroutines.flow.MutableSharedFlow
public final setNotificationEventFlow(MutableSharedFlow): void
private final createActiveNotification(StatusBarNotification): ActiveNotification
private final logNotificationDetails(StatusBarNotification, NotificationEventType): void
```

Class hierarchy (verified by grep):

```
NotificationCaptureService.smali
    .super Lcom/google/research/air/cosmo/notificationcapture/Hilt_NotificationCaptureService;

Hilt_NotificationCaptureService.smali
    .super Landroid/service/notification/NotificationListenerService;
```

…so the effective base class is `NotificationListenerService`.

[P:NL]. The permission is signature-protected per public AOSP source but
exposed to the user via `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
[R:OK-CONSENT].

### A.2 `com.google.research.air.cosmo.notificationcapture.NotificationCaptureManager`

Methods (non-synthetic):

```
public <init>()
public final dismissPermissionPrompt(): void
public final getNotificationListenerSettingsIntent(): android.content.Intent
public final getPermissionPromptRequest(): kotlinx.coroutines.flow.StateFlow
public final initialize(android.content.Context): void
public final isNotificationListenerAccessGranted(android.content.Context): boolean
public final requestPermissionPrompt(): void
private final checkPermissions(android.content.Context): void
```

### A.3 `com.google.research.air.cosmo.notificationcapture.ActiveNotification` (proto)

`FIELD_NUMBER` constants from `^\.field public static final` grep:

```
ID_FIELD_NUMBER             = 0x1
TAG_FIELD_NUMBER            = 0x2
PACKAGE_NAME_FIELD_NUMBER   = 0x3
TITLE_FIELD_NUMBER          = 0x4
TEXT_FIELD_NUMBER           = 0x5
BIG_TEXT_FIELD_NUMBER       = 0x6
POST_TIME_FIELD_NUMBER      = 0x7
```

### A.4 `com.google.research.air.cosmo.notificationcapture.NotificationEvent` (proto)

```
NOTIFICATION_FIELD_NUMBER = 0x1
EVENT_TYPE_FIELD_NUMBER   = 0x2
```

### A.5 `…notificationcapture.NotificationEventType`

Enum constants (from `^\.field public static final enum` grep):

```
POSTED
REMOVED
UNRECOGNIZED
```

Plus the proto enum-value ints `POSTED_VALUE = 0x0`, `REMOVED_VALUE = 0x1`.

---

## Section B — Periodic screen capture (text-only, accessibility-driven)

### B.1 `com.google.research.air.cosmo.contentcapture.PeriodicScreenCaptor` (interface)

```
public abstract startCapture(): kotlinx.coroutines.flow.Flow
```

### B.2 `com.google.research.air.cosmo.contentcapture.PeriodicScreenCaptorImpl`

Constructor (verified directly from the `^\.method public constructor`
line in the smali; types are erased to JVM signatures, so no generic
type parameters are claimed):

```
public <init>(
    android.content.Context,
    com.google.research.air.frameworks.intent.screencapture.ScreenCaptor,
    java.util.concurrent.Executor,
    com.google.intelligence.dbw.androidcore.a11yserviceprovider.ConnectedAccessibilityServiceProvider,
    javax.inject.Provider,
    javax.inject.Provider,
    java.util.concurrent.Executor
)
```

The seven `const-string` parameter-name tags emitted by the Kotlin
compiler in the constructor body (in declared order) are:

```
"appContext"
"screenCaptor"
"uiThreadExecutor"
"accessibilityServiceProvider"
"screenCaptureConfigProvider"
"extraScreenContentCaptureConfigProvider"
"backgroundExecutor"
```

…so the two `javax.inject.Provider` parameters are paired with
`screenCaptureConfigProvider` / `extraScreenContentCaptureConfigProvider`
respectively. The Kotlin generic type parameters (e.g.
`Provider<ScreenCaptureConfig>`) are erased at the smali layer; the
pairing above is from the parameter-name strings, not from a typed
signature.

Methods (non-synthetic):

```
public startCapture(): kotlinx.coroutines.flow.Flow
private final getUserManager(): android.os.UserManager
private final isA11yServiceConnected(): boolean
private final isA11yServiceEnabledInSettings(): boolean
private final isKeyboardVisible(): boolean
private final isWorkerJobActive(Continuation): Object
private final showA11yServiceDisabledToast(): void
private final startWork(Continuation): Object
private final stopWork(Continuation): Object
```

const-strings observed in `PeriodicScreenCaptorImpl.smali`:

```
"COSMO-PeriodicScreenCaptorImpl"
"enabled_accessibility_services"
"java/com/google/research/air/cosmo/contentcapture/PeriodicScreenCaptorImpl.kt"
```

A `MutableSharedFlow` field is held by the class (verified via
`access$getObservations$p(...)Lkotlinx/coroutines/flow/MutableSharedFlow;`
synthetic accessor on the smali). The Kotlin-metadata block additionally
references these types:

```
Lcom/google/intelligence/dbw/androidcore/a11yserviceprovider/ConnectedAccessibilityServiceProvider;
Lcom/google/protos/intelligence_dbw_modeling_proto/Observation;
Lcom/google/protos/research/air/frameworks/intent/proto/ScreenCaptureConfig;
Lcom/google/research/air/cosmo/contentcapture/ExtraScreenContentCaptureConfig;
Lkotlinx/coroutines/flow/MutableSharedFlow;
```

…the `Observation` type's role as the per-emission record is implied by
its presence in the captor's metadata together with the `MutableSharedFlow`
field, but the per-emission type parameter `MutableSharedFlow<Observation>`
is erased at the smali layer and not asserted here from a typed
signature.

[P:A11Y]. [R:OK-CONSENT].

### B.3 `com.google.research.air.cosmo.contentcapture.ContentCaptureConfig`

Method list:

```
private <init>()
public static registerAllExtensions(com.google.protobuf.ExtensionRegistryLite): void
```

(A protobuf descriptor shell with no business logic.)

### B.4 `com.google.research.air.cosmo.contentcapture.ExtraScreenContentCaptureConfig`

Proto class. `FIELD_NUMBER` constants (from `^\.field public static final.*FIELD_NUMBER` grep):

```
PREVENT_CAPTURE_WHEN_KEYBOARD_VISIBLE_FIELD_NUMBER = 0x3
```

Sibling smali files exist for `Builder`, `Kt$Dsl$Companion`, etc.

---

## Section C — Screen interaction capture

### C.1 `com.google.research.air.cosmo.interactioncapture.ScreenInteractionCaptor` (interface)

```
public abstract startCapture(): kotlinx.coroutines.flow.Flow
```

### C.2 `com.google.research.air.cosmo.interactioncapture.ScreenInteractionCaptorImpl`

Constructor:

```
public <init>(
    Context,
    com.google.intelligence.dbw.androidcore.a11yserviceprovider.ConnectedAccessibilityServiceProvider,
    com.google.research.air.frameworks.intent.screencapture.ScreenCaptor,
    kotlinx.coroutines.CoroutineDispatcher
)
```

Methods (non-synthetic):

```
public startCapture(): kotlinx.coroutines.flow.Flow
public onAccessibilityEvent(android.view.accessibility.AccessibilityEvent): void
private final isSameInteraction(ScreenInteraction, ScreenInteraction): boolean
private final maybeEmitAndSetPendingInteraction(ScreenInteraction): void
private final processInteraction(ScreenInteraction): void
private final schedulePendingInteractionDebounce(): void
private final startCapturing(): void
private final stopCapturing(): void
```

Class hierarchy (verified by grep):

```
ScreenInteractionCaptorImpl.smali
    .super Ljava/lang/Object;
    .implements Lcom/google/research/air/cosmo/interactioncapture/ScreenInteractionCaptor;
    .implements Lcom/google/intelligence/dbw/androidcore/a11yserviceprovider/AccessibilityEventListener;
```

…so the captor is **not** itself an `AccessibilityService`; it implements
the Cosmo helper interface
`AccessibilityEventListener` defined in
`com.google.intelligence.dbw.androidcore.a11yserviceprovider`. The
`onAccessibilityEvent(AccessibilityEvent): void` method is that
interface's callback, dispatched by the
`ConnectedAccessibilityServiceProvider` (which holds the actual binder
to whatever installed `AccessibilityService` exposes the events).

### C.3 `com.google.research.air.cosmo.interactioncapture.ScreenInteractionCaptorDispatcher`

Not a class — a `@Qualifier` annotation interface (verified from the
`.class` line):

```
.class public interface abstract annotation
       Lcom/google/research/air/cosmo/interactioncapture/ScreenInteractionCaptorDispatcher;
.implements Ljava/lang/annotation/Annotation;
.annotation runtime Ljavax/inject/Qualifier;
```

### C.4 `com.google.research.air.cosmo.interactioncapture.ScreenInteraction`

Fields (`^\.field` grep):

```
private final timestamp: j$.time.Instant
private final type: InteractionType
private final packageName: java.lang.String
private final windowId: int
private final node: intelligence.dbw.proto.android.accessibility.AndroidAccessibilityNodeInfo
```

Public accessors and copy/equals/hashCode/toString as per Kotlin data
class convention.

### C.5 `com.google.research.air.cosmo.interactioncapture.InteractionType`

Enum constants (from `^const-string ` grep on the enum smali):

```
"CLICK"
"FOCUS"
"SCROLL"
"SWITCH_APPLICATION"
"TEXT_CHANGED"
```

[P:A11Y]. [R:OK-CONSENT].

---

## Section D — Ephemeral buffer system

### D.1 `com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferReader` (interface)

```
public abstract flow(): kotlinx.coroutines.flow.Flow
public abstract isEmpty(): boolean
public abstract size(): int
public abstract snapshot(): com.google.common.collect.ImmutableList
public abstract snapshotReversed(): com.google.common.collect.ImmutableList
public abstract map(kotlin.jvm.functions.Function1): EphemeralBufferReader
```

### D.2 `com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer`

Constructor:

```
public <init>(int, long, com.google.common.time.TimeSource)
```

Methods:

```
public final add(java.lang.Object, long): boolean
public final clear(): void
public final countItemsAfter(long): int
public final getMaxEntryAgeMillis(): long
public final remove(kotlin.jvm.functions.Function1): boolean
public final replace(Function1, java.lang.Object): boolean
public final updateMaxEntryAgeMillis(long): void
public flow(): kotlinx.coroutines.flow.Flow
public isEmpty(): boolean
public size(): int
public snapshot(): com.google.common.collect.ImmutableList
public snapshotReversed(): com.google.common.collect.ImmutableList
private final isEntryNotExpired(Timestamped): boolean
```

Implements `EphemeralBufferReader`. Holds typed entries with timestamps;
items past `maxEntryAgeMillis` are dropped on access via
`isEntryNotExpired`.

### D.3 Per-source `@Qualifier` annotations

Filenames present in `com/google/research/air/cosmo/ephemeralbuffer/`:

```
AmbientAudioEphemeralBuffer.smali
BudsAudioEphemeralBuffer.smali
DeviceAudioEphemeralBuffer.smali
DeviceCameraEphemeralBuffer.smali
GlassesCameraEphemeralBuffer.smali
EphemeralBufferReader.smali
EphemeralBufferReader$-CC.smali
EphemeralBufferModule.smali
EphemeralBufferModule$Companion.smali
```

The five per-source `*EphemeralBuffer.smali` files are **not classes** —
they are runtime-retained `@Qualifier` annotation interfaces (verified
by `head` of each):

```
.class public interface abstract annotation
       Lcom/google/research/air/cosmo/ephemeralbuffer/<Source>EphemeralBuffer;
.super Ljava/lang/Object;
.implements Ljava/lang/annotation/Annotation;
```

(`<Source>` ∈ `AmbientAudio`, `BudsAudio`, `DeviceAudio`, `DeviceCamera`,
`GlassesCamera`.) They exist purely as Hilt/javax-inject disambiguators
for `InMemoryEphemeralBuffer` injections. The actual per-source buffer
instances are produced by `EphemeralBufferModule_Companion_Provide…
EphemeralBufferFactory.smali` (one factory per source, observed
filenames include `ProvideAmbientAudioEphemeralBufferFactory`,
`ProvideBudsAudioEphemeralBufferFactory`,
`ProvideDeviceAudioEphemeralBufferFactory`,
`ProvideDeviceCameraEphemeralBufferFactory`,
`ProvideCameraJPEGBytesEphemeralBufferFactory`,
`ProvideImageScreenEphemeralBufferFactory`,
`ProvideIntentEphemeralBufferFactory`,
`ProvideLocationEphemeralBufferFactory`,
`ProvideNotificationEphemeralBufferFactory`,
`ProvideScreenInteractionEphemeralBufferFactory`,
`ProvideWatchAudioEphemeralBufferFactory`, and the
`*Bytes`/`*Text`/`*TextReader` variants).
The same reclassification appears in `image_stack_map.md` §C for the
two camera-side qualifiers.

### D.4 `com.google.research.air.cosmo.memory.MemoryStorage` (interface)

```
public abstract add(MemoryEntry, Continuation): Object
public abstract clear(Continuation): Object
public abstract search(java.lang.String, int, SearchFilter, Continuation): Object
```

### D.5 `com.google.research.air.cosmo.memory.NoOpMemoryImpl`

Methods (non-synthetic):

```
public <init>()
public add(MemoryEntry, Continuation): Object
public clear(Continuation): Object
public search(String, int, SearchFilter, Continuation): Object
```

The `^const-string` grep on this file returns only `"of(...)"`.
`NoOpMemoryImpl` is the only class in the recovered DEX with
`.implements Lcom/google/research/air/cosmo/memory/MemoryStorage;`
(verified by grep across the full `cosmo_baksmali` tree). The Hilt
binding is in `NoOpMemoryModule.smali` (Kotlin metadata declares
`bindMemory: NoOpMemoryImpl → MemoryStorage`).

`com.google.research.air.cosmo.memory.MemoryEntry`:

```
public <init>(java.lang.String, java.util.List)
public final getText(): java.lang.String
public final getTags(): java.util.List
public final component1(): String
public final component2(): java.util.List
public final copy(...)
public equals/hashCode/toString
```

`com.google.research.air.cosmo.memory.SearchFilter` and
`com.google.research.air.cosmo.memory.MemoryStorageException` smali files
are also present; field/method enumeration not done in this analysis.

---

## Section E — `IntentExtractor`

### E.1 `com.google.research.air.cosmo.intentextraction.IntentExtractor` (interface)

```
public abstract getIntentFlow(): kotlinx.coroutines.flow.Flow
public abstract isActive(): boolean
public abstract start(): void
public abstract stop(): void
```

### E.2 `com.google.research.air.cosmo.intentextraction.IntentExtractorImpl`

Constructors:

```
public <init>(Context, kotlinx.coroutines.flow.SharedFlow, EphemeralBufferReader,
              EphemeralBufferReader, com.google.research.air.cosmo.lib.llm.LLM,
              CoroutineScope)
public <init>(Context, SharedFlow, EphemeralBufferReader, EphemeralBufferReader,
              LLM, CoroutineScope, com.google.common.time.TimeSource, boolean)
```

Methods (non-synthetic):

```
public getIntentFlow(): kotlinx.coroutines.flow.Flow
public isActive(): boolean
public start(): void
public stop(): void

private final collectGatekeeperOutput(Continuation): Object
private final collectScreenInteractions(Continuation): Object
private final extractIntent(java.util.List, Continuation): Object
private final gatherContext(long): java.util.List
private final getIntentExtractionPrompt(): java.lang.String
private final getTriggerResult(GatekeepingResult): GatekeeperTriggerResult
private final parseIntent(java.lang.String): java.lang.String
private final triggerIntent(long): void
```

const-strings observed in `IntentExtractorImpl.smali`:

```
"intent_extraction_prompt.jinja2"
"\n**Screen:**\n"
"\n**Interaction:**\n"
"```"
"```json"
"final_answer"
"image/png"
"COSMO-IntentExtractor"
"IntentExtractor is already running."
"IntentExtractor is already stopped."
"Failed to extract or parse extracted intent (response: %s)"
"Failed to emit inference request"
"Intent context has %d screens and %d interactions"
"extractIntent"
"gatherContext"
"getViewHierarchy(...)"
"getV0(...)"
"gatekeeperOutput"
"llm"
"N/A"
```

Inner class `IntentExtractorImpl$GatekeeperTriggerResult.smali` exists.

### E.3 The asset content of `assets/intent_extraction_prompt.jinja2`

Extracted via `unzip -p`. Full text (1942 bytes):

> You are analyzing a user's session on a mobile app. Each session consists
> of a sequence of screenshots and actions that will appear at the end of
> this prompt. Your goal is to understand the user's overall intent based
> on the series of interactions provided.
>
> Instructions:
>
> 1. Analyze Each Step in the Sequence:
>    - What is displayed in the screenshot?
>    - What action did the user take?
>    - Why might the user have taken this action?
>    - What specific details are relevant? (e.g., dates, items, locations,
>      quantities)
>
> 2. Summarize the User's Goal:
>    - After analyzing each screenshot-action pair, combine insights to
>      determine the user's overall objective.
>    - Include all observed details to make the goal clear and specific.
>
> Output:
>
> 1. **Reasoning**:
>    - Provide a step-by-step analysis of each screenshot and action pair.
>    - Focus on the user's likely intentions and relevant details observed
>      in the input sequence.
>
> 2. **Final Answer**:
>    - Summarize the user's overall goal in one concise sentence.
>    - Start with an action verb. Phrase the action in the imperative form.
>    - Include all specific details observed in the input sequence.
>    - Avoid using any phrases or structures from the instructions or
>      examples above.
>    - Your final answer should start with the appname, followed by a
>      semicolon, and then the inferred goal (example: "eBay; order a
>      basket ball").
>
> Important Notes:
>
> - Do not reuse or paraphrase any examples provided in the instructions.
> - Base your response solely on the screenshots and actions in the input
>   sequence.
> - Each session is unique ensure your final answer reflects the specific
>   details of this session alone.
>
> The format of the output should be a valid json object (with proper
> escaping) in the following structure:
>
> ```
> {
>     "reasoning": "your reasoning here",
>     "final_answer": "your final answer here"
> }
> ```
>
> ---
>
> The sequence of screenshots and actions for this session will now follow:

The prompt has no Jinja `{{ … }}` placeholders. The const-strings
`**Screen:**\n` and `**Interaction:**\n` from §E.2 are present in
`IntentExtractorImpl.smali` and the `extractIntent` private method is
the one that references the prompt asset filename; the per-call ordering
of the prompt text vs. the section markers is in a method body not
extracted line-by-line in this analysis.

---

## Section F — `CosmoController` (orchestrator)

### F.1 `com.google.research.air.cosmo.controller.CosmoController`

Constructor parameter types (verbatim from the smali; truncated here for
readability):

```
public <init>(
    PeriodicScreenCaptor,
    ScreenInteractionCaptor,
    AmbientAudioCaptor,                  ← three captor params named "AmbientAudioCaptor"
    CosmoGlassManager,
    AmbientAudioCaptor,
    AmbientAudioCaptor,
    AmbientAudioCaptor,
    InMemoryEphemeralBuffer × 6,
    SharedFlow,
    LocationCaptor,
    InMemoryEphemeralBuffer × 2,
    IntentExtractor,
    InMemoryEphemeralBuffer,
    com.google.research.air.frameworks.intent.export.ItemExporter,
    j$.time.InstantSource,
    com.google.research.air.cosmo.memory.MemoryStorage,
    CosmoSettingStore,
    CosmoConfiguration,
    com.google.research.air.cosmo.fulfillment.ActionsRepository,
    CoroutineScope,
    WearableSynchronizer,
    AltairActionSynchronizer,
    javax.inject.Provider × 3
)
```

(The constructor declares four parameters of the same type
`AmbientAudioCaptor` in adjacent positions, plus six adjacent
`InMemoryEphemeralBuffer` parameters. Per-slot mapping to ambient /
buds / device / watch / device-camera / glasses-camera is determined
by the Hilt qualifier annotations applied at the call site
(`AmbientAudioEphemeralBuffer`, `BudsAudioEphemeralBuffer`,
`DeviceAudioEphemeralBuffer`, etc.; see §D.3) — not visible in the
constructor's smali line itself.)

Methods (non-synthetic, public):

```
public final init(): void
public final getCosmoStartTimeMillis(): kotlinx.coroutines.flow.StateFlow
public final getMicAudioFlow(): kotlinx.coroutines.flow.Flow
public isCosmoEnabled(): boolean
public final isAmbientAudioCaptureOn(): boolean
public final isBudsAudioCaptureOn(): boolean
public final isDeviceAudioCaptureOn(): boolean
public final isWatchAudioCaptureOn(): boolean
public final isLocationCaptureOn(): boolean
public final isNotificationCaptureOn(): boolean
public final isScreenCaptureOn(): boolean
public final isScreenInteractionCaptureOn(): boolean
public final isIntentExtractionOn(): boolean
public isAnyMicAudioCaptureOn(): boolean
public pauseMicAudioCapture(): void
public resumeMicAudioCapture(): void
public final restoreAmbientAudioCapture(): void
public final startAudioCapture(Continuation): Object
public final startBudsAudioCapture(Continuation): Object
public final startDeviceAudioCapture(Continuation): Object
private final clearAllDataAndActions(): void
private final checkAudioBufferOverflow(InMemoryEphemeralBuffer, java.lang.String): void
private final checkCosmoEnabledAndLogIfDisabled(): boolean
private final onNotificationEvent(NotificationEvent): void
private final updateCaptureState(boolean, boolean, Function1, Function1, boolean, Continuation): Object
private final updateCaptureStates(boolean, Continuation): Object
private final updateContextCollectionState(): void
```

(More methods exist; only those visible in the first 30-line snippet of
`^.method` grep are listed above.)

A sibling inner-class file
`CosmoController$startIntentExtraction$2$1.smali` exists in the same
package as `CosmoController`. The naming pattern matches Kotlin's lambda
desugaring (`<owner>$<methodName>$<lambdaIndex>`), suggesting a method
named `startIntentExtraction` on `CosmoController`. The body of that
method is not extracted in this analysis.

---

## Section G — Cross-source merge (text into the BERT gatekeeper)

`com.google.research.air.cosmo.gatekeeping.impl.BertClassifierGatekeepingModel`
is the consumer that builds the BERT input string. The six source-prefix
const-strings (`"Ambient audio: "`, `"Buds audio: "`, `"Device audio: "`,
`"Watch audio: "`, `"Notification: "`, `"Screen: "`) and the 14 unique
action-name labels are enumerated in `audio_stack_map.md` §H.2.

---

## Section H — Reachability summary

| Path | Reach | Reason from artifact |
|---|---|---|
| Notification capture | [R:OK-CONSENT] | `BIND_NOTIFICATION_LISTENER_SERVICE` declared; `NotificationCaptureManager.getNotificationListenerSettingsIntent` exists for the deep-link |
| Periodic screen capture | [R:OK-CONSENT] | `BIND_ACCESSIBILITY_SERVICE` is the AOSP gate for `AccessibilityService.onAccessibilityEvent` (called by `ScreenInteractionCaptorImpl`); user-grantable in Settings |
| Screen interaction capture | [R:OK-CONSENT] | same a11y gate |
| Ephemeral buffer | [R:OK] | `InMemoryEphemeralBuffer` is pure Java + Guava; no system permission |
| `MemoryStorage.search` | [R:OK on NoOp] | only impl is `NoOpMemoryImpl`; gecko/i18n_sentencepiece assets not referenced from any smali |
| `IntentExtractor` | [R:OK on the LLM dependency] | depends on `LLM`; see `llm_stack_map.md` |

---

## Section I — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/` directories:
  `notificationcapture`, `contentcapture`, `interactioncapture`,
  `intentextraction`, `ephemeralbuffer`, `memory`, `controller`.
- `assets/intent_extraction_prompt.jinja2` (1,942 bytes; full text in §E.3).
- `aapt2 dump permissions com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk`.
- `audio_stack_map.md` for the prefix-tag and intent-label const-strings
  pulled from `BertClassifierGatekeepingModel.smali`.

This document is static analysis only.
