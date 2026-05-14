# Genesis System Architecture

This document is the system-level architecture of the genesis app (cosmo
clean-room reimplementation). It is the entry point — read this first, then
descend into the more specific docs as needed:

- **Marching orders & contract rules (C1–C9, PR0–PR6):** [`architecture-delta.md`](architecture-delta.md)
- **Active blockers:** [`blocker-register.md`](blocker-register.md)
- **Recovery maps (cosmo bytecode-level reference):** `~/cosmo-dev/repo/docs/{audio,text,image,llm,ui_surface,dagger_graph,settings_and_config,telemetry}_stack_map.md`
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
| Inherent backend | `gatekeeping/backend/InherentBackend.kt` | ⚠️ | `isAvailable` checks for `inherent.tflite` + `inherent.metadata.json` assets; `classify(RawPcm)` runs `InherentAudioGatekeepingModel.predict`. Real model produced by `~/inherent` project (in progress); placeholder bytes shipped today |
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
| `inherent.tflite` + `inherent.metadata.json` (placeholder bytes) | Genesis joint model | 🔵 | Real artifact pending from `~/inherent` |

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
| LiteRT-LM (on-device Gemma bundle) | `lib/llm/LiteRtLmLLM.kt` | ⚠️ | Requires provisioning the official Gemma 4 LiteRT-LM bundle (`scripts/provision-gemma4-litertlm.sh`); bundled-asset / app-private / runtime-download delivery |
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
| Debug panels | `ui/mainscreen/DebugPanels.kt` | ✅ | 24 Hilt-multibound panels for controller jobs, settings, permissions, LLM stats, action repo, fulfillment history, gatekeeper output, buffer sizes, model manifest, skill registry, observability schema |
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

## 13. Voice output (🟡 DEFERRED)

**Status: 🟡 deferred. Architecturally placed; no plans to build for current scope.**

Cosmo's recovered code includes a full on-device TTS stack. Genesis intentionally
does not build it for the parity demo scope (consolidated plan calls it
"VoiceLM/SoundStream → deferred; not required for parity demo"). This section
documents the placement so the slot is clear when (if) it becomes scope.

### 13.1 What cosmo had

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
slot were activated, the asset would need re-acquisition.

### 13.2 Architectural placement in genesis

If/when voice output enters scope:

| Slot | Genesis location |
|---|---|
| `TTS` interface | `lib/tts/TTS.kt` (new) |
| `VoiceLMTTS` impl | `lib/tts/VoiceLMTTS.kt` (new) — wraps Vogo + VoiceLM tflite ensemble |
| `LocalTTS` impl | `lib/tts/LocalTTS.kt` (new) — wraps `android.speech.tts.TextToSpeech` for the always-available path |
| `AudioRenderer` | `lib/tts/AudioRenderer.kt` (new) |
| `AudioResampler` | `lib/tts/AudioResampler.kt` (new) |
| `libvogo_jni.so` | `app/src/main/jniLibs/arm64-v8a/` — recovered binary exists at `~/Downloads/cosmo_recovered_native_libs/`; not bundled |
| VoiceLM assets | `app/src/main/assets/models/voicelm/` — recovered tflites exist at `~/Downloads/cosmo_recovered_large_assets/`; not bundled. `temporal_decoder` needs re-recovery. |

Nothing in the current code references `lib/tts/` — when this slot lands,
fulfillment action handlers that produce speakable output gain a single
`TTS` dependency; otherwise unchanged.

---

## 14. Appendix: Recovered-but-unwired surfaces

A catalog of things genesis could enable without writing new infrastructure.
None are blockers; all are inventory.

### 14.1 Native libraries recovered but not bundled in `jniLibs/`

Found at `~/Downloads/cosmo_recovered_native_libs/lib/arm64-v8a/`:

| Library | Subsystem | Why not bundled |
|---|---|---|
| `libvogo_jni.so` | VoiceLM/TTS | Voice output deferred (§13) |
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
| `voicelm/encoder_int4-graph-custom_op.tflite` (137M) | VoiceLM | Deferred (§13) |
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

## 15. Appendix: Document map

| If you need... | Read |
|---|---|
| The current system architecture (this doc) | `docs/architecture.md` |
| The C1–C9 contract rules + PR0–PR6 marching orders | `docs/architecture-delta.md` |
| Active blockers / not-yet-resolved issues | `docs/blocker-register.md` |
| AICore public-API surface limits | `docs/aicore-api-gap.md` |
| Audio training corpus plan | `docs/audio-corpus-plan.md` |
| Release signing policy | `docs/release-signing-policy.md` |
| Remote inference HTTP contract | `docs/remote-inference-contract.md` |
| Architecture-update enforcement hook | `docs/architecture-update-hook.md` |
| Recovery-level cosmo internals (smali / proto fields / asset bytes) | `~/cosmo-dev/repo/docs/*_stack_map.md`, `~/cosmo-dev/repo/docs/architecture.md` |
| Workspace-level decisions, deferred items, scope cuts | `~/cosmo-dev/WS4/docs/ws4-known-gaps.md`, `~/cosmo-dev/repo/docs/cosmo_apk_consolidated_plan.md` |
| Inherent joint-model runtime contract | `~/inherent/contracts/runtime_contract.md` |

---

## 16. Snapshot summary (2026-05-13)

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
DataStore, Nav3 UI with 24 debug panels, telemetry catalog with provenance.

**Partial (⚠️):** EdgeTPU Conformer backend (`isAvailable=false` until Pixel
returns), Inherent backend (placeholder asset until `~/inherent` ships),
AOC nanoapp transport (reflection-bridged; needs platform-signed callback or
bytecode-gen), Voice Match (JNI stub + config plumbing; no UI / no enrollment),
HotwordEvent routing (logged-only), LiteRT-LM (provisioning needed),
remote inference (live config needed), glass + camera capture (services
bind; consumers not wired), action handler split.

**Deferred (🟡):** Voice output stack (VoiceLM + Vogo + LocalTTS + AudioRenderer),
Gecko semantic search, Pumpkin semantic grammar, Diarization,
Android Auto context grammars, offline action data, 10 of 13 recovered
native libraries.

**Planned (🔵):** CaMeL trust layer (provenance + capability tokens + PLLM/QLLM
split + interpreter at tool-call boundary), mt6989 BERT variant (AOT pipeline
drafted, model not produced yet).

The architecture is **substrate-complete for the parity demo**: every cosmo
capture and classification path that genesis intends to ship has working code.
The work ahead is filling partial wirings (EdgeTPU on Pixel, Inherent model
delivery, AOC transport), the CaMeL trust layer, and the action-handler
split for fulfillment ergonomics.
