package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A single-line text that scrolls horizontally **only when it overflows**, with horizontal fading
 * edges so the scroll loop reads cleanly. Use for long titles in chrome (top bar, sticker tags,
 * task monitor headers). Adapted from Kvaesitso's `MarqueeText`.
 */
@Composable
fun CosmoMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    var overflow by remember(text) { mutableStateOf(false) }
    val effectModifier = if (overflow) {
        Modifier
            .horizontalFadingEdges(start = 12.dp, end = 12.dp)
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                repeatDelayMillis = 1200,
                initialDelayMillis = 600
            )
    } else {
        Modifier
    }
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.then(effectModifier),
        onTextLayout = { result: TextLayoutResult ->
            overflow = result.hasVisualOverflow
        }
    )
}
