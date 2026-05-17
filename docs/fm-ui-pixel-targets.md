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

## 2a. Non-Negotiable Component Behavior

### Orb Tick Rings

The orb state rings must be built from individually addressable rectangular ticks, not generic dashed arcs. "Listening", "working", and "needs input" each need per-tick color state so animation can fill, sweep, or rotate through the ring tick-by-tick. Measure the baseline tick count from the target images before implementation; do not guess from the current Compose approximation.

The implementation should support:

- inactive ticks with dim blue/gray glass color;
- active ticks with the state color: green, cyan, yellow, and magenta accent;
- per-state and per-variant tick palettes, including mixed-color rings rather than a single accent-only ring;
- per-state center glyphs/shapes such as dot, square, exclamation, and future custom glyphs;
- deterministic animation phase for screenshot tests;
- runtime animation where color advances through the tick set during spinning.

### Tab Minimized Rail

The minimized tab is a narrow edge rail, not a static rounded rectangle. It needs the baseline color treatment from `artifacts/fm_ui/tab/minimized.png`, plus an animated shining light element that feels polished in motion. The shimmer/glint may be subjective, but it must be an explicit animation primitive rather than a static flare.

The implementation should support:

- vertical glass rail body with measured width, height, radius, and border;
- cyan/blue internal rail color matching the target;
- animated light glint traveling along or blooming from the rail center;
- deterministic animation phase for screenshot tests.

### Card Stack Interactivity

The card stack is an interactive overlay surface, not a static poster. The pixel fixtures freeze the visual state, but the component architecture must still allow cards to contain richer controls and embedded surfaces.

The implementation should preserve room for:

- independent per-card actions, hover/press/selection states, and collapse-to-edge controls;
- complex card bodies such as mini browser/source review, messaging target picker, timers, and future embedded web/content surfaces;
- twelve first-class card payload families today: browser/source review, calling, messaging, document, list/tasks, calendar/timer, navigation, media playback, people context, media/image, source citation, and permission confirmation;
- multiple stacked cards with clear depth, focus, and ordering rules;
- gesture and lifecycle handling in the later overlay pass without rewriting the card visuals.

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
- primary ring radii, exact tick counts, tick sizes, tick rotations, per-tick active color spans, and center glyph sizes;
- minimized tab rail dimensions, internal color bands, glint size, and glint path;
- card widths, heights, padding, line heights, and button grid dimensions;
- minimized/expanded tab dimensions and flare positions;
- mobile/foldable stack offsets and maximum footprint.

Use a small image-measurement script or image editor overlays for the first measurement pass. The implementation sweep should then work from those numbers, not from repeated visual guessing.
