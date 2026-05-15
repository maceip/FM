package com.google.research.air.cosmo.presence.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceController
import com.google.research.air.cosmo.presence.PresenceMode
import com.google.research.air.cosmo.presence.PresenceState
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.gestures.EdgeDockState
import com.google.research.air.cosmo.presence.gestures.edgeDockable
import com.google.research.air.cosmo.presence.gestures.rememberEdgeDockState
import com.google.research.air.cosmo.presence.surfaces.CardStackSurface
import com.google.research.air.cosmo.presence.surfaces.OrbSurface
import com.google.research.air.cosmo.presence.surfaces.TabSurface

private val orbCubicBezier = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
fun PresenceRoot(
    controller: PresenceController,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    val dockState = rememberEdgeDockState()

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                when {
                    targetState is PresenceState.Hidden ->
                        fadeIn(tween(0)) togetherWith fadeOut(tween(200))
                    initialState is PresenceState.Hidden -> {
                        val enterSpec = tween<Float>(PresenceTokens.Motion.orbIdleToActiveMs, easing = orbCubicBezier)
                        (fadeIn(enterSpec) + scaleIn(enterSpec, initialScale = 0.6f)) togetherWith fadeOut(tween(0))
                    }
                    targetState is PresenceState.Ready -> {
                        val enterSlide = slideInHorizontally(
                            spring(PresenceTokens.Motion.tabExpandDamping, PresenceTokens.Motion.tabExpandStiffness)
                        ) { it / 2 }
                        (enterSlide + fadeIn(tween(200))) togetherWith fadeOut(tween(150))
                    }
                    initialState is PresenceState.Ready -> {
                        val exitSlide = slideOutHorizontally(
                            spring(PresenceTokens.Motion.snapDamping, PresenceTokens.Motion.snapStiffness)
                        ) { it / 2 }
                        fadeIn(tween(200)) togetherWith (exitSlide + fadeOut(tween(150)))
                    }
                    else ->
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                }
            },
            label = "presenceState"
        ) { currentState ->
            when (currentState) {
                is PresenceState.Hidden -> Box(Modifier)
                is PresenceState.Dismissed -> Box(Modifier)

                is PresenceState.Parked -> {
                    TouchableRegionBox {
                        ParkedSurface(
                            mode = currentState.mode,
                            activity = currentState.activity,
                            dockState = dockState,
                            onTap = { controller.requestExpand() },
                            onLongPress = { controller.setListening() },
                            onDismiss = { controller.dismissCurrent() }
                        )
                    }
                }

                is PresenceState.Working -> {
                    TouchableRegionBox {
                        WorkingSurface(
                            dockState = dockState,
                            pillText = currentState.pillText,
                            onTap = { controller.requestExpand() },
                            onDismiss = { controller.dismissCurrent() }
                        )
                    }
                }

                is PresenceState.NeedsInput -> {
                    TouchableRegionBox {
                        ParkedSurface(
                            mode = PresenceMode.ORB,
                            activity = OrbActivity.IDLE,
                            dockState = dockState,
                            needsInput = true,
                            onTap = { controller.requestExpand() },
                            onDismiss = { controller.dismissCurrent() }
                        )
                    }
                }

                is PresenceState.Ready -> {
                    TouchableRegionBox(Modifier.fillMaxSize()) {
                        CardStackSurface(
                            cards = currentState.cards,
                            onAction = { cardId, actionId ->
                                if (actionId == "collapse") controller.dismissCurrent()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkedSurface(
    mode: PresenceMode,
    activity: OrbActivity,
    dockState: EdgeDockState,
    needsInput: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .offset { IntOffset(dockState.offset.value.x.toInt(), dockState.offset.value.y.toInt()) }
            .edgeDockable(
                state = dockState,
                onTap = onTap,
                onLongPress = onLongPress,
                onDismiss = onDismiss,
                onExpand = onTap
            )
    ) {
        when (mode) {
            PresenceMode.ORB -> OrbSurface(
                activity = activity,
                needsInput = needsInput
            )
            PresenceMode.TAB -> TabSurface(
                expanded = false,
                onTap = onTap
            )
        }
    }
}

@Composable
private fun WorkingSurface(
    dockState: EdgeDockState,
    pillText: String?,
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .offset { IntOffset(dockState.offset.value.x.toInt(), dockState.offset.value.y.toInt()) }
            .edgeDockable(
                state = dockState,
                onTap = onTap,
                onDismiss = onDismiss,
                onExpand = onTap
            )
    ) {
        TabSurface(
            expanded = true,
            statusLabel = pillText?.uppercase() ?: "WORKING",
            onTap = onTap
        )
    }
}
