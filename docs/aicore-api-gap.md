# AICore Public API Gap

## Current State

Genesis currently has three LLM routes, none of which is fully production-live without additional provisioning:

- Public AICore `GenerativeModel` through `EdgeAICoreClientWrapper`.
- ML Kit GenAI Prompt as a public local fallback.
- LiteRT-LM through `LiteRtLmLLM`, which requires a local `.litertlm` model file.

Remote inference is also implemented as a production contract, but it requires build-time endpoint/certificate/audience configuration and runtime credential issuance. The default app build leaves that remote path unconfigured.

## Public AICore Gap

The checked-in dependency `com.google.ai.edge.aicore:aicore:0.0.1-exp02` exposes the typed `GenerativeModel` path used by the app.

The Marching Orders request `AiFeature 108 -> AiCoreClient -> LlmService -> LlmRequest` as a lower-level integration. That exact public API is not exposed as a stable callable surface in this AAR. Genesis must not use reflection or internal Play services classes to simulate feature selection, because that would make production behavior dependent on non-public ABI.

Production completion for this item requires one of these inputs:

- A public AICore AAR that exposes stable `AiFeature` selection and `AiCoreClient`/`LlmService`/`LlmRequest` request construction.
- A project-owned wrapper AAR from the AICore team that exposes the same contract.

Until that artifact exists, the production path remains the typed `GenerativeModel` API with normal device capability checks.
`CascadingLocalLLM` now treats that AICore path as opportunistic and falls through to public production
providers in this order:

1. ML Kit GenAI Prompt API with stable `FULL` then stable `FAST` model preference.
2. LiteRT-LM Android with a configured `.litertlm` model file.

## Experimental Non-Public AICore Option

We may try non-public AICore APIs to get feature-specific behavior working sooner, but only as an explicitly experimental branch or runtime-gated path. Requirements for that experiment:

- Keep the public `GenerativeModel` path as the production default.
- Hide non-public calls behind a kill switch / build flag.
- Do not weaken `scripts/verify-llm-phase1.sh` production reflection checks unless the experiment is isolated from release builds.
- Record exact device, package, API, and failure traces before depending on any internal ABI.
- Promote it to production only if the AICore team provides a stable wrapper AAR or accepts the ABI risk in writing.

The toggle is wired in `app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/AICoreExperiment.kt`. The build flag `cosmo.aicore.experimentalNonPublic` (default `false`) selects the experimental mode, but a runtime kill switch persisted in `aicore_experiment.preferences_pb` always forces `PUBLIC_ONLY` regardless of build configuration. The kill switch is exposed through `AICoreExperimentToggle.activateKillSwitch()` and surfaced as a `StateFlow<Boolean>` for debug/observability panels.

Architecture note: `docs/architecture-delta.md` PR6 currently targets deleting this speculative experiment toggle unless an active non-public AICore experiment consumes it. Until PR6 lands, this document describes the code that exists today.

## LiteRT-LM Model Status

The requested Gemma 4 assistant source is `google/gemma-4-E4B-it-assistant`.
That Hugging Face repository is the Gemma 4 MTP drafter, not a standalone LLM:
it requires target-model embeddings and shared KV states from `google/gemma-4-E4B-it`.
The current public `litert_torch` exporter can verify/download that checkpoint,
but it does not expose the Gemma 4 `tf_lite_mtp_drafter` export path needed to
turn the assistant safetensors into a valid standalone `.litertlm` bundle.

The production LiteRT-LM fallback is therefore the official
`litert-community/gemma-4-E4B-it-litert-lm` bundle, which includes the target
model and `tf_lite_mtp_drafter` section. Use `scripts/provision-gemma4-litertlm.sh`
to fetch and validate it locally at
`artifacts/models/google-gemma-4-E4B-it-litertlm/model.litertlm`.

LiteRT-LM is intentionally unavailable until that provisioned model file is
provisioned at `cosmo.localLlm.litertLmModelPath` or the app-private default
`files/models/gemma-4-E4B-it/model.litertlm`. Missing model files
produce a clean `LlmUnavailableException` and do not use synthetic responses.

Delivery choices:

- `LocalLlmModelDelivery` (in `app/src/main/kotlin/com/google/research/air/cosmo/lib/llm/LocalLlmModelDelivery.kt`) selects between explicit override path, staged bundled asset (copied from APK/AAB on first use, size + SHA-256 verified), app-private `files/models/...` deployment (e.g. `adb push` via `scripts/deploy-gemma4-litertlm.sh`), and runtime HTTPS download.
- Runtime download uses `cosmo.localLlm.litertLmModelUrl` plus the `litertLmModelSha256`/`litertLmModelSizeBytes` triple. The downloader stages to a `.part` file, validates checksum and size, and atomically renames to a cache file under app-private storage.
- The Hugging Face artifact URL is `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm`. Generate the SHA-256 and size via `scripts/provision-gemma4-litertlm.sh`, which now writes `model.manifest-entry.json` next to the artifact for direct merge into `app/src/main/assets/model-manifest.json`.
- `cosmo.localLlm.litertLmDeliveryMode` selects which sources are tried (`AUTO`, `EXPLICIT_PATH`, `BUNDLED_ASSET`, `APP_PRIVATE_FILES`, `RUNTIME_DOWNLOAD`). Internal/teamfood builds use bundled or app-private; external dev builds use runtime download. Android AI packs and Play/Game Asset Delivery are deferred until release variants exist.
