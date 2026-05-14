# Release signing policy (Genesis)

This document is for owners deciding **how** production APKs/AABs are signed. It does **not** define which organizational identity or HSM policy you must use—that is a security/org decision.

## What the codebase supports

- **Debug / local iteration**: `debug` uses the checked-in `config/signing/genesis-debug.keystore` (same as today).
- **Production `release`**: expects signing material from **environment variables** or **Gradle properties** (typically `~/.gradle/gradle.properties`, never committed). If those are missing, `assembleRelease` / `bundleRelease` fail fast with an actionable error.
- **Internal minified builds (`releaseInternal`)**: same R8/shrink settings as `release`, but signed with the **debug** keystore so CI and engineers can run a minified artifact **without** Play-ready keys.

## Production signing inputs (choose one integration path)

Set all four values for any machine or job that runs `./gradlew assembleRelease` or `bundleRelease`:

| Variable | Gradle property fallback | Meaning |
|----------|---------------------------|---------|
| `COSMO_RELEASE_STORE_FILE` | `cosmo.release.storeFile` | Filesystem path to the PKCS12/JKS keystore |
| `COSMO_RELEASE_STORE_PASSWORD` | `cosmo.release.storePassword` | Keystore password |
| `COSMO_RELEASE_KEY_ALIAS` | `cosmo.release.keyAlias` | Signing key alias inside the store |
| `COSMO_RELEASE_KEY_PASSWORD` | `cosmo.release.keyPassword` | Private key password (often same as store password) |

**Team decisions to record elsewhere** (wiki / security doc):

- Who generates and rotates the upload key; whether Play App Signing manages the app signing key.
- Where the keystore file lives for CI (encrypted artifact, secret file mount, or ephemeral generation)—**do not** commit it.
- Which GitHub/GitLab secret names map to the four values above for release workflows.

## CI layout (recommended)

- **Default PR/main pipeline**: build `assembleDebug`, `assembleReleaseInternal`, tests, lint—**no** production secrets required.
- **Release/upload workflow** (manual or tagged): inject the four secrets/env vars, check out or download the keystore to a path referenced by `COSMO_RELEASE_STORE_FILE`, then run `assembleRelease` / `bundleRelease`.

## Model manifest gate for production release

`assembleRelease` and `bundleRelease` depend on `verifyCosmoReleaseModelManifest`, which runs `scripts/download-models.sh --verify-only` **without** `--allow-empty`. That enforces a non-empty `models` array and on-disk checksum/size checks for **required** bundled entries before packaging.

Optional lightweight JSON-only check (no filesystem): `scripts/verify-model-manifest-non-empty.sh` — useful when large binaries are absent but you still want to assert `models` is non-empty.
