package com.google.research.air.cosmo.presence.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(modifier.fillMaxSize()) {
        when (uiState.route) {
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
                            .padding(end = 0.dp),
                    onTap = { onEvent(PresenceOverlayEvent.ShowCards) }
                )
            PresenceOverlayRoute.CARD_STACK -> {
                TabSurface(
                    expanded = true,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 0.dp, bottom = 220.dp),
                    onTap = { onEvent(PresenceOverlayEvent.Collapse) }
                )
                val ready = uiState.presenceState as? PresenceState.Ready
                if (ready != null) {
                    CardStackSurface(
                        cards = ready.cards,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp, top = 180.dp),
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
