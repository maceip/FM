package com.google.research.air.cosmo.presence.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.research.air.cosmo.presence.PresenceController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PresenceOverlayService : Service() {
    @Inject lateinit var presenceController: PresenceController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: PresenceOverlayWindowController? = null

    override fun onCreate() {
        super.onCreate()
        if (!startForegroundSafely()) {
            stopSelf()
            return
        }
        val windowManager = getSystemService(WindowManager::class.java)
        val backStack = PresenceOverlayBackStack()
        controller =
            PresenceOverlayWindowController(
                backStack = backStack,
                permissionChecker = AndroidPresenceOverlayPermissionChecker(this),
                host =
                    AndroidPresenceOverlayWindowHost(
                        context = this,
                        windowManager = windowManager,
                        onEvent = { event -> controller?.dispatch(event) }
                    )
            )
        scope.launch {
            presenceController.state.collectLatest { state ->
                controller?.renderPresence(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                controller?.dispatch(PresenceOverlayEvent.Dismiss)
                stopSelfResult(startId)
            }
            ACTION_REQUEST_PERMISSION -> {
                startActivity(PresenceOverlayPermissionIntent.create(this))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        controller?.destroy()
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely(): Boolean =
        runCatching {
            val channelId = "presence_overlay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Presence overlay",
                        NotificationManager.IMPORTANCE_MIN
                    )
                )
            }
            val notification =
                NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Cosmo presence overlay")
                    .setContentText("Showing the ambient assistant overlay")
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess

    companion object {
        const val ACTION_HIDE = "com.google.research.air.cosmo.presence.overlay.HIDE"
        const val ACTION_REQUEST_PERMISSION = "com.google.research.air.cosmo.presence.overlay.REQUEST_PERMISSION"
        private const val NOTIFICATION_ID = 7301
    }
}
