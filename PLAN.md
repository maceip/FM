# FM Engineering Plan

This is the master engineering plan. It consolidates planning, design, and
status content previously scattered across
`~/cosmo-dev/repo/docs/cosmo_apk_consolidated_plan.md`, `marching-orders.md`,
`parallel-offload-plan.md`, `implementation-status.md`,
`blockers-and-mitigations.md`, `llm-model-delivery-architecture-review.md`,
`security-threat-model.md`, `WS4/docs/ws4-demo.md`, and `ws4-known-gaps.md`.

Read alongside these FM-owned canonical docs:

| Need | Doc |
|---|---|
| Capability matrix + quick start | [`README.md`](README.md) |
| Where files live + consolidation map | [`LAYOUT.md`](LAYOUT.md) |
| Subsystem-by-subsystem system view | [`docs/architecture.md`](docs/architecture.md) |
| C1–C9 contract rules + PR0–PR6 marching orders | [`docs/architecture-delta.md`](docs/architecture-delta.md) |
| Per-AST-node CaMeL port reference | [`docs/camel-port-notes.md`](docs/camel-port-notes.md) |
| 3-tier fallback per deferred subsystem | [`docs/fallback-paths.md`](docs/fallback-paths.md) |
| Cosmo recovery stack maps (mirrored) | [`reference/`](reference/) |

Status legend: ✅ **live** / ⚠️ **partial** / 🟡 **deferred** / 🔵 **planned** / 🔴 **absent**.

---

## 1. Goal

Build a standalone Android assistant whose user-visible behavior matches
the observed cosmo app — **defined by outcomes, not internal architecture**.

```
- A user can ask a question by text, voice, or camera and get a useful answer.
- The app remembers prior context (audio/screen/notification) when permitted,
  and can recall it.
- The user can switch between Local, Cloud, and Hybrid fulfillment and feel
  the difference.
- The user can run a defined set of skills end-to-end (list, timer, calendar
  draft, doc draft, web search, jargon, insight, recall, summary).
- The user can disable any source or skill and the app respects it in code,
  not just UI.
```

**The cosmo APK recovery is reference material only.** It informs product
requirements, vocabulary, and parity targets. It is not a workstream, not
a dependency, and not something engineers spend time recovering or repairing.

**Parity is pinned to a captured reference capability set** (screenshot bundle
+ capability list in `~/Downloads/cosmo_artifact_integrity/`). If a capability
isn't in that set, it's out of scope. This kills "is X parity?" debates.

---

## 2. Milestones (outcome-driven)

The project ships in four demo-able milestones. Each milestone is a working
APK on a real device. No milestone is "done" until the demo runs from a clean
install.

### M1 — Thin Vertical Slice ✅

**Outcome:** open the app, type a question, get a cloud-model answer back,
see one skill (timer) execute.

```
WS1: Home screen + Ask input + minimal Settings (Cloud-only). Compose scaffolding chosen.
WS2: Context event schema landed. No capture yet. In-memory store stub.
WS3: Cloud LLM adapter only. Hardcoded route. Latency + path logging from day one.
WS4: Skill registry skeleton. Timer skill end-to-end. Demo script v1.
```

**Gate:** clean install → ask → answer → set timer. Nothing else required.

### M2 — Context + Recall ✅

**Outcome:** the app captures notifications and screen text (with permission),
the user can ask "what was that thing about X?" and get a cited recall.

```
WS1: Permission flows (mic, notifications, accessibility). Ambient Awareness
     screen with source toggles + buffer duration. Settings persistence.
WS2: Notification listener + accessibility capture. Rolling buffers. Local
     memory store (Room + SQLite + FTS5 over SQLCipher). Retention enforcement.
     Source toggles wired so disabled sources never reach storage.
WS3: Embedding model selected — Gecko (cosmo-recovered) primary;
     sentence-transformers MiniLM tier-2 (per docs/fallback-paths.md §Gecko).
     Recall over WS2 memory.
WS4: Recall skill + conversation summary skill. Confirmation pattern for any
     side-effect action.
```

**Gate:** disable a source in UI, generate events from it, assert via
instrumented test that zero events reach the store. Recall returns cited
results.

### M3 — Local + Hybrid Fulfillment ⚠️

**Outcome:** flip Fulfillment Model to Local; the app answers without
network. Flip to Hybrid; routing chooses sensibly and logs the choice.

```
WS1: Fulfillment Model screen (Hybrid/Cloud/Local). Model status + download
     UX. Skills discovery screen.
WS2: Mic + camera adapters behind interfaces. Audio frame buffer.
WS3: On-device ASR shipped — SODA (cosmo-recovered) live; tier-2 Whisper-tiny
     fallback per fallback-paths.md.
     On-device LLM shipped — AICore (Gemini Nano) first; LiteRT-LM (Gemma 4)
     second; remote inference (Aratea via PrivateToken) third.
     Intent/actionability classifier — Gatekeeper (BERT + regex fusion +
     C9 selector) live.
     Hybrid routing policy with explicit fallback rules + logged decision.
WS4: List, calendar draft, document draft, jargon, insight skills. Audit
     trail for actions.
```

**Gate:** airplane mode → ask → Local path answers. Hybrid path correctly
downgrades to Local on network loss and logs it.

**Current state:** On-device ASR + Gatekeeper + AICore live ✅. LiteRT-LM
provisioning ⚠️ (needs Gemma 4 bundle). Remote inference structural ⚠️
(no live endpoint — see §6.3). Local-only mode works for emulator + cloud
LLM via remote.

### M4 — Parity Demo + Hardening 🔵

**Outcome:** every screen in the parity set is present; every privacy toggle
is enforced; the demo flow runs unattended.

```
WS1: Final visual + accessibility pass. All Settings persist and round-trip
     across kill.
WS2: Privacy enforcement tests. Retention sweep verified.
WS3: Latency/regression budgets met for each path. Path-mix telemetry
     usable.
WS4: All nine skills working. Integration test suite green. Demo script +
     README + known-gaps doc.
```

**Gate:** clean-install demo runs end to end without engineer intervention.
Privacy test suite green.

---

## 3. Workstream structure

Four workstreams. One engineer each. A rotating **Release Captain** (1 week,
round-robin) owns the green build, demo APK, and bug triage that week. This
prevents WS4 from silently absorbing all QA.

| Workstream | Focus |
|---|---|
| **WS1** | Android app shell, UX, settings, permissions, Nav3, Presence layer (orb/tab/card surfaces — see `docs/architecture.md` §13) |
| **WS2** | Capture, context buffers, memory, recall, SODA integration, ContextStore |
| **WS3** | Model pipeline, routing, local/cloud fulfillment, LLM clients (AICore, LiteRT-LM, remote inference), gatekeeping backends |
| **WS4** | Skills, actions, integration, demo quality, skill registry |

### Cross-stream integration cadence

```
- Daily: 10-min standup, blocker-only.
- Weekly: 30-min integration sync — every WS demos current behavior on the shared APK.
- Per milestone: hard freeze 1 day before gate; Release Captain cuts the demo APK.
- Interface changes require a PR labeled "interface" and a thumbs-up from the consuming WS owner.
```

---

## 4. Interface contracts

These are the seams that make four parallel streams viable. Each is owned by
the producing WS and reviewed by all consumers. Specs live at
[`reference/contracts/`](reference/contracts/) (mirrored from
`~/cosmo-dev/repo/docs/contracts/`).

| Contract | Owner | Consumers | File |
|---|---|---|---|
| `ContextStore` API (insert, query-by-time, query-by-embedding, delete, source filter) | WS2 | All | `reference/contracts/01_context_store_api.md` |
| `Fulfillment` API (streaming request → answer with mode + path metadata) | WS3 | WS1, WS4 | `reference/contracts/02_fulfillment_api.md` |
| `SkillRegistry` API (list, enable/disable, invoke, confirm, audit) | WS4 | WS1 | `reference/contracts/03_skill_registry_api.md` |
| Permission + settings observer (current source toggles, fulfillment mode) | WS1 | All | (in-app `CosmoSettingStore` + `CosmoStateSource`) |

If a contract isn't written by end of Week 1 of any milestone slip, the
milestone slips. Treat contract definition as the highest-priority work,
ahead of any feature code.

---

## 5. Current snapshot (2026-05-15)

For per-component status, see [`docs/architecture.md`](docs/architecture.md)
**§19 Snapshot summary** — it's regenerated more often than this doc.

**Headline:**

- **M1 + M2 complete.** Capture, ContextStore, Gatekeeper, Intent extraction, Fulfillment, Skill registry all live.
- **M3 mostly complete.** All routing paths shipped; one of the three lanes (remote inference) needs a live endpoint; one (LiteRT-LM) needs Gemma 4 bundle provisioning.
- **M4 not started.** Privacy enforcement tests partially written; final visual pass not done; release-readiness gates pass except for the unrelated `Color.kt` MatchingDeclarationName detekt issue.

**In-flight work (as of 2026-05-15):**

| Item | Owner | Status |
|---|---|---|
| Voice Match enrollment UI (highest-leverage 1-day win) | WS1 + WS4 | 🔵 not started — assets ready, UI missing |
| CaMeL trust layer (Plan DSL variant) | (workstream not assigned) | 🔵 placement done in architecture.md §12; ~2 engineer-weeks per camel-port-notes.md |
| Pixel device validation (Conformer EdgeTPU activation, AOC nanoapp transport) | WS3 | ⚠️ pending Pixel + platform-signed compile |
| Inherent model (`~/inherent` joint audio→intent) | Separate project | ⚠️ in progress — 5–6 week training plan |
| LiteRT-LM Gemma 4 bundle provisioning | WS3 | ⚠️ `scripts/provision-gemma4-litertlm.sh` exists; bundle not deployed |
| Remote inference endpoint + token issuer | WS3 + ops | 🔵 architecture.md §15.4/§15.6 — server side not built |
| Mariner browsing-agent backend (tier-2 Browser-Use) | WS3 | 🔵 architecture.md §15.7 — UI live, backend not built |
| Presence state driver (GatekeeperOutput → PresenceState producer) | WS1 | ⚠️ surfaces render, producer missing |

---

## 6. Active blockers

External blockers cannot be solved in code. Mitigations keep us moving.

### 6.1 Pixel hardware (Conformer EdgeTPU activation, AOC nanoapp transport)

**Blocker:** the Conformer audio-gatekeeper backend
(`audio_gatekeeper_edgetpu.tflite` + `libdarwinndelegatejni.so`) requires
a Pixel device to validate the Darwinn delegate loads cleanly. AOC nanoapp
audio requires either a platform-signed compile or bytecode-generation for
the `@SystemApi ContextHubClientCallback` subclass; both are gated on a
Pixel target.

**Mitigation:**
- `ConformerEdgeTpuBackend.isAvailable=false` — CPU recovery path (Magic
  Mic + BERT) is the production path on all non-Pixel devices, fully wired
  per [architecture-delta.md §C1](docs/architecture-delta.md).
- `RecordServiceManager.startAocRecording` returns false → fallback to
  mic via `AudioRecord` is automatic.
- Inherent backend (when shipped) provides a CPU joint-model path that
  doesn't require EdgeTPU.

**When the Pixel arrives:** flip `ConformerEdgeTpuBackend.computeIsAvailable()`
from `return false` to `Build.SOC_MANUFACTURER == "google" && darwinnDelegateLoadsCleanly()`.
~1 day of validation work.

### 6.2 Real Private Aratea inference endpoint + token issuer

**Blocker:** no live `/v1/inference` or `/v1/private-token/issue` server.
Cannot do end-to-end remote inference validation.

**Mitigation:**
- `SimulationLLM` returns realistic canned JSON for the 9 parity skills.
  Bypasses the entire remote path; the full fulfillment loop is testable
  on emulator + CI without a server.
- `RemoteInferenceLLM` throws clear `LlmUnavailableException` with
  `LlmFailureCause.CONFIGURATION` or `AUTH` so downstream code degrades
  cleanly to the next routing lane.
- Client transport is fully wired (signing, pinning, PrivateToken auth,
  SSE parsing) — see [architecture.md §15.4–§15.6](docs/architecture.md).

**When the server contract is delivered:**
- Set `cosmo.remoteInference.baseUrl`, `cosmo.remoteInference.certPin`,
  `cosmo.remoteInference.audience` in Gradle properties.
- The client is already written to handle SSE streaming, JSON fallback,
  certificate pinning, retries, and proper error classification.
- Smallest server to stand up:
  [architecture.md §15.6](docs/architecture.md) describes the issuer
  service as ~150 LOC of FastAPI.

### 6.3 MediaTek mt6989 BERT AOT toolchain

**Blocker:** the BERT classifier compiles for Tensor G4/G5 (Pixel 9/10) but
not for the MediaTek Dimensity 9300 used in Xiaomi 15T Pro and others.

**Mitigation:**
- `model-manifest.json` reserves the `bert_v0_mt6989.tflite` slot with a
  placeholder hash; runtime falls through to `bert_v0_cpu.tflite` when the
  variant is absent.
- AOT compile pipeline drafted in [`reference/cosmo-bert-mt6989-aot-pipeline.md`](reference/cosmo-bert-mt6989-aot-pipeline.md);
  needs to be run on a machine with the MediaTek Neuron toolchain.

**When the AOT compile runs:** drop the produced `bert_v0_mt6989.tflite`
into `app/src/main/assets/models/`, update the manifest hash + size; the
backend selector picks it automatically.

### 6.4 Release APK size + posture

**Observation:** debug APK currently ~82 MB (with bundled SODA packs +
BERT variants); release APK ~16 MB after R8. Both healthy.

**Mitigation:** none needed; R8 + baseline profiles in Phase 5 keep this in
range. Verify each milestone with `./gradlew assembleRelease -P` and check
APK analyzer output.

---

## 7. Security threat model

STRIDE over components that hold private context, cross process boundaries,
or execute side effects. High and medium risks are listed with concrete
mitigations and code owners.

| Component | Risk | Severity | Mitigation | Code Path |
|---|---|---|---|---|
| Runtime permissions | User permanently denies a dangerous permission and the app silently keeps advertising the feature | High | Permission request history in DataStore; UI distinguishes first-ask, rationale, granted, permanently-denied states; permanent denial routes to Android app settings | `lib/permission/PermissionRationaleHelper.kt`, `ui/mainscreen/MainActivity.kt` |
| Capture settings | Private source toggles are shown in UI but disabled sources still reach storage or prompts | High | `RoomContextStore.insert` rejects disabled sources before writing; prompt construction reads from bounded ephemeral buffers only | `context/ContextStore.kt`, `fulfillment/Fulfillment.kt` |
| Context database | Screen text, notifications, location, and recall content are extracted from a rooted device backup or file pull | High | Room opens through SQLCipher with a 256-bit passphrase stored AES-GCM-encrypted in `noBackupFilesDir` under an Android Keystore key | `security/SecureStorage.kt`, `context/ContextStore.kt` |
| Settings DataStore | Privacy toggles read/written from plaintext local storage | Medium | Proto DataStore serializer encrypts payloads with Android Keystore AES-GCM; plaintext settings are read once for migration and rewritten encrypted | `security/SecureStorage.kt`, `setting/Settings.kt` |
| Remote inference | Token or request replay against the inference endpoint | High | HTTPS mandatory except loopback; cert pinning required for HTTPS; every request signed with Keystore ECDSA key and short-lived PrivateToken | `lib/llm/RemoteInferenceClient.kt`, `lib/llm/RequestSigner.kt`, `lib/llm/RemoteCredentialStore.kt` |
| Remote inference | PII leaks through logs, analytics, or failed network responses | High | Request bodies and authorization headers are not logged; telemetry stores route + counts, not prompt text; credentials encrypted at rest | `logging/Logging.kt`, `lib/llm/*` |
| LLM output | Malicious or malformed JSON triggers an unintended side-effect action | High | Unknown action types parse to `Unsupported`; side-effect handlers require explicit subtype handling and permission checks before execution | `fulfillment/Fulfillment.kt`, `skills/Skills.kt` |
| Prompt injection (notifications, screen captures, browsing-agent output flowing into LLM context) | **High** | High | **Architecturally placed (CaMeL trust layer — see [`docs/architecture.md §12`](docs/architecture.md))**. Not yet implemented; current threat model is defense-in-depth via input sanitization and bounded ephemeral buffers | (planned) `trust/`, `lib/llm/PrivilegedLlmService.kt`, `lib/llm/QuarantinedLlmService.kt` |
| Calendar and call actions | Side-effect action runs without runtime permission or user confirmation surface | High | Skill descriptors mark side-effects as confirmation-required; calendar writes and calls check dangerous permissions before launching or inserting | `skills/Skills.kt`, `fulfillment/Fulfillment.kt` |
| WorkManager batch processing | Batch LLM work drains battery or runs on metered networks | Medium | Unique periodic work requires unmetered network, battery-not-low, and idle constraints; worker rechecks battery capacity and retries at or below 20% | `batch/BatteryAwareBatchWorker.kt` |
| Battery snooze | Continuous capture runs on critically low battery | Medium | `BatteryStateReceiver` feeds `CosmoStateManager`; auto-snooze enabled by default; snooze for 2h below 15% | `lib/battery/`, `setting/Settings.kt` |
| Memory pressure | Raw media buffers survive backgrounding or critical memory pressure | Medium | `Application.onTrimMemory` clears byte buffers under low/critical pressure; clears writable ephemeral buffers when backgrounded | `CosmoApp.kt`, `lib/performance/MemoryPressureTrimmer.kt` |
| Main thread stalls | Capture or fulfillment blocks UI without developer visibility | Medium | Debug builds install StrictMode + a main-thread watchdog that logs stack snapshots after 5s of unresponsiveness | `lib/performance/DebugDiagnostics.kt` |
| Exported components | Another app invokes internal surfaces | High | Non-launcher internal activity is no longer exported; exported services either use platform bind permissions or the app's signature permission; cleartext traffic disabled | `AndroidManifest.xml` |
| Overlay permission | App obtains broad overlay capability without an implemented surface | Medium | `SYSTEM_ALERT_WINDOW` removed because no overlay window path exists | `AndroidManifest.xml` |
| Release shrinking | R8 strips Hilt, WorkManager, Room, protobuf, SQLCipher, or analytics classes | Medium | Release build uses full-mode R8 + explicit keep rules for generated/runtime-reflected surfaces | `app/proguard-rules.pro`, `app/build.gradle.kts` |

**Review cadence:** re-run this table at the end of every milestone and after
any new captor, model path, exported component, or side-effect action lands.

---

## 8. LLM / model delivery architecture

Three independent LLM lanes behind the same `LLM` contract:

| Lane | Provider | Path |
|---|---|---|
| **Local outsourced** | AICore first; ML Kit GenAI Prompt second | `lib/llm/AICoreClientWrapper.kt`, `lib/llm/MlKitPromptLLM.kt` |
| **Local app-managed** | LiteRT-LM bundle (Gemma 4 E2B or E4B) | `lib/llm/LiteRtLmLLM.kt` |
| **Remote** | Signed HTTPS calls (cert pinned, PrivateToken auth, SSE/JSON) | `lib/llm/RemoteInferenceClient.kt` |

Router is local-first for privacy and offline behavior. Every lane fails
with a typed `LlmUnavailableException` so the next eligible lane runs.
**No lane returns synthetic content in production** (the `SimulationLLM`
fake is qualifier-isolated to debug/test).

### Interface hygiene guardrails

- Keep `LLM` as the only application-facing generation interface.
- Keep `@LocalLLM`, `@ServerLLM`, `@HybridLLM` as the only app-level routing identities.
- Treat AICore, ML Kit, LiteRT-LM, and remote HTTP as **leaf implementations**. They may have private setup/config interfaces, but they must not become new app-wide routes.
- Capability mismatches must fail/skip explicitly. A provider must not convert unsupported audio/image inputs into descriptive text just to make the call fit.
- Model delivery must resolve verified files only; it must not influence routing beyond making the LiteRT-LM leaf available or unavailable.
- Remote credentials and pins are scoped transport details; they must not leak into hybrid routing or local model selection.

### Model artifact delivery

Two tiers per the architecture-delta:

- **Bundled** (BERT variants, SODA language packs, audio gatekeeper): part of the APK, SHA-256 verified at runtime by `app/src/main/assets/model-manifest.json`. Local-only; gitignored in the repo (provisioned from recovery via `scripts/provision-from-recovery.sh`).
- **Runtime download** (LiteRT-LM Gemma 4 bundle, future Inherent model): HTTPS download with checksum/cache, `.part` atomic rename. Storage path derived from model id via `scripts/model-storage-id.py` (Kotlin and Python agree on the storage key).

### Remaining production gates

- Validate the final remote endpoint contract against a live server: request shape, PrivateToken issuer payload, SSE format, certificate pin.
- Validate AICore, ML Kit GenAI Prompt, and LiteRT-LM on **Xiaomi 15T Pro hardware** (mt6989 target) with the same prompts and telemetry route labels.
- Decide whether SODA, gatekeeper TFLite, and LiteRT-LM should converge on one runtime artifact manager. Current state has the gatekeeper manifest, SODA pack manifest, and LiteRT-LM bundle as separate flows. Convergence is post-M4.

---

## 9. Build / test / ship gates

### Per-PR gates

```bash
./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Plus the architecture-delta enforcement:

```bash
scripts/verify-architecture-delta.sh   # docs/architecture-delta.md is current
scripts/verify-relocation-safety.sh    # no cross-machine path hardcoding
```

### Per-milestone gates

- Clean install of debug APK on a real device launches without crash.
- Demo script (§10) runs end-to-end unattended.
- Privacy enforcement tests (see below) pass.

### Release gates

```bash
scripts/verify-phase6-release-gates.sh
```

Runs:
- Strict `scripts/download-models.sh --verify-only` (no `--allow-empty` flag — manifest must list models)
- `scripts/verify-architecture-delta.sh`
- `scripts/verify-relocation-safety.sh`
- CI manifest + static-analysis checks
- Signing-config Gradle checks
- Regression check that LiteRT-LM delivery doesn't hardcode an E4B-only path

### Definition of done (project-level)

```
- The Android app builds and installs from our own repo.
- Every screen in the pinned parity set is present.
- Context capture, memory, model routing, and skills work together.
- Hybrid, Local, and Cloud modes all demonstrable on one device.
- The app does not depend on the corrupted APK.
- The app does not depend on Google's private model weights.
- Privacy controls are enforced in code and proven by tests, not just shown in UI.
- There is a repeatable demo script + test checklist + known-gaps doc.
- Target-SDK + permissions posture passes a Play Store policy self-check
  (accessibility, foreground service, notification listener rationale).
```

---

## 10. Demo script

Pre-flight:

```
- Wipe app data: Settings → Apps → FM → Storage → Clear, OR `adb uninstall <pkg>`.
- Install fresh: scripts/dev-loop.sh --clean-install
- Launch app. Confirm Home screen renders without crash.
```

### Flow 1 — Capture + recall

1. Grant mic + notifications + accessibility permissions.
2. Speak: *"Remind me about the dentist appointment on Thursday."*
3. Toggle Settings → Ambient Awareness → Notifications: ON.
4. Receive a notification. Wait 30s.
5. Ask: *"What was that thing about Thursday?"*
6. Expect: recall surfaces both the spoken utterance and the notification with citation timestamps.

### Flow 2 — Skills surface is registry-driven

1. Tap "Skills" in the bottom nav.
2. Verify the parity set of skills renders (List, Timer, Calendar Draft, Doc Writer, Web Search, Jargon, Insight, Recall, Conversation Summary).
3. Verify the category chips: All, Productivity, Capture, Knowledge, Utility.
4. Toggle a skill off → toggle reflects state. Toggle back on.

### Flow 3 — Side-effect confirmation

1. Ask: *"Set a timer for 10 minutes."*
2. Expect: confirmation prompt before the timer fires (skill descriptor marks it as `SIDE_EFFECT`).
3. Confirm. Verify the timer is set (system clock app shows the timer).
4. Audit trail surfaces the event in the debug panel.

### Flow 4 — Permission denial path (test-driven)

```bash
./gradlew :app:testDebugUnitTest --tests "*IntegrationTest.permissionDenialPath"
```

Expect green.

### Flow 5 — Local-only mode (airplane mode)

1. Toggle Settings → Fulfillment Model → Local.
2. Enable airplane mode.
3. Ask: *"What is photosynthesis?"*
4. Expect: AICore (Gemini Nano) responds without network access.
5. Verify: no network egress occurs during the session (test interceptor).

### Flow 6 — Hybrid mode downgrade

1. Toggle Settings → Fulfillment Model → Hybrid.
2. Disable network mid-query.
3. Expect: routing log shows graceful downgrade to Local; user-visible answer arrives from local model with no failure indicator.

---

## 11. Privacy enforcement (must-pass tests)

Privacy claims that aren't tested are marketing. The following tests **must
be green** by M4:

```
- Disabled-source test: with source X off, generate N events from X, assert
  0 reach the store and 0 reach any prompt sent to a model.
- Retention test: events older than configured retention are removed by
  the next sweep.
- Local-only test: with Fulfillment = Local, no network egress occurs during
  a scripted session (verified via test interceptor).
- Forget-window test: "forget last 10 min" removes matching events from
  store + any in-flight buffers.
```

WS2 owns sources 1, 2, 4. WS3 owns source 3. WS1 owns the UI surface for all four.

---

## 12. Reference-only material (what NOT to use the APK recovery for)

Use the prior APK work only to inform product requirements:

```
- Observed capabilities.
- Likely model roles.
- Subsystem vocabulary.
- Screenshot parity (pinned set).
- Known non-goals.
```

Do **not** use it for:

```
- Active engineering assignments.
- APK repair.
- Model recovery beyond the assets that scripts/provision-from-recovery.sh
  already populates.
- Runtime dependencies.
- Ship-blocking tasks.
```

Recovered cosmo binaries (TFLites, SODA packs, JNI .so files) are
intentionally sourced from `~/Downloads/cosmo_recovered_*` (read-only,
outside the repo). They land in the APK via
`scripts/provision-from-recovery.sh` and are SHA-256 verified by
`scripts/download-models.sh --verify-only` against `model-manifest.json`.

---

## 13. Out of scope

| Surface | Why out of scope |
|---|---|
| **Voice generation / TTS** (VoiceLM, Vogo, SoundStream) | Deferred per `docs/architecture.md §16`. Tier-2 fallback is `android.speech.tts.TextToSpeech` if a user-facing TTS need lands |
| **Workspace grounding** (server-side Gmail/Calendar/Drive fetch) | Replaced with on-device Android content providers; user owns the read path |
| **PI (Personal Intelligence) service** | Replaced with on-device `ContextStore` |
| **Server-side prompt-injection classifier** | Replaced with on-device CaMeL trust layer (`docs/architecture.md §12`) |
| **Federated learning aggregation** | Not on the parity capability set |
| **Multi-device fan-out** (assistant state mirrored across devices) | Not on parity; future workstream |
| **AgentQ-style ask-back service** | Google-internal name; generic LLM via `lib/llm/` replaces |

---

## 14. Cross-reference index

If you need... | Read
--- | ---
The current system architecture, subsystem-by-subsystem | `docs/architecture.md`
The C1–C9 contract rules + PR0–PR6 marching orders | `docs/architecture-delta.md`
3-tier fallback per deferred subsystem | `docs/fallback-paths.md`
Per-AST-node CaMeL port reference | `docs/camel-port-notes.md`
Active blockers (live, refreshable) | `docs/blocker-register.md`
Capability matrix + quick start | `README.md`
Where files live + consolidation map | `LAYOUT.md`
Cosmo recovery stack maps (mirrored) | `reference/cosmo-*.md`
Workstream contracts (ContextStore, Fulfillment, SkillRegistry) | `reference/contracts/`
Inherent runtime tensor contract | `reference/inherent-runtime-contract.md`
Inherent audio corpus collection | `reference/inherent-audio-collection.md`
mt6989 BERT AOT compile pipeline | `reference/cosmo-bert-mt6989-aot-pipeline.md`
Remote inference HTTP contract | `docs/remote-inference-contract.md`
AICore public-API surface limits | `docs/aicore-api-gap.md`
Release signing policy | `docs/release-signing-policy.md`
Audio training corpus plan | `docs/audio-corpus-plan.md`

---

## 15. Update protocol

This doc is the canonical engineering plan. Updates land here when:

- A milestone gate is met (update §2)
- A workstream owner changes or a Release Captain rotates (update §3)
- A new blocker arises or an old one is resolved (update §6)
- A new security risk is identified or mitigation lands (update §7)
- An LLM lane is added/removed (update §8)
- A new must-pass privacy test is identified (update §11)
- The out-of-scope list changes (update §13)

Per-component status snapshots live in `docs/architecture.md §19` and are
refreshed more frequently. Per-PR engineering progress lives in commit
messages and the `architecture-delta.md` PR0–PR6 status lines.
