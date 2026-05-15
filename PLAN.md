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
