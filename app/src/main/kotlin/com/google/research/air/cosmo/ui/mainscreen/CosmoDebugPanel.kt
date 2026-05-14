package com.google.research.air.cosmo.ui.mainscreen

import androidx.compose.runtime.Composable

interface CosmoDebugPanel {
    val order: Int
    val id: String
    val title: String

    @Composable
    fun Content()
}

abstract class BaseDebugPanel(
    override val order: Int,
    override val id: String,
    override val title: String
) : CosmoDebugPanel

internal const val PREVIEW_LIMIT = 5
internal const val TEXT_PREVIEW_CHARS = 1_200
