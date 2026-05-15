# Cosmo Interface Contracts

These three contracts are the seams that make four parallel workstreams viable. They must land in Week 1, before any feature code that crosses a workstream boundary.

| # | Contract | Producer | Consumers |
|---|---|---|---|
| 01 | [ContextStore API](01_context_store_api.md) | WS2 | WS3, WS4, WS1 |
| 02 | [Fulfillment API](02_fulfillment_api.md) | WS3 | WS1, WS4 |
| 03 | [Skill Registry API](03_skill_registry_api.md) | WS4 | WS1, WS3 |

## Rules

- Producer owns the file. Changes are PRs labeled `interface`, requiring sign-off from every consumer listed above.
- Additive changes (new fields, new enum cases consumers can ignore) do not need sign-off but must still be PR'd here.
- Schemas in code (Kotlin data classes, Room entities, JSON Schemas) must match this folder. If they drift, this folder is the source of truth and the code is wrong.
- Each invariant marked "verified by test" must have a corresponding instrumented test by the milestone it gates.

## Not in this folder

- Permission/settings observer (WS1 → all): trivial, lives as a single Kotlin interface in WS1's gradle module. Documented inline.
- Routing decision callback (WS3 → WS4): subsumed by `FulfillmentEvent.RouteDecided` in contract 02.
