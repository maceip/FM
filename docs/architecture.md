# Genesis System Architecture

This document is the system-level architecture of the genesis app (cosmo
clean-room reimplementation). It is the entry point — read this first, then
descend into the more specific docs as needed:

- **Marching orders & contract rules (C1–C9, PR0–PR6):** [`architecture-delta.md`](architecture-delta.md)
- **Active blockers:** [`blocker-register.md`](blocker-register.md)
- **Recovery maps (cosmo bytecode-level reference):** `$COSMO_DEV_ROOT/repo/docs/{audio,text,image,llm,ui_surface,dagger_graph,settings_and_config,telemetry}_stack_map.md`
- **Inherent (joint audio→intent model project):** `~/inherent/contracts/runtime_contract.md`
- **CaMeL reference (Google Research):** [github.com/google-research/camel-prompt-injection](https://github.com/google-research/camel-prompt-injection), [arxiv 2503.18813](https://arxiv.org/abs/2503.18813)

**Snapshot date:** 2026-05-13.

---

## 0. Status legend

Every component carries one of these flags:

| Flag | Meaning |
|---|---|
| ✅ **live** | Implemented, exercised by tests, runs on debug builds today. |
| ⚠️ **partial** | Code path exists but is gated off, stubbed, or missing a downstream consumer. Genesis ships the structure; behavior is incomplete. |
| 🟡 **deferred** | Recovered from cosmo and architecturally placed, but intentionally not built for current scope. Slot exists; no work scheduled. |
| 🔵 **planned** | Design space reserved in this architecture; no code yet; explicit roadmap item. |
| 🔴 **absent** | Not in genesis, not recovered, not scoped. Mentioned only when it's a meaningful negative. |

A subsystem may have mixed status (e.g., gatekeeping is mostly ✅ with one ⚠️ EdgeTPU backend).

---

## 1. Process model

`CosmoApp` (`com.google.research.air.cosmo.CosmoApp`) is the manifest-declared
application class. Hilt builds the `SingletonComponent` in `onCreate`.
Foreground services (`RecordService` for mic+device+call audio, plus per-source
services for wearables, glass) run with the appropriate
`foregroundServiceType` bitmask. Settings live in a single encrypted
DataStore-backed protobuf (`cosmo_setting.pb`), surfaced as `StateFlow` through
`CosmoSettingStore.getCosmoSettingFlow()`.

The capture and gatekeeping pipelines all run on `ApplicationScope` — a
single supervisor scope per app process — so cancellation propagates cleanly
on shutdown. There is no separate worker process. ✅

---

## 2. Capture layer

The capture layer converts physical signals (audio, accessibility events,
notification posts, location updates, camera frames) into typed events on
per-source [ephemeral buffers](#7-memory--context). Captors share the shape
`startCapture(): Flow<T>` plus `flush()`.

### 2.1 Sources

| Source | Captor | Buffer qualifier | Status | Notes |
|---|---|---|---|---|
| Ambient mic | `AmbientAudioCaptor` (`SODAAmbientAudioCaptor`) | `@AmbientAudioEphemeralBuffer` + `@AmbientAudioBytesEphemeralBuffer` | ✅ | Routes through `RecordServiceManager`; AOC vs mic dispatched per setting (see 2.2) |
| Pixel Buds | `BudsAudioCaptor` (`SODABudsAudioCaptor`) | `@BudsAudioTextEphemeralBuffer` + `@BudsAudioBytesEphemeralBuffer` | ✅ | Bluetooth SBC decode via `lib/buds/` (`BondedBudsDeviceDiscovery`, `BudsAudioProviderImpl`, `ReflectiveSbcAudioDecoder`); does NOT depend on `libsbcdecoder_jni.so` |
| Device audio (media) | `DeviceAudioCaptor` (`SODADeviceAudioCaptor`) | `@DeviceAudioTextEphemeralBuffer` + bytes | ✅ | `AudioSource = REMOTE_SUBMIX` |
| Call audio | rolled into device captor | shared | ✅ | `AudioSource = VOICE_CALL` |
| Watch audio | `WatchAudioCaptor` (`SODAWatchAudioCaptor`) | `@WatchAudioTextEphemeralBuffer` | ✅ | Streams over `WearableAudioStreamService` |
| Screen text | `PeriodicScreenCaptor` (accessibility-event based) | `@ScreenEphemeralBuffer` | ✅ | Polls every N seconds |
| Screen interactions | `ScreenInteractionCaptor` | `@ScreenInteractionEphemeralBuffer` | ✅ | Click/focus/scroll/switch-application/text-changed events |
| Notifications | `NotificationCaptureController` | `@NotificationEphemeralBuffer` | ✅ | Via `NotificationListenerService` |
| Location | `LocationCaptor` | `@LocationTextEphemeralBuffer` | ✅ | Coarse + fine permission gated |
| Glass / XR projected | `CosmoGlassManager` + `CosmoProjectedGlassCaptureService` | (TBD) | ⚠️ | Service binds; capture stream consumer not yet wired into gatekeeper |
| Camera (image analysis) | CameraX `ImageAnalysis$Analyzer` | `@CameraJPEGBytesEphemeralBuffer` | ⚠️ | Frame capture exists; downstream consumer for prompt grounding is not built |

### 2.2 AOC vs mic dispatch (C5)

`SODAAmbientAudioCaptor.startCapture` reads `isUseNanoappAudioEnabled` and
attempts `RecordServiceManager.startAocRecording(listener)` before falling
back to `startMicRecording`. The routing is live ✅. The AOC transport itself
(`NanoAppClient` in `lib/record/`) is **partial** ⚠️ — the cosmo shape was
ported (`ContextHubClientUtil` for hub/nanoapp discovery, IPM mode messages
LIVE_STREAM=0 / STREAMING_OFF=3, silence injection), but because
`ContextHubClientCallback` is an `@SystemApi` abstract class **not present
in the public Android SDK**, the call back wiring requires either bytecode-gen
or a platform-signed compile-only stub jar. Today the binding fails fast and
the caller falls back to mic — which is the correct degraded behavior.

### 2.3 Per-source SODA controllers

Each audio captor owns its own `SodaController` subclass (`MicSodaController`,
`BudsSodaController`, `DeviceSodaController`, `WatchSodaController`,
`CallSodaController`, `StreamingSodaController`). Each subclass constructs its
own `SodaSession(context, packDir, clock)` — a dedicated `Soda(context,
callback)` native session — so multiple sources can transcribe concurrently
with independent callbacks. Mirrors cosmo `SodaController.smali` lines
619–1375. ✅

---

## 3. SODA / on-device ASR

The on-device speech stack. Genesis bundles cosmo's recovered SODA native
library (`libsoda_dev_jni.so`) and three language packs (`lp_cpu`, `lp_lga_tpu`
for Tensor G4, `lp_zuma_tpu` for Tensor G5), and routes everything through a
thin Kotlin shape that matches cosmo's recovered controller.

### 3.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Native runtime | `libsoda_dev_jni.so` (jniLibs/arm64-v8a) | ✅ | Recovered binary, vendored |
| Pack provider | `lib/soda/SodaCapability.kt::SodaLanguagePackProvider` | ✅ | SoC-routed: G4→`lp_lga_tpu`, G5→`lp_zuma_tpu`, else→`lp_cpu`. Bundled-asset → cache → HTTPS download fallback chain. SHA-256 + size verified on extract |
| Capability probe + pack prep | `lib/soda/JniSodaCapability.kt` | ✅ | Thin: `isAvailable` + `prepare()` returns the resolved pack dir |
| Per-source session | `lib/soda/SodaSession.kt` | ✅ | Owns one `Soda(context, callback)` instance + lifecycle |
| Callback bridge | `SodaSession.kt::TranscriptingSodaCallback` | ✅ | Translates `SodaEventProto.SodaEvent` → `SodaTranscriptCallback.onTranscript` + `onDirectednessTrigger` |
| Config builder | `lib/soda/SodaConfigBuilder.kt` | ✅ | Sets `AMBIENT_ONESHOT` + `IntendedSpeechConfig.OPEN_MIC` + `attachOpenMicResult` (Magic Mic surface) |

### 3.2 What's bundled inside each language pack

The pack is a zip of cosmo's recovered SODA pack contents. The native runtime
loads everything inside it; genesis code only addresses a subset directly.

| Pack subsystem | Bundled? | Surfaced to genesis code? | Status |
|---|---|---|---|
| End-to-end ASR model (`endtoendmodel/*`) | ✅ | Implicitly — every transcript is its output | ✅ |
| Magic Mic / IntendedSpeech (`magic_mic/MARBLE_V2_*`) | ✅ | Yes — `intendedQueryScore ≥ intendedQueryThreshold` becomes a `DirectednessEvent` on `SodaController.directedness` | ✅ |
| Endpointer (`acousticmodel/MARBLE_*_EP.*`) | ✅ | Internal to SODA; we observe partial/final transcript boundaries | ✅ (opaque) |
| Punctuation restoration (`SODA_punctuation_*`) | ✅ | Internal to SODA; transcripts arrive punctuated | ✅ (opaque) |
| Text denormalization (`denorm/embedded_*.mfar`) | ✅ | Internal to SODA; numbers/dates/emoji arrive normalized | ✅ (opaque) |
| Grapheme-to-phoneme (`g2p*.syms`) | ✅ | Internal to SODA; would matter for TTS | ✅ (opaque, no consumer today) |
| Voice Match (`voice_match/MARBLE_speakerid.tflite`, `MARBLE_voice_filter.tflite`) | ✅ | `SodaConfigBuilder` accepts `voiceMatchEnabled`; `SodaEnrollmentManagerImpl` declares `nativeEnroll*` / `nativeGet*` JNI bridges; `TranscriptionData.speakerLabel` field exists | ⚠️ | See 3.3 |
| Hotword data (`hotword.data`) | ✅ | `attachHotwordEvent=true` in config; events arrive at callback and are **logged for diagnostics only** | ⚠️ | No routing action wired |
| Diarization (`diarization/*`) | ✅ | Config bit NOT enabled; `TranscriptionData.speakerLabel` always `""` | 🟡 |
| Pumpkin semantic grammar (`pumpkin.mmap` ~14MB, `semantics.pumpkin`, `config.pumpkin`, `monastery_config.pumpkin`) | ✅ (~42MB across 3 packs) | None — cosmo itself doesn't consume Pumpkin output (per delta G5); genesis matches | 🟡 |
| Android Auto context grammars (`context_prebuilt/*.fst`) | ✅ | Genesis is not an Auto host; never triggered | 🟡 |
| Offline action data (`offline_action_data.pb`) | ✅ | Internal to SODA, no genesis-side consumer | 🟡 |

### 3.3 Voice Match (cosmo-recovered, partially wired in genesis)

This is the most ready-to-finish unwired surface in genesis. Cosmo recovery
docs (`audio_stack_map.md §I`, `dagger_graph_map.md`, `settings_and_config_map.md`)
show cosmo had:

- A JNI surface (`nativeEnrollForVoiceMatch`, `nativeGetSpeakerInfo`,
  `nativeGetVoiceMatchModels`) — ported to genesis as `SodaEnrollmentManagerImpl`
- Setting keys `VOICE_MATCH_ENABLED=0x14` and `VOICE_MATCH_ENROLLED=0x19`
- A Compose enrollment UI (`VoiceMatchViewComposable`, `VoiceMatchAudioFetcherImpl`)
- Threading of `voice_match_enabled` into the fulfillment prompt context

What genesis has today: the JNI stub class, the settings flag, the SODA config
plumbing. **No callers** of the enrollment JNI methods, **no UI surface**, no
persisted enrollment bytes, `TranscriptionData.speakerLabel` always `""`. ⚠️

Closing this gap is ~1 day of work and unblocks per-speaker context attribution.

---

## 4. Gatekeeping

The gatekeeper decides (a) is this directed at the assistant, (b) what skill
is being invoked. Two modalities run mutually-exclusively per ambient
transcript (the C1 dispatch invariant — see `architecture-delta.md §C1`):
audio path via Conformer EdgeTPU when available, text path via Magic Mic gate
+ BERT classifier otherwise.

### 4.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Unified `Gatekeeper` | `gatekeeping/GatekeepingPipeline.kt` | ✅ | Owns subscriptions + dispatch; routes ambient mic via `selector.select().classify(input)`; non-ambient text routes through `cpuRecoveryBackend.classify` directly |
| Backend interface | `gatekeeping/backend/AudioGatekeepingBackend.kt` | ✅ | `classify(AudioInput): GatekeepingResult?`; each backend returns null for the wrong `AudioInput` shape, which IS the C1 mutex |
| Backend selector | `gatekeeping/backend/AudioGatekeepingBackendSelector.kt` | ✅ | Priority: `ConformerEdgeTpu` → `Inherent` → `CpuRecovery` |
| CPU recovery backend | `gatekeeping/backend/CpuRecoveryBackend.kt` | ✅ | Wraps Combined BERT+regex; gates on `directednessTrigger` for ambient source; passes through trusted (buds/device/watch) sources |
| EdgeTPU Conformer backend | `gatekeeping/backend/ConformerEdgeTpuBackend.kt` | ⚠️ | `isAvailable=false` until Pixel+Darwinn proof; calls `audioModel.predict(pcm)` when active. Awaiting hackathon-notes resolution + arrived Pixel |
| Inherent backend | `gatekeeping/backend/InherentBackend.kt` | ⚠️ | `isAvailable` checks for `inherent.tflite` + `inherent.metadata.json` assets; `classify(RawPcm)` runs `InherentAudioGatekeepingModel.predict`. The validated `asap-static3000-int8` artifact matches Android's tensor contract; quality + real-device proof remain open |
| BERT classifier | `gatekeeping/GatekeepingBert.kt` (`BertClassifierGatekeepingModel`) | ✅ | Recovered model; CPU + Tensor-G4/G5 variants |
| Combined BERT+regex | `gatekeeping/GatekeepingBert.kt` (`CombinedBertRegexGatekeepingModel`) | ✅ | Default text model; regex fast-path fused with BERT scores |
| Audio gatekeeping model | `gatekeeping/AudioGatekeepingModel.kt` | ✅ (loadable) | Code path is the Conformer + audio_frontend pipeline; activation gated on EdgeTPU backend availability |

### 4.2 Asset variants in `model-manifest.json`

| Asset | Variant | Status |
|---|---|---|
| `bert_v0_cpu.tflite` (72M) | CPU baseline | ✅ |
| `bert_v0_p24.tflite` (45M) | Tensor G4 EdgeTPU | ✅ |
| `bert_v0_p25.tflite` (46M) | Tensor G5 EdgeTPU | ✅ |
| `bert_v0_mt6989.tflite` (placeholder hash, 1 byte) | MediaTek Dimensity 9300 | 🔵 | Slot reserved; AOT pipeline drafted in `cosmo-dev/repo/docs/bert_gatekeeper_litert_jni_replacement.md` (`mtk_target.SocModel.MT6989`). No model bundled yet |
| `audio_frontend.tflite` + `audio_gatekeeper_edgetpu.tflite` | Conformer audio path | ✅ (bundled) / ⚠️ (active = false) |
| `inherent.tflite` + `inherent.metadata.json` | Genesis joint model | ⚠️ | Validated `asap-static3000-int8` artifact available locally and manifest-pinned; ignored binary must be provisioned before strict release gates |

### 4.3 Dispatch invariant (C1 mutex)

Each backend returns null for the non-native `AudioInput` shape:

- `CpuRecoveryBackend.classify(RawPcm) → null`. Owns the text path only.
- `ConformerEdgeTpuBackend.classify(TranscribedSegment) → null`. Owns the audio bytes path when active.
- `InherentBackend.classify(TranscribedSegment) → null`. Owns the audio bytes path when active.

So on a Pixel with EdgeTPU active, the ambient text dispatch is suppressed
(returns null → not emitted) and the audio bytes dispatch fires. On a generic
CPU-recovery device the inverse. The mutex is enforced by the backend type-filter
rather than by a `Gatekeeper`-side predicate. ✅

---

## 5. Intent extraction

C7 contract: `Gatekeeper.output` has two concurrent subscribers — the action
pipeline (Fulfillment) and the `IntentExtractor`. They observe independently;
neither blocks the other.

### 5.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| `IntentExtractor` (subscriber) | `intent/IntentExtractor.kt::IntentExtractorImpl` | ✅ | Collects `GatekeeperOutputSource.results`; absorbs exceptions at the collect site |
| `IntentClassifier` interface | `intent/IntentExtractor.kt` | ✅ | Pluggable; production binding is `LlmIntentClassifier` |
| `LlmIntentClassifier` | `intent/IntentExtractor.kt` | ✅ | Loads `assets/intent_extraction_prompt.jinja2` (cosmo's recovered prompt body); gathers up to 5 recent transcripts + 8 recent screen interactions from `@AmbientAudioEphemeralBuffer` and `@ScreenInteractionEphemeralBuffer`; calls `LLM.generateText`; strips ` ```json ` fences; parses with `org.json.JSONObject`; expects `{"reasoning": ..., "final_answer": "AppName; goal"}` per cosmo `IntentExtractorImpl.smali` |
| `IntentRecord` | `intent/IntentExtractor.kt` | ✅ | `intent`, `slots`, `confidence`, `input`, `modelId`, `reasoning?` |
| Intent buffer | `@IntentEphemeralBuffer` | ✅ | Downstream consumer for fulfillment prompt rendering + debug surfaces |

`LlmIntentClassifier` is the second-subscriber LLM-backed extractor. It is
fully cosmo-aligned on the parse path; the prompt rendering uses simple
sectioned string interpolation rather than jinja2 substitution (genesis does
not bundle a jinja engine). 🟡 jinja interpolation is a future enrichment if
the prompt body grows beyond what inline composition can express cleanly.

---

## 6. Fulfillment & skills

Fulfillment converts a `GatekeepingResult` into a concrete action (or no-op).
The path is: gatekeeper result → fulfillment orchestrator → prompt rendering
→ LLM call → strict action parse → confirmation policy → action handler →
execution + audit log.

### 6.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Fulfillment runner | `fulfillment/Fulfillment.kt` (`FulfillmentRunner`) | ✅ | Renders `fulfillment_prompt.jinja2` against the schema |
| Skill catalog | `skills/Skills.kt` (`SkillAssetCatalog`) | ✅ | Built at app-start from `assets/filtered_skill_schema.textproto` (22 action types, required args, trigger regexes, confirmation requirements, permission gates) |
| Action parser | `fulfillment/actions/FulfillmentAction.kt` | ✅ | Strict validation before repository insertion; explicit downgrade paths for missing first-party APIs |
| Confirmation policy | `fulfillment/Confirmation.kt` | ✅ | Per-subtype coverage; UI surface lives in `ui/` |
| Action handler | `FulfillmentActionHandlerImpl` | ⚠️ | Single-handler today; planned to split into subtype-owned executors |
| Actions repository | `ActionsRepositoryContract` | ✅ | Surfaces current/user-facing/executed/dismissed/cosmo-removed/history state |
| TaskMonitor surface (browsing agent) | `fulfillment/actions/mariner/TaskMonitorActivity.kt` | ✅ | Renders agent prompt/response bubbles |
| Audit trail | telemetry-backed | ✅ | Every action execution recorded with skill, input, result |

The fulfillment layer is functionally broad and covers the entire 22-action
schema, but the single-handler shape needs splitting into per-subtype executors.
That refactor is a Workstream C item, not a parity gap.

---

## 7. Memory & context

Two-tier: short-lived ephemeral buffers per source (in-memory ring buffers
with timestamp-based eviction), plus a persistent encrypted `ContextStore`
backed by Room over SQLCipher.

### 7.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Ephemeral buffers (23 per-source qualifiers) | `ephemeralbuffer/EphemeralBuffer.kt` (`InMemoryEphemeralBuffer<T>`) | ✅ | Disambiguated by Hilt `@Qualifier`; max age tracks `getEphemeralBufferDurationMin()` setting |
| Persistent context store | `context/ContextStore.kt` | ✅ | Room + SQLCipher; Keystore-wrapped passphrase in no-backup storage |
| Insert / queryByTime / queryByText | `ContextStore` interface | ✅ | Text query is TF-IDF or naive contains today |
| `queryByEmbedding` | `ContextStore` interface | 🟡 | Interface present; no embedding model wired → returns empty list. Hook for future Gecko integration |
| Memory storage abstraction | `memory/MemoryStorage.kt` (`NoOpMemoryImpl`) | ⚠️ | Cosmo had a real impl bound to Gecko embeddings + ANN search; genesis ships the no-op |

### 7.2 Gecko semantic search (deferred)

Cosmo's `gecko/gecko_out256_tpu_v0.tflite` + `gecko/i18n_sentencepiece.model`
provide on-device 256-dim sentence embeddings for the multilingual context store.
Genesis does NOT bundle these assets and does NOT have a real
`MemoryStorage.search`. 🟡

`text_stack_map.md` notes "gecko/i18n_sentencepiece assets not referenced
from any smali" — cosmo itself appears not to fully consume them either; the
embedding path is a recovered surface that may have been replaced server-side.
Architectural hook is reserved (`ContextStore.queryByEmbedding`,
`MemoryStorage` interface); no code path uses it.

---

## 8. State & settings

### 8.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Settings store (encrypted DataStore) | `setting/Settings.kt::CosmoSettingStore` | ✅ | 60-field protobuf surfaced as `StateFlow<CosmoSetting>` |
| `CosmoStateManager` (genesis-only convenience) | `setting/Settings.kt::CosmoStateManager` | ✅ | Enum `CosmoState{ENABLED, DISABLED, SNOOZED, OUTSIDE_ACTIVE_HOURS, LOW_BATTERY}`; alarm-scheduled transitions; low-battery auto-snooze |
| `CosmoStateSource` (cosmo-aligned probe) | `controller/CosmoStateSource.kt` + `CosmoController` impl | ✅ | 2-method probe interface (`isCosmoEnabled`, `isAnyMicAudioCaptureOn`) mirroring cosmo `CosmoStateSource.smali`. Inject this rather than `CosmoController` when only state probe is needed |
| `CosmoController` | `controller/CosmoController.kt` | ✅ | Reconciles per-source captor jobs against settings |
| Configuration (build-flavor flags) | `setting/Settings.kt::CosmoConfiguration` | ✅ | 27 visibility flags fixed at construction |

The original architecture-delta C6 spec called for a sealed
`CosmoState{Active, SuspendedLowBattery, SuspendedTimeOfDay}` ADT with
15%/20% battery hysteresis. That spec was invented; cosmo actually has
the 2-method probe interface. Genesis matches cosmo; the richer
`CosmoStateManager` is kept as a genesis-only convenience for the settings
UI / snooze flow but is not promoted to the parity contract.

---

## 9. LLM clients

A single `LLM` interface (`generateText(prompt: String): String` plus
`inference(parts, onToken)` for streaming and prompt-parts). Several backing
impls; selection is policy-driven.

### 9.1 Components

| Backend | File | Status | Notes |
|---|---|---|---|
| Public AICore (Gemini Nano via `GenerativeModel`) | `lib/llm/AICoreClientWrapper.kt` | ✅ | Production default when AICore feature is available on device |
| LiteRT-LM (on-device Gemma bundle) | `lib/llm/LiteRtLmLLM.kt` | ⚠️ | Dogfood default is the app-private E2B bundle (`scripts/provision-local-litertlm.sh`); E4B and asset-pack delivery are explicit overrides |
| Remote inference (Oak session) | `lib/llm/RemoteInferenceClient.kt` (`OkHttpRemoteInferenceClient`) | ⚠️ | Live path needs endpoint + certificate pin + audience configuration |
| Network-aware selector | `lib/llm/NetworkAwareLLM`-shape (in `LlmBackend.kt`) | ✅ | Routes local vs remote vs hybrid |
| Simulation LLM (debug) | `lib/llm/SimulationLLM.kt` | ✅ | Deterministic fixture LLM for unit tests |
| MlKit prompt LLM | `lib/llm/MlKitPromptLLM.kt` | ⚠️ | Alternative local path; status mirrors AICore availability |
| Stats | `lib/llm/LlmStatsRepository`-shape | ✅ | Hit/success/failure StateFlows |

`AICoreExperimentToggle`, the runtime-override config provider, and the
LiteRT-LM delivery state machine were **deleted in PR6** — the production
path reads `BuildConfig` directly and uses the public `GenerativeModel` API.
Re-add experiment/override layers only when a workstream actively consumes them.

---

## 10. UI surface

Navigation 3 (`NavDisplay`) with per-tab stacks, adaptive support-pane scene
strategy for foldables/tablets, predictive-back opt-in, Material adaptive
layouts.

### 10.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Main activity | `ui/mainscreen/MainActivity.kt` | ✅ | Nav3 destinations: Actions, Capture, Camera, Settings, Debug, action-detail, task-monitor |
| Debug panels | `ui/mainscreen/DebugPanels.kt` | ✅ | 25 Hilt-multibound panels for controller jobs, settings, permissions, LLM stats, action repo, fulfillment history, gatekeeper output, buffer sizes, model manifest, skill registry, observability schema, presence overlay |
| Glass surface | `glasses/CosmoGlass.kt` | ✅ | Glass permission state + connection/bind state + buffer-clear action via `CosmoGlassViewModel` |
| Route state persistence | `MainNavigationStateTest.kt` covers | ✅ | TaskMonitor token round-trip, corrupted route rejection, JSON `SavedNavigationBundle` |
| Task monitor | `fulfillment/actions/mariner/TaskMonitorActivity.kt` | ✅ | Browsing-agent surface |
| Voice Match enrollment UI | (missing) | 🟡 | Cosmo had `VoiceMatchViewComposable` per `dagger_graph_map.md` line 522. Not ported |

---

## 11. Telemetry & observability

`Logging.kt` defines the genesis observability catalog: 78 log tags, 309
Flogger event definitions each mapped to a `cosmo_*` Firebase Analytics event,
typed `TelemetryEvent` emits (fulfillment, actions, gatekeeper runs,
gatekeeper directedness gate, TaskMonitor, Glass), event provenance metadata
(owner / privacy class / source path / emission evidence), and tests that
fail closed if any Firebase event is undocumented. ✅

The `gatekeeper_directedness_gate` event (per architecture-delta G8) reports
each Magic Mic gate decision with `gate_fired` + `raw_audio_mode_active`
parameters so false-negative rate can be measured on real audio.

---

## 12. Trust layer — CaMeL (🔵 PLANNED)

**Status: 🔵 planned. No code yet. This section is the architectural placement
informed by reading the reference implementation at
`~/Downloads/pixel-backups/backup_git/git/home/adk-samples/python/agents/camel/`.**

### 12.1 What CaMeL actually is (concretely)

CaMeL ([arxiv 2503.18813](https://arxiv.org/abs/2503.18813),
[google-research/camel-prompt-injection](https://github.com/google-research/camel-prompt-injection))
is a **Python-subset AST interpreter** that wraps every runtime value in a
typed envelope carrying provenance + access-control metadata, and enforces
a security policy at the **tool-call boundary** when that metadata indicates
untrusted flow.

The pipeline:

1. The **Privileged LLM (P-LLM)** sees only the trusted user query plus the
   tool API surface and class type system. Its output is **a Python program
   as a string** — not an abstract plan, not JSON, actual Python that uses
   tool functions to fulfill the request.
2. The **CaMeL Interpreter** parses that Python with `ast.parse` and walks
   the AST. Every Python value flowing through the interpreter is wrapped
   as `Value[T]`, which carries the raw value **plus** a `Capabilities`
   record. Operations propagate `Capabilities` through the dependency graph.
3. At every **tool call** the interpreter consults the
   `SecurityPolicyEngine`. The default rule: **if any argument or any
   transitive dependency of any argument is non-public-readable, and the
   tool is state-changing, the call is denied** with a reason string.
4. A special built-in `query_ai_assistant(query: str, output_schema: str)
   -> int | str | float | bool` is the bridge to the **Quarantined LLM
   (Q-LLM)**. The P-LLM can have its emitted code call this built-in to
   parse unstructured data (email bodies, web pages, screen text) into
   typed primitives. The Q-LLM call is **stateless** (a new session per
   call), is **side-effect-free** (allow-listed in
   `NO_SIDE_EFFECT_TOOLS`), and returns a primitive that the interpreter
   wraps with `Capabilities.camel()` (source = `CAMEL`, readers = `Public`).
   The taint laundering is partial — the dependency chain still includes
   the untrusted source, which is what trips the policy on downstream
   side-effecting calls.
5. The agent runs as a `LoopAgent[P-LLM, Interpreter]` with
   `max_iterations=10`. On `CaMeLException` (parse error, type error, call
   error) the loop returns the error to the P-LLM and asks for revised
   code. On `SecurityPolicyDeniedError` the loop exits with a security
   violation message. On clean execution it returns the printed output.

The point is: **the P-LLM never sees the untrusted data**, only the
interpreter does, and the interpreter cannot be socially-engineered
because it's executing parsed Python AST nodes, not following natural-language
instructions.

### 12.2 Reference primitives (read from the ADK sample)

| ADK reference file | Type | Shape |
|---|---|---|
| `camel_library/capabilities/sources.py` | `SourceEnum` | `CAMEL`, `USER`, `ASSISTANT`, `TRUSTED_TOOL_SOURCE` |
| same | `Tool(tool_name, inner_sources)` | Source variant tagging tool outputs (e.g., a `Tool("read_email", inner_sources={addr1, addr2})` records the email addresses involved) |
| same | `Source` typealias | `SourceEnum \| Tool` — the union |
| `camel_library/capabilities/readers.py` | `Public` | Sentinel meaning "publicly readable" |
| same | `Readers` typealias | `frozenset[_T] \| Public` — either a closed set of allowed readers or fully public |
| `camel_library/capabilities/capabilities.py` | `Capabilities` (frozen dataclass) | `sources_set: frozenset[Source]`, `readers_set: Readers`, `other_metadata: dict[str, Any]`. Defaults: `default()` = `({USER}, Public())`; `camel()` = `({CAMEL}, Public())` |
| `camel_library/interpreter/camel_value.py` | `Value[T]` | Wraps the raw value + capabilities + dependency graph references. The interpreter's currency. Operates as a transparent proxy for most Python operations but tracks capability propagation |
| same | `CaMeLFunction` | Wraps a Python callable + its declared `capabilities` + its `dependencies`. Tools are registered as these |
| same | `Namespace` | Maps variable name → `Value`. The interpreter's environment |
| `camel_library/function_types.py` | `Function` (pydantic) | Tool descriptor: `name`, `call`, `full_docstring`, `parameters` (pydantic schema), `return_type` |
| same | `FunctionCall` (pydantic) | Audit record: `function: str`, `object_type: str?`, `args: Mapping`, `output: T \| Exception`, `is_builtin: bool` |
| `camel_library/security_policy.py` | `Allowed` / `Denied(reason)` | Sealed result |
| same | `SecurityPolicy` (Protocol) | `(tool_name, kwargs) -> Allowed \| Denied` |
| same | `SecurityPolicyEngine` (Protocol) | Holds `policies: list[tuple[pattern, SecurityPolicy]]` and `no_side_effect_tools: set[str]`. `check_policy(tool_name, kwargs, dependencies)` is the enforcement entry point |
| same | `base_security_policy` | Default: deny if any arg is non-Public, unless the tool is in `NO_SIDE_EFFECT_TOOLS` (which always includes `query_ai_assistant`) |
| same | `SecurityPolicyDeniedError` | Raised by the interpreter to abort the loop |
| `camel_library/interpreter/interpreter.py` | `parse_and_interpret_code(code, namespace, tool_calls_chain, deps, eval_args)` | The entry point. Parses with `ast`, evaluates, returns `(CaMeLResult, Namespace, tool_calls, dependencies)` |
| same | `CaMeLException[E]` | Wraps a Python exception with AST nodes + dependency `Value`s + capabilities. Exceptions also carry provenance |
| same | `CaMeLResult` typealias | `Ok[Value[Any]] \| Error[CaMeLException]` |
| same | `EvalArgs` | Configures `eval_mode: DependenciesPropagationMode` (NORMAL or STRICT) + `security_policy_engine` |
| same | `DependenciesPropagationMode` | `NORMAL` vs `STRICT` — STRICT propagates capabilities more conservatively |
| `camel_agent/camel_agent.py` | `QuarantinedLlmService` | Wraps a stateless LLM session per call. Public surface: `run(query, output_schema) -> Iterator[Event]` and `get_query_ai_assistant_function() -> Callable[[str, str], int\|str\|float\|bool]` |
| same | `CaMelInterpreterService` | Holds `tools`, `eval_args`, `namespace`, and the `QuarantinedLlmService`. Public: `execute_code(code, tool_calls_chain, deps) -> (output, tool_calls, error?, namespace, deps)` |
| same | `CaMeLInterpreter` (BaseAgent) | ADK agent wrapping `CaMelInterpreterService`. Reads `p_llm_code` from session state, runs it, emits result or error |
| same | `CaMeLAgent` (BaseAgent) | Top-level: a `LoopAgent` over `[pllm_agent, camel_interpreter_agent]` with `max_iterations=10`, plus session-state plumbing for `p_llm_code`/`eval_result`/`function_calls`/`dependencies` |

Tool registration in the reference is a **triple**:
`(py_callable, capabilities, dependencies)` — passed into
`CaMelInterpreterService.__init__`. The interpreter wraps each in
`CaMeLFunction` and inserts into the namespace.

### 12.3 Where it sits in genesis

```
┌────────────────────────────────────────────────────────────────────┐
│ Captors (mic, buds, device, watch, screen, notification,           │
│          location, glass, camera)                                  │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Ephemeral buffers (per-source)                                     │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Gatekeeper (directedness + skill — unchanged)                      │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ trusted user query (the directed transcript)
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ ╔══════════════════════════════════════════════════════════════╗   │
│ ║       CaMeL Trust Layer (PLANNED)                            ║   │
│ ║                                                              ║   │
│ ║   PrivilegedLlmService (P-LLM)                               ║   │
│ ║      input  : trusted user query + tool API + type schema    ║   │
│ ║      output : a program string (Kotlin-subset OR plan-DSL —  ║   │
│ ║               see 12.5 for the open decision)                ║   │
│ ║                       │                                      ║   │
│ ║                       ▼                                      ║   │
│ ║   CaMeLInterpreter                                           ║   │
│ ║      • Parses the program (AST or plan tree)                 ║   │
│ ║      • Walks it; wraps every value as Value<T>(raw,caps)     ║   │
│ ║      • Propagates Capabilities through operations            ║   │
│ ║      • At every tool call:                                   ║   │
│ ║          1. asks SecurityPolicyEngine.checkPolicy(           ║   │
│ ║               toolName, kwargs, dependencies)                ║   │
│ ║          2. if Denied → SecurityPolicyDeniedError            ║   │
│ ║          3. if Allowed → invokes the Kotlin callable;        ║   │
│ ║             wraps return in Value with combined caps         ║   │
│ ║      • Built-in: query_ai_assistant(query, outputSchema)     ║   │
│ ║          → routes to QuarantinedLlmService                   ║   │
│ ║          → wraps result in Value with Capabilities.camel()   ║   │
│ ║                       │                                      ║   │
│ ║                       ▼                                      ║   │
│ ║   QuarantinedLlmService (Q-LLM)                              ║   │
│ ║      • New stateless session per call                        ║   │
│ ║      • Parses untrusted text into one primitive              ║   │
│ ║        (int / str / float / bool)                            ║   │
│ ║      • Cannot drive control flow                             ║   │
│ ║                                                              ║   │
│ ║   SecurityPolicyEngine                                       ║   │
│ ║      • policies: list<(pattern, SecurityPolicy)>             ║   │
│ ║      • noSideEffectTools: set<String>                        ║   │
│ ║          (always includes query_ai_assistant; per-tool       ║   │
│ ║           overrides come from genesis SkillRegistry)         ║   │
│ ║                                                              ║   │
│ ║   LoopAgent (P-LLM → Interpreter, max 10 iterations)         ║   │
│ ║      • On CaMeLException → return error to P-LLM, retry      ║   │
│ ║      • On SecurityPolicyDeniedError → abort with message     ║   │
│ ║      • On clean Ok → escalate (return printed output)        ║   │
│ ╚══════════════════════════════════════════════════════════════╝   │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Fulfillment Action Handler (executes one capability-checked call)  │
└────────────────────────────────────────────────────────────────────┘
```

The Gatekeeper does **not** change. It still produces directedness + skill
classification. CaMeL is the layer **after** the directed transcript has
been identified as a user query worth acting on.

### 12.4 Genesis Kotlin port — primitive names to reserve

The names below are the cosmo-aligned port of the ADK reference primitives.
I'm reserving them in the architecture so the eventual implementation has
known landing slots; nothing here is `class` yet. A future
`C10. Trust Contract` addendum to `architecture-delta.md` will codify the
invariants (e.g., "every `Value<T>` carries non-null `Capabilities`",
"`SecurityPolicyEngine` is the only path to tool dispatch").

| Reference (Python) | Genesis (Kotlin) | Lives in |
|---|---|---|
| `SourceEnum` | `enum class CamelSource { USER, CAMEL, ASSISTANT, TRUSTED_TOOL_SOURCE }` | `trust/Source.kt` |
| `Tool(tool_name, inner_sources)` | `data class ToolSource(val toolName: String, val innerSources: Set<String> = emptySet())` | `trust/Source.kt` |
| `Source` typealias | `sealed interface CamelDataSource { data class FromEnum(val kind: CamelSource); data class FromTool(val tool: ToolSource) }` | `trust/Source.kt` |
| `Public` sentinel | `object PublicReaders : Readers` | `trust/Readers.kt` |
| `Readers` typealias | `sealed interface Readers { object Public; data class Restricted(val allowed: Set<Reader>) }` | `trust/Readers.kt` |
| `Capabilities` | `data class Capabilities(val sources: Set<CamelDataSource>, val readers: Readers, val metadata: Map<String, Any> = emptyMap())` with companion `default()` and `camel()` | `trust/Capabilities.kt` |
| `Value[T]` | `data class CamelValue<T>(val raw: T, val capabilities: Capabilities, val dependencies: List<CamelValue<*>>)` | `trust/CamelValue.kt` |
| `CaMeLFunction` | `data class CamelFunction(val name: String, val invoke: suspend (Map<String, CamelValue<*>>) -> CamelValue<*>, val capabilities: Capabilities, val dependencies: List<CamelValue<*>>)` | `trust/CamelFunction.kt` |
| `Namespace` | `class CamelNamespace(initial: Map<String, CamelValue<*>>)` with copy-on-write semantics | `trust/CamelNamespace.kt` |
| `FunctionCall` | `data class CamelFunctionCall(val function: String, val args: Map<String, Any?>, val output: Result<Any?>, val isBuiltin: Boolean)` | `trust/CamelFunctionCall.kt` (audit record) |
| `Allowed` / `Denied` | `sealed interface SecurityPolicyResult { object Allowed; data class Denied(val reason: String) }` | `trust/SecurityPolicy.kt` |
| `SecurityPolicy` | `fun interface SecurityPolicy { fun evaluate(toolName: String, kwargs: Map<String, CamelValue<*>>): SecurityPolicyResult }` | `trust/SecurityPolicy.kt` |
| `SecurityPolicyEngine` | `interface SecurityPolicyEngine { val policies: List<Pair<String, SecurityPolicy>>; val noSideEffectTools: Set<String>; fun checkPolicy(toolName, kwargs, dependencies): SecurityPolicyResult }` | `trust/SecurityPolicyEngine.kt` |
| `base_security_policy` | `class BaseSecurityPolicy(override val noSideEffectTools: Set<String> = setOf("query_ai_assistant")) : SecurityPolicyEngine` | `trust/BaseSecurityPolicy.kt` |
| `NoSecurityPolicyEngine` | `object PermissiveSecurityPolicyEngine : SecurityPolicyEngine` (debug-only; allows all) | `trust/SecurityPolicy.kt` |
| `SecurityPolicyDeniedError` | `class SecurityPolicyDeniedError(reason: String) : Exception(reason)` | `trust/SecurityPolicy.kt` |
| `CaMeLException` | `data class CamelEvalException(val cause: Throwable, val nodes: List<CamelAstNode>, val dependencies: List<CamelValue<*>>, val capabilities: Capabilities = Capabilities.camel())` | `trust/CamelEvalException.kt` |
| `CaMeLResult` | `sealed interface CamelResult<out T> { data class Ok<T>(val value: CamelValue<T>); data class Error(val exception: CamelEvalException) }` | `trust/CamelResult.kt` |
| `EvalArgs` + `DependenciesPropagationMode` | `data class EvalArgs(val mode: PropagationMode = PropagationMode.NORMAL, val engine: SecurityPolicyEngine); enum class PropagationMode { NORMAL, STRICT }` | `trust/EvalArgs.kt` |
| `QuarantinedLlmService` | `class QuarantinedLlmService(private val llm: LLM)` with `suspend fun query(query: String, outputSchema: QSchema): Any` and `enum class QSchema { INT, STR, FLOAT, BOOL }` | `lib/llm/QuarantinedLlmService.kt` |
| `query_ai_assistant` built-in | Registered into the interpreter namespace by `CamelInterpreterService` at startup | `trust/builtins/QueryAiAssistantBuiltin.kt` |
| `CaMelInterpreterService` | `class CamelInterpreterService(val tools: List<CamelFunction>, val evalArgs: EvalArgs, val quarantinedLlm: QuarantinedLlmService)` with `suspend fun executeCode(program: CamelProgram, callsChain, deps): InterpreterStepResult` | `trust/CamelInterpreterService.kt` |
| `CaMeLInterpreter` agent | `class CamelInterpreter(val service: CamelInterpreterService)` — the loop participant; consumes the P-LLM's emitted program from a shared session state | `trust/CamelInterpreter.kt` |
| `parse_and_interpret_code` | `fun parseAndInterpret(program: CamelProgram, namespace, callsChain, deps, args): InterpreterStepResult` — the core | `trust/InterpreterCore.kt` |
| P-LLM agent | `class PrivilegedLlmService(private val llm: LLM, private val systemPrompt: String, private val toolApi: ToolApiDescriptor)` with `suspend fun emitProgram(userQuery: CamelValue<String>): CamelProgram` | `lib/llm/PrivilegedLlmService.kt` |
| `CaMeLAgent` (loop) | `class CamelAgent(private val pllm: PrivilegedLlmService, private val interpreter: CamelInterpreter, private val maxIterations: Int = 10)` with `suspend fun run(userQuery: String): CamelRunResult` | `trust/CamelAgent.kt` |

### 12.5 Open architectural decisions

Two decisions defer until the implementing workstream picks up:

**Decision A — program language for the P-LLM output.**
The reference uses **Python-subset AST** parsed with Python's built-in
`ast` module. For genesis, three viable choices:

1. **Port the Python-subset interpreter.** Requires a Python AST parser in
   Kotlin (e.g., a small hand-written recursive-descent parser limited to
   the AST nodes the reference handles). Highest fidelity to the paper;
   lets us reuse the reference's evaluation logic almost line-for-line.
   ~1500 LOC for the parser + ~1000 LOC for the interpreter core.

2. **Kotlin-subset program.** P-LLM emits Kotlin-like syntax; we use
   Kotlin's compiler-embeddable parser. Cleaner for a Kotlin codebase but
   the P-LLM has to learn a less-common surface, and the parser brings a
   substantial dependency.

3. **Plan DSL.** P-LLM emits a structured plan (e.g., a JSON list of
   `{tool, args, capture_as}` steps with limited control flow:
   sequential, conditional, foreach). Simpler interpreter (~300 LOC) but
   loses CaMeL's expressive power — no inline computation between tool
   calls, no list comprehensions over tool outputs.

**Recommendation:** start with **(3) Plan DSL** for the first cut to land
the trust enforcement mechanics quickly; design the interpreter so that
swapping to (1) is a `CamelProgram` representation change, not an engine
rewrite. The `SecurityPolicyEngine`, `Capabilities`, `Value<T>`,
dependency tracking, and audit record logic are identical across all three
choices.

**Decision B — Q-LLM transport.**
The reference treats the Q-LLM as a separate stateless ADK session of the
same model. For genesis:

- The Q-LLM and P-LLM can be the same underlying genesis `LLM` (AICore /
  LiteRT-LM / remote) but **must be different `LLMService` instances** that
  maintain separate session state. The on-device LLM clients today
  (`AICoreClientWrapper`, `LiteRtLmLLM`) are stateless per call already, so
  the requirement is satisfied trivially.
- The architectural rule is that **no Q-LLM call returns a `CamelValue`
  with `readers = Public()` unless the result was successfully coerced to
  the requested primitive `outputSchema`**. Bad output coercion → null →
  P-LLM has to retry with a better prompt.

### 12.6 What changes in existing layers when CaMeL lands

The current architecture is intentionally shaped so the trust layer can be
added without major surgery elsewhere. The required touch points:

| Layer | Change |
|---|---|
| **Captors** | Each captor declares the `CamelDataSource.FromTool("read_<source>", innerSources=...)` that its output values inherit. No changes to `Flow<T>` shape; wrapping happens at the boundary into the trust layer |
| **Ephemeral buffers** | No change — buffers are unchanged; the trust layer reads from them and wraps |
| **Gatekeeper** | No change — Gatekeeper output is still `GatekeepingResult`; the directed transcript is what becomes the trusted user query that enters the CaMeL Agent |
| **IntentExtractor** | Reclassified — `LlmIntentClassifier.classify()` IS a Q-LLM call (untrusted ambient text → structured primitive). When CaMeL lands, this becomes the canonical first consumer of `QuarantinedLlmService` |
| **Fulfillment Runner** | Replaced — instead of "render prompt → LLM → parse action → call handler," the flow becomes "user query → P-LLM emits plan → interpreter runs plan; tool calls are skill-registry actions." The skill registry remains the source of truth for available tools and their argument schemas |
| **Skill handlers** | Each skill registers as a `CamelFunction` with declared `capabilities` (what it requires of its inputs) and the underlying `suspend` Kotlin implementation |
| **LLM clients** | No change to `LLM` interface. Two new wrapper services (`PrivilegedLlmService`, `QuarantinedLlmService`) become the only downstream entry points; raw `LLM` injection becomes an implementation detail of those wrappers |
| **Telemetry** | New event types: `CamelToolCallAllowed`, `CamelToolCallDenied(toolName, reason)`, `CamelInterpreterError(kind)`, `QuarantinedLlmCall(outputSchema, success)`. Goes through the existing observability schema in `Logging.kt` |
| **Settings** | New flag `isCamelEnabled` for gradual rollout; default false until validation passes |

### 12.7 Out of scope for the first cut

- **Full sandboxed code execution.** First cut uses a plan DSL (Decision A
  option 3). Python-subset interpreter is a follow-up.
- **Cross-process isolation of Q-LLM.** Reference runs all LLMs in the
  same Python process. Genesis first cut runs them in the same Android
  process. Sandboxing the Q-LLM in a separate process is a follow-up if
  attack-modeling requires it.
- **Dynamic capability negotiation.** First cut uses a static
  `SecurityPolicyEngine`. Runtime user-prompted capability grants
  ("genesis is asking to read your private notes — allow?") come later.
- **Capability flow through external storage.** If a CaMeL run writes a
  value to the `ContextStore` and a future CaMeL run reads it back, the
  capabilities need to be persisted alongside. First cut: don't persist
  CaMeL-managed values to the context store; they live only within one
  agent run.

### 12.8 What I had wrong in the first draft

The first version of this section named primitives like `Provenance` /
`Trust` / `CapabilityPolicy` / `ToolCallBoundary` that don't exist in the
reference. The actual primitives are `Capabilities` (compound — sources +
readers + metadata), `Value<T>` (the runtime carrier), `SecurityPolicy`
(the per-tool decision function), `SecurityPolicyEngine` (the dispatcher),
and `FunctionCall` (the audit record). The reference treats provenance as
a field of `Capabilities` (`sources_set`), not as a separate type, and
treats the access-control side (`readers_set`) symmetrically. There is no
"`ToolCallBoundary`" — tools just register with their declared
`Capabilities` and `dependencies`, and the interpreter handles the call
boundary uniformly via the security policy engine.

---

## 13. Presence layer (⚠️ first pass — surfaces landed, orchestration partial)

The user-facing "ambient agent" surface — distinct from the main Nav3 app
shell (§10). Where Nav3 is the settings/debug/actions explorer, **Presence is
what the user actually sees during ambient use**: an orb that parks at the
screen edge, expands into a tab + card stack when there's something to surface,
and can be projected to the glass capture target. Design-led aerospace/HUD
visual language, separate token palette from the main Compose theme.

> Per the user: "almost as important as the models themselves" — the
> on-device assistant succeeds or fails on this surface, not on the
> settings screen.

### 13.1 Components

| Component | File | Status | Notes |
|---|---|---|---|
| Sealed state | `presence/PresenceState.kt` | ✅ | `Hidden`, `Parked(mode, activity)`, `Working(taskId, phase, pillText?)`, `NeedsInput(taskId, prompt)`, `Ready(taskId, cards, primaryAction?)`, `Dismissed(until)` |
| Modes / phases | same | ✅ | `PresenceMode { ORB, TAB }`; `OrbActivity { IDLE, LISTENING, RECENTLY_DISMISSED }`; `WorkPhase { STARTED, SYNCING, PROCESSING, FINALIZING }` |
| Card payload types | same | ✅ | `DeepResearch(query, sources, activeTab)`, `SendResults(draftReply, recipients)`, `CalendarTimer(focusMinutes, focusSeconds, reminders)` |
| Action types | same | ✅ | `CardAction { id, label, sublabel?, type }`; `CardActionType { DEFAULT, PRIMARY, DESTRUCTIVE, COLLAPSE }` |
| Aerospace/HUD palette | `presence/PresenceTokens.kt` | ✅ | Separate from `CosmoTokens`; glass surfaces (low/medium/high), cyan glow edges, dim/bright borders |
| Orb surface | `presence/surfaces/OrbSurface.kt` | ✅ | Parked orb with idle/listening/recently-dismissed visuals + Compose animations |
| Tab surface | `presence/surfaces/TabSurface.kt` | ✅ | Expanded form |
| Card stack | `presence/surfaces/CardStackSurface.kt` | ✅ | Vertical glass-panel stack dispatched on `CardPayload` |
| Glass panel primitive | `presence/surfaces/GlassPanel.kt` | ✅ | Shared base for all surfaces |
| State driver | (not yet a dedicated file) | ⚠️ | Surfaces render against `PresenceState`; the **producer** that maps `GatekeeperOutput` / `FulfillmentResult` / `IntentRecord` transitions into `PresenceState` emissions is the missing piece |
| Projected-XR rendering | binds via `glasses/CosmoProjectedGlassCaptureService` | ⚠️ | `GlassPanel` exists; the service binds; the wiring from `PresenceState` flow → projected surface is not driven today |
| Card payload library expansion | `presence/PresenceState.kt::CardPayload` | ⚠️ | 3 payloads supported; mapping all 22 fulfillment action subtypes (§6) to payloads is incomplete |

### 13.2 The state-machine contract

The presence state-machine is the **observable surface** of the assistant
to the user. Transitions are driven by upstream events:

- `GatekeepingResult` (directed transcript classified) → `Working(taskId,
  phase=STARTED)` if it triggers fulfillment
- `IntentRecord` (from §5 IntentExtractor) → `Working(... phase=PROCESSING,
  pillText=intent.intent)` to surface in-flight intent
- `FulfillmentEvent.NeedsConfirmation(action)` → `NeedsInput(taskId,
  prompt)`
- `FulfillmentEvent.Executed(result)` → `Ready(taskId, cards,
  primaryAction)`
- User-side `dismiss(taskId, until)` → `Dismissed(until)` → eventually
  back to `Hidden` or `Parked`

The driver lives architecturally between `FulfillmentOrchestrator` (§6) and
the surfaces. **It is not yet implemented.** When it lands, it's a single
class (`PresenceStateMachine` or similar) that observes
`gatekeeper.results`, `intentBuffer`, and `fulfillment.events`, and exposes
`state: StateFlow<PresenceState>` that the surfaces collect.

### 13.3 Trust-layer interlock

When CaMeL (§12) lands, the `Ready` state's `primaryAction` and the
`NeedsInput` prompt become tool-call boundaries the interpreter consults
through `SecurityPolicyEngine`. The presence layer is where the user
gives capability grants (the "allow / deny / always allow" UI), so
trust-layer dynamic capability negotiation flows through Presence.

---

## 14. AppFunctions surface (Android 17 / API 36 — ⚠️ first pass)

Exposes FM's capabilities as **Android AppFunctions** so other apps and the
system Assistant runtime can invoke them. AppFunctions is the Android 17
public API for cross-app function-calling: a typed RPC contract that the
platform AI agents discover and call, replacing the older Slices /
intent-actions / app-action surface.

### 14.1 Functions exposed today

`appfunctions/AmbientAppFunctions.kt` declares these methods:

| Function | Status | Routes to |
|---|---|---|
| `getAmbientStatus(): AmbientStatus` (state, ambientAudioOn, budsAudioOn, watchAudioOn, glassOn, lanDiscoveryOn) | ✅ | `CosmoController` + `CosmoStateManager` |
| `startAmbientSession()` | ✅ | `CosmoStateManager.enableCosmo()` |
| `stopAmbientSession()` | ✅ | `CosmoStateManager.disableCosmo()` |
| `snoozeAmbientSession(duration: Duration)` | ✅ | `CosmoStateManager.setSnoozeUntil(...)` |
| `queryRecentContext(query: String, limit: Int = 10): List<String>` | ✅ | `ContextStore.queryByText` |
| `summarizeRecentContext(query: String): String` | ✅ | `queryRecentContext` → bullet-joined |
| `openTaskMonitor(title, prompt, agentResponse): Boolean` | ✅ | `TaskMonitorNavigationBridge.openTaskMonitor(...)` |
| `runUserConfirmedQuery(query, conversationHistory)` | ✅ | `FulfillmentOrchestrator.handleUserQuery(...)` |

### 14.2 Platform binding (incomplete)

| Slot | Status | Notes |
|---|---|---|
| `@AppFunctionSerializable` on data types (`AmbientStatus`) | ❌ | Required for the platform's type registry; not applied |
| `@AppFunctionSchema` or `@AppFunction` on methods | ❌ | Required for discovery by the platform Assistant agent; not applied |
| `AppFunctionService` subclass + manifest registration | ❌ | Required service entry-point; not present |
| Per-function permission gating | ❌ | First-pass methods are permissive. Session control (`startAmbientSession`, `stopAmbientSession`, `snoozeAmbientSession`) MUST require a system confirmation surface (e.g., `RequestPermission` flow) before being callable by a 3P app |
| Trust-layer interlock | 🔵 | When CaMeL (§12) lands, each AppFunction registers as a `CamelFunction` with its own `Capabilities`; permission checks route through `SecurityPolicyEngine` |

When platform binding is added: each method gets `@AppFunctionSchema`,
data types get `@AppFunctionSerializable`, `AppFunctionService` is added
to `AndroidManifest.xml`. The current pure-Kotlin class shape is the
**logic**; the annotations + service binding are the **discovery + invocation**
layer the platform demands.

---

## 15. Server-side workstreams

Genesis is on-device-first. Several cosmo functions had server-side
counterparts; this section lists those at the system-design level and
flags whether genesis builds an equivalent, replaces it, or skips it.

> Privacy model: anything that flows off-device is opt-in, audited, and
> (when CaMeL lands) routes through the trust layer (§12) via capability
> checks at the tool-call boundary.

### 15.1 Mapping cosmo server-side to genesis

| Cosmo server-side function | Genesis approach | Status |
|---|---|---|
| **LLM inference** (BARD / LaMDA backend) | On-device first: AICore (Gemini Nano), LiteRT-LM (on-device Gemma 4 bundle). Remote inference (Oak) as fallback | ✅ on-device live; ⚠️ remote needs endpoint + cert pin config |
| **Private inference transport (Oak session)** | `RemoteInferenceClient` + Oak session config via `liboak_session_config_builder_jni.so` + `libpi_client_session_config_jni.so` (recovered, not yet bundled) | ⚠️ transport ready; live endpoint not provisioned; Oak libs need bundling |
| **DBW data pipeline** (server-side training-data ingestion of accessibility-tree captures for screen-understanding model distillation) | See §15.2 below — client side partly built; server pipeline is a future workstream | 🔵 planned (system-level only) |
| **BARD Workspace grounding service** (server fetch of Gmail/Calendar/Drive content) | Replaced — genesis uses on-device Android content providers (`ContextStore` + per-source ephemeral buffers). User owns the read path | 🟡 explicitly skipped |
| **Prompt-injection classifier service** (BARD's `PromptInjectionWorkspaceMetadata`-driven scanner) | Replaced by on-device CaMeL trust layer (§12) — control/data flow separation by design, not classifier-by-classifier | 🔵 planned (CaMeL — different mechanism, same threat) |
| **Browsing-agent backend** (Mariner — drives TaskMonitor surface) | See §15.7 for the cosmo gRPC contract (`com.google.labs.mariner.v1.ActionService`) and genesis port plan. Tier-2 path is Browser-Use behind the §15.4 transport | ⚠️ UI live; agent backend not built (§15.7) |
| **Personal Intelligence (PI) service** (Google's personal-data aggregation backend) | Replaced — on-device `ContextStore` is the equivalent. No remote aggregation | 🟡 explicitly skipped |
| **Telemetry aggregation** (sink for 309 typed Firebase events) | Firebase Analytics handles ingestion; no custom backend | ✅ |
| **Model delivery / refresh** (LiteRT-LM bundle, audio gatekeeper assets, SODA packs, future Inherent model) | Two-tier: bundled-in-APK for stable assets (BERT, SODA packs); HTTPS download for variable-size bundles (LiteRT-LM, Inherent) | ⚠️ provisioning works locally; production CDN not configured |

### 15.2 DBW (Distill-By-Watching) — what cosmo had vs what genesis builds

**Cosmo recovered surface** (`~/Downloads/cosmo_baksmali/classes17/com/google/intelligence/dbw/`
+ `classes43/intelligence/dbw/proto/`): a **structured accessibility-tree
capture library**, not a training pipeline. The "Distill-By-Watching" name
refers to what the *server-side* did with these uploaded captures.

On-device library shape:
- `ImmutableAndroidAccessibilityForest` — collection of trees (one per window)
- `ImmutableAndroidAccessibilityTree` — a single window's hierarchy
- `ImmutableAndroidAccessibilityNode` — node with parent/child edges, clickable-span info, available actions
- `ImmutableAndroidAccessibilityWindow` — window metadata (type, bounds)
- `AccessibilityEventListener` / `AccessibilityServiceConnectionListener` / `AccessibilityTreeCache` / `ConnectedAccessibilityServiceProvider`
- Proto layer: `AndroidAccessibilityForest`, `AndroidAccessibilityTree`,
  `AndroidAccessibilityNodeInfo`, `AndroidAccessibilityWindowInfo`,
  `AndroidAccessibilityAction` + geometry types (`Point`, `PointF`,
  `Rect`, `Line`)

**Genesis state**:

| Layer | Status | Notes |
|---|---|---|
| Flat event-based screen capture | ✅ | `interactioncapture/ScreenInteractionCaptor` emits `ScreenInteraction(timestamp, type, packageName, windowId, text)` events; `contentcapture/PeriodicScreenCaptor` polls for screen text. Narrower than cosmo's full tree forest |
| Structured accessibility-tree capture | 🔵 | Not built. Architectural slot: `contentcapture/AccessibilityForestCaptor.kt` (new). Same `ConnectedAccessibilityServiceProvider` pattern; emits the full forest per window-change event |
| Tree-shaped proto schema (`AndroidAccessibilityForest`, etc.) | 🔵 | Not built. Either port cosmo's protobufs (recovery already extracted them) or define genesis-narrower equivalents under `contentcapture/proto/` |
| Server-side ingestion endpoint | 🔵 | Not built — out of this doc's scope at the implementation level. Contract surface: "opt-in uploads of forests, returns refined models." Capability-gated through CaMeL when present |
| Server-side distillation training | 🔵 | Not built. System-design only: ingest forests → de-identify (redact text, hash IDs) → screen-understanding model training run → output goes to model-manifest CDN for next bundle |
| Returned-model delivery | (uses §15.1 model delivery) | ⚠️ | Same path as LiteRT-LM bundle refresh |

The server piece is described **at the architecture level only**. Specific
pipeline implementation (data store choice, training framework, K8s job
orchestration) is left to server-team timing. Genesis treats the server side
as a contract surface: "given opt-in captures, return better
gatekeeper/intent models," with mechanism chosen later.

### 15.3 What genesis explicitly chooses NOT to mirror

| Cosmo surface | Why genesis skips it |
|---|---|
| **PI service** (Personal Intelligence) | On-device `ContextStore` is the privacy-preserving equivalent; no remote aggregation of personal data |
| **Workspace grounding** (server fetch of Gmail/Calendar/Drive) | Android content providers + user permissions own the read path on-device; no server fetch |
| **Server-side prompt-injection classifier** (`PromptInjectionClassifierAbuseMetadata`, `PromptInjectionWorkspaceMetadata` protos found in `cosmo_baksmali` proto deps) | Replaced by CaMeL on-device (§12) — different defense mechanism (architectural separation vs statistical classifier), same threat (prompt injection) |

These are conscious privacy-driven choices, not gaps. The on-device path
delivers equivalent functionality without an off-device data round-trip.

### 15.4 Private inference HTTP transport (⚠️ structural complete; no live endpoint)

When on-device LLMs (AICore / LiteRT-LM) can't fulfill a query — model not
available, prompt exceeds context window, low device battery — genesis falls
back to a **remote inference endpoint**. The transport is fully wired in
genesis as a contract; the production service it talks to isn't running yet.

**Client-side files** (`app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/`):

| File | Role |
|---|---|
| `LLM.kt` | `RemoteInferenceClient` interface — `infer(parts, onToken): String` |
| `RemoteInferenceClient.kt` | `OkHttpRemoteInferenceClient` impl + `RemoteHttpClientFactory` |
| `RequestSigner.kt` | `AndroidKeyStoreRequestSigner` — P-256 ECDSA, key alias `cosmo_remote_request_signing_v1` |
| `RemoteCredentialStore.kt` | `AndroidKeyStoreRemoteCredentialStore` — Keystore AES-GCM encrypted credential storage |

**HTTP contract** (`docs/remote-inference-contract.md`):

| Endpoint | Method | Purpose |
|---|---|---|
| `/v1/private-token/issue` | POST | Issue an anonymous credential bound to the device's signing public key |
| `/v1/inference` | POST | Run inference; consume one credential per call; return JSON or SSE stream |

**Request signing canonical payload** (SHA256withECDSA):

```
METHOD\n
/path?query\n
timestamp_millis\n
nonce\n
base64url_sha256_body
```

**Headers per request:** `Authorization: PrivateToken <token>`,
`X-Cosmo-Timestamp-Millis`, `X-Cosmo-Nonce`, `X-Cosmo-Body-SHA256`,
`X-Cosmo-Signature-Key-Id`, `X-Cosmo-Signature-Alg=SHA256withECDSA`,
`X-Cosmo-Signature` (base64url-encoded signature of the canonical payload).

**SPKI pinning:** when `cosmo.remoteInference.certPin` is set, the client
pins the configured Subject Public Key Info SHA-256 hash. Required for
production; blank in default builds.

**What's required server-side to make this live:**

1. Issue an SPKI cert + decide on a pin format
2. Stand up the `/v1/private-token/issue` issuer service (see §15.6 for the proto)
3. Stand up the `/v1/inference` service:
   - Verifies `Authorization: PrivateToken <token>` against the issuer
   - Verifies `X-Cosmo-Signature` against the device's pinned public key (key ID embedded in token)
   - Validates timestamp freshness (replay window — recommend 5 min)
   - Validates `X-Cosmo-Body-SHA256` matches actual body hash
   - Performs inference (any LLM backend — vLLM, TGI, Triton, etc.)
   - Returns JSON envelope `{output, model_id, usage}` or SSE stream of token events
4. Provision Gradle properties: `cosmo.remoteInference.baseUrl`,
   `cosmo.remoteInference.certPin`, `cosmo.remoteInference.audience`

**Status:** client structure complete; no live endpoint, no
deployment-side service. Tier-2 fallback per
[`docs/fallback-paths.md`](fallback-paths.md): vanilla self-hosted FastAPI
+ Caddy front-end with `Authorization: Bearer` for staging.

### 15.5 Oak confidential-session compute (🔵 PLANNED — not wired)

Cosmo's recovered upstream architecture cites a separate inference path
called `PrivateInferenceArateaLLM` that uses an **Oak session** —
attested, encrypted RPC over a Noise protocol channel, with the server
side running inside a TEE (Trusted Execution Environment). The relevant
recovered binaries are present in
`~/Downloads/cosmo_recovered_native_libs/lib/arm64-v8a/`:

- `liboak_session_config_builder_jni.so` — Oak client session config
- `libpi_client_session_config_jni.so` — Private Inference client session config

Neither is bundled in genesis. The pure-HTTPS + Private Tokens path (§15.4)
is the **single production transport today**; Oak is reserved as a stronger
confidentiality tier for sensitive workloads.

**What Oak provides** (above and beyond TLS):
- **Remote attestation** — the client verifies the server is running an
  attested TEE image (e.g., AMD SEV-SNP, Intel TDX) before establishing
  the session. The client doesn't trust the server operator, only the
  attested workload binary.
- **Noise-protocol session keys** — encrypted RPC payloads with forward
  secrecy; the server operator can't decrypt traffic even with TLS keys.
- **Open-source verifiable** — Oak is at [github.com/project-oak/oak](https://github.com/project-oak/oak).
  The attestation chain is auditable end-to-end.

**Genesis port plan** (when Oak workstream activates):

1. **Bundle the recovered libraries:** add `liboak_session_config_builder_jni.so`
   and `libpi_client_session_config_jni.so` to
   `app/src/main/jniLibs/arm64-v8a/`. Both are gitignored — provision via
   `scripts/provision-from-recovery.sh` (extend the script's
   `MAP` array to include them).
2. **Reflection-bridge the JNI surface:** like the AOC `NanoAppClient`
   pattern (§2.2). Define a `OakSessionConfigBuilder` Kotlin class that
   loads the .so and exposes `buildSession(endpoint, attestation): OakSession`.
3. **Add a `PrivateOakLLM` backend** alongside `OkHttpRemoteInferenceClient`,
   wrapping the Oak session as another `LLM` implementation.
4. **Server side:** stand up an Oak Restricted Kernel + Oak Containers
   workload that hosts the LLM inference; configure attestation roots.
   This is the heavy part — Oak operations are non-trivial, the Oak docs
   at oak's repo are the authoritative reference.

**Status:** 🔵 planned. Native libs recovered but not bundled. Zero
client-side or server-side code today. The HTTPS + PrivateToken path is
sufficient for the current threat model. Activate Oak when:
- A specific workload (e.g., medical-context queries) has a confidentiality
  requirement that survives a compromised server operator, OR
- A regulatory regime demands TEE-attested compute for the inference path.

**Tier-2 fallback** per `docs/fallback-paths.md`: stay on the §15.4 HTTPS
transport indefinitely; add Oak only when confidentiality > convenience.

### 15.5a What `liboak_session_config_builder_jni.so` actually does

For the eventual port, here's what the recovered binary exposes (per the
upstream `$COSMO_DEV_ROOT/repo/docs/audio_stack_map.md` and adjacent recovery
notes, cross-referenced against the public Oak repo):

1. **`OakSessionConfigBuilder`** — fluent builder that produces an
   `OakSessionConfig` proto. Key fields: endorsement reference values
   (the expected attestation evidence), client static key pair (Noise),
   handshake type (`NN`, `NK`, or `KK` per Noise spec). Cosmo configures
   for `NK` (server-static-known) so the client can verify the server's
   long-term identity.
2. **`OakSession`** — once the config is built, this object is the
   stateful session: client-side handshake state, encrypt/decrypt
   methods, send/receive over an arbitrary transport (cosmo wraps gRPC).
3. **Attestation verifier** — invoked during handshake. Validates the
   server's attestation evidence (TEE measurement + endorsement chain)
   against the configured reference values.

The Kotlin port wraps these via JNI reflection like `NanoAppClient` does for
Context Hub. Server side runs a parallel Oak workload (Rust, per Oak's
canonical implementation).

### 15.6 Anonymous credentials & private tokens (⚠️ client done; issuer service needed)

The auth model for the §15.4 inference endpoint is **unlinkable** — the
inference server learns "this request came from a device with a valid
token," not "this request came from device X." Devices get short-lived
tokens from an issuer service; the issuer learns "device X requested N
tokens" but not what they're for; the inference server learns "this is
a valid token" but not which device. Conceptual inspiration: IETF
Privacy Pass / Private Access Tokens
([RFC 9577](https://datatracker.ietf.org/doc/rfc9577/)).

**Client-side files** (already wired):

| Type | File | Role |
|---|---|---|
| `AnonymousCredentialProvider` | `LLM.kt` | Returns a usable credential, refreshing if needed |
| `AnonymousCredentialIssuerClient` | `LLM.kt` | Wraps the HTTP call to the issuer |
| `OkHttpAnonymousCredentialIssuerClient` | `LLM.kt` | Production impl |
| `RefreshingAnonymousCredentialProvider` | `LLM.kt` | Refresh-before-expiry logic with Clock injection |
| `RemoteCredentialStore` | `RemoteCredentialStore.kt` | AES-GCM-encrypted DataStore-backed persistence |
| `AndroidKeyStoreRemoteCredentialStore` | `RemoteCredentialStore.kt` | Keystore-wrapped AES-GCM impl |

**Issue protocol:**

```
POST /v1/private-token/issue
Request:
{
  "audience": "cosmo-remote-inference",
  "client": "genesis-android",
  "nonce": "base64url-random-16-bytes",
  "request_signing_public_key_spki": "base64url-der-subject-public-key-info"
}

Response:
{
  "authorization_header": "PrivateToken token-value",
  "expires_at_epoch_millis": 1770000000000
}
```

The token is opaque to the client. The issuer can mint stateful or
stateless tokens; the inference server validates them via either a
shared verification key (stateless JWT-style) or a per-token lookup
(stateful Redis-style).

**What's required server-side to make this live:**

1. **Issuer service**:
   - Accepts the JSON above on `POST /v1/private-token/issue`
   - Optionally rate-limits per-public-key (e.g., 1000 tokens/day)
   - Optionally audits the public-key → token mapping (for abuse response, NOT linkability to inference traffic)
   - Returns a signed token + expiry timestamp
2. **Inference-side validator**:
   - Validates the token signature (or looks it up)
   - Validates the token hasn't expired
   - Validates the device signature matches the public key the token was bound to
3. **Optional issuer flavors:**
   - **Anonymous** — issuer cannot link tokens to inference traffic at all (use blinded signatures, true Privacy Pass)
   - **Pseudonymous** — issuer can link but inference cannot (simpler; genesis's current shape)
   - **Identified** — both sides know the user (don't use; defeats the purpose)

**Status:** ⚠️ client-side complete. No production issuer service. Local
test fixtures can spoof the issuer for dev (`SimulationLLM` bypasses the
whole path). For production, the issuer service is **the smallest server
component genesis needs to stand up** — could be a single FastAPI
endpoint that issues HMAC-signed tokens with a 1-hour TTL. ~150 LOC of
server work.

### 15.7 Mariner browsing-agent backend (⚠️ client wired; backend absent)

The recovery surfaces concrete cosmo wire-level shape for the
browsing-agent backend that drives the TaskMonitor UI surface (§6). Per
`~/Downloads/cosmo_baksmali/.../fulfillment/actions/mariner/MarinerAction.smali`:

- **Transport:** gRPC, `io.grpc.ManagedChannel`
- **Stub:** `com.google.labs.mariner.v1.ActionServiceGrpcKt$ActionServiceCoroutineStub`
- **Entry point:** `execute(query: String): Flow<MarinerState>` — server-streaming RPC that takes a natural-language query and streams `MarinerState` updates back as the agent navigates pages, extracts content, and resolves the task
- **Auth in cosmo:** `OnePlatformAuth.smali` — Google's internal enterprise auth; **deeply Google-tied**, genesis replaces with generic OAuth/JWT bearer auth on the gRPC metadata

**`MarinerState` proto** (per recovery): includes progress phase, current
URL being browsed, extracted content snippets, agent thoughts, and the
final answer. The TaskMonitor UI renders this stream as prompt/response
bubbles (`fulfillment/actions/mariner/TaskMonitorActivity.kt` in genesis
today renders against this same shape).

**Genesis status:**

- **Client side:** `TaskMonitorActivity` + `TaskMonitorNavigationBridge` ship (§6). The view renders incremental agent updates.
- **gRPC stub:** the `com.google.labs.mariner.v1` proto + generated stub are NOT in genesis. Would either be regenerated from a cosmo-recovered `.proto` file (if extractable) or replaced with a genesis-defined contract that the genesis-side backend speaks.
- **Backend:** not built. **This is the heaviest unbuilt server-side surface** — Mariner backend is essentially a tool-using LLM agent that controls a headless browser.

**Server-side requirements for a genesis-equivalent Mariner backend:**

1. **gRPC service** — `service ActionService { rpc Execute(ExecuteRequest) returns (stream ActionEvent); }` or equivalent (could use SSE-over-HTTP via the §15.4 transport instead, removing the gRPC dep)
2. **Browser automation backend** — Playwright (Python/Node), Selenium, or Browser-Use (LangChain-ecosystem). Browser-Use is the closest off-the-shelf match
3. **LLM tool-use loop** — the LLM proposes a plan, executes one browser action, observes the page state, repeats. Tier-2 per `docs/fallback-paths.md` is Browser-Use with GPT-4o or Claude as the LLM
4. **Auth** — bearer token / Authorization header. The existing §15.4 + §15.6 (PrivateToken) infrastructure is sufficient; no need to mirror OnePlatformAuth
5. **Result streaming** — incremental state updates so the user sees progress; matches the existing `Flow<MarinerState>` consumer pattern

**Effort estimate** (per `docs/fallback-paths.md`): ~1 week to wire
Browser-Use behind the §15.4 transport. The TaskMonitor UI is already
built; this is just the backend service.

**Status:** 🔵 planned. The cosmo gRPC contract is the spec; the backend
is the work. Until built, "browsing agent" actions surface a graceful
"not yet available" downgrade.

### 15.8 What's NOT a server-side genesis target

While scanning the recovery, several cosmo classes that *look* server-side
turned out to be **cross-app Android IPC** or **deeply Google-internal**
surfaces that genesis doesn't mirror:

| Cosmo class | What it actually is | Why not server-side genesis work |
|---|---|---|
| `gboard/GBoardSuggestionsService` | Android `Service` exposing cosmo context to the GBoard keyboard via IPC | Cross-app IPC, not server. If genesis wants the same surface, it ships as a sibling to `AppFunctions` (§14) — not as a backend |
| `altair/AltairActionSynchronizer` | Binds to `ICosmoAmbientContextService` from the `com.google.research.air.experiences.glimmer.altair` Android app (XR display surface) | Cross-app IPC to a different first-party app. Genesis can replicate via Android service binding; no server involved |
| `voiceassistant/MainInteractionService` + `MainInteractionSessionService` | Android `VoiceInteractionService` / `VoiceInteractionSessionService` — the platform Assistant role binding | Platform binding, not server-side |
| `voiceassistant/MainRecognitionService` | Android `RecognitionService` — voice-input platform binding | Platform binding |
| `lib/google/auth/AuthManager` | Wraps Google account auth via Google Play Services | Genesis uses generic OAuth/JWT; deeply Google-tied; skip |
| `agents/proactivesuggestions/InvokeAgentQ` | Action type that routes a "ask AgentQ" query to a Google-internal Q&A backend | Genesis replaces with generic LLM via existing fulfillment + remote-inference path; "AgentQ" name is Google-internal |
| `skills/generation/SkillGenerator`, `GatekeepingGenerator` | App-start LLM calls that synthesize skill definitions from `filtered_skill_schema.textproto` | Uses generic `LLM` interface; no dedicated backend — routed via the existing LLM clients (§9) |
| `agents/proactivesuggestions/*` (whole package) | On-device proactive-suggestion ML pipeline — `AmbientData` → `Context` → LLM → `ProactiveSuggestion` | Entirely on-device per recovery (no gRPC stubs or HTTP clients inside the package); just needs the LLM clients (§9) to be live |

This list is the **deliberate scope cut**: surfaces that look server-side
but either run on-device, route through existing infrastructure, or are
deeply Google-internal and replaced with off-the-shelf alternatives.

---

## 16. Voice output (🟢 Sherpa replacement path)

**Status: active replacement path.** The Android `TextToSpeech` shim verifies
that spoken output is wired through the app, but real-device quality is not
acceptable for the product voice. The launch path is now Sherpa-ONNX
Supertonic-3 with Android `TextToSpeech` retained as the emergency fallback.

Cosmo's recovered code includes a full on-device TTS stack. Genesis intentionally
does not depend on that private stack for launch. This section preserves the
placement and the behavioral shape we should copy.

### 16.1 What cosmo had

| Cosmo component | Files (recovered) | Function |
|---|---|---|
| `lib/tts/TTS` interface | `audio_stack_map.md §I.1` | Unified TTS contract |
| `lib/tts/VoiceLMTTS` | `audio_stack_map.md §I.2`, `startVogoVoicelmTask` | Generative voice via VoiceLM + Vogo native lib |
| `lib/tts/LocalTTS` | `audio_stack_map.md §I.6` | Android `TextToSpeech.OnInitListener`-based fallback |
| `lib/tts/AudioRenderer` | `audio_stack_map.md §I.4` | PCM playback pipeline |
| `lib/tts/AudioResampler` | `audio_stack_map.md §I.5` | Sample-rate conversion |
| VoiceLM assets | `voicelm/encoder_int4-graph-custom_op.tflite`, `merged_depth_decoder_int4-graph-custom_op.tflite`, `temporal_decoder_int4-graph-custom_op.tflite`, `soundstream_{encoder,decoder,quantizer}.tflite`, `preprocessing.tflite`, `speaker_encodings_100ms.pb` | The generative voice model + neural codec |
| Vogo native lib | `libvogo_jni.so` | JNI shim for VoiceLM inference |

The recovered `temporal_decoder_int4-graph-custom_op.tflite` is **truncated** —
`audio_stack_map.md §H` flags 64.8MB missing of 112.5MB total. Even if the
slot were activated, the asset would need re-acquisition. The recovered app also
logged `NOT_FOUND: No suitable capability found` while initializing the Vogo
VoiceLM capability, which means the current recovered binary/asset set is not a
ship-ready path.

### 16.2 Launch architecture

Use the existing `lib/voice/VoiceOutput.kt` contract:

| Slot | Genesis location |
|---|---|
| `VoiceOutput` interface | `app/src/main/kotlin/.../lib/voice/VoiceOutput.kt` |
| Sherpa implementation | `lib/voice/SherpaVoiceOutput.kt` — wraps the selected Sherpa-ONNX TTS backend |
| Android fallback | `lib/voice/AndroidTextToSpeechVoiceOutput.kt` (split from the current file) |
| Playback | `AudioTrack` streaming PCM float32/short buffers, matching Cosmo's `AudioRenderer` shape |
| Cosmo facade | `lib/tts/TTS.kt`, `VoiceLMTTS`, `LocalTTS`, `AudioRenderer`, `AudioResampler` |
| Assets | External model bundle/cache, not Git. Launch bundle: `supertonic-3`; fallback/benchmark bundle: `kitten-mini-en-v0_1-fp16`; comparison bundles: `kokoro-int8-multi-lang-v1_1` and direct E2E `aufklarer/Kokoro-82M-ONNX` |

The current integration uses Sherpa-ONNX because it already exposes the right
Android/Kotlin TTS abstraction:

```
OfflineTts.generateWithConfigAndCallback(text, GenerationConfig(...)) { samples ->
    audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
}
```

That is structurally close to Cosmo's `VoiceLMTTS`: initialize one native
engine, keep a reusable task/runtime object, submit a text utterance, stream
audio into an `AudioTrack`, and provide `stop()` by cancelling/flushing the
active utterance.

The selected launch model is `supertonic-3`. Local Sherpa benchmarking across
20 assistant utterances put Supertonic-3 at aggregate RTF 0.178 and mean
synthesis 0.728s, compared with Kitten at aggregate RTF 0.441 and mean
synthesis 2.114s on the same host run. The model lives under
`app/src/main/assets/models/tts/supertonic-3` when hydrated, but that directory
is gitignored; rehydrate with `scripts/download-models.sh` (S3 manifest). Maintainers bootstrap from HF via `scripts/bootstrap-tts-from-upstream.sh`, then `scripts/pack-tts-archives.sh` + `scripts/upload-models-to-s3.sh`.
Kitten remains a fallback/benchmark bundle. If later hardware acceleration
makes another model practical, switch only the model bundle; the `VoiceOutput`
interface and playback architecture stay unchanged.

### 16.3 Research-only Cosmo placement

If VoiceLM/Vogo becomes viable later:

| Slot | Genesis location |
|---|---|
| `TTS` interface | `lib/tts/TTS.kt` — recovered sync API |
| `VoiceLMTTS` impl | `lib/tts/TTS.kt` — launch replacement delegates to `VoiceOutput`; a future Vogo-backed impl can keep the same binding |
| `LocalTTS` impl | `lib/tts/TTS.kt` — wraps `android.speech.tts.TextToSpeech` for the always-available path |
| `AudioRenderer` | `lib/tts/AudioRenderer.kt` |
| `AudioResampler` | `lib/tts/AudioResampler.kt` |
| `libvogo_jni.so` | `app/src/main/jniLibs/arm64-v8a/` — recovered binary exists at `~/Downloads/cosmo_recovered_native_libs/`; not bundled |
| VoiceLM assets | `app/src/main/assets/models/voicelm/` — recovered tflites exist at `~/Downloads/cosmo_recovered_large_assets/`; not bundled. `temporal_decoder` needs re-recovery. |

Nothing in the current code should depend on `lib/tts/` for launch. Fulfillment
action handlers that produce speakable output should depend only on
`VoiceOutput`; the backend behind that binding can be Sherpa, platform TTS, or a
future recovered VoiceLM provider.

---

## 17. Appendix: Recovered-but-unwired surfaces

A catalog of things genesis could enable without writing new infrastructure.
None are blockers; all are inventory.

### 14.1 Native libraries recovered but not bundled in `jniLibs/`

Found at `~/Downloads/cosmo_recovered_native_libs/lib/arm64-v8a/`:

| Library | Subsystem | Why not bundled |
|---|---|---|
| `libvogo_jni.so` | VoiceLM/TTS | Voice output deferred (§16) |
| `libsbcdecoder_jni.so` | Pixel Buds SBC | Genesis uses `ReflectiveSbcAudioDecoder` (MediaCodec-based) — `.so` not needed |
| `liboak_session_config_builder_jni.so` | Oak private-compute sessions | Genesis remote inference uses plain TLS over OkHttp, not Oak sessions |
| `libpi_client_session_config_jni.so` | Personal Intelligence service binding | Genesis doesn't bind to PI |
| `libbert_gatekeeper_jni.so` | BERT JNI | Genesis ships clean-room source (`app/src/main/cpp/bert_gatekeeper_jni.cc`) instead of vendoring the binary |
| `libtensorflowlite_jni_stable.so` | TFLite alt runtime | Duplicate of bundled `libtensorflowlite_jni.so` |
| `libfilterframework_jni.so` | Camera filter framework | Camera capture exists but doesn't use filter framework |
| `libimage_processing_util_jni.so` | Image utils | Same — not used |
| `libsurface_util_jni.so` | XR surface utils | Glass capture uses different path |
| `libandroidx.graphics.path.so` | Compose path stroker | AGP ships its own |

### 14.2 Recovered model assets not bundled in `assets/models/`

Found at `~/Downloads/cosmo_recovered_large_assets/`:

| Asset | Subsystem | Status |
|---|---|---|
| `voicelm/encoder_int4-graph-custom_op.tflite` (137M) | VoiceLM | Deferred (§16) |
| `voicelm/merged_depth_decoder_int4-graph-custom_op.tflite` (48M) | VoiceLM | Deferred |
| `voicelm/temporal_decoder_int4-graph-custom_op.tflite` (truncated, 64MB missing) | VoiceLM | Deferred + needs re-recovery |
| `voicelm/soundstream_{encoder,decoder,quantizer}.tflite` | VoiceLM neural codec | Deferred |
| `voicelm/preprocessing.tflite` (~10K) | VoiceLM preprocessor | Deferred |
| `voicelm/speaker_encodings_100ms.pb` | VoiceLM speaker conditioning | Deferred |
| `gecko/gecko_out256_tpu_v0.tflite` | Sentence embedder | Deferred (§7.2) |
| `gecko/i18n_sentencepiece.model` | Sentencepiece tokenizer | Deferred |

### 14.3 Recovered code surfaces with no genesis consumer

| Surface | Cosmo source | Genesis | Recovery doc |
|---|---|---|---|
| Voice Match enrollment UI | `VoiceMatchViewComposable` + `VoiceMatchAudioFetcherImpl` | Not ported | `audio_stack_map.md §I`, `dagger_graph_map.md` |
| `SodaEnrollmentManagerImpl` callers | Multiple in cosmo | JNI stub class exists; no callers | `audio_stack_map.md §I` |
| Pumpkin semantic grammar consumer | None even in cosmo (G5-resolved) | Asset bundled, never read | architecture-delta §G5 |
| HotwordEvent routing | Cosmo routed hotword events to wake flows | Genesis logs only | architecture-delta C8 |
| Diarization config + consumer | Cosmo had `clearDiarizationCache`, `nativeDeleteDiarizationProcessor` | Config not enabled, `speakerLabel` always `""` | `audio_stack_map.md` line 197 |
| mt6989 BERT variant | AOT-compile pipeline drafted | Manifest slot reserved; no model | `bert_gatekeeper_litert_jni_replacement.md` |
| Android Auto context grammars | `context_prebuilt/*.fst` in SODA pack | Genesis is not an Auto host | (SODA pack inventory) |
| Offline action data | `offline_action_data.pb` in SODA pack | No genesis-side consumer | (SODA pack inventory) |
| AOC nanoapp callback | Cosmo had platform-signed compile of `ContextHubClientCallback` subclass | Genesis cannot subclass `@SystemApi` abstract class from public SDK build | architecture-delta PR3 |

### 14.4 Why these are inventoried, not built

The intent of this appendix is **not** to schedule the work — it is to make
the slots visible so that:

1. Future workstreams know the shape of cosmo's recovery and don't reinvent it.
2. A reader of `architecture.md` understands what "deferred" means concretely
   per component, instead of treating every uncovered surface as an oversight.
3. The CaMeL trust layer (§12) can place its provenance/capability hooks
   correctly the first time, knowing which captors and consumers exist or
   will exist.

---

## 18. Appendix: Document map

| If you need... | Read |
|---|---|
| The current system architecture (this doc) | `docs/architecture.md` |
| Current operating plan (next milestone, work order, parked non-goals, gate facts) | `PLAN.md` |
| Repository layout — where everything lives | `LAYOUT.md` |
| The C1–C9 contract rules + PR0–PR6 marching orders | `docs/architecture-delta.md` |
| Per-AST-node CaMeL port reference (for the implementing workstream) | `docs/camel-port-notes.md` |
| 3-tier fallback paths per deferred subsystem (cosmo-recovered → commercial/OSS → build it) | `docs/fallback-paths.md` |
| Active blockers / not-yet-resolved issues | `docs/blocker-register.md` |
| Cosmo recovery stack maps (mirrored from `$COSMO_DEV_ROOT/repo/docs/`) | `reference/cosmo-*.md`, `reference/contracts/` |
| Inherent runtime tensor contract + audio corpus collection | `reference/inherent-*.md` |
| AICore public-API surface limits | `docs/aicore-api-gap.md` |
| Audio training corpus plan | `docs/audio-corpus-plan.md` |
| Release signing policy | `docs/release-signing-policy.md` |
| Remote inference HTTP contract | `docs/remote-inference-contract.md` |
| Architecture-update enforcement hook | `docs/architecture-update-hook.md` |
| Recovery-level cosmo internals (smali / proto fields / asset bytes) | `$COSMO_DEV_ROOT/repo/docs/*_stack_map.md`, `$COSMO_DEV_ROOT/repo/docs/architecture.md` |
| Workspace-level decisions, deferred items, scope cuts | `$COSMO_DEV_ROOT/WS4/docs/ws4-known-gaps.md`, `$COSMO_DEV_ROOT/repo/docs/cosmo_apk_consolidated_plan.md` |
| Inherent joint-model runtime contract | `~/inherent/contracts/runtime_contract.md` |

---

## 19. Snapshot summary (2026-05-18)

**Live and exercised today (✅):** capture (ambient + buds + device + watch
+ screen + interactions + notifications + location), SODA stack (per-source
sessions, Magic Mic directedness gate, endpointer / punctuation / denorm /
G2P / E2E ASR running inside SODA), gatekeeping (unified `Gatekeeper`,
C1 mutex via backend type-filter, BERT text path on CPU + Tensor variants,
Combined BERT+regex fusion), C9 selector routing, intent extraction (LLM-backed
`LlmIntentClassifier` with cosmo's `{reasoning, final_answer}` parse path
and context gather), fulfillment (22 action types, confirmation policy, audit
trail), LLM clients (public AICore, simulation), persistent context store
(Room + SQLCipher), state probe (`CosmoStateSource` cosmo-aligned), settings
DataStore, Nav3 UI with 25 debug panels, telemetry catalog with provenance,
**Presence surfaces (orb / tab / card stack / glass-panel primitive,
aerospace-HUD palette)**, **AppFunctions logic surface (`AmbientAppFunctions`
methods route through `CosmoController`, `CosmoStateManager`, `ContextStore`,
`FulfillmentOrchestrator`, `TaskMonitorNavigationBridge`)**, **voice output
through `VoiceOutput` using Sherpa-ONNX Supertonic-3 with Android
`TextToSpeech` fallback and streaming `AudioTrack` playback**.

**Partial (⚠️):** EdgeTPU Conformer backend (`isAvailable=false` until Pixel
returns), Inherent backend (contract-valid artifact available, quality and
device proof pending), AOC nanoapp transport (reflection-bridged; needs platform-signed callback or
bytecode-gen), Voice Match (JNI stub + config plumbing; no UI / no enrollment),
HotwordEvent routing (logged-only), LiteRT-LM (E2B app-private fallback
requires additional physical-device proof and production delivery),
remote inference (live config needed), glass + camera capture (services
bind; consumers not wired), richer fulfillment execution-error UI, **Presence state driver
now emits idle/specialty/action/gatekeeper states but still needs full
fulfillment-result and projected-XR consumers**, **AppFunctions platform binding (`@AppFunctionSchema` /
`@AppFunctionSerializable` annotations + `AppFunctionService` registration
not applied — methods aren't discoverable by the platform agent yet)**.

**Deferred (🟡):** exact VoiceLM + Vogo recovered stack, Gecko semantic search,
Pumpkin semantic grammar, Diarization,
Android Auto context grammars, offline action data, 10 of 13 recovered
native libraries.

**Planned (🔵):** CaMeL trust layer (provenance + capability tokens + PLLM/QLLM
split + interpreter at tool-call boundary), mt6989 BERT variant (AOT pipeline
drafted, model not produced yet), **structured accessibility-tree capture
(DBW-style `AccessibilityForestCaptor`)**, **server-side workstreams (DBW
data pipeline + opt-in upload + distillation training; browsing-agent
backend; production CDN for model delivery — system-design only, no
implementation)**.

The architecture is **substrate-complete for the parity demo**: every cosmo
capture and classification path that genesis intends to ship has working code.
The work ahead is filling partial wirings (EdgeTPU on Pixel, Inherent model
delivery, AOC callback activation), the CaMeL trust layer, and richer
execution-error UI for fulfillment.
