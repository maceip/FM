# Testing Velocity Upgrades

## Goal

Make the existing remote emulator loop the final proof, not the first proof.
The fastest upgrade path is a host-side ladder that gives engineers useful
failures before they build, upload, launch, and manually review a recording.

## Current repo shape

- The project already has a remote emulator gate through `scripts/emu-ci-run.sh`.
  It uploads an APK, launches an activity, records logcat and `screen.mp4`, and
  fails on crash/ANR/test-failure log patterns.
- The documented phase gate still depends on the emulator recording being
  visually reviewed for expected behavior.
- The remote emulator is Pixel 5 / API 35 / x86_64, while recovered SODA native
  binaries are arm64-only, so the emulator validates boot, Hilt, UI launch,
  graceful degradation, and text-only paths better than it validates native
  speech/model execution.
- CI currently compiles as a hard gate, but unit tests and lint/detekt are
  best-effort. That means some cheap failures do not block a PR.
- Host-side testing is already present: Robolectric, Compose UI test, MockK,
  MockWebServer, coroutines-test, Turbine, and JaCoCo are wired in Gradle.
- There are no checked-in Compose `@Preview` screenshot tests, Paparazzi tests,
  or Roborazzi tests today.

## Ranked upgrades

### 1. Make the host-side gate trustworthy first

This is the cheapest confidence gain.

- Keep the manifest drift check green before adding more infrastructure.
- Remove `|| true` from the GitHub unit-test step once the suite is green.
- Keep lint/detekt best-effort only if there is a specific known blocker; avoid
  swallowing all new failures.
- Add a single documented fast command for every PR:
  `./gradlew compileDebugKotlin :app:testDebugUnitTest`.

Why first: this uses dependencies the repo already has, catches real regressions
without a device, and lowers the pressure on the remote emulator queue.

### 2. Use existing Robolectric Compose tests for semantic UI confidence

This is the lowest-infrastructure UI upgrade.

- Expand the existing pattern used by `AllCardsRenderTest`: render a composable
  on the JVM and assert tags/text/interaction state.
- Add `testTag` to stable UI affordances when a screen has important states.
- Convert "renders without crash" tests into meaningful assertions where cheap,
  especially orb/tab/task-monitor states.
- Migrate Compose tests to the v2 `createComposeRule` API in a small cleanup to
  remove current deprecation warnings.

What it catches: missing content, broken state branching, unexpected crashes,
basic interaction regressions.

What it does not catch: pixel regressions, color/spacing drift, font/layout
polish.

### 3. Add official Compose Preview Screenshot Testing for visual smoke

This is the best fit for "render Compose without running Android" in this repo.

The official Android tool runs host-side screenshot tests from composable
previews and compares them with reference images. It supports `@PreviewTest`,
the `screenshotTest` source set, Gradle tasks like
`:app:updateDebugScreenshotTest` and `:app:validateDebugScreenshotTest`, and an
HTML diff report.

Fit here:

- Good: the repo is already AGP 9.x, Kotlin 2.3.x, JDK 17, and Compose-enabled.
- Good: it reuses Compose previews, so engineers can add small preview fixtures
  close to the UI they are changing.
- Good: no emulator/device is required for visual regression checks.
- Risk: the plugin is still alpha, so start with a tiny surface area and do not
  make it a required PR gate until it has been stable in CI for a few runs.

Recommended pilot:

1. Add the Compose screenshot plugin and `screenshotTest` source set.
2. Add two preview screenshot tests:
   - `CardStackSurface` with the existing ten-card sample data.
   - `TabSurface` expanded/collapsed states at one or two widths.
3. Commit references with a low image-diff threshold.
4. Run screenshot validation as an optional CI check at first.
5. Promote it to required only after false-positive rate is acceptable.

### 4. Treat Paparazzi as a fallback, not the first choice

Paparazzi is the Square/CashApp project the question refers to. It renders
Android views and Compose without a physical device or emulator and provides
record/verify Gradle tasks.

Fit here:

- Good: mature mental model, straightforward Compose snapshots, no emulator.
- Good: useful if the official Compose screenshot plugin is too unstable.
- Risk: the latest public docs point at Paparazzi `2.0.0-alpha04`, Java 21, and
  recent AGP support that may lag this repo's AGP 9.1.1 setup.
- Risk: image baselines imply Git LFS or careful repository-size policy.

Recommendation: do not start with Paparazzi unless the official screenshot
plugin fails in this repo. If tried, add it on a short-lived branch and prove
`recordPaparazziDebug` and `verifyPaparazziDebug` against one Compose surface
before committing to it.

### 5. Defer Roborazzi unless device-specific pixels become important

Roborazzi can be useful, but it overlaps more with the existing emulator
recording path and tends to pay off when a project already has stable
emulator/device CI. For this repo, it is not the least-infrastructure next step.

### 6. Grow core confidence with contracts and fixtures

The core-code path should stay mostly pure JVM.

- Add table-driven tests around gatekeeper regex/classification invariants,
  fulfillment parsing, route decisions, and source-toggle privacy behavior.
- Extend MockWebServer tests for every HTTP/SSE surface, including the future
  browsing-agent client.
- Keep native/model execution in small smoke tests, but wrap everything around
  it in fakes so most behavior can run in `:app:testDebugUnitTest`.
- Convert every bug found by `screen.mp4` review into a JVM regression test
  when the bug is in routing/state/serialization rather than Android runtime.
- Keep instrumented tests for permissions, real activity lifecycle, and native
  model/runtime proof only.

## Suggested validation ladder

1. Developer inner loop:
   `./gradlew compileDebugKotlin :app:testDebugUnitTest`
2. UI semantic check:
   targeted Robolectric Compose tests for changed surfaces.
3. UI visual check:
   `:app:validateDebugScreenshotTest` once the pilot exists.
4. Runtime smoke:
   `scripts/emu-ci-run.sh` for APK launch, logcat, and recording review.
5. Hardware-only proof:
   connected androidTest/baseline-profile/native-model checks on a real Pixel.

## Immediate next PRs

1. Make `:app:testDebugUnitTest` required in CI now that the manifest drift gate is green.
2. Add semantic assertions to the existing orb/tab Compose JVM tests.
3. Pilot official Compose Preview Screenshot Testing on `CardStackSurface`.
4. Add a short `docs/testing.md` command matrix once the first three are green.

## External references checked

- Compose Preview Screenshot Testing:
  https://developer.android.com/studio/preview/compose-screenshot-testing
- Compose Preview Screenshot Testing release notes:
  https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes
- Paparazzi:
  https://cashapp.github.io/paparazzi/
