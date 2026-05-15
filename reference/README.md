# Reference Mirror

Read-only snapshots of cosmo-recovery + inherent docs that `~/neural` cites.
Sourced from `~/cosmo-dev/repo/docs/` and `~/inherent/` via
`scripts/sync-reference-docs.sh`.

**Do not edit files here directly** — they're mirrors. Edit at source and
re-sync, or open a PR against the canonical doc and re-sync once it lands.

## What's here

### Cosmo recovery stack maps

| File | What it contains |
|---|---|
| `cosmo-architecture.md` | Upstream cosmo system architecture (179 lines) — the doc that `docs/architecture-delta.md` is a *delta against* |
| `cosmo-audio-stack-map.md` | Audio subsystem: SODA, Magic Mic, Voice Match, Hotword, Diarization, VoiceLM/TTS, Vogo, recovered asset byte counts |
| `cosmo-text-stack-map.md` | Text subsystem: BERT classifier, regex, Gecko embedder, screen-text capture |
| `cosmo-image-stack-map.md` | Camera + image-analysis capture path, including recovered `assets/gecko/` and `assets/voicelm/` paths |
| `cosmo-llm-stack-map.md` | LLM clients, routing, AICore + Aratea + LiteRT-LM call shape |
| `cosmo-ui-surface-map.md` | UI activities + services + Compose surfaces |
| `cosmo-dagger-graph-map.md` | Hilt component tree + 73 @Provides/@Binds edges + 33 @Qualifier annotations |
| `cosmo-settings-and-config-map.md` | 60-field `CosmoSetting` proto + 27 `CosmoConfiguration` flags |
| `cosmo-telemetry-map.md` | Firebase event catalog + Flogger tags |
| `cosmo-reachability-matrix.md` | DMCA §1201(f) reachability per fulfillment action |
| `cosmo-runtime-topology-map.md` | Process model, services, broadcast receivers, foreground service types |
| `cosmo-bert-mt6989-aot-pipeline.md` | MediaTek Dimensity 9300 (mt6989) AOT compile pipeline for the BERT gatekeeper (referenced from `docs/fallback-paths.md` mt6989 tier-1) |

### Inherent (joint audio→intent training pipeline)

| File | What it contains |
|---|---|
| `inherent-runtime-contract.md` | Tensor shapes, head order, threshold sidecar — the load-bearing interface between `~/inherent` and `InherentBackend` |
| `inherent-audio-collection.md` | Audio corpus collection pipeline + label schema |

### Workstream interface contracts

`contracts/` — the four parallel-workstream API contracts:

| File | Owner | What |
|---|---|---|
| `contracts/00_README.md` | All | Rules, ownership, sign-off |
| `contracts/01_context_store_api.md` | WS2 | Insert / query-by-time / query-by-embedding / delete / source-filter, plus invariants |
| `contracts/02_fulfillment_api.md` | WS3 | Streaming request → answer with mode + path telemetry |
| `contracts/03_skill_registry_api.md` | WS4 | Skill descriptors, invocation, confirmation, audit |

## Re-syncing

When the upstream source changes:

```bash
scripts/sync-reference-docs.sh         # full sync (overwrites local copies)
scripts/sync-reference-docs.sh --diff  # show what would change without copying
```

## What's intentionally NOT mirrored

- `~/cosmo-dev/repo/docs/marching-orders.md` — superseded by [`docs/architecture-delta.md`](../docs/architecture-delta.md) PR0–PR6
- `~/cosmo-dev/repo/docs/cosmo_apk_consolidated_plan.md` — superseded by [`PLAN.md`](../PLAN.md)
- `~/cosmo-dev/repo/docs/parallel-offload-plan.md` — historical work tracker; current state is in [`PLAN.md`](../PLAN.md)
- `~/cosmo-dev/repo/docs/implementation-status.md` — historical snapshot; current status is [`docs/architecture.md §19`](../docs/architecture.md)
- `~/cosmo-dev/repo/docs/blockers-and-mitigations.md` — superseded by [`docs/blocker-register.md`](../docs/blocker-register.md) and the current blockers in [`PLAN.md`](../PLAN.md)
- `~/cosmo-dev/repo/docs/security-threat-model.md` — superseded by the relevant security sections in [`docs/architecture.md`](../docs/architecture.md)
- `~/cosmo-dev/repo/docs/llm-model-delivery-architecture-review.md` — superseded by [`docs/architecture.md §9`](../docs/architecture.md)
- `~/cosmo-dev/WS4/docs/ws4-known-gaps.md` — superseded by [`docs/fallback-paths.md`](../docs/fallback-paths.md)
- `~/cosmo-dev/WS4/docs/ws4-demo.md` — superseded by the next milestone in [`PLAN.md`](../PLAN.md)

The "superseded" set is plan/design content that's been consolidated into FM-owned canonical docs. The "mirrored" set is genuinely cosmo-recovery reference material that FM cites but doesn't rewrite.
