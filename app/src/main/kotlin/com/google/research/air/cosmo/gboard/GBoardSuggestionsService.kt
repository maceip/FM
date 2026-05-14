package com.google.research.air.cosmo.gboard

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GBoardSuggestionsService : Service() {
    private val binder = Binder()

    override fun onBind(intent: Intent): IBinder = binder
}
