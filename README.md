# FM

**An ambient agent with local on-device intelligence.**

FM listens, watches, and acts on a phone without sending audio off the device
for the gatekeeping decision. It's a clean-room reimplementation of cosmo
(Google's research ambient assistant), built from publicly recoverable
artifacts under DMCA §1201(f) interoperability provisions. Ambient mic →
on-device ASR → on-device directedness classifier → on-device intent
extraction → on-device LLM (with remote fallback) → action dispatch.

> ⚠️ Not affiliated with Google. Recovered models and JNI shims sourced from
> the cosmo APK; clean-room Kotlin reimplementation around them.

## Capabilities

Each bar is 5 segments. 🟩 = shipping, 🟨 = partial / gated, ⬜ = planned or deferred.

### Capture sources

| Subsystem | Progress | Notes |
|---|---|---|
| Ambient microphone | 🟩🟩🟩🟩🟩 | Per-source `SodaSession`; recovered SODA blend; `AMBIENT_ONESHOT` + IntendedSpeech directedness |
| Pixel Buds (Bluetooth SBC) | 🟩🟩🟩🟩🟩 | `MediaCodec`-based decoder; no vendored `libsbcdecoder_jni.so` needed |
| Device / media audio | 🟩🟩🟩🟩🟩 | `AudioSource = REMOTE_SUBMIX` |
| Call audio | 🟩🟩🟩🟩🟩 | `AudioSource = VOICE_CALL` |
| Watch audio (Wear OS) | 🟩🟩🟩🟩🟩 | Streams over `WearableAudioStreamService` |
| Screen text (accessibility-driven) | 🟩🟩🟩🟩🟩 | Periodic capture |
| Screen interactions (clicks, focus, scroll) | 🟩🟩🟩🟩🟩 | Accessibility event listener |
| Notifications | 🟩🟩🟩🟩🟩 | `NotificationListenerService` |
| Location | 🟩🟩🟩🟩🟩 | Coarse + fine permission gated |
| Glass / XR projected capture | 🟨🟨🟨⬜⬜ | Service binds; downstream consumer not wired |
| Camera (image analysis frames) | 🟨🟨🟨⬜⬜ | Frames captured; prompt-grounding consumer not built |
| AOC nanoapp audio transport | 🟨🟨🟨⬜⬜ | Routing + Context Hub/nanoapp plumbing live; callback subclass needs platform-capable compile or bytecode-gen |

### On-device speech (SODA)

| Subsystem | Progress | Notes |
|---|---|---|
| End-to-end ASR (transcripts) | 🟩🟩🟩🟩🟩 | Runs inside `libsoda_dev_jni.so`; launch path uses CPU pack after Pixel 10 rejected TPU pack; Pixel fixture corpus routes 9 speech phrases through SODA → regex skills |
| Magic Mic / IntendedSpeech gate | 🟩🟩🟩🟨⬜ | CPU recovery directedness gate is wired; human-recorded fixture proof still pending |
| Endpointer / punctuation / denorm / G2P | 🟩🟩🟩🟩🟩 | Internal to SODA; transcripts arrive complete |
| Voice Match (speaker ID) | 🟨🟨⬜⬜⬜ | JNI stub + config plumbing; enrollment UI not ported |
| Hotword event routing | 🟨🟨⬜⬜⬜ | Events arrive at callback; logged-only, no action wiring |
| Diarization | ⬜⬜⬜⬜⬜ | Bundled in pack; config bit not enabled |

### Gatekeeping (directedness + skill classification)

| Subsystem | Progress | Notes |
|---|---|---|
| Unified `Gatekeeper` (single dispatch) | 🟩🟩🟩🟩🟩 | Owns subscriptions; C1 mutex via backend type-filter |
| BERT classifier (CPU + Tensor G4 + Tensor G5) | 🟩🟩🟩🟩🟩 | Combined BERT + regex fusion |
| C9 selector-routed dispatch | 🟩🟩🟩🟩🟩 | Backend returns null for non-native input type — that IS the mutex |
| Conformer EdgeTPU audio backend | 🟩🟩🟩🟩⬜ | Code path bundled and direct Pixel probe is available; Pixel 10 fails closed at Darwinn/TFLite create with `Internal error: Found invalid handle` |
| Inherent (joint audio→intent) backend | 🟩🟩🟩🟩⬜ | Pixel 10 runtime proof passes; scorecard shows 8/8 labeled positives scored, 0/8 top-1, 3/3 negatives clean, so current model quality is not launch-ready |
| MediaTek Dimensity (mt6989) BERT variant | 🟩⬜⬜⬜⬜ | Manifest slot reserved; AOT pipeline drafted; model not built yet |

### Intent + fulfillment

| Subsystem | Progress | Notes |
|---|---|---|
| LLM-backed intent extractor (second `Gatekeeper.output` subscriber) | 🟩🟩🟩🟩🟩 | Cosmo `{reasoning, final_answer}` parse path; context-gather from recent transcripts + screen interactions |
| Skill catalog (22 action types from `filtered_skill_schema.textproto`) | 🟩🟩🟩🟩🟩 | Schema-driven, required args + trigger regexes + permission gates |
| Fulfillment runner + confirmation policy | 🟩🟩🟩🟩🟨 | Functional end-to-end; subtype executors split; richer execution-error UI remains |
| Browsing agent surface (TaskMonitor) | 🟩🟩🟩🟩🟩 | Mariner-style prompt/response rendering |

### LLM clients

| Subsystem | Progress | Notes |
|---|---|---|
| Public AICore (Gemini Nano via `GenerativeModel`) | 🟩🟩🟩🟩🟩 | Pixel 10 Pro Fold proof passed via `LOCAL_LLM / LOCAL_ELIGIBLE`; public API still lacks explicit Nano v3 feature selection |
| Simulation LLM (deterministic test) | 🟩🟩🟩🟩🟩 | Test fixture |
| LiteRT-LM (on-device Gemma bundle) | 🟩🟩🟩⬜⬜ | Local fallback path; E2B model deploys to app storage, but `liblitertlm_jni.so` SIGILLs on arm64 emulator so physical-device proof is required |
| Remote inference (Oak session) | 🟨⬜⬜⬜⬜ | Post-launch fallback; endpoint + cert pin + audience config intentionally not provisioned for launch |
| Network-aware fallback selector | 🟩🟩🟩🟩🟩 | Local / remote / hybrid routing |

### Memory + state

| Subsystem | Progress | Notes |
|---|---|---|
| Per-source ephemeral buffers (23 qualifiers) | 🟩🟩🟩🟩🟩 | Timestamp-based eviction; max age tracks settings |
| Encrypted persistent context store | 🟩🟩🟩🟩🟩 | Room + SQLCipher; Keystore-wrapped passphrase |
| State probe (`CosmoStateSource`) | 🟩🟩🟩🟩🟩 | Cosmo-aligned 2-method interface |
| `CosmoStateManager` (snooze, low-battery, active hours) | 🟩🟩🟩🟩🟩 | Genesis-only convenience |
| Settings DataStore (60-field protobuf, encrypted) | 🟩🟩🟩🟩🟩 | `StateFlow<CosmoSetting>` surface |
| Semantic search over context store (Gecko embeddings) | ⬜⬜⬜⬜⬜ | Architectural hook exists (`queryByEmbedding`); model + impl deferred |

### Safety + observability

| Subsystem | Progress | Notes |
|---|---|---|
| Telemetry catalog (78 tags, 309 Firebase events) | 🟩🟩🟩🟩🟩 | Schema-backed; fail-closed on undocumented events |
| Runtime permission helper + rationale | 🟩🟩🟩🟩🟩 | DataStore-backed history; routes to settings for permanent denials |
| Battery-aware work scheduling | 🟩🟩🟩🟩🟩 | WorkManager with unmetered + battery-not-low + idle constraints |
| StrictMode + ANR watchdog (debug) | 🟩🟩🟩🟩🟩 | Debug-only diagnostics |
| Memory pressure trimming | 🟩🟩🟩🟩🟩 | Ephemeral buffers cleared under Android trim callbacks |
| **CaMeL trust layer (prompt-injection defense)** | 🟩⬜⬜⬜⬜ | Architecturally placed in `docs/architecture.md` §12; reference primitives mapped to Kotlin; no code yet |

### UI

| Subsystem | Progress | Notes |
|---|---|---|
| Nav3 with per-tab stacks + predictive back | 🟩🟩🟩🟩🟩 | `NavDisplay`; adaptive support-pane scene strategy for foldables/tablets |
| 25 Hilt-multibound debug panels | 🟩🟩🟩🟩🟩 | Live controller jobs, settings, LLM stats, gatekeeper output, buffer sizes, skill registry, manifest state, presence overlay |
| Route-state JSON persistence | 🟩🟩🟩🟩🟩 | TaskMonitor token round-trip, corrupted route rejection, tab-history restore |
| Presence overlay window | 🟩🟩🟩🟩⬜ | Orb/tab/card-stack surfaces hosted in `TYPE_APPLICATION_OVERLAY`; idle/specialty/action states, predictive back, and debug-panel controls; full result/projected-XR consumers remain |
| Glass / XR surface | 🟩🟩🟩🟩⬜ | Activity bound; capture consumer wiring partial |

### Voice output

| Subsystem | Progress | Notes |
|---|---|---|
| Sherpa Supertonic-3 `VoiceOutput` | 🟩🟩🟩🟩🟨 | Launch voice path; host Sherpa benchmark passes at aggregate RTF 0.178 vs Kitten 0.441; device smoke pending after the backend swap |
| Sherpa KittenTTS bundle | 🟩🟩🟩🟨⬜ | Retained as fallback/benchmark asset, no longer the launch voice path |
| Android `TextToSpeech` fallback | 🟩🟩🟩🟩🟩 | Emergency fallback behind the same `VoiceOutput` interface |
| AudioTrack streaming renderer | 🟩🟩🟩🟩🟨 | Sherpa callback streams PCM through `lib/tts/AudioRenderer`; real-device Supertonic listen pass remains |
| VoiceLM / SoundStream (generative voice) | ⬜⬜⬜⬜⬜ | Recovered to disk; not bundled; one asset truncated in recovery |
| Vogo native lib (`libvogo_jni.so`) | ⬜⬜⬜⬜⬜ | Recovered; not bundled |
| `lib/tts/*` recovered-compatible facade | 🟩🟩🟩🟨⬜ | `TTS`, `VoiceLMTTS`, `LocalTTS`, `AudioRenderer`, and `AudioResampler` surfaces exist; launch implementation delegates to `VoiceOutput`, exact Vogo task API remains research-only |

---

## Build

**Fast local dev loop** (recommended for everyday work):

```bash
scripts/dev-loop.sh                  # build → install → launch → stream logcat
scripts/dev-loop.sh --reinstall      # preserve app data on reinstall
scripts/dev-loop.sh --clean-install  # uninstall first
scripts/dev-loop.sh --mirror         # also launch scrcpy
scripts/dev-loop.sh --help           # all flags
```

Requires an attached device or running emulator (`adb devices` should list
one). Reads `ANDROID_SERIAL` for targeting if multiple are connected.

**First-time setup** — hydrate gitignored binaries from S3 + recovery:

```bash
scripts/download-models.sh              # gatekeeper, inherent, TTS (model-manifest → S3)
scripts/runtime-binaries.sh download    # SODA packs + JNI/libs only
scripts/verify-soda-assets.sh
# Optional 2.5GB LiteRT-LM (not in APK):
scripts/download-models.sh --only llm_gemma_4_e2b_litertlm
```

**Google Tensor SDK (Pixel EdgeTPU AOT)** — landed notebook + compiler in
`~/Downloads`: `LiteRT NPU AOT compilation for Google Tensor.ipynb`,
`litert_plugin_compiler.tar.gz` (`GOOGLE_TENSOR_SDK_BETA`). Compiles portable
`.tflite` (gatekeeper BERT, `inherent.tflite`) to per-SoC EdgeTPU artifacts.
Docs: [`docs/google-tensor-sdk.md`](docs/google-tensor-sdk.md),
[`~/inherent/docs/google-tensor-sdk.md`](../inherent/docs/google-tensor-sdk.md).
Latency vs STT→intent: [`~/inherent/README.md`](../inherent/README.md#latency-1-s-audio-order-of-magnitude).

`scripts/provision-from-recovery.sh` is a legacy fallback only.

**Standalone Gradle commands:**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

**Device smoke checks:**

```bash
scripts/run-device-core-capability-smoke.sh

# Optional: push a recorded phrase through the debug SODA audio path.
# WAV must be mono, 16 kHz, signed 16-bit PCM.
scripts/run-device-core-capability-smoke.sh /path/to/phrase.wav
```

**Gatekeeper → fulfillment golden test** (Cosmo-shaped path: head → intents → orchestrator queue → action deck):

```bash
# Fast loop: JVM only (~10s)
scripts/run-gatekeeper-golden.sh --unit-only

# Full loop: unit + Pixel/emulator when adb shows a device
scripts/run-gatekeeper-golden.sh

# All launch proofs (text golden, audio golden, inherent scorecard, release LLM)
scripts/run-launch-device-proofs.sh
scripts/run-launch-device-proofs.sh --skip-release-llm   # skip slow releaseInternal LLM step
```

Individual device proofs:

```bash
scripts/run-device-gatekeeper-golden-test.sh          # text → deck + presence
scripts/run-device-gatekeeper-audio-golden-test.sh    # WAV → SODA → deck
scripts/run-inherent-scorecard.sh                     # ambient head quality report
scripts/run-device-release-internal-llm-smoke.sh      # non-SimulationLLM on releaseInternal
```

Convert a phone recording before injection:

```bash
ffmpeg -i phrase.m4a -ac 1 -ar 16000 -sample_fmt s16 phrase.wav
```

**Dogfood LiteRT-LM fallback:**

```bash
scripts/download-models.sh --only llm_gemma_4_e2b_litertlm
DEPLOY_LITERTLM=1 scripts/run-device-model-routing-smoke.sh fallback
```

The bundle is listed in `model-manifest.json` (`llm_gemma_4_e2b_litertlm`) and
fetched from S3 like other models. Override with `LITERTLM_MODEL_ID=...` and
`LOCAL_MODEL=...` for E4B or other variants.

**Remote emu smoke:**

```bash
EMU_TOKEN=xxx ./scripts/emu-ci-run.sh
```

By default this uploads `app/build/outputs/apk/debug/app-debug.apk`, launches
`MainActivity`, runs for 90 seconds, and writes logcat plus `screen.mp4` under
`artifacts/emu-run/`. Each run also writes `summary.json` with the job URL and
artifact paths. Use `./scripts/emu-ci-run.sh --list-jobs` to inspect existing
jobs or `./scripts/emu-ci-run.sh --job-id <id>` to re-download artifacts without
rerunning the APK.

CI gates: `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug`.
The release path additionally runs `scripts/verify-phase6-release-gates.sh`
(strict model-manifest verification, architecture-delta check, signing config,
relocation safety).

## Docs

| Doc | Topic |
| --- | --- |
| [`PLAN.md`](PLAN.md) | What to work on next |
| [`docs/architecture.md`](docs/architecture.md) | System architecture + status |
| [`docs/google-tensor-sdk.md`](docs/google-tensor-sdk.md) | Pixel AOT notebook + Downloads toolchain |
| [`docs/architecture-delta.md`](docs/architecture-delta.md) | C1–C9 contracts, PR marching orders |
| [`docs/blocker-register.md`](docs/blocker-register.md) | Active blockers |
| [`reference/`](reference/) | Recovery stack maps + contracts |
| [`~/inherent/contracts/runtime_contract.md`](../inherent/contracts/runtime_contract.md) | Inherent ↔ app integration |

Also: `LAYOUT.md`, `docs/fallback-paths.md`, `docs/aicore-api-gap.md`,
`docs/release-signing-policy.md`. Recovery smali maps: `$COSMO_DEV_ROOT/repo/docs/*_stack_map.md`.

## Provenance

Models, JNI binaries, and SODA language packs come from the cosmo APK recovery
(`~/Downloads/cosmo_recovered_large_assets/`, `~/Downloads/cosmo_recovered_native_libs/`).
All recovered artifacts are SHA-256 verified at build time
(`scripts/download-models.sh --verify-only`, `scripts/verify-soda-assets.sh`).
Asset coverage and recovery completeness: `~/Downloads/cosmo_artifact_integrity/`.

The Kotlin app code is a clean-room reimplementation. The
`com.google.research.air.cosmo` package namespace mirrors cosmo's recovered
bytecode to keep the architecture-delta citations readable; class-level
behavior is reauthored from the smali reference.
