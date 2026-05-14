# Remote Inference Contract

Genesis owns this contract because this project does not have an owned production inference server contract elsewhere.

## Current State

The Android client side is implemented and covered by unit tests:

- `RemoteInferenceClient.kt` posts JSON multimodal requests and parses JSON or SSE responses.
- `RequestSigner.kt` signs requests with an Android Keystore P-256 key.
- `RemoteCredentialStore.kt` stores anonymous credentials encrypted locally.
- `RefreshingAnonymousCredentialProvider` refreshes credentials before expiry.
- `RemoteInferenceConfigProvider.kt` resolves runtime overrides from encrypted local storage before falling back to build-time Gradle properties. This exists for internal/teamfood provisioning; `docs/architecture-delta.md` PR6 still targets deleting it if no active workflow consumes runtime overrides.
- `NetworkAwareLLM` uses the remote path only after local inference fails and network policy allows remote fallback.

The path is not live by default. Runtime config and `BuildConfig.REMOTE_INFERENCE_*` values default to blank, so production remote inference requires provisioning:

- `cosmo.remoteInference.baseUrl`
- `cosmo.remoteInference.certPin`
- `cosmo.remoteInference.audience`
- An issuer service that implements `POST /v1/private-token/issue`
- An inference service that implements `POST /v1/inference`

Until those values and services exist, remote inference cleanly downgrades to unavailable and the app relies on local LLM providers.

## Transport

- Base URL is resolved through `RemoteInferenceConfigProvider`; the build-time fallback is `BuildConfig.REMOTE_INFERENCE_BASE_URL`.
- Production URLs must use HTTPS.
- The app pins the configured SPKI certificate hash when the resolved certificate pin is non-empty.
- Requests include `X-Cosmo-Timestamp-Millis`, `X-Cosmo-Nonce`, `X-Cosmo-Body-SHA256`, `X-Cosmo-Signature-Key-Id`, `X-Cosmo-Signature-Alg`, and `X-Cosmo-Signature`.

## Request Signing

The signature algorithm is `SHA256withECDSA` over this newline-delimited canonical payload:

```text
METHOD
/path?query
timestamp_millis
nonce
base64url_sha256_body
```

The key is a P-256 Android Keystore key generated on first use. The public key is identified by `X-Cosmo-Signature-Key-Id`.

## Anonymous Credential Issuer

`POST /v1/private-token/issue`

Request:

```json
{
  "audience": "cosmo-remote-inference",
  "client": "genesis-android",
  "nonce": "base64url-random-16-bytes",
  "request_signing_public_key_spki": "base64url-der-subject-public-key-info"
}
```

Response:

```json
{
  "authorization_header": "PrivateToken token-value",
  "expires_at_epoch_millis": 1770000000000
}
```

The client stores the returned credential encrypted locally and refreshes it before expiry.

## Inference

`POST /v1/inference`

Headers:

```text
Authorization: PrivateToken token-value
Content-Type: application/json
```

Request:

```json
{
  "content": {
    "parts": [
      {
        "text": "user prompt"
      },
      {
        "blob": {
          "mime_type": "audio/wav",
          "data": "base64-audio"
        }
      },
      {
        "blob": {
          "mime_type": "image/jpeg",
          "data": "base64-image"
        }
      }
    ]
  }
}
```

Response:

```json
{
  "text": "model response"
}
```

Servers return `401` for missing or expired credentials, `429` for rate-limit exhaustion, and `503` for capacity or model unavailability.
