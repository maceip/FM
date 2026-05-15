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
(no live endpoint — see §7.2). Local-only mode works for emulator + cloud
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

## 3. Ship Checklist — force-ranked

This is the operational answer to "what must work before we can ask someone to
use FM?" It consolidates the per-tier work items from M1–M4 (§2), in-flight
state (§6), the cosmo-recovery work surface (`docs/architecture.md`), and the
fallback paths (`docs/fallback-paths.md`).

Tiers are blocking: **Tier N cannot start its acceptance gate until Tier N-1
items are green.** Lower-tier items have implementation slack within a tier
but the gate is collective.

### 3.0 Validation convention (read first)

**Every item below is "done" only when it passes the emu gate.**

The emu gate today: `scripts/emu-ci-run.sh <APK> <ACTIVITY> <RUN_SECONDS>`
against the shared `emu.devkeys.net` service. The script:

1. Posts the APK to the emu service.
2. Installs + launches the named activity inside the remote emulator.
3. Captures logcat + screen recording for the requested run window.
4. **Fails if logcat matches `FATAL|ANR|CRASH|Exception|test.*fail|AssertionError`** within the window.
5. Downloads the recording + logs to `artifacts/emu-run/job-<id>/` for review.

If the emu run passes → task counts as done. If the recording doesn't
show the expected behavior — even if no crash fired — the task is **not
done**. Visual review of the screen.mp4 is part of the gate.

**Soon (Pixel arrives) — same gate against real device.** When a Pixel
device is on the test bench, every gate runs both against emu AND against
the device. The convention does not change; only the substrate doubles.

**For tests deeper than "doesn't crash"** (e.g., privacy enforcement,
permission denial path), wrap them as instrumented tests; emu-ci-run.sh
exercises them inside the activity it launches. A failed instrumented
test is a failed emu run.

For each Tier N item below, the **Emu gate** column names what the emu
run must do to count as proof.

#### Known emu substrate constraints

The emu (`emu.devkeys.net`) is **Pixel 5 / API 35 / x86_64**. The
recovered cosmo binaries are **arm64-v8a only**. Practical implications:

- **SODA native runtime (`libsoda_dev_jni.so`) cannot load on emu** — the
  on-device half of the speech stack is silently skipped on every emu run.
  `JniSodaCapability.isAvailable` returns false → gatekeeping falls
  through to the always-text path. This is fine: the emu still validates
  app boot, Hilt graph, MainActivity render, ContextStore open, ContextHub
  degradation, and any text-only Gatekeeper / Fulfillment / IntentExtractor
  / LLM-client behavior.
- **EdgeTPU Conformer + AOC nanoapp cannot be exercised on emu** —
  both depend on Tensor / platform-signed paths the emu doesn't have.
  Both report unavailable correctly during emu validation, which IS the
  expected behavior. Real activation only happens on real Pixel.
- **CI runner doesn't have the recovered binaries** (TFLites, SODA packs,
  .so files) — they're gitignored and provisioned via
  `scripts/provision-from-recovery.sh` from `~/Downloads/cosmo_recovered_*`,
  which the runner doesn't have. CI's `scripts/download-models.sh
  --verify-only --allow-missing-binaries` tolerates this. Binary
  distribution for CI is a known open problem (Tier 6+ item: hosted
  release-asset bundle or Azure blob — see PLAN.md §16 future work).

#### Validation baseline (job 3f7ce1f2247a433ead14d1f5889fb698, 2026-05-15)

A local-from-developer emu run of `MainActivity` for 90s on current `main`
produced these findings (full artifacts at
`artifacts/emu-run/job-3f7ce1f2247a433ead14d1f5889fb698/`):

✅ **Confirmed live by lifecycle log emissions:**
- App process boots; ART runtime loads (`-Xcheck:jni`, `CollectorTypeCMC GC`)
- Hilt `SingletonComponent` graph builds; injects through `MainActivity` → `ContextEventDao` → `PeriodicScreenCaptor` chain
- `MainActivity.onCreate` runs at `MainActivity.kt:128`
- `COSMO-Profiling` debug diagnostics initialize
- `LeakCanary` is running and ready (debug build canary active)
- `WorkManager` initialized; `SystemJobScheduler` enabled
- `ProfileInstaller` installs the baseline profile
- `ContextHubClientUtil` correctly reports "ContextHubManager not available on this device" (expected for emu)
- `NanoAppClient` correctly degrades with the documented "ContextHubClientCallback is a class; cannot subclass without bytecode gen" message
- `JniSodaCapability.isAvailable` is called (SODA controller attempted to start) but library load fails as expected on x86_64 emu

⚠️ **Real issues surfaced:**
- **`StrictMode` caught disk-read-on-main-thread** during `ContextStoreModule.provideDatabase(ContextStore.kt:391)` — the encrypted SQLCipher Room database opens eagerly via Hilt injection through `injectMainActivity → contextEventDao → SwitchingProvider`. **This is a real bug.** Likely fix: wrap as `Lazy<ContextStore>` or `Provider<ContextStore>` so the first DB open happens off-main-thread. (Tier 5.3-adjacent.)
- **165 frames skipped** (`Choreographer: Skipped 165 frames!`) + 2 `HWUI: Davey!` 2.8s frame drops — cold-start jank caused by the same eager DI chain.

🟡 **Not exercised by 90s MainActivity launch** (need explicit user flow / test fixture):
- `Gatekeeper.start()` — tag never appeared
- `IntentExtractor.start()` — tag never appeared
- BERT model loading via TFLite
- AICore wrapper / LLM invocation (no LLM calls in idle state)
- Fulfillment runner (no user query fired)
- Presence surfaces (no UI navigation; MainActivity is the Nav3 home, not Presence)
- 22 fulfillment action types
- Telemetry event emission (Firebase Analytics disabled — no `google-services.json` in debug build)

The validation baseline confirms Tier 0.4 + Tier 0.6 ✅ but leaves Tier 1
through Tier 5 items as **claimed-but-unexercised** until interactive
flows (text input, voice, permission grants) are scripted via instrumented
tests or `GatekeeperDebugReceiver` adb broadcasts. The natural next-step
work is Tier 1.2 (text input → timer flow) — that's the smallest end-to-end
exercise of the full stack.

---

### 3.0a Split by bucket — validation vs missing code

Every item in §3.1–§3.7 falls into one of two buckets. The split decides
who picks it up:

- **Bucket V — Validate feature is working.** Code is wired; work is
  exercising it on emu (eventually real device), watching logs + screen,
  confirming documented behavior occurs. Owned by the validation engineer.
- **Bucket M — Missing / new code.** Code does not exist or is partial.
  Work is writing or completing it. Owned by the feature engineer.

Items move M → V once written; not the other way (a validation-failure
means the feature regressed, not that the bucket flipped).

Each item below is **self-contained**: file paths, current state, the gap,
the contract to write, and what "done" looks like. An engineer should be
able to start either bucket without re-reading the rest of PLAN.md.

#### Bucket V — Validate working (per item)

For each: **Where** (entry point), **Trigger** (how to exercise), **Confirm** (what to look for).

**V0.1 PR gate green**
- Where: `.github/workflows/ci.yml`
- Trigger: open any PR
- Confirm: `compile-lint-test` job green; `emu-validate` skipped (manual)

**V0.2 dev-loop.sh on hardware**
- Where: `scripts/dev-loop.sh`
- Trigger: `scripts/dev-loop.sh --reinstall` with a USB-attached Android
- Confirm: APK installs, MainActivity launches, logcat streams; Ctrl+C cleanly stops logcat

**V0.3 emu-ci-run.sh manual trigger**
- Where: `.github/workflows/ci.yml` workflow_dispatch + `scripts/emu-ci-run.sh`
- Trigger: GH Actions → fm-ci → "Run workflow" with `run_emu=true`, `emu_run_seconds=90`
- Confirm: `emu-validate` job green; artifact `emu-<run>` contains `logcat.txt` + `screen.mp4`

**V0.4 MainActivity Home render 30s**
- Where: `app/src/main/kotlin/.../ui/mainscreen/MainActivity.kt:128`
- Trigger: `scripts/emu-ci-run.sh app-debug.apk com.google.research.air.cosmo/.ui.mainscreen.MainActivity 30`
- Confirm: 0 lines matching `FATAL|ANR|Exception` in logcat; screen.mp4 shows non-blank Home

**V0.5 Fresh clone → APK**
- Where: `scripts/provision-from-recovery.sh`, `scripts/download-models.sh`
- Trigger: `git clone`; `scripts/provision-from-recovery.sh`; `./gradlew assembleDebug`; `adb install`
- Confirm: assembleDebug exits 0; app launches on device

**V0.6 SHA-256 binary verification**
- Where: `app/src/main/assets/model-manifest.json` + `scripts/download-models.sh --verify-only`
- Trigger: PR CI compile-lint-test step "Model manifest"
- Confirm: 0 checksum mismatches (--allow-missing-binaries used; missing-not-mismatched still passes)

**V1.1 First-launch permission rationale flow**
- Where: `app/src/main/kotlin/.../lib/permission/PermissionRationaleHelper.kt:120` `PermissionRationaleHelper` class + `RuntimePermissionDescriptor` enum at `:42-107` (declaration at :42, 10 entries from :48 to :107)
- Trigger: fresh-install + MainActivity launch with no granted permissions
- Confirm: rationale dialog appears BEFORE each permission ask; deny → still functions; deny+don't-ask-again → settings deep link offered

**V1.2 Text → FulfillmentOrchestrator E2E**
- Where: `app/src/main/kotlin/.../fulfillment/FulfillmentOrchestrator.kt:12-23` `handleUserQuery(query, conversationHistory)`
- Trigger: Home text field → type "set a timer for 10 minutes" → submit
- Confirm: logcat shows `FulfillmentOrchestrator.handleUserQuery` → `Gatekeeper:` BERT classification → action parsed; a `CardPayload` renders on screen

**V1.4 Voice → SODA → Gatekeeper**
- Where: SODA JNI + `GatekeeperDebugReceiver`
- Trigger: `adb shell am broadcast -a com.google.research.air.cosmo.DEBUG_INJECT_AUDIO --es file <fixture>.wav` (or real-device mic)
- Confirm: SODA transcript log emitted → Gatekeeper classifies → same end of V1.2

**V1.5 Persistence across force-stop**
- Where: DataStore + `app/src/main/kotlin/.../context/ContextStore.kt:280-371` (RoomContextStore)
- Trigger: do V1.2 → `adb shell am force-stop com.google.research.air.cosmo` → relaunch → query ActionsRepository
- Confirm: prior timer entry present in history

**V1.6 Airplane mode no crash**
- Where: `app/src/main/kotlin/.../lib/llm/LLM.kt:151-270` NetworkAwareLLM
- Trigger: `adb shell svc data disable && svc wifi disable`; then V1.2 with a locally-answerable query
- Confirm: no LlmUnavailableException for both lanes; local path completes; LLMStatsRepository shows `route=Local`

**V2.2 SODA + Magic Mic directedness**
- Where: `app/src/main/kotlin/.../lib/soda/SodaController.kt:67` (`val directedness: SharedFlow<DirectednessEvent>`); `SodaConfigBuilder.kt:108` configures the IntendedSpeechConfig ("Magic Mic" in the comment); `JniSodaCapability.kt:23` is the JNI bridge
- Trigger: inject directed and undirected speech fixtures via `GatekeeperDebugReceiver`
- Confirm: `directedness` flow emits `DirectednessEvent` only for directed speech; `@AmbientAudioEphemeralBuffer` count increases only for directed

**V2.3 NotificationListenerService → buffer**
- Where: NotificationCaptureController + `@NotificationEphemeralBuffer`
- Trigger: `adb shell cmd notification post -t TEST -i android.R.drawable.sym_def_app_icon test test`
- Confirm: NotificationEphemeralBuffer count increments

**V2.4 Accessibility → buffer**
- Where: AccessibilityEventSource + `@ScreenEphemeralBuffer`
- Trigger: launch a known-text activity (debug `am start` with known text overlay)
- Confirm: buffer entries contain the expected text

**V2.5 Recall queryByText**
- Where: `ContextStore.kt:72-107` (interface), `queryByText` impl in RoomContextStore
- Trigger: seed events via debug receiver; ask "what did I see about X?"
- Confirm: response includes seeded text + timestamps + source; `queryByEmbedding` returns empty as documented

**V2.6 Disabled-source privacy (4 tests)**
- Where: each Captor's `if (settings.isXEnabled())` guard + `ContextStore.insert` filter; M5.8's self-check verifies the static map
- Trigger: instrumented `DisabledSourcePrivacyTest` (must-pass set: disabled-source, retention, local-only-no-egress, forget-window)
- Confirm: 0 events from disabled source reach ContextStore; 0 reach any LLM prompt context

**V3.1 Orb parked render**
- Where: `app/src/main/kotlin/.../presence/surfaces/OrbSurface.kt:50-118` consuming `OrbActivity.IDLE/LISTENING/RECENTLY_DISMISSED`
- Trigger: debug activity instantiating `OrbSurface(activity=IDLE)`
- Confirm: screen.mp4 shows orb in expected position with aerospace palette

**V3.2 Tab surface expand**
- Where: `app/src/main/kotlin/.../presence/surfaces/TabSurface.kt:60-85` `fun TabSurface(...)` (file is 313 lines total; rest is private composables) + `CardStackSurface.kt:42-107` `fun CardStackSurface(cards, onAction)`
- Trigger: tap orb in debug activity that wires TabSurface
- Confirm: TabSurface expands; CardStackSurface renders layered cards

**V3.4 Dismiss / snooze / reopen**
- Where: `presence/PresenceState.kt:7-14` (`Dismissed(until)`)
- Trigger: dismiss a Ready card in debug harness; advance a time-control fixture past `until`
- Confirm: state transitions `Dismissed → Parked` at the `until` timestamp

**V4.1 AICore on Pixel**
- Where: `app/src/main/kotlin/.../lib/llm/LLM.kt:104-116` `AICoreLLM` wrapping `AICoreClientWrapper`
- Trigger: real Pixel device with AICore installed
- Confirm: `LLMStatsRepository` shows `route=AICore` for a query that lands locally

**V4.4 Network router**
- Where: `LLM.kt:151-270` NetworkAwareLLM
- Trigger: toggle `adb shell svc wifi enable/disable`
- Confirm: route switches LOCAL ↔ CLOUD; reason logged in LLMStatsRepository

**V4.5 Lanes fail clean**
- Where: `LLM.kt:85-102` `LlmUnavailableException` + `LlmFailureCause` enum (AICORE_NOT_INSTALLED, MODEL_NOT_READY, QUOTA, NETWORK, AUTH, TIMEOUT, BAD_CERTIFICATE, CONFIGURATION, LOCAL_MODEL_UNAVAILABLE, INTERNAL — 10 cases)
- Trigger: force each of 10 failure causes (instrumented fixtures that inject the condition)
- Confirm: correct exception thrown; correct next-lane fallback taken

**V5.3 onTrimMemory**
- Where: `app/src/main/kotlin/.../lib/performance/MemoryPressureTrimmer.kt:8-31`
- Trigger: `adb shell am send-trim-memory com.google.research.air.cosmo RUNNING_CRITICAL`
- Confirm: log line `MemoryPressureTrimmer:`; `EphemeralBufferRegistry` byte + object buffers cleared

**V5.4 ANR watchdog**
- Where: `app/src/main/kotlin/.../lib/performance/DebugDiagnostics.kt:62-108` `MainThreadAnrWatchdog` (5s)
- Trigger: inject a 6s main-thread stall via debug receiver
- Confirm: stack dump logged BEFORE system ANR popup

**V5.5 StrictMode 0 violations in monkey** (gated on M-StrictMode landing)
- Where: StrictMode config in `app/src/main/kotlin/.../lib/performance/DebugDiagnostics.kt:15-23` (`setThreadPolicy` + `setVmPolicy`); installed from `CosmoApp.kt:45` via `DebugDiagnostics.install(this)`
- Trigger: `adb shell monkey -p com.google.research.air.cosmo 1000`
- Confirm: 0 `StrictMode` lines in logcat

**V5.6 R8 release build + APK ≤ 45 MB**
- Where: `app/build.gradle.kts` releaseInternal config
- Trigger: `./gradlew assembleReleaseInternal`
- Confirm: build green; APK size sane; install + launch → no missing-class crashes

**V5.7 Cert pinning + cleartext disabled**
- Where: `AndroidManifest.xml` `usesCleartextTraffic="false"` + `RemoteInferenceClient.kt:251-256` `CertificatePinner`
- Trigger: instrumented attempt at plain HTTP request to any host
- Confirm: refused; instrumented attempt with wrong cert → `BAD_CERTIFICATE` fail-fast

**V5.9 Encrypted ContextStore round-trip**
- Where: `ContextStore.kt` with `SupportOpenHelperFactory(passphraseProvider.passphrase())` at line 394
- Trigger: insert events → `adb pull` of `/data/data/<pkg>/databases/cosmo_context.db` → `file` + `xxd`
- Confirm: SQLCipher header; plaintext data NOT readable; `sqlite3` open without passphrase fails

**V6.9 Real Pixel sweep** (gated on Pixel arrival)
- Where: every V0–V5 item above
- Trigger: re-run each on the Pixel
- Confirm: same green state as emu, plus the V4.1 / V6.4 / V6.5 items emu cannot exercise

#### Bucket M — Missing / new code (per item, priority order)

**Cosmo as behavioral spec.** FM is a clean-room reimplementation of cosmo. The cosmo APK's decompiled smali is at `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/`. For each Bucket-M item below, the **Source** marker tags whether the item is:
- **PORT** — cosmo has the equivalent class/behavior; M-work is restoring it in FM
- **REORG** — FM has reorganized cosmo's structure (e.g. FM's `presence/` package consolidates UX that cosmo split across `voiceassistant/`, `ui/mainscreen/`, etc.) — M-work is wiring FM's redesigned shape
- **NEW** — no cosmo equivalent exists; the contract is genuinely new

Per-item source markers (verified by searching `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/`):
- **M1.3** — REORG. Cosmo has `SkillResult` proto at `gatekeeping/proto/SkillResultKt.smali`; the confirmation UI was rendered through `voiceassistant/ChatViewComposable` etc., not through FM's CardPayload abstraction.
- **M-StrictMode** — PORT/CLEAN. The eager-DI bug is FM-side; cosmo's class structure works fine because cosmo loaded sqlcipher elsewhere. Engineer should compare FM's `ContextStoreModule` against cosmo's equivalent (`~/Downloads/cosmo_baksmali/.../context/`).
- **M3.3** — REORG. **No cosmo `presence/` namespace exists.** Cosmo's UX state ran through individual ViewModels (`voiceassistant/BrowsingAgentViewModel.smali`, `ChatViewComposable`, `FloatyScreen$VoiceMatch.smali`). FM has consolidated into a single PresenceState producer; this is genuinely new architectural work.
- **M3.5** — REORG. Same as M3.3; CardPayload→Skill mapping is part of FM's redesigned presence layer, not cosmo.
- **M2.1** — NEW. Cosmo's `fulfillment/actions/` only has `mariner/` as a subdir; no per-skill executor split existed in cosmo.
- **M2.7** — NEW. No retention-sweep class found in cosmo's `ephemeralbuffer/` or `context/`. The buffer-side eviction is cosmo-shaped (EvictionPolicy in `EphemeralBuffer.kt:141-144`) but the store-side sweep is FM-new.
- **M2.8** — NEW. User-controlled RoutingMode is a clean-room addition. Cosmo's routing was driven by network policy automatically.
- **M2.9** — NEW. No "forget last N" surface in cosmo (search for `forget` returned only a regex match in `gatekeeping/impl/RegexGatekeepingModelV0.smali`, not a delete action).
- **M4.2** — PORT. Cosmo has `lib/llm/LiteRtLm*.smali` (LiteRtLmLLM exists in cosmo). The provisioning script is the FM-side gap.
- **M4.3** — NEW (server side). The client at `RemoteInferenceClient.kt` is structurally cosmo-derived (P-256 signing, PrivateToken bearer); the server stub never lived in the client APK.
- **M5.8** — NEW. CI self-check is FM tooling, not in cosmo.
- **M5.10** — PORT-style. Cosmo had per-permission rationale strings; FM's `RuntimePermissionDescriptor` enum currently covers all 10 dangerous manifest permissions. The risk is regression, not a current gap.
- **M6.1** — PORT. Cosmo has the full Voice Match UI: `voiceassistant/VoiceMatchViewComposable.smali` + `VoiceMatchEnrollingScreen` + `VoiceMatchEnrollmentEnrolledScreen`. FM has the JNI bridge at `SodaEnrollmentManagerImpl.kt`; the UI port is what's missing.
- **M6.2** — NEW. CaMeL is from the arxiv paper (2503.18813), not cosmo.
- **M6.3** — PORT. Cosmo's APK shipped with mt6989-targeted BERT variants; the AOT compile pipeline runs on a MediaTek toolchain machine.
- **M6.4** — PORT. Cosmo source: `gatekeeping/models/ConformerEncoderImpl.smali` + `ConformerEncoder.smali` + `ConformerEncoderModule.smali`. FM has the stub at `gatekeeping/backend/ConformerEdgeTpuBackend.kt`.
- **M6.5** — PORT-blocked. Cosmo source: `lib/record/NanoAppClient.smali` + `NanoAppClient$contextHubClientCallback$1.smali`. The subclass worked in cosmo because cosmo was platform-signed; FM needs bytecode-gen instead.
- **M6.6** — External project (Inherent); track for integration only.
- **M6.7** — NEW (24-handler split, not 22). Cosmo had centralized `when`; the split is a clean-room improvement.
- **M6.8** — Mixed. UI source in cosmo: `voiceassistant/BrowsingAgentViewModel.smali` + `BrowsingAgentScreenKt.smali` + `fulfillment/FulfillmentAction$BrowsingAgent.smali` (UI is PORT). Server-side backend is NEW.

For each item below: **Where** (current file/line + the gap), **Present** (what's wired today), **Gap** (what's missing), **Contract** (what to write), **Done** (acceptance), **Refs**.

**M1.3 SkillResult.SideEffectPending → confirmation card surfacing**
- Where:
  - `app/src/main/kotlin/.../skills/Skills.kt:62-86` — `SkillResult` sealed; `SideEffectPending(confirmationToken: String, summary: String)` at :73; `SideEffectApplied(auditId, summary)` at :78
  - `app/src/main/kotlin/.../skills/Skills.kt:88-107` — `SkillRegistry` already exposes `suspend fun confirm(token: String): SkillResult` (:104) and `suspend fun cancel(token: String)` (:106)
  - `app/src/main/kotlin/.../presence/PresenceState.kt:43-103` — `CardPayload.PermissionConfirmation(prompt: String, actionLabel: String, undoable: Boolean)` at :99-103
  - `app/src/main/kotlin/.../presence/surfaces/CardStackSurface.kt:42-107` — `CardStackSurface(cards, onAction: (cardId, actionId) -> Unit)`; renders `ActionButtonGrid(card.id, card.actions, onAction)` at :100
  - `app/src/main/kotlin/.../presence/surfaces/cards/PermissionConfirmationCard.kt:28-53` — content composable; its inner "CONFIRM / UNDO" boxes are decorative (no click handlers)
- Present: SkillResult emits SideEffectPending; SkillRegistry has confirm/cancel; CardStackSurface has onAction callback hook + per-card `actions: List<CardAction>` plumbing; PermissionConfirmation payload exists
- Gap: no producer constructs a `PresenceCard(id = token, payload = PermissionConfirmation, actions = [Confirm, Cancel])` from a SideEffectPending emission; no host wires `onAction` → `SkillRegistry.confirm/cancel`. The card's inner buttons being decorative is fine — the real surface is the action grid added below the card by `CardStackSurface`.
- Contract (sub-task of M3.3 producer):
  - In M3.3's reducer: when a `SkillResult.SideEffectPending(token, summary)` arrives, emit `PresenceState.Ready(taskId = invocationTaskId, cards = listOf(PresenceCard(id = token, payload = CardPayload.PermissionConfirmation(prompt = summary, actionLabel = "Confirm", undoable = true), actions = listOf(CardAction("confirm", "Confirm", type = CardActionType.PRIMARY), CardAction("cancel", "Cancel", type = CardActionType.DESTRUCTIVE)))))`
  - In the screen that hosts `CardStackSurface`: wire `onAction = { cardId, actionId -> scope.launch { when (actionId) { "confirm" -> skillRegistry.confirm(cardId); "cancel" -> skillRegistry.cancel(cardId) } } }`. cardId == confirmationToken by construction above.
- Done: instrumented: `FulfillmentOrchestrator.handleUserQuery("set a timer for 10 minutes")` → screen shows Permission card with the summary + Confirm/Cancel buttons rendered by ActionButtonGrid. Tap Confirm → `SkillRegistry.confirm(token)` returns `SideEffectApplied(auditId, summary)`; `ActionsRepository.history()` grows by 1. Tap Cancel → SkillRegistry.cancel(token); no apply; card dismisses.
- Refs: architecture.md §6 (fulfillment) + §13.2 (presence). Tightly coupled with M3.3.

**M-StrictMode disk-read-on-main-thread (Tier 5 critical fix)**
- Where: `app/src/main/kotlin/.../context/ContextStore.kt:381-396` `ContextStoreModule.provideDatabase` (object at :381; `System.loadLibrary("sqlcipher")` at :391; `.openHelperFactory(SupportOpenHelperFactory(...))` at :394); chain pulled in via `contentcapture/ScreenCapture.kt:32-64` (`PeriodicScreenCaptorImpl` class, injects `ContextStore` via the constructor at :32-39) → `controller/CosmoController.kt:35` (`private val periodicScreenCaptor: PeriodicScreenCaptor,`) → `MainActivity.kt:103` (`@Inject lateinit var cosmoController: CosmoController`)
- Present: `System.loadLibrary("sqlcipher")` at ContextStore.kt:391 runs synchronously during Hilt `provideDatabase` during `injectMainActivity` → ~154ms DiskRead on main thread; 165 frames skipped per baseline emu log
- Gap: loadLibrary on main thread + eager DI of ContextStore-dependent components into MainActivity
- Contract: (a) move `System.loadLibrary("sqlcipher")` to `CosmoApp.onCreate` on a dedicated single-thread coroutine + a `CompletableDeferred<Unit>` consumed before any DB op; (b) remove loadLibrary from `provideDatabase`; (c) change `CosmoController.periodicScreenCaptor` to `dagger.Lazy<PeriodicScreenCaptor>` and call `.get()` only in `reconcileCaptureJobs()` — same for any other ContextStore-touching consumer in MainActivity's transitive graph (audit all CosmoController fields)
- Done: emu-ci-run → 0 `StrictMode` DiskRead lines from ContextStoreModule on cold start; Choreographer skipped-frame count < 30 (baseline: 165)
- Refs: baseline emu run `artifacts/emu-run/job-3f7ce1f2247a433ead14d1f5889fb698/logcat.txt`

**M3.3 PresenceState driver/producer**
- Where: `app/src/main/kotlin/.../presence/PresenceState.kt` (file is 145 lines). Sealed variants at :7-14: `Hidden`, `Parked(mode: PresenceMode, activity: OrbActivity)`, `Working(taskId: String, phase: WorkPhase, pillText: String? = null)`, `NeedsInput(taskId, prompt)`, `Ready(taskId: String, cards: List<PresenceCard>, primaryAction: CardAction? = null)`, `Dismissed(until: Instant)`. `PresenceCard` at :24-28 wraps `(id, payload, actions)`. Supporting enums at :16-18 (`PresenceMode { ORB, TAB }`, `OrbActivity { IDLE, LISTENING, RECENTLY_DISMISSED }`, `WorkPhase { STARTED, SYNCING, PROCESSING, FINALIZING }`). Surfaces (OrbSurface, TabSurface, CardStackSurface) already consume `PresenceState`. No producer file today.
- Present: state shapes + surfaces wired; no orchestrator emits the state
- Gap: no `StateFlow<PresenceState>` producer that watches Gatekeeper output + SkillResult emissions + dismiss events
- Contract: new `presence/PresenceStateProducer.kt`:
  ```kotlin
  @Singleton
  class PresenceStateProducer @Inject constructor(
      gatekeeperOutput: GatekeeperOutputSource,           // emits GatekeepingResult flow
      orchestrator: FulfillmentOrchestrator,              // (or its SkillResult emission source)
      // dismissal channel from the host UI
  ) {
      private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val state: StateFlow<PresenceState> = … // see reduce()
      private fun reduce(...): PresenceState
  }
  ```
  Reducer mapping:
  - Idle (no recent input + no pending result) → `Parked(mode = ORB, activity = IDLE)`
  - Gatekeeper detects directed audio → `Parked(mode = ORB, activity = LISTENING)`; then on triggeredFulfillment → `Working(taskId, phase = STARTED)`; phase advances as runner emits progress
  - `SkillResult.SideEffectPending(token, summary)` → `Ready(taskId, cards = [PresenceCard(id = token, payload = PermissionConfirmation(prompt = summary, actionLabel = "Confirm"), actions = [Confirm, Cancel])])` (see M1.3 for exact CardAction shape)
  - `SkillResult.Answer(text)` / `Draft(...)` / `SideEffectApplied(...)` → `Ready(taskId, cards = [...payload-specific...])` per skill-card mapping (M3.5)
  - `SkillResult.Failed(reason)` → `Ready(taskId, cards = [error-card])` OR back to `Parked` with a transient toast (designer's call; document the choice)
  - Dismiss action from UI → `Dismissed(until = now + 10.minutes)`; auto-return to `Parked` when `Instant.now() >= until`
- Done: instrumented harness: post fake GatekeepingResult with triggeredFulfillment=true → `state.value` is `Working`. Emit SideEffectPending → `state.value` is `Ready(cards=[PermissionConfirmation])`. Fire dismiss → `Dismissed`. Advance time-control past `until` → `Parked`.
- Refs: architecture.md §13.2.

**M2.1 Per-subtype skill executor split (9 parity skills)**
- Where: `app/src/main/kotlin/.../skills/Skills.kt:88-107` SkillRegistry (uses 4-bucket `SkillCategory` PRODUCTIVITY/CAPTURE/KNOWLEDGE/UTILITY); `app/src/main/kotlin/.../fulfillment/FulfillmentActionHandler.kt:29-138` central `when (action)` at :41
- Present: SkillRegistry interface; SkillsRepositoryImpl with category-only descriptors; central FulfillmentActionHandlerImpl dispatching to inline blocks
- Gap: no per-skill executor classes; 9 parity skills not formally enumerated as distinct types
- Contract: 
  ```kotlin
  sealed interface SkillExecutor {
      val id: SkillId
      suspend fun execute(query: ParsedQuery, ctx: List<ContextEvent>): SkillResult
  }
  ```
  One impl per parity skill in `skills/executors/`: TimerSkill, ListSkill, CalendarSkill, DocSkill, WebSearchSkill, JargonSkill, InsightSkill, RecallSkill, SummarySkill. Bind via `@IntoMap @SkillIdKey` Dagger multibinding; replace central `when` with `executors[query.skillId]?.execute(query, ctx)`.
- Done: 9 executor files exist; central `when` block deleted or only delegates; instrumented test routes each skill to its dedicated class.
- Refs: precursor to M6.7 (22-handler split for FulfillmentAction). Architecture.md §6.1.

**M2.7 ContextStore retention sweep**
- Where:
  - `app/src/main/kotlin/.../context/ContextStore.kt:72-107` — `ContextStore` interface. Existing delete APIs:
    - `suspend fun delete(id: String)` (:95)
    - `suspend fun deleteRange(range: LongRange, sources: Set<Source>? = null): Int` (:97-100) — **time-range + optional source filter; NO retention-class filter**
    - `suspend fun purgeSource(source: Source): Int` (:102)
  - `app/src/main/kotlin/.../context/ContextStore.kt:50` — `enum class RetentionClass { EPHEMERAL, SHORT, LONG }` — present as a column on `ContextEventEntity` (:44), exposed in `stats().countsByRetention`, but NOT a filter param on `deleteRange`.
  - `app/src/main/kotlin/.../ephemeralbuffer/EphemeralBuffer.kt:141-144` — `EvictionPolicy(maxEntries, maxEntryAgeMillis)` for in-memory buffers
- Present: time-range deletion + per-source purge; retentionClass is a column but not a filter for delete; no sweep scheduler
- Gap: (a) `deleteRange` cannot filter by `RetentionClass`, so a per-class sweep needs a Dao-level addition or a query-then-delete approach; (b) no `WorkManager` job that runs the sweep
- Contract:
  - Extend the interface: add `suspend fun deleteRange(range: LongRange, retentionClasses: Set<RetentionClass>): Int` (overload). Implement in `RoomContextStore` via a new `@Query("DELETE FROM context_events WHERE timestampMs BETWEEN :startMs AND :endMs AND retentionClass IN (:classes)")` Dao method.
  - New `context/ContextRetentionSweeper.kt` — `WorkManager` `PeriodicWorkRequest` every 1h. Reads a `RetentionPolicy(map: Map<RetentionClass, Duration>)` from CosmoSettingStore (default: EPHEMERAL=24h, SHORT=7d, LONG=90d). For each class with a finite duration, call `contextStore.deleteRange(range = 0L..(now-duration).toEpochMilli(), retentionClasses = setOf(rc))`.
  - Schedule from `CosmoApp.onCreate` (after the sqlcipher-load completes per M-StrictMode).
- Done: instrumented: insert ephemeral events older than 24h, short older than 7d, recent of each → trigger one-shot sweep work → only events newer than per-class retention remain.

**M2.8 Routing-mode settings UI**
- Where: `app/src/main/kotlin/.../setting/Settings.kt:91-241` CosmoSettingStore; `app/src/main/kotlin/.../lib/llm/LLM.kt:151-270` NetworkAwareLLM (2-mode: LOCAL/CLOUD; "hybrid" is an execution strategy, not a 3rd mode)
- Present: NetworkAwareLLM picks lane by network availability; no user-controlled mode
- Gap: `RoutingMode` user setting + Compose preference UI + NetworkAwareLLM honor of user-forced mode
- Contract: (a) add `enum class RoutingMode { LOCAL_ONLY, CLOUD_ONLY, HYBRID }` to settings proto + CosmoSettingStore getter/setter (default = HYBRID); (b) add segmented control to Settings screen under "Privacy & Routing"; (c) in `NetworkAwareLLM.execute()` short-circuit: LOCAL_ONLY → local only (`LlmFailureCause.CONFIGURATION` if local unavail); CLOUD_ONLY → cloud only; HYBRID → current behavior
- Done: setting persists across force-stop; `LLMStatsRepository` shows the correct lane per mode.

**M2.9 "Forget last N minutes" action**
- Where: no orchestrator file yet
- Present:
  - `ContextStore.deleteRange(range: LongRange, sources: Set<Source>? = null): Int` at `ContextStore.kt:97-100` — usable directly for a time-window wipe across all sources or a filtered subset
  - `EphemeralBufferWriter` interface at `EphemeralBuffer.kt:196-212` exposes `clear()` (:204) and `remove(predicate: (T) -> Boolean): Boolean` (:206) — no time-range API
  - `EphemeralBufferRegistry` interface at `EphemeralBuffer.kt:381-401` exposes `writableObjectSourceIds(): Set<EphemeralBufferId>` (:386), `byteSourceIds(): Set<EphemeralBufferId>` (:388), `writer(id): EphemeralBufferWriter<Any>` (:392), `byteWriter(id): EphemeralBufferWriter<ByteArray>` (:396)
- Gap: (a) user-facing action; (b) orchestrator handler; (c) buffer-side range-clear API
- Contract:
  - Add `fun clearRange(rangeMs: LongRange): Int` to `EphemeralBufferWriter` interface (`EphemeralBuffer.kt:196`); implement in `InMemoryEphemeralBuffer` (from `EphemeralBuffer.kt:246` onward) by traversing the timestamped entries and removing those whose `timestampMillis` falls in the range
  - Add `ForgetWindowAction(endMs: Long, durationMs: Long)` to `FulfillmentModels.kt`
  - Add Gatekeeper intent label `FORGET_WINDOW`; either retrain BERT classifier with the new label OR add a regex pre-classifier that catches phrases "forget the last N (minutes|hours)" before BERT
  - New handler `ForgetWindowHandler.handle(action)`:
    - `val rangeMs = (action.endMs - action.durationMs)..action.endMs`
    - For each id in `bufferRegistry.writableObjectSourceIds()`: `bufferRegistry.writer(id).clearRange(rangeMs)`
    - For each id in `bufferRegistry.byteSourceIds()`: `bufferRegistry.byteWriter(id).clearRange(rangeMs)`
    - `contextStore.deleteRange(range = rangeMs, sources = null)` — purges all sources in the window
  - UI: Settings → "Forget last…" preset buttons (1m / 5m / 10m / 30m / 1h) calling the handler with `endMs = System.currentTimeMillis()`
- Done: voice phrase OR settings button → 0 events remain in any buffer between [end-duration, end]; 0 rows in ContextStore for the same window; events outside window survive.

**M3.5 Card payload coverage per parity skill**
- Where: `app/src/main/kotlin/.../presence/PresenceState.kt:43-104` (CardPayload sealed — 10 variants: Browser, Calling, Messaging, Document, ListTasks, CalendarTime, PeopleContext, MediaImage, SourceCitation, PermissionConfirmation)
- Present: 10 payloads + card composables in `presence/surfaces/cards/` (correction: PLAN.md previously claimed 3 — wrong)
- Gap: the mapping `SkillResult → CardPayload variant` per parity skill is not documented or wired uniformly. Some skills emit payloads; some may emit only `Done` with no card.
- Contract: new `skills/SkillCardMapping.kt` — exhaustive `fun SkillResult.toCardPayload(): CardPayload?` for each of 9 parity skills. Wire into M3.3's reducer.
- Done: each of 9 skills executed in instrumented harness → card renders with correct payload variant + correct content.

**M4.2 LiteRT-LM bundle provisioning default**
- Where: `scripts/provision-gemma4-litertlm.sh` exists; `app/src/main/kotlin/.../lib/llm/LiteRtLmLLM.kt:32-266` loads from `models/${storageSlug()}/bundled.litertlm` (line 225)
- Present: script + loader present
- Gap: bundle not provisioned on fresh clone; `LiteRtLmLLM.isAvailable` returns false until someone runs the script manually
- Contract: add a bundle entry to `app/src/main/assets/model-manifest.json` (id `gemma4-e4b-litertlm`, delivery=`remote`, url=official HuggingFace LiteRT-LM URL, sha256/sizeBytes, mediaType `application/x-litertlm-bundle`, required=true) so `scripts/download-models.sh` provisions it. OR: invoke `provision-gemma4-litertlm.sh` from `scripts/provision-from-recovery.sh` for fresh clones.
- Done: fresh clone + provision + assembleDebug + launch → `LiteRtLmLLM.isAvailable=true`; query routes through it with `route=LiteRtLm` in LLMStatsRepository.

**M4.3 Remote inference server**
- Where: no `server/` yet. Client side in `app/src/main/kotlin/.../lib/llm/RemoteInferenceClient.kt`:
  - `interface RemoteInferenceClient.infer(parts, onToken)` at :34-39
  - `data class RemoteInferenceConfig(baseUrl, certificatePin, audience)` at :41-75; `fromBuildConfig()` reads `CosmoBuildConfig.remoteInference*` Gradle properties
  - `data class AnonymousRateLimitedCredential(authorizationHeader, expiresAtEpochMillis)` at :77-88 with required `"PrivateToken "` bearer scheme prefix
  - `OkHttpClient.Builder` at :245-259 with `CertificatePinner` at :251-256, `RequestSigningInterceptor` added at :257
  - `OkHttpRemoteInferenceClient` class at :268
- Present: full client incl. signed-request scheme + cert pinning + PrivateToken bearer credential type
- Gap: minimum server stub matching the client's existing contract
- Contract: new `server/inference/` Python (FastAPI), ~150 LOC:
  - `POST /v1/private-token/issue` returns an authorization header value satisfying `"PrivateToken " + token` (per AnonymousRateLimitedCredential at :77-88), valid until `expiresAtEpochMillis`
  - `POST /v1/inference` verifies the request-signing interceptor's header (ECDSA over canonical request bytes — read the exact algorithm in `RequestSigner` / `RequestSigningInterceptor` before implementing the verifier), verifies `Authorization: PrivateToken …`, runs LLM, returns SSE deltas
  - LLM backend: configurable; default `mock-llm` for dev; `vllm` recipe documented for Gemma3
  - `Dockerfile` + `pyproject.toml`; `make dev` runs locally on `:8080`
  - For emu loopback set `cosmo.remoteInference.baseUrl=http://10.0.2.2:8080` in debug Gradle props (read by `CosmoBuildConfig.remoteInferenceBaseUrl`)
- Done: client+server roundtrip locally; emu debug build hits `10.0.2.2:8080`, gets SSE response; `LLMStatsRepository` shows the route + token issuance recorded.
- Refs: architecture.md §15.4–§15.6.

**M5.8 Permission posture self-check**
- Where: `app/src/main/AndroidManifest.xml` (23 `<uses-permission>` entries — verified by `grep -c "<uses-permission " AndroidManifest.xml`); `app/src/main/kotlin/.../lib/permission/PermissionRationaleHelper.kt:51-107` (RuntimePermissionDescriptor enum)
- Present: enum has descriptors for some permissions
- Gap: no scripted self-check
- Contract: new `scripts/verify-permission-posture.sh` (Python): parse AndroidManifest.xml uses-permission entries; for each dangerous permission, assert an entry exists in `RuntimePermissionDescriptor`; assert foreground-service types declared in `<service>` match `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_*">` set; exit non-zero on any mismatch. Add to CI compile-lint-test as a non-blocking step initially, then hard gate after M5.10.
- Done: script runs in CI; reports clean.

**M5.10 Rationale UI coverage for all dangerous permissions**
- Where: `RuntimePermissionDescriptor` enum at `PermissionRationaleHelper.kt:42-107` (declaration at :42; 10 enum entries from :48–:107). Current entries: Microphone, Camera, Notifications, FineLocation, ApproximateLocation, CalendarRead, CalendarWrite, PhoneCall, NearbyDevices, LocalNetwork. `PermissionRationaleHelper` class at :120 with `states()` returning per-permission `RuntimePermissionState(descriptor, grantState)`.
- Present: enum covers all 10 *dangerous-runtime* permissions in the manifest. The other 13 manifest entries are NORMAL permissions (INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE_*, SCHEDULE_EXACT_ALARM, WAKE_LOCK, etc.) or signature-protected (ACCESS_CONTEXT_HUB) — no runtime rationale required.
- Gap: **probably already done** at coverage level. The real risk is that future-added dangerous permissions get added to manifest without an enum entry — M5.8's self-check catches that. Spot-check: every current dangerous `<uses-permission>` does map to an enum entry.
- Contract: keep this item open as a guard-rail. M5.8's script enforces the invariant going forward. No new code today unless a permission is genuinely missing — verify by running M5.8's script once written.
- Done: M5.8 self-check passes; instrumented launch with each dangerous permission ungranted → rationale dialog renders before the ask.

**M6.1 Voice Match enrollment UI**
- Where: `app/src/main/kotlin/com/google/android/libraries/assistant/speakerid/SodaEnrollmentManagerImpl.kt` (26 lines, full file). JNI surface (all `external fun`):
  - `nativeConstruct(): Long` (:10) — returns a native handle
  - `nativeDelete(handle: Long)` (:12)
  - `nativeProcessRequest(handle: Long, payload: ByteArray)` (:14) — feed audio frames / commands during enrollment
  - `nativeGetSpeakerInfo(handle: Long): ByteArray` (:19) — readable speaker model bytes
  - `nativeGetVoiceMatchModels(handle: Long): ByteArray` (:21)
  - `nativeGetEnrollmentAudio(handle: Long): ByteArray` (:23)
  - `nativeGetEnrollmentAudioInfo(handle: Long): ByteArray` (:25)
  - `init { SodaJniLibraryLoader.ensureLoaded() }` (:6-8)
- Present: JNI bridge + recovered SODA enrollment binaries (provisioned via `scripts/provision-from-recovery.sh`). No UI; no Kotlin-side session wrapper that manages the handle lifecycle.
- Gap: 3-utterance enrollment Compose flow + handle lifecycle wrapper + speakerInfo persistence + speakerLabel population
- Contract:
  - New `speakerid/SodaEnrollmentSession.kt` — Kotlin wrapper managing the native handle:
    ```kotlin
    class SodaEnrollmentSession : AutoCloseable {
        private val impl = SodaEnrollmentManagerImpl()
        private val handle: Long = impl.nativeConstruct()
        fun processUtterance(pcm: ByteArray) = impl.nativeProcessRequest(handle, pcm)
        fun speakerInfo(): ByteArray = impl.nativeGetSpeakerInfo(handle)
        override fun close() { impl.nativeDelete(handle) }
    }
    ```
  - New `presence/screens/VoiceMatchEnrollmentScreen.kt` — Compose 3-step flow (Welcome → Record 1/2/3 → Done). Each record step opens a `MediaRecorder` (PCM 16kHz mono) and pipes captured frames into `session.processUtterance(pcm)`.
  - On completion: `session.speakerInfo()` → persist `ByteArray` to encrypted CosmoSettingStore; expose `getSpeakerInfo(): ByteArray?`.
  - Wire into transcription path: when SODA emits TranscriptionData and `getSpeakerInfo()` non-null, populate `TranscriptionData.speakerLabel` (verify there is a Kotlin-side compare API or whether speakerLabel is already populated by the underlying SODA library when a saved speakerInfo is restored before transcription).
- Done: emu run launches enrollment → 3 fixture utterances → `getSpeakerInfo()` returns non-empty bytes; ambient transcript afterward has populated `speakerLabel`.

**M6.2 CaMeL trust layer (Plan DSL variant)**
- Where: no code; architecture at `docs/architecture.md §12`; per-AST-node port reference at `docs/camel-port-notes.md`
- Present: architecture only
- Gap: everything (~2 engineer-weeks)
- Contract (Kotlin), new package `lib/camel/`:
  - `data class Capabilities(val sourcesSet: Set<Source>, val readersSet: Set<Reader>)` with `union`, `intersection`, `isReadableBy` helpers
  - `data class Value<T>(val value: T, val capabilities: Capabilities)`
  - `interface SecurityPolicy { fun check(call: FunctionCall, args: Map<String, Value<*>>, deps: Set<Value<*>>): SecurityResult }`
  - `class SecurityPolicyEngine(val policies: List<SecurityPolicy>) { fun check(...): SecurityResult }`
  - `interface QuarantinedLlmService { suspend fun query(prompt: Value<String>): Value<String> }` — output inherits prompt capabilities; backed by an LLM with a strict "answer only" system prompt
  - `interface PrivilegedLlmService { suspend fun generate(prompt: String): String }` — no Value wrapping; for the code-gen path
  - Interpreter: Python-subset AST → Kotlin executor; nodes for `assign`, `call`, `if`, `for`, `while`, `binop`, `attr`, `index`; each call passes through SecurityPolicyEngine.check
  - `class LoopAgent(val pllm: PrivilegedLlmService, val interpreter: PlanInterpreter)` — PLLM emits code as string, Interpreter executes, loop on error
- Done: emu run: a tainted-source query (NotificationEphemeralBuffer entry passed as `Value<String>(value, Capabilities(sourcesSet=setOf(NOTIFICATION)))`) attempts a state-changing tool → SecurityPolicyDeniedError surfaces in agent loop; the action does NOT execute.
- Refs: arxiv 2503.18813; reference impl `~/Downloads/pixel-backups/backup_git/git/home/adk-samples/python/agents/camel/`.

**M6.3 mt6989 BERT variant AOT pipeline**
- Where: no in-repo file; reference source in `~/Downloads/cosmo_recovered_*` for the original AOT pipeline
- Present: arm64 cpu/dsp BERT variants in `app/src/main/assets/models/`; mt6989 variant absent
- Gap: AOT pipeline (runs on a MediaTek Neuron toolchain machine, not the dev's normal machine)
- Contract: 
  - On the toolchain machine: port cosmo AOT script (or reverse-engineer from recovered)
  - Output: `bert_mt6989_dsp.tflite`; entry in `app/src/main/assets/model-manifest.json` with sha256/size/url
  - `SocVariantResolver` picks `bert_mt6989_dsp.tflite` when `Build.SOC_MODEL == "MT6989"`
- Done: debug-override SocVariantResolver to report MT6989 → resolver picks the new variant → BERT load succeeds.

**M6.4 Conformer EdgeTPU activation on Pixel**
- Where: `app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/backend/ConformerEdgeTpuBackend.kt`
- Cosmo source: `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/gatekeeping/models/ConformerEncoderImpl.smali` (+ ConformerEncoder.smali, ConformerEncoderModule.smali) — port the real probe + initialize path
- Present: `isAvailable` stub (engineer should read FM file + diff against cosmo smali to identify the gap)
- Gap: real Tensor DarwinNN delegate probe (cosmo had the working version under platform-signed access)
- Contract: real `isAvailable` checks `Build.DEVICE` membership in a Pixel-with-DarwinNN allowlist AND probes EdgeTPU delegate construction without throw; returns false on emu / non-Pixel (preserving current behavior).
- Done: real Pixel device → Conformer route logged in LLMStatsRepository for transcription queries.

**M6.5 AOC nanoapp transport**
- Where: `app/src/main/kotlin/com/google/research/air/cosmo/lib/record/NanoAppClient.kt` — currently degrades with log `ContextHubClientCallback is a class; cannot subclass without bytecode gen` (per baseline emu log)
- Cosmo source: `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/lib/record/NanoAppClient.smali` + `NanoAppClient$contextHubClientCallback$1.smali` — cosmo subclassed `ContextHubClientCallback` because cosmo's APK was platform-signed. FM does not have platform signing; the subclass must be generated at build time
- Present: degraded probe path
- Gap: real subclass of `ContextHubClientCallback`
- Contract: prefer build-time bytecode gen (ByteBuddy or `kotlinx.metadata.jvm`-based gen) producing a concrete `ContextHubClientCallback` subclass that forwards to `Channel<NanoAppMessage>`. Alternative: platform-signed APK + manifest signing (requires platform signing access).
- Done: emu (with mock ContextHub fixture) or platform-signed Pixel build → no "cannot subclass" log → nanoapp events flow through the channel.

**M6.7 Per-subtype action handler split (24 handlers)**
- Where: `app/src/main/kotlin/.../fulfillment/FulfillmentActionHandler.kt:29` (`FulfillmentActionHandlerImpl` class); `when (action)` at :41. Subtypes in `FulfillmentModels.kt:15` (`sealed class FulfillmentAction`) — **24 concrete subtypes** verified: AddTask, AddTimer, AddToList, AddToMemory, BrowsingAgent, CallAgent, CreateCalendarEvent, CreateDoc, DeepResearch, DefineTerm, DraftEmail, DraftSms, InvokeAgentQ, Navigation, PlayMusic, ProvideEventContext, ProvideInsight, ProvidePersonContext, ProvideRecall, QueryPhotos, WebSearch, ActiveDataPlan, RemovedAction, Unsupported.
- Cosmo source: cosmo's `fulfillment/` smali does NOT have a per-handler split — only `fulfillment/actions/mariner/` as a subdir. So **this item is a clean-room improvement over cosmo's centralized `when`, not a port.**
- Present: monolithic handler
- Gap: 24 dedicated handler classes
- Contract:
  ```kotlin
  sealed interface ActionHandler<A : FulfillmentAction> { suspend fun handle(action: A, ctx: HandlerContext): HandlerResult }
  ```
  One file per subtype in `fulfillment/handlers/` (24 files total); `@IntoMap @ActionTypeKey` Dagger multibinding; replace `when (action)` with `handlers[action::class].handle(...)`.
- Done: 24 handler files; central `when` deleted.
- Refs: extends M2.1.

**M6.8 Mariner browsing-agent backend**
- Where: `app/src/main/kotlin/.../fulfillment/actions/mariner/TaskMonitorNavigationBridge.kt:1-98` (activity-bridge — NOT gRPC as PLAN.md previously implied)
- Present: activity-bridge stub
- Gap: real browsing-agent backend + streamed state to TaskMonitor surface
- Contract: tier-2 (default) Browser-Use server in `server/browse/` (similar shape to M4.3): `POST /v1/browse/execute` returns SSE of `BrowserState(currentUrl, screenshotUrl, actionHistory, status)`. New Kotlin client `fulfillment/actions/browser/BrowsingAgentClient.kt` (HTTP/SSE). Wire BrowsingAgent action → client.execute(query) → flows into TaskMonitor surface as incremental progress.
- Done: emu run: "find me a flight SFO to LAX" → TaskMonitor shows incremental BrowserState → final answer.

**M6.6 Inherent joint model delivery** — external project deliverable; not in-repo work. Track for integration; no contract written here.

#### Will Bucket V + Bucket M = demo-ready / user-ready?

**Demo-ready: yes.** With V+M complete, every Tier 0–3 flow has a screen-recordable emu run; Tier 4 routing demonstrates all lanes; Tier 5 hardening passes the monkey + StrictMode + cleartext checks. The 6-flow demo script (PLAN.md §11) plays end-to-end from a single APK.

**User-ready (= ask a real person to install + use it day-to-day): no.** V+M leaves at least these gaps before users:

1. **Crash reporting** — Firebase Analytics is disabled (no `google-services.json` in debug build per baseline emu log). With no crash reporter, the first user-side crash is invisible. Need: Firebase Crashlytics integration OR a self-hosted alternative (Sentry self-host, Bugsnag-style endpoint to the M4.3 server) tied to the privacy posture.
2. **Production release signing + distribution** — `releaseInternal` works (V5.6) but it's debug-signed. No production keystore is set up (`config/signing/*release*.keystore` is gitignored, no committed production cert). No distribution channel chosen (Play Store internal track? Firebase App Distribution? signed sideload?). Without this, "ask someone to install" means "give them an unsigned/dev-signed APK", which most Android devices will refuse outside developer mode.
3. **User feedback loop** — no in-app feedback form, no telemetry endpoint for "this didn't work" reports. The 4 must-pass privacy tests assume the user has no need to phone home; some opt-in feedback path is necessary or you only hear about bugs through word-of-mouth.
4. **Real-device battery proof** — V5.2 is gated on Pixel arrival; until proven, sustained-use battery is unknown. 3%/day target may not hold; without it, users will uninstall after a day.
5. **Full data-wipe** — M2.9 covers "forget last N minutes" per source. There's no "delete everything" / GDPR-style flow that wipes ContextStore + buffers + speakerInfo + settings + cached LLM responses in one action. Required for trust.
6. **Onboarding flow** — V1.1 covers permission rationales but there's no first-launch product walkthrough (what is FM, what does it do, what does it not do, voice match enrollment if available). Without it, install-to-first-meaningful-use cliff is steep.
7. **i18n / accessibility** — strings are English-only; no TalkBack audit, no font-scaling audit. Required for any non-English user; required for Play Store accessibility compliance.
8. **Self-hosted server SLO story (M4.3)** — if remote inference is offered, uptime + logging + rate-limiting + abuse handling is operational work outside the client. The server stub doesn't address run-it-in-production.

**Net:** V+M is sufficient to **show** FM working to a stakeholder via emu screen recordings or a one-on-one Pixel demo with engineer present. It is **not** sufficient to put on a stranger's phone and walk away. The "ready for users" layer is a Tier 5.5 / 5.6 of operational/release concerns that are not yet in PLAN.md. Adding them after Bucket V+M are complete is ~1–2 additional engineer-weeks (excluding self-host server ops, which can be deferred by choosing the on-device-only routing default).

---

### 3.1 Tier 0 — Foundation (everything else assumes these)

| # | Item | Status | Emu gate |
|---|---|---|---|
| 0.1 | `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug` green on every PR | ⚠️ (pre-existing `Color.kt` detekt MatchingDeclarationName issue blocks `detekt`; build + lint + unit tests green) | CI runs the four commands; both Kotlin + Java sources clean |
| 0.2 | `scripts/dev-loop.sh` launches FM on a connected device, streams logs, exits cleanly when interrupted | ✅ smoke-tested via `--help`; not exercised end-to-end on hardware | Run `dev-loop.sh --no-logs --no-launch --apk-only` against emu — APK produced, size sane |
| 0.3 | `scripts/emu-ci-run.sh` integrated into PR CI; fails on any FATAL/ANR within a 60s run window | ⚠️ script exists; CI not yet calling it on every PR | A PR opens with a deliberate crash injection → emu-ci-run reports FATAL → PR fails |
| 0.4 | `MainActivity` launches and renders the Home destination of Nav3 without exception | ✅ build succeeds; live behavior unverified on emu | emu-ci-run launches MainActivity for 30s; no `FATAL` in logcat; screen.mp4 shows non-blank Home screen |
| 0.5 | Fresh clone → `scripts/provision-from-recovery.sh` → `./gradlew assembleDebug` → APK launches | ⚠️ provision script smoke-tested; full new-machine flow not yet validated | New CI job: clone FM, run provision, build, push to emu, launch — full chain green |
| 0.6 | All gitignored recovered binaries SHA-256-verified against `model-manifest.json` and `verify-soda-assets.sh` | ✅ scripts/download-models.sh --verify-only passes locally | emu-ci-run does NOT re-verify (binaries are in the APK), but the PR-side `scripts/verify-phase6-release-gates.sh` does |

**Tier 0 acceptance:** every PR that lands main runs through 0.1–0.4 in CI
and the result is green.

---

### 3.2 Tier 1 — One end-to-end user flow (the "is this even alive" gate)

The minimum a user must experience without an engineer in the room: ask FM
to set a timer, see confirmation, see the timer get set.

| # | Item | Status | Emu gate |
|---|---|---|---|
| 1.1 | First-launch permission rationale flow: app starts, asks for mic + notifications + accessibility with clear rationale text, handles deny + don't-ask-again | ⚠️ helper exists; full flow validation pending | emu-ci-run launches; instrumented test grants permissions programmatically; screen.mp4 shows rationale dialog before any permission ask |
| 1.2 | Text input on Home: user types "set a timer for 10 minutes" → `FulfillmentOrchestrator.handleUserQuery` runs | ⚠️ Fulfillment wired (architecture.md §6); end-to-end from Home not verified on emu | emu run + instrumented test simulates text input → logcat shows `Gatekeeper:` BERT classification → `Fulfillment:` action parsed → confirmation surface rendered |
| 1.3 | Side-effect confirmation: timer skill returns `SideEffectPending` → user taps Confirm → Android Clock provider receives `ACTION_SET_TIMER` → audit trail records the apply | ⚠️ skill registry wired; confirmation UI may not surface from a real `FulfillmentResult` | emu run + instrumented test confirms the action → adb shell query Clock provider shows new timer; `ActionsRepository` history grows by one entry |
| 1.4 | Voice input on Home: user speaks (instrumented `MediaRecorder` fixture audio) → SODA transcribes → Gatekeeper classifies → same flow as 1.2 | ⚠️ SODA wired on-device but not validated end-to-end via emu (emu lacks mic; need fixture-audio inject) | emu run plays a WAV fixture through `GatekeeperDebugReceiver` `ACTION_INJECT_AUDIO` → SODA produces transcript → same end of 1.2/1.3 |
| 1.5 | State persists across `am force-stop`: timer history, settings, permission grants survive | ⚠️ DataStore + SQLCipher wired; round-trip-across-kill not regression-tested | emu run: do flow 1.1–1.3, `am force-stop` the app, relaunch, query `ActionsRepository.history()` via debug receiver → previous timer entry present |
| 1.6 | App handles airplane mode without crash (local-LLM-only path stays alive) | ⚠️ NetworkAwareLLM degradation path exists; not validated via emu | emu run: simulate airplane mode (`adb shell svc data disable`); do flow 1.2 with a query that AICore can answer locally; no `LlmUnavailableException` for both lanes |

**Tier 1 acceptance:** an unscripted user installs the APK, grants permissions
through the rationale flow, sets a timer by voice, sees it confirmed and
applied. The emu run captures the whole sequence as a single video.

---

### 3.3 Tier 2 — The parity demo (all 9 skills + recall + privacy)

This is M2 + M3 of the original milestone plan. The user can demo the full
"ambient agent" pitch.

| # | Item | Status | Emu gate |
|---|---|---|---|
| 2.1 | 9 parity skills end-to-end: list (add to grocery list), timer, calendar draft, doc draft, web search, jargon definitions, insight, recall, conversation summary | ⚠️ skill registry wired; each subtype's executor is single-handler stub today (architecture.md §6.1) — needs per-subtype split | emu run: instrumented test invokes each of 9 skills in turn → each emits `SideEffectApplied` or `Result` event in `ActionsRepository` |
| 2.2 | Ambient mic capture via SODA: speech detected, Magic Mic directedness gate fires, transcript flows into `@AmbientAudioEphemeralBuffer` | ✅ on-device wired; emu validation needed (mic-fixture inject) | emu run: inject directed speech fixture → buffer count increases by 1; inject undirected speech → buffer count unchanged (gate suppressed) |
| 2.3 | Notification capture: `NotificationListenerService` receives a posted notification → flows into `@NotificationEphemeralBuffer` | ✅ wired; emu validation needed | emu run: `adb shell cmd notification post` a notification → `@NotificationEphemeralBuffer` count increases |
| 2.4 | Screen text capture: AccessibilityService observes screen text → flows into `@ScreenEphemeralBuffer` | ✅ wired; emu validation needed | emu run: launch a known activity with known text → buffer contains that text |
| 2.5 | Recall: user asks "what was that thing about <topic>?" → `ContextStore.queryByText` returns relevant events with timestamps + source attribution | ⚠️ `queryByText` works; semantic `queryByEmbedding` is no-op until Gecko (deferred per fallback-paths.md) | emu run: pre-seed buffer + ContextStore with known events → ask recall query → response includes the seeded text + timestamp |
| 2.6 | Disabled-source privacy enforcement: with source X off, generate N events → assert 0 reach store AND 0 reach any prompt | 🔵 the 4 must-pass privacy tests (PLAN.md §12) are listed but instrumented forms not all written | emu run runs `DisabledSourcePrivacyTest` (new); it must be green |
| 2.7 | Retention sweep: events older than `getEphemeralBufferDurationMin()` are removed on next sweep | ⚠️ eviction policy in `InMemoryEphemeralBuffer`; ContextStore retention sweep status unknown | emu run + time-control fixture (override `Clock` injection) inserts old + recent events → trigger sweep → only recent remain |
| 2.8 | Fulfillment routing modes: Local-only, Cloud-only, Hybrid each work; mode toggle persists; routing logs the choice | ⚠️ NetworkAwareLLM exists; the 3-mode toggle in Settings may or may not be wired | emu run: toggle each mode, do a query → `LLMStatsRepository` shows the correct route taken |
| 2.9 | "Forget last 10 minutes" action removes matching events from store + in-flight buffers | 🔵 not built | emu run: insert recent events → invoke forget-window → buffer + ContextStore both clean |

**Tier 2 acceptance:** the emu can replay the 6-flow demo script (PLAN.md §11)
end-to-end without engineer intervention. The screen recording matches the
expected per-flow behavior.

---

### 3.4 Tier 3 — Presence surface (the user-visible UX)

The orb / tab / card stack is what the user looks at during ambient use.
Without it FM is a settings screen, not an assistant.

| # | Item | Status | Emu gate |
|---|---|---|---|
| 3.1 | Orb surface renders parked at screen edge with idle/listening/dismissed visuals (`presence/surfaces/OrbSurface.kt`) | ✅ Compose surface exists; emu render unverified | emu run launches a debug activity that renders `OrbSurface(state=Parked)` → screen.mp4 shows the orb in expected position with the design's aerospace palette |
| 3.2 | Tab surface expands from orb on tap; cards render for `DeepResearch`, `SendResults`, `CalendarTimer` payloads | ✅ surfaces exist; tap-expand behavior unverified | emu run: simulate tap on orb → screen.mp4 shows tab expanded with 3 stub cards |
| 3.3 | Presence state driver: a producer observes `Gatekeeper.results` + `IntentBuffer` + `FulfillmentEvent` and emits `PresenceState` transitions; surfaces collect from a single `StateFlow<PresenceState>` | ⚠️ surfaces render against `PresenceState`; the producer/orchestrator is the missing piece (`architecture.md §13.2`) | emu run + instrumented harness: post a `GatekeepingResult` to a fake gatekeeper → assert `PresenceState` flow emits `Working(taskId, STARTED)` |
| 3.4 | Dismiss + snooze + reopen: user can dismiss a card for N minutes; reopen on demand | ⚠️ `Dismissed(until)` state exists; dismiss control wiring unverified | emu run: dismiss a Ready card → state transitions to Dismissed → after time-control advance, returns to Parked |
| 3.5 | Card payload library covers all 9 parity skills (today: 3 payloads exist — `DeepResearch`, `SendResults`, `CalendarTimer`) | ⚠️ payload-per-skill mapping incomplete | emu run renders each of 9 skill-result payloads → screen.mp4 shows each cleanly |
| 3.6 | Glass / projected XR rendering wired (`GlassPanel` exists; `CosmoProjectedGlassCaptureService` binds; producer from `PresenceState` not driven today) | 🟡 stays deferred for v1; slot reserved | (deferred; no v1 gate) |

**Tier 3 acceptance:** the emu can run a 30-second loop where the orb parks
→ listens → shows a Working pill → reveals a Ready card → user dismisses →
orb returns to parked. Recorded as a single video for the demo.

---

### 3.5 Tier 4 — LLM lane completeness (so hybrid mode actually works)

Three independent LLM lanes, each with a clean failover and a typed
`LlmUnavailableException` on missing capability.

| # | Item | Status | Emu gate |
|---|---|---|---|
| 4.1 | AICore (Gemini Nano) generates on a Pixel target; emu falls back to remote | ✅ wrapper exists; Pixel validation pending hardware | emu run: no AICore available → `LlmUnavailableException(AICORE_NOT_INSTALLED)` → routing falls to next lane |
| 4.2 | LiteRT-LM bundle: `scripts/provision-gemma4-litertlm.sh` downloads the official `litert-community/gemma-4-E4B-it-litert-lm` bundle; `LiteRtLmLLM` loads it on emu | ⚠️ bundle not provisioned by default; storage-id derivation works | emu run: pre-provision the bundle → `LiteRtLmLLM.isAvailable` true → a query routes through it → response has `route=LiteRtLm` in `LLMStatsRepository` |
| 4.3 | Remote inference: smallest server stand-up = `/v1/private-token/issue` (PrivateToken issuer, ~150 LOC FastAPI) + `/v1/inference` (verifies signature + token, runs LLM, returns SSE) | 🔵 client wired; no server | emu run with a local test server (loopback `BuildConfig.REMOTE_INFERENCE_BASE_URL` = `http://10.0.2.2:8080`) → request signed correctly → response parsed |
| 4.4 | Network-aware router: airplane mode → Local; network restored → Hybrid; mode change logs route + reason | ⚠️ logic exists; emu validation needed | emu run: airplane on → Local route; airplane off → Hybrid route picked; `LLMStatsRepository` shows both routes taken with reasons |
| 4.5 | All 3 lanes fail clean: `LlmUnavailableException` with a specific `LlmFailureCause` for each kind of unavailability (not installed, no model, network, auth, timeout, bad cert, configuration) | ✅ exception classes exist; coverage of all paths in tests pending | emu run: instrumented tests force each `LlmFailureCause` → assert correct exception caught + correct next-lane attempt |

**Tier 4 acceptance:** the emu can demonstrate hybrid mode under three
network conditions (full network, metered network, airplane mode) and the
right lane is chosen each time, logged with the reason.

---

### 3.6 Tier 5 — Production hardening (before "ask someone to use this")

| # | Item | Status | Emu gate |
|---|---|---|---|
| 5.1 | All 4 must-pass privacy tests green (PLAN.md §12): disabled-source, retention, local-only-no-egress, forget-window | 🔵 listed; not all instrumented | emu run runs the privacy test suite as instrumented tests → all green |
| 5.2 | Battery drain test: 24h ambient run on real device < 3% battery loss per day | 🔵 `scripts/run-perfetto-battery.sh` exists; baseline measurement not captured | (real-device gate; emu can't measure) |
| 5.3 | Memory pressure trim: `Application.onTrimMemory(TRIM_MEMORY_RUNNING_LOW/CRITICAL)` clears byte buffers; `_BACKGROUND` clears writable ephemeral buffers | ✅ `MemoryPressureTrimmer` wired | emu run: `adb shell am send-trim-memory <pkg> RUNNING_CRITICAL` → `lib/performance/MemoryPressureTrimmer:` log line confirms clear |
| 5.4 | ANR watchdog (debug only): 5s main-thread stall triggers stack dump | ✅ `DebugDiagnostics` wired | emu run: inject a deliberate 6s stall → screen.mp4 shows no ANR popup (watchdog dumps before system ANR triggers); log has watchdog stack |
| 5.5 | StrictMode (debug only): 0 violations in a 30-min monkey test | ✅ wired; monkey test not regression-run | emu run: `adb shell monkey -p <pkg> 1000` → logcat has 0 `StrictMode` violations |
| 5.6 | R8 release build green; APK ≤ 45 MB after minification; no missing-class warnings | ✅ `:app:assembleRelease` working; APK size healthy | emu run installs `releaseInternal` APK (release minification, debug signing); launches; no missing-class crashes |
| 5.7 | Cert pinning + cleartext disabled (`AndroidManifest.xml usesCleartextTraffic="false"`) + non-launcher activities not exported | ✅ wired | emu run: instrumented test attempts cleartext HTTP — fails as expected |
| 5.8 | Permission posture passes a Play Store policy self-check: every dangerous permission has rationale, accessibility service shows justification, foreground service has correct type bitmask | ⚠️ posture mostly correct; self-check not formalized | (no emu gate; CI script `scripts/verify-phase6-release-gates.sh` covers the static checks) |
| 5.9 | Encrypted ContextStore (Room + SQLCipher) round-trips data; backup-extraction yields ciphertext only | ✅ wired | emu run: instrumented test inserts → adb pull the `.db` → `file` reports SQLCipher header / data not readable as plain SQLite |
| 5.10 | All 29 manifest permissions requested with rationale; none silently held without UI | ⚠️ `PermissionRationaleHelper` exists; full coverage audit pending | emu run + audit script: every `<uses-permission>` matches a UI rationale entry |

**Tier 5 acceptance:** the emu can run the full demo (Tier 1+2+3) for 30+
minutes with monkey load and no FATAL/ANR/StrictMode/cleartext-egress
violations.

---

### 3.7 Tier 6 — Pre-v1 nice-to-haves (in priority order)

These improve the experience but **don't block "ask someone to use it."**
Ordered by leverage per `docs/fallback-paths.md` triage table.

| # | Item | Effort | Status | Emu gate |
|---|---|---|---|---|
| 6.1 | Voice Match enrollment UI (cosmo assets + JNI already ported — only UI missing) | ~1 day | 🔵 | emu run: launch enrollment flow → speak 3 sample utterances → `SodaEnrollmentManagerImpl.nativeGetSpeakerInfo` returns non-empty bytes; `TranscriptionData.speakerLabel` populated on subsequent ambient transcript |
| 6.2 | CaMeL trust layer — Plan DSL variant (foundational types: `Capabilities`, `Value<T>`, `SecurityPolicyEngine`, `QuarantinedLlmService`, `PrivilegedLlmService`, then interpreter) | ~2 engineer-weeks | 🔵 | emu run: a tainted-source query (notification text) attempts a state-changing tool call → `SecurityPolicyDeniedError` surfaces in the CaMeLAgent loop; the action does NOT execute |
| 6.3 | mt6989 BERT variant: run the drafted AOT pipeline on a machine with the MediaTek Neuron toolchain | ~1 day on toolchain machine | 🔵 | (compile-time; emu validates by loading the new variant when SOC reports `MT6989`) |
| 6.4 | Conformer EdgeTPU activation on Pixel (flip `isAvailable=false` → real probe) | ~1 day after Pixel arrives | 🔵 (gated on hardware) | (real-device gate only; emu lacks Tensor Darwinn) |
| 6.5 | AOC nanoapp transport (platform-signed compile or bytecode-gen for `ContextHubClientCallback` subclass) | ~1 day after platform-signing access | 🔵 (gated on signing) | (platform-signed gate; emu may not expose Context Hub) |
| 6.6 | Inherent joint model delivery (`~/inherent` ships `inherent.tflite` + `inherent.metadata.json`) | 5–6 weeks per inherent project plan | ⚠️ in progress | emu run: provision new assets → `InherentBackend.isAvailable=true` → routing picks it on appropriate query |
| 6.7 | Per-subtype action handler split (`FulfillmentActionHandlerImpl` → 22 dedicated handlers) | ~1 week | ⚠️ central handler today | emu run: each of 22 action types invokes its dedicated handler → none falls through to a generic catch |
| 6.8 | Mariner browsing-agent backend (tier-2 Browser-Use behind §15.4 transport) | ~1 week | 🔵 | emu run: invoke a `BrowsingAgent` action → TaskMonitor surface shows incremental `MarinerState` updates → final answer returned |
| 6.9 | Real Pixel hardware integration full sweep (every Tier 0–5 item validated on real device, not just emu) | ~1 day after Pixel arrives | 🔵 | (real-device gate; emu cannot validate; this is when the "soon to also be a real device" half of §3.0 activates) |

---

### 3.8 Out of scope for v1 (do NOT work on these)

These have explicit scope cuts in `docs/architecture.md §13 / §15.3` and
`docs/fallback-paths.md`. Pulling them in delays "ask someone to use this."

| Surface | Why out of scope |
|---|---|
| **Voice generation / TTS** (VoiceLM, Vogo, SoundStream, LocalTTS, AudioRenderer) | Deferred per `docs/architecture.md §16`. If a v1 user-facing TTS need lands, tier-2 fallback is `android.speech.tts.TextToSpeech` (~2 days). VoiceLM tier-1 requires re-recovering the truncated `temporal_decoder` asset |
| **Workspace grounding** (server fetch of Gmail/Calendar/Drive) | Replaced with on-device Android content providers; user owns the read path |
| **PI (Personal Intelligence) service** | Replaced with on-device `ContextStore` |
| **Server-side prompt-injection classifier** | Replaced with on-device CaMeL (Tier 6.2) |
| **Gecko semantic search over ContextStore** | Architectural hook exists (`queryByEmbedding`); model + impl deferred. `queryByText` works for v1 |
| **DBW server pipeline** (opt-in upload of accessibility-tree captures for distillation training) | Long-tail planned item per `docs/architecture.md §15.2`; client-side `AccessibilityForestCaptor` slot reserved |
| **Federated learning aggregation** | Not on parity capability set |
| **Multi-device fan-out** (assistant state mirrored across devices) | Not on parity; future workstream |
| **AgentQ-style ask-back service** | Google-internal name; generic LLM via `lib/llm/` replaces |

---

### 3.9 The single decision tree

For anyone picking up work, the order is:

```
Is Tier 0 green in CI?            → If no, fix Tier 0 first. Nothing else matters.
Is Tier 1 green on emu?           → If no, work on Tier 1. The minimum bar.
Is Tier 2 green on emu?           → If no, work on Tier 2. The parity demo.
Is Tier 3 green on emu?           → If no, work on Tier 3. The user-visible UX.
Is Tier 4 green on emu?           → If no, work on Tier 4. Routing across LLM lanes.
Is Tier 5 green on emu?           → If no, work on Tier 5. Production hardening.
Otherwise → pick from Tier 6 by leverage. Voice Match enrollment is the
            highest-leverage 1-day win; CaMeL is the highest-value 2-week
            investment.
```

**Don't skip tiers.** If Tier 1 is failing on emu, do not start CaMeL.

---

## 4. Workstream structure

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

## 5. Interface contracts

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

## 6. Current snapshot (2026-05-15)

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

## 7. Active blockers

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

## 8. Security threat model

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

## 9. LLM / model delivery architecture

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

## 10. Build / test / ship gates

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

## 11. Demo script

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

## 12. Privacy enforcement (must-pass tests)

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

## 13. Reference-only material (what NOT to use the APK recovery for)

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

## 14. Out of scope

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

## 15. Cross-reference index

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

## 16. Update protocol

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

The **Ship Checklist (§3) is the source of truth for "what to work on next."**
Update tier statuses there as items go green on the emu gate.
