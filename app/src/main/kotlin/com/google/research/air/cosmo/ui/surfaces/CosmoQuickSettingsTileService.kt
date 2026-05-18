package com.google.research.air.cosmo.ui.surfaces

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.google.research.air.cosmo.setting.CosmoState
import com.google.research.air.cosmo.setting.CosmoStateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CosmoQuickSettingsTileService : TileService() {
    @Inject lateinit var stateManager: CosmoStateManager

    override fun onStartListening() {
        super.onStartListening()
        stateManager.updateAndScheduleCosmoState()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (stateManager.getCosmoState().value == CosmoState.ENABLED) {
            stateManager.disableCosmo()
        } else {
            stateManager.enableCosmo()
        }
        qsTile?.postDelayedUpdate()
    }

    companion object {
        const val EXTRA_SHOW_SETTINGS = "cosmo_show_settings"
    }

    private fun updateTile() {
        qsTile?.apply {
            val currentState = stateManager.getCosmoState().value
            state = if (currentState == CosmoState.ENABLED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Cosmo"
            subtitle = currentState.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
            updateTile()
        }
    }

    private fun Tile.postDelayedUpdate() {
        updateTile()
        this@CosmoQuickSettingsTileService.mainExecutor.execute { this@CosmoQuickSettingsTileService.updateTile() }
    }
}
