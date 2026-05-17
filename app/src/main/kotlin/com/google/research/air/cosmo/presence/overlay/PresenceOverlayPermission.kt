package com.google.research.air.cosmo.presence.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface PresenceOverlayPermissionChecker {
    fun canDrawOverlays(): Boolean
}

class AndroidPresenceOverlayPermissionChecker
@Inject
constructor(
    @ApplicationContext
    private val context: Context
) : PresenceOverlayPermissionChecker {
    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
}

object PresenceOverlayPermissionIntent {
    fun create(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
