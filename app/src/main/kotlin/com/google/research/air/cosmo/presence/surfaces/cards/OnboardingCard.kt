package com.google.research.air.cosmo.presence.surfaces.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.CardPayload
import com.google.research.air.cosmo.presence.OnboardingCheckpoint
import com.google.research.air.cosmo.presence.PresenceTokens
import com.google.research.air.cosmo.presence.surfaces.InnerPanel

@Composable
internal fun OnboardingContent(data: CardPayload.Onboarding) {
    Column(Modifier.testTag("onboardingCard"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardHeader(categoryLabel = "ONBOARDING", title = data.title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("SETUP FLOW")
            MonoLabel("${data.step} / ${data.totalSteps}")
        }
        InnerPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    data.body,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
                CheckpointRail(data.checkpoints)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF4266A9).copy(alpha = 0.72f))
                .border(1.dp, PresenceTokens.Palette.BorderBright, RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "NEXT: SETTINGS",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun CheckpointRail(checkpoints: List<OnboardingCheckpoint>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        checkpoints.take(4).forEach { checkpoint ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (checkpoint.complete) Color(0xFF4D73B9) else Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, PresenceTokens.Palette.BorderBright, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (checkpoint.complete) "OK" else "-",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    checkpoint.label.uppercase(),
                    color = PresenceTokens.Palette.OnSurfaceDim,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
