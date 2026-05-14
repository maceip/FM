package com.google.research.air.cosmo.lib.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.google.research.air.cosmo.setting.CosmoStateManager

class BatteryStateReceiver(
    private val cosmoStateManager: CosmoStateManager
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            cosmoStateManager.handleBatteryLevelChange(level * 100f / scale)
        }
    }
}
