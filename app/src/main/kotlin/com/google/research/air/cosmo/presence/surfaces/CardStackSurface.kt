package com.google.research.air.cosmo.presence.surfaces

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.PresenceCard
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.gestures.cardSwipeToDismiss
import com.google.research.air.cosmo.presence.surfaces.cards.ActionButtonGrid
import com.google.research.air.cosmo.presence.surfaces.cards.BrowserContent
import com.google.research.air.cosmo.presence.surfaces.cards.CalendarTimeContent
import com.google.research.air.cosmo.presence.surfaces.cards.CallingContent
import com.google.research.air.cosmo.presence.surfaces.cards.DocumentContent
import com.google.research.air.cosmo.presence.surfaces.cards.ListTasksContent
import com.google.research.air.cosmo.presence.surfaces.cards.MediaImageContent
import com.google.research.air.cosmo.presence.surfaces.cards.MediaPlaybackContent
import com.google.research.air.cosmo.presence.surfaces.cards.NavigationContent
import com.google.research.air.cosmo.presence.surfaces.cards.MessagingContent
import com.google.research.air.cosmo.presence.surfaces.cards.OnboardingContent
import com.google.research.air.cosmo.presence.surfaces.cards.PermissionConfirmationContent
import com.google.research.air.cosmo.presence.surfaces.cards.PeopleContextContent
import com.google.research.air.cosmo.presence.surfaces.cards.SettingsContent
import com.google.research.air.cosmo.presence.surfaces.cards.SourceCitationContent

@Composable
fun CardStackSurface(
    cards: List<PresenceCard>,
    modifier: Modifier = Modifier,
    onAction: (cardId: String, actionId: String) -> Unit = { _, _ -> },
    onCardSelected: (cardId: String) -> Unit = {},
    onDismiss: (cardId: String) -> Unit = {}
) {
    if (cards.size <= 1) {
        Column(
            modifier = modifier
                .padding(horizontal = PresenceTokens.Spacing.cardPadding)
                .widthIn(max = PresenceTokens.Size.cardStackMaxWidth)
                .testTag("cardStack"),
            verticalArrangement = Arrangement.spacedBy(PresenceTokens.Spacing.cardGap)
        ) {
            cards.forEach { card ->
                PresenceCardPanel(card = card, onAction = onAction, onCardSelected = onCardSelected, onDismiss = onDismiss)
            }
        }
        return
    }

    DepthCardStack(
        cards = cards,
        modifier = modifier
            .padding(horizontal = PresenceTokens.Spacing.cardPadding)
            .widthIn(max = PresenceTokens.Size.cardStackMaxWidth)
            .testTag("cardStack"),
        onAction = onAction,
        onCardSelected = onCardSelected,
        onDismiss = onDismiss
    )
}

@Composable
private fun DepthCardStack(
    cards: List<PresenceCard>,
    modifier: Modifier,
    onAction: (cardId: String, actionId: String) -> Unit,
    onCardSelected: (cardId: String) -> Unit,
    onDismiss: (cardId: String) -> Unit
) {
    val visibleCards = cards.take(4)
    val overflowCount = (cards.size - visibleCards.size).coerceAtLeast(0)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (cards.size <= 3) 14.dp else 8.dp)
    ) {
        visibleCards.forEachIndexed { depth, card ->
            val lift by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "cardDepthLift_${card.id}"
            )
            PresenceCardPanel(
                card = card,
                onAction = onAction,
                onCardSelected = onCardSelected,
                onDismiss = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = (depth * 7).dp)
                    .graphicsLayer {
                        rotationX = depth * 1.4f
                        rotationZ = (depth - 1) * 0.65f
                        scaleX = 1f - depth * 0.022f
                        scaleY = 1f - depth * 0.018f
                        translationY = (1f - lift) * 18f
                        cameraDistance = 18f * density
                    }
                    .drawBehind {
                        if (depth > 0) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))
                                ),
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height)
                            )
                        }
                    }
            )
        }
        if (overflowCount > 0) {
            OverflowPill(
                overflowCount = overflowCount,
                modifier = Modifier
                    .offset(x = 22.dp)
            )
        }
    }
}

@Composable
private fun PresenceCardPanel(
    card: PresenceCard,
    onAction: (cardId: String, actionId: String) -> Unit,
    onCardSelected: (cardId: String) -> Unit,
    onDismiss: (cardId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pressed by remember(card.id) { mutableStateOf(false) }
    var entered by remember(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id) {
        entered = true
    }
    val pressScale by animateFloatAsState(
        targetValue = when {
            !entered -> 0.965f
            pressed -> 1.018f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 760f),
        label = "cardPress_${card.id}"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "cardAlpha_${card.id}"
    )
    GlassPanel(
        showGlowEdge = true,
        fill = PresenceTokens.Palette.SurfaceGlassMedium,
        modifier = modifier
            .cardSwipeToDismiss(onDismiss = { onDismiss(card.id) })
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = cardAlpha
            }
            .pointerInput(card.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onCardSelected(card.id) }
                )
            }
            .testTag("card_${card.id}")
    ) {
        Column(Modifier.padding(PresenceTokens.Spacing.cardPadding)) {
            CardContent(card.payload)
            if (card.actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(PresenceTokens.Palette.BorderDim))
                Spacer(Modifier.height(12.dp))
                ActionButtonGrid(card.id, card.actions, onAction)
            }
        }
    }
}

@Composable
private fun OverflowPill(overflowCount: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(116.dp)
            .height(28.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.54f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                )
                drawRoundRect(
                    color = PresenceTokens.Palette.BorderBright.copy(alpha = 0.34f),
                    size = size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            "+$overflowCount QUEUED",
            color = PresenceTokens.Palette.OnSurfaceVariant,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CardContent(payload: CardPayload) {
    when (payload) {
        is CardPayload.Browser -> BrowserContent(payload)
        is CardPayload.Calling -> CallingContent(payload)
        is CardPayload.Messaging -> MessagingContent(payload)
        is CardPayload.Document -> DocumentContent(payload)
        is CardPayload.ListTasks -> ListTasksContent(payload)
        is CardPayload.CalendarTime -> CalendarTimeContent(payload)
        is CardPayload.Navigation -> NavigationContent(payload)
        is CardPayload.MediaPlayback -> MediaPlaybackContent(payload)
        is CardPayload.PeopleContext -> PeopleContextContent(payload)
        is CardPayload.MediaImage -> MediaImageContent(payload)
        is CardPayload.SourceCitation -> SourceCitationContent(payload)
        is CardPayload.PermissionConfirmation -> PermissionConfirmationContent(payload)
        is CardPayload.Settings -> SettingsContent(payload)
        is CardPayload.Onboarding -> OnboardingContent(payload)
    }
}
