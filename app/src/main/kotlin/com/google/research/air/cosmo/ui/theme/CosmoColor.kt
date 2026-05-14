package com.google.research.air.cosmo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * A color value that can either be a hard literal or a reference into a runtime palette (e.g.
 * derived from the user's wallpaper). Adapted from Kvaesitso's [ColorRef]/[StaticColor] pattern.
 *
 * Use this when defining a *token* whose appearance should be able to track the active palette;
 * use a plain [Color] (or [CosmoTokens.Color.X]) when you really do want a fixed value.
 */
sealed interface CosmoColor {
    @Composable @ReadOnlyComposable fun resolve(): Color

    /** Fixed color; ignores any active palette. */
    data class Literal(val value: Color) : CosmoColor {
        @Composable @ReadOnlyComposable override fun resolve(): Color = value
    }

    /**
     * Tone reference into the active [LocalWallpaperPalette]. Falls back to [fallback] (or
     * [CosmoTokens.Color.Ink]) when no wallpaper palette is installed.
     *
     * Tone is on the Material-color-utilities scale (0..100). Today we only have access to the
     * 3 wallpaper colors Android exposes (primary/secondary/tertiary) so [tone] is hinted but not
     * applied — wire `material-color-utilities` later for proper HCT tonal scaling.
     */
    data class PaletteRef(
        val role: PaletteRole,
        val tone: Int = 40,
        val fallback: Color = CosmoTokens.Color.Ink
    ) : CosmoColor {
        @Composable @ReadOnlyComposable
        override fun resolve(): Color = LocalWallpaperPalette.current.colorFor(role) ?: fallback
    }
}

/** Roles used by [CosmoColor.PaletteRef] to look up a base color from the active palette. */
enum class PaletteRole { Primary, Secondary, Tertiary }

/** Convenience: build a literal [CosmoColor] inline. */
fun cosmoColor(value: Color): CosmoColor = CosmoColor.Literal(value)

/** Convenience: build a palette reference inline. */
fun cosmoColor(role: PaletteRole, tone: Int = 40, fallback: Color = CosmoTokens.Color.Ink): CosmoColor =
    CosmoColor.PaletteRef(role, tone, fallback)
