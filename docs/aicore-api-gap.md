# AICore Public API Gap

## Current State

Genesis currently has three LLM routes, none of which is fully production-live without additional provisioning:

- Public AICore `GenerativeModel` through `EdgeAICoreClientWrapper`.
- ML Kit GenAI Prompt as a public local fallback.
- LiteRT-LM through `LiteRtLmLLM`, which requires a local `.litertlm` model file.

Remote inference is also implemented as a production contract, but it requires build-time endpoint/certificate/audience configuration and runtime credential issuance. The default app build leaves that remote path unconfigured.

Device rule: a Pixel 10 with working Nano v3/AICore does not need LiteRT-LM
for the text happy path. LiteRT-LM remains the local fallback for any device
where AICore is unavailable, unsupported for the prompt modality, not ready, or
fails before emitting tokens.

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

## Non-Public AICore Option

No non-public AICore branch is active in production. The speculative experiment
toggle was deleted in PR6, so any future feature-specific AICore experiment
must be reintroduced as an isolated branch or build flag and must not replace
the public production path until ABI risk is accepted.

## LiteRT-LM Model Status

The requested Gemma 4 assistant source is `google/gemma-4-E4B-it-assistant`.
That Hugging Face repository is the Gemma 4 MTP drafter, not a standalone LLM:
it requires target-model embeddings and shared KV states from `google/gemma-4-E4B-it`.
The current public `litert_torch` exporter can verify/download that checkpoint,
but it does not expose the Gemma 4 `tf_lite_mtp_drafter` export path needed to
turn the assistant safetensors into a valid standalone `.litertlm` bundle.

The dogfood LiteRT-LM fallback is the local
`google-gemma-4-E2B-it-litertlm` bundle. Use
`scripts/provision-local-litertlm.sh` to copy the local `.litertlm` file into
`artifacts/models/google-gemma-4-E2B-it-litertlm-165e6fc4354294a0/model.litertlm`.
That artifact is 2,583,085,056 bytes with SHA-256
`ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`.

The larger official E4B fallback remains available as an explicit override via
`litert-community/gemma-4-E4B-it-litert-lm`, which includes the target model and
`tf_lite_mtp_drafter` section. Use `scripts/provision-gemma4-litertlm.sh` to
fetch and validate it locally when that path is intentionally being tested.

LiteRT-LM is intentionally unavailable until that provisioned model file is
provisioned as either a bundled APK asset
`assets/models/<model-storage-id>/bundled.litertlm` or an app-private file
`files/models/<model-storage-id>/model.litertlm`. Missing model files produce
a clean `LlmUnavailableException` and do not use synthetic responses.

Delivery choices:

- `LiteRtLmLLM` first checks the app-private path populated by
  `scripts/deploy-gemma4-litertlm.sh`, then stages a bundled asset if present.
- Runtime download is not active. Re-add it only when a release-owned download
  workflow exists.
- The E4B Hugging Face artifact URL is `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm`. Generate the SHA-256 and size via `scripts/provision-gemma4-litertlm.sh`, which writes `model.manifest-entry.json` next to the artifact for direct merge into `app/src/main/assets/model-manifest.json` if that future release path is selected.
- Internal/teamfood builds use bundled or app-private deployment. Android AI
  packs and Play/Game Asset Delivery are deferred until release variants exist.
