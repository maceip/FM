package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightScheme = lightColorScheme(
    primary = CosmoTokens.Color.Orange,
    onPrimary = CosmoTokens.Color.OrangeDeep,
    primaryContainer = CosmoTokens.Color.OrangeHot,
    onPrimaryContainer = CosmoTokens.Color.OrangeDeep,
    secondary = CosmoTokens.Color.White,
    onSecondary = CosmoTokens.Color.LimeDeep,
    secondaryContainer = CosmoTokens.Color.Lime,
    onSecondaryContainer = CosmoTokens.Color.LimeDeep,
    tertiary = CosmoTokens.Color.Violet,
    onTertiary = CosmoTokens.Color.VioletDeep,
    tertiaryContainer = CosmoTokens.Color.VioletHot,
    onTertiaryContainer = CosmoTokens.Color.VioletDeep,
    background = CosmoTokens.Color.Asphalt,
    onBackground = CosmoTokens.Color.SignalText,
    surface = CosmoTokens.Color.Asphalt,
    onSurface = CosmoTokens.Color.SignalText,
    surfaceVariant = CosmoTokens.Color.SurfaceHighest,
    onSurfaceVariant = CosmoTokens.Color.SignalTextWarm,
    inverseSurface = CosmoTokens.Color.SignalText,
    inverseOnSurface = CosmoTokens.Color.SurfaceHigh,
    inversePrimary = CosmoTokens.Color.OrangeHot,
    surfaceTint = CosmoTokens.Color.Orange,
    outline = CosmoTokens.Color.SignalText,
    outlineVariant = CosmoTokens.Color.Outline,
    error = CosmoTokens.Color.Error,
    onError = CosmoTokens.Color.Ink,
    errorContainer = CosmoTokens.Color.ErrorContainer,
    onErrorContainer = CosmoTokens.Color.Error
)

private val DarkScheme = darkColorScheme(
    primary = CosmoTokens.Color.Orange,
    onPrimary = CosmoTokens.Color.OrangeDeep,
    primaryContainer = CosmoTokens.Color.OrangeHot,
    onPrimaryContainer = CosmoTokens.Color.OrangeDeep,
    secondary = CosmoTokens.Color.White,
    onSecondary = CosmoTokens.Color.LimeDeep,
    secondaryContainer = CosmoTokens.Color.Lime,
    onSecondaryContainer = CosmoTokens.Color.LimeDeep,
    tertiary = CosmoTokens.Color.Violet,
    onTertiary = CosmoTokens.Color.VioletDeep,
    tertiaryContainer = CosmoTokens.Color.VioletHot,
    onTertiaryContainer = CosmoTokens.Color.VioletDeep,
    background = CosmoTokens.Color.Asphalt,
    onBackground = CosmoTokens.Color.SignalText,
    surface = CosmoTokens.Color.Asphalt,
    onSurface = CosmoTokens.Color.SignalText,
    surfaceVariant = CosmoTokens.Color.SurfaceHighest,
    onSurfaceVariant = CosmoTokens.Color.SignalTextWarm,
    inverseSurface = CosmoTokens.Color.SignalText,
    inverseOnSurface = CosmoTokens.Color.SurfaceHigh,
    inversePrimary = CosmoTokens.Color.OrangeHot,
    surfaceTint = CosmoTokens.Color.Orange,
    outline = CosmoTokens.Color.SignalText,
    outlineVariant = CosmoTokens.Color.Outline,
    error = CosmoTokens.Color.Error,
    onError = CosmoTokens.Color.Ink,
    errorContainer = CosmoTokens.Color.ErrorContainer,
    onErrorContainer = CosmoTokens.Color.Error
)

/**
 * Root theme entry. Installs [LocalCosmoTokens] (override via [tokens]) and, optionally, a
 * wallpaper-derived palette into [LocalWallpaperPalette]. The Material color scheme is fixed —
 * wallpaper colors only flow through [CosmoColor.PaletteRef] consumers, not through M3 roles, so
 * existing chrome stays stable while opt-in surfaces (e.g. accent stickers) can react.
 */
@Composable
fun CosmoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    tokens: CosmoTokenSet = CosmoTokenSet(),
    wallpaperDriven: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (wallpaperDriven) rememberWallpaperPalette() else WallpaperPalette.Empty
    CompositionLocalProvider(
        LocalCosmoTokens provides tokens,
        LocalWallpaperPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = CosmoTypography,
            shapes = CosmoShapes,
            content = content
        )
    }
}
