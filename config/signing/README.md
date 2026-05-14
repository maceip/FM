# Signing configs

- **`genesis-debug.keystore`** — checked in for **debug** builds only (`storePassword`/`keyPassword` are the standard debug values). Never use this keystore for Play production uploads.
- **Production upload keys** — stored per `docs/release-signing-policy.md`; reference them only via `COSMO_RELEASE_*` env vars or `cosmo.release.*` Gradle properties. Do not add real keystores or passwords to this directory or to git.
