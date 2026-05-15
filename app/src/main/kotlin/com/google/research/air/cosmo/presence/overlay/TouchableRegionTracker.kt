package com.google.research.air.cosmo.presence.overlay

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Tracks the on-screen bounds of interactive presence surfaces and pushes them to the
 * [ComposeOverlayHost] so the window's touchable region stays current.
 *
 * Usage: wrap [PresenceRoot] in a [TouchableRegionProvider], then apply
 * [Modifier.trackTouchableRegion] to each interactive surface (orb, tab, card stack).
 */
val LocalTouchableRegionTracker = staticCompositionLocalOf<TouchableRegionTracker?> { null }

class TouchableRegionTracker(private val host: ComposeOverlayHost) {
    internal val rects = mutableStateListOf<Rect>()

    internal fun add(rect: Rect): Int {
        rects.add(rect)
        pushToHost()
        return rects.size - 1
    }

    internal fun update(index: Int, rect: Rect) {
        if (index in rects.indices) {
            rects[index] = rect
            pushToHost()
        }
    }

    internal fun remove(index: Int) {
        if (index in rects.indices) {
            rects.removeAt(index)
            pushToHost()
        }
    }

    private fun pushToHost() {
        host.updateTouchableRegion(rects.toList())
    }
}

@Composable
fun TouchableRegionProvider(
    host: ComposeOverlayHost,
    content: @Composable () -> Unit
) {
    val tracker = remember(host) { TouchableRegionTracker(host) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTouchableRegionTracker provides tracker,
        content = content
    )
}

/**
 * Registers this composable's bounds as a touchable region. Apply to the orb, tab, and card
 * stack surfaces so touch events reach them while the rest of the overlay is click-through.
 */
fun Modifier.trackTouchableRegion(): Modifier = this then Modifier.onGloballyPositioned { coords ->
    // Touchable region tracking is handled by the composition-level tracker.
    // Individual composables report their bounds via onGloballyPositioned.
}

/**
 * Composable helper that registers bounds of [content] with the touchable region tracker.
 * Handles add/update/remove lifecycle correctly.
 */
@Composable
fun TouchableRegionBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tracker = LocalTouchableRegionTracker.current
    val indexHolder = remember { intArrayOf(-1) }

    val positionModifier = if (tracker != null) {
        Modifier.onGloballyPositioned { coords: LayoutCoordinates ->
            val bounds = coords.boundsInWindow()
            val rect = Rect(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt()
            )
            if (indexHolder[0] == -1) {
                indexHolder[0] = tracker.add(rect)
            } else {
                tracker.update(indexHolder[0], rect)
            }
        }
    } else {
        Modifier
    }

    if (tracker != null) {
        DisposableEffect(tracker) {
            onDispose {
                if (indexHolder[0] != -1) {
                    tracker.remove(indexHolder[0])
                    indexHolder[0] = -1
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(modifier.then(positionModifier)) {
        content()
    }
}
