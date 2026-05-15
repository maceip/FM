# FM Ship Plan

This is the operating plan. Read this before `PLAN.md`.

`PLAN.md` is now the audit trail and reference backlog. It is not the daily
work queue.

## Current Truth

- We do not have a shippable app flow yet.
- Tier 0 is red: relocation safety, detekt, unit tests, lint, and release R8
  all need cleanup.
- Home has no free-text Ask input. The current Ask button sends a hardcoded
  prompt.
- Emulator fulfillment is not guaranteed because `SimulationLLM` is not the
  default app binding.
- The existing shipping path is:
  `Home input -> FulfillmentOrchestrator -> FulfillmentRunner -> FulfillmentAction -> ActionsRepository -> ActionDeck confirmation`.

Use that path. Do not add a parallel skill/executor architecture unless a
failing acceptance test proves the existing path cannot ship.

## Work Order

Do these in order. Do not start lower items while a higher item is red.

1. **Make the repo honest**
   - Fix `scripts/verify-relocation-safety.sh`.
   - Fix `detekt`, `lintDebug`, and `:app:testDebugUnitTest`.
   - Fix `:app:assembleReleaseInternal` R8 missing classes.
   - Then make CI fail on those checks instead of hiding them behind `|| true`.

2. **Ship one user flow**
   - Add Home free-text Ask input.
   - Add a deterministic debug/test LLM path or fixture.
   - Prove: type "set a timer for 10 minutes" -> AddTimer action appears ->
     user confirms -> action is marked executed.

3. **Validate the parity demo on the existing action path**
   - Add fixtures for: timer, list, calendar draft, doc draft, web search,
     define term, insight, recall, summary.
   - Each fixture proves the expected `FulfillmentAction` and UI/history state.

4. **Prove privacy**
   - Disabled source reaches neither ContextStore nor prompt context.
   - Retention sweep deletes old data.
   - Local-only mode has no network egress.
   - Forget-window deletes ContextStore rows and in-flight buffers.

5. **Only then wire Presence**
   - Drive `PresenceState` from `ActionsRepository` and Gatekeeper output.
   - Map `FulfillmentAction` to `PresenceCard`.
   - Do not block the timer flow on Presence.

## Explicit Non-Goals Until Steps 1-2 Are Green

- CaMeL.
- Mariner/browser backend.
- Per-action handler split.
- Voice Match enrollment UI.
- Pixel-only EdgeTPU/AOC activation.
- Inherent model integration.
- Remote inference production server.
- New contract documents.

## Team Split

- **Build captain:** Step 1 only. Owns CI honesty and release build.
- **App-flow owner:** Step 2 only. Owns Home input, debug LLM fixture, timer
  confirmation.
- **Validation owner:** Step 3 and Step 4 fixtures. Owns emulator proof and
  screen/log artifacts.
- **Presence owner:** waits until Step 2 is green; then owns Step 5.

## Acceptance Bar

The next real milestone is not "PLAN updated." It is:

```text
clean install -> type timer query -> action card appears -> confirm -> executed
```

Captured as an emulator run with logs and video, with Tier 0 checks green.
