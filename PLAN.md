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

As of 2026-05-16, Steps 1 and 2 are green on `codex/ship-plan-audit`.

- Tier 0 passes locally:
  `scripts/verify-relocation-safety.sh`,
  `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug`,
  and `./gradlew :app:assembleReleaseInternal`.
- CI now runs the hard gates without `|| true`; current remote runs are green
  through `c2c0bd8`, with later emulator-proof commits still running.
- Home has a free-text Ask input wired to
  `FulfillmentOrchestrator.handleUserQuery`.
- Debug/test builds have a deterministic `SimulationLLM` path for the timer
  flow.
- The timer flow passed in remote emulator job
  `e872fb0d19e44808ba290684f1eaecf6`; artifacts are in
  `artifacts/timer-flow-sqlcipher-fixed/job-e872fb0d19e44808ba290684f1eaecf6/`.
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

The next milestone is a deterministic parity fixture suite on the same action
path as the timer flow. Do not fork into skill-specific executors.

Done means these flows all prove expected `FulfillmentAction` output and
resulting UI/history state:

- Timer
- List
- Calendar draft
- Document draft
- Web search
- Define term / jargon
- Insight
- Recall
- Summary

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

Status: Next.

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

Four tests must pass:

- Disabled source reaches neither `ContextStore` nor prompt context.
- Retention sweep deletes old data.
- Local-only mode has no network egress.
- Forget-window deletes matching `ContextStore` rows and in-flight buffers.

### 5. Wire Presence

Owner: presence owner.

Start only after Step 2 is green.

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

## Do Not Work On Yet

These are parked until Steps 1 and 2 are green:

- CaMeL.
- Mariner/browser backend.
- Per-action handler split.
- Voice Match enrollment UI.
- Pixel-only EdgeTPU/AOC activation.
- Inherent model integration.
- Remote inference production server.
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
