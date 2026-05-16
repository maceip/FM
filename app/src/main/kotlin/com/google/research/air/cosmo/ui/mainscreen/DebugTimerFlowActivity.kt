package com.google.research.air.cosmo.ui.mainscreen

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class DebugTimerFlowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_DEBUG_TIMER_FLOW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
