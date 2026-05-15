package com.google.research.air.cosmo.ui.theme

import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The currently-active wallpaper-derived palette. Empty by default; populated when [CosmoTheme] is
 * called with `wallpaperDriven = true` (or when a screen explicitly wraps itself in
 * [rememberWallpaperPalette]).
 *
 * Today exposes only the 3 wallpaper colors Android surfaces via [WallpaperManager.getWallpaperColors]
 * (SDK 27+). For full Material-You tonal palettes wire `material-color-utilities` and expand
 * [WallpaperPalette] to hold per-role tone arrays.
 */
val LocalWallpaperPalette = staticCompositionLocalOf { WallpaperPalette.Empty }

data class WallpaperPalette(
    val primary: Color? = null,
    val secondary: Color? = null,
    val tertiary: Color? = null
) {
    fun colorFor(role: PaletteRole): Color? = when (role) {
        PaletteRole.Primary -> primary
        PaletteRole.Secondary -> secondary
        PaletteRole.Tertiary -> tertiary
    }

    val isEmpty: Boolean get() = primary == null && secondary == null && tertiary == null

    companion object {
        val Empty = WallpaperPalette()
    }
}

/**
 * Reads the system wallpaper colors and re-emits them when the wallpaper changes. Returns
 * [WallpaperPalette.Empty] below API 27 (where [WallpaperManager.OnColorsChangedListener] doesn't
 * exist) or when no wallpaper is set.
 */
@Composable
fun rememberWallpaperPalette(): WallpaperPalette {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(WallpaperPalette.Empty) }

    DisposableEffect(context) {
        val manager = WallpaperManager.getInstance(context)
        palette = manager.snapshotPalette()

        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            palette = manager.snapshotPalette()
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return palette
}

private fun WallpaperManager.snapshotPalette(): WallpaperPalette {
    val colors = getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return WallpaperPalette.Empty
    return WallpaperPalette(
        primary = Color(colors.primaryColor.toArgb()),
        secondary = colors.secondaryColor?.let { Color(it.toArgb()) },
        tertiary = colors.tertiaryColor?.let { Color(it.toArgb()) }
    )
}
