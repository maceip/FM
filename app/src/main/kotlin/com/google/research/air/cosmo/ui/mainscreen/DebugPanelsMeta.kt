package com.google.research.air.cosmo.ui.mainscreen

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.research.air.cosmo.logging.ObservabilitySchema
import com.google.research.air.cosmo.skills.SkillCategory
import com.google.research.air.cosmo.skills.SkillRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SkillsDebugPanel
@Inject
constructor(
    private val skills: SkillRegistry
) : BaseDebugPanel(22, "skills", "Skills") {
    @Composable override fun Content() {
        DebugCard(title) {
            MetricRow("Total skills", skills.list().size.toString())
            SkillCategory.entries.forEach { category ->
                MetricRow(category.name, skills.listByCategory(category).size.toString())
            }
        }
    }
}

class ModelAssetsDebugPanel
@Inject
constructor(
    @ApplicationContext private val context: Context
) : BaseDebugPanel(23, "model_assets", "Model assets") {
    @Composable override fun Content() {
        val manifest = remember {
            runCatching {
                context.assets.open("model-manifest.json").bufferedReader().use { it.readText() }
            }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
        }
        DebugCard(title) {
            Text(manifest.take(TEXT_PREVIEW_CHARS))
        }
    }
}

class ObservabilitySchemaDebugPanel
@Inject
constructor(
    private val schema: ObservabilitySchema
) : BaseDebugPanel(24, "observability_schema", "Observability schema") {
    @Composable override fun Content() {
        DebugCard(title) {
            MetricRow("Log tags", schema.logTags.size.toString())
            MetricRow("Flogger events", schema.floggerEvents.size.toString())
            MetricRow("Firebase events (Flogger catalog)", schema.firebaseEvents.size.toString())
            MetricRow("Firebase TelemetryEvent defs", schema.telemetryFirebaseEvents.size.toString())
            schema.logTags.take(PREVIEW_LIMIT).forEach { Text(it.tag) }
        }
    }
}
