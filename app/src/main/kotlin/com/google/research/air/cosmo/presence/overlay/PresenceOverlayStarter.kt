package com.google.research.air.cosmo.presence.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PresenceOverlayStarter
@Inject
constructor(
    @ApplicationContext
    private val context: Context
) {
    fun start(): Result<Unit> =
        runCatching {
            if (!Settings.canDrawOverlays(context)) return@runCatching
            val intent = Intent(context, PresenceOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.map { Unit }

    fun hide(): Result<Unit> =
        runCatching {
            context.stopService(Intent(context, PresenceOverlayService::class.java))
        }.map { Unit }

    fun requestPermission(): Result<Unit> =
        runCatching {
            context.startActivity(PresenceOverlayPermissionIntent.create(context))
        }.map { Unit }
}
