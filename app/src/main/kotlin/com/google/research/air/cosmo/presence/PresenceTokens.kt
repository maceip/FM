package com.google.research.air.cosmo.presence

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tactical Glass Interface tokens extracted from the design team's DESIGN.md.
 * These are separate from [CosmoTokens] — the presence layer has its own visual language
 * (aerospace/HUD) that doesn't map to the ambient-agent sticker palette.
 */
object PresenceTokens {

    object Palette {
        val Background = Color(0xFF050914)
        val SurfaceGlass = Color(0xAA0D1117)
        val SurfaceGlassMedium = Color(0xC0101520)
        val SurfaceGlassHigh = Color(0xD0141A26)
        val SurfaceDark = Color(0xFF111318)
        val SurfaceContainer = Color(0xFF1E2024)
        val SurfaceContainerHigh = Color(0xFF282A2E)

        val OnSurface = Color(0xFFE2E2E8)
        val OnSurfaceDim = Color(0xFF7C8798)
        val OnSurfaceVariant = Color(0xFFBAC9CC)

        val BorderDefault = Color(0x33B8C7E0)
        val BorderBright = Color(0x80CFE3FF)
        val BorderDim = Color(0x1FB8C7E0)

        val Cyan = Color(0xFF00E5FF)
        val CyanDim = Color(0xFF38BDF8)
        val CyanGlow = Color(0x9900E5FF)
        val Blue = Color(0xFF0356FF)

        val AcidGreen = Color(0xFF7AFF00)
        val AcidGreenGlow = Color(0x997AFF00)
        val AcidYellow = Color(0xFFFFE600)
        val AcidYellowGlow = Color(0x99FFE600)
        val Magenta = Color(0xFFFF00E5)
        val AlertRed = Color(0xFFFF3D00)

        val GlassInnerGlow = Color(0x1ACFE3FF)
    }

    object Size {
        val orbMin: Dp = 36.dp
        val orbMax: Dp = 48.dp
        val orbDefault: Dp = 48.dp
        val orbCorner: Dp = 14.dp

        val tabIdleWidth: Dp = 12.dp
        val tabIdleHeight: Dp = 180.dp
        val tabExpandedWidth: Dp = 350.dp
        val tabExpandedHeight: Dp = 160.dp
        val tabCorner: Dp = 80.dp

        val cardCorner: Dp = 16.dp
        val cardStackMaxWidth: Dp = 360.dp
        val cardGlowStripWidth: Dp = 2.dp
        val cardInnerCorner: Dp = 8.dp
        val cardIconSize: Dp = 32.dp
        val cardDotSize: Dp = 6.dp
    }

    object Ring {
        val innerRadius: Dp = 12.dp
        val outerRadius: Dp = 18.dp
        const val dashOn: Float = 6f
        const val dashOff: Float = 6f
        const val dotOn: Float = 3f
        const val dotOff: Float = 4f
        const val strokeWidth: Float = 2f
        const val strokeWidthHeavy: Float = 4f
        const val spinDurationMs: Int = 10_000
        const val spinDurationSlowMs: Int = 12_000
    }

    object Blur {
        val glass: Dp = 16.dp
        val glassMedium: Dp = 20.dp
        val glassLight: Dp = 8.dp
    }

    object Glow {
        val orbShadowRadius: Dp = 30.dp
        val tabLensFlareWidth: Dp = 60.dp
        val tabLensFlareHeight: Dp = 1.dp
        val cardEdgeGlowWidth: Dp = 2.dp
    }

    object Spacing {
        val unit: Dp = 4.dp
        val cardPadding: Dp = 16.dp
        val cardGap: Dp = 16.dp
        val sectionGap: Dp = 12.dp
        val actionGridGap: Dp = 8.dp
    }

    object Motion {
        const val orbIdleToActiveMs: Int = 400
        const val tabIdleToActiveMs: Int = 200
        const val cardDismissMs: Int = 250

        const val snapDamping: Float = 0.7f
        const val snapStiffness: Float = 800f
        const val dragDamping: Float = 0.9f
        const val dragStiffness: Float = 300f
        const val cardLiftDamping: Float = 0.7f
        const val cardLiftStiffness: Float = 600f
        const val tabExpandDamping: Float = 0.8f
        const val tabExpandStiffness: Float = 400f

        const val tapMaxDisplacementDp: Float = 8f
        const val tapMaxDurationMs: Long = 200
        const val longPressMinDurationMs: Long = 400
        const val longPressMaxDisplacementDp: Float = 16f
        const val flickVelocityThresholdDpPerSec: Float = 1500f
        const val expandVelocityThresholdDpPerSec: Float = 800f
        const val anchorProximityDp: Float = 48f
    }

    object RotationSpeed {
        const val fast: Int = 6_000
        const val mediumFast: Int = 8_000
        const val medium: Int = 10_000
    }

    object CardStack {
        val cardLift: Dp = 4.dp
        val interCardSpacing: Dp = 8.dp
        const val depthScale: Float = 0.95f
    }
}
