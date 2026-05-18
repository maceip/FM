package com.google.research.air.cosmo.presence.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.presence.CardActionType
import com.google.research.air.cosmo.presence.OrbActivity
import com.google.research.air.cosmo.presence.PresenceState
import com.google.research.air.cosmo.presence.surfaces.CardStackSurface
import com.google.research.air.cosmo.presence.surfaces.OrbSurface
import com.google.research.air.cosmo.presence.surfaces.TabSurface

@Composable
fun PresenceOverlayContent(
    uiState: PresenceOverlayUiState,
    onEvent: (PresenceOverlayEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val predictiveBackModifier =
        Modifier.graphicsLayer {
            val progress = uiState.backProgress.coerceIn(0f, 1f)
            scaleX = 1f - progress * 0.04f
            scaleY = 1f - progress * 0.04f
            translationX = progress * 34f
            alpha = 1f - progress * 0.22f
        }
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.route,
            transitionSpec = {
                (fadeIn(spring(dampingRatio = 0.78f, stiffness = 520f)) +
                    scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f))) togetherWith
                    (fadeOut(spring(dampingRatio = 0.9f, stiffness = 640f)) +
                        scaleOut(targetScale = 0.98f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 640f))) using
                    SizeTransform(clip = false)
            },
            label = "presenceOverlayRoute"
        ) { route ->
        when (route) {
            PresenceOverlayRoute.HIDDEN,
            PresenceOverlayRoute.PERMISSION_REQUIRED,
            PresenceOverlayRoute.ERROR -> Unit
            PresenceOverlayRoute.PARKED ->
                OrbSurface(
                    activity = (uiState.presenceState as? PresenceState.Parked)?.activity ?: OrbActivity.IDLE,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                )
            PresenceOverlayRoute.EXPANDED_TAB ->
                TabSurface(
                    expanded = true,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 0.dp)
                            .then(predictiveBackModifier),
                    onTap = { onEvent(PresenceOverlayEvent.ShowCards) }
                )
            PresenceOverlayRoute.CARD_STACK -> {
                TabSurface(
                    expanded = true,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 0.dp, bottom = 220.dp)
                            .then(predictiveBackModifier),
                    onTap = { onEvent(PresenceOverlayEvent.Collapse) }
                )
                val ready = uiState.presenceState as? PresenceState.Ready
                if (ready != null) {
                    CardStackSurface(
                        cards = ready.cards,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp, top = 180.dp)
                                .then(predictiveBackModifier),
                        onAction = { cardId, actionId ->
                            val action = ready.cards.firstOrNull { it.id == cardId }?.actions?.firstOrNull { it.id == actionId }
                            if (action?.type == CardActionType.COLLAPSE || actionId.contains("collapse", ignoreCase = true)) {
                                onEvent(PresenceOverlayEvent.Collapse)
                            }
                        }
                    )
                }
            }
        }
        }
    }
}
