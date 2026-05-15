package com.google.research.air.cosmo.presence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.research.air.cosmo.presence.surfaces.CardStackSurface
import com.google.research.air.cosmo.presence.surfaces.OrbSurface
import com.google.research.air.cosmo.presence.surfaces.TabSurface

class PresencePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.fillMaxSize().background(PresenceTokens.Palette.Background)) {
                PresencePreviewContent()
            }
        }
    }
}

@Composable
private fun PresencePreviewContent() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SectionLabel("ORB STATES")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OrbSurface(activity = OrbActivity.IDLE)
                Spacer(Modifier.height(8.dp))
                MiniLabel("IDLE")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OrbSurface(activity = OrbActivity.LISTENING)
                Spacer(Modifier.height(8.dp))
                MiniLabel("LISTENING")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OrbSurface(activity = OrbActivity.IDLE, isWorking = true)
                Spacer(Modifier.height(8.dp))
                MiniLabel("WORKING")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OrbSurface(activity = OrbActivity.IDLE, needsInput = true)
                Spacer(Modifier.height(8.dp))
                MiniLabel("NEEDS INPUT")
            }
        }

        SectionLabel("TAB — IDLE")
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.CenterEnd) {
            TabSurface(expanded = false)
        }

        SectionLabel("TAB — EXPANDED")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TabSurface(expanded = true)
        }

        SectionLabel("CARD STACK")
        CardStackSurface(cards = sampleCards)

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = PresenceTokens.Palette.CyanDim,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(PresenceTokens.Palette.BorderDim))
}

@Composable
private fun MiniLabel(text: String) {
    Text(text, color = PresenceTokens.Palette.OnSurfaceDim, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
}

private val sampleCards = listOf(
    PresenceCard(
        id = "browser",
        payload = CardPayload.Browser(
            query = "quantum computing",
            sources = listOf(
                Source("What is Quantum Computing?", "IBM", 98, "IBM"),
                Source("A Gentle Introduction to Qubits", "arxiv.org", 91, "Q")
            )
        ),
        actions = listOf(
            CardAction("open", "OPEN", "IN BROWSER"),
            CardAction("review", "REVIEW", "SOURCES"),
            CardAction("collapse", "COLLAPSE", "TO EDGE")
        )
    ),
    PresenceCard(
        id = "messaging",
        payload = CardPayload.Messaging(
            body = "Here are the key findings from\nmy research on quantum computing.\nLet me know your thoughts.",
            recipients = listOf(Recipient("Alex Morgan", "alex@quantumlab.com", "AM"))
        ),
        actions = listOf(
            CardAction("send", "SEND", "NOW"),
            CardAction("choose", "CHOOSE", "APP"),
            CardAction("collapse", "COLLAPSE", "TO EDGE")
        )
    ),
    PresenceCard(
        id = "calendar",
        payload = CardPayload.CalendarTime(
            focusMinutes = 25,
            focusSeconds = 0,
            reminders = listOf(
                Reminder("09:00", "Stand Up"),
                Reminder("11:00", "Design Review"),
                Reminder("15:30", "Client Sync")
            )
        ),
        actions = listOf(
            CardAction("start", "START", "TIMER"),
            CardAction("add", "ADD", "REMINDER"),
            CardAction("create", "CREATE", "EVENT")
        )
    )
)
