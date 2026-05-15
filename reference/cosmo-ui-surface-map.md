# Cosmo UI Surface — Static Analysis Map

Static-analysis map of the user-facing surface of the recovered Cosmo
APK (`com.google.research.air.cosmo`, antisplit v1.1):

- the launcher / main entry point,
- every Activity's UI seam (`setContent` invokes),
- every `*ViewComposable` and `*ViewModel`,
- the resource inventory (`aapt2 dump resources` totals + name samples),
- Compose framework footprint.

## Discipline

Every claim is backed by `[smali …]`, `[asset …]`, `[aapt2 …]`, or
`[doc §X.Y]`. Aggregations carry the union of their tags.

---

## Section A — Launcher entry: `MainActivity`

`com.google.research.air.cosmo.ui.mainscreen.MainActivity`
(`.super Hilt_MainActivity → Landroidx/activity/ComponentActivity;`).

Manifest declaration `[runtime §B.1]`:

```
<activity
    android:name="com.google.research.air.cosmo.ui.mainscreen.MainActivity"
    android:exported="true"
    android:screenOrientation="1">
  <intent-filter>
    <action android:name="android.intent.action.MAIN"/>
    <category android:name="android.intent.category.LAUNCHER"/>
  </intent-filter>
</activity>
```

`onCreate(Bundle)` (verified by direct read of
`[smali ui/mainscreen/MainActivity.smali]`):

1. `super.onCreate(savedInstanceState)` (delegates to `Hilt_MainActivity`).
2. `androidx.activity.EdgeToEdge.enable$default(this, null, null, 3, null)`.
3. If not already initialized: `getCosmoController().init()`,
   then `getNotificationCaptureManager()` is dereferenced (the smali
   continues into a flow-collection block past the snippet read).
4. `Landroidx/activity/compose/ComponentActivityKt;->setContent$default(...)`
   is invoked — the Compose root composable is wired here.

Injected dependencies on `MainActivity` (every `@Inject`-style getter
exposed; from `^\.method public final get`):

```
ActionChipsFactory       — fulfillment/ui composable factory
ActionsRepository
ChatViewComposable
ConversationController
CosmoController          — the orchestrator seam (text §F.1)
CosmoGlassManager
CosmoHoursViewComposable
CosmoSettingStore
CreateSkillViewComposable
FirebaseAnalytics        — the underlying com.google.firebase.analytics.FirebaseAnalytics
FulfillmentModelViewComposable
FulfillmentOrchestrator
Gatekeeper
LanguagePackManager
LLM                       (typed as AICoreLLM specifically)
NotificationCaptureManager
PermissionHelper
ProcessingModeViewComposable
RecordServiceManager
ScreenContextViewComposable
SettingViewComposable
SkillLibraryViewComposable
TTS
VoiceMatchViewComposable
CoroutineScope
```

…plus the `setX` companion setters (Hilt field-injection convention).
Cross-ref `[dagger §H]` for the `MainActivity_MembersInjector`.

Logger tag: class-name (no explicit `COSMO-` const-string in
`MainActivity.smali`'s tag set, only Flogger via
`access$getLogger$cp`).

The `onCreate` snippet shows `MainActivity.init()` is the boot-time
seam that calls `CosmoController.init()` — this is the "captors come
online" trigger from `[architecture §E]`.

---

## Section B — `CosmoGlassActivity` UI

`com.google.research.air.cosmo.glasses.CosmoGlassActivity`
(`.super Hilt_CosmoGlassActivity → Landroidx/activity/ComponentActivity;`).

Manifest `[runtime §B.1]`:

```
<activity
    android:name="com.google.research.air.cosmo.glasses.CosmoGlassActivity"
    android:exported="false"
    android:launchMode="3"
    android:requiredDisplayCategory="android.hardware.display.category.XR_PROJECTED"
    android:label="Cosmo Glass Activity">
```

Methods (from `[image §B.2]`):

```
onCreate(Bundle)
onDestroy()
onNewIntent(Intent)
arePermissionsGranted(): boolean
getViewModel(): CosmoGlassViewModel
handleFinishIntent(Intent)
onPermissionsGranted()
requestPermissions()
```

`onCreate` invokes
`Landroidx/activity/compose/ComponentActivityKt;->setContent$default(...)`
(verified by grep on `[smali glasses/CosmoGlassActivity.smali]`) — so a
Compose root is rendered when the projected glass display is engaged.

`CosmoGlassViewModel` is the activity's ViewModel; produced via
`CosmoGlassViewModel_HiltModules$BindsModule` /
`CosmoGlassViewModel_HiltModules$KeyModule` `[dagger §J]`.

---

## Section C — `TaskMonitorActivity` UI

`com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorActivity`
(`.super Hilt_TaskMonitorActivity → Landroidx/activity/ComponentActivity;`).

Manifest `[runtime §B.1]`:

```
<activity android:name="com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorActivity"/>
```

(no `exported` declared — defaults to `false` on API 31+).

`R8`-mangled lambda methods (from `^\.method public static synthetic`)
indicate Compose-side rendering:

```
$r8$lambda$I14oyGGj81601of6UOBtqw-r7Dk(Message, Composer, int): Unit
$r8$lambda$XRGmR7blOVfG5ApKVu1LPVVO1aE(List, TaskMonitorActivity, LazyListScope): Unit
```

…plus `access$MessageBubble(TaskMonitorActivity, Message, Composer, int)`
— the activity renders a list of `Message`s with per-message bubble
composables (the "Mariner" task-monitor UI for browsing-agent
fulfillment).

ViewModel: `TaskMonitorViewModel`, factory
`TaskMonitorViewModel_Factory` `[dagger §G]`. The
`executeMarinerAction$1$3` inner-class shows fulfillment is dispatched
from inside the ViewModel.

---

## Section D — `DoubleTapActivity` (no UI)

`com.google.research.air.cosmo.fulfillment.DoubleTapActivity`
(`.super Hilt_DoubleTapActivity → Landroidx/activity/ComponentActivity;`).

Manifest `[runtime §B.1]`:

```
<activity
    android:name="com.google.research.air.cosmo.fulfillment.DoubleTapActivity"
    android:exported="true"
    android:taskAffinity=""
    android:excludeFromRecents="true"
    android:launchMode="3"
    android:noHistory="true"
    android:theme="@0x01030055">
```

Public methods: `<init>()` only `[llm §G.1]`.

`onCreate` body (read directly from
`[smali fulfillment/DoubleTapActivity.smali]`):

1. `super.onCreate(savedInstanceState)`.
2. Flogger log line `"DoubleTapActivity started"`.
3. Construct `Intent("show_session")`.
4. `setPackage(getPackageName())`.
5. `putExtra("trigger_type", "DOUBLE_TAP")`.
6. `sendBroadcast(intent)`.
7. `finishAndRemoveTask()`.
8. `overridePendingTransition(0, 0)` (no animation).

The activity does **not** call `setContent` — it is a no-UI shim that
fires a broadcast and exits. The recipient of `show_session` is the
`MainInteractionService` `broadcastReceiver$1` inner class
(`[smali voiceassistant/MainInteractionService$broadcastReceiver$1.smali]`).
The `trigger_type=DOUBLE_TAP` extra maps to
`FulfillmentTriggerType.PIXEL_PHONE_DOUBLE_TAP` `[llm §E.3]`.

---

## Section E — `*ViewComposable` enumeration

Every `*ViewComposable.smali` under
`com/google/research/air/cosmo/voiceassistant/` (verified count: 24):

```
voiceassistant/camera/CameraViewComposable                     [image §A.1]
voiceassistant/ChatViewComposable
voiceassistant/CosmoHoursViewComposable
voiceassistant/CreateSkillViewComposable
voiceassistant/FloatyNavViewComposable
voiceassistant/FloatyViewComposables
voiceassistant/PrivateInferenceWarningComposable
voiceassistant/ScreenContextViewComposable
voiceassistant/SettingViewComposable
voiceassistant/SkillLibraryViewComposable
voiceassistant/VoiceMatchViewComposable
voiceassistant/fulfillmentmodel/FulfillmentModelViewComposable
voiceassistant/processingmode/ProcessingModeViewComposable
voiceassistant/debugpanel/AboutViewComposable
voiceassistant/debugpanel/ActionListViewComposable
voiceassistant/debugpanel/BufferViewComposable
voiceassistant/debugpanel/DebugPanelViewComposable
voiceassistant/debugpanel/FulfillmentViewComposable
voiceassistant/debugpanel/GatekeepingViewComposable
voiceassistant/debugpanel/HealthStatusViewComposable
voiceassistant/debugpanel/LogsViewComposable
voiceassistant/debugpanel/MemoryViewComposable
voiceassistant/debugpanel/ReplayViewComposable
voiceassistant/debugpanel/StatsViewComposable
voiceassistant/debugpanel/UxViewComposable
```

Plus `fulfillment/ui/ActionChipsFactory` (creates per-action chip
composables: `createGoogleDoc$1`, `draftEmail$1`, etc.; verified by
sibling `*$1.smali` filenames).

Each has a matching `*_Factory` (Hilt) `[dagger §G]`. None of these are
declared as standalone `<activity>` in the manifest — they are
rendered inside `MainActivity`'s composition tree via the navigation
graph (see §F.4 below).

---

## Section F — `*ViewModel` enumeration

Every `*ViewModel.smali`:

```
voiceassistant/BrowsingAgentViewModel
voiceassistant/CreateSkillViewModel
voiceassistant/ScreenContextViewModel
voiceassistant/SkillLibraryViewModel
voiceassistant/debugpanel/ChatViewModel
voiceassistant/debugpanel/SuggestionChipButtonViewModel
glasses/CosmoGlassViewModel                            [§B above]
fulfillment/actions/mariner/TaskMonitorViewModel       [§C above]
```

Two of the eight (`CosmoGlassViewModel`, `TaskMonitorViewModel`) are
`@HiltViewModel` (verified by the `*_HiltModules$BindsModule` /
`*_HiltModules$KeyModule` pair in `[dagger §J]`).

---

## Section G — Compose navigation in `MainActivity`

Eight R8-mangled lambda methods on `MainActivity`
(`^\.method public static synthetic \$r8\$lambda\$.*MainActivity`)
share the signature

```
(NavHostController, MainActivity, AnimatedContentScope, NavBackStackEntry, Composer, int) → kotlin.Unit
```

…and one further lambda

```
(NavHostController, MainActivity, NavGraphBuilder) → kotlin.Unit
```

This is the `NavHost` route definition: each per-route lambda is a
composable destination; the `NavGraphBuilder` lambda is the graph
builder. The destinations cover at least 8 routes (one mangled lambda
per route observed).

`androidx.navigation.NavHostController` and `NavGraphBuilder` are
imported (per the smali). The build output dir
`updated_navigation_xml` mentioned in `[runtime A]` is the AGP-side
codegen artifact for the navigation graph — Cosmo uses Compose
navigation (declarative), not classic `<navigation>` XML.

---

## Section H — Resource inventory

From `aapt2 dump resources`, count by type
`[aapt2 dump resources com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk]`:

| Type | Count |
|---|---|
| `anim` | 44 |
| `animator` | 44 |
| `array` | 16 |
| `attr` | 1,595 |
| `bool` | 29 |
| `color` | 2,091 |
| `dimen` | 1,439 |
| `drawable` | 314 |
| `font` | 72 |
| `id` | 552 |
| `integer` | 130 |
| `interpolator` | 25 |
| `layout` | 97 |
| `plurals` | 2 |
| `raw` | 12 |
| `string` | 678 |
| `style` | 2,543 |
| `xml` | 13 |
| **Total** | **9,696** |

### H.1 Cosmo-specific strings

Only nine `string` entries are explicitly Cosmo-named (the rest are
inherited from libraries):

```
0x7f11008f  string/cosmo_active_hours
0x7f110090  string/cosmo_disabled
0x7f110091  string/cosmo_enabled
0x7f110092  string/cosmo_glass_title
0x7f110093  string/cosmo_hours_description
0x7f110094  string/cosmo_logo_description
0x7f110095  string/cosmo_start_time
0x7f110096  string/cosmo_thinking
0x7f110097  string/cosmo_version
```

…plus `string/title_cosmo_active_hours` (0x7f110284) and
`string/use_cosmo_nanoapp_title` (0x7f110293).

### H.2 Cosmo-specific layouts

One classic XML layout:

```
0x7f0d002f  layout/floaty_ui
```

The other 96 `layout/*` entries belong to bundled libraries
(Material3, Material, AndroidX widget, etc.). Cosmo's main UI is
Compose-only.

### H.3 `raw/` resources

```
raw/build_data
raw/com_google_android_apps_hardware_protostar_device_heterodyne_info
raw/com_google_android_apps_hardware_protostar_device_registration_info
raw/com_google_android_gms_auth_account_client_heterodyne_info
raw/com_google_android_gms_auth_account_client_registration_info
raw/com_google_android_gms_fido_heterodyne_info
raw/com_google_android_gms_measurement_heterodyne_info
raw/flags_pb
raw/keep_cronet_api
raw/public_keys_labs_proto
raw/public_keys_nonprod_proto
raw/public_keys_prod_proto
```

`flags_pb` is the bundled Phenotype flag bytes; `public_keys_*_proto`
are the three environment-specific public-key sets (labs / nonprod /
prod).

### H.4 `xml/` resources (13)

```
xml/accessibility_service_config        — bound by CosmoAccessibilityService
xml/app_metadata                         — application <property>
xml/com_google_android_apps_hardware_protostar_device_phenotype
xml/com_google_android_gms_auth_account_client_phenotype
xml/core_certificates_all
xml/core_certificates_release_only
xml/file_paths                           — FileProvider paths
xml/interaction_service_config           — bound by MainInteractionService
xml/m3_button_group_child_size_change    — Material3
xml/m3_list_item_shape_state_list        — Material3
xml/m3_split_button_inner_corner_size_state_list — Material3
xml/m3expressive_button_shape_state_list  — Material3
xml/m3expressive_connected_buttons_inner_corner_size_state_list — Material3
```

The first eight are Cosmo-side; the bottom five are Material3.

### H.5 Theme

The application theme is `@0x7f12043b`. Activities override per-class:

- `RequestPermissionsOnPhoneActivity` → `@0x7f12055e`
- `GoToPhoneGlassesActivity` → `@0x7f12043c`
- `DoubleTapActivity` → `@0x01030055` (system theme)
- `RequestPermissionsOnHostActivity` → `@0x7f120429`
- `GoToHostProjectedActivity` → `@0x7f12043c`
- `PermissionsRequesterActivity` → `@0x7f12055d`

(System theme `@0x01030055` is `Theme.NoDisplay` — confirms
`DoubleTapActivity` does not display a UI.)

---

## Section I — Compose framework footprint

Compose subpackages referenced in Cosmo's smali tree (verified by
grep on `Landroidx/compose/<dir>/<dir>;`):

```
animation                 (AnimatedContent, AnimatedVisibility,
                            EnterExit, ContentTransform, ColorVectorConverter)
animation/core            (transition primitives)
foundation                (Background, Border, Canvas, Clickable, Focusable,
                            Image, Indication, Scroll, Overscroll)
foundation/gestures
foundation/interaction
foundation/layout
foundation/lazy
foundation/pager
foundation/relocation
foundation/selection
foundation/shape
foundation/text
material/icons            (icons-only Material 1 import)
material3                 (full Material3: AppBar, Button, Card, …)
runtime                   (Composer, MutableState, …)
ui                        (Modifier, geometry, graphics, layout, semantics)
```

(Sample is non-exhaustive — the import set inside the smali tree is
extensive.)

`androidx.activity.compose.ComponentActivityKt.setContent$default` is
the entry point used by `MainActivity` and `CosmoGlassActivity`
(verified by grep). `androidx.navigation` (Compose nav) is used by
`MainActivity` (§F).

---

## Section J — Provenance

- `aapt2 dump resources com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk`
- `aapt2 dump xmltree … --file AndroidManifest.xml`
- `cosmo_baksmali/classes*/com/google/research/air/cosmo/`:
  `ui/mainscreen/MainActivity*.smali`,
  `glasses/CosmoGlassActivity*.smali`,
  `glasses/CosmoGlassViewModel*.smali`,
  `fulfillment/DoubleTapActivity.smali`,
  `fulfillment/actions/mariner/TaskMonitorActivity*.smali`,
  every `voiceassistant/**/*ViewComposable.smali`,
  every `voiceassistant/**/*ViewModel.smali`.
- Cross-references: `[image §A, §B]`, `[llm §G.1]`,
  `[runtime §A, §B.1]`, `[dagger §G, §H, §J]`,
  `[telemetry §E.1, §E.2]`.

This document is static analysis only.
