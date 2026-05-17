# FM Plan

This is the only day-to-day plan.

Other docs are reference material. Do not create new workstream or contract
documents until the first timer flow works end to end.

## Goal

Ship a standalone Android ambient assistant that can:

- Accept a user query.
- Produce a useful answer or action.
- Confirm side effects before execution.
- Capture permitted context and recall it.
- Enforce privacy toggles in code, not just in UI.

The recovered cosmo APK and `$COSMO_DEV_ROOT` are reference evidence only. They
can answer "what did cosmo do?" They are not a work queue.

## Current Truth

As of 2026-05-17, Steps 1 through 5 are green on `codex/ship-plan-audit`.

- Tier 0 passes locally:
  `scripts/verify-relocation-safety.sh`,
  `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug`,
  and `./gradlew :app:assembleReleaseInternal`.
- CI now runs the hard gates without `|| true`; remote runs are green through
  plan-status commit `9aad34a`, with presence commit `8a86884` running.
- Home has a free-text Ask input wired to
  `FulfillmentOrchestrator.handleUserQuery`.
- Debug/test builds have a deterministic `SimulationLLM` path for the timer
  flow.
- The timer flow passed in remote emulator job
  `e872fb0d19e44808ba290684f1eaecf6`; artifacts are in
  `artifacts/timer-flow-sqlcipher-fixed/job-e872fb0d19e44808ba290684f1eaecf6/`.
- Xiaomi fallback-lane production model routing passed on device
  `2506BPN68G` (`Xiaomi/klimt_eea/klimt:16/BP2A.250605.031.A3/OS3.0.13.0.WOSEUXM:user/release-keys`)
  with app-private `google-gemma-4-E2B-it-litertlm` LiteRT-LM. Artifacts:
  `artifacts/device-model-routing/20260516-165925-fallback/`.
- Xiaomi core capability smoke passed on device with
  `scripts/run-device-core-capability-smoke.sh`: gatekeeper timer detection,
  debug/test fulfillment actions for timer/list/doc/calendar/search/email,
  Presence `Ready`, local memory action execution, and SODA native startup with
  injected PCM. Artifacts:
  `artifacts/device-core-capability/20260516-172858/`.
- The same core smoke can push recorded phrases through SODA:
  `scripts/run-device-core-capability-smoke.sh /path/to/phrase.wav`.
- The generated fixture corpus passed on emulator with 10/10 speech phrases
  producing final SODA transcripts and expected regex skill routing. Artifacts:
  `artifacts/audio-run/20260517-161257/`. Real human-recorded phrases are still
  needed once a phone is available.
- The raw Conformer/Darwinn audio debug path now fails closed on emulator with
  `raw_audio_native_unsupported_on_emulator` instead of crashing in
  `libtensorflowlite_jni.so`; Pixel hardware is still required for EdgeTPU
  proof.
- The existing path to ship next remains:

```text
Home input
  -> FulfillmentOrchestrator
  -> FulfillmentRunner
  -> FulfillmentAction
  -> ActionsRepository
  -> ActionDeck confirmation
```

Use that path. Do not add a parallel skill/executor architecture unless a
failing acceptance test proves the existing path cannot ship.

## Next Milestone

Production LLM and real-device validation are one milestone. We are not adding
a second model architecture; we are proving the existing `LLM -> @LocalLLM /
@ServerLLM / @HybridLLM` tree on hardware.

### Production LLM Routing Tree

The app has one production tree and two validation lanes.

```text
Production caller
  -> @HybridLLM / NetworkAwareLLM
  -> local first
       -> AICore public GenerativeModel
       -> ML Kit GenAI Prompt
       -> LiteRT-LM
  -> remote fallback only if local fails before tokens and network policy allows it
       -> RemoteInferenceLLM
```

Important constraints:

- Debug UI flow uses `SimulationLLM`; it is not production LLM evidence.
- A Pixel 10 with Nano v3 should not need LiteRT-LM for the text happy path if
  AICore initializes and generates.
- The fallback lane is for every device where Nano v3, AICore, or recovered
  acceleration is unavailable or fails, including the Pixel 10.
- Non-Nano phones need at least one real fallback: ML Kit GenAI Prompt,
  provisioned LiteRT-LM, or configured remote inference. For offline/local-only
  non-Nano validation, LiteRT-LM is required unless ML Kit is proven available
  and good enough on that device.
- Recovered SODA, BERT, Conformer EdgeTPU, AOC, and Inherent assets are
  gatekeeping/capture paths. They are not substitutes for a production LLM.

### Device Lanes

Lane A, Pixel 10 happy path:

- Command: `scripts/run-device-model-routing-smoke.sh pixel10`
- Require AICore visibility.
- Run the production `@HybridLLM` smoke and record path, reason, model output,
  build fingerprint, and model identifier.
- Probe recovered Pixel-only acceleration (EdgeTPU Conformer / AOC / Nano v3)
  where available, but do not let it block the fallback lane.

Lane B, fallback path:

Status: Done on Xiaomi with app-private E2B LiteRT-LM. Still rerun this lane
on Pixel 10 if Lane A fails.

- Command: `scripts/run-device-model-routing-smoke.sh fallback`
- Local-only fallback command:
  `DEPLOY_LITERTLM=1 scripts/run-device-model-routing-smoke.sh fallback`
- Run on Xiaomi and also on Pixel 10 if Lane A fails.
- Do not require AICore.
- Prove ML Kit, LiteRT-LM, or remote inference produces a non-empty response
  through the same `@HybridLLM` route.
- If this lane is intended to be local-only, deploy a LiteRT-LM bundle first
  and prove no remote route was used.

Done when both lanes have artifacts and logs, or when a failed lane has an
explicit unavailable reason and the other lane still gives us a shippable demo
path.

## Work Order

Do these in order. Do not start a lower item while a higher item is red.

### 1. Make The Repo Honest

Owner: build captain.

Status: Done on `codex/ship-plan-audit`.

Fix the gates that currently fail:

- `scripts/verify-relocation-safety.sh`
- `./gradlew detekt`
- `./gradlew lintDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleReleaseInternal`

Then update CI so these checks fail the PR instead of being hidden behind
`|| true`.

Done when:

```bash
scripts/verify-relocation-safety.sh
./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleReleaseInternal
```

all pass locally and in CI.

### 2. Ship One User Flow

Owner: app-flow owner.

Status: Done on `codex/ship-plan-audit`; remote emulator job
`e872fb0d19e44808ba290684f1eaecf6` captured the timer flow.

Implement only what is needed for the timer flow:

- Add a Home free-text Ask input.
- Add a deterministic debug/test LLM binding or fixture.
- Route the submitted text through `FulfillmentOrchestrator.handleUserQuery`.
- Produce `FulfillmentAction.AddTimer`.
- Show the existing ActionDeck confirmation.
- Confirm and mark the action executed.

Done when the milestone flow above passes in an emulator run.

### 3. Validate The Parity Demo On The Existing Action Path

Owner: validation owner.

Status: Done on `codex/ship-plan-audit`; the nine parity fixtures pass through
`FulfillmentRunner -> ActionsRepository -> fulfillmentHistory`.

Add deterministic fixtures for these flows:

- Timer
- List
- Calendar draft
- Document draft
- Web search
- Define term / jargon
- Insight
- Recall
- Summary

Each fixture should prove the expected `FulfillmentAction` and resulting
UI/history state. Keep the centralized action handler unless a concrete test
failure shows it blocks shipping.

### 4. Prove Privacy

Owner: validation owner with WS2/WS3 help.

Status: Done on `codex/ship-plan-audit`; commit `a751bdb` adds automated tests
for disabled-source persistence/prompt gating, retention sweep, local-only
network egress, and forget-window deletion.

Four tests must pass:

- Disabled source reaches neither `ContextStore` nor prompt context.
- Retention sweep deletes old data.
- Local-only mode has no network egress.
- Forget-window deletes matching `ContextStore` rows and in-flight buffers.

### 5. Wire Presence

Owner: presence owner.

Status: Done on `codex/ship-plan-audit`; commit `8a86884` drives
`PresenceState` from `ActionsRepository` plus Gatekeeper output and maps
`FulfillmentAction` to `PresenceCard` while leaving ActionDeck authoritative for
confirmation.

- Drive `PresenceState` from `ActionsRepository` and Gatekeeper output.
- Map `FulfillmentAction` to `PresenceCard`.
- Keep ActionDeck as the source of truth for side-effect confirmation until
  Presence proves equivalent behavior.

## Active Contracts

These are the only contracts that matter right now. Keep them small and update
them only when an implementation PR proves the contract wrong.

### Build Contract

Owned by the build captain.

- Inputs: existing Gradle project, GitHub Actions workflow, model/recovery
  verification scripts.
- Allowed changes: CI gating, lint/detekt fixes, test drift fixes, R8 keep or
  dependency fixes, relocation-safe doc/script paths.
- Must not change: product behavior, action schemas, capture semantics.
- Done means the commands in Step 1 pass locally and in CI, with no `|| true`
  masking for detekt, lint, or unit tests.

### Timer Flow Contract

Owned by the app-flow owner.

- Input UI: Home accepts arbitrary user text and submits it once per explicit
  send action.
- LLM seam: debug/test builds have a deterministic path that maps "set a timer
  for 10 minutes" to an `add_timer` JSON response.
- Fulfillment seam: `FulfillmentOrchestrator.handleUserQuery(query, history)`
  receives the exact submitted text; `FulfillmentRunner` parses it into
  `FulfillmentAction.AddTimer`.
- UI seam: `ActionsRepository.userFacingActions` drives the existing ActionDeck
  card; confirmation calls `FulfillmentActionHandler.execute`.
- Done means the emulator recording shows typed input, action card, confirm,
  and executed state.

### Parity Fixture Contract

Owned by the validation owner.

- Use the existing `FulfillmentAction` model.
- Each fixture declares: query, expected action subtype, required fields, and
  expected UI/history state.
- Fixtures must cover timer, list, calendar draft, document draft, web search,
  define term, insight, recall, and summary.
- Must not require a per-skill executor split.
- Done means all nine fixtures pass against the same path as the timer flow.

### Privacy Contract

Owned by the validation owner with WS2/WS3.

- Disabled source: when a source is off, generated events from that source do
  not reach `ContextStore` and are not included in model prompt context.
- Retention: old rows and old in-memory entries are deleted according to the
  configured retention window.
- Local-only: a scripted local-only query has no network egress.
- Forget-window: the requested time range is removed from both persistent
  context and in-flight buffers.
- Done means these are automated tests, not manual inspection.

### Presence Contract

Owned by the presence owner after Step 2 is green.

- Source of truth: `ActionsRepository` for fulfillment results and
  Gatekeeper output for listening/working state.
- Mapping: `FulfillmentAction -> PresenceCard`; no new action schema.
- Confirmation: Presence may mirror ActionDeck confirmation, but ActionDeck
  remains authoritative until parity is proven by the same timer-flow test.
- Done means Presence can be disabled and the timer flow still ships.

## Parked Work

These are parked unless a concrete ship-blocking test pulls them in:

- CaMeL.
- Mariner/browser backend.
- Per-action handler split.
- Voice Match enrollment UI.
- Pixel-only EdgeTPU/AOC activation beyond the Lane A probe.
- Inherent model integration.
- First-party provider integrations beyond the existing Android intents, web
  search, text drafts, and TaskMonitor fallbacks.
- Remote inference production server unless it becomes the chosen Lane B
  fallback.
- New contract documents.
- Recovered APK repair.

## Team Split

- **Build captain:** Step 1 only.
- **App-flow owner:** Step 2 only.
- **Validation owner:** Steps 3 and 4.
- **Presence owner:** waits until Step 2 is green, then owns Step 5.

One rotating release captain owns the current demo APK and decides whether a
PR moves the milestone forward or adds scope.

## Verification Facts From The Audit

These facts were checked on `main` / the audit branch on 2026-05-15:

- `scripts/verify-relocation-safety.sh` failed on checked-in developer-local
  recovery paths; this branch replaces them with repo references or
  `COSMO_DEV_ROOT` / `INHERENT_ROOT`.
- `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug`
  fails immediately at detekt with 15 weighted issues.
- `./gradlew :app:testDebugUnitTest` fails: 132 tests, 1 failed
  (`GatekeeperModelManifestDriftTest`).
- `./gradlew lintDebug` fails with 14 errors; first failure is
  `AudioCaptors.kt:224` missing permission handling.
- `./gradlew :app:assembleReleaseInternal` fails in
  `minifyReleaseInternalWithR8` on missing SODA/Flogger/protobuf/desugar
  classes.
- `scripts/verify-architecture-delta.sh` passes.
- `scripts/download-models.sh --verify-only --allow-missing-binaries` passes.

## Reference Docs

Read these only when needed:

- `README.md` — quick start and repo overview.
- `LAYOUT.md` — where files live.
- `docs/architecture.md` — subsystem reference.
- `docs/architecture-delta.md` — C1-C9 constraints and historical PR phases.
- `docs/fallback-paths.md` — fallback options for deferred systems.
- `reference/` — mirrored recovery maps and historical contracts.

If any reference doc conflicts with this file, this file wins for what to work
on next.
