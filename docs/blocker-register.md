# Genesis Blocker Register

This is the source of truth for active Genesis blockers. Keep this file sorted by
release impact first, then by the shortest path to unblock. Phase summaries and
PR notes should point here instead of maintaining parallel blocker lists.

## Impact Levels

- P0: Blocks production-live dogfood or release-candidate validation.
- P1: Blocks phase completion or high-confidence device validation.
- P2: Blocks polish, maintainability, or broader product parity.
- P3: Cleanup, upstream watch items, or accepted low-risk gaps.

## P0 Blockers

| ID | Blocker | Current State | Exit Evidence |
| --- | --- | --- | --- |
| B-P0-001 | Pixel 10 production model-routing proof is complete. | Xiaomi fallback lane passed on `2506BPN68G` with app-private `google-gemma-4-E2B-it-litertlm` (2,583,085,056 bytes, SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`), route `LOCAL_LLM / LOCAL_ELIGIBLE`, redacted output `ok`, artifacts `artifacts/device-model-routing/20260516-165925-fallback/`. Pixel 10 Pro Fold lane passed on `google/rango/rango:16/BD3A.251105.010.E1/14337626:user/release-keys`, AICore visible as `com.google.android.aicore`, route `LOCAL_LLM / LOCAL_ELIGIBLE`, redacted output `ok`, artifacts `artifacts/device-model-routing/20260518-020349-pixel10/`. | Closed for launch routing. Remaining AICore risk is feature selection: public `GenerativeModel` does not expose explicit Nano v3 selection. |

## P1 Blockers

| ID | Blocker | Current State | Exit Evidence |
| --- | --- | --- | --- |
| B-P1-001 | Phase 2 source ingestion needs hardware validation beyond buffer-backed jobs. | Ambient, buds, device/media, and watch capture jobs, source-owned SODA controllers, qualified buffers, settings lifecycle wiring, and AOC-vs-mic routing with fallback exist. AOC now has reflective Context Hub discovery, nanoapp lookup, LIVE_STREAM/STREAMING_OFF messages, payload forwarding, and silence injection, but the current public-SDK build cannot construct the required `ContextHubClientCallback` subclass. Stock installs therefore fall back to mic by design. | Debug panels or logs show source-specific bytes/text entering the correct buffers from real sources, including watch and media paths where hardware is available. For AOC, show successful nanoapp registration and callback delivery from a platform-capable build, or document expected mic fallback on non-AOC/stock hardware. |
| B-P1-002 | Raw-audio runtime plumbing now passes on Pixel 10; model quality remains weak. | BERT JNI, model manifest verification, unit coverage, and androidTest CPU BERT smoke exist. The updated `~/inherent/artifacts/production` artifact matches Android's `[1,3000,128] -> [1,13]` contract and is installed locally into ignored assets. Pixel 10 `GatekeepingRuntimeSmokeTest` passes CPU BERT and Inherent frontend + 13-head backend. `scripts/test-fixtures/run-audio-fixtures.sh both` passes with 11 ambient results and no `raw_audio_unavailable`. The Pixel 10 ambient scorecard at `artifacts/audio-run/20260518-022111/` scored all 8 labeled positives but got 0/8 expected top-1 heads; 3/3 negative controls stayed clean. Direct recovered Conformer probe at `artifacts/audio-run/20260518-023616/` fails closed with `raw_audio_unavailable:audio_tflite_split_create_failed`; root cause is TensorFlow Lite/Darwinn interpreter create failing with `Internal error: Found invalid handle`. | Rerun Inherent on Xiaomi. Treat recovered Conformer as gated until Darwinn interpreter creation is fixed or replaced. Add held-out quality metrics for per-head precision/recall and false-positive rate before treating Inherent as product-quality. |
| B-P1-003 | Release remote inference environment is not provisioned. | `docs/remote-inference-contract.md` defines the issuer and inference contract. Build config is intentionally blank by default. | Provision endpoint, certificate pin, audience, and credential issuer. Validate MockWebServer parity plus one live transcript with no secrets logged. |
| B-P1-005 | SODA transcript plumbing passes on Pixel 10 with CPU pack. | `SodaConfigBuilder` uses `AMBIENT_ONESHOT` + `IntendedSpeechConfig.OPEN_MIC`; `JniSodaCapability` emits directedness when intended-query score meets threshold; `SodaController.directedness` and `Gatekeeper` consume it locally. Pixel 10 rejected the recovered Zuma TPU pack with `INVALID_LANGUAGE_PACK`, so launch selection now uses the CPU pack on all devices. Pixel fixture harness passed with 9 final SODA transcripts and expected regex skills, artifacts `artifacts/audio-run/20260518-015814/`. | Human-recorded directed/undirected phrases still needed for quality, but generated-fixture transcript plumbing is no longer blocked. |

## P2 Blockers

| ID | Blocker | Current State | Exit Evidence |
| --- | --- | --- | --- |
| B-P2-003 | Nav3 result/adaptive recipe parity needs device/UI proof. | Saveable route-state tests cover tab stacks, TaskMonitor payloads, corrupted tokens, and adaptive support panes. | Validate result-passing flow and compact, unfolded, and tablet adaptive layouts with screenshots or instrumentation logs. |
| B-P2-004 | Broader phase gates remain incomplete. | Phase 6 gates cover model manifest, release signing separation, telemetry provenance, Firebase boundary, LiteRT-LM path safety, raw-audio production binding, Nav3 route-state coverage, and the dogfood contract gate for SODA IntendedSpeech, selector-routed gatekeeping, AOC fail-closed callback behavior, fulfillment executor split, and README progress-table drift. | Add runtime gates for live LLM path proof, connected source capture, action execution on device, and battery thresholds as those surfaces become device-validated. |
| B-P2-005 | Architecture-delta consolidation tail remains open. | PR6 cleanup is complete. PR2/PR3/PR5 have first-pass implementations: per-source `SodaSession`, AOC-vs-mic routing with partial Context Hub plumbing, and LLM-backed `IntentExtractorImpl` parse-path parity. Remaining work is platform-capable AOC callback construction, connected-device SODA proof, richer intent context, and any product-required cosmo intent extraction parity. | Complete the remaining PR2/PR5 tasks or explicitly re-plan them in `docs/architecture-delta.md` with current product rationale. |

## P3 Blockers

| ID | Blocker | Current State | Exit Evidence |
| --- | --- | --- | --- |
| B-P3-002 | AICore feature-selection public API gap. | Production uses public `GenerativeModel` opportunistically. Non-public feature APIs are only allowed behind a kill switch. | A public AICore API or AAR exposes stable feature selection, or product accepts opportunistic AICore plus ML Kit/LiteRT-LM fallback. |
| B-P3-003 | Large multipurpose files remain. | Main navigation, fulfillment, and gatekeeping have grown broad while phase work landed. | Split along existing ownership boundaries once P0/P1 runtime blockers are closed. |

## Recently Removed From Blocker Status

- AGP lint K2 UAST workaround: removed deprecated `android.lint.useK2Uast=false` from `gradle.properties`; `lintDebug` stays required in CI—re-add only if a toolchain regression forces it and document under P3.
- Raw-audio unavailable production binding: replaced by `LiteRtAudioInterpreterFactory` and `audio_gatekeeper_jni.cc`.
- Empty model manifest: required BERT and audio gatekeeper assets are present and verified by `scripts/download-models.sh --verify-only`.
- Telemetry provenance gap: `Logging.kt` defines provenance metadata and Phase 6 gates enforce the Firebase boundary.
- Fulfillment parser defaulting: invalid or incomplete action payloads are rejected instead of silently defaulted.
- Side-effect confirmation coverage: schema action subtypes now have confirmation-policy coverage.
- Nav3 saveable route-state gap: route serialization and restore paths are covered by `MainNavigationStateTest`.
- C9 backend router classification gap: `CpuRecoveryBackend.classify()` now runs BERT/regex on directed transcripts, EdgeTPU/Inherent remain raw-audio-only until their prerequisites are available, and `Gatekeeper` routes ambient/raw inputs through `AudioGatekeepingBackendSelector`. Verified with `GatekeeperDispatchInvariantTest` and `CpuRecoveryBackendTest` on 2026-05-17.
- LiteRT-LM model packaging decision: dogfood/default fallback uses the app-private `google-gemma-4-E2B-it-litertlm` bundle, not a checked-in APK asset. Local file size is 2,583,085,056 bytes with SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`; `scripts/provision-local-litertlm.sh` writes the hashed artifact directory and `scripts/run-device-model-routing-smoke.sh` deploys it with `DEPLOY_LITERTLM=1`. E4B and asset-pack delivery remain explicit overrides/future release work.
- Centralized fulfillment executor: `FulfillmentActionHandlerImpl` now delegates to a Hilt multibound `FulfillmentActionExecutor` registry split across intent/calendar/draft actions, memory actions, TaskMonitor/AgentQ actions, and passive removed/unsupported actions. Verified by `FulfillmentActionHandlerImplTest` plus the existing fulfillment parser/parity tests on 2026-05-17.
- Fulfillment provider downgrade policy: dogfood accepts Android intents, web search, text drafts, and TaskMonitor/AgentQ fallback for unavailable first-party surfaces. Calendar provider writes still try direct insert when permission/calendar access exists, otherwise fall back to `ACTION_INSERT`. Concrete first-party/provider APIs are post-dogfood integrations, not launch blockers.

## Maintenance Rules

1. Add or update the blocker here before summarizing it in phase docs or PRs.
2. Keep blockers sorted by impact and release risk, not by implementation area.
3. P0 and P1 blockers need concrete exit evidence, not only code changes.
4. When a blocker is removed, move it to the recently removed section with the proof that closed it.
