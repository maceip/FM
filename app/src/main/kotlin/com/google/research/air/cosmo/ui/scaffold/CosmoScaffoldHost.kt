package com.google.research.air.cosmo.ui.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

/**
 * Manages a stack of [CosmoScaffoldComponent]s — the top one is visible, others may stay
 * resident according to [CosmoScaffoldComponent.survivesPause]. Use [rememberCosmoScaffoldHost] in
 * a screen, push/pop components into [CosmoScaffoldHostState], and render them via
 * [CosmoScaffoldStack].
 */
class CosmoScaffoldHostState internal constructor() {
    internal val stack: MutableList<CosmoScaffoldComponent> = mutableStateListOf()

    val top: CosmoScaffoldComponent? get() = stack.lastOrNull()

    fun push(component: CosmoScaffoldComponent) {
        val existing = stack.indexOfFirst { it.key == component.key }
        if (existing != -1) {
            // Re-activate by moving to top instead of duplicating.
            stack.add(stack.removeAt(existing))
        } else {
            stack.add(component)
        }
    }

    /** Returns true if a component was popped, false if the stack was already empty (or only
     *  permanent components remain). */
    fun pop(): Boolean {
        val candidate = stack.lastOrNull() ?: return false
        if (candidate.permanent) return false
        stack.removeAt(stack.size - 1)
        return true
    }
}

@Composable
fun rememberCosmoScaffoldHost(): CosmoScaffoldHostState =
    remember { CosmoScaffoldHostState() }

/**
 * Renders the current top of the stack, runs lifecycle hooks on transitions, and wires back
 * handling so back-press pops a non-permanent component before exiting the screen.
 */
@Composable
fun CosmoScaffoldStack(
    state: CosmoScaffoldHostState,
    modifier: Modifier = Modifier
) {
    val top = state.top

    LaunchedEffect(top?.key) {
        top?.onPreActivate()
        top?.onActivate()
    }
    DisposableEffect(top?.key) {
        val current = top
        onDispose {
            // best-effort: lifecycle hooks are suspend so we can't call them from onDispose;
            // dismissal is signalled via stack mutation instead. Implementations that need a
            // dismiss callback should wrap their action in a coroutine before calling pop().
            current?.let { /* no-op; pop already informed callers */ }
        }
    }

    BackHandler(enabled = top != null && !top.permanent) {
        state.pop()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val component = top ?: return@Box
        if (component.drawBackground) {
            val bg = MaterialTheme.colorScheme.background
            SideEffect { /* hook for future inset/system-bar handling */ }
            Box(Modifier.fillMaxSize().background(bg)) { component.Content() }
        } else {
            component.Content()
        }
    }
}

/** Pre-built component for a transient overlay that owns its content and dismisses on back. */
abstract class CosmoOverlayComponent(override val key: String) : CosmoScaffoldComponent {
    override val drawBackground: Boolean = false
    override val permanent: Boolean = false
}
