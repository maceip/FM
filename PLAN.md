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

The recovered cosmo APK and `~/cosmo-dev` are reference evidence only. They
can answer "what did cosmo do?" They are not a work queue.

## Current Truth

As of 2026-05-15, FM does not yet have a shippable app flow.

- Tier 0 is red.
- Home has no free-text Ask input. The current Ask button sends a hardcoded
  query from `MainActivity.kt:642-646`.
- The default `LLM` binding is `@HybridLLM`, not `SimulationLLM`. On emulator,
  without AICore, LiteRT-LM, or remote inference config, fulfillment can fail
  closed with no actions.
- Release is not green: `:app:assembleReleaseInternal` fails in R8 on missing
  SODA/Flogger/protobuf/desugar classes.
- The existing path to ship first is:

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

The next milestone is not another plan. It is this recorded flow:

```text
clean install
  -> type "set a timer for 10 minutes"
  -> AddTimer action card appears
  -> user confirms
  -> action is marked executed
```

It must be captured with emulator logs and video, with Tier 0 checks green.

## Work Order

Do these in order. Do not start a lower item while a higher item is red.

### 1. Make The Repo Honest

Owner: build captain.

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

- `scripts/verify-relocation-safety.sh` fails on checked-in `~/cosmo-dev` and
  `/Users/mac/cosmo-dev` references.
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
