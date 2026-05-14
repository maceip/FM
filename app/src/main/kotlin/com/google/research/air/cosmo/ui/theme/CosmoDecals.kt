package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared sticker/decal primitives for the Ambient Agent visual system.
 *
 * The reference designs use hard outline layers and offset vinyl-like depth, not Material elevation.
 */
@Composable
fun CosmoStickerSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    borderColor: Color = CosmoTokens.Color.Ink,
    borderWidth: Dp = CosmoTokens.Stroke.heavy,
    shadowColor: Color = CosmoTokens.Color.Ink,
    shadowOffset: Dp = CosmoTokens.Decal.shadowOffset,
    rotationDegrees: Float = 0f,
    content: @Composable () -> Unit
) {
    Box(modifier.graphicsLayer(rotationZ = rotationDegrees)) {
        if (shadowOffset > 0.dp) {
            Surface(
                modifier = Modifier.matchParentSize().offset(x = shadowOffset, y = shadowOffset),
                shape = shape,
                color = shadowColor,
                content = {}
            )
        }
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            border = BorderStroke(borderWidth, borderColor),
            content = content
        )
    }
}

@Composable
fun CosmoSignalTag(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    rotationDegrees: Float = -6f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
) {
    val tokens = MaterialTheme.cosmo
    CosmoStickerSurface(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(tokens.shape.pill),
        borderWidth = tokens.stroke.medium,
        shadowOffset = tokens.decal.tagShadowOffset,
        rotationDegrees = rotationDegrees
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(contentPadding),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun CosmoBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = MaterialTheme.cosmo
    val background = MaterialTheme.colorScheme.background
    val dot = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.background(background)) {
        val dotStep = tokens.decal.dotGrid
        val dotR = tokens.decal.dotRadius
        Canvas(Modifier.matchParentSize()) {
            val step = dotStep.toPx()
            val radius = dotR.toPx()
            var y = step / 2f
            while (y < size.height) {
                var x = step / 2f
                while (x < size.width) {
                    drawCircle(
                        color = dot.copy(alpha = 0.32f),
                        radius = radius,
                        center = Offset(x, y)
                    )
                    x += step
                }
                y += step
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
