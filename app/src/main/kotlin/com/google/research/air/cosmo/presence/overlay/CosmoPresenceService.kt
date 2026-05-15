package com.google.research.air.cosmo.presence.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.research.air.cosmo.presence.PresenceController
import com.google.research.air.cosmo.ui.theme.CosmoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that owns the presence overlay window. Hosts a [ComposeOverlayHost] containing
 * the [PresenceRoot] composable tree. Survives configuration changes, process backgrounding, and
 * activity destruction.
 *
 * Start/stop via:
 * ```
 * context.startForegroundService(Intent(context, CosmoPresenceService::class.java).apply {
 *     action = ACTION_SHOW
 * })
 * ```
 */
@AndroidEntryPoint
class CosmoPresenceService : Service() {

    companion object {
        const val ACTION_SHOW = "com.google.research.air.cosmo.presence.SHOW"
        const val ACTION_HIDE = "com.google.research.air.cosmo.presence.HIDE"
        const val CHANNEL_ID = "cosmo_presence"
        const val NOTIFICATION_ID = 9001

        fun showIntent(context: Context) = Intent(context, CosmoPresenceService::class.java).apply {
            action = ACTION_SHOW
        }

        fun hideIntent(context: Context) = Intent(context, CosmoPresenceService::class.java).apply {
            action = ACTION_HIDE
        }
    }

    @Inject lateinit var controller: PresenceController

    private var overlayHost: ComposeOverlayHost? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        controller.hydrate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            else -> showOverlay()
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayHost?.onConfigurationChanged()
    }

    override fun onDestroy() {
        overlayHost?.destroy()
        overlayHost = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun showOverlay() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (overlayHost != null) return

        val host = ComposeOverlayHost(this)
        host.setContent {
            TouchableRegionProvider(host) {
                CosmoTheme {
                    PresenceRoot(controller = controller)
                }
            }
        }
        host.show()
        overlayHost = host
    }

    private fun hideOverlay() {
        overlayHost?.hide()
        overlayHost?.destroy()
        overlayHost = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Cosmo Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Ambient assistant presence indicator"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val hideIntent = PendingIntent.getService(
            this, 0,
            hideIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Cosmo")
            .setContentText("Ambient assistant active")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hide", hideIntent)
            .build()
    }
}
