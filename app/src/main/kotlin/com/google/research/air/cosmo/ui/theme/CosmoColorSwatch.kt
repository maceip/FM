package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A round color chip that always renders its border and (optional) check icon in a tone with
 * enough contrast against [color] to be legible. Useful in palette/theme pickers. Adapted from
 * Kvaesitso's `ColorSwatch`.
 *
 * Contrast logic uses [Color.luminance] (sRGB-relative luminance) rather than HCT tone since we
 * don't yet pull in `material-color-utilities`; the cutoff at 0.5 matches the
 * WCAG-bright-vs-dark heuristic and works for the palettes we ship.
 */
@Composable
fun CosmoColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null
) {
    val accent = if (color.luminance() > 0.5f) Color.Black else Color.White
    Surface(
        color = color,
        shape = CircleShape,
        border = BorderStroke(2.dp, accent.copy(alpha = 0.7f)),
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (selected) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent
                )
            }
        }
    }
}
