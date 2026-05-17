# Architecture Update Hook

## Current State

Genesis is past the initial wiring phase but not release-complete. Current project-owned docs must describe both implemented surfaces and active blockers:

- `docs/blocker-register.md` is the canonical blocker register and is sorted by release impact.
- Phase 1 LLM routing exists, but remote inference is unconfigured by default, public AICore lacks feature-selection APIs, and LiteRT-LM requires model provisioning.
- Phase 2 remains a runtime blocker for attached hardware/source ingestion, but model manifest artifacts are present. Gatekeeper subscription ownership, unified BERT/audio model dispatch, SODA IntendedSpeech/Magic Mic directedness gating, directedness telemetry, per-source SODA sessions, AOC-vs-mic routing with fallback, C9 backend classification routing, the LLM-backed `IntentExtractorImpl`, and speculative-config deletion are locally implemented and build-green. Device SODA smoke, AOC callback activation on a platform-capable build, richer intent context, and connected-source proof remain open.
- Phase 3 and Phase 4 are broad but need final product-surface hardening: fulfillment parsing, confirmation coverage, and executor-owned dispatch are typed; Nav3 route state is saveable and covered; the remaining work is richer execution-error UI plus result/adaptive UI recipe parity.
- Phase 5 hardening exists; Phase 6 has now added release signing separation, non-empty model gates, and telemetry provenance gates, while full Phase 2-5 architecture gates remain open.
- Phase 6 release/dogfood execution has started: strict model-manifest verification, release-signing separation, and model-variant-safe LiteRT-LM delivery are now required.

Any change that modifies startup, permissions, capture sources, qualified buffers,
LLM routing, fulfillment actions, telemetry schemas, or UI entry points must be
checked against the upstream `architecture.md` and stack maps (see `docs/reference-repository.md` for where that checkout lives on your machine).

Before merging:

1. Run `scripts/verify-architecture-delta.sh`.
2. Run `scripts/verify-relocation-safety.sh`.
3. Run `scripts/download-models.sh --verify-only`; all `required` model entries must exist and match SHA-256/size metadata.
4. Run `./gradlew clean assembleDebug assembleReleaseInternal detekt lintDebug testDebugUnitTest` (production `assembleRelease` requires release signing env vars; see `docs/release-signing-policy.md`).
5. Record changed architecture surfaces in the PR body.
6. Update `docs/architecture-delta.md` for Genesis-owned changes.
7. Update the source maps in your reference docs checkout when behavior no longer matches the cited artifact map. See `docs/reference-repository.md` for the optional `COSMO_REFERENCE_REPO_ROOT` convention.

Before declaring a phase complete:

1. Phase 1 must have at least one live LLM path verified on device or webshell/device-equivalent runtime: remote inference, public AICore/ML Kit, or provisioned LiteRT-LM.
2. Phase 2 must have source-specific captor tests, manifest-backed BERT CPU model loading, directedness-gate dispatch tests, AOC fallback/transport tests, raw-audio gatekeeper unavailable/available tests, intent-extractor subscriber tests, and connected-device SODA fixture evidence for both directed and undirected audio.
3. Phase 3 must have typed action validation, side-effect confirmation coverage for every schema action subtype, and executor-owned dispatch.
4. Phase 4 must have Nav3 saveable route-state coverage and adaptive layout checks for compact, unfolded, and tablet widths.
5. Phase 5 must have release-specific signing/model/telemetry gates.
6. Dogfood contract drift must be caught by `scripts/verify-dogfood-contract-gates.sh`: SODA IntendedSpeech, selector-routed gatekeeping, AOC fail-closed behavior, fulfillment executor split, and README progress bars.
7. Phase 6 must keep `scripts/verify-phase6-release-gates.sh` green before dogfood or release-candidate builds.

The CI workflow runs the first two checks before the Gradle build so missing
architecture deltas and malformed large-asset manifests fail before slower work.
