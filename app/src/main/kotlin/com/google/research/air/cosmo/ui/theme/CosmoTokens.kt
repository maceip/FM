package com.google.research.air.cosmo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cosmo design tokens. Two parallel access paths:
 *
 *  - `CosmoTokens.Color.Ink` — static defaults (object access). Use from non-composable code or for
 *    literal constants that never need to react to runtime overrides.
 *  - `MaterialTheme.cosmo.color.ink` — CompositionLocal-driven (via [LocalCosmoTokens]). Use from
 *    composables when you want previews/screens/tests to be able to override values.
 *
 * Both paths resolve to the same numbers by default. [CosmoTheme] installs the default [CosmoTokenSet]
 * via [LocalCosmoTokens]; callers can wrap a subtree in `CompositionLocalProvider(LocalCosmoTokens
 * provides ...)` to override.
 */
object CosmoTokens {
    /** Raw Ambient Agent palette, adapted from the prioritized Sticker Slap / Signal Tag / Street Flow ZIP specs. */
    object Color {
        val Ink = androidx.compose.ui.graphics.Color(0xFF070E1D)
        val Asphalt = androidx.compose.ui.graphics.Color(0xFF0C1322)
        val SurfaceLow = androidx.compose.ui.graphics.Color(0xFF151B2B)
        val Surface = androidx.compose.ui.graphics.Color(0xFF191F2F)
        val SurfaceHigh = androidx.compose.ui.graphics.Color(0xFF232A3A)
        val SurfaceHighest = androidx.compose.ui.graphics.Color(0xFF2E3445)
        val SurfaceBright = androidx.compose.ui.graphics.Color(0xFF32394A)
        val SignalText = androidx.compose.ui.graphics.Color(0xFFDCE2F8)
        val SignalTextWarm = androidx.compose.ui.graphics.Color(0xFFE4BEB1)
        val Outline = androidx.compose.ui.graphics.Color(0xFFAB897D)
        val OutlineDim = androidx.compose.ui.graphics.Color(0xFF5B4137)
        val Orange = androidx.compose.ui.graphics.Color(0xFFFFB59A)
        val OrangeHot = androidx.compose.ui.graphics.Color(0xFFFF5C00)
        val OrangeDeep = androidx.compose.ui.graphics.Color(0xFF521800)
        val Lime = androidx.compose.ui.graphics.Color(0xFFA9F900)
        val LimeDim = androidx.compose.ui.graphics.Color(0xFF94DB00)
        val LimeDeep = androidx.compose.ui.graphics.Color(0xFF121F00)
        val Violet = androidx.compose.ui.graphics.Color(0xFFECB2FF)
        val VioletHot = androidx.compose.ui.graphics.Color(0xFFD060FF)
        val VioletDeep = androidx.compose.ui.graphics.Color(0xFF4B0067)
        val Paper = androidx.compose.ui.graphics.Color(0xFFFFDBCE)
        val White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        val Error = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
        val ErrorContainer = androidx.compose.ui.graphics.Color(0xFF93000A)
    }

    /** Shared layout rhythm; prefer these over scattered magic numbers in chrome. */
    object Spacing {
        val screenHorizontal: Dp = 20.dp
        val screenVertical: Dp = 20.dp
        val chromeHorizontal: Dp = 24.dp
        val chromeVertical: Dp = 16.dp
        val chromeTitleGap: Dp = 10.dp
        val chromeSmallGap: Dp = 8.dp
        val bottomBarMinHeight: Dp = 88.dp
        val railWidth: Dp = 112.dp
        val railVerticalPadding: Dp = 16.dp
        val navRailDotStrip: Dp = 4.dp
        val navRailCellMinHeight: Dp = 78.dp
        val navIconSize: Dp = 24.dp
        val navSelectionBandHeight: Dp = 10.dp
        val navSelectionDotRowWidth: Dp = 44.dp
        val navSelectedIndicatorHeight: Dp = 8.dp
        val navCellVerticalPadding: Dp = 10.dp
        val glassScreenPadding: Dp = 24.dp
        val glassSectionGap: Dp = 12.dp
        val taskMonitorOuter: Dp = 16.dp
        val taskMonitorBubbleVertical: Dp = 6.dp
        val taskMonitorBubbleInner: Dp = 12.dp
        val taskMonitorHistoryGap: Dp = 8.dp
        val stickerGap: Dp = 8.dp
        val stickerOverlap: Dp = (-12).dp
        val stickerPadding: Dp = 16.dp
    }

    /** Corner geometry for [CosmoShapes] — keep in sync with Material shape slots. */
    object Shape {
        val cornerExtraSmall: Dp = 8.dp
        val cornerSmall: Dp = 16.dp
        val cornerMedium: Dp = 24.dp
        val cornerLarge: Dp = 32.dp
        val cornerExtraLarge: Dp = 48.dp
        val pill: Dp = 999.dp
    }

    /** Structural depth; Ambient Agent uses hard sticker offsets instead of soft shadows. */
    object Stroke {
        val thin: Dp = 2.dp
        val medium: Dp = 3.dp
        val heavy: Dp = 4.dp
        val slap: Dp = 6.dp
    }

    object Decal {
        val shadowOffset: Dp = 4.dp
        val tagShadowOffset: Dp = 2.dp
        val dotGrid: Dp = 14.dp
        val dotRadius: Dp = 1.dp
    }
}

/**
 * Reactive mirror of [CosmoTokens] suitable for [LocalCosmoTokens]. Defaults to the static values;
 * pass an override to [CosmoTheme] (or a `CompositionLocalProvider`) to swap them for a subtree.
 */
data class CosmoTokenSet(
    val color: CosmoColorPalette = CosmoColorPalette(),
    val spacing: CosmoSpacingTokens = CosmoSpacingTokens(),
    val shape: CosmoShapeTokens = CosmoShapeTokens(),
    val stroke: CosmoStrokeTokens = CosmoStrokeTokens(),
    val decal: CosmoDecalTokens = CosmoDecalTokens()
)

data class CosmoColorPalette(
    val ink: Color = CosmoTokens.Color.Ink,
    val asphalt: Color = CosmoTokens.Color.Asphalt,
    val surfaceLow: Color = CosmoTokens.Color.SurfaceLow,
    val surface: Color = CosmoTokens.Color.Surface,
    val surfaceHigh: Color = CosmoTokens.Color.SurfaceHigh,
    val surfaceHighest: Color = CosmoTokens.Color.SurfaceHighest,
    val surfaceBright: Color = CosmoTokens.Color.SurfaceBright,
    val signalText: Color = CosmoTokens.Color.SignalText,
    val signalTextWarm: Color = CosmoTokens.Color.SignalTextWarm,
    val outline: Color = CosmoTokens.Color.Outline,
    val outlineDim: Color = CosmoTokens.Color.OutlineDim,
    val orange: Color = CosmoTokens.Color.Orange,
    val orangeHot: Color = CosmoTokens.Color.OrangeHot,
    val orangeDeep: Color = CosmoTokens.Color.OrangeDeep,
    val lime: Color = CosmoTokens.Color.Lime,
    val limeDim: Color = CosmoTokens.Color.LimeDim,
    val limeDeep: Color = CosmoTokens.Color.LimeDeep,
    val violet: Color = CosmoTokens.Color.Violet,
    val violetHot: Color = CosmoTokens.Color.VioletHot,
    val violetDeep: Color = CosmoTokens.Color.VioletDeep,
    val paper: Color = CosmoTokens.Color.Paper,
    val white: Color = CosmoTokens.Color.White,
    val error: Color = CosmoTokens.Color.Error,
    val errorContainer: Color = CosmoTokens.Color.ErrorContainer
)

data class CosmoSpacingTokens(
    val screenHorizontal: Dp = CosmoTokens.Spacing.screenHorizontal,
    val screenVertical: Dp = CosmoTokens.Spacing.screenVertical,
    val chromeHorizontal: Dp = CosmoTokens.Spacing.chromeHorizontal,
    val chromeVertical: Dp = CosmoTokens.Spacing.chromeVertical,
    val chromeTitleGap: Dp = CosmoTokens.Spacing.chromeTitleGap,
    val chromeSmallGap: Dp = CosmoTokens.Spacing.chromeSmallGap,
    val bottomBarMinHeight: Dp = CosmoTokens.Spacing.bottomBarMinHeight,
    val railWidth: Dp = CosmoTokens.Spacing.railWidth,
    val railVerticalPadding: Dp = CosmoTokens.Spacing.railVerticalPadding,
    val navRailDotStrip: Dp = CosmoTokens.Spacing.navRailDotStrip,
    val navRailCellMinHeight: Dp = CosmoTokens.Spacing.navRailCellMinHeight,
    val navIconSize: Dp = CosmoTokens.Spacing.navIconSize,
    val navSelectionBandHeight: Dp = CosmoTokens.Spacing.navSelectionBandHeight,
    val navSelectionDotRowWidth: Dp = CosmoTokens.Spacing.navSelectionDotRowWidth,
    val navSelectedIndicatorHeight: Dp = CosmoTokens.Spacing.navSelectedIndicatorHeight,
    val navCellVerticalPadding: Dp = CosmoTokens.Spacing.navCellVerticalPadding,
    val glassScreenPadding: Dp = CosmoTokens.Spacing.glassScreenPadding,
    val glassSectionGap: Dp = CosmoTokens.Spacing.glassSectionGap,
    val taskMonitorOuter: Dp = CosmoTokens.Spacing.taskMonitorOuter,
    val taskMonitorBubbleVertical: Dp = CosmoTokens.Spacing.taskMonitorBubbleVertical,
    val taskMonitorBubbleInner: Dp = CosmoTokens.Spacing.taskMonitorBubbleInner,
    val taskMonitorHistoryGap: Dp = CosmoTokens.Spacing.taskMonitorHistoryGap,
    val stickerGap: Dp = CosmoTokens.Spacing.stickerGap,
    val stickerOverlap: Dp = CosmoTokens.Spacing.stickerOverlap,
    val stickerPadding: Dp = CosmoTokens.Spacing.stickerPadding
)

data class CosmoShapeTokens(
    val cornerExtraSmall: Dp = CosmoTokens.Shape.cornerExtraSmall,
    val cornerSmall: Dp = CosmoTokens.Shape.cornerSmall,
    val cornerMedium: Dp = CosmoTokens.Shape.cornerMedium,
    val cornerLarge: Dp = CosmoTokens.Shape.cornerLarge,
    val cornerExtraLarge: Dp = CosmoTokens.Shape.cornerExtraLarge,
    val pill: Dp = CosmoTokens.Shape.pill
)

data class CosmoStrokeTokens(
    val thin: Dp = CosmoTokens.Stroke.thin,
    val medium: Dp = CosmoTokens.Stroke.medium,
    val heavy: Dp = CosmoTokens.Stroke.heavy,
    val slap: Dp = CosmoTokens.Stroke.slap
)

data class CosmoDecalTokens(
    val shadowOffset: Dp = CosmoTokens.Decal.shadowOffset,
    val tagShadowOffset: Dp = CosmoTokens.Decal.tagShadowOffset,
    val dotGrid: Dp = CosmoTokens.Decal.dotGrid,
    val dotRadius: Dp = CosmoTokens.Decal.dotRadius
)

/**
 * CompositionLocal carrying the active [CosmoTokenSet]. Read via [MaterialTheme.cosmo]; override
 * via `CompositionLocalProvider(LocalCosmoTokens provides ...)`.
 */
val LocalCosmoTokens = staticCompositionLocalOf { CosmoTokenSet() }

/** Reactive accessor for the active token set. */
val MaterialTheme.cosmo: CosmoTokenSet
    @Composable
    @ReadOnlyComposable
    get() = LocalCosmoTokens.current
