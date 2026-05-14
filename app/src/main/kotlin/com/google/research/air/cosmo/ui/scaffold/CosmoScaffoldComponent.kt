package com.google.research.air.cosmo.ui.scaffold

import androidx.compose.runtime.Composable

/**
 * A pluggable "page" that can be activated, dismissed, or stacked inside a [CosmoScaffoldHost].
 * Adapted from Kvaesitso's `ScaffoldComponent`. Useful for overlays/panels (model picker, settings
 * sheet, debug drawer) that aren't a good fit for full nav routes — they have lifecycle but live
 * over the current route rather than replacing it.
 *
 * Implementations supply the composable body via [Content] and override the flags they care about.
 * The host calls the lifecycle hooks in order: `onPreActivate` → `onActivate` (when becoming top)
 * and `onPreDismiss` → `onDismiss` (when leaving).
 *
 * Existing Cosmo screens stay on Navigation3 — this is infrastructure for future panels, not a
 * migration target.
 */
interface CosmoScaffoldComponent {
    /** If true, the component is always present in the host and not dismissable via back. */
    val permanent: Boolean get() = false

    /** If true, the host draws its background behind this component (otherwise the component owns
     *  background rendering). */
    val drawBackground: Boolean get() = true

    /** If true, the host expects an IME to be opened by this component and adjusts insets. */
    val hasIme: Boolean get() = false

    /** If true, the component remains attached across `onPause` (e.g. media controllers). */
    val survivesPause: Boolean get() = false

    /** Stable identifier used for state save/restore and dedup inside the host. */
    val key: String

    /** Called once before activation (good for warming up state, fetching data). */
    suspend fun onPreActivate() = Unit

    /** Called when the component becomes the top of the stack. */
    suspend fun onActivate() = Unit

    /** Called before [onDismiss], while the component is still in the tree. */
    suspend fun onPreDismiss() = Unit

    /** Called after the component leaves the tree. */
    suspend fun onDismiss() = Unit

    /** The composable body. The host owns positioning/insets — the body fills its slot. */
    @Composable fun Content()
}
