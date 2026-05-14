package com.google.research.air.cosmo.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * CompositionLocal exposing a single [HazeState] for the current scaffold. Bars read this to apply
 * `hazeEffect`; the main content reads it to apply `hazeSource`.
 *
 * Defaults to a no-op state (`HazeState()` that no source has been installed on); `hazeEffect`
 * against it is a no-op, so reading [LocalHazeState] without a host is safe.
 */
val LocalHazeState = staticCompositionLocalOf { HazeState() }

/**
 * Installs a shared [HazeState] for a screen so chrome can blur its content. Wrap the entire
 * screen (scaffold + content) in this; mark the scrollable area as the source with
 * [Modifier.cosmoHazeSource], then pass `frosted = true` to [CosmoTopBar] / [CosmoBottomBar].
 */
@Composable
fun CosmoHazeHost(content: @Composable () -> Unit) {
    val state = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides state) { content() }
}

/** Marks a composable's content as the source layer that haze blurs underneath chrome. */
fun Modifier.cosmoHazeSource(state: HazeState): Modifier = this.hazeSource(state)

/**
 * `true` when the runtime can render real backdrop blur. Haze requires API 31+ for GPU blur and
 * falls back to a solid tint on older devices; we keep the same fallback shape ourselves so callers
 * can choose to skip `hazeEffect` entirely (and avoid the per-frame cost).
 */
val supportsRealBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Default frosted style used by Cosmo chrome. Tinted toward the active surface so the bar still
 * reads against any wallpaper.
 */
@Composable
fun cosmoFrostedStyle(
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    tintAlpha: Float = 0.55f
): HazeStyle = HazeStyle(
    backgroundColor = backgroundColor,
    tints = listOf(HazeTint(backgroundColor.copy(alpha = tintAlpha))),
    blurRadius = 24.dp,
    noiseFactor = 0.05f
)

/**
 * Convenience: applies real blur on API 31+ or a tinted scrim on older devices. Use on chrome
 * surfaces (top bar, bottom bar, sticky overlays).
 */
@Composable
fun Modifier.cosmoFrosted(
    state: HazeState = LocalHazeState.current,
    style: HazeStyle = cosmoFrostedStyle()
): Modifier =
    if (supportsRealBlur) {
        this.hazeEffect(state = state, style = style)
    } else {
        val tint = style.tints.firstOrNull()?.color ?: MaterialTheme.colorScheme.surface
        this.background(tint)
    }
