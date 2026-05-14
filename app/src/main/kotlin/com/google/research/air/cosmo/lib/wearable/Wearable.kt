package com.google.research.air.cosmo.lib.wearable

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.research.air.cosmo.R
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.WatchAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.notifications.CosmoNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearableService : WearableListenerService() {
    @Inject
    @WatchAudioBytesEphemeralBuffer
    lateinit var watchAudioBytes: InMemoryEphemeralBuffer<ByteArray>

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path.isWatchAudioPath()) {
            watchAudioBytes.add(messageEvent.data)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path.orEmpty().isWatchAudioPath()) {
                event.dataItem.data?.takeIf { it.isNotEmpty() }?.let(watchAudioBytes::add)
            }
        }
    }

    private fun String.isWatchAudioPath(): Boolean =
        contains("audio", ignoreCase = true) || contains("pcm", ignoreCase = true)
}

@AndroidEntryPoint
class WearableAudioStreamService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        startForeground(1601, notification())
        return START_STICKY
    }

    private fun notification(): Notification = NotificationCompat
        .Builder(this, CosmoNotificationManager.WEARABLE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Receiving watch audio")
        .setOngoing(true)
        .build()
}
