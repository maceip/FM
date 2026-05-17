# Genesis Architecture Delta

This file records Genesis-owned implementation deltas against the **upstream architecture doc** (`architecture.md`) and the stack maps in the reference repository described in `docs/reference-repository.md`. It is the merge-time source for project-local changes until those upstream docs are updated. Active blockers are tracked in impact order in `docs/blocker-register.md`; update that file first when blocker status changes.

## Current State

- Build and validation status: the 2026-05-13 architecture check passed `./gradlew :app:testDebugUnitTest :app:assembleDebug` after the C6 cosmo-state-probe interface and C7 LLM-backed intent extractor landed; `:app:detekt` still fails on the pre-existing `ui/theme/Color.kt` MatchingDeclarationName issue, unrelated to gatekeeping/SODA/state/intent. A connected SODA smoke could not be re-run because `adb devices` returned no attached devices.
- Phase 0 is structurally in place: baseline profile wiring, model manifest schema, modern Android SDK floor, architecture verification, and CI hooks exist. The manifest now supports verified bundled assets as well as remote HTTPS delivery.
- Phase 1 is implemented as a production contract and routing layer, but no live production LLM path is fully provisioned by default. Remote inference requires endpoint, certificate pin, and audience configuration; public AICore is used opportunistically through `GenerativeModel`; non-public AICore feature APIs may be explored only behind a kill switch; LiteRT-LM requires provisioning the official Gemma 4 LiteRT-LM bundle.
- Phase 2 is now substantially in place. Ambient, buds, device/media, and watch capture jobs exist; the recovered BERT/audio gatekeeper assets and SODA language packs are bundled in the debug APK and hash-verified. Gatekeeper subscription ownership, the unified text/audio `GatekeepingModel` surface, Magic Mic/IntendedSpeech directedness gating, typed directedness telemetry, settings-gated AOC-vs-mic routing with mic fallback, the C9 selector-routed backend dispatch (each backend's `classify` consumes its native `AudioInput` shape and returns null for the other, so the C1 mutex is enforced by backend type-filtering), per-source `SodaSession` instances (cosmo `SodaController.smali` parity), the cosmo-shaped `CosmoStateSource` probe interface (C6: 2-method probe matching cosmo, not the invented sealed-state ADT), and the LLM-backed `LlmIntentClassifier` (C7: cosmo `IntentExtractorImpl.smali` parse-path parity: `org.json.JSONObject` + `optString` + fence stripping) are implemented locally and build-green. PR6 speculative-config deletion has landed (all listed files removed). Remaining Phase 2 gaps are connected-device SODA proof and platform-capable AOC callback construction: Context Hub/nanoapp plumbing exists, but stock public-SDK builds cannot instantiate `ContextHubClientCallback`, so routing falls back to mic. Optional enrichments: move the intent prompt to an asset, gather richer context (recent transcripts + screen interactions) for the extractor.
- Phase 3 is functionally broad and now has strict action parsing, confirmation-policy coverage, executor-owned action dispatch, and an explicit dogfood downgrade policy: unavailable first-party surfaces use Android intents, web search, text drafts, or TaskMonitor fallback rather than fake integrations.
- Phase 4 is substantially improved. The main app uses Nav3, adaptive supporting panes, predictive-back opt-in, 25 Hilt debug panels, expanded telemetry definitions, and saveable JSON route-state coverage for tab stacks, TaskMonitor payloads, and adaptive support panes. Remaining work is device/UI recipe parity for result passing and broader adaptive layout behavior.
- Phase 5 hardening exists but is not release-complete. Permission rationale, encrypted settings/context storage, WorkManager batching, diagnostics, memory trimming, and release R8 preservation exist; Phase 6 has now added release signing separation, non-empty model gates, and telemetry provenance, while broader architecture gates remain open.
- Phase 6 release/dogfood execution has started. CI now treats the model manifest as non-empty, `release` signing is separated from the reachability debug keystore, and `scripts/verify-phase6-release-gates.sh` captures the first release-readiness checks.

## Phase 0

- Baseline profile production flow: `baselineprofile/build.gradle.kts`, `baselineprofile/src/main/kotlin/com/google/research/air/cosmo/baselineprofile/GenesisBaselineProfileGenerator.kt`, `app/build.gradle.kts`, and `scripts/generate-baseline-profile.sh` add the AndroidX producer/consumer path for startup profile generation.
- Large model assets: `app/src/main/assets/model-manifest.json`, `config/assets/model-manifest.schema.json`, and `scripts/download-models.sh` define the manifest-backed source of truth for bundled or release-owned HTTPS model artifacts, byte sizes, and SHA-256 verification.
- Platform floor and build SDK: `app/build.gradle.kts`, `baselineprofile/build.gradle.kts`, and `gradle/libs.versions.toml` set Genesis on a modern Android stack: `minSdk` API 33, `compileSdk` API 37, AGP 9.1, Kotlin 2.3, and the current Navigation 3 / Material adaptive supporting-pane libraries.
- Architecture gate: `docs/reference-repository.md`, `docs/architecture-update-hook.md`, `docs/architecture-delta.md`, `scripts/verify-architecture-delta.sh`, `scripts/verify-relocation-safety.sh`, and `.github/workflows/ci.yml` make architecture delta updates and standalone-root safety explicit CI checks.

## Phase 1

- Remote inference contract: `docs/remote-inference-contract.md` defines the owned issuer and inference HTTP contract for live remote inference.
- Remote inference client: `app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RemoteInferenceClient.kt` owns live inference transport, request signing, credential acquisition, and PrivateToken authorization headers.
- Secure credential storage: `app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RemoteCredentialStore.kt` stores acquired anonymous credentials encrypted with an Android Keystore key.
- Request signing: `app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/RequestSigner.kt` signs issuer and inference requests with an Android Keystore-backed ECDSA key.
- Phase 1 LLM gate: `scripts/verify-llm-phase1.sh` blocks reflection regressions, build-time remote credentials, stale remote-contract names, and missing route/credential tests.
- AICore public API gap: `docs/aicore-api-gap.md` records the lower-level `AiFeature`/`AiCoreClient` blocker if the checked-in public AAR still does not expose a stable feature-selection API.
- Remote inference config: `RemoteInferenceConfig.fromBuildConfig()` reads the build-time `cosmo.remoteInference.*` Gradle properties. `OkHttpAnonymousCredentialIssuerClient`, `OkHttpRemoteInferenceClient`, and `RemoteHttpClientFactory` resolve the live config on each call.
- AICore experiment cleanup: the speculative non-public AICore toggle was deleted in PR6. The production wrapper uses the public `GenerativeModel` API only.
- Local LLM model delivery: `LiteRtLmLLM` derives bundled and app-private storage paths from `cosmo.localLlm.litertLmModelId` instead of hardcoding a specific Gemma variant. It checks app-private files deployed by `scripts/deploy-gemma4-litertlm.sh`, then stages a bundled APK asset if present. Runtime download and asset-pack delivery remain deferred until release variants exist.

## Phase 2

- BERT gatekeeper runtime: `app/src/main/cpp/bert_gatekeeper_jni.cc`, `app/src/main/cpp/litert_minimal_c_api.h`, `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/models/BertEncoderImpl.kt`, and `gatekeeping/GatekeepingBert.kt` port the binary-compatible BERT JNI surface from the reference repo. The consolidated `BertClassifierGatekeepingModel` owns backend selection, thresholds, output mapping, and native handle cleanup behind the shared `GatekeepingModel.predict(String)` overload.
- Model loading: `GatekeepingModels.kt` loads BERT and audio model bytes directly via `context.assets.open("models/<filename>")`; runtime manifest indirection (`GatekeepingModelLoader`/`ManifestGatekeepingModelLoader`) is deleted (PR6 row 5). Missing or invalid assets surface explicit unavailable states rather than silent skips. The build-time `scripts/download-models.sh --verify-only` remains the CI gate against drift.
- BERT output mapping: `GatekeepingBert.kt` maps the recovered 19-float vector into production skill labels for memory, list, search/define, photos, calendar, docs, person/event context, research, insight, browsing, call, and timer actions. The 12 audio-skill parity set required by G6 is covered.
- Source-specific audio capture: `AudioCaptors.kt`, `SodaController.kt`, `EphemeralBuffer.kt`, and `CosmoController.kt` provide distinct ambient, buds, device/media, and watch capture jobs, source-owned SODA controllers, qualified text/byte buffers including watch audio bytes, and settings-driven start/stop lifecycle for `isBudsAudioEnabled`, `isMediaAudioEnabled`, `isWatchAudioEnabled`, and raw-audio gatekeeping. `SODAAmbientAudioCaptor` now reads `isUseNanoappAudioEnabled` and tries `RecordServiceManager.startAocRecording(listener)` before falling back to mic. `NanoAppClient` now mirrors the cosmo AOC flow through Context Hub discovery, nanoapp lookup, live/off messages, payload forwarding, and silence injection; the remaining gap is constructing the required platform `ContextHubClientCallback` subclass from a build that has access to that API.
- Recovered SODA blend: `JniSodaCapability.kt`, `SodaSession.kt`, `SodaController.kt`, `SodaCapability.kt` contract/result/pack types, `app/src/main/cpp/soda/soda_jni.cc`, and the `com.google.android.libraries.assistant.soda` compatibility classes preserve the Genesis `SodaTranscriptionController` boundary while routing PCM through a recovered-compatible `libsoda_dev_jni.so` lifecycle. `SodaController` now creates per-source `SodaSession` instances instead of a shared SODA session; `SodaConfigBuilder.kt` configures `AMBIENT_ONESHOT`, `attachHotwordEvent`, and `IntendedSpeechConfig` `OPEN_MIC` with `attachOpenMicResult` so FinalRecognitionResult intended-query scores can drive directedness. `app/src/main/assets/soda` bundles `lp_cpu.zip`, `lp_lga_tpu.zip`, and `lp_zuma_tpu.zip`; `scripts/verify-soda-assets.sh` verifies recovered checksums and sizes. Connected-device SODA smoke remains required for release confidence.
- Raw-audio gatekeeper path: `AudioGatekeepingModel.kt`, `AudioGatekeeperLiteRtImpl.kt`, and `audio_gatekeeper_jni.cc` hold the frontend/backend LiteRT path. The unified `Gatekeeper` only enters this path when `AudioGatekeepingBackendSelector` selects `ConformerEdgeTpu`; that backend is deliberately unavailable today pending Pixel/Darwinn validation, so CPU recovery uses Magic Mic directedness plus BERT-on-transcript instead.
- Bundled gatekeeper artifacts: `app/src/main/assets/models` now contains `bert_vocab.txt`, `bert_v0_cpu.tflite`, `bert_v0_p24.tflite`, `bert_v0_p25.tflite`, `audio_frontend.tflite`, and `audio_gatekeeper_edgetpu.tflite`, plus `SHA256SUMS`. `scripts/download-models.sh --verify-only` verifies the manifest hashes and sizes for these bundled entries.

## Phase 3

- Schema-backed skills: `app/src/main/kotlin/com/google/research/air/cosmo/skills/Skills.kt` now builds the production skill catalog from `app/src/main/assets/filtered_skill_schema.textproto`, including all 22 emitted action types, required arguments, trigger regexes, confirmation requirements, and permission gates.
- Asset-backed prompt rendering: `app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/Fulfillment.kt` renders fulfillment prompts from the checked-in prompt and schema assets before adding bounded ephemeral buffer context, settings, and user query details.
- Fulfillment action contract: `FulfillmentAction` parsing and dispatch now cover every schema action type plus removed/unsupported action records, with explicit execution telemetry and downgrade paths for unavailable external surfaces such as AgentQ and direct Calendar provider writes.
- Actions debug state: `ActionsRepositoryContract` remains the public surface for current, user-facing, executed, dismissed, Cosmo-removed, and history/debug action state.

## Phase 4

- Main UI navigation: `app/src/main/kotlin/com/google/research/air/cosmo/ui/mainscreen/MainActivity.kt` uses Android Navigation 3 `NavDisplay` for Actions, Capture, Camera, Settings, Debug, action detail, and task-monitor destinations, with permission rationale UI for runtime capture permissions. Regular tall phones use bottom navigation with preserved per-tab stacks; foldables/tablets use a navigation rail and Material adaptive `SupportingPaneSceneStrategy` so Debug/observability and secondary action surfaces can live beside the selected main surface. Back handling pops the active Nav3 route stack, then prior tab history, then exits the activity, and the manifest opts into Android's `OnBackInvoked` predictive-back callback path. The Debug destination renders a Hilt multibound set of 24 injected Compose panels from `app/src/main/kotlin/com/google/research/air/cosmo/ui/mainscreen/DebugPanels.kt`.
- Route-state coverage: `MainNavigationStateTest.kt` verifies TaskMonitor route round-trips, corrupted route-token rejection, selected-tab stack restore, adaptive support-pane insertion, and JSON persistence of `SavedNavigationBundle`.
- Live debug observability: `DebugPanels.kt` exposes controller job state, settings, runtime permission state, LLM stats, action repository state, fulfillment history, live gatekeeper output and skill scores, all object/byte ephemeral buffer sizes, model manifest state, skill registry counts, and observability schema counts.
- Task monitor surface: `app/src/main/kotlin/com/google/research/air/cosmo/fulfillment/actions/mariner/TaskMonitorActivity.kt` renders browsing-agent prompt/response bubbles alongside fulfillment history.
- Glass surface: `app/src/main/kotlin/com/google/research/air/cosmo/glasses/CosmoGlass.kt` exposes Glass permission state, connection/bind state, telemetry, and a user-visible buffer-clear action through `CosmoGlassViewModel`.
- Telemetry schema: `app/src/main/kotlin/com/google/research/air/cosmo/logging/Logging.kt` defines the Genesis observability catalog with 78 log tags, 309 Flogger event definitions (each mapped to a `cosmo_*` Firebase Analytics event under `GenesisObservabilitySchema.firebaseEvents`), explicit Firebase definitions for typed `TelemetryEvent` emits (`telemetryFirebaseEvents`, fail-closed if undocumented), event provenance metadata with owner/privacy/source evidence, and typed telemetry events for fulfillment, actions, gatekeeper runs, gatekeeper directedness decisions, TaskMonitor, and Glass.

## Phase 5

- Runtime permission hardening: `app/src/main/kotlin/com/google/research/air/cosmo/lib/permission/PermissionRationaleHelper.kt` centralizes dangerous permission descriptors, records request history in DataStore, differentiates rationale and permanently denied states, and routes unrecoverable denials to Android app settings.
- Encrypted local storage: `app/src/main/kotlin/com/google/research/air/cosmo/security/SecureStorage.kt` adds Android Keystore AES-GCM helpers; `setting/Settings.kt` encrypts the settings proto DataStore; `context/ContextStore.kt` opens Room through SQLCipher with a Keystore-wrapped database passphrase in no-backup storage.
- Batch and battery constraints: `app/src/main/kotlin/com/google/research/air/cosmo/batch/BatteryAwareBatchWorker.kt` schedules unique periodic fulfillment work with unmetered network, battery-not-low, and idle constraints, and rechecks battery capacity before running.
- Debug diagnostics and memory pressure: `lib/performance/DebugDiagnostics.kt` installs StrictMode and a main-thread ANR watchdog in debug builds; `lib/performance/MemoryPressureTrimmer.kt` clears ephemeral buffers under Android memory pressure callbacks.
- Manifest and release hardening: `AndroidManifest.xml` removes `SYSTEM_ALERT_WINDOW`, makes the double-tap activity non-exported, and keeps cleartext disabled; `app/proguard-rules.pro` preserves Hilt, WorkManager, Room, protobuf, Firebase, AICore, and SQLCipher runtime surfaces for R8 full mode.
- Perf tooling: `scripts/run-perfetto-battery.sh` collects Perfetto traces, Battery Historian-compatible bugreports, and package batterystats for the Phase 5 drain gate.

## Phase 6

- Release signing policy (no secrets in repo): `docs/release-signing-policy.md` and `config/signing/README.md` describe env-first production signing (`COSMO_RELEASE_*` / optional `cosmo.release.*` Gradle properties), checked-in `genesis-debug.keystore` for debug-only use, and CI versus upload workflows.
- Gradle separation: `app/build.gradle.kts` applies `releaseProduction` signing only when credentials resolve; `assembleRelease` / `bundleRelease` fail fast without them and depend on `verifyCosmoReleaseModelManifest` (`scripts/download-models.sh --verify-only`). The `releaseInternal` build type mirrors release minification but stays debug-signed for CI and local R8 smoke without Play keys.
- Strict model gate: `.github/workflows/ci.yml` runs `scripts/download-models.sh --verify-only` without `--allow-empty`, so the manifest must list models and required bundled artifacts must exist with matching hashes/sizes in CI.
- Release gate script: `scripts/verify-phase6-release-gates.sh` runs strict model verification, architecture verification, relocation safety, dogfood contract checks, CI manifest/static-analysis checks, signing/manifest Gradle checks, and a regression check that LiteRT-LM delivery does not hardcode an E4B-only runtime path.
- Dogfood contract gate: `scripts/verify-dogfood-contract-gates.sh` guards the static launch contracts that previously drifted in docs or code: SODA `AMBIENT_ONESHOT` + IntendedSpeech open-mic config, selector-routed gatekeeper dispatch, AOC public-SDK fail-closed behavior, fulfillment executor multibindings, and README progress-table language.
- Model variant readiness: `LiteRtLmLLM` defaults to the proven app-private Gemma 4 E2B dogfood bundle and supports E4B or future LiteRT-LM bundles through `cosmo.localLlm.litertLmModelId`-derived storage paths, so validated local files are not silently deployed as a different variant.

## Final Review Findings And Unlock Plan

This section captures the final cross-phase review and the recommended order of operations. It is intentionally action-oriented: each finding names the blocker or code smell, the unlock path, and the workstream that should own it.

### Findings And Unlocks

- Phase 2 remains blocked primarily by attached-device runtime proof and the unfinished architecture-delta tail. Ambient, buds, device/media, and watch now have distinct captors/controllers, qualified buffers, settings-driven source job state, and tests. The recovered BERT/audio TFLite artifacts and SODA language packs are bundled and hash-verified; the BERT and raw-audio model paths are consolidated, and local gatekeeper validation is green. `GatekeepingRuntimeSmokeTest` and SODA fixture smoke still need attached-device evidence.
- Model artifacts are no longer absent from `app/src/main/assets/model-manifest.json`. The recovered gatekeeper artifacts are copied into `app/src/main/assets/models` under canonical filenames, recorded with SHA-256 and size metadata, and verified by `scripts/download-models.sh --verify-only`. `GatekeepingRuntimeSmokeTest` now verifies CPU BERT produces a finite 19-float vector when androidTest runs on an attached device or emulator.
- Raw-audio gatekeeping is no longer placeholder-bound. `rawAudioGatekeeperEnabled`, `AUDIO_FRONTEND`, and `AUDIO_GATEKEEPER` exist, ambient raw bytes are buffered, and audio frontend/gatekeeper assets are present. The EdgeTPU Conformer backend is intentionally unavailable today, so the default recoverable path is SODA IntendedSpeech/Magic Mic directedness followed by BERT-on-transcript.
- Phase 1 LLM routing is structurally solid but still needs target-device proof for each chosen lane. Remote inference defaults to blank build config, AICore feature selection is blocked by the public AAR surface, and the launch LiteRT-LM fallback is the app-private E2B bundle provisioned by `scripts/provision-local-litertlm.sh`. Any future non-public AICore experiment must be isolated behind a branch or build flag and must not replace the public production path until ABI risk is accepted. The available `google/gemma-4-E4B-it-assistant` safetensors source is an MTP drafter and cannot be converted into a standalone model with the current public exporter; use `scripts/provision-gemma4-litertlm.sh` only when intentionally testing the larger official `litert-community/gemma-4-E4B-it-litert-lm` E4B override.
- Fulfillment actions cover the schema surface with strict parser validation before repository insertion, confirmation-policy coverage for every action subtype, and subtype-grouped executors. Dogfood intentionally uses generic intents, web search, text drafts, or TaskMonitor fallback where no first-party API is available; richer execution/error UI state remains polish.
- Nav3 is materially improved and now has saveable route-state coverage. `MainActivity.kt` has per-tab stacks, predictive-back opt-in, secondary action routes, TaskMonitor route tokenization, and JSON-restored tab history; `MainNavigationStateTest` covers route round trips and adaptive support panes. Remaining recipe work is result passing and device/UI validation across compact, unfolded, and tablet widths.
- Phase 5 hardening is present but not yet release-complete for store-ready builds: telemetry provenance is now schema-backed and gated, while broader Phase 2-5 architecture gates remain open; Phase 6 documents production signing inputs and strict CI model verification (`scripts/verify-phase6-release-gates.sh`).
- The AGP/Kotlin toolchain uses AGP built-in Kotlin: `gradle.properties` sets `android.builtInKotlin=true`, the legacy `android.newDsl=false` escape hatch is removed, and modules no longer apply `org.jetbrains.kotlin.android` (Compose and Serialization compiler plugins remain explicit). KSP, Hilt, and Compose compiler flows are exercised by `:app:assembleDebug`, `:app:assembleRelease`, and `:app:testDebugUnitTest` in CI/local smoke.

### Code Smells To Fix

- Split large multipurpose files. `Fulfillment.kt`, `MainActivity.kt`, and the debug panel modules should be divided into parser/actions/orchestrator, nav/screens, and debug panel ownership boundaries. Gatekeeping was deliberately consolidated per C1-C3; do not re-split it into runner/factory layers.
- Finish source-specific audio validation and AOC callback activation. Buds, device, watch, call, and ambient audio now have source-specific controller/buffer wiring, and ambient capture has the settings-gated AOC-vs-mic branch. The AOC protocol plumbing is present, but the public-SDK build cannot create the required `ContextHubClientCallback` subclass, so `startAocRecording` returns false on stock installs and ambient capture falls back to mic.
- ~~Fix `CosmoController` lifecycle semantics.~~ Resolved 2026-05-17: `pauseMicAudioCapture()` now stops ambient, buds, media, and watch audio jobs; `resumeMicAudioCapture()` reconciles the same source-specific jobs against settings/state; `CosmoControllerTest` covers pause, resume, and resume-before-init behavior.
- ~~Finish backend-selector wiring.~~ Resolved 2026-05-12: `Gatekeeper.onAmbientAudio` and `onAmbientAudioBytes` route through `backendSelector.select().classify(input)`; `CpuRecoveryBackend`, `ConformerEdgeTpuBackend`, and `InherentBackend` all perform their owned classification shape. Inherent quality and attached-device proof remain open, but the backend is no longer a stub.
- Replace regex parsing of `filtered_skill_schema.textproto` with a structured parser or generated schema contract. The current `SkillAssetCatalog` regex bridge is useful but fragile.
- Harden debug and telemetry provenance. `Logging.kt` now records owner, privacy class, source path, and emission evidence for every direct Firebase telemetry event, and tests enforce that Firebase Analytics is only used behind the logging boundary. The remaining debug hardening work is to tie broader debug panels to phase-specific architecture gates.
- ~~Split remaining product-surface handlers.~~ Resolved 2026-05-17: `FulfillmentActionHandlerImpl` delegates through a Hilt multibound `FulfillmentActionExecutor` registry for intent/calendar/draft actions, memory writes, TaskMonitor/AgentQ surfaces, and passive removed/unsupported actions. Richer user-facing execution error state remains future polish.
- Remove remaining release-time debug assumptions. Release signing, model availability, and telemetry gates exist; remote inference configuration still needs environment-specific live validation instead of relying on reachability defaults.

### Recommended Order Of Operations

1. Attach a target device/emulator and run the new runtime proofs: CPU BERT androidTest, raw-audio LiteRT frontend/backend execution, LLM smoke, and battery/perf capture.
2. Make one LLM path production-live: provision remote inference with certificate pinning and runtime credentials, validate public AICore/ML Kit on target hardware, or provision/package an official LiteRT-LM bundle through the supported model-delivery path.
3. Split fulfillment into subtype-owned executors with richer execution/error UI state; validation and confirmation coverage are already in place.
4. Complete remaining Nav3 recipe parity: result passing, adaptive scene metadata, and predictive-back behavior checks on compact, unfolded, and tablet widths.
5. Run dogfood release-candidate gates after toolchain bumps (lint uses default K2 UAST; re-open in `docs/blocker-register.md` if regressions appear).

### Workstreams

- Workstream A: Phase 2 capture and gatekeeper. Own attached-device buds/device/watch SODA validation, raw-audio LiteRT runtime proof, CPU/NPU BERT smoke execution, source job lifecycle, and battery/perf evidence.
- Workstream B: Phase 1 LLM and model delivery. Own remote inference provisioning, public AICore/ML Kit target-device validation, official LiteRT-LM packaging/delivery, and local/remote LLM smoke tests.
- Workstream C: Product surface hardening. Own fulfillment executor split, richer action/detail error state, remaining Nav3 result/adaptive recipe parity, AGP/Kotlin cleanup, and dogfood/release gate hardening.

## Architectural Contract Addendum

This section is normative. It records control-flow invariants and shape constraints that the upstream `architecture.md` describes structurally but does not encode operationally. Genesis must satisfy these contracts before it can claim parity with cosmo. Each rule cites the recovered cosmo smali at `~/Downloads/cosmo_baksmali/` (paths abbreviated `...` for the `classes38/com/google/research/air/cosmo/` prefix) so future drift is detectable by re-reading the source of truth.

### C1. Gatekeeper Dispatch Invariant (Mutually-Exclusive Modalities)

**Rule.** Cosmo's `Gatekeeper` runs the audio classifier and the text/regex+BERT classifier as **mutually-exclusive** branches per ambient transcript, not as parallel jobs. When `isRawAudioGatekeeperEnabled() && isRealtimeProcessingEnabled().value` evaluates true, only `processRawAudioInput(td, ...)` runs and the method returns; the text path is the OFF-state fallback. The text path is never invoked alongside the audio path on the same transcript.

**Cosmo source.** `...gatekeeping/Gatekeeper$2$1.smali` lines 135–277 (the inner `Gatekeeper$2$1.emit(Object)` for the ambient `EphemeralBufferReader.subscribe` on the audio-transcript stream): conditional on the two settings flows, the smali calls `processRawAudioInput` and unconditionally `return-void`s; the `else` branch invokes `processInput(td.getTranscription(), ContextSource.AMBIENT_AUDIO, ...)`.

**Why it matters.** This is the privacy/latency tripwire: on devices where the audio gatekeeper is enabled, raw transcripts must never be classified by the text path because doing so widens the data exposed to BERT and double-counts the gating decision. The audio gatekeeper *is* the gate; falling through to text breaks the user privacy contract independently of any output filtering.

**Genesis status (2026-05-12).** Locally implemented and covered by unit tests; now enforced through C9 backend routing. `GatekeepingPipeline.kt` owns ambient text and byte subscriptions, builds an `AudioInput.TranscribedSegment` / `AudioInput.RawPcm` for each ambient event, and routes via `backendSelector.select().classify(input)`. The C1 mutual-exclusivity invariant is enforced by backend type-filtering: `ConformerEdgeTpuBackend.classify` returns null for `TranscribedSegment` (the audio bytes path owns it under EdgeTPU); `CpuRecoveryBackend.classify` returns null for `RawPcm` (the text path owns it under CPU recovery). `GatekeeperDispatchInvariantTest` covers raw-audio-on, raw-audio-off, realtime-off, audio-bytes, directedness-window, and Conformer-active cases. Connected-device SODA proof is still required before declaring runtime parity.

**Fix shape.** Collapse subscription + dispatch into a single `Gatekeeper` class owning both input streams (mirror cosmo's 15-arg constructor with 6 `EphemeralBufferReader` instances and both `GatekeepingModel` slots). The dispatch site reads settings once per ambient transcript, runs exactly one branch, and emits to the single shared `MutableSharedFlow` it owns. Parallel `Job`s for audio vs text gatekeeping are forbidden.

### C2. Single-Class Gatekeeper Ownership

**Rule.** The `Gatekeeper` class owns its inputs and its output sink. External code (controllers, services) does not pump data into it. External code constructs the `Gatekeeper` once at app start, hands it a flow of buffer readers, and observes its `output: SharedFlow<GatekeeperOutput>`.

**Cosmo source.** `...gatekeeping/Gatekeeper.smali` (2876 lines). The 15-arg constructor takes 6 `EphemeralBufferReader` instances + the BERT `GatekeepingModel` + the lazy audio `GatekeepingModel` + `Logger` + `SkillsRepository` + `SettingsRepository` + `Clock` + `AudioAccumulator` + `RealtimeProcessing` + `CoroutineScope`. Inner classes `Gatekeeper$2..$7` implement `FlowCollector.emit` for each of the six per-source subscriptions. The output sink is a `MutableSharedFlow` member.

**Genesis status (2026-05-11).** Mostly implemented. `CosmoController` starts the unified `Gatekeeper`; subscription and dispatch live in `GatekeepingPipeline.kt`; `RawAudioGatekeeper` has been removed. The output surface is currently `GatekeeperOutputSource.results: SharedFlow<GatekeepingResult>` rather than the exact cosmo-shaped `GatekeeperOutput` object described below.

**Fix shape.** Move all `EphemeralBufferReader.subscribe` calls into the `Gatekeeper` class. `CosmoController` constructs the `Gatekeeper`, calls `start(scope: CoroutineScope)` once, and observes `gatekeeper.output`. No external pumping.

### C3. Unified `GatekeepingModel` Interface

**Rule.** Audio and text classifiers implement a single `GatekeepingModel` interface with method overloads for the two input shapes. There is no separate `RawAudioGatekeeperRunner` type hierarchy.

**Cosmo source.** `...gatekeeping/GatekeepingModel.smali` (interface) is implemented by `BertClassifierGatekeepingModel.smali` for the text path and `AudioGatekeepingModel.smali` for the audio path. Both expose `predict(...)` overloads on the same interface; thresholds, output mapping, and JNI handle ownership are private state on each impl class.

**Genesis status (2026-05-12).** Implemented. The public interface is a single `GatekeepingModel : AutoCloseable` with `predict(String)` and `predict(ByteArray)` overloads. `BertClassifierGatekeepingModel` and `AudioGatekeepingModel` own threshold/output mapping and native calls. The runtime `GatekeepingModelLoader` manifest indirection and `SocResolver` are deleted (PR6).

**Fix shape.** One `GatekeepingModel` interface with `predict(text: String, ...)` and `predict(pcm: ByteArray, ...)` overloads. Two impls: `BertClassifierGatekeepingModel` and `AudioGatekeepingModel`. Threshold tables, output mappers, and JNI bridges are private members of the impl class — not separate top-level types.

### C4. Consolidation Contract (Cohesion Over Speculative Configurability)

**Rule.** When cosmo encodes a behavior in one class, genesis encodes it in one class. Splitting a cohesive cosmo class into multiple types — to "improve testability", "support future delivery modes", "abstract the JNI", or any similar speculation — is a contract violation. Configurability layers (delivery enums, runtime config providers, kill-switch toggles, factory + interface + impl + result-sealed-type chains) are forbidden unless an active workstream consumes them.

**Cosmo precedent.** Cosmo's recovered classes are uniformly cohesive: `SodaController` is one abstract class with per-source subclasses and SODA framing/JNI inside; `BertClassifierGatekeepingModel` owns its JNI handle, thresholds, and output mapping directly; model loading is `FileUtil.loadMappedFile(Context, String)` from a fixed asset path with no manifest indirection at runtime.

**Specific genesis violations to dissolve.** Each row lists the cohesive cosmo unit (LHS) and the genesis fragmentation count (RHS). Target: collapse RHS to LHS shape.

| Cohesive unit (cosmo) | Genesis fragmentation | Target |
|---|---|---|
| `Gatekeeper` (1 class, owns dispatch) | Resolved: `GatekeepingPipeline.kt` owns subscriptions and dispatch; C9 selector-routed classification is now the only path; direct `model`/`audioModel` constructor refs deleted (2026-05-12) | Finish exact output-shape parity (`GatekeeperOutput` vs `GatekeepingResult`) |
| `BertClassifierGatekeepingModel` (1 class) | Resolved: BERT runner/bridge/result/mapper/threshold types folded into `GatekeepingBert.kt`; runtime manifest loader and `SocResolver` deleted in PR6 | — |
| `AudioGatekeepingModel` + `ConformerEncoderImpl` (2 classes) | Resolved: audio runner/factory/result/frontend constants folded into `AudioGatekeepingModel.kt`; `ConformerEdgeTpuBackend` wraps it through C9; keep EdgeTPU `isAvailable=false` until Pixel/Darwinn proof | — |
| `SodaController` + per-source subclasses (~3 classes) | Resolved (2026-05-12): per-source `SodaSession` instances mirror cosmo `SodaController.smali` lines 619–1375; `JniSodaCapability` reduced to native-runtime probe + language-pack preparer; `GatekeeperDebugReceiver` uses its own dedicated `SodaSession` | optional follow-up: fold `SodaPrepareResult`/`SodaStartResult`/`SodaTranscriptCallback` if call sites stay readable |
| Direct `BuildConfig` reads for inference config | Resolved (PR6 row 7): `RemoteInferenceConfigProvider`/`LayeredRemoteInferenceConfigProvider`/`RemoteInferenceConfigStore`/`AndroidKeyStoreRemoteInferenceConfigStore`/`RemoteInferenceConfigSource`/encrypted DataStore deleted | — |
| `LiteRtLmLLM` (1 class) | Resolved (PR6 row 6): `LocalLlmModelDelivery`/`DefaultLocalLlmModelDelivery`/`LocalLlmDeliveryMode`/`LocalLlmModelSource`/`LocalLlmModelPaths`/`ResolvedLocalLlmModel` deleted; bundle path inlined | — |
| `AICoreClientWrapper` (1 class) | Resolved (PR6 row 8): `AICoreExperimentToggle`/`BuildConfigAICoreExperimentToggle`/`DataStoreBackedAICoreExperimentToggle`/`AICoreExperimentMode` deleted; public `GenerativeModel` API is the only path | — |
| Inline `Build.SOC_MANUFACTURER` reads | Resolved (PR6): `SocResolver`/`DefaultSocResolver`/`SocClass`/Hilt binding deleted; `Build.SOC_MANUFACTURER` read inline at the BERT backend selection site | — |

**Why it matters.** Each layer of speculative indirection makes invariants like C1–C3 harder to encode atomically. The dispatch bug in C1 was *enabled* by the splitting in C2/C3: with `Gatekeeper` and `RawAudioGatekeeper` as separate top-level classes coordinated externally, no single file expresses the mutual-exclusivity contract, and no reviewer can spot the violation by reading one file. Cohesion is not just an aesthetic preference — it is the mechanism by which control-flow invariants survive refactors.

**Engineering rule.** Before adding an interface, factory, sealed result type, or configuration enum: cite the cosmo class that does the same job in a single type. If cosmo's version is one class, genesis's is one class. Diverging requires an active, named workstream consumer of the new layer; "future flexibility" is not a consumer.

### C5. Settings-Gated Source Dispatch (AOC vs Mic)

**Rule.** The ambient capture source toggles between AlwaysOn-Compute (nanoapp) audio and direct mic capture based on `isUseNanoappAudioEnabled` settings flow. The dispatch is a runtime decision per capture-start, not a build-time wiring.

**Cosmo source.** `...audiocapture/SODAAmbientAudioCaptor.smali` `startCapture` reads the AOC flag and conditionally invokes `RecordServiceManager.startAocRecording` vs `startMicRecording`.

**Genesis status (2026-05-17).** Routing implemented, platform callback incomplete. `SODAAmbientAudioCaptor.startCapture` reads `isUseNanoappAudioEnabled`, tries `RecordServiceManager.startAocRecording(listener)`, and falls back to mic when AOC is unavailable. `NanoAppClient` owns Context Hub discovery, nanoapp lookup, `LIVE_STREAM`/`STREAMING_OFF` messages, raw payload forwarding, and silence injection. The missing piece is a real `ContextHubClientCallback` subclass: Java reflection can invoke the hidden APIs once a callback instance exists, but cannot synthesize that abstract-class subclass. Stock public-SDK builds therefore return false and use the mic fallback.

**Fix shape.** `SODAAmbientAudioCaptor.startCapture` reads `settingsRepository.settings.first().isUseNanoappAudioEnabled` and dispatches accordingly.

### C6. Cosmo State Probe (`CosmoStateSource`)

**Rule.** Cosmo exposes the controller's state through a tiny probe interface: `CosmoStateSource` with two boolean methods, `isCosmoEnabled()` and `isAnyMicAudioCaptureOn()`. `CosmoController` implements it directly. There is no sealed state ADT, no battery-hysteresis machinery, no atomic state-flow distribution to per-source captors in cosmo. The original C6 spec invented those — corrected here to match cosmo reality.

**Cosmo source.** `...controller/CosmoStateSource.smali` (interface, 2 abstract methods). `...controller/CosmoController.smali` declares `.implements Lcom/google/research/air/cosmo/controller/CosmoStateSource;`.

**Genesis status (2026-05-13).** Cosmo-aligned. `controller/CosmoStateSource.kt` defines the interface. `CosmoController` implements it: `isCosmoEnabled()` delegates to `settingStore.isGlobalCosmoEnabled()`; `isAnyMicAudioCaptureOn()` ORs the four per-source `is*AudioCaptureOn()` job-active checks. Hilt binds `CosmoStateSource` to `CosmoController` via `CosmoControllerModule`.

**Note on `CosmoStateManager` (genesis-only).** Genesis's `setting/Settings.kt` contains a richer `CosmoStateManager` with enum `CosmoState` (ENABLED/DISABLED/SNOOZED/OUTSIDE_ACTIVE_HOURS/LOW_BATTERY), alarm scheduling, and low-battery snooze. That is a genesis-only addition, not cosmo parity work. Keep it if its consumers (settings UI, debug panels, snooze flow) remain useful; do not promote it to "the C6 contract."

### C7. IntentExtractor as Second Gatekeeper-Output Consumer

**Rule.** `Gatekeeper.output` has two consumers: the action pipeline (skill detection → fulfillment) and the `IntentExtractor` (intent classification → context store). They observe the same flow concurrently.

**Cosmo source.** `...intent/IntentExtractorImpl.smali` subscribes to the same `Gatekeeper.output` flow that the action pipeline reads.

**Genesis status (2026-05-13).** Cosmo-shaped LLM-backed extractor landed. `IntentExtractorImpl` collects `GatekeeperOutputSource.results` alongside the action pipeline. The production binding is `LlmIntentClassifier` (injects `LLM`, builds a JSON-only prompt with the transcript + ranked candidate skills, calls `llm.generateText`, strips ` ```json` markdown fences per cosmo's `IntentExtractorImpl.smali`, parses with `org.json.JSONObject`). `IntentRecord` now carries `intent`, `slots: Map<String, String>`, `confidence`, `input`, and `modelId`. Failure modes (LLM throws, JSON malformed, intent="unknown", empty input + no detected skills) all return null without emitting; `IntentExtractorImpl` catches any uncaught exceptions at the collect site so the second-subscriber contract is preserved. `LlmIntentClassifierTest` covers fenced/bare JSON parse, malformed JSON, LLM throw, unknown intent, no-skill skip, and confidence clamping.

**Optional follow-ups (not parity blockers).** Move the prompt into an asset (`assets/intent_extraction_prompt.txt`) and render with a small Kotlin template engine if/when the prompt grows past inline-readable size; gather richer context (recent transcripts, screen interactions) per cosmo's `gatherContext` once the relevant ephemeral-buffer readers are wired into the extractor.

### C8. CPU/EdgeTPU Functional Parity Contract

**Rule.** The EdgeTPU Conformer (`AudioGatekeepingModel` + `audio_gatekeeper_edgetpu.tflite`) provided two functions: (i) an `isInteresting` directedness gate, (ii) twelve per-skill audio heads. Genesis must provide functionally equivalent behavior on a CPU-only device using only assets we already have. The EdgeTPU path is **deferred, not abandoned** — it remains the preferred path on Tensor/Coral-attached devices once hardware-class identification (hackathon notes + on-device validation) is restored. Both paths must satisfy the same C1 dispatch invariant: exactly one classification path runs per ambient transcript.

**Recoverable mapping (CPU path).**

| Lost EdgeTPU function | Recovered CPU mechanism | Asset / source |
|---|---|---|
| `isInteresting` directedness gate | SODA IntendedSpeech / Magic Mic score check (`FinalRecognitionResult.intendedQueryScore >= intendedQueryThreshold`) | `magic_mic/MARBLE_V2_*.int8.tflite` (lp_cpu pack) and `magic_mic/SODA_V2_*.int8.tflite` (TPU packs); CPU-quantized; runs inside `libsoda_dev_jni.so` |
| 12 per-skill audio heads | Two-stage text-side classification: SODA transcript → BERT classifier | `bert_v0_cpu.tflite` plus the consolidated `BertClassifierGatekeepingModel`; Pumpkin remains bundled inside SODA packs but cosmo does not consume its output |

**Functional gaps the CPU path does not replicate** (acknowledge, do not paper over):
- **Single-pass joint audio→intent inference.** EdgeTPU did directedness + per-skill in one forward pass; CPU path is sequential (IntendedSpeech/Magic Mic gate → SODA transcribe → BERT classify). Latency is higher.
- **Prosody-conditioned per-skill cues.** EdgeTPU heads could discriminate, e.g., a question vs. command intonation for the same words; text-side classification can't. Magic Mic absorbs the directedness piece independently, but per-skill intonation cues are lost.

These gaps are accepted as the cost of CPU recoverability and do not block the work. The EdgeTPU path, when re-enabled, restores both.

### Recoverable-Path Gap Inventory

Things genesis must add to make the CPU path functionally complete. Each row is a unit of work — pick up in PR0 below.

| # | Gap | Where | Status |
|---|---|---|---|
| G1 | Enable Magic Mic / IntendedSpeech on the CPU recovery path. Current implementation uses `OnDeviceAsrConfig.applicationDomain = AMBIENT_ONESHOT`, `attachHotwordEvent = true`, and `IntendedSpeechConfig.enabled = true` with `INTENT_DETECTION_MODE_OPEN_MIC` plus `attachOpenMicResult`. It also mirrors the secondary genesis-vs-cosmo deltas: 60s cache, explicit audio logging `NONE`, raw format `INT16`, and `requireHotword = false`. The earlier `AMBIENT_CONTINUOUS` note was superseded by fixture validation: `AMBIENT_ONESHOT` is the per-utterance path that surfaces intended-query scores. | `SodaConfigBuilder.kt:coreConfig`/`clientConfig` | Implemented locally; historical code comment records on-device fixture score validation on 2026-05-10; latest 2026-05-11 check could not re-run because no device was attached. |
| G2 | Bridge SODA directedness from callback events. Current implementation logs present SODA event types for diagnostics, handles final/partial recognition results, and treats `FinalRecognitionResult.intendedQueryScore >= intendedQueryThreshold` as the directedness trigger. This replaced the earlier unverified `hasTriggerEvent()`/HotwordEvent-only plan. | `JniSodaCapability.kt:163-212` | Implemented locally; still needs directed/undirected fixture smoke on device. |
| G3 | Surface Magic Mic / IntendedSpeech as a first-class per-source signal on `SodaController` and route it into `Gatekeeper` as the directedness gate input. | `SodaController.directedness: SharedFlow<DirectednessEvent>` with `replay = 1`, observed by `Gatekeeper` alongside transcript flow | Implemented locally. |
| G4 | Implement C1 dispatch with Magic-Mic gating semantics: ambient transcript proceeds to BERT only if a directedness event fired before the transcript and within the directedness window. Without directedness, transcript is dropped. | `Gatekeeper.onAmbientAudio` / `directednessGateFired` | Implemented locally with directional window unit coverage; device proof pending. |
| G5 | ~~Confirm Pumpkin engine integration.~~ **Resolved (task #21):** Pumpkin is bundled in every SODA pack (`semantics.pumpkin`, `pumpkin.mmap`, `config.pumpkin`) but cosmo does **not** consume any Pumpkin output. Cosmo's `SodaCallback.smali:167,236` extracts only `getHypothesisList()` from `FinalRecognitionResult`. `FinalRecognitionResult.DomainPredictionInfo` (binary domain gate) and `FinalRecognitionResult.intendedQueryScore/Threshold` (a SODA-side directedness signal) exist but cosmo ignores them. Per-skill classification in cosmo is **BERT-on-transcript only**. Genesis matches cosmo by routing transcripts through `BertClassifierGatekeepingModel` after the Magic Mic gate — no PumpkinRunner needed. Pumpkin remains a future-enhancement option (consume `intendedQueryScore` and/or surface domain-prediction info), not a parity gap. | n/a — closed | Closed |
| G6 | Ensure BERT's existing skill heads cover cosmo's 12 audio-skill set (`addToList`, `termSearch`, `photo`, `calendar`, `createDoc`, `personContext`, `eventContext`, `deepResearch`, `insight`, `browsingAgent`, `callingAgent`, `startTimer`). BERT exposes 19 text-side outputs; the consolidated successor emits the 12 parity categories. | `GatekeepingBert.kt` | Implemented locally. |
| G7 | Per-source Magic Mic enablement gate. Ambient mic requires directedness; buds/device/watch are treated as trusted sources that proceed to BERT without the ambient Magic Mic gate. | `Gatekeeper.onAmbientAudio`, `Gatekeeper.onText`, per-source `SodaController` subclasses | Implemented as current Genesis policy; still needs final smali/device confirmation for buds/device/watch. |
| G8 | Telemetry: emit a typed event for directedness gate fired vs suppressed so we can measure false-negative rate on real audio. | `Logging.kt`, `GatekeeperLoggerImpl`, `Gatekeeper.onAmbientAudio` | Implemented locally as `gatekeeper_directedness_gate` with `gate_fired` and `raw_audio_mode_active` parameters. |

### Refactor Sequencing

The dispatch invariant (C1), the consolidation contract (C4), and the CPU recovery path (C8) land in a single chain. Sequence:

**PR0 — CPU directedness path (G1, G2, G3, G5, G7).** ✅ Landed locally. SODA `AMBIENT_ONESHOT` + `IntendedSpeechConfig.OPEN_MIC`; directedness gate is `FinalRecognitionResult.intendedQueryScore >= intendedQueryThreshold`. Device-fixture smoke still pending an attached device.

**PR1 — Gatekeeper unification + CPU dispatch (C1, C2, C3, G4, G6, G8).** ✅ Landed. Unified `Gatekeeper.kt` owns subscription + dispatch. C9 selector routing replaced direct `model`/`audioModel` calls on 2026-05-12 — the EdgeTPU branch is now backend-gated via `ConformerEdgeTpuBackend.isAvailable` (false today until Pixel/Darwinn).

**PR2 — `SodaController` consolidation (C4 row 4).** ✅ Landed 2026-05-12 (per-source `SodaSession` instances).

**PR3 — Settings-Gated AOC vs Mic Dispatch (C5).** ⚠️ Partial. Routing and most Context Hub/nanoapp plumbing are implemented; callback subclass construction still requires a platform-capable build or bytecode generation.

**PR4 — Cosmo State Probe (C6).** ✅ Landed 2026-05-13 (cosmo-aligned shape: `CosmoStateSource` interface + `CosmoController` impl). The original "sealed state ADT + 15/20% hysteresis" spec was invented and is corrected in C6.

**PR5 — `IntentExtractor` (C7).** ✅ Landed 2026-05-13 (LLM-backed `LlmIntentClassifier` replacing the coarse top-skill pass-through; cosmo's parse path).

**PR6 — Delete Speculative Configurability.** ✅ Completed. All listed files deleted.

**EdgeTPU re-enablement (deferred, not in this sequence).** When (a) Pixel device returns and (b) hackathon notes resolve the Coral/Magnetar/Tensor question, flip `ConformerEdgeTpuBackend.computeIsAvailable()` from `return false` to a real `Build.SOC_MANUFACTURER` + `darwinnDelegateLoadsCleanly()` probe. The backend's `classify(RawPcm)` body is already wired through `AudioGatekeepingModel.predict`, so this is a single-predicate flip, not a rewrite.

### C9. Pluggable Backend Architecture

**Rule.** Audio gatekeeping is fronted by `AudioGatekeepingBackend` (under `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/backend/`). Three concrete backends, one selector. The unified `Gatekeeper` (post-PR1) does not call models directly — it asks `AudioGatekeepingBackendSelector.select()` for the active backend and routes through it.

**Genesis status (2026-05-17).** Implemented for all three backends, with hardware/quality proof still pending for EdgeTPU and Inherent. `Gatekeeper.onAmbientAudio` and `onAmbientAudioBytes` both route through `backendSelector.select().classify(input)`. `CpuRecoveryBackend.classify` calls `textModel.predict(transcript)` (Combined BERT+regex) after the Magic Mic directedness gate. `ConformerEdgeTpuBackend.classify` calls `audioModel.get().predict(pcm)` (gated on `isAvailable`, which stays false until Pixel/Darwinn probe lands). `InherentBackend.classify` calls `InherentAudioGatekeepingModel.predict(pcm)` when the `inherent.tflite` + metadata assets pass the Android tensor contract and raw-audio realtime gatekeeping is enabled. The C1 mutex is enforced by each backend returning null for the non-native `AudioInput` shape — no separate `audioConformerActive()` predicate is needed. Non-ambient text paths (buds/device/watch/screen/notification/location) bypass the selector and route through `cpuRecoveryBackend.classify` directly, matching cosmo's per-source dispatch.

| Backend | When active | Source of intelligence |
|---|---|---|
| `ConformerEdgeTpuBackend` | Pixel + Darwinn delegate verified at runtime (today: never) | Cosmo's recovered `audio_gatekeeper_edgetpu.tflite` via Darwinn |
| `InherentBackend` | `inherent.tflite` asset present + raw-audio realtime enabled | From-scratch CPU model from `~/inherent` project — see runtime contract below |
| `CpuRecoveryBackend` | Always available — the baseline | SODA Magic Mic for directedness + cosmo BERT for skills |

Default selection priority: EdgeTPU → Inherent → CpuRecovery. A debug `forcedBackend` setting overrides priority for A/B / regression testing. The selector caches `isAvailable` per session.

**Why this shape:** the three implementations differ in input shape (raw PCM vs. transcribed text), latency profile, and label fidelity. A single interface lets us swap them without touching the dispatch logic in `Gatekeeper`, and lets the same APK run on Pixel (with the EdgeTPU teacher when available), on a generic CPU device today (with the recovery path), and on a generic CPU device tomorrow when the from-scratch student lands (with `Inherent`).

### `~/inherent` — From-Scratch Joint Audio→Intent Model

Separate Python project at `~/inherent/`. Builds a CPU-deployable replacement for the EdgeTPU Conformer's functional contract (`isInteresting` + 12 per-skill heads in one pass) using public corpora plus TTS-synthesized data for the 5 intent heads no public dataset covers. ETA: ~5–6 weeks of focused work, no specialized hardware needed.

The load-bearing artifact: `~/inherent/contracts/runtime_contract.md`. That file pins the input/output tensor shapes, head order, threshold sidecar format, and performance budget that genesis's `InherentBackend` consumes. Both sides depend on it; changes require coordinated updates.

Project layout summary (for genesis-side context):

- `src/inherent/data/{directedness,intents,synthesis}.py` — corpus assembly + TTS synthesis
- `src/inherent/models/architecture.py` — small Conformer (default) or `wav2vec2-base` backbone + 13-head sigmoid
- `src/inherent/training/train.py` — focal-BCE multi-label training
- `src/inherent/eval/evaluate.py` — per-head AUC/EER/FPR-at-95
- `src/inherent/export/tflite.py` — int8 quantization + TFLite export with metadata sidecar

When a new inherent export ships, run `scripts/install-inherent-litert-artifact.py --source-dir <export-dir>`. The current validated default is `~/inherent/artifacts/asap-static3000-int8`; the old `model-groups/001/export/litert` export is only 50 frames and fails the Android contract. After provisioning, enable raw-audio realtime gatekeeping and `InherentBackend.isAvailable` can flip true. No other genesis change should be needed.

## Marching Orders

This section is the operational companion to the contract addendum. It turns each contract rule into concrete file edits, sequenced so each PR leaves the build green, the SODA smoke test passing, and the dispatch invariant either correct or visibly under construction (never silently broken). Read top-to-bottom; do not skip ahead.

### Ground Rules (read before touching any file)

1. **Cosmo is the spec.** When in doubt, open `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/<package>/<Class>.smali` and read it. Do not infer cosmo's behavior from genesis code; genesis is what we are correcting.
2. **Cite the smali in commit messages.** Every commit that re-encodes cosmo behavior should cite the smali path and line range it mirrors. Future readers (including future-you) need to verify the encoding without re-doing the audit.
3. **Run the SODA smoke after every PR in this sequence.** `JniSodaCapability` is fragile (recovered jar, bytecode-rewritten desugar, JNI .so dlopen on the device). A working SODA path before the change must still produce a final transcript after the change. The check: `adb shell am start -n com.google.research.air.cosmo.debug/.MainActivity`, push a fixture WAV via `GatekeeperDebugReceiver`, watch logcat for `JniSodaCapability: SODA final transcript: …`.
4. **Do not add new interfaces, factories, or sealed result types in any of these PRs.** The point of this work is consolidation. If a refactor seems to require a new abstraction, stop — that is a signal you are mis-reading the cosmo shape.
5. **Delete code as you go; do not leave stubs.** Half-deleted classes, `@Deprecated` shells, and "kept for compatibility" re-exports are forbidden. If a file's only remaining purpose is to delegate to its replacement, delete it and update call sites in the same commit.
6. **Each PR must compile and pass `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug`.** No "WIP" merges. If a PR is too large to land green, split it along seams that themselves leave the build green; do not split along seams that require a broken intermediate state.

### PR0 — CPU Directedness Path (G1, G2, G3, G5, G7)

**Current status (2026-05-11): locally implemented, device-smoke pending.** The current implementation uses SODA's `AMBIENT_ONESHOT` + `IntendedSpeechConfig.OPEN_MIC` path, not the earlier `AMBIENT_CONTINUOUS` plan. Directedness is emitted from `FinalRecognitionResult.intendedQueryScore >= intendedQueryThreshold`, surfaced through `SodaController.directedness` with `replay = 1`, and consumed by `Gatekeeper`. The 2026-05-11 local check could not re-run SODA fixture smoke because no device was attached.

**Pre-flight.**
- Confirm the SODA smoke still produces a final transcript on the MediaTek device. Baseline.
- Re-read `~/Downloads/cosmo_baksmali/classes41/com/google/speech/soda/TriggerTypeProto$TriggerType.smali` and the recovered `FinalRecognitionResult` intended-query fields before changing the directedness discriminator. Trigger events remain useful diagnostics, but the active CPU recovery gate is score/threshold based.

**Step 1 — Resolve G1 (enable Magic Mic in SODA config).**

Magic Mic / IntendedSpeech activation is driven by the SODA config domain plus intended-speech config. The original plan below used `AMBIENT_CONTINUOUS`; current device-oriented wiring uses `AMBIENT_ONESHOT` because it is the per-utterance path that surfaces intended-query scores on `FinalRecognitionResult`. Keep this current shape unless a new device smoke proves a better SODA discriminator.

In `app/src/main/kotlin/com/google/research/air/cosmo/lib/soda/SodaConfigBuilder.kt:coreConfig`:

```kotlin
val asrConfig =
    SodaCoreConfigProto.OnDeviceAsrConfig
        .newBuilder()
        .setLanguagePackDirectory(languagePackDirectory.absolutePath)
        .setApplicationDomain(ApplicationDomainProto.ApplicationDomain.AMBIENT_ONESHOT)
        .setAttachHotwordEvent(true)
        .build()
```

Also set `IntendedSpeechConfig.enabled = true`, `intentDetectionMode = INTENT_DETECTION_MODE_OPEN_MIC`, and `attachOpenMicResult = true` so `FinalRecognitionResult.intendedQueryScore` and `intendedQueryThreshold` are populated.

Mirror four other genesis-vs-cosmo deltas in the same builder (cite `SodaConfigUtils.smali:451, 467-469, 719-723, 765`):

```kotlin
// CachedAudioConfig: 60s window, not 30s
.setMaxAudioMs(60_000)

// AudioLoggingPolicy: explicit NONE
.setPolicyType(LoggingProto.AudioLoggingPolicyType.AUDIO_LOGGING_POLICY_TYPE_NONE)

// RawAudioFormat (in clientConfig encoded provider): explicit INT16
.setSampleFormat(AudioProto.RawAudioFormat.SampleFormat.INT16)

// SodaClientConfig: explicit hotword=false
.setRequireHotword(false)
```

Cite `SodaConfigUtils.smali:287-289` in the PR description as the smali source for the domain selection.

**Step 2 — Resolve G2 (handle trigger events in callback bridge).**

Edit `app/src/main/kotlin/com/google/research/air/cosmo/lib/soda/JniSodaCapability.kt` (`TranscriptingSodaCallback.handleSodaEvent`). The current implementation logs present SODA event types for diagnostics, handles recognition events, and treats intended-speech result fields as the directedness signal:

```kotlin
if (recognitionEvent.hasFinalResult()) {
    handleIntendedSpeechSignal(recognitionEvent.finalResult)
    emitFinalTranscript(recognitionEvent.finalResult)
}
```

`SodaTranscriptCallback` interface gains a new method: `fun onDirectednessTrigger(at: Instant) {}` (default impl no-op for backward compatibility within this PR — PR1 will remove the default).

**Step 3 — Resolve G3 (surface directedness flow on `SodaController`).**

Current shape in `SodaController.kt`:
```kotlin
private val _directedness = MutableSharedFlow<DirectednessEvent>(replay = 1, extraBufferCapacity = 16)
override val directedness: SharedFlow<DirectednessEvent> = _directedness

data class DirectednessEvent(val source: ContextSource, val at: Instant)
```

`JniSodaCapability` calls `onDirectednessTrigger`, and each source-owned `SodaController` emits `DirectednessEvent(source, at)` into its own flow.

**Step 4 — Resolve G5 (Pumpkin invocation trace). [RESOLVED — no work needed]**

Cosmo does not consume Pumpkin output. `SodaCallback.smali:167,236` reads only `getHypothesisList()` from `FinalRecognitionResult`. The Pumpkin assets in the SODA pack are loaded by `libsoda_dev_jni.so` for SODA's internal grammar matching but their results aren't surfaced to the Java callback. Genesis matches cosmo's behavior by relying on `BertClassifierGatekeepingModel` for per-skill classification on the transcript text. Skip this step.

Implementation note: Genesis now consumes `FinalRecognitionResult.getIntendedQueryScore()` + `getIntendedQueryThreshold()` for the CPU recovery directedness gate. This is a Genesis recovery mechanism for non-EdgeTPU devices, not a claim that cosmo consumed those fields.

**Step 5 — Resolve G7 (per-source Magic Mic enablement).**

Current Genesis policy: ambient mic requires directedness before BERT; buds/device/watch proceed to BERT as trusted sources. This still needs final smali/device confirmation for source parity.

**Step 6 — Verify on the MediaTek device.**

```
./gradlew :app:installDebug
adb logcat -c
adb shell am broadcast -a com.google.research.air.cosmo.debug.INJECT_AUDIO --es target soda --es fixture timer_10min.wav
adb logcat | grep -E "JniSodaCapability|SodaController"
```

Definition of done:
- Directed-command fixture logs intended-query score and threshold with `directed=true`.
- Undirected-conversation fixture logs either no intended-query fields or a score below threshold (record one if needed; e.g., a ~10 sec clip of background conversation with no command intent).
- `SODA final transcript:` still appears for both fixtures (Magic Mic gates downstream classification, not transcription itself).

### PR1 — Gatekeeper Unification (C1 + C2 + C3 + G4 + G6 + G8)

**Current status (2026-05-12): implemented and locally green.** The unified `Gatekeeper` owns subscriptions and dispatch, BERT/audio model families are consolidated, CPU directedness gating is wired, directedness telemetry exists, C9 selector-routed classification is the only path (direct `model`/`audioModel` constructor refs deleted), and `GatekeeperDispatchInvariantTest` covers the C1 tripwire including the Conformer-active case. Remaining work: connected-device SODA smoke and exact `GatekeeperOutput` shape parity (current emit type is `GatekeepingResult`).

**Pre-flight.**

- Re-read `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/gatekeeping/Gatekeeper.smali` end-to-end. Note the 15-arg constructor signature, the six `Gatekeeper$2..$7` inner classes, and the `processInput` / `processRawAudioInput` private methods.
- Re-read `Gatekeeper$2$1.smali` lines 135–277. Confirm the conditional and the unconditional `return-void` after `processRawAudioInput` for your own eyes.
- Run the SODA smoke and confirm a final transcript on the attached device. This is your "before" baseline.

**Step 1 — Collapse the BERT family into `BertClassifierGatekeepingModel`.**

Files to delete:
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertGatekeeperRunner.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/NativeBertGatekeeperRunner.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertNativeBridge.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/JniBertNativeBridge.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertGatekeeperRunResult.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertGatekeeperOutputMapper.kt` (its function becomes a private `mapBertOutputToSkills` in `BertClassifierGatekeepingModel`)
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertOutputThresholds.kt` (becomes a `private object Thresholds` inside `BertClassifierGatekeepingModel`)
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertOutputThresholdProfile.kt` (becomes a private enum inside `BertClassifierGatekeepingModel`)
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/BertBackendCandidate.kt` (becomes a private `data class Backend` inside `BertClassifierGatekeepingModel`)

The new `BertClassifierGatekeepingModel.kt` shape:

- Constructor takes `Context`, `BertEncoderImpl` (the JNI thin wrapper — keep this; it's the C2-equivalent of cosmo's encoder), `SocResolver` (until C4-row-8 deletes it), `Logger`.
- Implements `GatekeepingModel.predict(input: String, context: ContextSource): GatekeepingModelOutput` directly.
- Owns the JNI handle as a `private val encoderHandle: Long` initialized from `BertEncoderImpl.create(...)` in `init { }`.
- Owns thresholds, output mapping, backend selection as private members.
- Implements `Closeable` and calls `BertEncoderImpl.nativeDestroy(encoderHandle)` in `close()`.

**Step 2 — Collapse the audio-gatekeeper family into `AudioGatekeepingModel` + `ConformerEncoderImpl`.**

Files to delete:
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/RawAudioGatekeeper.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/RawAudioGatekeeperRunner.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/ManifestRawAudioGatekeeperRunner.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/ConformerAudioGatekeeperRunner.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/AudioInterpreter.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/AudioInterpreterFactory.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/UnavailableAudioInterpreterFactory.kt`
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/ConformerAudioFrontendInput.kt` (becomes private inside `ConformerEncoderImpl`)
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/ConformerAudioConstants.kt` (becomes `private const val` inside `ConformerEncoderImpl`)
- `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/AudioGatekeeperThresholds.kt` (becomes a `private object` inside `AudioGatekeepingModel`)
- All `ResolvedAudio*` and `AudioInterpreterUnavailableException` types

Files to keep (rename if needed):
- `LiteRtAudioInterpreterFactory` content folds into `ConformerEncoderImpl` as private `loadModelFile` and the LiteRT JNI calls. `AudioGatekeeperLiteRtImpl` content folds into `AudioGatekeepingModel`.

The new `AudioGatekeepingModel.kt` shape mirrors cosmo's `AudioGatekeepingModel.smali`: implements `GatekeepingModel.predict(pcm: ByteArray, context: ContextSource): GatekeepingModelOutput`. Owns a `ConformerEncoderImpl` instance as a private member; calls `runFrontendStage(pcm)` then `runBackendStage(features)` then maps the backend output to the `GatekeepingModelOutput` shape.

**Step 3 — Unify the `GatekeepingModel` interface.**

Edit `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeepingModel.kt`:

```kotlin
interface GatekeepingModel : Closeable {
    fun predict(input: String, context: ContextSource): GatekeepingModelOutput
    fun predict(pcm: ByteArray, context: ContextSource): GatekeepingModelOutput
}
```

Both impls override both overloads. The audio impl throws `UnsupportedOperationException` from the `String` overload; the BERT impl throws from the `ByteArray` overload. Cosmo does the same — see the smali; the unused overload is `throw new UnsupportedOperationException` in the bytecode.

**Step 4 — Rewrite `Gatekeeper.kt` to own subscription + dispatch.**

Open `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/gatekeeping/Gatekeeper.smali`. Mirror the constructor signature exactly. The new `Gatekeeper.kt` shape:

```kotlin
@Singleton
class Gatekeeper @Inject constructor(
    @AmbientAudioTextEphemeralBuffer private val ambientAudioTextReader: EphemeralBufferReader<TranscriptDelta>,
    @AmbientAudioBytesEphemeralBuffer private val ambientAudioBytesReader: EphemeralBufferReader<ByteArray>,
    @BudsAudioEphemeralBuffer private val budsReader: EphemeralBufferReader<TranscriptDelta>,
    @DeviceAudioEphemeralBuffer private val deviceReader: EphemeralBufferReader<TranscriptDelta>,
    @WatchAudioTextEphemeralBuffer private val watchTextReader: EphemeralBufferReader<TranscriptDelta>,
    @WatchAudioBytesEphemeralBuffer private val watchBytesReader: EphemeralBufferReader<ByteArray>,
    private val bertModel: BertClassifierGatekeepingModel,
    private val audioModel: dagger.Lazy<AudioGatekeepingModel>,
    private val settingsRepository: SettingsRepository,
    private val realtimeProcessing: RealtimeProcessing,
    private val skillsRepository: SkillsRepository,
    private val audioAccumulator: AudioAccumulator,
    private val clock: Clock,
    private val logger: Logger,
) {
    private val _output = MutableSharedFlow<GatekeeperOutput>(replay = 0, extraBufferCapacity = 64)
    val output: SharedFlow<GatekeeperOutput> = _output.asSharedFlow()

    fun start(scope: CoroutineScope) {
        scope.launch { ambientAudioTextReader.subscribe().collect { onAmbientAudioText(it) } }
        scope.launch { ambientAudioBytesReader.subscribe().collect { onAmbientAudioBytes(it) } }
        scope.launch { budsReader.subscribe().collect { processInput(it.transcription, ContextSource.BUDS_AUDIO) } }
        scope.launch { deviceReader.subscribe().collect { processInput(it.transcription, ContextSource.DEVICE_AUDIO) } }
        scope.launch { watchTextReader.subscribe().collect { processInput(it.transcription, ContextSource.WATCH_AUDIO) } }
        scope.launch { watchBytesReader.subscribe().collect { /* watch audio bytes path */ } }
    }

    private suspend fun onAmbientAudioText(td: TranscriptDelta) {
        // C1 dispatch — exactly one classification path runs per transcript.
        // Two mutually-exclusive branches: EdgeTPU audio Conformer (deferred) vs CPU
        // Magic Mic + BERT. C8 mandates functional parity between them.
        if (audioConformerActive()) {
            // Reserved for EdgeTPU re-enablement (post-Pixel-return + hackathon-notes).
            // Today this predicate is always false; processRawAudioInput is unreachable.
            return
        }
        // CPU path: SODA Magic Mic provides the directedness gate.
        // Drop ambient transcripts unless a Magic Mic event fired in the same window.
        if (!directednessGateFired(td.window, ContextSource.AMBIENT_AUDIO)) {
            telemetry.emit(GatekeeperDirectednessGate(fired = false, source = AMBIENT_AUDIO))
            return
        }
        telemetry.emit(GatekeeperDirectednessGate(fired = true, source = AMBIENT_AUDIO))
        processInput(td.transcription, ContextSource.AMBIENT_AUDIO)
    }

    private fun audioConformerActive(): Boolean = false  // EdgeTPU re-enablement flag (C8)

    private fun directednessGateFired(window: TranscriptWindow, source: ContextSource): Boolean {
        // Buds/device/watch are trusted sources — no Magic Mic gate (cosmo per-source
        // behavior per G7). Ambient mic requires a TRIGGER_TYPE_MAGIC_MIC event from
        // SodaController.directedness within the transcript window.
        if (source != ContextSource.AMBIENT_AUDIO) return true
        val lastTrigger = ambientSodaController.directedness.replayCache.lastOrNull() ?: return false
        return lastTrigger.at in window.start..window.end
    }

    private suspend fun onAmbientAudioBytes(pcm: ByteArray) {
        audioAccumulator.append(pcm)
        if (!audioAccumulator.hasFullWindow()) return
        val window = audioAccumulator.consumeWindow()
        processRawAudioInput(window, ContextSource.AMBIENT_AUDIO)
    }

    private suspend fun processInput(text: String, source: ContextSource) {
        val output = bertModel.predict(text, source)
        _output.emit(GatekeeperOutput(text, output, source, clock.now()))
    }

    private suspend fun processRawAudioInput(pcm: ByteArray, source: ContextSource) {
        val output = audioModel.get().predict(pcm, source)
        if (output.isTriggered) {
            // Audio gate triggered — now and only now does the text classifier run.
            // (Or: emit directly; check cosmo smali to confirm whether a triggered
            // audio gate fans out to BERT or emits its own GatekeeperOutput.)
        }
    }

    private companion object {
        const val TAG = "Gatekeeper"
    }
}
```

**CRITICAL.** Before finalizing `processRawAudioInput`, re-open `Gatekeeper.smali` and trace what happens after `processRawAudioInput` returns true vs false. Do *not* guess based on the pseudocode above — the comment notes the open question deliberately. If cosmo emits directly without calling BERT, do that. If cosmo fans out to BERT only on positive audio gating, do that. Encode whatever cosmo does, not what seems reasonable.

**Step 5 — Delete the parallel jobs from `CosmoController`.**

Edit `app/src/main/kotlin/com/google/research/air/cosmo/controller/CosmoController.kt`:

- Delete `audioJob` and `rawAudioGatekeeperJob` fields and the methods that start/stop them (lines 119–153 in the current file).
- Delete the `gatekeeper.processInput(...)` and `rawAudioGatekeeper.processAmbientAudioWindow(...)` call sites.
- Add a single `gatekeeper.start(controllerScope)` call in the controller's `init()` method.
- Add a single `controllerScope.launch { gatekeeper.output.collect { actionPipeline.handle(it) } }` collector for the action pipeline.

**Step 6 — Update Hilt bindings.**

Edit the gatekeeping module(s) under `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/di/`:

- Bind `BertClassifierGatekeepingModel` as `@IntoSet GatekeepingModel` (or whatever cosmo's binding shape is — read `GatekeeperOutputModule.smali` if present).
- Bind `AudioGatekeepingModel` as `dagger.Lazy<AudioGatekeepingModel>` (cosmo wraps the audio model in `Lazy` so it isn't constructed unless the audio gate is enabled).
- Delete bindings for the deleted runner/factory/interface types.
- Run `./gradlew :app:kspDebugKotlin` and fix any unresolved Hilt graph errors.

**Step 7 — Add the dispatch-invariant unit test.**

New file: `app/src/test/kotlin/com/google/research/air/cosmo/gatekeeping/GatekeeperDispatchInvariantTest.kt`

Tests to write:
- `whenRawAudioEnabledAndRealtimeOn_ambientTextDoesNotInvokeBertModel` — fake `SettingsRepository` returns `isRawAudioGatekeeperEnabled = true`, fake `RealtimeProcessing.value = true`. Emit a `TranscriptDelta` on the ambient text reader. Assert the mock `BertClassifierGatekeepingModel.predict(String, …)` was never called.
- `whenRawAudioDisabled_ambientTextInvokesBertModel` — settings `false`. Same emission. Assert `predict(String, …)` was called exactly once.
- `whenRealtimeOff_ambientTextInvokesBertModel` — settings `true`, realtime `false`. Assert `predict(String, …)` was called exactly once (matches cosmo: both flags must be true to take the audio branch).
- `audioBytesAlwaysInvokesAudioModel_regardlessOfTextSettings` — emit on bytes reader. Assert `audioModel.predict(ByteArray, …)` was called.

These four tests are the regression tripwire for C1. If any future refactor breaks them, the privacy invariant has regressed.

**Step 8 — Verify.**

```
./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Then on the attached device:
```
./gradlew :app:installDebug
adb logcat -c
adb shell am broadcast -a com.google.research.air.cosmo.debug.INJECT_AUDIO --es target soda --es fixture timer_10min.wav
adb logcat | grep -E "JniSodaCapability|Gatekeeper"
```

Definition of done: `SODA final transcript: Set a timer for 10 minutes` appears in logcat *and* exactly one of `Gatekeeper: bert.predict` / `Gatekeeper: audio.predict` log lines appears per transcript (never both).

### PR2 — SodaController Consolidation (C4 row 4)

**Current status (2026-05-12): implemented; type-fold decision is the only follow-up.** The `SodaCapability` interface, `UnavailableSodaCapability`, and `SodaCapabilityModule` are gone. `JniSodaCapability` is a thin native-runtime probe + `prepare()` that resolves the pack dir. Each per-source `SodaController` subclass (`Mic`, `Streaming`, `Buds`, `Device`, `Watch`, `Call`) constructs its own `SodaSession(context, packDir, clock)` with its own `TranscriptingSodaCallback` — multiple concurrent recognition sessions are now possible, matching cosmo `SodaController.smali` lines 619–1375. `GatekeeperDebugReceiver` constructs its own dedicated `SodaSession` for audio injection so it never competes with production controllers. Remaining: optional fold of `SodaPrepareResult`/`SodaStartResult`/`SodaTranscriptCallback`/`SodaPack*` types into the call sites that consume them, and connected-device SODA smoke for runtime parity claim.

**Estimate.** ~600 LOC net negative; ~12 files deleted.

**Step 1.** Re-read `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/lib/soda/SodaController.smali`. Note: cosmo has `SodaController` as an abstract base + per-source subclasses (`AmbientSodaController`, `BudsSodaController`, etc.). Each subclass owns its `Soda` instance, its `SodaCallback`, and its config-builder calls. There is no separate `SodaCapability` interface in cosmo.

**Step 2.** Already done: delete the `SodaCapability` interface, `UnavailableSodaCapability`, and `SodaCapabilityModule`; make `JniSodaCapability` the concrete native-runtime probe/language-pack preparer.

**Step 3.** Done in first-pass form: move per-recognition-session state into `SodaSession`, and have each per-source `SodaController` construct its own `SodaSession(context, packDir, clock)` with its own callback.

**Step 4.** Remaining consolidation decision:
- `SodaPrepareResult`, `SodaStartResult`, and `SodaTranscriptCallback` live in `SodaCapability.kt` as operation contracts shared by `JniSodaCapability`, `SodaSession`, and `SodaController`. Fold them only if the call sites remain readable.
- `SodaPackResolution`, `SodaPackKind`, and `SodaPackDescriptor` still support `SodaLanguagePackProvider`'s bundled/cache/download selection. Keep them only if runtime pack delivery remains an active operator workflow; otherwise fold pack selection into the controller/session path.
- `SodaConfigBuilder.kt` stays acceptable because cosmo has `SodaConfigUtils.smali`.

**Step 5.** Verify per-source subclasses (`AmbientSodaController`, `BudsSodaController`, etc.) construct their own `Soda` instance with their own config and listener. No shared singleton across sources.

**Step 6.** Run the SODA smoke test. Final transcripts must still appear.

### PR3 — Settings-Gated AOC vs Mic Dispatch (C5)

**Current status (2026-05-17): routing implemented, AOC callback subclass pending.** `SODAAmbientAudioCaptor.startCapture` reads `settingsStore.isUseNanoappAudioEnabled()`, calls `recordServiceManager.startAocRecording(listener)`, and falls back to mic when the call returns false. `RecordServiceManager.startAocRecording` now delegates to `NanoAppClient`, which performs Context Hub discovery, verifies the ambient-audio nanoapp, creates LIVE_STREAM/OFF messages, forwards nanoapp payload bytes into `RecordServiceListener.onBytesReceived`, and mirrors cosmo's silence-injection behavior. The remaining platform gap is the `ContextHubClientCallback` subclass instance required by `ContextHubManager.createClient`.

**Estimate.** ~50 LOC; 1 file touched.

Current shape in `app/src/main/kotlin/com/google/research/air/cosmo/audiocapture/AudioCaptors.kt`:

```kotlin
val aocStarted =
    settingsStore.isUseNanoappAudioEnabled() && recordServiceManager.startAocRecording(listener)
if (!aocStarted) {
    recordServiceManager.startMicRecording(listener)
}
```

Remaining work: provide a platform-capable `ContextHubClientCallback` implementation, either by compiling this path against a platform/system stub jar for a signed build or by adding a small bytecode-generated subclass. Stock public-SDK builds should keep returning false so the caller falls back to mic.

### PR4 — Cosmo State Probe (C6)

**Current status (2026-05-13): cosmo-aligned shape landed.** `controller/CosmoStateSource.kt` defines the 2-method interface; `CosmoController` implements it (`isCosmoEnabled()` reads `settingStore.isGlobalCosmoEnabled()`; `isAnyMicAudioCaptureOn()` ORs the four per-source job-active checks). Hilt binds `CosmoStateSource` to `CosmoController` via `CosmoControllerModule`. The earlier "sealed state ADT + 15%/20% hysteresis + captor-wide state-flow gating" spec was invented and is removed — cosmo's `CosmoStateSource.smali` only has the two boolean methods, and `CosmoController.smali` declares it directly. Genesis's `setting/Settings.kt::CosmoStateManager` remains as a genesis-only convenience for the settings UI / snooze flow; it is not a parity target.

**Remaining work (optional, not parity).** If a future consumer needs probe access without dragging `CosmoController` into the dependency graph, prefer `@Inject CosmoStateSource` over `@Inject CosmoController` in that consumer. Not a code change to land now.

### PR5 — IntentExtractor as Second Gatekeeper-Output Consumer (C7)

**Current status (2026-05-13): cosmo-shaped LLM extractor landed.** `intent/IntentExtractor.kt` now binds `LlmIntentClassifier` as the production `IntentClassifier`. The classifier loads no prompt asset yet — it constructs the prompt inline from `result.input` + ranked `result.detectedSkills` — and matches cosmo `intentextraction/IntentExtractorImpl.smali` on the parse path (`stripJsonFences` then `JSONObject.optString`/`optDouble`). `IntentRecord` carries `intent`, `slots`, `confidence`, `input`, `modelId`. `IntentExtractorTest` switched to a `CannedIntentClassifier` fake; `LlmIntentClassifierTest` covers 7 parsing/failure scenarios.

**Cosmo source.** `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/intentextraction/IntentExtractorImpl.smali` — constructor takes `(Context, SharedFlow, EphemeralBufferReader, EphemeralBufferReader, LLM, CoroutineScope[, TimeSource, Boolean])`; private const `INTENT_EXTRACTION_PROMPT_ASSET_PATH = "intent_extraction_prompt.jinja2"`; uses `org.json.JSONObject` + `optString` for tolerant extraction.

**Optional parity follow-ups.**
- Move prompt to asset (`assets/intent_extraction_prompt.txt` or `.jinja2` if a renderer is justified).
- Wire two extra `EphemeralBufferReader`s for context (recent transcripts + screen interactions, mirroring cosmo's `gatherContext`).
- Optional `TimeSource` + `Boolean` constructor overload if a debug/test seam is needed.

None of the above block the parity claim — they're enrichments.

### PR6 — Delete Speculative Configurability (C4 rows 5–8)

**Current status (2026-05-12): completed.** All target files are deleted (verified by grep — no source files match `GatekeepingModelLoader`, `ManifestGatekeepingModelLoader`, `LocalLlmModelDelivery*`, `RemoteInferenceConfig*`, `AICoreExperiment*`, or `SocResolver*`). Sections below are kept as historical record; no remaining work in PR6.

**Estimate.** ~1200 LOC net negative; ~25 files deleted.

For each deletion, first run `grep -r "ClassNameToDelete" app/src/main` to confirm no production wiring depends on it. If a test depends on it, delete the test (the test was testing the speculative layer, not a contract).

**Row 5 — Manifest indirection.** Keep `model-manifest.json` and the build-time verifier (`scripts/download-models.sh --verify-only`) — those are legitimate CI gates. But delete the *runtime* manifest indirection: `GatekeepingModelLoader`, `ManifestGatekeepingModelLoader`, `GatekeepingModelLoadResult`, `GatekeepingModelAsset`. Replace runtime calls with direct `FileUtil.loadMappedFile(context, "models/<filename>")` at the BERT and audio model construction sites.

**Row 6 — LiteRT-LM delivery state machine.** Delete `LocalLlmModelDelivery`, `DefaultLocalLlmModelDelivery`, `LocalLlmDeliveryMode`, `LocalLlmModelSource`, `LocalLlmModelPaths`, `ResolvedLocalLlmModel`, and the 6 BuildConfig fields. `LiteRtLmLLM` constructs its bundle path inline from a single `cosmo.localLlm.litertLmModelId` field. If the runtime-download path is ever needed, re-add it then — not in anticipation.

**Row 7 — Remote inference config provider.** Delete `RemoteInferenceConfigProvider`, `LayeredRemoteInferenceConfigProvider`, `RemoteInferenceConfigStore`, `AndroidKeyStoreRemoteInferenceConfigStore`, `RemoteInferenceConfigSource`, the encrypted DataStore, and the `RemoteInferenceConfigDataStore` qualifier. `RemoteInferenceClient`, `OkHttpAnonymousCredentialIssuerClient`, etc. read directly from `BuildConfig.COSMO_REMOTE_INFERENCE_*`. **Caveat.** If a teamfood/internal workflow is *currently* using the runtime override (check Slack/PRs), delay this row until that workflow has a build-time replacement.

**Row 8 — AICore experiment toggle.** Delete `AICoreExperimentToggle`, `BuildConfigAICoreExperimentToggle`, `DataStoreBackedAICoreExperimentToggle`, `AICoreExperimentMode`, the DataStore file, and the `cosmo.aicore.experimentalNonPublic` Gradle property. `AICoreClientWrapper` always uses the public `GenerativeModel` API. Re-introduce the toggle if and when an experimental non-public AICore branch lands.

**SocResolver.** Delete `SocResolver`, `DefaultSocResolver`, `SocClass`, the Hilt binding. Inline `Build.SOC_MANUFACTURER` reads at the BERT backend selection site (the only consumer).

### Quality Bar — How To Tell You Did It Well

- **The diff is net-negative.** If PR1 + PR2 + PR6 add lines on net, you preserved scaffolding you should have deleted. Re-audit.
- **No new top-level types ending in `Runner`, `Factory`, `Provider`, `Resolver`, `Bridge`, `Wrapper`, `Capability`, or `Result`.** These suffixes are markers of speculative indirection. Cosmo uses none of them in the gatekeeping/SODA code.
- **Each `Gatekeeper`/`SodaController` class fits in one file readable end-to-end in under 10 minutes.** If a file requires re-reading to follow, it is still over-fragmented internally — split into private methods, not new classes.
- **The four C1 unit tests pass.** They are the privacy regression tripwire.
- **The SODA smoke produces a final transcript.** If it stopped working, you broke the recovered jar wiring; revert and try again with smaller steps.
- **Every commit cites a smali path it mirrors.** If you can't cite one, you are inventing architecture instead of encoding cosmo's. Stop and re-read the smali.

### When To Stop And Re-Plan

- If PR1 grows past ~3000 LOC delta or ~40 files touched, stop. Either you are rewriting more than C1+C2+C3 require (scope creep) or the existing fragmentation has dependencies that need their own PR first. Surface the blocker in `docs/blocker-register.md` and re-plan.
- If a deletion in PR6 reveals a production consumer you didn't expect, do not "just keep it." Ask: was the consumer added because the speculative layer existed (justifying the speculation post-hoc)? If yes, delete the consumer too. If the consumer is load-bearing, document why in this file as a new C-rule and keep the layer.
- If the SODA smoke regresses and you cannot localize the cause within an hour, revert the PR. The recovered SODA stack is the most fragile thing in the codebase; partial breaks compound fast.
