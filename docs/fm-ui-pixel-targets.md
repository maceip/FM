# FM UI Pixel Targets

This is the frozen target set for the presence surface design work. Do not add or remove targets during an implementation sweep without explicitly updating this file first.

## 1. Frozen Target Images

### Orb

- `artifacts/fm_ui/orb/variant1.png` — listening orb, `230x658`.
- `artifacts/fm_ui/orb/variant2.png` — working orb, `230x658`.
- `artifacts/fm_ui/orb/variant3.png` — needs-input orb, `230x658`.
- `artifacts/fm_ui/orb/orb.png` — board-level reference, not a per-component screenshot fixture.

### Tab

- `artifacts/fm_ui/tab/minimized.png` — minimized edge sliver, `30x300`.
- `artifacts/fm_ui/tab/expanded.png` — expanded syncing pill, `528x280`.

### Card Stack

- `artifacts/fm_ui/stack/variant1.png` — browser/deep-research card, `1122x1402`.
- `artifacts/fm_ui/stack/variant2.png` — messaging/send-results card, `1122x1402`.
- `artifacts/fm_ui/stack/variant3.png` — calendar/timer card, `1122x1402`.
- `artifacts/fm_ui/stack/mobile.png` — mobile layout/context reference, `1122x1402`.
- `artifacts/fm_ui/stack/foldable.png` — foldable layout/context reference, `1448x1086`.

The HTML files under each component folder are implementation hints only. The root images above are the visual contract.

## 2. Compose Preview Fixtures

Preview fixtures live in `app/src/main/kotlin/com/google/research/air/cosmo/presence/surfaces/FmUiPresencePreviews.kt`.

The fixtures intentionally use:

- the same canvas dimensions as the target PNGs;
- black backgrounds so transparent/cropped roots can be compared directly;
- fixed sample data for browser, messaging, and calendar cards;
- stable names that include the corresponding artifact path.

These previews are not acceptance by themselves. They are the render targets that screenshot validation should consume.

## 3. Screenshot Validation Path

Use the first-party Android Compose Preview Screenshot Testing plugin rather than a third-party screenshot library. The repo already satisfies the important version constraints: AGP `9.1.1`, Kotlin `2.3.20`, JDK 17, and Compose enabled.

Recommended setup:

- Add the official `com.android.compose.screenshot` plugin.
- Enable `android.experimental.enableScreenshotTest=true`.
- Put screenshot tests in the `screenshotTest` source set and annotate fixtures with `@PreviewTest`.
- Use `./gradlew :app:updateDebugScreenshotTest` to generate references.
- Use `./gradlew :app:validateDebugScreenshotTest` for diffs and HTML reports.

This is host-side and preview-based; it does not require an emulator. It is still an alpha Android tool, so keep the configuration isolated from production behavior.

## 4. Spec Extraction Approach

Before implementation, extract measurements from the frozen PNGs once and add them beside this file or in the screenshot-test fixture comments:

- outer component bounds and canvas offset;
- corner radii and stroke widths;
- primary ring radii, tick counts, active arc spans, and center glyph sizes;
- card widths, heights, padding, line heights, and button grid dimensions;
- minimized/expanded tab dimensions and flare positions;
- mobile/foldable stack offsets and maximum footprint.

Use a small image-measurement script or image editor overlays for the first measurement pass. The implementation sweep should then work from those numbers, not from repeated visual guessing.
