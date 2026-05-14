package com.google.research.air.cosmo.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/**
 * Root theme entry for every `ComponentActivity` that shows Compose UI. Ensures [CosmoTheme] wraps
 * the tree exactly once so [androidx.compose.material3.MaterialTheme] resolves to Cosmo tokens everywhere.
 */
fun ComponentActivity.setCosmoContent(
    wallpaperDriven: Boolean = false,
    tokens: CosmoTokenSet = CosmoTokenSet(),
    content: @Composable () -> Unit
) {
    setContent {
        CosmoTheme(tokens = tokens, wallpaperDriven = wallpaperDriven, content = content)
    }
}
