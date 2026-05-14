package com.google.research.air.cosmo.ui.mainscreen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import javax.inject.Inject

class GatekeeperLiveDebugPanel
@Inject
constructor(
    private val gatekeeperOutput: GatekeeperOutputSource
) : BaseDebugPanel(12, "gatekeeper_live", "Live gatekeeper") {
    @Composable override fun Content() {
        val results = remember { mutableStateListOf<GatekeepingResult>() }
        LaunchedEffect(gatekeeperOutput) {
            gatekeeperOutput.results.collect {
                results.add(0, it)
                while (results.size > PREVIEW_LIMIT) results.removeAt(results.lastIndex)
            }
        }
        DebugCard(title) {
            MetricRow("Recent results", results.size.toString())
            results.forEach { Text("${it.detector}: ${it.detectedSkills.keys.joinToString()}") }
        }
    }
}

class GatekeeperScoresDebugPanel
@Inject
constructor(
    private val gatekeeperOutput: GatekeeperOutputSource
) : BaseDebugPanel(13, "gatekeeper_scores", "Gatekeeper scores") {
    @Composable override fun Content() {
        val results = remember { mutableStateListOf<GatekeepingResult>() }
        LaunchedEffect(gatekeeperOutput) {
            gatekeeperOutput.results.collect {
                results.add(0, it)
                while (results.size > 1) results.removeAt(results.lastIndex)
            }
        }
        DebugCard(title) {
            val latest = results.firstOrNull()
            MetricRow("Latest model", latest?.modelId ?: "none")
            latest?.skillScores?.forEach { MetricRow(it.skillId, "${it.confidence} ${it.detector}") }
        }
    }
}
