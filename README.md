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
| Ambient microphone | 🟩🟩🟩🟩🟩 | Per-source `SodaSession`; recovered SODA blend; transcript-first `AMBIENT_CONTINUOUS` |
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
| AOC nanoapp audio transport | 🟨🟨⬜⬜⬜ | Routing + Context Hub binding live; callback subclass needs platform-signed compile or bytecode-gen |

### On-device speech (SODA)

| Subsystem | Progress | Notes |
|---|---|---|
| End-to-end ASR (transcripts) | 🟩🟩🟩🟩🟩 | Runs inside `libsoda_dev_jni.so`; 3 SoC language packs bundled; emulator fixture corpus routes 10/10 speech phrases through SODA → regex skills |
| Magic Mic / IntendedSpeech gate | 🟨⬜⬜⬜⬜ | Not used as launch gate; recovered cosmo SODA stays transcript-first with `AMBIENT_CONTINUOUS` |
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
| Conformer EdgeTPU audio backend | 🟩🟩🟩🟩⬜ | Code path bundled; activation gated on Pixel + Darwinn proof; emulator raw-audio debug path fails closed instead of SIGILL |
| Inherent (joint audio→intent) backend | 🟩🟩🟩⬜⬜ | Wired; v0.1 LiteRT artifact now matches Android tensor contract; quality + real-device proof pending |
| MediaTek Dimensity (mt6989) BERT variant | 🟩⬜⬜⬜⬜ | Manifest slot reserved; AOT pipeline drafted; model not built yet |

### Intent + fulfillment

| Subsystem | Progress | Notes |
|---|---|---|
| LLM-backed intent extractor (second `Gatekeeper.output` subscriber) | 🟩🟩🟩🟩🟩 | Cosmo `{reasoning, final_answer}` parse path; context-gather from recent transcripts + screen interactions |
| Skill catalog (22 action types from `filtered_skill_schema.textproto`) | 🟩🟩🟩🟩🟩 | Schema-driven, required args + trigger regexes + permission gates |
| Fulfillment runner + confirmation policy | 🟩🟩🟩🟩⬜ | Functional end-to-end; single action handler awaiting per-subtype split |
| Browsing agent surface (TaskMonitor) | 🟩🟩🟩🟩🟩 | Mariner-style prompt/response rendering |

### LLM clients

| Subsystem | Progress | Notes |
|---|---|---|
| Public AICore (Gemini Nano via `GenerativeModel`) | 🟩🟩🟩🟩🟩 | Pixel/Nano happy path; Pixel 10 device proof pending |
| Simulation LLM (deterministic test) | 🟩🟩🟩🟩🟩 | Test fixture |
| LiteRT-LM (on-device Gemma bundle) | 🟩🟩🟩⬜⬜ | Local fallback path; E2B model deploys to app storage, but `liblitertlm_jni.so` SIGILLs on arm64 emulator so physical-device proof is required |
| Remote inference (Oak session) | 🟨⬜⬜⬜⬜ | Post-launch fallback; endpoint + cert pin + audience config intentionally not provisioned for launch |
| Network-aware fallback selector | 🟩🟩🟩🟩🟩 | Local / remote / hybrid routing |

### Memory + state

| Subsystem | Progress | Notes |
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
| Presence overlay window | 🟩🟩🟩⬜⬜ | Orb/tab/card-stack surfaces hosted in `TYPE_APPLICATION_OVERLAY`; permission-gated with unit coverage and debug-panel controls |
| Glass / XR surface | 🟩🟩🟩🟩⬜ | Activity bound; capture consumer wiring partial |

### Voice output (intentionally deferred)

| Subsystem | Progress | Notes |
|---|---|---|
| VoiceLM / SoundStream (generative voice) | ⬜⬜⬜⬜⬜ | Recovered to disk; not bundled; one asset truncated in recovery |
| Vogo native lib (`libvogo_jni.so`) | ⬜⬜⬜⬜⬜ | Recovered; not bundled |
| `lib/tts/*` (TTS interface, AudioRenderer/Resampler) | ⬜⬜⬜⬜⬜ | Not ported. Architectural slot reserved |

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

**First-time setup on a new machine** — provision the gitignored recovered
binaries (TFLites, SODA packs, JNI .so) from `~/Downloads/cosmo_recovered_*`:

```bash
scripts/provision-from-recovery.sh   # copies binaries into the assets/jniLibs tree
```

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

Convert a phone recording before injection:

```bash
ffmpeg -i phrase.m4a -ac 1 -ar 16000 -sample_fmt s16 phrase.wav
```

**Dogfood LiteRT-LM fallback:**

```bash
scripts/provision-local-litertlm.sh
DEPLOY_LITERTLM=1 scripts/run-device-model-routing-smoke.sh fallback
```

The launch fallback default is the app-private
`google-gemma-4-E2B-it-litertlm` bundle. It is not checked into git; the
provision script copies the local `.litertlm` file into the hashed storage path
that `LiteRtLmLLM` and the deploy script both use. Test E4B or future bundles
by passing `LITERTLM_MODEL_ID=...` and `LOCAL_MODEL=...`.

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

- [`PLAN.md`](PLAN.md) — short operating plan. Read this first for what to work on next.
- [`LAYOUT.md`](LAYOUT.md) — where everything lives (canonical inside `~/neural`, what's outside and why, consolidation status)
- [`docs/architecture.md`](docs/architecture.md) — system architecture with status per component, including Presence UX layer (§13), AppFunctions surface (§14), server-side workstreams (§15), CaMeL trust-layer placement (§12), and recovered-but-unwired surface inventory (§17)
- [`docs/architecture-delta.md`](docs/architecture-delta.md) — C1–C9 contract rules + PR0–PR6 marching orders
- [`docs/camel-port-notes.md`](docs/camel-port-notes.md) — per-AST-node CaMeL port-fidelity reference (read when the implementing workstream picks up §12)
- [`docs/fallback-paths.md`](docs/fallback-paths.md) — 3-tier fallback for each deferred subsystem (cosmo-recovered → commercial/OSS → build-it), with effort estimates + triage
- [`reference/`](reference/) — read-only mirrors of cosmo-recovery stack maps + workstream interface contracts + inherent contracts
- [`docs/blocker-register.md`](docs/blocker-register.md) — active blockers (oldest first)
- [`docs/aicore-api-gap.md`](docs/aicore-api-gap.md) — public AICore feature-selection limits
- [`docs/remote-inference-contract.md`](docs/remote-inference-contract.md) — issuer + inference HTTP contract
- [`docs/release-signing-policy.md`](docs/release-signing-policy.md) — production signing inputs

External:

- Recovery-level cosmo internals (smali / proto fields / asset bytes): `$COSMO_DEV_ROOT/repo/docs/*_stack_map.md`
- Inherent (joint audio→intent training pipeline): `~/inherent/contracts/runtime_contract.md`
- CaMeL paper: [arxiv 2503.18813](https://arxiv.org/abs/2503.18813) — reference impl: [google-research/camel-prompt-injection](https://github.com/google-research/camel-prompt-injection)

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
