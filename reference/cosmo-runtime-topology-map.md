# Cosmo Runtime Topology — Static Analysis Map

Static-analysis map of the OS-side contract of the recovered Cosmo APK
(`com.google.research.air.cosmo`, antisplit v1.1):

- the `Application` subclass and its Hilt entry point,
- every `<activity>` / `<service>` / `<receiver>` / `<provider>` declared,
- intent-filters, exported flags, `<queries>` block, custom permissions,
- per-service `foregroundServiceType` declarations,
- notification channels created at runtime,
- `<uses-native-library>` declarations,
- the manifest-level `<property>`/`<meta-data>` attached to the application.

## Discipline

Every claim is backed by one of:

- `[smali …]` — a path under
  `cosmo_baksmali/classes*/com/google/research/air/cosmo/...` plus a
  method name or line range; for greps, the exact pattern.
- `[asset …]` — an asset path under `assets/` plus a byte range or full
  filename, extracted via `unzip -p`.
- `[aapt2 …]` — an `aapt2` dump-mode (`permissions`,
  `xmltree AndroidManifest.xml`, `resources`) invoked against
  `com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk`.

Where a claim aggregates over multiple primitives, it ends in the union
of their tags. No behavior is asserted that hasn't been read from
artifact.

---

## Section A — `Application` subclass and Hilt entry

The manifest declares `[aapt2 xmltree AndroidManifest.xml@69]`:

```
<application
    android:name="com.google.research.air.cosmo.CosmoApp"
    android:label="Cosmo"
    android:theme="@0x7f12043b"
    android:icon="@0x7f0800a4"
    android:roundIcon="@0x7f0800a4"
    android:largeHeap="true"
    android:extractNativeLibs="false"
    android:appComponentFactory="androidx.core.app.CoreComponentFactory">
```

`.super` chain (verified by grep):

```
CosmoApp.smali        .super Lcom/google/research/air/cosmo/Hilt_CosmoApp;
Hilt_CosmoApp.smali   .super Landroid/app/Application;
```

`CosmoApp` declares these injected fields `[smali CosmoApp.smali]`:

```
public  backgroundScope: kotlinx.coroutines.CoroutineScope
private batteryStateReceiver: com.google.research.air.cosmo.lib.battery.BatteryStateReceiver
public  cosmoStateManager: com.google.research.air.cosmo.setting.CosmoStateManager
public  processInitializerRunner: com.google.android.libraries.processinit.ProcessInitializerRunner
public  workerFactory: androidx.hilt.work.HiltWorkerFactory
```

Public methods:

```
constructor <init>()
final getBackgroundScope(): CoroutineScope
final setBackgroundScope(CoroutineScope): void
final getCosmoStateManager(): CosmoStateManager
final setCosmoStateManager(CosmoStateManager): void
final getProcessInitializerRunner(): ProcessInitializerRunner
final setProcessInitializerRunner(ProcessInitializerRunner): void
final getWorkerFactory(): androidx.hilt.work.HiltWorkerFactory
final setWorkerFactory(HiltWorkerFactory): void
getWorkManagerConfiguration(): androidx.work.Configuration
onCreate(): void
onTerminate(): void
```

Boot sequence inside `onCreate()` (read directly from smali bytecode):

1. `super.onCreate()` (delegates to `Hilt_CosmoApp.onCreate()`, which
   is the Hilt-generated injection point).
2. `PureProcess.isPure(Context)` is checked; if `true`, the method
   returns immediately. (This skips initializer logic for non-main
   processes such as `:primes_lifeboat`, see §C.)
3. `getProcessInitializerRunner().init()` — runs every
   `@ProcessInitializer` registered in the Hilt graph.
4. A `BatteryStateReceiver` is constructed and the application
   registers it for `android.intent.action.BATTERY_CHANGED`. The first
   sticky `Intent` returned by `registerReceiver` is dispatched
   directly to the receiver via `onReceive`.

`onTerminate()` and `getWorkManagerConfiguration()` bodies are present
in `[smali CosmoApp.smali]` but not enumerated line-by-line here.

The Hilt graph populated at this point is enumerated in
`[dagger §A]`–`[dagger §F]`.

---

## Section B — Manifest components

### B.1 Activities

All eight `<activity>` declarations from `[aapt2 xmltree AndroidManifest.xml]`:

| Class | exported | launchMode | Other |
|---|---|---|---|
| `com.google.android.glasses.sdk.internal.application.CompatibilityDialogActivity` | `true` | — | label `"Android Glasses Compatibility Checker"` |
| `com.google.android.glasses.sdk.permissions.PermissionsTrampolineActivity` | `false` | — | — |
| `com.google.android.glasses.sdk.permissions.standardactivity.RequestPermissionsOnPhoneActivity` | `false` | — | label `@0x7f11021c` |
| `com.google.android.glasses.sdk.permissions.standardactivity.GoToPhoneGlassesActivity` | `false` | — | `requiredDisplayCategory="android.hardware.display.category.XR_PROJECTED"` |
| `com.google.android.gms.auth.api.signin.internal.SignInHubActivity` | `false` | — | `excludeFromRecents=true` |
| `com.google.android.gms.common.api.GoogleApiActivity` | `false` | — | — |
| `com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorActivity` | (default) | — | (no other attrs) |
| `com.google.research.air.cosmo.fulfillment.DoubleTapActivity` | `true` | `3` (singleInstance) | `taskAffinity=""`, `excludeFromRecents=true`, `noHistory=true`, theme `@0x01030055` |
| `com.google.research.air.cosmo.glasses.CosmoGlassActivity` | `false` | `3` (singleInstance) | label `"Cosmo Glass Activity"`, `requiredDisplayCategory="android.hardware.display.category.XR_PROJECTED"` |
| `com.google.research.air.cosmo.ui.mainscreen.MainActivity` | `true` | — | `screenOrientation=1` (portrait); intent-filter `MAIN`+`LAUNCHER` |
| `com.google.research.air.libraries.common.android.permissions.PermissionsRequesterActivity` | (default) | `3` | `excludeFromRecents=true`, theme `@0x7f12055d` |
| `androidx.compose.ui.tooling.PreviewActivity` | `true` | — | (Compose preview) |
| `androidx.xr.projected.permissions.RequestPermissionsOnHostActivity` | `false` | — | label `@0x7f11021b`, theme `@0x7f120429` |
| `androidx.xr.projected.permissions.GoToHostProjectedActivity` | `false` | — | `requiredDisplayCategory="xr_projected"`, theme `@0x7f12043c` |

Cosmo's own activity hierarchies (verified `.super` chains):

```
DoubleTapActivity.smali        .super Hilt_DoubleTapActivity
Hilt_DoubleTapActivity.smali   .super Landroidx/activity/ComponentActivity;

CosmoGlassActivity.smali       .super Hilt_CosmoGlassActivity
Hilt_CosmoGlassActivity.smali  .super Landroidx/activity/ComponentActivity;

MainActivity.smali             .super Hilt_MainActivity
Hilt_MainActivity.smali        .super Landroidx/activity/ComponentActivity;

TaskMonitorActivity.smali      .super Hilt_TaskMonitorActivity
Hilt_TaskMonitorActivity.smali .super Landroidx/activity/ComponentActivity;
```

`MainActivity` is the launcher. Cross-ref `[ui §B]` for its Compose
hierarchy.

### B.2 Services

All `<service>` declarations from `[aapt2 xmltree]`:

| Class | exported | permission | foregroundServiceType | Other |
|---|---|---|---|---|
| `com.google.research.air.cosmo.lib.record.RecordService` | `false` | — | `0x80` (microphone) | — |
| `com.google.android.gms.measurement.AppMeasurementService` | `false` | — | — | enabled |
| `com.google.android.gms.measurement.AppMeasurementJobService` | `false` | `BIND_JOB_SERVICE` | — | — |
| `com.google.research.air.cosmo.lib.wearable.WearableService` | `true` | — | — | intent-filter `MESSAGE_RECEIVED`, `CHANNEL_EVENT`; `<data scheme="wear" host="*" pathPrefix="/cosmowear">` |
| `com.google.research.air.cosmo.lib.wearable.WearableAudioStreamService` | `false` | — | `0x01` (dataSync) | — |
| `com.google.android.libraries.phenotype.registration.PhenotypeMetadataHolderService` | `false` | — | — | `enabled=false`, `process=""`, intent-filter for `Phenotype…` |
| `com.google.research.air.cosmo.accessibility.CosmoAccessibilityService` | `true` | `BIND_ACCESSIBILITY_SERVICE` | — | label `"CosmoAccessibilityService"`, intent-filter `AccessibilityService` |
| `com.google.research.air.cosmo.gboard.GBoardSuggestionsService` | `true` | — | — | intent-filter `com.google.research.air.cosmo.gboard.bind` |
| `com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service.ProjectedGlassCaptureService` | `false` | — | `0xc0` (microphone+camera) | label `"Projected Glass Capture Service"` |
| `com.google.android.gms.auth.api.signin.RevocationBoundService` | `true` | `…REVOCATION_NOTIFICATION` | — | visibleToInstantApps |
| `com.google.firebase.components.ComponentDiscoveryService` | `false` | — | — | meta-data registers Firebase analytics + installations |
| `com.google.research.air.cosmo.voiceassistant.MainInteractionService` | `true` | `BIND_VOICE_INTERACTION` | — | label `"Cosmo Floaty Mode"`, intent-filter `VoiceInteractionService` |
| `com.google.research.air.cosmo.voiceassistant.MainInteractionSessionService` | `true` | `BIND_VOICE_INTERACTION` | — | — |
| `com.google.research.air.cosmo.voiceassistant.MainRecognitionService` | `true` | `BIND_RECOGNITION_SERVICE` | — | — |
| `com.google.apps.tiktok.concurrent.AndroidFuturesService` | `false` | — | — | — |
| `com.google.apps.tiktok.concurrent.InternalForegroundService` | `false` | — | `0x800` (specialUse) | — |
| `android.net.http.MetaDataHolder` | `false` | — | — | `enabled=false` |
| `androidx.appfunctions.service.PlatformAppFunctionService` | `true` | `BIND_APP_FUNCTION_SERVICE` | — | `enabled=@0x7f050003` (resource bool); intent-filter `AppFunctionService`; properties `app_functions.xml`, `app_functions_schema.xsd`, `app_functions_v2.xml` |
| `androidx.appfunctions.service.ExtensionAppFunctionService` | `true` | `BIND_APP_FUNCTION_SERVICE` | — | `enabled=@0x7f050002` |
| `androidx.camera.core.impl.MetadataHolderService` | `false` | — | — | `enabled=false`, meta-data names `Camera2Config$DefaultProvider` |
| `androidx.room.MultiInstanceInvalidationService` | `false` | — | — | `directBootAware=true` |
| `androidx.work.impl.background.systemjob.SystemJobService` | `true` | `BIND_JOB_SERVICE` | — | — |
| `androidx.work.impl.foreground.SystemForegroundService` | `false` | — | — | `enabled=@0x7f050005` |
| `com.google.android.build.data.PropertiesServiceHolder` | `false` | — | — | `enabled=false`, `process=""` |

Cosmo's own service hierarchies (verified `.super` chains):

```
RecordService.smali                       .super Landroid/app/Service;
WearableService.smali                     .super Hilt_WearableService
Hilt_WearableService.smali                .super Lcom/google/android/gms/wearable/WearableListenerService;
WearableAudioStreamService.smali          .super Hilt_WearableAudioStreamService
Hilt_WearableAudioStreamService.smali     .super Landroid/app/Service;
NotificationCaptureService.smali          .super Hilt_NotificationCaptureService
Hilt_NotificationCaptureService.smali     .super Landroid/service/notification/NotificationListenerService;
CosmoAccessibilityService.smali           .super Hilt_CosmoAccessibilityService
Hilt_CosmoAccessibilityService.smali      .super Landroid/accessibilityservice/AccessibilityService;
GBoardSuggestionsService.smali            .super Hilt_GBoardSuggestionsService
Hilt_GBoardSuggestionsService.smali       .super Landroid/app/Service;
MainInteractionService.smali              .super Hilt_MainInteractionService
Hilt_MainInteractionService.smali         .super Landroid/service/voice/VoiceInteractionService;
MainInteractionSessionService.smali       .super Hilt_MainInteractionSessionService
Hilt_MainInteractionSessionService.smali  .super Landroid/service/voice/VoiceInteractionSessionService;
MainRecognitionService.smali              .super Hilt_MainRecognitionService
Hilt_MainRecognitionService.smali         .super Landroid/speech/RecognitionService;
```

`NotificationCaptureService` — declared with `permission="BIND_NOTIFICATION_LISTENER_SERVICE"`,
`exported=true`, intent-filter `android.service.notification.NotificationListenerService`.

### B.3 Receivers

| Class | exported | permission | process | intent-filter |
|---|---|---|---|---|
| `com.google.android.gms.measurement.AppMeasurementReceiver` | `false` | — | — | — |
| `com.google.research.air.cosmo.setting.CosmoStateUpdateReceiver` | `false` | — | — | — |
| `com.google.android.libraries.performance.primes.transmitter.LifeboatReceiver` | `false` | — | `:primes_lifeboat` | — |
| `com.google.android.libraries.phenotype.client.stable.AccountRemovedBroadcastReceiver` | `true` | — | `""` | `android.accounts.action.ACCOUNT_REMOVED` |
| `com.google.android.libraries.phenotype.client.stable.PhenotypeUpdateBackgroundBroadcastReceiver` | `true` | `…PHENOTYPE_UPDATE_BROADCAST` | `""` | `com.google.android.gms.phenotype.UPDATE` |
| `androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver` | `false` | — | — | — |
| `androidx.work.impl.background.systemalarm.RescheduleReceiver` | `false` | — | — | `android.intent.action.BOOT_COMPLETED` |
| `androidx.work.impl.diagnostics.DiagnosticsReceiver` | `true` | `android.permission.DUMP` | — | `androidx.work.diagnostics.REQUEST_DIAGNOSTICS` |

`CosmoStateUpdateReceiver` super chain:

```
CosmoStateUpdateReceiver.smali       .super Hilt_CosmoStateUpdateReceiver
Hilt_CosmoStateUpdateReceiver.smali  .super Landroid/content/BroadcastReceiver;
```

Methods: `onReceive(Context, Intent)`, `getCosmoState()`,
`setCosmoState(CosmoStateManager)`. Cross-ref `[settings §B]` —
`CosmoStateManager.COSMO_STATE_UPDATE_ACTION =
"com.google.research.air.cosmo.setting.STATE_UPDATE"` is the action
this receiver handles, fired by an `AlarmManager` schedule with
`COSMO_STATE_UPDATE_REQUEST_CODE = 0x3e9`.

In addition, `CosmoApp.onCreate` registers a runtime
`BatteryStateReceiver` for `android.intent.action.BATTERY_CHANGED`
(super `Landroid/content/BroadcastReceiver;`); not declared in the
manifest, registered programmatically.

### B.4 Providers

| Class | authorities | exported | grantUriPermissions |
|---|---|---|---|
| `androidx.startup.InitializationProvider` | `com.google.research.air.cosmo.androidx-startup` | `false` | — |
| `androidx.core.content.FileProvider` | `com.google.research.air.cosmo.provider` | `false` | `true` (file paths `@0x7f140006`) |

### B.5 `<queries>` block

Cosmo declares it can talk to these external packages
`[aapt2 xmltree@39]`:

```
com.google.android.apps.photos
com.google.research.air.frameworks.intent.memorylabs
com.google.android.apps.aipex.memora
com.google.research.air.experiences.glimmer.altair
com.google.android.glasses.core
com.google.android.projection.core
com.google.ambient.streaming
```

…plus these intents:

```
<intent>
  <action android:name="android.intent.action.MAIN"/>
  <category android:name="android.intent.category.LAUNCHER"/>
</intent>
<intent>
  <action android:name="androidx.window.extensions.layout.EngagementModeService"/>
</intent>
<intent>
  <action android:name="androidx.xr.projected.ACTION_ENGAGEMENT_BIND"/>
</intent>
<intent>
  <action android:name="androidx.xr.projected.ACTION_BIND"/>
</intent>
```

The four bound external services Cosmo invokes
(`[audio §A.4][llm §A.3, §A.9, §F.4][image §B.1]`) — AICore, Aratea,
ProjectedGlassCaptureService, AgentClient — are all reachable through
this `<queries>` set: AICore via the `BIND_SERVICE` permission, Aratea
via the bound MDI service inside the `aipex.memora` / `glimmer.altair`
companion packages, ProjectedGlassCaptureService via the same APK
(declared at line 216 above; the service itself is a
`com.google.android.glasses.shared.…` class but ships in the same APK
as Cosmo for the recovered build), and `AgentClient` via the
`androidx.xr.projected.ACTION_BIND` intent.

---

## Section C — Process topology

Process distribution, from `android:process="…"` attributes
`[aapt2 xmltree]`:

| Process | Components |
|---|---|
| `<default>` (the main `com.google.research.air.cosmo` process) | `CosmoApp`, all Cosmo activities and own services, `RecordService`, `WearableAudioStreamService`, `WearableService`, `NotificationCaptureService`, `CosmoAccessibilityService`, `GBoardSuggestionsService`, `MainInteractionService` and friends, `DoubleTapActivity`, `MainActivity`, `CosmoGlassActivity`, etc. |
| `:primes_lifeboat` | `LifeboatReceiver` only |
| `""` (process erased — runs in default) | `PhenotypeMetadataHolderService`, `AccountRemovedBroadcastReceiver`, `PhenotypeUpdateBackgroundBroadcastReceiver`, `PropertiesServiceHolder` |

`CosmoApp.onCreate` short-circuits via `PureProcess.isPure(Context)`
when running in `:primes_lifeboat`, so the Hilt graph only fully
populates in the main process.

---

## Section D — Notification channels

Two Cosmo-side notification channels are created via
`NotificationManager.createNotificationChannel`, plus one referenced
by string but created in `LockscreenView` only (which is unused on
modern Android lock screens):

### D.1 `audio_record` (created by `notifications.CosmoNotificationManager`)

`[smali notifications/CosmoNotificationManager.smali]`:

```
private static final AUDIO_RECORD_CHANNEL_ID:Ljava/lang/String; = "audio_record"
```

Method: `createNotificationChannels(Context)`. Channel name string:
`"Recording Audio"`. Description: `"Phone microphone is capturing
audio."`. Importance is passed as the third `int` arg to
`NotificationChannel.<init>(String, CharSequence, int)`.

`RecordServiceManager` (`[audio §B]`) calls
`CosmoNotificationManager.createNotificationChannels(Context)` to
create this channel before starting the foreground microphone service.

### D.2 `WearableServiceChannel` (created by `WearableAudioStreamService`)

`[smali lib/wearable/WearableAudioStreamService.smali]`:

```
.method private final createNotificationChannel()V
    const-string v3, "WearableServiceChannel"
    new-instance v0, Landroid/app/NotificationChannel;
    invoke-direct {v0, v3, v1, v2},
        Landroid/app/NotificationChannel;-><init>(…)
```

### D.3 `hindsight_assistant_channel` (created by `LockscreenView`)

`[smali voiceassistant/LockscreenView.smali]`:

```
.field public static final CHANNEL_ID:Ljava/lang/String; = "hindsight_assistant_channel"
```

This identifier survives from a legacy "Hindsight" code path — present
in the smali, created by `createNotificationChannel(Context)` on
`LockscreenView`.

---

## Section E — `WAKE_LOCK` acquisition sites

The `WAKE_LOCK` permission is declared `[aapt2 permissions]`
`[audio §permissions]`. In the Cosmo-owned smali tree the only
`PowerManager`-related call is in
`com.google.research.air.cosmo.lib.screenstate.ScreenStateMonitorImpl`:

```
check-cast v0, Landroid/os/PowerManager;
invoke-virtual {v0}, Landroid/os/PowerManager;->isInteractive()Z
```

…which reads screen state but does not acquire a wake-lock. The
`WAKE_LOCK` permission is consumed transitively by bundled libraries
(WorkManager's `SystemForegroundService`, `androidx.work` schedulers,
`InternalForegroundService` from `apps.tiktok.concurrent`) — none of
them are Cosmo's own classes. No `PowerManager.newWakeLock` invocation
appears in any smali under `com/google/research/air/cosmo/`.

---

## Section F — Foreground service types

Decoded from the four `foregroundServiceType=…` attribute values
`[aapt2 xmltree]` (constants per Android `FOREGROUND_SERVICE_TYPE_*`
masks):

| Hex | Decoded | Service |
|---|---|---|
| `0x80` | `microphone` | `RecordService` |
| `0x01` | `dataSync` | `WearableAudioStreamService` |
| `0xc0` | `microphone` + `camera` (`0x80` + `0x40`) | `ProjectedGlassCaptureService` |
| `0x800` | `specialUse` | `apps.tiktok.concurrent.InternalForegroundService` |

Cosmo's own foreground services are `RecordService` (microphone) and
`WearableAudioStreamService` (dataSync). The Glass-side foreground
service ships in the same APK but is owned by the
`com.google.android.glasses.shared.…` package (`[image §D]`).

---

## Section G — Permissions topology

`<uses-permission>` declarations (29 total) are reproduced verbatim in
`[audio §permissions]`. This section adds:

### G.1 Custom permission declared by Cosmo

`[aapt2 xmltree@64]`:

```
<permission
    android:name="com.google.research.air.cosmo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    android:protectionLevel="0x00000002"/>
```

Protection level `0x2` = `signature`. Used by Hilt-generated
broadcast registrations to gate non-exported dynamic receivers
within Cosmo's process.

### G.2 Permissions consumed but not declared by Cosmo

`com.google.research.air.experiences.glimmer.altair.permission.WRITE_COSMO_CONTEXT`
appears in `<uses-permission>` (line 35); the matching `<permission>`
declaration is in the `glimmer.altair` companion app, not in this APK
(no `<permission android:name="…WRITE_COSMO_CONTEXT">` element in this
manifest).

### G.3 Bound-service permissions

The matching `BIND_*` permissions Cosmo applies to its own services as
gates:

| Service | `android:permission` |
|---|---|
| `NotificationCaptureService` | `BIND_NOTIFICATION_LISTENER_SERVICE` |
| `CosmoAccessibilityService` | `BIND_ACCESSIBILITY_SERVICE` |
| `MainInteractionService` | `BIND_VOICE_INTERACTION` |
| `MainInteractionSessionService` | `BIND_VOICE_INTERACTION` |
| `MainRecognitionService` | `BIND_RECOGNITION_SERVICE` |

---

## Section H — `<uses-native-library>` declarations

From `[aapt2 xmltree@81-93, @316-328, @544]` — every shared system
library Cosmo declares an optional dependency on (`required="false"`
on each):

```
libedgetpu_util.so
libedgetpu_client.google.so
libOpenCL.so
libOpenCL-car.so
libOpenCL-pixel.so
libopenxr.google.so
```

The first five are declared twice (once at the top of `<application>`,
once before `CosmoAccessibilityService`'s sub-block). `libopenxr.google.so`
is declared once near the XR-projected activities. None of these are
shipped inside the APK; they are vendor-provided system libraries
loaded from the device.

---

## Section I — Application-level `<property>` and `<meta-data>`

`[aapt2 xmltree@77, @312]`:

```
<property
    android:name="android.app.appfunctions.app_metadata"
    android:resource="@0x7f140001"/>            // xml/app_metadata
```

Plus the `com.google.android.gms.version` meta-data
(`@0x7f0b003c`), the `com.google.firebase.components:*` meta-data
under the `ComponentDiscoveryService` block, and the
`com.android.dynamic.apk.fused.modules` meta-data with value
`"models_feature"` at the very end of the manifest (indicating that
the antisplit was assembled to fold a `models_feature` dynamic feature
module into the base APK).

`<uses-library>` (optional system libraries):

```
com.android.extensions.appfunctions   (required=false)
android.ext.adservices                 (required=false)
androidx.window.extensions             (required=false)
androidx.window.sidecar                (required=false)
```

---

## Section J — Provenance

- `aapt2 dump xmltree com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk --file AndroidManifest.xml`
- `aapt2 dump permissions …` (already reproduced in `[audio §permissions]`).
- `cosmo_baksmali/classes*/com/google/research/air/cosmo/`:
  `CosmoApp`, `Hilt_CosmoApp`, every `*Service`/`*Activity`/`*Receiver`
  Cosmo class plus the matching `Hilt_*` parent.
- Cross-references: `[audio §B, §F.3, §permissions]`,
  `[text §A.1, §C.2]`, `[image §B.2]`, `[llm §G.1]`,
  `[settings §B]`, `[dagger §A]`, `[telemetry §C]`.

This document is static analysis only.
