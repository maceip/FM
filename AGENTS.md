# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

FM ("CosmoGenesis") is an ambient AI assistant Android app — a Gradle-based Kotlin + JNI C++ project.
No backend services, no Docker, no Node.js. See `README.md` for capability matrix and `PLAN.md` for engineering plan.

### Environment requirements

- **JDK 17+** (JDK 21 works)
- **Android SDK** at `$ANDROID_HOME` (`/opt/android-sdk`) with:
  - Platform `android-37.0` (compileSdk 37)
  - NDK `27.0.12077973`
  - CMake `3.22.1`
  - Build-tools `37.0.0`
- `local.properties` must contain `sdk.dir=/opt/android-sdk` (created by the update script)

### Build commands

| Task | Command |
|---|---|
| Compile Kotlin | `./gradlew :app:compileDebugKotlin` |
| Build debug APK | `./gradlew :app:assembleDebug -x verifyCosmoReleaseModelManifest -x verifySodaAssets` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Detekt | `./gradlew detekt` |
| KTLint | `./gradlew ktlintCheck` |
| CI gates | `./gradlew detekt lintDebug :app:testDebugUnitTest :app:assembleDebug` |

### Important gotchas

1. **Asset verification tasks must be skipped in CI/Cloud**:
   `assembleDebug` depends on `verifyCosmoReleaseModelManifest` and `verifySodaAssets`,
   which verify SHA-256 checksums of large gitignored binaries (TFLite models ~72 MB,
   SODA language packs ~56–146 MB). These binaries come from cosmo APK recovery
   (`scripts/provision-from-recovery.sh`) and are not available in Cloud Agent VMs.
   Always pass `-x verifyCosmoReleaseModelManifest -x verifySodaAssets` when building.

2. **Pre-existing test failure**: `GatekeeperModelManifestDriftTest.manifestAndKotlinMapAgreeOnEveryEntry`
   fails due to drift between `model-manifest.json` (8 entries) and the test's hardcoded
   list (7 entries — missing `INHERENT_V0`). 131 of 132 tests pass.

4. **No Android device/emulator available in Cloud Agent VMs**: This is an Android-only
   project. The APK can be built but cannot be installed or run without a device or
   emulator. `scripts/dev-loop.sh` requires ADB.

5. **JaCoCo coverage gates**: Unit tests finalize with `jacocoTestReport` and
   `jacocoTestCoverageVerification` (60% line / 50% branch minimum). These run
   automatically after `testDebugUnitTest`.
