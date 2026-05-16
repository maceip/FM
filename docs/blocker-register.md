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
| B-P0-001 | Pixel 10 production model-routing proof remains open. | Xiaomi fallback lane passed on `2506BPN68G` with app-private `google-gemma-4-E2B-it-litertlm` (2,583,085,056 bytes, SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`), route `LOCAL_LLM / LOCAL_ELIGIBLE`, redacted output `ok`, artifacts `artifacts/device-model-routing/20260516-165925-fallback/`. Pixel 10/Nano v3 lane has not run yet. | Run the production `@HybridLLM` smoke on Pixel 10. Pixel lane requires AICore visibility. Record command, device/build fingerprint, route path/reason, model identifier or unavailable reason, and redacted output. |

## P1 Blockers

| ID | Blocker | Current State | Exit Evidence |
| --- | --- | --- | --- |
| B-P1-001 | Phase 2 source ingestion needs hardware validation beyond buffer-backed jobs. | Ambient, buds, device/media, and watch capture jobs, source-owned SODA controllers, qualified buffers, settings lifecycle wiring, and AOC-vs-mic routing with fallback exist. AOC nanoapp transport still returns false and falls back to mic. | Debug panels or logs show source-specific bytes/text entering the correct buffers from real sources, including watch and media paths where hardware is available. For AOC, show successful nanoapp registration or document expected mic fallback on non-AOC hardware. |
| B-P1-002 | Raw-audio runtime proof is still failing on Xiaomi. | BERT JNI, raw-audio LiteRT JNI, model manifest verification, unit coverage, and androidTest CPU BERT smoke exist. Core capability smoke passed BERT/regex gatekeeper text detection on Xiaomi. Debug WAV injection against the raw-audio path returned `raw_audio_unavailable:audio_frontend_run_failed`, so the current fallback is CPU recovery/SODA rather than raw-audio LiteRT on this device. | Run `GatekeepingRuntimeSmokeTest` and raw-audio LiteRT smoke on Pixel 10 and on Xiaomi after the replacement `inherent.tflite` lands; record outputs and failure mode if unsupported. |
| B-P1-003 | Release remote inference environment is not provisioned. | `docs/remote-inference-contract.md` defines the issuer and inference contract. Build config is intentionally blank by default. | Provision endpoint, certificate pin, audience, and credential issuer. Validate MockWebServer parity plus one live transcript with no secrets logged. |
| B-P1-004 | LiteRT-LM model packaging decision is open. | Runtime can use a bundled asset or an app-private file keyed by `cosmo.localLlm.litertLmModelId`; the deploy script and runtime now agree on the hashed storage path. The requested E4B LiteRT-LM bundle is not yet checked into a release-owned path. | Decide bundled asset, app-private file, or future asset-pack delivery. Record model id, byte size, SHA-256, install behavior, and storage budget. |
| B-P1-005 | SODA transcript quality still needs human-recorded fixture proof. | `SodaConfigBuilder` uses `AMBIENT_ONESHOT` + `IntendedSpeechConfig.OPEN_MIC`; `JniSodaCapability` emits directedness when intended-query score meets threshold; `SodaController.directedness` and `Gatekeeper` consume it locally. Xiaomi core smoke proved SODA native startup and PCM acceptance. Debug WAV injection proved SODA event and intended-query logging with `scripts/run-device-core-capability-smoke.sh /path/to/phrase.wav`; the synthetic timer fixture produced directedness signals but blank transcripts. | Run directed-command and undirected-conversation human recordings on target hardware. Record intended-query score/threshold, final transcript, and absence/presence of directedness. |
| B-P1-006 | C9 backend router is scaffolded but not classification-complete. | `AudioGatekeepingBackend`, selector, and concrete backend classes exist, but backend `classify()` methods still contain `TODO(...)`; `Gatekeeper` still calls concrete models directly after using the selector only for active-backend id. | Wire `CpuRecoveryBackend` to BERT-on-transcript, keep EdgeTPU and Inherent unavailable until their prerequisites exist, and route Gatekeeper classification through the selector without direct model calls. |

## P2 Blockers

| ID | Blocker | Current State | Exit Evidence |
| --- | --- | --- | --- |
| B-P2-001 | Fulfillment executor remains too centralized. | Action parsing rejects missing required fields and side-effect confirmation coverage exists, but one implementation still owns many action branches. | Move subtype execution into focused executors or a registry with typed results. Preserve parser and confirmation tests. |
| B-P2-002 | Some fulfillment actions intentionally downgrade to generic intents/search. | Calendar, external agent, and unavailable first-party surfaces use explicit downgrade paths instead of fake integrations. | Implement concrete provider/API integrations where required, or document the downgrade as accepted product behavior for dogfood. |
| B-P2-003 | Nav3 result/adaptive recipe parity needs device/UI proof. | Saveable route-state tests cover tab stacks, TaskMonitor payloads, corrupted tokens, and adaptive support panes. | Validate result-passing flow and compact, unfolded, and tablet adaptive layouts with screenshots or instrumentation logs. |
| B-P2-004 | Broader phase gates remain incomplete. | Phase 6 gates cover model manifest, release signing separation, telemetry provenance, Firebase boundary, LiteRT-LM path safety, raw-audio production binding, and Nav3 route-state coverage. | Add phase-specific gates for live LLM path proof, model delivery manifest, gatekeeper outputs, capture jobs, action execution, and battery thresholds as those surfaces become device-validated. |
| B-P2-005 | Architecture-delta consolidation tail remains open. | PR6 cleanup is complete. PR2/PR3/PR5 have first-pass implementations: per-source `SodaSession`, AOC-vs-mic routing with stubbed transport, and coarse `IntentExtractorImpl`. Remaining work is real AOC transport, connected-device SODA proof, richer intent context, and any product-required cosmo intent extraction parity. | Complete the remaining PR2/PR5 tasks or explicitly re-plan them in `docs/architecture-delta.md` with current product rationale. |

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

## Maintenance Rules

1. Add or update the blocker here before summarizing it in phase docs or PRs.
2. Keep blockers sorted by impact and release risk, not by implementation area.
3. P0 and P1 blockers need concrete exit evidence, not only code changes.
4. When a blocker is removed, move it to the recently removed section with the proof that closed it.
